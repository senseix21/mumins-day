package com.senseix21.muminsday.alarm

import android.content.Context
import android.media.AudioManager
import com.senseix21.muminsday.store.SilentMode
import android.util.Log
import com.senseix21.muminsday.store.AlarmKind
import com.senseix21.muminsday.store.LoggedAlarm
import com.senseix21.muminsday.store.Outcome
import com.senseix21.muminsday.store.Stores
import java.time.Instant

/**
 * An alarm has arrived: decide (pure, [chooseAction]), then ring or post a silent notification, then log what happened
 * (INV-S7, INV-S8). [now] is the delivery instant, read once at the receiver.
 */
fun deliver(context: Context, trigger: Trigger, now: Instant) {
    when (trigger) {
        Trigger.Replan, Trigger.Unreadable -> Unit
        is Trigger.Reminder -> {
            val alarm = trigger.alarm
            val outcome = act(context, alarm.kind, alarm.plannedAt, now, noticeCode = alarm.noticeCode())
            Stores.alarmLog(context).recordDelivered(alarm, now, outcome)
        }
        // Not logged: a test must not colour the reliability history.
        is Trigger.Test -> act(context, AlarmKind.Adhan, trigger.plannedAt, now, noticeCode = TEST_NOTICE_CODE)
    }
}

private const val TEST_NOTICE_CODE = 60

private fun LoggedAlarm.noticeCode(): Int = salah.ordinal * AlarmKind.entries.size + kind.ordinal

private fun act(context: Context, kind: AlarmKind, plannedAt: Instant, now: Instant, noticeCode: Int): Outcome {
    val silentMode = Stores.settings(context).readApp().silentMode
    return act(context, kind, plannedAt, now, noticeCode, silentMode)
}

private fun act(context: Context, kind: AlarmKind, plannedAt: Instant, now: Instant, noticeCode: Int, silentMode: SilentMode): Outcome =
    when (val action = chooseAction(kind, plannedAt, now, phoneBusy(context), muted(context, silentMode))) {
        is Action.Notify -> {
            val notice = when (action.reason) {
                NoticeReason.TooLate -> Notifications.Notice.Missed
                NoticeReason.Silent, NoticeReason.PreReminder, NoticeReason.PhoneBusy, NoticeReason.SilentMode -> Notifications.Notice.Reminder
            }
            Notifications.post(context, notice, noticeCode)
            Outcome.SilentNotification
        }
        is Action.Ring -> try {
            // From an exact alarm the platform allows this background start (developer.android.com/develop/
            // background-work/services/fgs/restrictions-bg-start). Which alarm methods qualify is G7.
            context.startForegroundService(PlaybackService.playIntent(context, action.sound, audioUseFor(silentMode)))
            Outcome.Rang
        } catch (e: IllegalStateException) {
            // Includes ForegroundServiceStartNotAllowedException (API 31). Never silent (INV-S6): log it, tell the user.
            Log.e(LOG_TAG, "deliver: playback could not start", e)
            Notifications.post(context, Notifications.Notice.Reminder, noticeCode)
            Outcome.RingFailed
        } catch (e: SecurityException) {
            Log.e(LOG_TAG, "deliver: playback not permitted", e)
            Notifications.post(context, Notifications.Notice.Reminder, noticeCode)
            Outcome.RingFailed
        }
    }

/** F1 failure mode 16: no Adhan over a call. */
private fun phoneBusy(context: Context): Boolean {
    val mode = checkNotNull(context.getSystemService(AudioManager::class.java)).mode
    return mode == AudioManager.MODE_IN_CALL || mode == AudioManager.MODE_IN_COMMUNICATION || mode == AudioManager.MODE_RINGTONE
}

/** F1 failure mode 15: the ringer is silent or vibrate and the user chose to respect it. */
private fun muted(context: Context, silentMode: SilentMode): Boolean {
    val ringer = checkNotNull(context.getSystemService(AudioManager::class.java)).ringerMode
    return mutedByUser(silentMode, ringerIsNormal = ringer == AudioManager.RINGER_MODE_NORMAL)
}

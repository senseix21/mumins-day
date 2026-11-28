package com.senseix21.muminsday.alarm

import com.senseix21.muminsday.deed.DayCard
import com.senseix21.muminsday.planner.AlarmSpec
import com.senseix21.muminsday.planner.Delivery
import com.senseix21.muminsday.planner.PlannerSettings
import com.senseix21.muminsday.planner.ReminderMode
import com.senseix21.muminsday.planner.RingMode
import com.senseix21.muminsday.planner.Salah
import com.senseix21.muminsday.planner.decideDelivery
import com.senseix21.muminsday.store.AlarmKind
import com.senseix21.muminsday.store.LoggedAlarm
import com.senseix21.muminsday.store.SilentMode
import java.time.Duration
import java.time.Instant

/**
 * Pure parts of the alarm path: no Android type in this file, so a JVM test can check each rule. The rule that matters
 * most here is that an alarm's intent carries only plain strings and a number, parsed once at the receiver (parse at
 * the edge, trust inside).
 */

/** How the alarm queue is asked to ring: `setAlarmClock` for the Adhan, `setExactAndAllowWhileIdle` for the rest. */
enum class Method { AlarmClock, ExactWhileIdle }

fun methodFor(spec: AlarmSpec): Method = when (spec) {
    is AlarmSpec.PrayerAlarm -> when (spec.mode) {
        RingMode.Adhan -> Method.AlarmClock
        RingMode.Tone, RingMode.Silent -> Method.ExactWhileIdle
    }
    is AlarmSpec.PreReminderAlarm -> Method.ExactWhileIdle
    // The same API as the silent and pre-reminder alarms: a nudge is a notification, never audio, so it needs no more.
    is AlarmSpec.DeedNudge -> Method.ExactWhileIdle
    is AlarmSpec.Replan -> Method.ExactWhileIdle
}

/** Whether any prayer rings or notifies. The home screen says "reminders are off" when this is false. */
fun remindersEnabled(settings: PlannerSettings): Boolean =
    Salah.entries.any { settings.reminders[it].mode != ReminderMode.Off }

/**
 * The planner always adds a replan alarm; with every prayer Off and no nudge there is nothing to wake for, so nothing is
 * set. The nudge needs the queue on its own (G35), which is why this is not [remindersEnabled].
 */
fun alarmsNeeded(settings: PlannerSettings): Boolean = remindersEnabled(settings) || settings.deedNudge != null

/** F4: the nudge is for a card not yet done. A day with no card recorded is not done. Never a reason to repeat. */
fun nudgeNeeded(today: DayCard?): Boolean = today?.doneAt == null

/** What a delivered alarm turned out to be, read from its intent. */
sealed interface Trigger {
    data object Replan : Trigger

    /** The daily deed nudge. Not a prayer alarm, so it is not logged and `assessHealth` never sees it (G35). */
    data object Nudge : Trigger

    /** A planned reminder; it is logged. */
    data class Reminder(val alarm: LoggedAlarm) : Trigger

    /** "Test Adhan": rings like an Adhan, is never logged, so it cannot colour the reliability history (INV-S8). */
    data class Test(val plannedAt: Instant) : Trigger

    /** An intent this build cannot read. The shell still replans, and rings nothing. */
    data object Unreadable : Trigger
}

const val KIND_REPLAN: String = "Replan"
const val KIND_TEST: String = "Test"
const val KIND_NUDGE: String = "DeedNudge"

/** The three plain values an alarm's intent carries. */
class TriggerExtras(val kind: String, val salah: String?, val plannedAtMillis: Long)

fun extrasFor(spec: AlarmSpec): TriggerExtras = when (spec) {
    is AlarmSpec.DeedNudge -> TriggerExtras(KIND_NUDGE, null, spec.at.toEpochMilli())
    is AlarmSpec.Replan -> TriggerExtras(KIND_REPLAN, null, spec.at.toEpochMilli())
    is AlarmSpec.PrayerAlarm, is AlarmSpec.PreReminderAlarm -> {
        val logged = checkNotNull(LoggedAlarm.of(spec)) { "a prayer or pre-reminder alarm is always logged" }
        TriggerExtras(logged.kind.name, logged.salah.name, logged.plannedAt.toEpochMilli())
    }
}

fun parseTrigger(kind: String?, salah: String?, plannedAtMillis: Long?): Trigger {
    if (kind == KIND_REPLAN) return Trigger.Replan
    if (kind == KIND_NUDGE) return Trigger.Nudge
    val plannedAt = plannedAtMillis?.let(Instant::ofEpochMilli) ?: return Trigger.Unreadable
    if (kind == KIND_TEST) return Trigger.Test(plannedAt)
    val alarmKind = AlarmKind.entries.firstOrNull { it.name == kind } ?: return Trigger.Unreadable
    val prayer = Salah.entries.firstOrNull { it.name == salah } ?: return Trigger.Unreadable
    return Trigger.Reminder(LoggedAlarm(prayer, alarmKind, plannedAt))
}

/** What the phone plays. A stand-in until the Adhan recording is licensed (Q5): one sine tone, two lengths. */
enum class Sound(val length: Duration) {
    Adhan(Duration.ofSeconds(12)),
    Tone(Duration.ofSeconds(3)),
}

/** Why a reminder is a notification and not audio. */
enum class NoticeReason { Silent, PreReminder, TooLate, PhoneBusy, SilentMode }

sealed interface Action {
    data class Ring(val sound: Sound) : Action
    data class Notify(val reason: NoticeReason) : Action
}

/** Which audio stream the playback uses (G26). `USAGE_ALARM` ignores silent mode; `USAGE_NOTIFICATION` obeys it. */
enum class AudioUse { Alarm, Notification }

fun audioUseFor(choice: SilentMode): AudioUse = when (choice) {
    SilentMode.AlwaysRing -> AudioUse.Alarm
    SilentMode.RespectSilent -> AudioUse.Notification
}

/**
 * F1 failure mode 15: the user chose "respect silent mode" and the phone's ringer is silent or vibrate, so the Adhan must
 * not sound. [ringerIsNormal] is a fact the shell reads. "Always ring" never mutes.
 */
fun mutedByUser(choice: SilentMode, ringerIsNormal: Boolean): Boolean = choice == SilentMode.RespectSilent && !ringerIsNormal

/**
 * INV-S7 and F1 failure modes 15, 16 and 17: audio only when the alarm is within the late limit, no call is in
 * progress and the phone is not muted by the user's choice; otherwise a silent notification. [phoneBusy] and [muted]
 * are facts the shell reads; [now] is the delivery instant.
 */
fun chooseAction(kind: AlarmKind, plannedAt: Instant, now: Instant, phoneBusy: Boolean, muted: Boolean): Action = when (kind) {
    AlarmKind.Silent -> Action.Notify(NoticeReason.Silent)
    AlarmKind.PreReminder -> Action.Notify(NoticeReason.PreReminder)
    AlarmKind.Adhan, AlarmKind.Tone -> when (decideDelivery(plannedAt, now)) {
        Delivery.SilentNotification -> Action.Notify(NoticeReason.TooLate)
        Delivery.Ring ->
            if (phoneBusy) Action.Notify(NoticeReason.PhoneBusy)
            else if (muted) Action.Notify(NoticeReason.SilentMode)
            else Action.Ring(if (kind == AlarmKind.Adhan) Sound.Adhan else Sound.Tone)
    }
}

/** A mono 16-bit sine with a linear fade at both ends so it starts and stops without a click. */
fun sineWave(sampleRate: Int, length: Duration, frequencyHz: Double, fade: Duration): ShortArray {
    val count = (length.toMillis() * sampleRate / 1000).toInt()
    val fadeSamples = (fade.toMillis() * sampleRate / 1000).toInt().coerceAtMost(count / 2).coerceAtLeast(1)
    return ShortArray(count) { i ->
        val envelope = minOf(1.0, i.toDouble() / fadeSamples, (count - 1 - i).toDouble() / fadeSamples)
        (Math.sin(2.0 * Math.PI * frequencyHz * i / sampleRate) * envelope * 0.5 * Short.MAX_VALUE).toInt().toShort()
    }
}

package com.senseix21.muminsday.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import java.time.Instant

/** `AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED` (API 31), spelled out: the constant would fail lint on minSdk 26. */
private const val EXACT_ALARM_PERMISSION_CHANGED = "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED"

/**
 * Every system event that must put the alarms back (INV-S3, F1 failure modes 9 to 12). All of them do the same thing,
 * so there is one receiver and one function.
 *
 * It never starts playback. Android 15 forbids a `mediaPlayback` foreground service from a BOOT_COMPLETED receiver
 * (developer.android.com/about/versions/15/behavior-changes-15), and a replan has no reason to ring.
 */
class SystemEventReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        val known = action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_TIME_CHANGED ||
            action == Intent.ACTION_TIMEZONE_CHANGED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == EXACT_ALARM_PERMISSION_CHANGED
        if (!known) return
        Log.i(LOG_TAG, "system event: $action")
        replan(context, Instant.now())
    }
}

/** The system delivers every registered alarm here, the Test Adhan and the midnight replan included. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_ALARM) return
        val now = Instant.now()
        val planned = if (intent.hasExtra(AlarmScheduler.EXTRA_PLANNED_AT)) intent.getLongExtra(AlarmScheduler.EXTRA_PLANNED_AT, 0L) else null
        val trigger = parseTrigger(
            intent.getStringExtra(AlarmScheduler.EXTRA_KIND),
            intent.getStringExtra(AlarmScheduler.EXTRA_SALAH),
            planned,
        )
        Log.i(LOG_TAG, "alarm delivered: $trigger now=$now")
        // Delivery first, because it is the time-critical part; then plan again from this moment (ADR-3).
        deliver(context, trigger, now)
        replan(context, now)
    }
}

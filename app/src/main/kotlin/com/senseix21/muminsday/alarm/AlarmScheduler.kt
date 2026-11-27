package com.senseix21.muminsday.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.senseix21.muminsday.MainActivity
import com.senseix21.muminsday.planner.AlarmId
import com.senseix21.muminsday.planner.AlarmSpec
import com.senseix21.muminsday.planner.Plan
import java.time.Instant

/** What [AlarmScheduler.apply] did. There is no inexact fallback, so there is no third case (INV-S6). */
sealed interface ApplyResult {
    data class Applied(val registered: Int) : ApplyResult

    /** The phone will not let the app set exact alarms. Nothing was registered; the caller must say so in words. */
    data object ExactAlarmsNotAllowed : ApplyResult
}

const val ACTION_ALARM: String = "com.senseix21.muminsday.ALARM"

/** Outside [AlarmId.SPACE], so a replan's cancel-all never removes a pending Test Adhan. */
private const val TEST_REQUEST_CODE: Int = 1000

/**
 * Registers a [Plan] with the system alarm queue. "Cancel the whole closed id space, then set the plan" is what makes
 * it idempotent (ADR-3, INV-S4): the queue ends up holding exactly this plan however often it runs.
 */
class AlarmScheduler(private val context: Context) {

    private val alarms: AlarmManager = checkNotNull(context.getSystemService(AlarmManager::class.java))

    /** Android 12 and 12L are the only versions where the user can have switched this off (ADR-4). */
    fun canScheduleExact(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()

    fun cancelAll() {
        for (id in AlarmId.all) cancel(id.value)
    }

    fun apply(plan: Plan): ApplyResult {
        cancelAll()
        if (!canScheduleExact()) return ApplyResult.ExactAlarmsNotAllowed
        return try {
            for (spec in plan.alarms) register(spec.id.value, spec.at, methodFor(spec), extrasFor(spec))
            ApplyResult.Applied(plan.alarms.size)
        } catch (_: SecurityException) {
            // The permission was withdrawn between the check and the call. Leave nothing half-registered.
            cancelAll()
            ApplyResult.ExactAlarmsNotAllowed
        }
    }

    /** "Test Adhan": a real alarm through the same receiver and delivery path as a prayer (F1, check 2). */
    fun scheduleTest(at: Instant, method: Method = Method.AlarmClock): ApplyResult {
        cancel(TEST_REQUEST_CODE)
        if (!canScheduleExact()) return ApplyResult.ExactAlarmsNotAllowed
        return try {
            register(TEST_REQUEST_CODE, at, method, TriggerExtras(KIND_TEST, null, at.toEpochMilli()))
            ApplyResult.Applied(1)
        } catch (_: SecurityException) {
            ApplyResult.ExactAlarmsNotAllowed
        }
    }

    private fun intent(): Intent = Intent(context, AlarmReceiver::class.java).setAction(ACTION_ALARM)

    private fun register(requestCode: Int, at: Instant, method: Method, extras: TriggerExtras) {
        val intent = intent()
            .putExtra(EXTRA_KIND, extras.kind)
            .putExtra(EXTRA_PLANNED_AT, extras.plannedAtMillis)
        extras.salah?.let { intent.putExtra(EXTRA_SALAH, it) }
        val operation = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        when (method) {
            // Exits Doze, and the system shows its alarm-clock icon (F1 failure mode 1).
            Method.AlarmClock -> alarms.setAlarmClock(AlarmManager.AlarmClockInfo(at.toEpochMilli(), showApp()), operation)
            Method.ExactWhileIdle -> alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), operation)
        }
    }

    /** Looks the pending intent up without creating one, so cancelling an id that was never set costs nothing. */
    private fun cancel(requestCode: Int) {
        val existing = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent(),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        ) ?: return
        alarms.cancel(existing)
        existing.cancel()
    }

    private fun showApp(): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        const val EXTRA_KIND: String = "kind"
        const val EXTRA_SALAH: String = "salah"
        const val EXTRA_PLANNED_AT: String = "plannedAt"
    }
}

/**
 * Android 12 and 12L: the screen that lets the user allow exact alarms for this app. Null where there is nothing to
 * grant. The flow, from developer.android.com/develop/background-work/services/alarms: check `canScheduleExact()`,
 * send this intent, re-check in `onResume`, and replan when `ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED`
 * arrives ([SystemEventReceiver] does that).
 */
fun exactAlarmGrantIntent(context: Context): Intent? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
    } else {
        null
    }

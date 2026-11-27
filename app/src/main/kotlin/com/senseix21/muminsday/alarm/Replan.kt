package com.senseix21.muminsday.alarm

import android.content.Context
import android.util.Log
import com.senseix21.muminsday.clock.CalcTimesClock
import com.senseix21.muminsday.planner.Planner
import com.senseix21.muminsday.store.Stores
import java.time.Instant

const val LOG_TAG: String = "Mumins"

sealed interface ReplanResult {
    /** Every prayer is Off: nothing is registered, and everything that was is cancelled. */
    data object NothingEnabled : ReplanResult

    data class Armed(val registered: Int, val daysWithoutTimes: Int) : ReplanResult

    /** Nothing was registered. The home screen must say so (INV-S6). */
    data object ExactAlarmsNotAllowed : ReplanResult
}

/**
 * The one function every trigger calls (INV-S3, INV-S4): boot, time set, zone change, update, permission change, app
 * foreground, settings change, alarm delivered, the midnight replan. It reads settings, plans, cancels the whole id
 * space and registers the plan. The plan is never stored (ADR-3).
 *
 * Plain and synchronous: the planner's target is 50 ms (F1), well inside what a receiver may do.
 */
fun replan(context: Context, now: Instant): ReplanResult {
    val settings = Stores.settings(context).read()
    val log = Stores.alarmLog(context)
    val scheduler = AlarmScheduler(context)

    // First, so a reminder the user switched off cannot later read as missed.
    log.deleteNotYetDue(now)
    log.trim(now)

    if (!remindersEnabled(settings)) {
        scheduler.cancelAll()
        Log.i(LOG_TAG, "replan: nothing enabled")
        return ReplanResult.NothingEnabled
    }

    val plan = Planner(CalcTimesClock()).plan(now, settings)
    // INV-T7: a day that cannot be computed has no alarms. Say so in the log; the home screen reads gaps later.
    for (gap in plan.gaps) Log.w(LOG_TAG, "replan: ${gap.date} not computable (${gap.prayer}, ${gap.reason})")

    return when (val applied = scheduler.apply(plan)) {
        is ApplyResult.Applied -> {
            log.recordPlanned(plan)
            Log.i(LOG_TAG, "replan: registered=${applied.registered} gaps=${plan.gaps.size}")
            ReplanResult.Armed(applied.registered, plan.gaps.size)
        }
        ApplyResult.ExactAlarmsNotAllowed -> {
            Log.w(LOG_TAG, "replan: exact alarms not allowed; nothing registered")
            ReplanResult.ExactAlarmsNotAllowed
        }
    }
}

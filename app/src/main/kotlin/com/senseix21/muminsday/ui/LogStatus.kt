package com.senseix21.muminsday.ui

import com.senseix21.muminsday.health.LATE_FOR_HEALTH
import com.senseix21.muminsday.store.AlarmLogRow
import com.senseix21.muminsday.store.Outcome
import java.time.Duration
import java.time.Instant

/** How one logged alarm turned out, for the Reminder-health screen (INV-S8). About the phone's alarm, never about the user (INV-C4). */
sealed interface LogStatus {
    data object OnTime : LogStatus

    /** Whole minutes after the planned time, rounded up, at least 1. */
    data class Late(val minutes: Int) : LogStatus

    data object NotArrived : LogStatus

    /** Planned time has passed by less than the lateness allowance, nothing arrived yet. */
    data object Pending : LogStatus

    /** Arrived on time, as a silent notification (a silent reminder, a pre-reminder, a muted or busy phone). */
    data object Notice : LogStatus

    data object RingFailed : LogStatus
}

/** Pure: [now] is an argument. The same 2-minute allowance as `assessHealth` ([LATE_FOR_HEALTH]), so the two never disagree. */
fun logStatus(row: AlarmLogRow, now: Instant): LogStatus {
    if (row.outcome == Outcome.RingFailed) return LogStatus.RingFailed
    val planned = row.alarm.plannedAt
    val delivered = row.deliveredAt
        ?: return if (Duration.between(planned, now) > LATE_FOR_HEALTH) LogStatus.NotArrived else LogStatus.Pending
    val lateBy = Duration.between(planned, delivered)
    return when {
        lateBy > LATE_FOR_HEALTH -> LogStatus.Late(((lateBy.seconds + 59) / 60).toInt())
        row.outcome == Outcome.SilentNotification -> LogStatus.Notice
        else -> LogStatus.OnTime
    }
}

package com.senseix21.muminsday.ui

import com.senseix21.muminsday.R
import com.senseix21.muminsday.health.DegradedReason

/** The one tap that fixes a [DegradedReason]. Running it is [HealthFix.kt]'s job; naming it is this file's. */
enum class Fix { AllowExactAlarms, AllowNotifications, BatteryOptimisation, BrandSteps, ReminderHealth }

/**
 * What the screen says about one [DegradedReason] (INV-S6): a message that says what is wrong and what it costs, and a
 * button label with the fix it runs. [count] is set only for the reason that carries a number (a plural message).
 */
class HealthNotice(val message: Int, val count: Int?, val fixLabel: Int, val fix: Fix)

/** Exhaustive with no `else`: a new reason is a compile error here until it has a message and a fix (T-UI-1). */
fun noticeFor(reason: DegradedReason): HealthNotice = when (reason) {
    DegradedReason.ExactAlarmsNotAllowed ->
        HealthNotice(R.string.health_exact_alarms, null, R.string.fix_exact_alarms, Fix.AllowExactAlarms)
    DegradedReason.NotificationsBlocked ->
        HealthNotice(R.string.health_notifications, null, R.string.fix_notifications, Fix.AllowNotifications)
    DegradedReason.BatteryOptimised ->
        HealthNotice(R.string.health_battery, null, R.string.fix_battery, Fix.BatteryOptimisation)
    DegradedReason.BrandStepsNotConfirmed ->
        HealthNotice(R.string.health_brand, null, R.string.fix_brand, Fix.BrandSteps)
    is DegradedReason.RecentAlarmTrouble ->
        HealthNotice(R.plurals.health_recent, reason.late + reason.missed, R.string.fix_recent, Fix.ReminderHealth)
}

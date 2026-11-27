package com.senseix21.muminsday.ui

import com.senseix21.muminsday.health.DegradedReason
import com.senseix21.muminsday.health.Health
import com.senseix21.muminsday.store.AlarmLogRow
import java.time.Duration

/** The facts a report carries and nothing more: no name, account, device id, district or coordinates (ADR-7, INV-P2). */
class ReportFacts(
    val appVersion: String,
    val androidVersion: String,
    val manufacturer: String,
    val model: String,
    val health: Health,
    val log: List<AlarmLogRow>,
)

/** English on purpose: the reader is the developer. Plain text, one fact per line, so the user can read it all before sending. */
fun reportText(facts: ReportFacts): String = buildString {
    appendLine("Mu'mins DAY report")
    appendLine("App version: ${facts.appVersion}")
    appendLine("Android: ${facts.androidVersion}")
    appendLine("Phone: ${facts.manufacturer} ${facts.model}")
    appendLine()
    appendLine("Reminder health:")
    when (val health = facts.health) {
        Health.Armed -> appendLine("  no problem found")
        is Health.Degraded -> health.reasons.forEach { appendLine("  ${describe(it)}") }
    }
    appendLine()
    appendLine("Last 7 days (planned time in UTC; delivered is seconds after planned):")
    if (facts.log.isEmpty()) appendLine("  no alarms logged")
    facts.log.forEach { appendLine("  ${logLine(it)}") }
}

internal fun describe(reason: DegradedReason): String = when (reason) {
    DegradedReason.ExactAlarmsNotAllowed -> "exact alarms not allowed"
    DegradedReason.NotificationsBlocked -> "notifications blocked"
    DegradedReason.BatteryOptimised -> "battery optimisation on"
    DegradedReason.BrandStepsNotConfirmed -> "brand steps not confirmed"
    is DegradedReason.RecentAlarmTrouble -> "recent alarms: ${reason.late} late, ${reason.missed} not arrived"
}

internal fun logLine(row: AlarmLogRow): String {
    val delivered = row.deliveredAt?.let { "+${Duration.between(row.alarm.plannedAt, it).seconds}s" } ?: "not delivered"
    return "${row.alarm.salah} ${row.alarm.kind} planned ${row.alarm.plannedAt} $delivered ${row.outcome}"
}

package com.senseix21.muminsday.health

import java.time.Duration
import java.time.Instant

/** An alarm delivered more than this after its planned time counts against the phone's reliability. */
public val LATE_FOR_HEALTH: Duration = Duration.ofMinutes(2)

/** How far back the alarm log is judged. Matches the seven days the Reminder-health screen shows (INV-S8). */
public val HEALTH_LOOKBACK: Duration = Duration.ofDays(7)

/** Whether this phone's maker is known to stop apps in the background, and whether the user has done the fix. */
public enum class BrandSteps { NotNeeded, Needed, Confirmed }

/** One row of the alarm log: when an alarm was meant to fire and when it did. Null means it has not arrived. */
public data class AlarmRecord(val plannedAt: Instant, val deliveredAt: Instant?)

/** What the shell reads from the platform and its own log. Gathering these is the shell's job; judging them is not. */
public data class HealthFacts(
    val exactAlarmsAllowed: Boolean,
    val notificationsAllowed: Boolean,
    val exemptFromBatteryOptimisation: Boolean,
    val brandSteps: BrandSteps,
    val alarmLog: List<AlarmRecord>,
)

/** Why a reminder cannot be promised. Each reason has its own message and one-tap fix on the home screen (INV-S6). */
public sealed interface DegradedReason {
    public data object ExactAlarmsNotAllowed : DegradedReason
    public data object NotificationsBlocked : DegradedReason
    public data object BatteryOptimised : DegradedReason
    public data object BrandStepsNotConfirmed : DegradedReason

    /** Counts of alarms in the last [HEALTH_LOOKBACK] that came late or never came. At least one is above zero. */
    public class RecentAlarmTrouble internal constructor(public val late: Int, public val missed: Int) : DegradedReason {
        init {
            require(late >= 0 && missed >= 0 && late + missed > 0) { "no trouble to report: late=$late missed=$missed" }
        }

        override fun equals(other: Any?): Boolean =
            other is RecentAlarmTrouble && other.late == late && other.missed == missed

        override fun hashCode(): Int = 31 * late + missed

        override fun toString(): String = "RecentAlarmTrouble(late=$late, missed=$missed)"
    }
}

/** The scheduler's two standing states (see the state machine in the architecture spec). */
public sealed interface Health {
    public data object Armed : Health

    /** [reasons] is never empty and is in a fixed order, so the screen does not reshuffle between checks. */
    public class Degraded internal constructor(public val reasons: List<DegradedReason>) : Health {
        init {
            require(reasons.isNotEmpty()) { "Degraded needs a reason; with none the state is Armed" }
        }

        override fun equals(other: Any?): Boolean = other is Degraded && other.reasons == reasons

        override fun hashCode(): Int = reasons.hashCode()

        override fun toString(): String = "Degraded($reasons)"
    }
}

/**
 * INV-S6: every way a reminder can fail to be exact becomes a named reason; nothing degrades silently. Pure: [now] is
 * an argument.
 */
public fun assessHealth(now: Instant, facts: HealthFacts): Health {
    val reasons = buildList {
        if (!facts.exactAlarmsAllowed) add(DegradedReason.ExactAlarmsNotAllowed)
        if (!facts.notificationsAllowed) add(DegradedReason.NotificationsBlocked)
        if (!facts.exemptFromBatteryOptimisation) add(DegradedReason.BatteryOptimised)
        when (facts.brandSteps) {
            BrandSteps.Needed -> add(DegradedReason.BrandStepsNotConfirmed)
            BrandSteps.NotNeeded, BrandSteps.Confirmed -> Unit
        }
        recentAlarmTrouble(now, facts.alarmLog)?.let(::add)
    }
    return if (reasons.isEmpty()) Health.Armed else Health.Degraded(reasons)
}

private fun recentAlarmTrouble(now: Instant, log: List<AlarmRecord>): DegradedReason.RecentAlarmTrouble? {
    val since = now.minus(HEALTH_LOOKBACK)
    var late = 0
    var missed = 0
    for (record in log) {
        // Planned in the future is not yet anything; planned before the lookback is forgiven.
        if (record.plannedAt <= since || record.plannedAt > now) continue
        val delivered = record.deliveredAt
        when {
            // Not arrived, and already past the point where arriving would count as on time.
            delivered == null -> if (Duration.between(record.plannedAt, now) > LATE_FOR_HEALTH) missed++
            Duration.between(record.plannedAt, delivered) > LATE_FOR_HEALTH -> late++
        }
    }
    return if (late + missed > 0) DegradedReason.RecentAlarmTrouble(late, missed) else null
}

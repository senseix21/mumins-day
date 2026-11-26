package com.senseix21.muminsday.health

import io.kotest.property.Arb
import io.kotest.property.RandomSource
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.enum
import io.kotest.property.arbitrary.long
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class HealthTest {

    private val now = Instant.parse("2026-11-26T12:00:00Z")

    private val allGood = HealthFacts(
        exactAlarmsAllowed = true,
        notificationsAllowed = true,
        exemptFromBatteryOptimisation = true,
        brandSteps = BrandSteps.NotNeeded,
        alarmLog = emptyList(),
    )

    private fun reasons(facts: HealthFacts): List<DegradedReason> = when (val health = assessHealth(now, facts)) {
        Health.Armed -> emptyList()
        is Health.Degraded -> health.reasons
    }

    private fun record(plannedAgo: Duration, lateBy: Duration?) =
        AlarmRecord(now.minus(plannedAgo), lateBy?.let { now.minus(plannedAgo).plus(it) })

    private fun trouble(vararg records: AlarmRecord): DegradedReason.RecentAlarmTrouble? =
        reasons(allGood.copy(alarmLog = records.toList())).filterIsInstance<DegradedReason.RecentAlarmTrouble>().singleOrNull()

    @Test
    fun INV_S6_nothing_wrong_is_armed() {
        assertEquals(Health.Armed, assessHealth(now, allGood))
        assertEquals(Health.Armed, assessHealth(now, allGood.copy(brandSteps = BrandSteps.Confirmed)))
    }

    @Test
    fun INV_S6_each_fact_alone_gives_exactly_its_own_reason() {
        assertEquals(listOf(DegradedReason.ExactAlarmsNotAllowed), reasons(allGood.copy(exactAlarmsAllowed = false)))
        assertEquals(listOf(DegradedReason.NotificationsBlocked), reasons(allGood.copy(notificationsAllowed = false)))
        assertEquals(listOf(DegradedReason.BatteryOptimised), reasons(allGood.copy(exemptFromBatteryOptimisation = false)))
        assertEquals(listOf(DegradedReason.BrandStepsNotConfirmed), reasons(allGood.copy(brandSteps = BrandSteps.Needed)))
    }

    @Test
    fun INV_S6_everything_wrong_lists_every_reason_in_a_fixed_order() {
        val facts = HealthFacts(
            exactAlarmsAllowed = false,
            notificationsAllowed = false,
            exemptFromBatteryOptimisation = false,
            brandSteps = BrandSteps.Needed,
            alarmLog = listOf(record(Duration.ofHours(5), lateBy = null)),
        )
        assertEquals(
            listOf(
                DegradedReason.ExactAlarmsNotAllowed,
                DegradedReason.NotificationsBlocked,
                DegradedReason.BatteryOptimised,
                DegradedReason.BrandStepsNotConfirmed,
                DegradedReason.RecentAlarmTrouble(late = 0, missed = 1),
            ),
            reasons(facts),
        )
    }

    @Test
    fun INV_S8_on_time_alarms_are_no_trouble() {
        assertEquals(null, trouble(record(Duration.ofHours(1), lateBy = Duration.ZERO)))
        assertEquals(null, trouble(record(Duration.ofHours(1), lateBy = Duration.ofSeconds(45))))
        // Exactly the threshold is still on time; one nanosecond more is late.
        assertEquals(null, trouble(record(Duration.ofHours(1), lateBy = LATE_FOR_HEALTH)))
        assertEquals(
            DegradedReason.RecentAlarmTrouble(late = 1, missed = 0),
            trouble(record(Duration.ofHours(1), lateBy = LATE_FOR_HEALTH.plusNanos(1))),
        )
    }

    @Test
    fun INV_S8_an_alarm_that_never_came_is_missed_only_once_it_is_overdue() {
        // Planned ahead, or due a moment ago: not arrived yet is not a miss.
        assertEquals(null, trouble(AlarmRecord(now.plus(Duration.ofHours(3)), null)))
        assertEquals(null, trouble(record(Duration.ofSeconds(30), lateBy = null)))
        assertEquals(null, trouble(record(LATE_FOR_HEALTH, lateBy = null)))
        assertEquals(
            DegradedReason.RecentAlarmTrouble(late = 0, missed = 1),
            trouble(record(LATE_FOR_HEALTH.plusNanos(1), lateBy = null)),
        )
    }

    @Test
    fun INV_S8_only_the_last_seven_days_count() {
        assertEquals(null, trouble(record(HEALTH_LOOKBACK, lateBy = null)))
        assertEquals(null, trouble(record(Duration.ofDays(20), lateBy = Duration.ofHours(1))))
        assertEquals(
            DegradedReason.RecentAlarmTrouble(late = 0, missed = 1),
            trouble(record(HEALTH_LOOKBACK.minusNanos(1), lateBy = null)),
        )
    }

    @Test
    fun INV_S8_late_and_missed_are_counted_separately() {
        assertEquals(
            DegradedReason.RecentAlarmTrouble(late = 2, missed = 1),
            trouble(
                record(Duration.ofDays(1), lateBy = Duration.ofMinutes(3)),
                record(Duration.ofDays(2), lateBy = Duration.ofMinutes(40)),
                record(Duration.ofDays(3), lateBy = null),
                record(Duration.ofDays(4), lateBy = Duration.ofSeconds(5)),
            ),
        )
    }

    /** Over random facts: Armed exactly when nothing is wrong; Degraded never empty; same input, same answer. */
    @Test
    fun INV_S6_property_armed_iff_no_fact_is_wrong() {
        val rs = RandomSource.seeded(11)
        val flag = Arb.boolean()
        val steps = Arb.enum<BrandSteps>()
        val seconds = Arb.long(-86_400L * 9, 3_600L)
        val lateness = Arb.long(-1L, 900L) // -1 stands for "never arrived" in this generator only
        repeat(3_000) {
            val log = List(rs.random.nextInt(0, 6)) {
                val planned = now.plusSeconds(seconds.sample(rs).value)
                val late = lateness.sample(rs).value
                AlarmRecord(planned, if (late < 0) null else planned.plusSeconds(late))
            }
            val facts = HealthFacts(
                flag.sample(rs).value, flag.sample(rs).value, flag.sample(rs).value, steps.sample(rs).value, log,
            )
            val health = assessHealth(now, facts)
            assertEquals(health, assessHealth(now, facts), "deterministic: $facts")

            val logTrouble = log.any { record ->
                val inWindow = record.plannedAt > now.minus(HEALTH_LOOKBACK) && record.plannedAt <= now
                val overdueFrom = record.deliveredAt ?: now
                inWindow && Duration.between(record.plannedAt, overdueFrom) > LATE_FOR_HEALTH
            }
            val somethingWrong = !facts.exactAlarmsAllowed || !facts.notificationsAllowed ||
                !facts.exemptFromBatteryOptimisation || facts.brandSteps == BrandSteps.Needed || logTrouble
            if (somethingWrong) {
                assertTrue(assertIs<Health.Degraded>(health, "$facts").reasons.isNotEmpty())
            } else {
                assertEquals(Health.Armed, health, "$facts")
            }
        }
    }
}

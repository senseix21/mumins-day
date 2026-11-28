package com.senseix21.muminsday.store

import com.senseix21.muminsday.clock.CalcTimesClock
import com.senseix21.muminsday.health.AlarmRecord
import com.senseix21.muminsday.planner.AlarmId
import com.senseix21.muminsday.planner.AlarmSpec
import com.senseix21.muminsday.planner.Delivery
import com.senseix21.muminsday.planner.Planner
import com.senseix21.muminsday.planner.ReminderMode
import com.senseix21.muminsday.planner.Reminders
import com.senseix21.muminsday.planner.RingMode
import com.senseix21.muminsday.planner.Salah
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AlarmLogTest {

    private val now = Instant.parse("2026-11-27T06:00:00Z")
    private val date = LocalDate.of(2026, 11, 27)

    @Test
    fun `the cutoff is exactly 30 days before now`() {
        assertEquals(now.minus(Duration.ofDays(30)), logCutoff(now))
    }

    @Test
    fun `a row converts to what assessHealth expects`() {
        val planned = now.minusSeconds(600)
        val delivered = now.minusSeconds(590)
        val alarm = LoggedAlarm(Salah.Dhuhr, AlarmKind.Adhan, planned)
        assertEquals(AlarmRecord(planned, delivered), AlarmLogRow(1, alarm, delivered, Outcome.Rang).toHealthRecord())
        assertEquals(AlarmRecord(planned, null), AlarmLogRow(2, alarm, null, Outcome.Planned).toHealthRecord())
    }

    @Test
    fun `each alarm spec maps to its kind and the replan is not logged`() {
        val at = now.plusSeconds(60)
        fun prayer(mode: RingMode) = AlarmSpec.PrayerAlarm(AlarmId.prayer(date, Salah.Asr), at, Salah.Asr, mode, at)
        assertEquals(AlarmKind.Adhan, LoggedAlarm.of(prayer(RingMode.Adhan))?.kind)
        assertEquals(AlarmKind.Tone, LoggedAlarm.of(prayer(RingMode.Tone))?.kind)
        assertEquals(AlarmKind.Silent, LoggedAlarm.of(prayer(RingMode.Silent))?.kind)
        val pre = AlarmSpec.PreReminderAlarm(AlarmId.preReminder(date, Salah.Asr), at, Salah.Asr, at.plusSeconds(900))
        assertEquals(LoggedAlarm(Salah.Asr, AlarmKind.PreReminder, at), LoggedAlarm.of(pre))
        assertNull(LoggedAlarm.of(AlarmSpec.Replan(AlarmId.replan(date), at)))
    }

    /**
     * G35: `currentHealth` builds `HealthFacts.alarmLog` from `rowsSince`, and the only writers are `recordPlanned`
     * (which keeps what `LoggedAlarm.of` returns) and `recordDelivered` (which the nudge never calls). So a nudge
     * can never be a row, and so can never read as a missed prayer reminder.
     */
    @Test
    fun `the deed nudge is never logged, so it cannot count against reminder health`() {
        val at = now.plusSeconds(60)
        assertNull(LoggedAlarm.of(AlarmSpec.DeedNudge(AlarmId.deedNudge(date), at)))

        val everything = defaultSettings.copy(
            reminders = Reminders.all(ReminderMode.Adhan),
            deedNudge = LocalTime.of(9, 0),
        )
        val plan = Planner(CalcTimesClock()).plan(now, everything)
        val nudges = plan.alarms.count { it is AlarmSpec.DeedNudge }
        assertTrue(nudges >= 2, "the plan has nudges: $nudges")
        val logged = plan.alarms.mapNotNull(LoggedAlarm::of)
        assertEquals(plan.alarms.size - nudges - 1, logged.size, "everything but the nudges and the replan is logged")
        assertEquals(Salah.entries.toSet(), logged.map { it.salah }.toSet())

        val nudgeOnly = Planner(CalcTimesClock()).plan(now, defaultSettings.copy(deedNudge = LocalTime.of(9, 0)))
        assertEquals(emptyList(), nudgeOnly.alarms.mapNotNull(LoggedAlarm::of))
    }

    @Test
    fun `every delivery decision has an outcome that is not Planned`() {
        Delivery.entries.forEach { check(Outcome.of(it) != Outcome.Planned) }
    }
}

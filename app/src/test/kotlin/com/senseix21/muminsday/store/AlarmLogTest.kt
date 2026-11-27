package com.senseix21.muminsday.store

import com.senseix21.muminsday.health.AlarmRecord
import com.senseix21.muminsday.planner.AlarmId
import com.senseix21.muminsday.planner.AlarmSpec
import com.senseix21.muminsday.planner.Delivery
import com.senseix21.muminsday.planner.RingMode
import com.senseix21.muminsday.planner.Salah
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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

    @Test
    fun `every delivery decision has an outcome that is not Planned`() {
        Delivery.entries.forEach { check(Outcome.of(it) != Outcome.Planned) }
    }
}

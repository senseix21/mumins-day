package com.senseix21.muminsday.ui

import com.senseix21.muminsday.planner.Salah
import com.senseix21.muminsday.store.AlarmKind
import com.senseix21.muminsday.store.AlarmLogRow
import com.senseix21.muminsday.store.LoggedAlarm
import com.senseix21.muminsday.store.Outcome
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class LogStatusTest {
    private val planned = Instant.parse("2026-11-27T00:00:00Z")

    private fun row(deliveredAfterSeconds: Long?, outcome: Outcome) = AlarmLogRow(
        id = 1,
        alarm = LoggedAlarm(Salah.Fajr, AlarmKind.Adhan, planned),
        deliveredAt = deliveredAfterSeconds?.let(planned::plusSeconds),
        outcome = outcome,
    )

    @Test
    fun `delivered within two minutes is on time, a second past is late and rounds up`() {
        assertEquals(LogStatus.OnTime, logStatus(row(0, Outcome.Rang), planned.plusSeconds(10)))
        assertEquals(LogStatus.OnTime, logStatus(row(120, Outcome.Rang), planned.plusSeconds(500)))
        assertEquals(LogStatus.Late(3), logStatus(row(121, Outcome.Rang), planned.plusSeconds(500)))
        assertEquals(LogStatus.Late(5), logStatus(row(300, Outcome.Rang), planned.plusSeconds(500)))
    }

    @Test
    fun `not delivered is pending for two minutes, then not arrived`() {
        assertEquals(LogStatus.Pending, logStatus(row(null, Outcome.Planned), planned.plusSeconds(120)))
        assertEquals(LogStatus.NotArrived, logStatus(row(null, Outcome.Planned), planned.plusSeconds(121)))
    }

    @Test
    fun `a ring that failed and a silent notice are named as such`() {
        assertEquals(LogStatus.RingFailed, logStatus(row(1, Outcome.RingFailed), planned.plusSeconds(500)))
        assertEquals(LogStatus.Notice, logStatus(row(1, Outcome.SilentNotification), planned.plusSeconds(500)))
        // Late wins over the notice kind: lateness is the news.
        assertEquals(LogStatus.Late(10), logStatus(row(600, Outcome.SilentNotification), planned.plusSeconds(900)))
    }

    @Test
    fun `status agrees with the health rule about what counts as late`() {
        // assessHealth counts more than LATE_FOR_HEALTH as late; the screen must too (the same constant).
        for (seconds in listOf(0L, 60L, 120L, 121L, 600L)) {
            val late = logStatus(row(seconds, Outcome.Rang), planned.plusSeconds(700)) is LogStatus.Late
            assertEquals(seconds > 120, late, "delivered after $seconds s")
        }
    }
}

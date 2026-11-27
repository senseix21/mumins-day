package com.senseix21.muminsday.clock

import com.senseix21.muminsday.entitlements.Everything
import com.senseix21.muminsday.entitlements.Feature
import com.senseix21.muminsday.planner.Salah
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UpcomingTest {
    private val place = Place(Latitude.of(23.8)!!, Longitude.of(90.4)!!, Meters.of(0.0)!!, dhaka)
    private val settings = ClockSettings()
    private val day = LocalDate.of(2026, 11, 27)

    /** Fake clock: every prayer of a computable date is on the hour, Fajr at 05:00 local, then 12, 15, 17, 19. */
    private fun clock(vararg broken: LocalDate) = PrayerClock { date, _, _ ->
        if (date in broken) {
            ClockResult.NotComputable(Prayer.Fajr, Reason.SunNeverReachesAngle)
        } else {
            fun at(hour: Int) = date.atTime(hour, 0).atZone(dhaka).toInstant()
            ClockResult.Computed(DayTimes(at(4), at(5), at(6), at(12), at(15), at(17), at(19)))
        }
    }

    private fun local(date: LocalDate, hour: Int, minute: Int = 0) = date.atTime(hour, minute).atZone(dhaka).toInstant()

    @Test
    fun `before Fajr the next is Fajr today`() {
        assertEquals(Upcoming.Found(Salah.Fajr, local(day, 5)), upcomingSalah(clock(), local(day, 3), place, settings))
    }

    @Test
    fun `at the exact time that prayer is not next`() {
        assertEquals(Upcoming.Found(Salah.Dhuhr, local(day, 12)), upcomingSalah(clock(), local(day, 5), place, settings))
        assertEquals(Upcoming.Found(Salah.Asr, local(day, 15)), upcomingSalah(clock(), local(day, 12), place, settings))
    }

    @Test
    fun `after Isha the next is tomorrow's Fajr`() {
        assertEquals(Upcoming.Found(Salah.Fajr, local(day.plusDays(1), 5)), upcomingSalah(clock(), local(day, 20), place, settings))
    }

    @Test
    fun `a day that cannot be computed is skipped, never guessed`() {
        assertEquals(Upcoming.Unknown, upcomingSalah(clock(day, day.plusDays(1)), local(day, 8), place, settings))
        assertEquals(Upcoming.Found(Salah.Fajr, local(day.plusDays(1), 5)), upcomingSalah(clock(day), local(day, 8), place, settings))
    }

    @Test
    fun `after midnight local the date is the place's, not the phone's`() {
        val justAfterMidnight = Instant.parse("2026-11-26T18:30:00Z") // 00:30 on the 27th in Dhaka
        assertEquals(Upcoming.Found(Salah.Fajr, local(day, 5)), upcomingSalah(clock(), justAfterMidnight, place, settings))
    }

    @Test
    fun `countdown rounds minutes up`() {
        val from = Instant.parse("2026-11-27T00:00:00Z")
        assertEquals(Countdown(0, 0), countdown(from, from))
        assertEquals(Countdown(0, 1), countdown(from, from.plusSeconds(1)))
        assertEquals(Countdown(0, 1), countdown(from, from.plusSeconds(60)))
        assertEquals(Countdown(0, 2), countdown(from, from.plusSeconds(61)))
        assertEquals(Countdown(1, 0), countdown(from, from.plusSeconds(3600)))
        assertEquals(Countdown(3, 25), countdown(from, from.plusSeconds(3 * 3600 + 24 * 60 + 5)))
    }

    @Test
    fun `countdown to a time already past is zero`() {
        val from = Instant.parse("2026-11-27T00:00:00Z")
        assertEquals(Countdown(0, 0), countdown(from, from.minusSeconds(500)))
    }

    @Test
    fun `v1 entitles every feature`() {
        assertTrue(Feature.entries.all(Everything::allows))
    }
}

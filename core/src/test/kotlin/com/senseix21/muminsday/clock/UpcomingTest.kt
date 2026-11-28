package com.senseix21.muminsday.clock

import com.senseix21.muminsday.entitlements.Everything
import com.senseix21.muminsday.entitlements.Feature
import com.senseix21.muminsday.planner.Salah
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UpcomingTest {
    private val place = Place(Latitude.of(23.8)!!, Longitude.of(90.4)!!, Meters.of(0.0)!!, dhaka)
    private val settings = ClockSettings()
    private val day = LocalDate.of(2026, 11, 27)
    private val realClock = CalcTimesClock()

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
    fun `fast - before Sehri end it is Sehri end, between the two it is Iftar, after Iftar it is tomorrow's Sehri end`() {
        assertEquals(UpcomingFast.Found(FastingTime.SehriEnd, local(day, 4)), upcomingFast(clock(), local(day, 3), place, settings))
        assertEquals(UpcomingFast.Found(FastingTime.Iftar, local(day, 17)), upcomingFast(clock(), local(day, 4, 1), place, settings))
        assertEquals(UpcomingFast.Found(FastingTime.Iftar, local(day, 17)), upcomingFast(clock(), local(day, 12), place, settings))
        assertEquals(UpcomingFast.Found(FastingTime.SehriEnd, local(day.plusDays(1), 4)), upcomingFast(clock(), local(day, 17, 1), place, settings))
        assertEquals(UpcomingFast.Found(FastingTime.SehriEnd, local(day.plusDays(1), 4)), upcomingFast(clock(), local(day, 23, 59), place, settings))
    }

    @Test
    fun `fast - at the exact time that one is not next`() {
        assertEquals(UpcomingFast.Found(FastingTime.Iftar, local(day, 17)), upcomingFast(clock(), local(day, 4), place, settings))
        assertEquals(UpcomingFast.Found(FastingTime.SehriEnd, local(day.plusDays(1), 4)), upcomingFast(clock(), local(day, 17), place, settings))
    }

    @Test
    fun `fast - a day that cannot be computed is skipped, never guessed`() {
        assertEquals(UpcomingFast.Unknown, upcomingFast(clock(day, day.plusDays(1)), local(day, 8), place, settings))
        assertEquals(UpcomingFast.Found(FastingTime.SehriEnd, local(day.plusDays(1), 4)), upcomingFast(clock(day), local(day, 8), place, settings))
        // Today is fine but tomorrow is not: after Iftar there is nothing to name.
        assertEquals(UpcomingFast.Unknown, upcomingFast(clock(day.plusDays(1)), local(day, 18), place, settings))
    }

    @Test
    fun `fast - after midnight local the date is the place's, not the phone's`() {
        val justAfterMidnight = Instant.parse("2026-11-26T18:30:00Z") // 00:30 on the 27th in Dhaka
        assertEquals(UpcomingFast.Found(FastingTime.SehriEnd, local(day, 4)), upcomingFast(clock(), justAfterMidnight, place, settings))
    }

    /**
     * F2 #5, property over random Bangladesh places, dates and moments: the next fasting time is strictly ahead, is the
     * earliest of the next Sehri end and Iftar, is at most a day and a bit away so the countdown never skips a day,
     * and Iftar is Maghrib (INV-T2). After Iftar it is always tomorrow's Sehri end, whatever the hour.
     */
    @Test
    fun `fast - property over random places and moments, across midnight`() {
        val random = kotlin.random.Random(51)
        for (case in cases(count = 1500, seed = 51)) {
            val today = computed(realClock.dayTimes(case.date, case.place, settings))
            val tomorrow = computed(realClock.dayTimes(case.date.plusDays(1), case.place, settings))
            val startOfDay = case.date.atStartOfDay(case.place.zone).toInstant()
            val now = startOfDay.plusSeconds(random.nextLong(0, 86_400))
            val where = "now=$now ${case.date} ${case.place}"

            val next = upcomingFast(realClock, now, case.place, settings)
            val found = next as? UpcomingFast.Found ?: error("Unknown for a Bangladesh place: $where")
            assertTrue(found.at > now, "strictly ahead: $where")
            assertTrue(Duration.between(now, found.at) <= Duration.ofHours(25), "no day skipped: $where")
            val expected = listOf(
                FastingTime.SehriEnd to today.sehriEnd, FastingTime.Iftar to today.iftar,
                FastingTime.SehriEnd to tomorrow.sehriEnd, FastingTime.Iftar to tomorrow.iftar,
            ).first { (_, at) -> at > now }
            assertEquals(expected, found.time to found.at, "earliest of the four candidates: $where")
            assertEquals(today[Prayer.Maghrib], today.iftar, "INV-T2: $where")
            if (found.time == FastingTime.Iftar) assertTrue(found.at == today.maghrib || found.at == tomorrow.maghrib, "Iftar is Maghrib: $where")
            if (now >= today.iftar) {
                assertEquals(UpcomingFast.Found(FastingTime.SehriEnd, tomorrow.sehriEnd), next, "after Iftar: $where")
            }
        }
    }

    @Test
    fun `fast - the countdown to Iftar rounds up and is never zero before it arrives`() {
        val now = local(day, 16, 59)
        val next = upcomingFast(clock(), now, place, settings) as UpcomingFast.Found
        assertEquals(Countdown(0, 1), countdown(now, next.at))
    }

    @Test
    fun `v1 entitles every feature`() {
        assertTrue(Feature.entries.all(Everything::allows))
    }
}

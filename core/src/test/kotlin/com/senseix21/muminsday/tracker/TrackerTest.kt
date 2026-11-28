package com.senseix21.muminsday.tracker

import com.senseix21.muminsday.planner.Salah
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TrackerTest {

    private val dhaka = ZoneId.of("Asia/Dhaka")
    private val day = LocalDate.of(2026, 11, 27)

    @Test
    fun `the window is today and seven days before it, newest first`() {
        val window = trackerWindow(day, emptySet())
        assertEquals(TRACKER_DAYS, window.size)
        assertEquals((0L..7L).map { day.minusDays(it) }, window.map { it.date })
    }

    @Test
    fun `a day shows exactly what was marked on that date`() {
        val marks = setOf(PrayerMark(day, Salah.Fajr), PrayerMark(day, Salah.Isha), PrayerMark(day.minusDays(2), Salah.Asr))
        val window = trackerWindow(day, marks)
        assertEquals(Salah.entries.map { it in setOf(Salah.Fajr, Salah.Isha) }, Salah.entries.map { window[0].isPrayed(it) })
        assertTrue(window[2].isPrayed(Salah.Asr))
        assertFalse(window[1].isPrayed(Salah.Asr))
    }

    @Test
    fun `a mark outside the window is ignored, not lost`() {
        val old = PrayerMark(day.minusDays(30), Salah.Dhuhr)
        val window = trackerWindow(day, setOf(old))
        assertTrue(window.none { day -> Salah.entries.any(day::isPrayed) })
    }

    // Date attribution, across midnight.
    @Test
    fun `an Isha marked after midnight belongs to the evening it was prayed for`() {
        val ishaAt = day.atTime(23, 30).atZone(dhaka).toInstant()
        val tappedAt = day.plusDays(1).atTime(0, 20).atZone(dhaka).toInstant()
        assertEquals(day, dateOfPrayer(ishaAt, dhaka))
        // The tap is later, on the next date; the prayer's date is what counts.
        assertEquals(day.plusDays(1), tappedAt.atZone(dhaka).toLocalDate())
    }

    @Test
    fun `the date is the place's, not UTC's`() {
        // 2026-11-27 20:00 UTC is 02:00 on the 28th in Dhaka.
        assertEquals(LocalDate.of(2026, 11, 28), dateOfPrayer(Instant.parse("2026-11-27T20:00:00Z"), dhaka))
        assertEquals(LocalDate.of(2026, 11, 27), dateOfPrayer(Instant.parse("2026-11-27T20:00:00Z"), ZoneId.of("UTC")))
    }

    // Date attribution, across a change of zone.
    @Test
    fun `a stored mark keeps its date when the zone changes, only the window moves`() {
        val marked = PrayerMark(day, Salah.Maghrib)
        val instant = day.atTime(23, 0).atZone(dhaka).toInstant()
        val kolkata = ZoneId.of("Asia/Kolkata")
        val kiritimati = ZoneId.of("Pacific/Kiritimati") // UTC+14, a date ahead of everywhere else
        assertEquals(day, dateOfPrayer(instant, dhaka))
        assertEquals(day, dateOfPrayer(instant, kolkata))
        assertEquals(day.plusDays(1), dateOfPrayer(instant, kiritimati))

        for (zone in listOf(dhaka, kolkata, kiritimati)) {
            val today = Instant.parse("2026-11-28T00:00:00Z").atZone(zone).toLocalDate()
            val window = trackerWindow(today, setOf(marked))
            val shown = window.filter { it.isPrayed(Salah.Maghrib) }.map { it.date }
            assertEquals(listOf(day), shown, "the mark stays on $day when the zone is $zone")
        }
    }

    @Test
    fun `property - every day of the window lists exactly the marks stored for its date`() {
        val random = Random(20261128)
        repeat(200) {
            val today = day.plusDays(random.nextLong(-400, 400))
            val marks = List(random.nextInt(0, 40)) {
                PrayerMark(today.minusDays(random.nextLong(-3, 20)), Salah.entries[random.nextInt(Salah.entries.size)])
            }.toSet()
            for (shown in trackerWindow(today, marks)) {
                for (salah in Salah.entries) {
                    assertEquals(PrayerMark(shown.date, salah) in marks, shown.isPrayed(salah), "seed round $it, ${shown.date} $salah")
                }
            }
        }
    }

    // INV-C5: nothing in the model can express a missed count, a streak or a percentage.
    @Test
    fun `no member of the tracker types is named for a count, streak, percentage or miss`() {
        val forbidden = Regex("count|streak|percent|missed|miss|total|score|rate|ratio|size|length", RegexOption.IGNORE_CASE)
        for (type in listOf(TrackerDay::class.java, PrayerMark::class.java)) {
            val names = type.declaredFields.map { it.name } + type.declaredMethods.map { it.name }
            val offenders = names.filter { forbidden.containsMatchIn(it) }
            assertTrue(offenders.isEmpty(), "${type.simpleName} has $offenders")
        }
    }
}

package com.senseix21.muminsday.hijri

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.chrono.HijrahChronology
import java.time.temporal.ChronoField
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HijriTest {

    private val leapYearsOfCycle = setOf(2, 5, 7, 10, 13, 16, 18, 21, 24, 26, 29)

    private fun monthStart(year: Int, month: HijriMonth) = checkNotNull(hijriDateOf(year, month, 1))

    @Test
    fun `leap years are the published 30-year pattern and a cycle has 10631 days`() {
        for (year in 1..600) {
            val days = HijriMonth.entries.sumOf { monthLength(year, it) }
            val leap = (year % 30) in leapYearsOfCycle
            assertEquals(if (leap) 355 else 354, days, "year $year")
        }
        val cycle = (1..30).sumOf { year -> HijriMonth.entries.sumOf { monthLength(year, it) } }
        assertEquals(10_631, cycle)
    }

    @Test
    fun `every month is 29 or 30 days and the next first day follows the last`() {
        var date = LocalDate.of(1990, 1, 1)
        val end = LocalDate.of(2060, 1, 1)
        while (date.isBefore(end)) {
            val h = calculatedHijri(date)
            assertTrue(h.day in 1..monthLength(h.year, h.month), "$date")
            assertTrue(monthLength(h.year, h.month) in 29..30)
            val tomorrow = calculatedHijri(date.plusDays(1))
            if (h.day < monthLength(h.year, h.month)) {
                assertEquals(h.day + 1, tomorrow.day); assertEquals(h.month, tomorrow.month); assertEquals(h.year, tomorrow.year)
            } else {
                assertEquals(1, tomorrow.day)
            }
            date = date.plusDays(1)
        }
    }

    @Test
    fun `Gregorian to Hijri to Gregorian is the identity over six centuries`() {
        var epochDay = LocalDate.of(1700, 1, 1).toEpochDay()
        val end = LocalDate.of(2300, 1, 1).toEpochDay()
        while (epochDay < end) {
            val date = LocalDate.ofEpochDay(epochDay)
            assertEquals(date, gregorianOf(calculatedHijri(date)), "$date")
            epochDay += 7 // every weekday occurs
        }
    }

    @Test
    fun `Hijri to Gregorian to Hijri is the identity for every valid date`() {
        for (year in 1300..1500) for (month in HijriMonth.entries) for (day in 1..monthLength(year, month)) {
            val date = checkNotNull(hijriDateOf(year, month, day))
            assertEquals(date, calculatedHijri(gregorianOf(date)))
        }
    }

    @Test
    fun `a day outside the month is no date`() {
        assertNull(hijriDateOf(1447, HijriMonth.Safar, 30)) // an even month has 29 days
        assertNull(hijriDateOf(1447, HijriMonth.Muharram, 0))
        assertNotNull(hijriDateOf(1447, HijriMonth.Muharram, 30))
    }

    @Test
    fun `the user's adjustment moves the date by exactly that many days and no more`() {
        var date = LocalDate.of(2024, 1, 1)
        repeat(1500) {
            val none = hijriReading(date, false, HijriAdjustment.None).date
            assertEquals(calculatedHijri(date.plusDays(1)), hijriReading(date, false, HijriAdjustment.DayLater).date)
            assertEquals(calculatedHijri(date.minusDays(1)), hijriReading(date, false, HijriAdjustment.DayEarlier).date)
            assertEquals(none, calculatedHijri(date))
            assertNotEquals(none, hijriReading(date, false, HijriAdjustment.DayLater).date)
            date = date.plusDays(1)
        }
    }

    @Test
    fun `after Maghrib the Hijri day is the next one`() {
        val date = LocalDate.of(2026, 2, 18)
        assertEquals(calculatedHijri(date.plusDays(1)), hijriReading(date, true, HijriAdjustment.None).date)
        assertEquals(calculatedHijri(date), hijriReading(date, false, HijriAdjustment.None).date)
    }

    @Test
    fun `reading at an instant uses Maghrib when known and midnight when it is not`() {
        val zone = ZoneId.of("Asia/Dhaka")
        val maghrib = Instant.parse("2026-02-18T11:50:00Z") // 17:50 in Dhaka
        val before = Instant.parse("2026-02-18T11:49:00Z")
        val after = Instant.parse("2026-02-18T11:50:00Z")
        assertEquals(calculatedHijri(LocalDate.of(2026, 2, 18)), hijriReadingAt(before, zone, maghrib, HijriAdjustment.None).date)
        assertEquals(calculatedHijri(LocalDate.of(2026, 2, 19)), hijriReadingAt(after, zone, maghrib, HijriAdjustment.None).date)
        assertEquals(calculatedHijri(LocalDate.of(2026, 2, 18)), hijriReadingAt(after, zone, null, HijriAdjustment.None).date)
    }

    @Test
    fun `every reading names its rule and the adjustment the user chose`() {
        val reading = hijriReading(LocalDate.of(2026, 2, 19), false, HijriAdjustment.DayEarlier)
        assertEquals(HijriRule.CalculatedTabular, reading.rule)
        assertEquals(HijriAdjustment.DayEarlier, reading.adjustment)
        assertEquals(HijriAdjustment.None, HijriAdjustment.ofDays(7))
        assertEquals(HijriAdjustment.DayLater, HijriAdjustment.ofDays(1))
    }

    /**
     * Published anchor, a news report and not a table: the National Moon Sighting Committee announced on 2026-02-18 that
     * Ramadan 1447 begins in Bangladesh on Thursday 2026-02-19 (Dhaka Tribune,
     * https://www.dhakatribune.com/bangladesh/403655/holy-ramadan-begins-on-thursday, read 2026-11-28). This test pins
     * how far the calculated date is from that one announcement; it is not a claim that the calculation matches.
     */
    @Test
    fun `the calculated first of Ramadan 1447 is within one day of the Bangladesh announcement`() {
        val announced = LocalDate.of(2026, 2, 19)
        val calculated = gregorianOf(monthStart(1447, HijriMonth.Ramadan))
        assertTrue(kotlin.math.abs(calculated.toEpochDay() - announced.toEpochDay()) <= 1, "calculated first of Ramadan: $calculated")
    }

    /**
     * The JDK's Umm al-Qura table is a second, independent calculation. The tabular rule here is not that table, so this
     * measures how far apart two calculated calendars are; the user's one-day move is only enough if they stay close.
     */
    @Test
    fun `the tabular date stays within two days of the JDK Umm al-Qura calendar from 1980 to 2070`() {
        var worst = 0L
        var date = LocalDate.of(1980, 1, 1)
        val end = LocalDate.of(2070, 1, 1)
        while (date.isBefore(end)) {
            val umm = HijrahChronology.INSTANCE.date(date)
            // Umm al-Qura has 30-day months where the tabular rule has 29, so count from the first of the month.
            val first = checkNotNull(
                hijriDateOf(umm.get(ChronoField.YEAR_OF_ERA), HijriMonth.entries[umm.get(ChronoField.MONTH_OF_YEAR) - 1], 1),
            )
            val sameDayByTabular = gregorianOf(first).plusDays(umm.get(ChronoField.DAY_OF_MONTH) - 1L)
            val gap = kotlin.math.abs(sameDayByTabular.toEpochDay() - date.toEpochDay())
            if (gap > worst) worst = gap
            date = date.plusDays(1)
        }
        println("HIJRI-PROBE worst gap tabular vs Umm al-Qura 1980-2070: $worst days")
        assertTrue(worst <= 2, "worst gap $worst")
    }

    @Test
    fun `Ramadan is suggested only in the calculated month of Ramadan and never when the mode is already on`() {
        var date = LocalDate.of(2026, 1, 1)
        repeat(400) {
            val reading = hijriReading(date, false, HijriAdjustment.None)
            assertEquals(reading.date.month == HijriMonth.Ramadan, suggestsRamadanMode(reading, ramadanModeOn = false), "$date")
            assertTrue(!suggestsRamadanMode(reading, ramadanModeOn = true))
            date = date.plusDays(1)
        }
    }

    @Test
    fun `the calculation never reads the clock or the default zone`() {
        // Same inputs, different default zone: the answer cannot change.
        val before = java.util.TimeZone.getDefault()
        try {
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Pacific/Kiritimati"))
            val a = hijriReadingAt(Instant.parse("2026-06-01T10:00:00Z"), ZoneOffset.ofHours(6), null, HijriAdjustment.None)
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("America/Los_Angeles"))
            val b = hijriReadingAt(Instant.parse("2026-06-01T10:00:00Z"), ZoneOffset.ofHours(6), null, HijriAdjustment.None)
            assertEquals(a, b)
        } finally {
            java.util.TimeZone.setDefault(before)
        }
    }
}

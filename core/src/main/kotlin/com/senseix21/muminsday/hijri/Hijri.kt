package com.senseix21.muminsday.hijri

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

public enum class HijriMonth {
    Muharram, Safar, RabiAlAwwal, RabiAlAkhir, JumadaAlUla, JumadaAlAkhira, Rajab, Shaban, Ramadan, Shawwal, DhuAlQadah, DhuAlHijjah,
}

/** A Hijri date. Only [hijriDateOf] and the conversions make one, so [day] is always inside its month. */
@ConsistentCopyVisibility
public data class HijriDate internal constructor(public val year: Int, public val month: HijriMonth, public val day: Int)

/**
 * How far the user has moved the calculated date (INV-H1): one day either way or not at all. A closed set, so no
 * other offset can be written down.
 */
public enum class HijriAdjustment(public val days: Int) {
    DayEarlier(-1), None(0), DayLater(1);

    public companion object {
        /** Parse at the edge: a stored number that is not -1, 0 or 1 is no adjustment. */
        public fun ofDays(days: Int): HijriAdjustment = entries.firstOrNull { it.days == days } ?: None
    }
}

/**
 * The rule that produced a date (INV-H1). One rule exists today. An announcement from a moon-sighting body needs a
 * source that is not decided (G54), so there is no `Announced` case: adding one is a compile error in every `when` over
 * this type, and the screen cannot show a calculated date as an announcement by leaving it out.
 */
public enum class HijriRule {
    /** The tabular (arithmetical) civil calendar: a fixed 30-year cycle, not an observation of the moon. */
    CalculatedTabular,
}

/** What the screen shows: a date, the rule behind it, and whether the user moved it. */
public data class HijriReading(public val date: HijriDate, public val rule: HijriRule, public val adjustment: HijriAdjustment) {
    public val isRamadan: Boolean get() = date.month == HijriMonth.Ramadan
}

/**
 * Whether Home may offer Ramadan mode (G33). It only offers: nothing here switches the mode, because the calculated
 * month can be a day out and the first day follows an announcement the app does not hold.
 */
public fun suggestsRamadanMode(reading: HijriReading, ramadanModeOn: Boolean): Boolean = reading.isRamadan && !ramadanModeOn

/*
 * The tabular Islamic calendar, civil epoch. Rules as published at
 * https://en.wikipedia.org/wiki/Tabular_Islamic_calendar (read 2026-11-28): odd months 30 days, even months 29, the
 * twelfth month 30 in a leap year, leap years 2, 5, 7, 10, 13, 16, 18, 21, 24, 26 and 29 of each 30-year cycle (the
 * "Kuwaiti" arithmetic). The day-count formulas below are the usual ones for that rule (Dershowitz and Reingold,
 * Calendrical Calculations, cited by that page); the tests check them against the rule above, not against each other.
 * 1 Muharram 1 AH, civil, is Julian Day Number 1948440 (Friday, 16 July 622 Julian); the Unix epoch day 0 is JDN 2440588.
 */
private const val EPOCH_DAY_OF_1_MUHARRAM_1: Long = 1_948_440L - 2_440_588L

/** Days from 1 Muharram 1 to 1 Muharram of [year]. */
private fun daysBeforeYear(year: Int): Long = 354L * (year - 1) + Math.floorDiv(3L + 11L * year, 30L)

private fun isLeap(year: Int): Boolean = daysBeforeYear(year + 1) - daysBeforeYear(year) == 355L

public fun monthLength(year: Int, month: HijriMonth): Int = when {
    month == HijriMonth.DhuAlHijjah -> if (isLeap(year)) 30 else 29
    month.ordinal % 2 == 0 -> 30 // the odd-numbered months: first, third, ...
    else -> 29
}

/** Days from the first of the year to the first of [month]: 30, 59, 89, ... */
private fun daysBeforeMonth(month: HijriMonth): Int = (59 * month.ordinal + 1) / 2

/** The Hijri date for [date] under the tabular rule, with no day change at Maghrib and no adjustment. */
public fun calculatedHijri(date: LocalDate): HijriDate {
    val days = date.toEpochDay() - EPOCH_DAY_OF_1_MUHARRAM_1
    val year = Math.floorDiv(30L * days + 10_646L, 10_631L).toInt()
    var dayOfYear = (days - daysBeforeYear(year)).toInt()
    val month = HijriMonth.entries.last { daysBeforeMonth(it) <= dayOfYear }
    dayOfYear -= daysBeforeMonth(month)
    return HijriDate(year, month, dayOfYear + 1)
}

/** A Hijri date, or null when [day] is outside the month (a value, not a throw). */
public fun hijriDateOf(year: Int, month: HijriMonth, day: Int): HijriDate? =
    if (day in 1..monthLength(year, month)) HijriDate(year, month, day) else null

/** The Gregorian date of a Hijri date under the same rule. */
public fun gregorianOf(date: HijriDate): LocalDate =
    LocalDate.ofEpochDay(EPOCH_DAY_OF_1_MUHARRAM_1 + daysBeforeYear(date.year) + daysBeforeMonth(date.month) + date.day - 1)

/**
 * The reading for [date] (the place's civil date). The Hijri day begins at Maghrib (ASSUMED convention, reviewer to
 * confirm, PLAN phase 11), so [afterMaghrib] moves it to the next one; then the user's [adjustment] is added.
 */
public fun hijriReading(date: LocalDate, afterMaghrib: Boolean, adjustment: HijriAdjustment): HijriReading {
    val shifted = date.plusDays((if (afterMaghrib) 1L else 0L) + adjustment.days)
    return HijriReading(calculatedHijri(shifted), HijriRule.CalculatedTabular, adjustment)
}

/**
 * The reading at [now] in [zone]. [maghrib] is that civil day's Maghrib, or null when it cannot be computed (INV-T7):
 * the app then does not guess a time, and the Hijri day changes at midnight for that day.
 */
public fun hijriReadingAt(now: Instant, zone: ZoneId, maghrib: Instant?, adjustment: HijriAdjustment): HijriReading =
    hijriReading(now.atZone(zone).toLocalDate(), afterMaghrib = maghrib != null && !now.isBefore(maghrib), adjustment)

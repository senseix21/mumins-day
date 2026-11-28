package com.senseix21.muminsday.tracker

import com.senseix21.muminsday.planner.Salah
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The Salah tracker's whole model (INV-C5). It records only what the user marked: a prayer on a local date. There is no
 * type, field or function in this package that could hold or produce a count of prayers not marked, a streak, a
 * percentage or a comparison, so no screen can show one. A test checks the member names.
 */
public data class PrayerMark(val date: LocalDate, val salah: Salah)

/**
 * The local date a "Prayed" tap on a prayer's notification belongs to: the date of the prayer's own time in the place's
 * zone, not the date of the tap. Isha at 23:30 tapped at 00:20 is still that evening's Isha. Pure: the zone is an
 * argument (INV-T3). Once stored, a mark is a plain date and never moves, whatever zone the phone is in later.
 */
public fun dateOfPrayer(prayerAt: Instant, zone: ZoneId): LocalDate = prayerAt.atZone(zone).toLocalDate()

/** Today and the seven days before it (F-tracker: "today and the past 7 days"). */
public const val TRACKER_DAYS: Int = 8

/** One day of the tracker: which of the five the user marked. Nothing else is derivable from it (INV-C5). */
public class TrackerDay(public val date: LocalDate, private val prayed: Set<Salah>) {

    public fun isPrayed(salah: Salah): Boolean = salah in prayed

    override fun equals(other: Any?): Boolean = other is TrackerDay && other.date == date && other.prayed == prayed
    override fun hashCode(): Int = 31 * date.hashCode() + prayed.hashCode()
    override fun toString(): String = "TrackerDay($date, $prayed)"
}

/**
 * The days the tracker shows, newest first: [today], then seven earlier days. [marks] may hold any dates; those outside
 * the window are ignored here and stay stored. The window is built from [today] in the place's zone, so a change of zone
 * moves which days are shown and never which day a mark is on.
 */
public fun trackerWindow(today: LocalDate, marks: Set<PrayerMark>): List<TrackerDay> =
    (0L until TRACKER_DAYS.toLong()).map { daysAgo ->
        val date = today.minusDays(daysAgo)
        TrackerDay(date, marks.filter { it.date == date }.map { it.salah }.toSet())
    }

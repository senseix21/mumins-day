package com.senseix21.muminsday.clock

import com.senseix21.muminsday.planner.Salah
import java.time.Duration
import java.time.Instant

/** The next of the five prayers after a moment, or the fact that none can be named (INV-T7: never a guessed time). */
public sealed interface Upcoming {
    public data class Found(val salah: Salah, val at: Instant) : Upcoming

    /** Neither today nor tomorrow could be computed, or every prayer of both has passed. */
    public data object Unknown : Upcoming
}

/**
 * The first prayer strictly after [now] today or tomorrow in the place's zone. A day the clock cannot compute is
 * skipped, never filled in. Pure: [now] is an argument (INV-T3).
 */
public fun upcomingSalah(clock: PrayerClock, now: Instant, place: Place, settings: ClockSettings): Upcoming {
    val next = firstAfter(clock, now, place, settings) { times -> Salah.entries.map { it to times[it.prayer] } }
    return if (next == null) Upcoming.Unknown else Upcoming.Found(next.first, next.second)
}

/** The two times a fast is kept between. Iftar is Maghrib (INV-T2); Sehri end is not Fajr (F2 #6). */
public enum class FastingTime { SehriEnd, Iftar }

/** The next of [FastingTime] after a moment, or the fact that none can be named (INV-T7). */
public sealed interface UpcomingFast {
    public data class Found(val time: FastingTime, val at: Instant) : UpcomingFast

    /** Neither today nor tomorrow could be computed. */
    public data object Unknown : UpcomingFast
}

/**
 * Ramadan mode's "what comes next" (F2 #5): the first Sehri end or Iftar strictly after [now], today or tomorrow in
 * the place's zone. After today's Iftar the answer is tomorrow's Sehri end, so the countdown never stops at midnight.
 * It names no Hijri date and decides nothing about whether it is Ramadan: the user turns the mode on (G33).
 */
public fun upcomingFast(clock: PrayerClock, now: Instant, place: Place, settings: ClockSettings): UpcomingFast {
    val next = firstAfter(clock, now, place, settings) { times ->
        listOf(FastingTime.SehriEnd to times.sehriEnd, FastingTime.Iftar to times.iftar)
    }
    return if (next == null) UpcomingFast.Unknown else UpcomingFast.Found(next.first, next.second)
}

/** [candidates] lists a day's named instants in order; the first strictly after [now] over today and tomorrow wins. */
private fun <T> firstAfter(
    clock: PrayerClock,
    now: Instant,
    place: Place,
    settings: ClockSettings,
    candidates: (DayTimes) -> List<Pair<T, Instant>>,
): Pair<T, Instant>? {
    val today = now.atZone(place.zone).toLocalDate()
    for (date in listOf(today, today.plusDays(1))) {
        val computed = clock.dayTimes(date, place, settings) as? ClockResult.Computed ?: continue
        val next = candidates(computed.times).firstOrNull { (_, at) -> at > now }
        if (next != null) return next
    }
    return null
}

/** Whole hours and the minutes left over. */
public data class Countdown(val hours: Int, val minutes: Int)

/**
 * Time left until [to], minutes rounded up so the screen never says "0 minutes" for a prayer that has not begun
 * (INV-T5: a prayer may begin = round up). Never negative: a time already passed is zero.
 */
public fun countdown(from: Instant, to: Instant): Countdown {
    val remaining = Duration.between(from, to)
    if (remaining.isNegative || remaining.isZero) return Countdown(0, 0)
    val totalMinutes = (remaining.seconds + 59) / 60
    return Countdown((totalMinutes / 60).toInt(), (totalMinutes % 60).toInt())
}

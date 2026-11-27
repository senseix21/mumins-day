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
    val today = now.atZone(place.zone).toLocalDate()
    for (date in listOf(today, today.plusDays(1))) {
        val computed = clock.dayTimes(date, place, settings) as? ClockResult.Computed ?: continue
        val next = Salah.entries.map { it to computed.times[it.prayer] }.firstOrNull { (_, at) -> at > now }
        if (next != null) return Upcoming.Found(next.first, next.second)
    }
    return Upcoming.Unknown
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

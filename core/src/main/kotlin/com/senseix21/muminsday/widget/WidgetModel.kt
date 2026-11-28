package com.senseix21.muminsday.widget

import com.senseix21.muminsday.clock.ClockResult
import com.senseix21.muminsday.clock.ClockSettings
import com.senseix21.muminsday.clock.Place
import com.senseix21.muminsday.clock.PrayerClock
import com.senseix21.muminsday.clock.Upcoming
import com.senseix21.muminsday.clock.upcomingSalah
import com.senseix21.muminsday.health.Health
import com.senseix21.muminsday.planner.Salah
import java.time.Instant

/**
 * What a home-screen widget shows, as plain values. A widget computes nothing: it asks `clock` and `health` and draws
 * the answer (spec 03, forward path). Pure: [now] is an argument (INV-T3).
 *
 * [remindersDegraded] is the home screen's own fact (INV-S6): when reminders cannot be promised the widget says so in
 * words and never looks healthier than the app.
 */
public sealed interface NextPrayerModel {
    public val remindersDegraded: Boolean

    public data class Found(val salah: Salah, val at: Instant, override val remindersDegraded: Boolean) : NextPrayerModel

    /** No next prayer can be named (INV-T7). The widget shows an em dash, never a guessed time. */
    public data class Unknown(override val remindersDegraded: Boolean) : NextPrayerModel
}

public fun nextPrayerModel(
    clock: PrayerClock,
    now: Instant,
    place: Place,
    settings: ClockSettings,
    health: Health,
): NextPrayerModel {
    val degraded = health is Health.Degraded
    return when (val next = upcomingSalah(clock, now, place, settings)) {
        is Upcoming.Found -> NextPrayerModel.Found(next.salah, next.at, degraded)
        Upcoming.Unknown -> NextPrayerModel.Unknown(degraded)
    }
}

/** One of today's five. [at] is null when the clock could not compute the day (INV-T7); the widget then shows an em dash. */
public data class FiveTimesRow(val salah: Salah, val at: Instant?)

public data class FiveTimesModel(val rows: List<FiveTimesRow>, val remindersDegraded: Boolean) {
    init {
        require(rows.map { it.salah } == Salah.entries) { "a five-times model has the five prayers in order" }
    }
}

/** Today's five, in the place's zone. A day that cannot be computed gives five rows with no time, never five guesses. */
public fun fiveTimesModel(
    clock: PrayerClock,
    now: Instant,
    place: Place,
    settings: ClockSettings,
    health: Health,
): FiveTimesModel {
    val today = now.atZone(place.zone).toLocalDate()
    val times = (clock.dayTimes(today, place, settings) as? ClockResult.Computed)?.times
    return FiveTimesModel(
        rows = Salah.entries.map { FiveTimesRow(it, times?.get(it.prayer)) },
        remindersDegraded = health is Health.Degraded,
    )
}

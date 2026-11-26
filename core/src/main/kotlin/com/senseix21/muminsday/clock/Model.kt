package com.senseix21.muminsday.clock

import java.time.Instant
import java.time.LocalDate

public enum class Prayer { SehriEnd, Fajr, Sunrise, Dhuhr, Asr, Maghrib, Isha }

public enum class AsrConvention { Hanafi, Shafi }

/** One user offset per prayer. An offset moves exactly that prayer (INV-T6). */
public class Offsets private constructor(private val byPrayer: List<Minutes>) {

    public operator fun get(prayer: Prayer): Minutes = byPrayer[prayer.ordinal]

    public fun with(prayer: Prayer, minutes: Minutes): Offsets =
        Offsets(byPrayer.toMutableList().also { it[prayer.ordinal] = minutes })

    override fun equals(other: Any?): Boolean = other is Offsets && other.byPrayer == byPrayer
    override fun hashCode(): Int = byPrayer.hashCode()
    override fun toString(): String = Prayer.entries.joinToString(prefix = "Offsets(", postfix = ")") { "$it=${this[it].value}" }

    public companion object {
        public val none: Offsets = Offsets(List(Prayer.entries.size) { Minutes(0) })
    }
}

public data class ClockSettings(
    val asr: AsrConvention = AsrConvention.Hanafi,
    val offsets: Offsets = Offsets.none,
    /** How many minutes before Fajr eating must stop. Never negative. */
    val sehriMargin: Minutes = Minutes(0),
) {
    init {
        require(sehriMargin.value >= 0) { "sehriMargin must be >= 0, was ${sehriMargin.value}" }
    }
}

public data class DayTimes(
    val sehriEnd: Instant,
    val fajr: Instant,
    val sunrise: Instant,
    val dhuhr: Instant,
    val asr: Instant,
    val maghrib: Instant,
    val isha: Instant,
) {
    public val iftar: Instant get() = maghrib

    public operator fun get(prayer: Prayer): Instant = when (prayer) {
        Prayer.SehriEnd -> sehriEnd
        Prayer.Fajr -> fajr
        Prayer.Sunrise -> sunrise
        Prayer.Dhuhr -> dhuhr
        Prayer.Asr -> asr
        Prayer.Maghrib -> maghrib
        Prayer.Isha -> isha
    }
}

public enum class Reason {
    /** The sun does not reach the required angle on this date at this latitude (or the engine gave a non-finite value). */
    SunNeverReachesAngle,

    /** No instant 24 h apart from the engine's value falls on the requested local date in the place's zone. */
    NoInstantOnRequestedDate,
}

public sealed interface ClockResult {
    public data class Computed(val times: DayTimes) : ClockResult
    public data class NotComputable(val prayer: Prayer, val reason: Reason) : ClockResult
}

/** The seam that lets the engine be swapped (ADR-2). */
public fun interface PrayerClock {
    public fun dayTimes(date: LocalDate, place: Place, settings: ClockSettings): ClockResult
}

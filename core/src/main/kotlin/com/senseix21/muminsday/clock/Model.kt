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

/**
 * Fixed minutes added to the astronomical times before the user's own offsets, so that the app's times line up with a
 * published timetable. Not a user setting: the user's "matches my mosque" offsets start from zero on top of it.
 */
public class Calibration private constructor(public val offsets: Offsets, public val sehriMargin: Minutes) {

    override fun equals(other: Any?): Boolean = other is Calibration && other.offsets == offsets && other.sehriMargin == sehriMargin
    override fun hashCode(): Int = 31 * offsets.hashCode() + sehriMargin.hashCode()
    override fun toString(): String = "Calibration($offsets, sehriMargin=${sehriMargin.value})"

    public companion object {
        /** Astronomy only: the engine's times, safe-side rounded, nothing added. */
        public val none: Calibration = Calibration(Offsets.none, Minutes(0))

        /**
         * The Islamic Foundation, Bangladesh, permanent Dhaka timetable (ADR-16). Its own note says it adds 3 minutes to
         * Fajr, Dhuhr and Maghrib as a precaution and puts Sehri end 3 minutes before true dawn. Measured against all 71
         * usable rows over 2025 to 2028 (IslamicFoundationTableTest), these values are the smallest that keep the app
         * never more than 1 minute on the unsafe side of the Foundation for every prayer:
         * Sehri end earlier, every "prayer may begin" time later, sunrise not later.
         */
        public val islamicFoundation: Calibration = Calibration(
            offsets = Offsets.none
                .with(Prayer.Fajr, Minutes(2))
                .with(Prayer.Dhuhr, Minutes(3))
                .with(Prayer.Maghrib, Minutes(3))
                .with(Prayer.Isha, Minutes(2)),
            sehriMargin = Minutes(6),
        )
    }
}

public data class ClockSettings(
    val asr: AsrConvention = AsrConvention.Hanafi,
    /** The user's own offsets, added on top of [calibration]. An offset moves exactly one prayer (INV-T6). */
    val offsets: Offsets = Offsets.none,
    /** Extra minutes before Fajr that eating must stop, on top of [calibration]'s. Never negative. */
    val sehriMargin: Minutes = Minutes(0),
    val calibration: Calibration = Calibration.none,
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

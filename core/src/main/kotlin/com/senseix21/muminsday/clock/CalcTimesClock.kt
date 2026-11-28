package com.senseix21.muminsday.clock

import dev.metinkale.calctimes.CalcTimes
import dev.metinkale.calctimes.DoubleTimes
import dev.metinkale.calctimes.Method
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.EnumMap
import kotlin.math.ceil
import kotlin.math.floor
import kotlinx.datetime.LocalDate as EngineDate
import kotlinx.datetime.TimeZone as EngineZone

/**
 * Karachi method (Fajr 18, Isha 18) on the vendored calctimes engine. Pure: no clock, zone, locale or I/O is read.
 *
 * Offsets go through the engine's own minute fields so there is one mechanism (INV-T6). Sehri end uses the engine's
 * imsak slot at the same 18 degrees, so it follows the calculated Fajr and is not moved by the Fajr offset.
 */
public class CalcTimesClock : PrayerClock {

    override fun dayTimes(date: LocalDate, place: Place, settings: ClockSettings): ClockResult {
        val calibration = settings.calibration
        // One offset per prayer: the calibration's plus the user's. Both are whole minutes through the same engine field.
        fun offset(prayer: Prayer): Minutes = Minutes(calibration.offsets[prayer].value + settings.offsets[prayer].value)
        val offsets = Prayer.entries.fold(Offsets.none) { acc, prayer -> acc.with(prayer, offset(prayer)) }
        val margin = calibration.sehriMargin.value + settings.sehriMargin.value
        val method = Method.Karachi.copy(
            imsakAngle = 18.0,
            imsakMinute = offsets[Prayer.SehriEnd].value - margin,
            fajrAngle = 18.0,
            fajrMinute = offsets[Prayer.Fajr].value,
            sunriseMinute = offsets[Prayer.Sunrise].value,
            dhuhrMinute = offsets[Prayer.Dhuhr].value,
            asrHanafiMinute = offsets[Prayer.Asr].value,
            asrShafiMinute = offsets[Prayer.Asr].value,
            maghribMinute = offsets[Prayer.Maghrib].value,
            ishaaAngle = 18.0,
            ishaaMinute = offsets[Prayer.Isha].value,
        )
        // The engine's zone only matters to its own toTimes(); we read the UTC hours, so any fixed zone is the same.
        val engine = CalcTimes(
            place.latitude.degrees,
            place.longitude.degrees,
            place.elevation.value,
            EngineZone.UTC,
            method,
        )
        val raw = engine.getDoubleTimes(EngineDate(date.year, date.monthValue, date.dayOfMonth))

        val midnightUtc = date.atStartOfDay(ZoneOffset.UTC).toInstant()
        val instants = EnumMap<Prayer, Instant>(Prayer::class.java)
        for (prayer in Prayer.entries) { // Prayer order: the first failure is the one reported.
            val hours = utcHours(raw, prayer, settings.asr)
            if (!hours.isFinite()) return ClockResult.NotComputable(prayer, Reason.SunNeverReachesAngle)
            val minutes = roundToWholeMinutes(hours, rounding(prayer))
            val instant = onLocalDate(midnightUtc.plus(Duration.ofMinutes(minutes.toLong())), date, place.zone)
                ?: return ClockResult.NotComputable(prayer, Reason.NoInstantOnRequestedDate)
            instants[prayer] = instant
        }
        return ClockResult.Computed(
            DayTimes(
                sehriEnd = instants.getValue(Prayer.SehriEnd),
                fajr = instants.getValue(Prayer.Fajr),
                sunrise = instants.getValue(Prayer.Sunrise),
                dhuhr = instants.getValue(Prayer.Dhuhr),
                asr = instants.getValue(Prayer.Asr),
                maghrib = instants.getValue(Prayer.Maghrib),
                isha = instants.getValue(Prayer.Isha),
            ),
        )
    }
}

internal enum class Rounding { Down, Up }

/** INV-T5: stop-eating and sunrise round down; every "may begin" time rounds up. */
private fun rounding(prayer: Prayer): Rounding = when (prayer) {
    Prayer.SehriEnd, Prayer.Sunrise -> Rounding.Down
    Prayer.Fajr, Prayer.Dhuhr, Prayer.Asr, Prayer.Maghrib, Prayer.Isha -> Rounding.Up
}

/** Hours since 00:00 UTC, in [0, 24] (engine wraps with `while > 24`, so exactly 24 is possible), date info lost. */
private fun utcHours(raw: DoubleTimes, prayer: Prayer, asr: AsrConvention): Double = when (prayer) {
    Prayer.SehriEnd -> raw.imsak
    Prayer.Fajr -> raw.fajr
    Prayer.Sunrise -> raw.sunrise
    Prayer.Dhuhr -> raw.dhuhr
    Prayer.Asr -> when (asr) {
        AsrConvention.Hanafi -> raw.asrHanafi
        AsrConvention.Shafi -> raw.asrShafi
    }
    Prayer.Maghrib -> raw.maghrib
    Prayer.Isha -> raw.ishaa
}

/**
 * Tolerance, in minutes, for a value that is a whole minute up to floating-point noise. hours * 60 after the engine's
 * chain of additions and wraps carries error around 1e-12 minutes; a bare ceil would turn 05:07.000000000001 into
 * 05:08 and a bare floor would turn 05:06.999999999999 into 05:06. 1e-9 minutes (60 ns) is far above the noise and far
 * below anything a person could act on. The same tolerance both ways keeps an offset of N minutes moving a prayer by
 * exactly N (INV-T6).
 */
internal const val WHOLE_MINUTE_TOLERANCE: Double = 1e-9

internal fun roundToWholeMinutes(hours: Double, rounding: Rounding): Int {
    val minutes = hours * 60.0
    return when (rounding) {
        Rounding.Down -> floor(minutes + WHOLE_MINUTE_TOLERANCE)
        Rounding.Up -> ceil(minutes - WHOLE_MINUTE_TOLERANCE)
    }.toInt()
}

/**
 * The engine's hours carry no date. Bangladesh is UTC+6, so a 05:00 local Fajr is 23:00 UTC of the day before and the
 * naive "date at 00:00 UTC + minutes" lands on the wrong local date. Move by whole days until the local date matches;
 * instants 24 h apart cover every local day except one a zone transition skipped, where we give up (null) rather than
 * guess.
 */
private fun onLocalDate(first: Instant, date: LocalDate, zone: ZoneId): Instant? {
    var instant = first
    repeat(3) {
        val local = instant.atZone(zone).toLocalDate()
        instant = when {
            local == date -> return instant
            local.isBefore(date) -> instant.plus(Duration.ofHours(24))
            else -> instant.minus(Duration.ofHours(24))
        }
    }
    return null
}

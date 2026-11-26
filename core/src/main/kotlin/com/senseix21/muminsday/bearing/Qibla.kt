package com.senseix21.muminsday.bearing

import com.senseix21.muminsday.clock.Latitude
import com.senseix21.muminsday.clock.Longitude
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** The Kaaba, as upstream has it (`compass/MainFragment.kt:198-199`). */
public const val KAABA_LATITUDE: Double = 21.42247
public const val KAABA_LONGITUDE: Double = 39.826198

/** A direction, in degrees clockwise from north, always in `[0, 360)`. Whether north is true or magnetic is the caller's to say. */
@JvmInline
public value class Bearing private constructor(public val degrees: Double) {

    /** This bearing turned by [degrees], positive clockwise. */
    public fun turned(degrees: Double): Bearing = of(this.degrees + degrees)

    public companion object {
        /** Any finite angle, wrapped into `[0, 360)`. Null for NaN or infinity. */
        public fun ofOrNull(degrees: Double): Bearing? = if (degrees.isFinite()) of(degrees) else null

        internal fun of(degrees: Double): Bearing {
            val wrapped = degrees.mod(360.0)
            // -1e-17.mod(360.0) is 360.0 exactly in floating point; fold it back so the range stays half-open.
            return Bearing(if (wrapped >= 360.0) 0.0 else wrapped)
        }
    }
}

public sealed interface QiblaDirection {
    /** Face this way. [bearing] is from true north. */
    public data class Toward(val bearing: Bearing) : QiblaDirection

    /** At the Kaaba, or at the point opposite it on the globe, every direction is equally right. */
    public data object Everywhere : QiblaDirection
}

/**
 * INV-Q1: the initial great-circle bearing from a place to the Kaaba, from true north.
 *
 * Computed on a sphere. Against the WGS84 ellipsoid the difference is under 0.04 degrees anywhere in Bangladesh
 * (measured with geographiclib, 2026-11-26), far inside the 1-degree target and below what a phone compass resolves.
 */
public fun qiblaDirection(latitude: Latitude, longitude: Longitude): QiblaDirection {
    val from = Math.toRadians(latitude.degrees)
    val to = Math.toRadians(KAABA_LATITUDE)
    val east = Math.toRadians(KAABA_LONGITUDE - longitude.degrees)

    val y = sin(east) * cos(to)
    val x = cos(from) * sin(to) - sin(from) * cos(to) * cos(east)
    // Both vanish only at the Kaaba and its antipode; there atan2 would return a meaningless 0.
    if (abs(x) < NO_DIRECTION && abs(y) < NO_DIRECTION) return QiblaDirection.Everywhere
    return QiblaDirection.Toward(Bearing.of(Math.toDegrees(atan2(y, x))))
}

/** About 6 metres on the ground. Closer to the Kaaba than this, a bearing from coordinates means nothing. */
private const val NO_DIRECTION = 1e-6

/**
 * What a magnetic compass must read to face [trueBearing]. [declinationEast] is how far magnetic north lies east of
 * true north at the place, in degrees, negative when west; the shell gets it from the platform's geomagnetic model.
 */
public fun magneticBearing(trueBearing: Bearing, declinationEast: Double): Bearing = trueBearing.turned(-declinationEast)

public enum class Cardinal { North, East, South, West }

/**
 * A bearing as people say it: "west", or "8 degrees north of west". The words themselves belong to l10n and a native
 * speaker; this is only the structure.
 */
public sealed interface CompassPoint {
    public data class Exactly(val cardinal: Cardinal) : CompassPoint

    /** [degrees] from [from] toward [toward], 1..45. [toward] is always a neighbour of [from]. */
    public data class Between(val from: Cardinal, val degrees: Int, val toward: Cardinal) : CompassPoint
}

/**
 * Whole degrees from the nearest cardinal point. Exactly halfway (45 degrees) is told from north or south, as in
 * "45 degrees east of north", never from east or west.
 */
public fun compassPoint(bearing: Bearing): CompassPoint {
    val whole = bearing.degrees.roundToInt().mod(360)
    val quadrantStart = Cardinal.entries[whole / 90]
    val quadrantEnd = Cardinal.entries[(whole / 90 + 1) % 4]
    val past = whole % 90
    return when {
        past == 0 -> CompassPoint.Exactly(quadrantStart)
        past < 45 -> CompassPoint.Between(quadrantStart, past, quadrantEnd)
        past > 45 -> CompassPoint.Between(quadrantEnd, 90 - past, quadrantStart)
        else -> when (quadrantStart) {
            Cardinal.North, Cardinal.South -> CompassPoint.Between(quadrantStart, 45, quadrantEnd)
            Cardinal.East, Cardinal.West -> CompassPoint.Between(quadrantEnd, 45, quadrantStart)
        }
    }
}

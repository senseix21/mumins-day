package com.senseix21.muminsday.clock

import java.time.ZoneId

/** A signed count of whole minutes. Offsets and margins cross function boundaries as this, never as a bare Int. */
@JvmInline
public value class Minutes(public val value: Int)

/** Height above sea level. Finite and not negative; the engine takes a square root of it. */
@JvmInline
public value class Meters private constructor(public val value: Double) {
    public companion object {
        /** Null when [value] is NaN, infinite or negative. */
        public fun of(value: Double): Meters? =
            if (value.isFinite() && value >= 0.0) Meters(value) else null
    }
}

/** Degrees north, -90..90. */
@JvmInline
public value class Latitude private constructor(public val degrees: Double) {
    public companion object {
        /** Null when [degrees] is NaN or outside -90..90. */
        public fun of(degrees: Double): Latitude? =
            if (degrees in -90.0..90.0) Latitude(degrees) else null
    }
}

/** Degrees east, -180..180. */
@JvmInline
public value class Longitude private constructor(public val degrees: Double) {
    public companion object {
        /** Null when [degrees] is NaN or outside -180..180. */
        public fun of(degrees: Double): Longitude? =
            if (degrees in -180.0..180.0) Longitude(degrees) else null
    }
}

public data class Place(
    val latitude: Latitude,
    val longitude: Longitude,
    val elevation: Meters,
    val zone: ZoneId,
)

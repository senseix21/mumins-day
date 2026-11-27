package com.senseix21.muminsday.bearing

/** A signed angle in degrees: a declination (east positive) or a tilt. Finite by construction. */
@JvmInline
public value class Degrees private constructor(public val value: Double) {
    public companion object {
        /** Null for NaN or infinity. */
        public fun of(value: Double): Degrees? = if (value.isFinite()) Degrees(value) else null
    }
}

/** Magnetic flux density in microtesla. Finite and not negative. */
@JvmInline
public value class MicroTesla private constructor(public val value: Double) {
    public companion object {
        /** Null for NaN, infinity or a negative number. */
        public fun of(value: Double): MicroTesla? = if (value.isFinite() && value >= 0.0) MicroTesla(value) else null
    }
}

/**
 * How much the platform says it trusts a sensor reading. The platform's own scale is SENSOR_STATUS_ACCURACY_* (0 to 3);
 * the shell maps -1 (no contact) and 0 to [Unreliable]. Ordered, so "less than high" is a comparison (INV-Q2).
 */
public enum class SensorAccuracy { Unreliable, Low, Medium, High }

/**
 * The weakest and strongest field the Earth produces at its surface, from the platform's own constants
 * `SensorManager.MAGNETIC_FIELD_EARTH_MIN` and `_MAX` (KNOWN: read from android.jar, API 37, 2026-11-27; documented at
 * https://developer.android.com/reference/android/hardware/SensorManager#MAGNETIC_FIELD_EARTH_MAX). Using them as hard
 * limits for "interference" (F3 #2) is our choice (target): a reading outside them is a magnet, metal or a bad sensor.
 */
public const val EARTH_FIELD_MIN_MICROTESLA: Double = 30.0
public const val EARTH_FIELD_MAX_MICROTESLA: Double = 60.0

/**
 * When the phone is tilted more than this from flat, in either direction, the screen asks the user to lay it flat
 * (F3 #5). 25 degrees is our choice (target); no source gives a number.
 */
public const val LAY_FLAT_TILT_DEGREES: Double = 25.0

/** Everything the decision needs; the shell reads each fact from the platform. */
public data class CompassFacts(
    val hasMagnetometer: Boolean,
    /** The magnetometer's own accuracy, [SensorAccuracy.Unreliable] until the platform has said otherwise. */
    val accuracy: SensorAccuracy,
    /** The measured field strength, null until the first reading arrives. */
    val field: MicroTesla?,
    /** Magnetic north's angle east of true north at the place, null if the model gave none. */
    val declinationEast: Degrees?,
    /** The great-circle bearing from true north (INV-Q1). */
    val trueBearing: Bearing,
)

public enum class UntrustedReason {
    /** No field reading yet. */
    NoReading,

    /** The platform reports less than high accuracy (INV-Q2): the compass needs calibrating. */
    LowAccuracy,
    FieldTooWeak,
    FieldTooStrong,

    /** Without the declination a magnetic needle would point at true north's direction, which is wrong by the declination. */
    NoDeclination,
}

public sealed interface CompassState {
    /** No magnetometer: show the bearing and a hint, never a needle (INV-Q3). */
    public data object NoSensor : CompassState

    /** The needle may be shown in full colour. [magneticTarget] is the bearing a magnetic compass must read to face the Qibla. */
    public data class Trusted(val magneticTarget: Bearing) : CompassState

    /**
     * Show the needle greyed with a warning and a calibration prompt (INV-Q2). [reasons] is never empty.
     * [magneticTarget] is null when it cannot be worked out (no declination), and then no needle is drawn at all.
     */
    public data class Untrusted(val reasons: List<UntrustedReason>, val magneticTarget: Bearing?) : CompassState
}

/** INV-Q2, INV-Q3, F3 #1 to #3 and #6. Pure: the same facts always give the same state. */
public fun compassState(facts: CompassFacts): CompassState {
    if (!facts.hasMagnetometer) return CompassState.NoSensor

    val reasons = buildList {
        val field = facts.field
        if (field == null) add(UntrustedReason.NoReading)
        if (facts.accuracy < SensorAccuracy.High) add(UntrustedReason.LowAccuracy)
        if (field != null && field.value < EARTH_FIELD_MIN_MICROTESLA) add(UntrustedReason.FieldTooWeak)
        if (field != null && field.value > EARTH_FIELD_MAX_MICROTESLA) add(UntrustedReason.FieldTooStrong)
        if (facts.declinationEast == null) add(UntrustedReason.NoDeclination)
    }
    val target = facts.declinationEast?.let { magneticBearing(facts.trueBearing, it.value) }
    return if (reasons.isEmpty()) {
        // No reason means a declination, so the target exists; the sealed type cannot say so, hence the explicit check.
        if (target == null) CompassState.Untrusted(listOf(UntrustedReason.NoDeclination), null) else CompassState.Trusted(target)
    } else {
        CompassState.Untrusted(reasons, target)
    }
}

/** F3 #5: true when the phone is too far from flat for the reading to be good. */
public fun shouldLayFlat(pitch: Degrees, roll: Degrees): Boolean =
    kotlin.math.abs(pitch.value) > LAY_FLAT_TILT_DEGREES || kotlin.math.abs(roll.value) > LAY_FLAT_TILT_DEGREES

/** Where the needle points on screen, clockwise from the top of the phone, given where the phone's top points. */
public fun needleAngle(deviceHeading: Bearing, magneticTarget: Bearing): Bearing = magneticTarget.turned(-deviceHeading.degrees)

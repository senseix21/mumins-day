package com.senseix21.muminsday.sensors

import android.hardware.GeomagneticField
import com.senseix21.muminsday.bearing.Degrees
import com.senseix21.muminsday.clock.Place
import java.time.Instant

/**
 * How far magnetic north lies east of true north at [place] on [at], from the platform's World Magnetic Model, which
 * ships inside Android and needs no network (F3 #6). Null if the model returns something that is not a number.
 * Reference: https://developer.android.com/reference/android/hardware/GeomagneticField
 */
fun declinationAt(place: Place, at: Instant): Degrees? = Degrees.of(
    GeomagneticField(
        place.latitude.degrees.toFloat(),
        place.longitude.degrees.toFloat(),
        place.elevation.value.toFloat(),
        at.toEpochMilli(),
    ).declination.toDouble(),
)

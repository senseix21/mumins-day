package com.senseix21.muminsday.districts

import com.senseix21.muminsday.clock.Latitude
import com.senseix21.muminsday.clock.Longitude
import com.senseix21.muminsday.clock.Meters
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** Mean Earth radius in metres (IUGG). A district is chosen by distance to a headquarters point, so a sphere is enough. */
private const val EARTH_RADIUS_METERS = 6_371_008.8

/** Beyond this the phone is not near any district's headquarters, so the app asks before offering one (F1 #6). */
public val NEAR_DISTRICT_LIMIT: Meters = checkNotNull(Meters.of(50_000.0))

public enum class WhyAsk {
    /** The position is outside Bangladesh's bounding box (see [isInsideBangladeshBoundingBox]). */
    OutsideBangladesh,

    /** Inside the box but more than [NEAR_DISTRICT_LIMIT] from every headquarters point. */
    TooFarFromAnyDistrict,
}

/**
 * What "find my district" concluded from one position. Every case needs the user's tap before the district changes: the
 * app never switches on its own (F1 #7). [NeedsConfirmation] is for a position that is probably not where the user means
 * to have prayer times worked out, so the screen must say so in words before it offers the district.
 */
public sealed interface NearestDistrict {
    public data object NoDistricts : NearestDistrict

    public data class Near(val district: District, val distance: Meters) : NearestDistrict

    public data class NeedsConfirmation(val district: District, val distance: Meters, val why: WhyAsk) : NearestDistrict
}

/**
 * The district whose headquarters point is nearest to ([latitude], [longitude]), judged by great-circle distance. Pure,
 * and never stores the position: the caller keeps only the district it is told to (INV-P2). Ties go to the earlier record.
 */
public fun nearestDistrict(latitude: Latitude, longitude: Longitude, districts: List<District>): NearestDistrict {
    val nearest = districts.minByOrNull { distanceBetween(latitude, longitude, it.latitude, it.longitude).value }
        ?: return NearestDistrict.NoDistricts
    val distance = distanceBetween(latitude, longitude, nearest.latitude, nearest.longitude)
    return when {
        !isInsideBangladeshBoundingBox(latitude, longitude) ->
            NearestDistrict.NeedsConfirmation(nearest, distance, WhyAsk.OutsideBangladesh)
        distance.value > NEAR_DISTRICT_LIMIT.value ->
            NearestDistrict.NeedsConfirmation(nearest, distance, WhyAsk.TooFarFromAnyDistrict)
        else -> NearestDistrict.Near(nearest, distance)
    }
}

/** The distance for a sentence such as "about 12 km away": whole kilometres, rounded to the nearest. */
public fun Meters.wholeKilometers(): Int = (value / 1000.0).roundToInt()

/** Haversine distance. Always finite and not negative, so it is a valid [Meters]. */
internal fun distanceBetween(fromLatitude: Latitude, fromLongitude: Longitude, toLatitude: Latitude, toLongitude: Longitude): Meters {
    val phi1 = Math.toRadians(fromLatitude.degrees)
    val phi2 = Math.toRadians(toLatitude.degrees)
    val deltaPhi = phi2 - phi1
    val deltaLambda = Math.toRadians(toLongitude.degrees - fromLongitude.degrees)
    val a = sin(deltaPhi / 2) * sin(deltaPhi / 2) + cos(phi1) * cos(phi2) * sin(deltaLambda / 2) * sin(deltaLambda / 2)
    // Rounding can push a above 1 by a hair for antipodal points; min keeps the root real.
    val meters = 2 * EARTH_RADIUS_METERS * asin(sqrt(min(1.0, a)))
    return checkNotNull(Meters.of(meters)) { "a great-circle distance is finite and not negative: $meters" }
}

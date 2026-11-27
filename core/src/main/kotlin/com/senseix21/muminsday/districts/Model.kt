package com.senseix21.muminsday.districts

import com.senseix21.muminsday.clock.Latitude
import com.senseix21.muminsday.clock.Longitude
import com.senseix21.muminsday.clock.Meters
import com.senseix21.muminsday.clock.Place
import java.time.ZoneId

/**
 * One record of `content/districts.json` exactly as the shell read it. Every field is nullable because this is the
 * untrusted edge: a value of the wrong type in the file arrives here as null. Only [parseDistricts] turns it into a
 * [District].
 */
public data class RawDistrict(
    val id: String?,
    val nameBn: String?,
    val nameEn: String?,
    val latitude: Double?,
    val longitude: Double?,
    val elevation: Double?,
)

/** A stable, never reused, non-blank identifier. Stored in settings, so it must not change between releases. */
@JvmInline
public value class DistrictId private constructor(public val value: String) {
    public companion object {
        /** Null when [value] is blank. */
        public fun of(value: String): DistrictId? = if (value.isBlank()) null else DistrictId(value)
    }
}

/**
 * A place the user can pick. The elevation is fixed per district and never read from GPS (ADR-8, F2 #3). The
 * constructor is internal so a value can only come out of [parseDistricts].
 */
@ConsistentCopyVisibility
public data class District internal constructor(
    val id: DistrictId,
    val nameBn: String,
    val nameEn: String,
    val latitude: Latitude,
    val longitude: Longitude,
    val elevation: Meters,
) {
    /** The input to the prayer-time clock. The zone is an argument because pure code never reads the default (INV-T3). */
    public fun place(zone: ZoneId): Place = Place(latitude, longitude, elevation, zone)
}

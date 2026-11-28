package com.senseix21.muminsday.districts

import com.senseix21.muminsday.clock.Latitude
import com.senseix21.muminsday.clock.Longitude
import com.senseix21.muminsday.clock.Meters

/** Bangladesh's bounding box in degrees, a little generous (ASSUMED from general knowledge, not checked against a source). */
public val BANGLADESH_LATITUDES: ClosedFloatingPointRange<Double> = 20.5..26.7
public val BANGLADESH_LONGITUDES: ClosedFloatingPointRange<Double> = 88.0..92.7

/**
 * Whether a coordinate lies in the rectangle above, edges included. A bounding box is not the border: it contains
 * stretches of India, Myanmar and the Bay of Bengal, so "true" means "plausibly Bangladesh", never "in Bangladesh".
 * It catches a swapped latitude and longitude or a typo in a district record (F1 #6).
 */
public fun isInsideBangladeshBoundingBox(latitude: Latitude, longitude: Longitude): Boolean =
    latitude.degrees in BANGLADESH_LATITUDES && longitude.degrees in BANGLADESH_LONGITUDES

/** Above this a district elevation is a typo, not a hill (ASSUMED: Bangladesh's highest point is about 1,000 m). */
public const val MAX_ELEVATION_METERS: Double = 1100.0

public enum class DistrictField { Id, NameBn, NameEn, Latitude, Longitude, Elevation }

public enum class DistrictProblemKind {
    /** The field is absent, or present with the wrong type. */
    Missing,

    /** A text field with nothing in it. */
    Blank,

    /** Not a finite number. */
    NotFinite,

    /** A number the type refuses: latitude beyond 90, longitude beyond 180, negative or absurd elevation. */
    OutOfRange,

    /** A valid coordinate that is not in Bangladesh's bounding box. */
    OutsideBangladesh,

    /** The same id as an earlier record. Reported on the later one. */
    DuplicateId,

    /** The file has no districts at all. [DistrictProblem.recordIndex] is null. */
    NoDistricts,
}

/** [recordIndex] counts from 0 in file order; it and [field] are null for a problem with the file as a whole. */
public data class DistrictProblem(
    val recordIndex: Int?,
    val recordId: String?,
    val field: DistrictField?,
    val kind: DistrictProblemKind,
)

public sealed interface DistrictCheck {
    /** Every district in file order, each valid, ids unique. */
    public data class Accepted(val districts: List<District>) : DistrictCheck

    /** [problems] is never empty and lists every problem found, so the file is fixed in one pass. */
    public data class Rejected(val problems: List<DistrictProblem>) : DistrictCheck {
        init {
            require(problems.isNotEmpty()) { "Rejected needs a problem; with none the districts are Accepted" }
        }
    }
}

/** The district list's gate: parse at the edge, trust inside. Collects every problem rather than stopping at the first. */
public fun parseDistricts(records: List<RawDistrict>): DistrictCheck {
    val problems = mutableListOf<DistrictProblem>()
    val districts = mutableListOf<District>()
    val seenIds = mutableSetOf<String>()

    if (records.isEmpty()) problems += DistrictProblem(null, null, null, DistrictProblemKind.NoDistricts)

    records.forEachIndexed { index, raw ->
        fun report(field: DistrictField, kind: DistrictProblemKind) {
            problems += DistrictProblem(index, raw.id, field, kind)
        }

        val id = raw.id?.let(DistrictId::of)
        when {
            raw.id == null -> report(DistrictField.Id, DistrictProblemKind.Missing)
            id == null -> report(DistrictField.Id, DistrictProblemKind.Blank)
            !seenIds.add(id.value) -> report(DistrictField.Id, DistrictProblemKind.DuplicateId)
        }
        val nameBn = text(raw.nameBn, DistrictField.NameBn, ::report)
        val nameEn = text(raw.nameEn, DistrictField.NameEn, ::report)

        val latitude = coordinate(raw.latitude, DistrictField.Latitude, BANGLADESH_LATITUDES, Latitude::of, ::report)
        val longitude = coordinate(raw.longitude, DistrictField.Longitude, BANGLADESH_LONGITUDES, Longitude::of, ::report)
        val elevation = number(raw.elevation, DistrictField.Elevation, ::report)?.let { meters ->
            Meters.of(meters)?.takeIf { meters <= MAX_ELEVATION_METERS }
                ?: null.also { report(DistrictField.Elevation, DistrictProblemKind.OutOfRange) }
        }

        // A district is built only when this record raised nothing at all, so a half-valid record never gets through.
        val reportedHere = problems.count { it.recordIndex == index }
        if (reportedHere == 0 && id != null && nameBn != null && nameEn != null &&
            latitude != null && longitude != null && elevation != null
        ) {
            districts += District(id, nameBn, nameEn, latitude, longitude, elevation)
        }
    }

    return if (problems.isEmpty()) DistrictCheck.Accepted(districts) else DistrictCheck.Rejected(problems)
}

private fun text(value: String?, field: DistrictField, report: (DistrictField, DistrictProblemKind) -> Unit): String? = when {
    value == null -> null.also { report(field, DistrictProblemKind.Missing) }
    value.isBlank() -> null.also { report(field, DistrictProblemKind.Blank) }
    else -> value
}

private fun number(value: Double?, field: DistrictField, report: (DistrictField, DistrictProblemKind) -> Unit): Double? = when {
    value == null -> null.also { report(field, DistrictProblemKind.Missing) }
    !value.isFinite() -> null.also { report(field, DistrictProblemKind.NotFinite) }
    else -> value
}

private fun <T> coordinate(
    value: Double?,
    field: DistrictField,
    box: ClosedFloatingPointRange<Double>,
    make: (Double) -> T?,
    report: (DistrictField, DistrictProblemKind) -> Unit,
): T? {
    val degrees = number(value, field, report) ?: return null
    val typed = make(degrees)
    return when {
        typed == null -> null.also { report(field, DistrictProblemKind.OutOfRange) }
        degrees !in box -> null.also { report(field, DistrictProblemKind.OutsideBangladesh) }
        else -> typed
    }
}

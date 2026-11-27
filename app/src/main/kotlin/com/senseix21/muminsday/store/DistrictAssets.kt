package com.senseix21.muminsday.store

import android.content.Context
import com.senseix21.muminsday.districts.DistrictCheck
import com.senseix21.muminsday.districts.RawDistrict
import com.senseix21.muminsday.districts.parseDistricts
import java.io.FileNotFoundException
import org.json.JSONException
import org.json.JSONObject

/** What reading the bundled district list gave. The list never has a made-up fallback: no list means no picker. */
sealed interface DistrictsLoad {
    /** Parsed and valid. */
    data class Loaded(val check: DistrictCheck.Accepted) : DistrictsLoad

    /** The file was read but core's gate refused it. A build gate normally prevents this shipping. */
    data class Invalid(val check: DistrictCheck.Rejected) : DistrictsLoad

    /** There is no `districts.json` asset (Q4: release builds have none until the list is delivered), or it is not JSON of the expected shape. */
    data object Unavailable : DistrictsLoad
}

/**
 * Parse at the edge: the platform's `org.json` (part of android.jar, not a dependency) reads `assets/districts.json`
 * into raw values, and core's [parseDistricts] judges them, as the districts build gate does for the same file. A value
 * of the wrong type is read as missing.
 */
fun loadDistricts(context: Context): DistrictsLoad {
    val text = try {
        context.assets.open(DISTRICTS_ASSET).use { it.readBytes().toString(Charsets.UTF_8) }
    } catch (_: FileNotFoundException) {
        return DistrictsLoad.Unavailable
    }
    val list = try {
        JSONObject(text).optJSONArray("districts")
    } catch (_: JSONException) {
        null
    } ?: return DistrictsLoad.Unavailable

    val records = (0 until list.length()).map { index ->
        val entry = list.optJSONObject(index)
        RawDistrict(
            id = entry?.opt("id") as? String,
            nameBn = entry?.opt("name_bn") as? String,
            nameEn = entry?.opt("name_en") as? String,
            latitude = (entry?.opt("latitude") as? Number)?.toDouble(),
            longitude = (entry?.opt("longitude") as? Number)?.toDouble(),
            elevation = (entry?.opt("elevation_m") as? Number)?.toDouble(),
        )
    }
    return when (val check = parseDistricts(records)) {
        is DistrictCheck.Accepted -> DistrictsLoad.Loaded(check)
        is DistrictCheck.Rejected -> DistrictsLoad.Invalid(check)
    }
}

private const val DISTRICTS_ASSET = "districts.json"

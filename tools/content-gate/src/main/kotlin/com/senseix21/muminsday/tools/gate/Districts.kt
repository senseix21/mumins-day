package com.senseix21.muminsday.tools.gate

import com.senseix21.muminsday.districts.DistrictCheck
import com.senseix21.muminsday.districts.DistrictField
import com.senseix21.muminsday.districts.DistrictProblem
import com.senseix21.muminsday.districts.DistrictProblemKind
import com.senseix21.muminsday.districts.RawDistrict
import com.senseix21.muminsday.districts.parseDistricts
import groovy.json.JsonException
import groovy.json.JsonSlurper

private val DISTRICT_KEYS: Map<DistrictField, String> = mapOf(
    DistrictField.Id to "id",
    DistrictField.NameBn to "name_bn",
    DistrictField.NameEn to "name_en",
    DistrictField.Latitude to "latitude",
    DistrictField.Longitude to "longitude",
    DistrictField.Elevation to "elevation_m",
)

/**
 * The districts gate (ADR-8, F2 #3). [json] is the text of a districts file, or null when it does not exist; [name] is
 * how the build refers to it. Expected shape: `{ "districts": [ { "id": "...", "name_bn": "...", "name_en": "...",
 * "latitude": 23.7, "longitude": 90.4, "elevation_m": 8 } ] }`. Reads the shape; every judgement is core's
 * `parseDistricts`, the same function the app runs on the same file. A field of the wrong type is read as missing.
 */
fun runDistrictGate(json: String?, required: Boolean, name: String): GateReport {
    if (json == null) {
        return if (required) {
            GateReport(false, listOf("districts gate: FAILED. $name does not exist and this build requires it (Q4: the district list is not delivered yet)."))
        } else {
            GateReport(true, listOf("districts gate: no $name yet, nothing to check."))
        }
    }

    fun failed(problems: List<String>) =
        GateReport(false, listOf("districts gate: FAILED. ${problems.size} problem(s) in $name:") + problems.map { "  - $it" })

    val root = try {
        JsonSlurper().parseText(json)
    } catch (e: JsonException) {
        return failed(listOf("the file is not valid JSON: ${e.message?.lineSequence()?.firstOrNull()}"))
    } catch (e: IllegalArgumentException) {
        return failed(listOf("the file is empty"))
    }
    if (root !is Map<*, *>) return failed(listOf("the top level must be an object with \"districts\""))
    val shape = mutableListOf<String>()
    for (key in root.keys) if (key != "districts") shape += "unknown top-level key \"$key\""
    val list = root["districts"]
    if (list !is List<*>) return failed(shape + "\"districts\" must be a list")

    val records = list.mapIndexed { index, entry ->
        if (entry !is Map<*, *>) {
            shape += "districts[$index] must be an object"
            return@mapIndexed null
        }
        for (key in entry.keys) if (key !in DISTRICT_KEYS.values) shape += "districts[$index]: unknown key \"$key\""
        RawDistrict(
            id = entry["id"] as? String,
            nameBn = entry["name_bn"] as? String,
            nameEn = entry["name_en"] as? String,
            latitude = (entry["latitude"] as? Number)?.toDouble(),
            longitude = (entry["longitude"] as? Number)?.toDouble(),
            elevation = (entry["elevation_m"] as? Number)?.toDouble(),
        )
    }
    if (shape.isNotEmpty()) return failed(shape)

    return when (val check = parseDistricts(records.filterNotNull())) {
        is DistrictCheck.Accepted -> GateReport(true, listOf("districts gate: passed. $name: ${check.districts.size} districts."))
        is DistrictCheck.Rejected -> failed(check.problems.map(::describeDistrict))
    }
}

private fun describeDistrict(problem: DistrictProblem): String {
    val where = problem.recordIndex?.let { index ->
        if (problem.recordId.isNullOrBlank()) "districts[$index]" else "districts[$index] (\"${problem.recordId}\")"
    }
    val key = problem.field?.let { "\"${DISTRICT_KEYS.getValue(it)}\"" }
    val what = when (problem.kind) {
        DistrictProblemKind.Missing -> "$key is missing or has the wrong type"
        DistrictProblemKind.Blank -> "$key is empty"
        DistrictProblemKind.NotFinite -> "$key is not a finite number"
        DistrictProblemKind.OutOfRange -> "$key is out of range"
        DistrictProblemKind.OutsideBangladesh -> "$key is outside Bangladesh's bounding box"
        DistrictProblemKind.DuplicateId -> "this id is already used by an earlier record"
        DistrictProblemKind.NoDistricts -> "the file lists no districts"
    }
    return if (where != null) "$where: $what" else what
}

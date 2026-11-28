package com.senseix21.muminsday.tools.gate

import com.senseix21.muminsday.content.ContentKind
import com.senseix21.muminsday.content.FileField
import com.senseix21.muminsday.content.KindCheck
import com.senseix21.muminsday.content.KindProblem
import com.senseix21.muminsday.content.KindProblemKind
import com.senseix21.muminsday.content.NUMERIC_FIELDS
import com.senseix21.muminsday.content.Place
import com.senseix21.muminsday.content.RawKindFile
import com.senseix21.muminsday.content.checkKind
import com.senseix21.muminsday.content.fields
import com.senseix21.muminsday.content.key
import com.senseix21.muminsday.deed.MIN_APPROVED
import groovy.json.JsonException
import groovy.json.JsonSlurper

/**
 * T-BUILD-4 for `content/duas.json` and `content/hadith.json` (INV-C1, INV-C3, INV-C6). [json] is the file's text, or
 * null when it does not exist: until the reviewer delivers it there is nothing to ship, so absence passes with a notice
 * unless [required]. [allowTestFixture] is false for the file that ships and true only for the debug fixture.
 *
 * Expected shape: `{ "version": 1, "test_fixture": false, "sources": ["..."], "duas": [ { "id": "...", ... } ] }`,
 * with `hadith` in place of `duas` for hadith, and the record keys of [key]. This function reads only the shape; every
 * judgement is core's [checkKind], the same one the app runs on the same file.
 */
fun runKindGate(kind: ContentKind, json: String?, required: Boolean, allowTestFixture: Boolean, name: String = "content/${kind.fileName}"): GateReport {
    val label = "${kind.name.lowercase()} gate"
    if (json == null) {
        return if (required) {
            GateReport(false, listOf("$label: FAILED. $name does not exist and this build requires it."))
        } else {
            GateReport(true, listOf("$label: no $name yet, nothing to check."))
        }
    }
    fun failed(problems: List<String>) =
        GateReport(false, listOf("$label: FAILED. ${problems.size} problem(s) in $name:") + problems.map { "  - $it" })

    val root = try {
        JsonSlurper().parseText(json)
    } catch (e: JsonException) {
        return failed(listOf("the file is not valid JSON: ${e.message?.lineSequence()?.firstOrNull()}"))
    } catch (_: IllegalArgumentException) {
        return failed(listOf("the file is empty"))
    }
    if (root !is Map<*, *>) return failed(listOf("the top level must be an object"))

    val shape = mutableListOf<String>()
    val topKeys = setOf("version", "test_fixture", "sources", kind.listKey)
    for (key in root.keys) if (key !in topKeys) shape += "unknown top-level key \"$key\""
    val version = when (val value = root["version"]) {
        null -> null
        is Int -> value
        else -> null.also { shape += "\"version\" must be a whole number" }
    }
    val testFixture = when (val value = root["test_fixture"]) {
        null -> null
        is Boolean -> value
        else -> null.also { shape += "\"test_fixture\" must be true or false" }
    }
    val sources = when (val value = root["sources"]) {
        null -> null
        is List<*> -> value.map { it as? String ?: null.also { shape += "\"sources\" must hold only text in quotes" } }
        else -> null.also { shape += "\"sources\" must be a list" }
    }
    val list = root[kind.listKey] as? List<*> ?: return failed(shape + "\"${kind.listKey}\" must be a list")

    val keyed = kind.fields().associateBy { kind.key(it) }
    val records = list.mapIndexed { index, entry ->
        if (entry !is Map<*, *>) {
            shape += "${kind.listKey}[$index] must be an object"
            return@mapIndexed emptyMap()
        }
        val where = recordLabel(kind, index, entry["id"] as? String)
        for (key in entry.keys) if (key !in keyed.keys) shape += "$where: unknown key \"$key\""
        buildMap {
            for ((key, field) in keyed) {
                when (val value = entry[key]) {
                    null -> Unit
                    is String -> put(field, value)
                    is Int -> if (field in NUMERIC_FIELDS) put(field, value.toString()) else shape += "$where: \"$key\" must be text in quotes"
                    else -> shape += "$where: \"$key\" must be ${if (field in NUMERIC_FIELDS) "a whole number" else "text in quotes"}"
                }
            }
        }
    }
    // A record that is not even an object cannot be judged; stop at the shape so its neighbours keep their numbers.
    if (shape.isNotEmpty()) return failed(shape)

    return when (val check = checkKind(kind, RawKindFile(version, testFixture, sources, records), allowTestFixture)) {
        is KindCheck.Accepted -> GateReport(
            true,
            listOf(
                "$label: passed. $name version ${check.content.version}: ${check.content.approved.size} approved, " +
                    "${check.content.retired.size} retired, ${check.content.sources.size} source(s)" +
                    if (check.content.testFixture) ", TEST FIXTURE (debug only)." else ".",
            ),
        )
        is KindCheck.Rejected -> failed(check.problems.map { describe(kind, it) })
    }
}

private fun recordLabel(kind: ContentKind, index: Int, id: String?): String =
    if (id.isNullOrBlank()) "${kind.listKey}[$index]" else "${kind.listKey}[$index] (\"$id\")"

private fun describe(kind: ContentKind, problem: KindProblem): String {
    val (where, key) = when (val place = problem.place) {
        is Place.InFile -> null to "\"" + when (place.field) {
            FileField.Version -> "version"
            FileField.TestFixture -> "test_fixture"
            FileField.Sources -> "sources"
            FileField.Records -> kind.listKey
        } + "\""
        is Place.InRecord -> recordLabel(kind, place.index, place.id) to place.field?.let { "\"${kind.key(it) ?: it.name}\"" }
    }
    val what = when (problem.kind) {
        KindProblemKind.Missing -> "$key is missing or blank"
        KindProblemKind.Invalid -> "$key has a value that is not allowed"
        KindProblemKind.NotApproved -> "status is \"draft\"; only approved or retired records may be in the file"
        KindProblemKind.StatusMismatch -> "a retired record needs \"retired_in\", and an approved record must not have it"
        KindProblemKind.FromTheFuture -> "$key is later than the file's version"
        KindProblemKind.DuplicateId -> "this id is already used by an earlier record"
        KindProblemKind.TooFewApproved -> "fewer than $MIN_APPROVED approved records"
        KindProblemKind.NotInSources -> "$key names a book that is not in the file's \"sources\" list"
        KindProblemKind.DuplicateSource -> "\"sources\" names the same book twice"
        KindProblemKind.TestFixture -> "\"test_fixture\" is true: a file of fake records cannot ship"
        KindProblemKind.DirectionControl -> "$key holds a directional control character (U+202A-U+202E or U+2066-U+2069)"
    }
    return if (where != null) "$where: $what" else what
}

/** Arguments: the kind (`Dua` or `Hadith`), the file's path, then `true`/`false` for required and for allowing a test fixture. */
object KindMain {
    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size == 4) { "usage: <Dua|Hadith> <path> <required: true|false> <allowTestFixture: true|false>" }
        val kind = ContentKind.valueOf(args[0])
        val file = java.io.File(args[1])
        val text = if (file.isFile) file.readText(Charsets.UTF_8) else null
        val report = runKindGate(kind, text, args[2].toBooleanStrict(), args[3].toBooleanStrict(), "${file.parentFile?.name}/${file.name}")
        report.lines.forEach(::println)
        if (!report.passed) kotlin.system.exitProcess(1)
    }
}


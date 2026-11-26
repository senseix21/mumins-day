package com.senseix21.muminsday.tools.gate

import com.senseix21.muminsday.deed.ContentCheck
import com.senseix21.muminsday.deed.Field
import com.senseix21.muminsday.deed.MAX_TEXT_LENGTH
import com.senseix21.muminsday.deed.MIN_APPROVED
import com.senseix21.muminsday.deed.Problem
import com.senseix21.muminsday.deed.ProblemKind
import com.senseix21.muminsday.deed.RawDeed
import com.senseix21.muminsday.deed.checkContent
import groovy.json.JsonException
import groovy.json.JsonSlurper

/** What the build prints, and whether it may go on. */
data class GateReport(val passed: Boolean, val lines: List<String>)

/** The file's key for each field of a record. One list, so the reader and the messages cannot drift apart. */
private val KEYS: Map<Field, String> = mapOf(
    Field.Id to "id",
    Field.TextBn to "text_bn",
    Field.TextEn to "text_en",
    Field.SourceBook to "source_book",
    Field.SourceNumber to "source_number",
    Field.SourceEdition to "source_edition",
    Field.Grading to "grading",
    Field.Reviewer to "reviewer",
    Field.ReviewDate to "review_date",
    Field.Status to "status",
    Field.AddedIn to "added_in",
    Field.RetiredIn to "retired_in",
)

/** These two are version numbers; the file may write them as JSON integers. Everything else must be a JSON string. */
private val NUMERIC = setOf(Field.AddedIn, Field.RetiredIn)

/**
 * T-BUILD-4. [json] is the text of `content/deeds.json`, or null when the file does not exist.
 *
 * Expected shape: `{ "version": 3, "deeds": [ { "id": "...", "text_bn": "...", ... } ] }`.
 *
 * This function only reads the shape. Every judgement about a record (approved, reviewed, complete, long enough) is
 * the core gate's, the same one the app runs on the same file.
 */
fun runGate(json: String?, required: Boolean): GateReport {
    if (json == null) {
        return if (required) {
            GateReport(false, listOf("content gate: FAILED. content/deeds.json does not exist and this build requires it."))
        } else {
            GateReport(true, listOf("content gate: no content/deeds.json yet, nothing to check."))
        }
    }

    val shape = mutableListOf<String>()
    val root = try {
        JsonSlurper().parseText(json)
    } catch (e: JsonException) {
        return failed(listOf("the file is not valid JSON: ${e.message?.lineSequence()?.firstOrNull()}"))
    } catch (e: IllegalArgumentException) {
        return failed(listOf("the file is empty"))
    }
    if (root !is Map<*, *>) return failed(listOf("the top level must be an object with \"version\" and \"deeds\""))

    for (key in root.keys) if (key != "version" && key != "deeds") shape += "unknown top-level key \"$key\""
    val version = root["version"]
    if (version !is Int) shape += "\"version\" must be a whole number"
    val deeds = root["deeds"]
    if (deeds !is List<*>) shape += "\"deeds\" must be a list"
    if (version !is Int || deeds !is List<*>) return failed(shape)

    val records = deeds.mapIndexed { index, entry ->
        if (entry !is Map<*, *>) {
            shape += "deeds[$index] must be an object"
            return@mapIndexed null
        }
        val label = label(index, entry["id"] as? String)
        for (key in entry.keys) if (key !in KEYS.values) shape += "$label: unknown key \"$key\""
        fun read(field: Field): String? = when (val value = entry[KEYS.getValue(field)]) {
            null -> null
            is String -> value
            is Int -> if (field in NUMERIC) value.toString() else wrongType(shape, label, field, "text in quotes")
            else -> wrongType(shape, label, field, if (field in NUMERIC) "a whole number" else "text in quotes")
        }
        RawDeed(
            id = read(Field.Id),
            textBn = read(Field.TextBn),
            textEn = read(Field.TextEn),
            sourceBook = read(Field.SourceBook),
            sourceNumber = read(Field.SourceNumber),
            sourceEdition = read(Field.SourceEdition),
            grading = read(Field.Grading),
            reviewer = read(Field.Reviewer),
            reviewDate = read(Field.ReviewDate),
            status = read(Field.Status),
            addedIn = read(Field.AddedIn),
            retiredIn = read(Field.RetiredIn),
        )
    }
    // A record that is not even an object cannot be judged; stop at the shape so its neighbours keep their numbers.
    if (shape.isNotEmpty()) return failed(shape)

    return when (val check = checkContent(version, records.filterNotNull())) {
        is ContentCheck.Accepted -> GateReport(
            true,
            listOf(
                "content gate: passed. Version ${check.content.version}: " +
                    "${check.content.approved.size} approved, ${check.content.retired.size} retired.",
            ),
        )
        is ContentCheck.Rejected -> failed(check.problems.map(::describe))
    }
}

private fun wrongType(shape: MutableList<String>, label: String, field: Field, expected: String): String? {
    shape += "$label: \"${KEYS.getValue(field)}\" must be $expected"
    return null
}

private fun failed(problems: List<String>): GateReport =
    GateReport(false, listOf("content gate: FAILED. ${problems.size} problem(s) in content/deeds.json:") + problems.map { "  - $it" })

private fun label(index: Int, id: String?): String = if (id.isNullOrBlank()) "deeds[$index]" else "deeds[$index] (\"$id\")"

private fun describe(problem: Problem): String {
    val where = problem.recordIndex?.let { label(it, problem.recordId) }
    val key = problem.field?.let { "\"${KEYS.getValue(it)}\"" }
    val what = when (problem.kind) {
        ProblemKind.Missing -> "$key is missing or blank"
        ProblemKind.Invalid -> if (key != null) "$key has a value that is not allowed" else "\"version\" must be 1 or more"
        ProblemKind.NotApproved -> "status is \"draft\"; only approved or retired records may be in the file"
        ProblemKind.StatusMismatch -> "a retired record needs \"retired_in\", and an approved record must not have it"
        ProblemKind.FromTheFuture -> "$key is later than the file's version"
        ProblemKind.TooLong -> "$key is longer than $MAX_TEXT_LENGTH characters"
        ProblemKind.DuplicateId -> "this id is already used by an earlier record"
        ProblemKind.TooFewApproved -> "fewer than $MIN_APPROVED approved deeds"
    }
    return if (where != null) "$where: $what" else what
}

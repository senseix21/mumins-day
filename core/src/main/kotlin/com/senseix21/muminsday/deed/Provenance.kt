package com.senseix21.muminsday.deed

import java.time.LocalDate
import java.time.format.DateTimeParseException

/** The twelve fields of a record. */
public enum class Field {
    Id, TextBn, TextEn, SourceBook, SourceNumber, SourceEdition, Grading, Reviewer, ReviewDate, Status, AddedIn, RetiredIn,
}

/** Why a field, or the file as a whole, was refused. Each rule of the gate has its own kind. */
public enum class ProblemKind {
    /** Absent or blank. */
    Missing,

    /** Present but not a legal value: unknown book or status, bad date, bad number, retired before added. */
    Invalid,

    /** Status `draft`: known, but not allowed to ship (INV-C1). */
    NotApproved,

    /** `retired` without `retiredIn`, or `approved` with one. */
    StatusMismatch,

    /** A version number later than the content version being built. */
    FromTheFuture,

    /** More than [MAX_TEXT_LENGTH] code points. */
    TooLong,

    /** The id appeared earlier in the file. Reported on the later record. */
    DuplicateId,

    /** Fewer than [MIN_APPROVED] approved records. */
    TooFewApproved,
}

/**
 * One thing a reviewer must fix. [recordIndex], [recordId] and [field] are null for file-wide problems; [recordId] is
 * the raw id and is null when the record has none.
 */
public data class Problem(val recordIndex: Int?, val recordId: String?, val field: Field?, val kind: ProblemKind)

/** The provenance fields of a record, with no knowledge of what the record is about. */
internal data class RawProvenance(
    val id: String?,
    val sourceBook: String?,
    val sourceNumber: String?,
    val sourceEdition: String?,
    val grading: String?,
    val reviewer: String?,
    val reviewDate: String?,
    val status: String?,
    val addedIn: String?,
    val retiredIn: String?,
)

/** Provenance that passed the gate. [id] is non-blank. */
internal data class Provenance(
    val id: String,
    val source: Source,
    val review: Review,
    val status: Status,
    val addedIn: Int,
    val retiredIn: Int?,
)

internal data class FieldProblem(val field: Field, val kind: ProblemKind)

internal sealed interface Checked<out T> {
    data class Ok<T>(val value: T) : Checked<T>
    data class Bad(val problems: List<FieldProblem>) : Checked<Nothing>
}

/**
 * INV-C1 and INV-C3 for any kind of reviewed content: source, grading, reviewer, review date, status and versions. It
 * knows nothing about deed text, so duas and daily hadith can reuse it. Collects every problem; never stops at the first.
 */
internal fun checkProvenance(version: Int, raw: RawProvenance): Checked<Provenance> {
    val problems = mutableListOf<FieldProblem>()
    fun report(field: Field, kind: ProblemKind) {
        problems += FieldProblem(field, kind)
    }

    fun required(field: Field, value: String?): String? =
        if (value.isNullOrBlank()) {
            report(field, ProblemKind.Missing)
            null
        } else {
            value
        }

    fun versionNumber(field: Field, value: String): Int? {
        val number = value.toIntOrNull()
        if (number == null || number < 1) {
            report(field, ProblemKind.Invalid)
            return null
        }
        if (number > version) report(field, ProblemKind.FromTheFuture)
        return number
    }

    val id = required(Field.Id, raw.id)
    val bookText = required(Field.SourceBook, raw.sourceBook)
    val number = required(Field.SourceNumber, raw.sourceNumber)
    val edition = required(Field.SourceEdition, raw.sourceEdition)
    val grading = required(Field.Grading, raw.grading)
    val reviewer = required(Field.Reviewer, raw.reviewer)
    val dateText = required(Field.ReviewDate, raw.reviewDate)
    val statusText = required(Field.Status, raw.status)
    val addedText = required(Field.AddedIn, raw.addedIn)
    // retiredIn may be absent; a blank value counts as absent. Whether that is allowed depends on the status.
    val retiredText = raw.retiredIn?.takeUnless { it.isBlank() }

    val book = bookText?.let { text ->
        SourceBook.entries.firstOrNull { it.name == text } ?: null.also { report(Field.SourceBook, ProblemKind.Invalid) }
    }
    val date = dateText?.let { text ->
        try {
            LocalDate.parse(text)
        } catch (_: DateTimeParseException) {
            report(Field.ReviewDate, ProblemKind.Invalid)
            null
        }
    }
    val status = statusText?.let { text ->
        when (text) {
            "approved" -> Status.Approved
            "retired" -> Status.Retired
            "draft" -> null.also { report(Field.Status, ProblemKind.NotApproved) }
            else -> null.also { report(Field.Status, ProblemKind.Invalid) }
        }
    }
    val added = addedText?.let { versionNumber(Field.AddedIn, it) }
    val retired = retiredText?.let { versionNumber(Field.RetiredIn, it) }
    if (added != null && retired != null && retired < added) report(Field.RetiredIn, ProblemKind.Invalid)

    // Only a known status can disagree with retiredIn; draft and unknown values are already reported.
    when (status) {
        Status.Retired -> if (retiredText == null) report(Field.RetiredIn, ProblemKind.StatusMismatch)
        Status.Approved -> if (retiredText != null) report(Field.RetiredIn, ProblemKind.StatusMismatch)
        null -> Unit
    }

    if (problems.isNotEmpty() || id == null || book == null || number == null || edition == null || grading == null ||
        reviewer == null || date == null || status == null || added == null
    ) {
        return Checked.Bad(problems)
    }
    return Checked.Ok(
        Provenance(id, Source(book, number, edition), Review(reviewer, date, grading), status, added, retired),
    )
}

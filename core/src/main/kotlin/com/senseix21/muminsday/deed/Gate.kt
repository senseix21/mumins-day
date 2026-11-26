package com.senseix21.muminsday.deed

/** Most Unicode code points in one deed text (target, F4 data model). Code points, not UTF-16 units. */
public const val MAX_TEXT_LENGTH: Int = 90

/** Fewest approved deeds a content file may ship with: a month without a repeat (G5, Q9). */
public const val MIN_APPROVED: Int = 30

public sealed interface ContentCheck {
    public data class Accepted(val content: DeedContent) : ContentCheck

    /** [problems] is never empty and lists every problem found, so the reviewer fixes them in one pass. */
    public class Rejected internal constructor(public val problems: List<Problem>) : ContentCheck {
        init {
            require(problems.isNotEmpty()) { "Rejected needs a problem; with none the content is Accepted" }
        }

        override fun equals(other: Any?): Boolean = other is Rejected && other.problems == problems

        override fun hashCode(): Int = problems.hashCode()

        override fun toString(): String = "Rejected($problems)"
    }
}

/**
 * The review gate (INV-C1, INV-C3, T-BUILD-4). [version] is the content version being built. Parse at the edge, trust
 * inside: an [ContentCheck.Accepted] holds only reviewed deeds, in file order.
 */
public fun checkContent(version: Int, records: List<RawDeed>): ContentCheck {
    val problems = mutableListOf<Problem>()
    if (version < 1) problems += Problem(null, null, null, ProblemKind.Invalid)

    val approved = mutableListOf<Deed>()
    val retired = mutableListOf<Deed>()
    val seenIds = mutableSetOf<String>()

    records.forEachIndexed { index, raw ->
        fun report(found: FieldProblem) {
            problems += Problem(index, raw.id, found.field, found.kind)
        }

        if (!raw.id.isNullOrBlank() && !seenIds.add(raw.id)) report(FieldProblem(Field.Id, ProblemKind.DuplicateId))

        when (val checked = checkDeed(version, raw)) {
            is Checked.Ok -> when (checked.value.status) {
                Status.Approved -> approved += checked.value
                Status.Retired -> retired += checked.value
            }
            is Checked.Bad -> checked.problems.forEach(::report)
        }
    }

    // Counted from the raw status so a record with one other fault is not also reported as a missing approval.
    val approvedCount = records.count { it.status == "approved" }
    if (approvedCount < MIN_APPROVED) problems += Problem(null, null, null, ProblemKind.TooFewApproved)

    return if (problems.isEmpty()) {
        ContentCheck.Accepted(DeedContent(version, approved, retired))
    } else {
        ContentCheck.Rejected(problems)
    }
}

/** The deed-specific part of the gate: the two texts, then the shared provenance check. */
internal fun checkDeed(version: Int, raw: RawDeed): Checked<Deed> {
    val problems = mutableListOf<FieldProblem>()
    fun text(field: Field, value: String?): String? {
        if (value.isNullOrBlank()) {
            problems += FieldProblem(field, ProblemKind.Missing)
            return null
        }
        if (value.codePointCount(0, value.length) > MAX_TEXT_LENGTH) problems += FieldProblem(field, ProblemKind.TooLong)
        return value
    }

    val textBn = text(Field.TextBn, raw.textBn)
    val textEn = text(Field.TextEn, raw.textEn)

    val provenance = when (val checked = checkProvenance(version, raw.provenance())) {
        is Checked.Ok -> checked.value
        is Checked.Bad -> {
            problems += checked.problems
            null
        }
    }
    val id = provenance?.let { DeedId.of(it.id) }

    if (problems.isNotEmpty() || provenance == null || id == null || textBn == null || textEn == null) {
        return Checked.Bad(problems)
    }
    return Checked.Ok(
        Deed(id, textBn, textEn, provenance.source, provenance.review, provenance.status, provenance.addedIn, provenance.retiredIn),
    )
}

private fun RawDeed.provenance() = RawProvenance(
    id = id,
    sourceBook = sourceBook,
    sourceNumber = sourceNumber,
    sourceEdition = sourceEdition,
    grading = grading,
    reviewer = reviewer,
    reviewDate = reviewDate,
    status = status,
    addedIn = addedIn,
    retiredIn = retiredIn,
)

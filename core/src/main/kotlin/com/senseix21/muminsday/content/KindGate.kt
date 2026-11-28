package com.senseix21.muminsday.content

import com.senseix21.muminsday.deed.Checked
import com.senseix21.muminsday.deed.Field
import com.senseix21.muminsday.deed.MIN_APPROVED
import com.senseix21.muminsday.deed.ProblemKind
import com.senseix21.muminsday.deed.RawProvenance
import com.senseix21.muminsday.deed.Status
import com.senseix21.muminsday.deed.checkProvenanceOf

/** The file-level keys of a kind's file. */
public enum class FileField { Version, TestFixture, Sources, Records }

/** Where a problem is: the file as a whole, or one record (and, if known, one of its fields). */
public sealed interface Place {
    public data class InFile(val field: FileField) : Place

    /** [id] is the record's raw id, null when it has none; [field] is null for a problem with the record as a whole. */
    public data class InRecord(val index: Int, val id: String?, val field: KindField?) : Place
}

/** Why something was refused. Each rule of the gate has its own kind. */
public enum class KindProblemKind {
    /** Absent or blank. */
    Missing,

    /** Present but not a legal value: a bad date or number, retired before added, a field this kind does not have. */
    Invalid,

    /** Status `draft`: known, but not allowed to ship (INV-C1). */
    NotApproved,

    /** `retired` without `retired_in`, or `approved` with one. */
    StatusMismatch,

    /** A version number later than the file's version. */
    FromTheFuture,

    /** The id appeared earlier in the file. Reported on the later record. */
    DuplicateId,

    /** Fewer than [MIN_APPROVED] approved records. */
    TooFewApproved,

    /** The record cites a book that is not in the file's reviewed `sources` list (ADR-22). */
    NotInSources,

    /** The `sources` list names the same book twice. */
    DuplicateSource,

    /** The file says it is a test fixture, and this gate is for a file that ships (INV-C6). */
    TestFixture,

    /**
     * A text holds a directional embedding or isolate control. The screen wraps Arabic in an isolate of its own
     * (INV-L3); a stray control inside the text could close it early, so the text must come without them.
     */
    DirectionControl,
}

public data class KindProblem(val place: Place, val kind: KindProblemKind)

public sealed interface KindCheck {
    public data class Accepted(val content: KindContent) : KindCheck

    /** [problems] is never empty and lists every problem found, so the reviewer fixes them in one pass. */
    public class Rejected internal constructor(public val problems: List<KindProblem>) : KindCheck {
        init {
            require(problems.isNotEmpty()) { "Rejected needs a problem; with none the content is Accepted" }
        }

        override fun equals(other: Any?): Boolean = other is Rejected && other.problems == problems

        override fun hashCode(): Int = problems.hashCode()

        override fun toString(): String = "Rejected($problems)"
    }
}

/**
 * T-BUILD-4 for a dua or hadith file (INV-C1, INV-C3, INV-C6). The provenance of every record goes through the same
 * check as deeds; only the texts and the list of allowed books are the kind's own. [allowTestFixture] is false for the
 * file that ships and true only for the debug fixture. Parse at the edge, trust inside: an [KindCheck.Accepted] holds
 * only reviewed records, in file order.
 */
public fun checkKind(kind: ContentKind, file: RawKindFile, allowTestFixture: Boolean): KindCheck {
    val problems = mutableListOf<KindProblem>()
    fun inFile(field: FileField, problem: KindProblemKind) {
        problems += KindProblem(Place.InFile(field), problem)
    }

    val version = file.version
    if (version == null) inFile(FileField.Version, KindProblemKind.Missing) else if (version < 1) inFile(FileField.Version, KindProblemKind.Invalid)

    when (file.testFixture) {
        null -> inFile(FileField.TestFixture, KindProblemKind.Missing)
        true -> if (!allowTestFixture) inFile(FileField.TestFixture, KindProblemKind.TestFixture)
        false -> Unit
    }

    val sources = mutableListOf<String>()
    if (file.sources.isNullOrEmpty()) inFile(FileField.Sources, KindProblemKind.Missing)
    for (source in file.sources.orEmpty()) {
        when {
            source.isNullOrBlank() -> inFile(FileField.Sources, KindProblemKind.Missing)
            source in sources -> inFile(FileField.Sources, KindProblemKind.DuplicateSource)
            else -> sources += source
        }
    }

    val approved = mutableListOf<Reviewed>()
    val retired = mutableListOf<Reviewed>()
    val seenIds = mutableSetOf<String>()
    file.records.forEachIndexed { index, fields ->
        val id = fields[KindField.Id]
        fun report(field: KindField?, problem: KindProblemKind) {
            problems += KindProblem(Place.InRecord(index, id, field), problem)
        }
        if (!id.isNullOrBlank() && !seenIds.add(id)) report(KindField.Id, KindProblemKind.DuplicateId)
        // version 1 stands in for a missing or bad version, which is already reported once for the file.
        checkRecord(kind, version?.takeIf { it >= 1 } ?: 1, sources, fields, ::report)?.let { record ->
            when (record.status) {
                Status.Approved -> approved += record
                Status.Retired -> retired += record
            }
        }
    }

    // Counted from the raw status so a record with one other fault is not also reported as a missing approval.
    if (file.records.count { it[KindField.Status] == "approved" } < MIN_APPROVED) inFile(FileField.Records, KindProblemKind.TooFewApproved)

    return if (problems.isEmpty() && version != null && file.testFixture != null) {
        KindCheck.Accepted(KindContent(kind, version, file.testFixture, sources, approved, retired))
    } else {
        KindCheck.Rejected(problems)
    }
}

/** One record: its texts, then the shared provenance check. Null when anything was reported. */
private fun checkRecord(
    kind: ContentKind,
    version: Int,
    sources: List<String>,
    fields: Map<KindField, String>,
    report: (KindField?, KindProblemKind) -> Unit,
): Reviewed? {
    var bad = false
    for (field in fields.keys) {
        if (kind.key(field) == null) {
            report(field, KindProblemKind.Invalid)
            bad = true
        }
    }
    fun text(field: KindField): String? {
        val value = fields[field]
        if (value.isNullOrBlank()) {
            report(field, KindProblemKind.Missing)
            bad = true
            return null
        }
        if (value.any { it in DIRECTION_CONTROLS }) {
            report(field, KindProblemKind.DirectionControl)
            bad = true
        }
        return value
    }

    val arabic = text(KindField.TextAr)
    val bengali = text(KindField.TextBn)
    val english = text(KindField.TextEn)
    val body = when (kind) {
        ContentKind.Dua -> {
            val transliteration = text(KindField.Transliteration)
            val occasion = text(KindField.Occasion)
            if (arabic != null && bengali != null && english != null && transliteration != null && occasion != null) {
                Body.DuaBody(arabic, transliteration, bengali, english, occasion)
            } else {
                null
            }
        }
        ContentKind.Hadith ->
            if (arabic != null && bengali != null && english != null) Body.HadithBody(arabic, bengali, english) else null
    }

    val raw = RawProvenance(
        id = fields[KindField.Id],
        sourceBook = fields[KindField.SourceBook],
        sourceNumber = fields[KindField.SourceNumber],
        sourceEdition = fields[KindField.SourceEdition],
        grading = fields[KindField.Grading],
        reviewer = fields[KindField.Reviewer],
        reviewDate = fields[KindField.ReviewDate],
        status = fields[KindField.Status],
        addedIn = fields[KindField.AddedIn],
        retiredIn = fields[KindField.RetiredIn],
    )
    val provenance = when (val checked = checkProvenanceOf(version, raw) { book -> book.takeIf { it in sources } }) {
        is Checked.Ok -> checked.value
        is Checked.Bad -> {
            for (found in checked.problems) report(kindField(found.field), kindProblem(found.field, found.kind))
            null
        }
    }
    val id = provenance?.let { RecordId.of(it.id) }
    if (bad || body == null || provenance == null || id == null) return null
    return Reviewed(
        id, body, Citation(provenance.book, provenance.number, provenance.edition), provenance.review,
        provenance.status, provenance.addedIn, provenance.retiredIn,
    )
}

/** The provenance check reports in the deed's field names; these are the same fields of a dua or hadith record. */
private fun kindField(field: Field): KindField = when (field) {
    Field.Id -> KindField.Id
    Field.TextBn -> KindField.TextBn
    Field.TextEn -> KindField.TextEn
    Field.SourceBook -> KindField.SourceBook
    Field.SourceNumber -> KindField.SourceNumber
    Field.SourceEdition -> KindField.SourceEdition
    Field.Grading -> KindField.Grading
    Field.Reviewer -> KindField.Reviewer
    Field.ReviewDate -> KindField.ReviewDate
    Field.Status -> KindField.Status
    Field.AddedIn -> KindField.AddedIn
    Field.RetiredIn -> KindField.RetiredIn
}

private fun kindProblem(field: Field, problem: ProblemKind): KindProblemKind = when (problem) {
    ProblemKind.Missing -> KindProblemKind.Missing
    // For a dua or hadith the only "not allowed" book is one the reviewer's list does not name.
    ProblemKind.Invalid -> if (field == Field.SourceBook) KindProblemKind.NotInSources else KindProblemKind.Invalid
    ProblemKind.NotApproved -> KindProblemKind.NotApproved
    ProblemKind.StatusMismatch -> KindProblemKind.StatusMismatch
    ProblemKind.FromTheFuture -> KindProblemKind.FromTheFuture
    ProblemKind.DuplicateId -> KindProblemKind.DuplicateId
    ProblemKind.TooFewApproved -> KindProblemKind.TooFewApproved
    // The provenance check measures no text; a length rule, if the reviewer wants one, would be its own kind.
    ProblemKind.TooLong -> KindProblemKind.Invalid
}

/** U+202A to U+202E (embeddings and overrides) and U+2066 to U+2069 (isolates). */
private val DIRECTION_CONTROLS: Set<Char> = (0x202A..0x202E).map(Int::toChar).toSet() + (0x2066..0x2069).map(Int::toChar)

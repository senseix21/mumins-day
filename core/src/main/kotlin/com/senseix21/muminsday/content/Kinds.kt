package com.senseix21.muminsday.content

import com.senseix21.muminsday.deed.Review
import com.senseix21.muminsday.deed.Status

/**
 * The reviewed content kinds after deeds (INV-C6, ADR-22). Deeds keep their own v1 gate; both gates share the one
 * provenance check. [fileName] is the file in `content/` and the asset name; [listKey] is the file's list of records.
 */
public enum class ContentKind(public val fileName: String, public val listKey: String) {
    Dua("duas.json", "duas"),
    Hadith("hadith.json", "hadith"),
}

/** Every field a record of any kind can have. Which ones a kind has, and their keys in the file, is [key]. */
public enum class KindField {
    Id, TextAr, Transliteration, TextBn, TextEn, Occasion,
    SourceBook, SourceNumber, SourceEdition, Grading, Reviewer, ReviewDate, Status, AddedIn, RetiredIn,
}

/**
 * The file's key for [field] in this kind's records, or null when the kind has no such field. One table, read by both
 * parsers (the build's Groovy reader and the app's `org.json`), so the two cannot disagree about a name. The record
 * shapes are the ones `PLAN.md` phase 13 lists: a dua has Arabic, transliteration, Bengali, English, source and
 * occasion; a hadith has Arabic, Bengali, English, collection, number, edition and grading. Both carry the reviewer's
 * sign-off, and a dua carries a number, an edition and a grading as well, because INV-C6 asks every kind for them.
 */
public fun ContentKind.key(field: KindField): String? = when (this) {
    ContentKind.Dua -> when (field) {
        KindField.Id -> "id"
        KindField.TextAr -> "text_ar"
        KindField.Transliteration -> "transliteration"
        KindField.TextBn -> "text_bn"
        KindField.TextEn -> "text_en"
        KindField.Occasion -> "occasion"
        KindField.SourceBook -> "source"
        KindField.SourceNumber -> "source_number"
        KindField.SourceEdition -> "source_edition"
        KindField.Grading -> "grading"
        KindField.Reviewer -> "reviewer"
        KindField.ReviewDate -> "review_date"
        KindField.Status -> "status"
        KindField.AddedIn -> "added_in"
        KindField.RetiredIn -> "retired_in"
    }
    ContentKind.Hadith -> when (field) {
        KindField.Id -> "id"
        KindField.TextAr -> "text_ar"
        KindField.Transliteration -> null
        KindField.TextBn -> "text_bn"
        KindField.TextEn -> "text_en"
        KindField.Occasion -> null
        KindField.SourceBook -> "collection"
        KindField.SourceNumber -> "number"
        KindField.SourceEdition -> "edition"
        KindField.Grading -> "grading"
        KindField.Reviewer -> "reviewer"
        KindField.ReviewDate -> "review_date"
        KindField.Status -> "status"
        KindField.AddedIn -> "added_in"
        KindField.RetiredIn -> "retired_in"
    }
}

/** The fields this kind's records have, in file order. */
public fun ContentKind.fields(): List<KindField> = KindField.entries.filter { key(it) != null }

/** Version numbers; a file may write them as JSON integers. Every other field is a JSON string. */
public val NUMERIC_FIELDS: Set<KindField> = setOf(KindField.AddedIn, KindField.RetiredIn)

/**
 * One file exactly as a parser read it: the untrusted edge. [records] map each field present to its text; a field
 * absent from the map was absent from the file (or of the wrong type, which the gate also treats as missing).
 * [sources] is the reviewer's list of the books this kind may cite (ADR-22): the app knows no book names of its own.
 */
public data class RawKindFile(
    val version: Int?,
    val testFixture: Boolean?,
    val sources: List<String?>?,
    val records: List<Map<KindField, String>>,
)

/** A stable, never reused, non-blank identifier. */
@JvmInline
public value class RecordId private constructor(public val value: String) {
    public companion object {
        /** Null when [value] is blank. */
        public fun of(value: String): RecordId? = if (value.isBlank()) null else RecordId(value)
    }
}

/** The words of a record, which differ by kind. Every field is the reviewed text, shown as it is. */
public sealed interface Body {
    public val arabic: String
    public val bengali: String
    public val english: String

    public data class DuaBody(
        override val arabic: String,
        val transliteration: String,
        override val bengali: String,
        override val english: String,
        val occasion: String,
    ) : Body

    public data class HadithBody(
        override val arabic: String,
        override val bengali: String,
        override val english: String,
    ) : Body
}

/** Where the record comes from: [book] is one of the file's own reviewed `sources`; [number] is as numbered in [edition]. */
public data class Citation(val book: String, val number: String, val edition: String)

/**
 * A reviewed record. The constructor is internal, so holding one is the proof that it passed [checkKind]: it has a
 * citation from the reviewer's list, a grading as the reviewer wrote it, a reviewer, a review date and a shippable
 * status (INV-C1, INV-C6).
 */
@ConsistentCopyVisibility
public data class Reviewed internal constructor(
    val id: RecordId,
    val body: Body,
    val citation: Citation,
    val review: Review,
    val status: Status,
    val addedIn: Int,
    val retiredIn: Int?,
)

/**
 * One kind's file as the gate accepted it. [approved] is in file order, which is the rotation order (INV-C2);
 * [retired] is kept for the audit trail and never shown. [testFixture] is true only for the debug fixture: the gate for
 * the real file refuses one, and the screen shows a banner when it is true.
 */
public class KindContent internal constructor(
    public val kind: ContentKind,
    public val version: Int,
    public val testFixture: Boolean,
    public val sources: List<String>,
    public val approved: List<Reviewed>,
    public val retired: List<Reviewed>,
) {
    init {
        require(approved.size >= MIN_KIND_ROTATION) { "rotation needs at least $MIN_KIND_ROTATION records, got ${approved.size}" }
    }

    override fun toString(): String = "KindContent($kind, version=$version, approved=${approved.size}, retired=${retired.size})"
}

/** The record of the day (INV-C2), by the same rule as deeds. */
public fun KindContent.forDay(date: java.time.LocalDate): Reviewed = pickForDay(date, approved)

/** The floor for "half a cycle away" to differ from yesterday, today and tomorrow (G4). */
internal const val MIN_KIND_ROTATION: Int = 4

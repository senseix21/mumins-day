package com.senseix21.muminsday.deed

import java.time.LocalDate

/**
 * One record of `content/deeds.json` exactly as the shell parsed it. Every field is a nullable String because this is
 * the untrusted edge: nothing here is known to be present, well formed or reviewed. Only [checkContent] turns it into
 * a [Deed].
 */
public data class RawDeed(
    val id: String?,
    val textBn: String?,
    val textEn: String?,
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

/** A stable, never reused, non-blank identifier. */
@JvmInline
public value class DeedId private constructor(public val value: String) {
    public companion object {
        /** Null when [value] is blank. */
        public fun of(value: String): DeedId? = if (value.isBlank()) null else DeedId(value)
    }
}

/** v1 allows only these two books (F4 data model). */
public enum class SourceBook { Bukhari, Muslim }

/** Only these two exist as typed values: a `draft` record never becomes one (INV-C1). */
public enum class Status { Approved, Retired }

/** [number] is as numbered in [edition]; numbering differs between editions, so the edition is part of the source. */
public data class Source(val book: SourceBook, val number: String, val edition: String)

/** The reviewer's sign-off, as recorded; [grading] is the reviewer's text, never ours. */
public data class Review(val reviewer: String, val date: LocalDate, val grading: String)

/**
 * A reviewed deed. The constructor is internal so a value can only come out of the gate in [checkContent] (INV-C1,
 * INV-C3): holding a [Deed] is the proof that it has a source, a review and a status of approved or retired.
 */
@ConsistentCopyVisibility
public data class Deed internal constructor(
    val id: DeedId,
    val textBn: String,
    val textEn: String,
    val source: Source,
    val review: Review,
    val status: Status,
    val addedIn: Int,
    val retiredIn: Int?,
)

/**
 * The content of one version of the deed file, as the gate accepted it. [approved] is in file order, which is the
 * rotation order (INV-C2). [retired] is kept for audit and is never shown.
 */
public class DeedContent internal constructor(
    public val version: Int,
    public val approved: List<Deed>,
    public val retired: List<Deed>,
) {
    init {
        require(approved.size >= MIN_ROTATION) { "rotation needs at least $MIN_ROTATION deeds, got ${approved.size}" }
    }

    /**
     * A plain lookup over approved deeds only. Null is a real answer: the id is not in rotation (retired, or no longer
     * in the file), and the card for it must be recomputed (G5).
     */
    public fun find(id: DeedId): Deed? = approved.firstOrNull { it.id == id }

    override fun equals(other: Any?): Boolean =
        other is DeedContent && other.version == version && other.approved == approved && other.retired == retired

    override fun hashCode(): Int = 31 * (31 * version + approved.hashCode()) + retired.hashCode()

    override fun toString(): String = "DeedContent(version=$version, approved=${approved.size}, retired=${retired.size})"
}

/** The mathematical floor for "show me another" to differ from yesterday, today and tomorrow (G4). */
internal const val MIN_ROTATION: Int = 4

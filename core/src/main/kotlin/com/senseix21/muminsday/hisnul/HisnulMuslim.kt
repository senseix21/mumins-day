package com.senseix21.muminsday.hisnul

import com.senseix21.muminsday.quran.Json
import com.senseix21.muminsday.quran.JsonParse
import com.senseix21.muminsday.quran.parseJson

/**
 * Hisnul Muslim as served by the ThelightHub dua API (ADR-27): unreviewed, kept on the developer's machine only
 * (`content/local/`), packaged into debug builds only. Every text is the source's string exactly as decoded from its
 * JSON: nothing here trims, normalises, converts digits or line endings, or merges fields. A field the source sent as
 * `null` is null here, and the screen shows nothing for it.
 */
@JvmInline
public value class ChapterId(public val value: Int)

@JvmInline
public value class DuaId(public val value: Int)

/** One chapter of the book: its number as the source gives it (0 to 132), its Bengali name, and how many duas it has. */
public data class Chapter(val id: ChapterId, val nameBn: String, val duaCount: Int)

/**
 * One dua. [number] is its place in its chapter as the source numbers it; the source gives none (null) when the chapter
 * has a single dua. [nameBn] is the source's `duaname`.
 */
public data class Dua(val globalId: DuaId, val chapter: ChapterId, val number: Int?, val nameBn: String, val segments: List<Segment>)

/**
 * One part of a dua, each field the source's own: [before] (`top`, a note before the text), [arabic], [transliteration]
 * (Bengali letters), [translation] (Bengali, `translations`), [after] (`bottom`, a note after), [reference].
 */
public data class Segment(
    val before: String?,
    val arabic: String?,
    val transliteration: String?,
    val translation: String?,
    val after: String?,
    val reference: String?,
)

/** The whole collection: chapters in the source's order, and each chapter's duas in their source order. */
public data class HisnulMuslim(val chapters: List<Chapter>, val duas: List<Dua>) {
    private val byChapter: Map<ChapterId, List<Dua>> = duas.groupBy { it.chapter }

    public fun duasOf(chapter: ChapterId): List<Dua> = byChapter[chapter].orEmpty()

    public fun chapter(id: ChapterId): Chapter? = chapters.firstOrNull { it.id == id }

    public val segmentCount: Int get() = duas.sumOf { it.segments.size }
}

public sealed interface HisnulCheck {
    public data class Accepted(val collection: HisnulMuslim) : HisnulCheck

    /** Every reason found, each naming its file. Nothing of a refused collection is shown. */
    public data class Refused(val reasons: List<String>) : HisnulCheck
}

/** The book the files must belong to: the API's `book_id` 1, Hisnul Muslim (KNOWN, `books.json` of the fetch). */
public const val HISNUL_MUSLIM_BOOK: Int = 1

/** The chapter list's file and the duas' folder, relative to `content/local/hisnul-muslim/` and to the debug assets. */
public const val CHAPTERS_FILE: String = "b1-chapters.json"
public const val DUAS_FOLDER: String = "duas"

/**
 * Reads the chapter list ([chaptersText], the `b1-chapters.json` response) and every dua response ([duaTexts], by file
 * name such as `2.json`) into one [HisnulMuslim], or refuses with every reason. A dua file must be named after its own
 * `dua_global_id`, belong to book 1 and a listed chapter, and have a segment with a text to show (a note, Arabic or a translation);
 * each chapter must hold exactly the number of duas the list says, and no two duas may share a global id or a number in
 * their chapter. A text may not carry a direction control (U+202A to U+202E, U+2066 to U+2069), so the isolate the
 * screen adds (INV-L3) cannot be closed early by the source.
 */
public fun checkHisnulMuslim(chaptersText: String, duaTexts: Map<String, String>): HisnulCheck {
    val reasons = mutableListOf<String>()
    val chapters = when (val parsed = parseChapters(chaptersText)) {
        is Parsed.Ok -> parsed.value
        is Parsed.Bad -> return HisnulCheck.Refused(listOf("$CHAPTERS_FILE: ${parsed.reason}"))
    }
    val duas = mutableListOf<Dua>()
    for ((name, text) in duaTexts.toSortedMap(compareBy({ it.length }, { it }))) {
        when (val parsed = parseDua(text)) {
            is Parsed.Bad -> reasons += "$DUAS_FOLDER/$name: ${parsed.reason}"
            is Parsed.Ok -> {
                val dua = parsed.value
                if (name != "${dua.globalId.value}.json") reasons += "$DUAS_FOLDER/$name: holds dua_global_id ${dua.globalId.value}"
                duas += dua
            }
        }
    }
    val known = chapters.map { it.id }.toSet()
    duas.groupBy { it.globalId }.filterValues { it.size > 1 }.keys.forEach { reasons += "dua_global_id ${it.value} appears more than once" }
    duas.filter { it.chapter !in known }.forEach { reasons += "dua ${it.globalId.value}: chapter ${it.chapter.value} is not in $CHAPTERS_FILE" }
    val byChapter = duas.groupBy { it.chapter }
    for (chapter in chapters) {
        val found = byChapter[chapter.id].orEmpty()
        if (found.size != chapter.duaCount) reasons += "chapter ${chapter.id.value}: $CHAPTERS_FILE says ${chapter.duaCount} dua(s), the files hold ${found.size}"
        found.mapNotNull { it.number }.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
            .forEach { reasons += "chapter ${chapter.id.value}: dua number $it appears more than once" }
        if (found.size > 1 && found.any { it.number == null }) reasons += "chapter ${chapter.id.value}: a dua without a number in a chapter of ${found.size}"
    }
    if (reasons.isNotEmpty()) return HisnulCheck.Refused(reasons)
    // The source's order: chapters as listed; in a chapter, by the number the source gives (its global ids are not in
    // that order for every chapter, KNOWN: chapter 4 lists dua 2 before dua 1 by global id).
    val ordered = chapters.flatMap { chapter -> byChapter[chapter.id].orEmpty().sortedWith(compareBy({ it.number ?: 0 }, { it.globalId.value })) }
    return HisnulCheck.Accepted(HisnulMuslim(chapters, ordered))
}

/**
 * The chapters whose Bengali name contains [typed], in their order. Plain containment of the typed characters, after
 * trimming the typed text only; the names are never changed. An empty query is every chapter.
 */
public fun chaptersMatching(collection: HisnulMuslim, typed: String): List<Chapter> {
    val query = typed.trim()
    if (query.isEmpty()) return collection.chapters
    return collection.chapters.filter { query in it.nameBn }
}

private sealed interface Parsed<out T> {
    data class Ok<T>(val value: T) : Parsed<T>

    data class Bad(val reason: String) : Parsed<Nothing>
}

private fun parseChapters(text: String): Parsed<List<Chapter>> {
    val root = when (val json = parseJson(text)) {
        is JsonParse.Parsed -> json.value as? Json.Obj ?: return Parsed.Bad("not a JSON object")
        is JsonParse.Refused -> return Parsed.Bad("not JSON: ${json.reason}")
    }
    if (root.fields["success"] != Json.Bool(true)) return Parsed.Bad("\"success\" is not true")
    if (root.int("book_id") != HISNUL_MUSLIM_BOOK) return Parsed.Bad("\"book_id\" is not $HISNUL_MUSLIM_BOOK")
    val list = root.fields["data"] as? Json.Arr ?: return Parsed.Bad("\"data\" is not a list")
    val chapters = mutableListOf<Chapter>()
    list.items.forEachIndexed { index, item ->
        val entry = item as? Json.Obj ?: return Parsed.Bad("chapter entry $index is not an object")
        val id = entry.int("chap_id") ?: return Parsed.Bad("chapter entry $index has no whole-number \"chap_id\"")
        val name = entry.text("chapname") ?: return Parsed.Bad("chapter ${id}: \"chapname\" is not a text")
        val count = entry.int("dua_count")?.takeIf { it >= 1 } ?: return Parsed.Bad("chapter $id: \"dua_count\" is not a positive whole number")
        if (name.isBlank()) return Parsed.Bad("chapter $id: \"chapname\" is empty")
        if (name.hasDirectionControl()) return Parsed.Bad("chapter $id: \"chapname\" carries a direction control")
        chapters += Chapter(ChapterId(id), name, count)
    }
    if (chapters.isEmpty()) return Parsed.Bad("no chapter")
    val repeated = chapters.groupingBy { it.id }.eachCount().filterValues { it > 1 }.keys
    if (repeated.isNotEmpty()) return Parsed.Bad("chap_id ${repeated.joinToString { it.value.toString() }} appears more than once")
    val total = root.int("total")
    if (total != null && total != chapters.size) return Parsed.Bad("\"total\" says $total, the list holds ${chapters.size}")
    return Parsed.Ok(chapters)
}

/** The source's segment keys, in the order a [Segment] holds them. `arabic_diacless`, `app_reference` and `words` are not shown. */
private val SEGMENT_KEYS: List<String> = listOf("top", "arabic", "transliteration", "translations", "bottom", "reference")

private fun parseDua(text: String): Parsed<Dua> {
    val root = when (val json = parseJson(text)) {
        is JsonParse.Parsed -> json.value as? Json.Obj ?: return Parsed.Bad("not a JSON object")
        is JsonParse.Refused -> return Parsed.Bad("not JSON: ${json.reason}")
    }
    if (root.fields["success"] != Json.Bool(true)) return Parsed.Bad("\"success\" is not true")
    val data = root.fields["data"] as? Json.Obj ?: return Parsed.Bad("\"data\" is not an object")
    val id = data.int("dua_global_id")?.takeIf { it >= 1 } ?: return Parsed.Bad("\"dua_global_id\" is not a positive whole number")
    if (data.int("book_id") != HISNUL_MUSLIM_BOOK) return Parsed.Bad("\"book_id\" is not $HISNUL_MUSLIM_BOOK")
    val chapter = data.int("chap_id") ?: return Parsed.Bad("\"chap_id\" is not a whole number")
    val number = when (val raw = data.fields["dua_id"]) {
        Json.Null, null -> null
        else -> data.int("dua_id")?.takeIf { it >= 1 } ?: return Parsed.Bad("\"dua_id\" is neither null nor a positive whole number: $raw")
    }
    val name = data.text("duaname") ?: return Parsed.Bad("\"duaname\" is not a text")
    val list = data.fields["segments"] as? Json.Arr ?: return Parsed.Bad("\"segments\" is not a list")
    val segments = mutableListOf<Segment>()
    list.items.forEachIndexed { index, item ->
        val entry = item as? Json.Obj ?: return Parsed.Bad("segment $index is not an object")
        val values = SEGMENT_KEYS.map { key ->
            when (val value = entry.fields[key]) {
                Json.Null, null -> null
                is Json.Str -> value.value.also { if (it.hasDirectionControl()) return Parsed.Bad("segment $index \"$key\" carries a direction control") }
                else -> return Parsed.Bad("segment $index \"$key\" is neither a text nor null")
            }
        }
        segments += Segment(values[0], values[1], values[2], values[3], values[4], values[5])
    }
    // Some entries are a note only (`top` and a reference, KNOWN: 11 of the 312), so any shown text will do.
    if (segments.none { listOfNotNull(it.before, it.arabic, it.translation, it.after).any(String::isNotBlank) }) return Parsed.Bad("no segment has a text to show")
    if (name.hasDirectionControl()) return Parsed.Bad("\"duaname\" carries a direction control")
    return Parsed.Ok(Dua(DuaId(id), ChapterId(chapter), number, name, segments))
}

private fun Json.Obj.int(key: String): Int? = (fields[key] as? Json.Num)?.raw?.takeIf { WHOLE.matches(it) }?.toIntOrNull()

private fun Json.Obj.text(key: String): String? = (fields[key] as? Json.Str)?.value

private val WHOLE = Regex("-?[0-9]+")

/** U+202A to U+202E (embeddings and overrides) and U+2066 to U+2069 (isolates), as the content gates refuse (ADR-22). */
private fun String.hasDirectionControl(): Boolean = any { it in '‪'..'‮' || it in '⁦'..'⁩' }

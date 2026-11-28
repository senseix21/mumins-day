package com.senseix21.muminsday.hadithbooks

import com.senseix21.muminsday.quran.Json
import com.senseix21.muminsday.quran.JsonParse
import com.senseix21.muminsday.quran.parseJson
import java.math.BigDecimal

/**
 * Eight hadith books as served by fawazahmed0's hadith-api (ADR-28): unreviewed, kept on the developer's machine only
 * (`content/local/hadith/`), packaged into debug builds only. Each book is two of the API's edition files, the Bengali
 * (`ben-<key>`) and the Arabic with diacritics (`ara-<key>`), paired by the source's own hadith number. Every text is
 * the source's string exactly as decoded from its JSON: nothing here trims, normalises, converts digits or merges.
 */
public enum class HadithBook(public val key: String) {
    Bukhari("bukhari"),
    Muslim("muslim"),
    AbuDawud("abudawud"),
    Tirmidhi("tirmidhi"),
    Nasai("nasai"),
    IbnMajah("ibnmajah"),
    Malik("malik"),
    Nawawi("nawawi"),
    ;

    /** The API's minified edition files, served under these names; the `.json` ones hold the same JSON (KNOWN, ADR-28). */
    public val bengaliFile: String get() = "ben-$key.min.json"
    public val arabicFile: String get() = "ara-$key.min.json"
}

/** The editions' folder, relative to `content/local/hadith/` and to the debug assets. */
public const val EDITIONS_FOLDER: String = "editions"

/** A section as the source numbers it: the keys of `metadata.sections`, and each hadith's `reference.book`. */
@JvmInline
public value class SectionId(public val value: Int)

/**
 * A hadith's number exactly as the source writes it: `7`, or `402.2` for an entry the source placed between two
 * numbers. Two editions' records are the same hadith when these are equal.
 */
@JvmInline
public value class HadithNumber(public val written: String)

/** A section: its id, its name as the source has it (English; one section of some books has the empty name). */
public data class Section(val id: SectionId, val name: String, val hadithCount: Int)

/** One grading as the source gives it: who graded ([name]) and the grade's words, both unchanged. */
public data class Grade(val name: String, val grade: String)

/** The source's `reference`: the section (`book`) and the place in it (`hadith`), as written. */
public data class Reference(val book: Int, val hadith: Int)

/**
 * One hadith: [arabic] from the Arabic edition, [bengali] from the Bengali one, each null when that edition has no record
 * with this number (an empty string is the source's own empty text and is kept). [arabicNumber] is the source's
 * `arabicnumber` as written, null where the source has none. [section] is the source's `reference.book`, which is how the
 * API's own section files group hadiths (KNOWN, ADR-28).
 */
public data class Hadith(
    val number: HadithNumber,
    val arabicNumber: String?,
    val section: SectionId,
    val arabic: String?,
    val bengali: String?,
    val grades: List<Grade>,
    val reference: Reference,
)

/**
 * One book: [name] as the source has it, its [sections] that hold at least one hadith in the source's order, and every
 * hadith in the source's order. [onlyBengali] and [onlyArabic] count the numbers one edition has and the other lacks.
 */
public data class Book(
    val book: HadithBook,
    val name: String,
    val sections: List<Section>,
    val hadiths: List<Hadith>,
    val onlyBengali: Int,
    val onlyArabic: Int,
) {
    private val bySection: Map<SectionId, List<Hadith>> = hadiths.groupBy { it.section }

    public fun hadithsOf(section: SectionId): List<Hadith> = bySection[section].orEmpty()
}

public sealed interface BookCheck {
    public data class Accepted(val book: Book) : BookCheck

    /** Every reason found, each naming its file. Nothing of a refused book is shown. */
    public data class Refused(val reasons: List<String>) : BookCheck
}

/**
 * Reads [book]'s Bengali edition ([bengaliText]) and Arabic edition ([arabicText]) into one [Book], or refuses with every
 * reason. Each file must be the API's edition shape (`metadata` with `name` and `sections`, a `hadiths` list of records
 * with `hadithnumber`, an optional `arabicnumber`, `text`, `grades`, `reference`), with no other key, no number twice,
 * and every `reference.book` a listed section. The two files must name the same book and sections, and a number both
 * hold must have the same `arabicnumber`, grades and reference in both. A text may carry direction controls only in
 * balanced, nested pairs, so none can close the isolate the screen adds (INV-L3).
 */
public fun checkHadithBook(book: HadithBook, bengaliText: String, arabicText: String): BookCheck {
    val parsedBengali = parseEdition(bengaliText)
    val parsedArabic = parseEdition(arabicText)
    val bengali = (parsedBengali as? Parsed.Ok)?.value
    val arabic = (parsedArabic as? Parsed.Ok)?.value
    if (bengali == null || arabic == null) {
        return BookCheck.Refused(
            listOfNotNull(
                (parsedBengali as? Parsed.Bad)?.let { "${book.bengaliFile}: ${it.reason}" },
                (parsedArabic as? Parsed.Bad)?.let { "${book.arabicFile}: ${it.reason}" },
            ),
        )
    }
    val reasons = mutableListOf<String>()
    if (bengali.name != arabic.name) reasons += "the two editions name different books: \"${bengali.name}\" and \"${arabic.name}\""
    if (bengali.sections != arabic.sections) reasons += "the two editions list different sections"
    val arabicByNumber = arabic.records.associateBy { it.number }
    val bengaliByNumber = bengali.records.associateBy { it.number }
    for (record in bengali.records) {
        val other = arabicByNumber[record.number] ?: continue
        val number = record.number.written
        if (record.arabicNumber != other.arabicNumber) reasons += "hadith $number: the editions give different arabicnumber"
        if (record.grades != other.grades) reasons += "hadith $number: the editions give different grades"
        if (record.reference != other.reference) reasons += "hadith $number: the editions give different references"
    }
    if (reasons.isNotEmpty()) return BookCheck.Refused(reasons)

    // The source lists each edition in ascending number (KNOWN for all 16 files); the union keeps that order.
    val numbers = (bengali.records.map { it.number } + arabic.records.map { it.number }).distinct()
        .sortedWith { a, b -> BigDecimal(a.written).compareTo(BigDecimal(b.written)) }
    val hadiths = numbers.mapNotNull { number ->
        val fromBengali = bengaliByNumber[number]
        val fromArabic = arabicByNumber[number]
        val described = fromBengali ?: fromArabic ?: return@mapNotNull null
        Hadith(number, described.arabicNumber, SectionId(described.reference.book), fromArabic?.text, fromBengali?.text, described.grades, described.reference)
    }
    val counts = hadiths.groupingBy { it.section }.eachCount()
    val sections = bengali.sections.mapNotNull { (id, name) -> counts[id]?.let { Section(id, name, it) } }
    return BookCheck.Accepted(
        Book(
            book = book,
            name = bengali.name,
            sections = sections,
            hadiths = hadiths,
            onlyBengali = bengali.records.count { it.number !in arabicByNumber },
            onlyArabic = arabic.records.count { it.number !in bengaliByNumber },
        ),
    )
}

/** What a typed number asks for: one the source could have written, or not a number at all. */
public sealed interface TypedNumber {
    public data class Number(val number: HadithNumber) : TypedNumber

    public data object NotANumber : TypedNumber
}

/**
 * Reads what the user typed in the jump box: ASCII, Bengali (০-৯) or Arabic-Indic (٠-٩) digits, an optional `.` part,
 * spaces around it ignored, leading zeros dropped. The result is written the way the source writes its numbers, so
 * comparing it with [HadithNumber.written] finds the hadith.
 */
public fun readTypedNumber(typed: String): TypedNumber {
    val ascii = buildString {
        for (char in typed.trim()) {
            append(
                when (char) {
                    in '০'..'৯' -> '0' + (char - '০')
                    in '٠'..'٩' -> '0' + (char - '٠')
                    else -> char
                },
            )
        }
    }
    val match = TYPED.matchEntire(ascii) ?: return TypedNumber.NotANumber
    val whole = match.groupValues[1].trimStart('0').ifEmpty { "0" }
    val part = match.groupValues[2]
    if (whole.length > MAX_DIGITS || whole == "0" && part.isEmpty()) return TypedNumber.NotANumber
    return TypedNumber.Number(HadithNumber(whole + part))
}

private val TYPED = Regex("([0-9]+)(\\.[0-9]{1,3})?")

/** The largest number in the eight books is 7563 (KNOWN); six digits leave room and refuse a pasted long number. */
private const val MAX_DIGITS: Int = 6

private sealed interface Parsed<out T> {
    data class Ok<T>(val value: T) : Parsed<T>

    data class Bad(val reason: String) : Parsed<Nothing>
}

private data class Record(
    val number: HadithNumber,
    val arabicNumber: String?,
    val text: String,
    val grades: List<Grade>,
    val reference: Reference,
)

private data class Edition(val name: String, val sections: List<Pair<SectionId, String>>, val records: List<Record>)

private val EDITION_KEYS = setOf("metadata", "hadiths")
private val METADATA_KEYS = setOf("name", "sections", "section_details")
private val RECORD_KEYS = setOf("hadithnumber", "arabicnumber", "text", "grades", "reference")
private val GRADE_KEYS = setOf("name", "grade")
private val REFERENCE_KEYS = setOf("book", "hadith")

private fun parseEdition(text: String): Parsed<Edition> {
    val root = when (val json = parseJson(text)) {
        is JsonParse.Parsed -> json.value as? Json.Obj ?: return Parsed.Bad("not a JSON object")
        is JsonParse.Refused -> return Parsed.Bad("not JSON: ${json.reason}")
    }
    if (root.fields.keys != EDITION_KEYS) return Parsed.Bad("the keys are ${root.fields.keys}, not $EDITION_KEYS")
    val metadata = root.fields["metadata"] as? Json.Obj ?: return Parsed.Bad("\"metadata\" is not an object")
    if (!METADATA_KEYS.containsAll(metadata.fields.keys)) return Parsed.Bad("\"metadata\" has a key outside $METADATA_KEYS")
    val name = metadata.text("name")?.takeIf { it.isNotBlank() } ?: return Parsed.Bad("\"metadata.name\" is not a text")
    val listed = metadata.fields["sections"] as? Json.Obj ?: return Parsed.Bad("\"metadata.sections\" is not an object")
    val sections = mutableListOf<Pair<SectionId, String>>()
    for ((key, value) in listed.fields) {
        val id = key.takeIf { WHOLE.matches(it) }?.toIntOrNull() ?: return Parsed.Bad("section key \"$key\" is not a whole number")
        val sectionName = (value as? Json.Str)?.value ?: return Parsed.Bad("section $key: the name is not a text")
        if (!sectionName.directionControlsBalanced()) return Parsed.Bad("section $key: the name has an unbalanced direction control")
        sections += SectionId(id) to sectionName
    }
    val known = sections.map { it.first }.toSet()
    if (known.size != sections.size) return Parsed.Bad("a section key appears more than once")
    val list = root.fields["hadiths"] as? Json.Arr ?: return Parsed.Bad("\"hadiths\" is not a list")
    val records = mutableListOf<Record>()
    val seen = HashSet<HadithNumber>()
    list.items.forEachIndexed { index, item ->
        val record = when (val parsed = parseRecord(item)) {
            is Parsed.Ok -> parsed.value
            is Parsed.Bad -> return Parsed.Bad("hadith entry $index: ${parsed.reason}")
        }
        if (!seen.add(record.number)) return Parsed.Bad("hadithnumber ${record.number.written} appears more than once")
        if (SectionId(record.reference.book) !in known) return Parsed.Bad("hadith ${record.number.written}: reference.book ${record.reference.book} is not a listed section")
        records += record
    }
    if (records.isEmpty()) return Parsed.Bad("no hadith")
    return Parsed.Ok(Edition(name, sections, records))
}

private fun parseRecord(item: Json): Parsed<Record> {
    val entry = item as? Json.Obj ?: return Parsed.Bad("not an object")
    if (!RECORD_KEYS.containsAll(entry.fields.keys)) return Parsed.Bad("a key outside $RECORD_KEYS")
    val number = (entry.fields["hadithnumber"] as? Json.Num)?.raw?.takeIf { NUMBER.matches(it) }
        ?: return Parsed.Bad("\"hadithnumber\" is not a positive number")
    val arabicNumber = when (val raw = entry.fields["arabicnumber"]) {
        null -> null
        is Json.Num -> raw.raw
        is Json.Str -> raw.value.takeIf { it.isNotBlank() && it.directionControlsBalanced() } ?: return Parsed.Bad("\"arabicnumber\" is an empty or unbalanced text")
        else -> return Parsed.Bad("\"arabicnumber\" is neither a number nor a text")
    }
    val text = entry.text("text") ?: return Parsed.Bad("hadith $number: \"text\" is not a text")
    if (!text.directionControlsBalanced()) return Parsed.Bad("hadith $number: \"text\" has an unbalanced direction control")
    val gradeList = entry.fields["grades"] as? Json.Arr ?: return Parsed.Bad("hadith $number: \"grades\" is not a list")
    val grades = mutableListOf<Grade>()
    for (grade in gradeList.items) {
        val fields = grade as? Json.Obj ?: return Parsed.Bad("hadith $number: a grade is not an object")
        if (fields.fields.keys != GRADE_KEYS) return Parsed.Bad("hadith $number: a grade's keys are not $GRADE_KEYS")
        val who = fields.text("name")?.takeIf { it.isNotBlank() && it.directionControlsBalanced() } ?: return Parsed.Bad("hadith $number: a grade has no name")
        val words = fields.text("grade")?.takeIf { it.isNotBlank() && it.directionControlsBalanced() } ?: return Parsed.Bad("hadith $number: a grade has no grade")
        grades += Grade(who, words)
    }
    val reference = entry.fields["reference"] as? Json.Obj ?: return Parsed.Bad("hadith $number: \"reference\" is not an object")
    if (reference.fields.keys != REFERENCE_KEYS) return Parsed.Bad("hadith $number: \"reference\" keys are not $REFERENCE_KEYS")
    val book = reference.int("book")?.takeIf { it >= 0 } ?: return Parsed.Bad("hadith $number: \"reference.book\" is not a whole number")
    val place = reference.int("hadith")?.takeIf { it >= 0 } ?: return Parsed.Bad("hadith $number: \"reference.hadith\" is not a whole number")
    return Parsed.Ok(Record(HadithNumber(number), arabicNumber, text, grades, Reference(book, place)))
}

private fun Json.Obj.int(key: String): Int? = (fields[key] as? Json.Num)?.raw?.takeIf { WHOLE.matches(it) }?.toIntOrNull()

private fun Json.Obj.text(key: String): String? = (fields[key] as? Json.Str)?.value

private val WHOLE = Regex("-?[0-9]+")

/** The source's numbers: positive, a whole part without leading zeros and at most one `.` part (KNOWN: `402.2`). */
private val NUMBER = Regex("[1-9][0-9]*(\\.[0-9]+)?")

/**
 * True when every embedding or override (U+202A to U+202E) is closed by a PDF (U+202C) and every isolate (U+2066 to
 * U+2068) by a PDI (U+2069), properly nested, and nothing closes what it did not open. Such a text cannot end the
 * isolate the screen wraps it in (INV-L3). The source has one balanced, empty RLE PDF pair (KNOWN: an-Nawawi 2, Arabic).
 */
internal fun String.directionControlsBalanced(): Boolean {
    val open = ArrayDeque<Boolean>()
    for (char in this) {
        when (char) {
            '‪', '‫', '‭', '‮' -> open.addLast(false)
            '⁦', '⁧', '⁨' -> open.addLast(true)
            '‬' -> if (open.lastOrNull() == false) open.removeLast() else return false
            '⁩' -> if (open.lastOrNull() == true) open.removeLast() else return false
        }
    }
    return open.isEmpty()
}

package com.senseix21.muminsday.quran

/** One surah as the source's metadata describes it. [name] is the metadata's Arabic name, shown as it is. */
public data class SurahInfo(val number: SurahNumber, val ayahCount: Int, val name: String)

public sealed interface MetadataParse {
    public data class Parsed(val surahs: List<SurahInfo>) : MetadataParse

    /** [reason] is for the build log; the app shows a plain sentence. */
    public data class Refused(val reason: String) : MetadataParse
}

/**
 * Reads the `<sura index="..." ayas="..." name="..." .../>` elements of Tanzil's `quran-data.xml`. Strict: exactly 114,
 * numbered 1 to 114 in order, each with a whole, positive ayah count and a non-blank name. Element order and other
 * attributes are not read, so this is not an XML parser; the file it reads is fixed by its hash (T-BUILD-6).
 */
public fun parseMetadata(xml: String): MetadataParse {
    val surahs = mutableListOf<SurahInfo>()
    for (element in SURA.findAll(xml)) {
        fun attribute(name: String): String? = Regex("\\s$name=\"([^\"]*)\"").find(element.value)?.groupValues?.get(1)
        val number = attribute("index")?.toIntOrNull()?.let(SurahNumber::of)
        val count = attribute("ayas")?.toIntOrNull()?.takeIf { it > 0 }
        val name = attribute("name")?.takeUnless { it.isBlank() }
        if (number == null || count == null || name == null) {
            return MetadataParse.Refused("a <sura> element lacks a valid index, ayas or name: ${element.value.take(80)}")
        }
        surahs += SurahInfo(number, count, name)
    }
    if (surahs.size != SurahNumber.COUNT) return MetadataParse.Refused("${surahs.size} <sura> elements, expected ${SurahNumber.COUNT}")
    surahs.forEachIndexed { index, surah ->
        if (surah.number.value != index + 1) return MetadataParse.Refused("<sura> number ${surah.number.value} is at position ${index + 1}")
    }
    return MetadataParse.Parsed(surahs)
}

private val SURA = Regex("<sura\\s[^>]*/>")

/** The number of juz the metadata divides the Quran into. */
public const val JUZ_COUNT: Int = 30

public sealed interface JuzParse {
    /** [starts] holds the first ayah of juz 1 to 30, in order. */
    public data class Parsed(val starts: List<VerseRef>) : JuzParse

    public data class Refused(val reason: String) : JuzParse
}

/**
 * Reads the `<juz index="..." sura="..." aya="..."/>` elements of Tanzil's `quran-data.xml` (ADR-24). Strict, like
 * [parseMetadata]: exactly 30, numbered 1 to 30 in order, juz 1 at 1:1, each later one strictly after the one before.
 * Whether each start ayah exists in its surah is checked by the [Quran] layout that uses them.
 */
public fun parseJuzStarts(xml: String): JuzParse {
    val starts = mutableListOf<VerseRef>()
    for (element in JUZ.findAll(xml)) {
        fun attribute(name: String): Int? = Regex("\\s$name=\"(\\d+)\"").find(element.value)?.groupValues?.get(1)?.toIntOrNull()
        val index = attribute("index")
        val surah = attribute("sura")?.let(SurahNumber::of)
        val ayah = attribute("aya")?.takeIf { it >= 1 }
        if (index == null || surah == null || ayah == null) return JuzParse.Refused("a <juz> element lacks a valid index, sura or aya: ${element.value.take(80)}")
        if (index != starts.size + 1) return JuzParse.Refused("<juz> number $index is at position ${starts.size + 1}")
        val ref = VerseRef(surah, ayah)
        val previous = starts.lastOrNull()
        if (previous != null && !(ref.surah > previous.surah || (ref.surah == previous.surah && ref.ayah > previous.ayah))) {
            return JuzParse.Refused("juz $index starts at $ref, not after juz ${index - 1} at $previous")
        }
        starts += ref
    }
    if (starts.size != JUZ_COUNT) return JuzParse.Refused("${starts.size} <juz> elements, expected $JUZ_COUNT")
    if (starts.first().surah.value != 1 || starts.first().ayah != 1) return JuzParse.Refused("juz 1 starts at ${starts.first()}, not 1:1")
    return JuzParse.Parsed(starts)
}

private val JUZ = Regex("<juz\\s[^>]*/>")

/**
 * The verified Quran: the displayed text, the text that is only indexed for search, and the metadata, all agreeing
 * (INV-QT1). [verses] are in order, 1:1 first. Construct with [checkQuran].
 */
public class Quran internal constructor(
    public val surahs: List<SurahInfo>,
    public val verses: List<Line.Verse>,
    /** The same ayahs in ordinary spelling, same order; never displayed (ADR-23). */
    public val searchVerses: List<Line.Verse>,
) {
    private val firstIndex: IntArray = IntArray(surahs.size).also { firsts ->
        var running = 0
        surahs.forEachIndexed { index, surah ->
            firsts[index] = running
            running += surah.ayahCount
        }
    }

    public fun surah(number: SurahNumber): SurahInfo = surahs[number.value - 1]

    /** True when the ayah exists. */
    public fun has(ref: VerseRef): Boolean = ref.ayah in 1..surah(ref.surah).ayahCount

    /** 0-based position of [ref] in [verses], or null when the ayah does not exist. */
    public fun indexOf(ref: VerseRef): Int? = if (has(ref)) firstIndex[ref.surah.value - 1] + ref.ayah - 1 else null

    public fun versesOf(number: SurahNumber): List<Line.Verse> {
        val first = firstIndex[number.value - 1]
        return verses.subList(first, first + surah(number).ayahCount)
    }
}

public sealed interface QuranCheck {
    public data class Verified(val quran: Quran) : QuranCheck

    /** Every reason found, for the build log. */
    public data class Refused(val reasons: List<String>) : QuranCheck
}

/**
 * The text, the search text and the metadata must describe the same Quran: the same number of surahs, and in each
 * surah the number of ayahs the metadata gives. The texts are not compared word for word (they differ in spelling by
 * design); only their numbering is.
 */
public fun checkQuran(text: TextParse, searchText: TextParse, metadata: MetadataParse): QuranCheck {
    val reasons = mutableListOf<String>()
    fun verses(parse: TextParse, label: String): List<Line.Verse>? = when (parse) {
        is TextParse.Parsed -> parse.verses
        is TextParse.Refused -> null.also { reasons += "$label: ${parse.problems.take(5)}" }
    }
    val displayed = verses(text, "text")
    val searched = verses(searchText, "search text")
    val surahs = when (metadata) {
        is MetadataParse.Parsed -> metadata.surahs
        is MetadataParse.Refused -> null.also { reasons += "metadata: ${metadata.reason}" }
    }
    if (displayed == null || searched == null || surahs == null) return QuranCheck.Refused(reasons)

    for ((label, verses) in listOf("text" to displayed, "search text" to searched)) {
        val counts = verses.groupingBy { it.ref.surah }.eachCount()
        for (surah in surahs) {
            val found = counts[surah.number] ?: 0
            if (found != surah.ayahCount) reasons += "$label: surah ${surah.number.value} has $found ayahs, metadata says ${surah.ayahCount}"
        }
        if (counts.size != surahs.size) reasons += "$label: ${counts.size} surahs, metadata has ${surahs.size}"
    }
    if (displayed.map { it.ref } != searched.map { it.ref }) reasons += "the text and the search text are not numbered alike"
    return if (reasons.isEmpty()) QuranCheck.Verified(Quran(surahs, displayed, searched)) else QuranCheck.Refused(reasons)
}

/**
 * A translation, checked against the [Quran] it translates: exactly one line per ayah, in order. It is shown and
 * indexed as it is. [testFixture] is true for the FAKE debug translation only (the release has none, INV-QT1).
 */
public class Translation internal constructor(public val verses: List<Line.Verse>, public val testFixture: Boolean)

public sealed interface TranslationCheck {
    public data class Accepted(val translation: Translation) : TranslationCheck

    public data class Refused(val reason: String) : TranslationCheck
}

/** The first line of a FAKE translation. A file that starts with it is a test fixture. */
public const val TEST_FIXTURE_MARKER: String = "# test_fixture"

public fun checkTranslation(quran: Quran, parse: TextParse, firstLine: String): TranslationCheck = when (parse) {
    is TextParse.Refused -> TranslationCheck.Refused("translation: ${parse.problems.take(5)}")
    is TextParse.Parsed ->
        if (parse.verses.map { it.ref } == quran.verses.map { it.ref }) {
            TranslationCheck.Accepted(Translation(parse.verses, testFixture = firstLine == TEST_FIXTURE_MARKER))
        } else {
            TranslationCheck.Refused("translation: ${parse.verses.size} ayahs, not numbered like the text")
        }
}

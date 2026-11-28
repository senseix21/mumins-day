package com.senseix21.muminsday.quran

/**
 * The words of [text] as the search index holds them (ADR-23). This is for the INDEX and the query only: the text the
 * reader shows is never passed through it (INV-QT1). Arabic loses its vowel marks, Quranic annotation marks, tatweel
 * and small letters, and every form of alef becomes a bare alef and alef maksura becomes yeh, so a word typed with or
 * without diacritics finds the same ayahs. Letters and digits of every script are kept, with their own marks outside
 * Arabic (a Bengali vowel sign is part of its word); everything else separates words. Latin is lower-cased.
 */
public fun indexWords(text: String): String = buildString {
    var pendingSpace = false
    text.codePoints().forEach { point ->
        val type = Character.getType(point)
        // By block, not script: Arabic vowel marks are script Inherited and tatweel is Common.
        val arabic = Character.UnicodeBlock.of(point) in ARABIC_BLOCKS
        val mark = type == Character.NON_SPACING_MARK.toInt() || type == Character.ENCLOSING_MARK.toInt() ||
            type == Character.COMBINING_SPACING_MARK.toInt()
        when {
            arabic && (mark || type == Character.MODIFIER_LETTER.toInt()) -> Unit
            Character.isLetterOrDigit(point) || mark -> {
                if (pendingSpace && isNotEmpty()) append(' ')
                pendingSpace = false
                appendCodePoint(foldArabic(Character.toLowerCase(point)))
            }
            else -> pendingSpace = true
        }
    }
}

private val ARABIC_BLOCKS: Set<Character.UnicodeBlock> = setOf(
    Character.UnicodeBlock.ARABIC,
    Character.UnicodeBlock.ARABIC_SUPPLEMENT,
    Character.UnicodeBlock.ARABIC_EXTENDED_A,
    Character.UnicodeBlock.ARABIC_PRESENTATION_FORMS_A,
    Character.UnicodeBlock.ARABIC_PRESENTATION_FORMS_B,
)

private fun foldArabic(point: Int): Int = when (point) {
    0x0622, 0x0623, 0x0625, 0x0671 -> 0x0627 // alef with madda, hamza above, hamza below, wasla -> alef
    0x0649 -> 0x064A // alef maksura -> yeh
    else -> point
}

/** What the search box asked for. */
public sealed interface Lookup {
    /** Nothing but spaces or separators was typed. */
    public data object Empty : Lookup

    public data class Verse(val ref: VerseRef) : Lookup

    public data class Surah(val number: SurahNumber) : Lookup

    /** A reference was typed, but that surah or ayah does not exist. Nothing is guessed. */
    public data object NoSuchVerse : Lookup

    /** Words to find. [match] is the SQLite FTS4 query; [words] are its terms, already in [indexWords] form. */
    public data class Words(val words: List<String>, val match: String) : Lookup
}

/**
 * Reads the search box. "2:255", the same in Bengali digits, "2.255" or "2 255" is an ayah, "36" a surah, in ASCII, Bengali or Arabic-Indic
 * digits; anything else is words. Each word is matched as a prefix (so a word with an ending still matches) and all
 * words must be in the ayah. [surahs] are the metadata's 114, so a reference is checked against the real ayah counts
 * without the text in memory. The words are quoted in the FTS query, so nothing typed can act as an FTS operator.
 */
public fun lookup(input: String, surahs: List<SurahInfo>): Lookup {
    val digits = input.trim().map(::asciiDigit).joinToString("")
    REFERENCE.matchEntire(digits)?.let { found ->
        val surah = found.groupValues[1].toIntOrNull()?.let(SurahNumber::of) ?: return Lookup.NoSuchVerse
        val ref = VerseRef(surah, found.groupValues[2].toIntOrNull() ?: return Lookup.NoSuchVerse)
        return if (ref.ayah in 1..surahs[surah.value - 1].ayahCount) Lookup.Verse(ref) else Lookup.NoSuchVerse
    }
    SURAH_ONLY.matchEntire(digits)?.let { found ->
        return found.groupValues[1].toIntOrNull()?.let(SurahNumber::of)?.let { Lookup.Surah(it) } ?: Lookup.NoSuchVerse
    }
    val words = indexWords(input).split(' ').filter { it.isNotEmpty() }
    if (words.isEmpty()) return Lookup.Empty
    return Lookup.Words(words, words.joinToString(" ") { "\"$it*\"" })
}

private val REFERENCE = Regex("(\\d{1,3})\\s*[:.\\-/ ]\\s*(\\d{1,3})")
private val SURAH_ONLY = Regex("(\\d{1,3})")

private fun asciiDigit(char: Char): Char = when (char) {
    in '\u09E6'..'\u09EF' -> '0' + (char - '\u09E6') // Bengali
    in '\u0660'..'\u0669' -> '0' + (char - '\u0660') // Arabic-Indic
    in '\u06F0'..'\u06F9' -> '0' + (char - '\u06F0') // Extended Arabic-Indic
    else -> char
}

/**
 * The reference implementation of a word search over the verified text, for tests and for checking the database: an
 * ayah matches when every word starts some word of its indexed text, search text or translation. The app asks SQLite
 * (FTS4) instead; both must agree.
 */
public class MemoryIndex(private val quran: Quran, translation: Translation? = null) {
    private val tokens: List<List<String>> = quran.verses.indices.map { index ->
        (listOf(quran.verses[index].text, quran.searchVerses[index].text) + listOfNotNull(translation?.verses?.get(index)?.text))
            .flatMap { indexWords(it).split(' ') }
    }

    public fun find(words: List<String>): List<VerseRef> =
        tokens.indices.filter { index -> words.all { word -> tokens[index].any { it.startsWith(word) } } }.map { quran.verses[it].ref }
}

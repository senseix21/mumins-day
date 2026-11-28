package com.senseix21.muminsday.quran

/**
 * The translations the app may bundle (ADR-26): QuranEnc.com's two Bengali translations, each kept in
 * `content/quran/translations/<key>/` exactly as QuranEnc served it, under QuranEnc's published terms (quoted in
 * `content/quran-SOURCES.md`). A closed set: a translation is added here, with its terms recorded, or not at all
 * (INV-QT1).
 */
public enum class Edition(public val key: String, public val format: EditionFormat) {
    /** Dr. Abu Bakr Muhammad Zakaria. QuranEnc offers no whole-file download of it, so its 114 sura API responses. */
    BengaliZakaria("bengali_zakaria", EditionFormat.SuraResponses),

    /** Rowwad Translation Center. QuranEnc's whole-translation XML download. */
    BengaliRwwad("bengali_rwwad", EditionFormat.WholeXml),
    ;

    public companion object {
        /** Null for a key that is not one of the bundled editions. */
        public fun ofKey(key: String): Edition? = entries.firstOrNull { it.key == key }
    }
}

public enum class EditionFormat {
    /** `001.json` to `114.json`, each the body of `https://quranenc.com/api/v1/translation/sura/<key>/<n>` as received. */
    SuraResponses,

    /** One `.xml` file as QuranEnc's download serves it, its `<meta>` transcript block included. */
    WholeXml,
}

/**
 * One ayah of a translation. [text] and [footnotes] are exactly the source's strings, footnote markers such as `[১]`
 * and any markup included (INV-QT1, QuranEnc condition 1). [footnotes] is "" where the source's footnotes are empty.
 */
public data class TranslatedAyah(val ref: VerseRef, val text: String, val footnotes: String)

/** A translation checked against the [Quran]: one ayah for each of its ayahs, in order. Construct with [checkTranslation]. */
public class Translation internal constructor(
    public val edition: Edition,
    public val verses: List<TranslatedAyah>,
    /** The source's own transcript information (QuranEnc condition 4), verbatim; null when the source carries none. */
    public val transcript: String?,
)

public sealed interface TranslationCheck {
    public data class Accepted(val translation: Translation) : TranslationCheck

    /** [reason] is for the build log; the app shows a plain sentence. */
    public data class Refused(val reason: String) : TranslationCheck
}

/**
 * Reads [edition]'s files ([files]: name within its folder to text) in its [EditionFormat] and checks it against
 * [quran]: exactly one ayah for each of the text's ayahs, numbered alike. Strict: a file that is not where the format
 * puts it, a field that is missing, or anything the format does not have refuses the whole translation.
 */
public fun checkTranslation(quran: Quran, edition: Edition, files: Map<String, String>): TranslationCheck {
    val parsed = when (edition.format) {
        EditionFormat.SuraResponses -> parseSuraResponses(files)
        EditionFormat.WholeXml -> parseWholeXml(files)
    }
    return when (parsed) {
        is SourceParse.Refused -> TranslationCheck.Refused("${edition.key}: ${parsed.reason}")
        is SourceParse.Parsed ->
            if (parsed.verses.map { it.ref } == quran.verses.map { it.ref }) {
                TranslationCheck.Accepted(Translation(edition, parsed.verses, parsed.transcript))
            } else {
                TranslationCheck.Refused("${edition.key}: ${parsed.verses.size} ayahs, not numbered like the text")
            }
    }
}

/**
 * The `<meta>...</meta>` block of a QuranEnc XML download, verbatim, from the start of the file ([head] may be only its
 * first few kilobytes). Null when the head has no such block. For the Licences screen; [checkTranslation] verifies it.
 */
public fun quranEncTranscript(head: String): String? {
    val start = head.indexOf("<meta>")
    val end = head.indexOf("</meta>")
    return if (start < 0 || end < start) null else head.substring(start, end + "</meta>".length)
}

private sealed interface SourceParse {
    data class Parsed(val verses: List<TranslatedAyah>, val transcript: String?) : SourceParse

    data class Refused(val reason: String) : SourceParse
}

/** The sura API's body: `{"result":[{"id":..,"sura":"n","aya":"m","arabic_text":..,"translation":..,"footnotes":..},...]}`. */
private fun parseSuraResponses(files: Map<String, String>): SourceParse {
    val expected = (1..SurahNumber.COUNT).map { it.toString().padStart(3, '0') + ".json" }
    if (files.keys != expected.toSet()) return SourceParse.Refused("expected exactly 001.json to 114.json, found ${files.size} file(s)")
    val verses = mutableListOf<TranslatedAyah>()
    for ((index, name) in expected.withIndex()) {
        val surah = SurahNumber.of(index + 1) ?: return SourceParse.Refused("no surah ${index + 1}")
        val body = when (val json = parseJson(files.getValue(name))) {
            is JsonParse.Parsed -> json.value
            is JsonParse.Refused -> return SourceParse.Refused("$name: ${json.reason}")
        }
        val result = ((body as? Json.Obj)?.takeIf { it.fields.keys == setOf("result") }?.fields?.get("result") as? Json.Arr)
            ?: return SourceParse.Refused("$name: not an object holding only a \"result\" array")
        for (item in result.items) {
            val fields = (item as? Json.Obj)?.fields ?: return SourceParse.Refused("$name: a result entry is not an object")
            fun text(key: String): String? = (fields[key] as? Json.Str)?.value
            val sura = text("sura")
            val aya = text("aya")?.toIntOrNull()
            val translation = text("translation")
            val footnotes = text("footnotes")
            if (sura != "${surah.value}" || aya == null || translation == null || footnotes == null) {
                return SourceParse.Refused("$name: an entry lacks sura ${surah.value}, a numbered aya, translation or footnotes")
            }
            if (translation.isBlank()) return SourceParse.Refused("$name: ayah $aya has an empty translation")
            verses += TranslatedAyah(VerseRef(surah, aya), translation, footnotes)
        }
    }
    return SourceParse.Parsed(verses, transcript = null)
}

/**
 * QuranEnc's XML download: `<translation_root>`, a `<meta>` block, then `<sura_list>` of `<sura number="n">` holding
 * `<aya number="m">` with `<translation><![CDATA[...]]></translation>` and `<footnotes>`, empty or one CDATA section.
 * Read by position, not by an XML parser: CDATA content is taken as the exact substring (an XML parser would also turn
 * `\r\n` into `\n`), and anything else between the elements but whitespace refuses the file.
 */
private fun parseWholeXml(files: Map<String, String>): SourceParse {
    val source = files.values.singleOrNull()?.takeIf { files.keys.single().endsWith(".xml") }
        ?: return SourceParse.Refused("expected exactly one .xml file, found ${files.keys}")
    val scan = XmlScan(source)
    val verses = mutableListOf<TranslatedAyah>()
    scan.expect("<translation_root>")
    scan.space()
    val transcript = quranEncTranscript(source)?.takeIf { source.startsWith(it, scan.at) }
        ?: return SourceParse.Refused("no <meta> block after <translation_root>")
    scan.at += transcript.length
    scan.expect("<sura_list>")
    while (scan.problem == null && scan.next("<sura ")) {
        val surah = SurahNumber.of(scan.numbered("<sura number=\"") ?: break)
            ?: return SourceParse.Refused("a surah number outside 1 to 114 near character ${scan.at}")
        while (scan.problem == null && scan.next("<aya ")) {
            val ayah = scan.numbered("<aya number=\"") ?: break
            scan.expect("<translation>")
            val text = scan.cdata() ?: break
            scan.expectHere("</translation>")
            scan.expect("<footnotes>")
            val footnotes = if (scan.isHere("<![CDATA[")) scan.cdata() ?: break else ""
            scan.expectHere("</footnotes>")
            scan.expect("</aya>")
            if (text.isBlank()) return SourceParse.Refused("${surah.value}:$ayah has an empty translation")
            verses += TranslatedAyah(VerseRef(surah, ayah), text, footnotes)
        }
        scan.expect("</sura>")
    }
    scan.expect("</sura_list>")
    scan.expect("</translation_root>")
    scan.space()
    if (scan.problem == null && scan.at != source.length) scan.problem = "text after </translation_root>"
    return scan.problem?.let { SourceParse.Refused(it) } ?: SourceParse.Parsed(verses, transcript)
}

/** A cursor over the XML. The first problem is kept in [problem]; every step after it does nothing. */
private class XmlScan(private val source: String) {
    var at: Int = 0
    var problem: String? = null

    fun space() {
        while (at < source.length && source[at] in " \t\r\n") at++
    }

    fun isHere(literal: String): Boolean = source.startsWith(literal, at)

    /** After whitespace, whether [literal] comes next. */
    fun next(literal: String): Boolean {
        space()
        return isHere(literal)
    }

    /** [literal] immediately here, with no whitespace before it (inside an element, whitespace would be content). */
    fun expectHere(literal: String) {
        if (problem != null) return
        if (isHere(literal)) at += literal.length else problem = "expected $literal at character $at"
    }

    fun expect(literal: String) {
        if (problem != null) return
        space()
        expectHere(literal)
    }

    /** `<tag number="N">`: [opening] is everything up to the digits. */
    fun numbered(opening: String): Int? {
        expect(opening)
        val start = at
        while (problem == null && at < source.length && source[at].isDigit()) at++
        val number = source.substring(start, at).toIntOrNull()?.takeIf { it >= 1 }
        expectHere("\">")
        if (number == null && problem == null) problem = "no number at character $start"
        return if (problem == null) number else null
    }

    /** `<![CDATA[...]]>` here: the content, exactly. */
    fun cdata(): String? {
        expectHere("<![CDATA[")
        if (problem != null) return null
        val end = source.indexOf("]]>", at)
        if (end < 0) {
            problem = "an unclosed CDATA section at character $at"
            return null
        }
        return source.substring(at, end).also { at = end + "]]>".length }
    }
}

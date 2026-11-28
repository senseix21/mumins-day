package com.senseix21.muminsday.quran

import java.io.File
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * INV-QT1 over QuranEnc's two Bengali translations as shipped in `content/quran/translations/` (ADR-26). No translation
 * text is typed here: every string a test looks for is taken from the files themselves.
 */
class QuranEncTest {

    private val directory = File("../content/quran")
    private val quran: Quran = (
        checkQuran(
            parseText(File(directory, "quran-uthmani.txt").readText(Charsets.UTF_8)),
            parseText(File(directory, "quran-simple-clean.txt").readText(Charsets.UTF_8)),
            parseMetadata(File(directory, "quran-data.xml").readText(Charsets.UTF_8)),
        ) as QuranCheck.Verified
        ).quran

    private fun files(edition: Edition): Map<String, String> =
        File(directory, "translations/${edition.key}").listFiles().orEmpty().filter { it.isFile }.associate { it.name to it.readText(Charsets.UTF_8) }

    private fun accepted(edition: Edition, files: Map<String, String> = files(edition)): Translation =
        (checkTranslation(quran, edition, files) as TranslationCheck.Accepted).translation

    @Test
    fun INV_QT1_zakaria_has_6236_ayahs_with_the_per_surah_counts_of_the_metadata() = countsMatch(Edition.BengaliZakaria)

    @Test
    fun INV_QT1_rwwad_has_6236_ayahs_with_the_per_surah_counts_of_the_metadata() = countsMatch(Edition.BengaliRwwad)

    private fun countsMatch(edition: Edition) {
        val translation = accepted(edition)
        assertEquals(6236, translation.verses.size)
        val counts = translation.verses.groupingBy { it.ref.surah }.eachCount()
        for (surah in quran.surahs) assertEquals(surah.ayahCount, counts[surah.number], "${edition.key} surah ${surah.number.value}")
        assertEquals(quran.verses.map { it.ref }, translation.verses.map { it.ref })
    }

    /** G65: every bundled footnote and text with markup pairs cleanly, so none falls back to showing raw tags. */
    @Test
    fun G65_every_bundled_bold_tag_is_understood() {
        for (edition in Edition.entries) {
            var tagged = 0
            for (verse in accepted(edition).verses) {
                for (source in listOf(verse.text, verse.footnotes)) {
                    if ("<b>" !in source && "</b>" !in source) continue
                    tagged++
                    val marked = boldMarkup(source)
                    assertTrue(marked.bold.isNotEmpty(), "${edition.key} ${verse.ref}: markup not understood")
                    assertTrue("<b>" !in marked.shown && "</b>" !in marked.shown, "${edition.key} ${verse.ref}")
                }
            }
            if (edition == Edition.BengaliZakaria) assertTrue(tagged > 0)
        }
    }

    @Test
    fun INV_QT1_zakaria_every_response_re_encodes_to_its_exact_bytes() {
        // QuranEnc's API escapes like PHP's json_encode: every non-ASCII UTF-16 unit as \uxxxx, "/" as "\/". If the
        // decoded values give back each file byte for byte, nothing was lost or changed in reading them.
        val files = files(Edition.BengaliZakaria)
        for ((name, body) in files) {
            val json = (parseJson(body) as JsonParse.Parsed).value
            assertEquals(body, encodeLikeQuranEnc(json), name)
        }
    }

    @Test
    fun INV_QT1_rwwad_every_text_and_footnote_is_the_exact_cdata_of_the_file_in_order() {
        val source = files(Edition.BengaliRwwad).values.single()
        var at = 0
        for (ayah in accepted(Edition.BengaliRwwad).verses) {
            val text = "<translation><![CDATA[${ayah.text}]]></translation>"
            at = source.indexOf(text, at).also { assertTrue(it >= 0, "${ayah.ref}") } + text.length
            val footnotes = if (ayah.footnotes.isEmpty()) "<footnotes></footnotes>" else "<footnotes><![CDATA[${ayah.footnotes}]]></footnotes>"
            assertTrue(source.startsWith(footnotes, source.indexOf("<footnotes>", at)), "${ayah.ref} footnotes")
        }
    }

    @Test
    fun INV_QT1_footnote_markers_and_footnotes_are_kept() {
        for (edition in Edition.entries) {
            val translation = accepted(edition)
            val raw = files(edition).values.joinToString("")
            val withFootnotes = translation.verses.count { it.footnotes.isNotEmpty() }
            assertTrue(withFootnotes > 0, edition.key)
            // A marker "[" + Bengali digit one + "]" as the file spells it, written as escapes so no translation is typed.
            val marker = "[\u09E7]"
            val parsedMarkers = translation.verses.sumOf { Regex(Regex.escape(marker)).findAll(it.text + it.footnotes).count() }
            val sourceMarkers = when (edition.format) {
                EditionFormat.WholeXml -> Regex(Regex.escape(marker)).findAll(raw).count()
                EditionFormat.SuraResponses -> Regex(Regex.escape("[\\u09e7]")).findAll(raw).count()
            }
            assertEquals(sourceMarkers, parsedMarkers, edition.key)
        }
    }

    @Test
    fun the_rwwad_transcript_is_the_files_meta_block_and_names_its_version() {
        val translation = accepted(Edition.BengaliRwwad)
        val transcript = assertNotNull(translation.transcript)
        assertTrue(transcript.startsWith("<meta>") && transcript.endsWith("</meta>"))
        assertTrue("<id>bengali_rwwad</id>" in transcript)
        assertTrue("(v1.1.4-xml.1)" in transcript, transcript)
        assertEquals(transcript, quranEncTranscript(files(Edition.BengaliRwwad).values.single().take(4096)))
        assertNull(accepted(Edition.BengaliZakaria).transcript, "the sura API carries no transcript block")
    }

    @Test
    fun a_missing_file_a_changed_structure_or_a_dropped_ayah_is_refused() {
        val zakaria = files(Edition.BengaliZakaria)
        assertIs<TranslationCheck.Refused>(checkTranslation(quran, Edition.BengaliZakaria, zakaria - "114.json"))
        assertIs<TranslationCheck.Refused>(checkTranslation(quran, Edition.BengaliZakaria, zakaria + ("115.json" to "{}")))
        val first = zakaria.getValue("001.json")
        val broken = zakaria + ("001.json" to first.dropLast(1))
        assertIs<TranslationCheck.Refused>(checkTranslation(quran, Edition.BengaliZakaria, broken))
        // Drop the last ayah of surah 1 (the entry objects are separated by "},{").
        val dropped = first.substring(0, first.lastIndexOf("},{") + 1) + "]}"
        assertIs<TranslationCheck.Refused>(checkTranslation(quran, Edition.BengaliZakaria, zakaria + ("001.json" to dropped)))

        val (name, xml) = files(Edition.BengaliRwwad).entries.single()
        assertIs<TranslationCheck.Refused>(checkTranslation(quran, Edition.BengaliRwwad, mapOf(name to xml.replaceFirst("<aya number=\"2\">", "<aya number=\"3\">"))))
        assertIs<TranslationCheck.Refused>(checkTranslation(quran, Edition.BengaliRwwad, mapOf(name to xml.replaceFirst("<translation><![CDATA[", "<translation> <![CDATA["))))
        assertIs<TranslationCheck.Refused>(checkTranslation(quran, Edition.BengaliRwwad, mapOf(name to xml + "x")))
        assertIs<TranslationCheck.Refused>(checkTranslation(quran, Edition.BengaliRwwad, mapOf(name to xml, "other.xml" to xml)))
        // The other edition's files are not this edition's format.
        assertIs<TranslationCheck.Refused>(checkTranslation(quran, Edition.BengaliRwwad, zakaria))
    }

    @Test
    fun search_finds_an_ayah_from_two_of_its_own_translated_words() {
        val random = Random(26)
        for (edition in Edition.entries) {
            val translation = accepted(edition)
            val memory = MemoryIndex(quran, translation)
            repeat(300) {
                val index = random.nextInt(quran.verses.size)
                val words = translation.verses[index].text.split(' ').filter { indexWords(it).isNotEmpty() }
                val start = random.nextInt(words.size)
                val typed = words.subList(start, minOf(words.size, start + 2)).joinToString(" ")
                val found = lookup(typed, quran.surahs)
                if (found is Lookup.Words) assertTrue(quran.verses[index].ref in memory.find(found.words), "${edition.key} ${quran.verses[index].ref} from \"$typed\"")
            }
        }
    }

    @Test
    fun the_json_reader_decodes_escapes_and_refuses_what_is_not_json() {
        val parsed = parseJson("""{"a":"\u09e7\/\"\\\n","b":[1,-2.5e3,true,false,null],"c":{}}""") as JsonParse.Parsed
        val fields = (parsed.value as Json.Obj).fields
        assertEquals(Json.Str("\u09E7/\"\\\n"), fields["a"])
        assertEquals(Json.Arr(listOf(Json.Num("1"), Json.Num("-2.5e3"), Json.Bool(true), Json.Bool(false), Json.Null)), fields["b"])
        assertEquals(listOf("a", "b", "c"), fields.keys.toList())
        for (bad in listOf("", "{", "{\"a\":1,\"a\":2}", "[1,]", "\"\\x\"", "\"\u0001\"", "{} x", "01", "\"\\u12\"")) {
            assertIs<JsonParse.Refused>(parseJson(bad), bad)
        }
    }
}

/** PHP json_encode's default output, the encoding QuranEnc's API uses (KNOWN: it reproduces all 114 files, above). */
private fun encodeLikeQuranEnc(json: Json): String = when (json) {
    is Json.Obj -> json.fields.entries.joinToString(",", "{", "}") { (key, value) -> quote(key) + ":" + encodeLikeQuranEnc(value) }
    is Json.Arr -> json.items.joinToString(",", "[", "]") { encodeLikeQuranEnc(it) }
    is Json.Str -> quote(json.value)
    is Json.Num -> json.raw
    is Json.Bool -> json.value.toString()
    Json.Null -> "null"
}

private fun quote(text: String): String = buildString {
    append('"')
    for (char in text) {
        when {
            char == '"' -> append("\\\"")
            char == '\\' -> append("\\\\")
            char == '/' -> append("\\/")
            char == '\n' -> append("\\n")
            char == '\r' -> append("\\r")
            char == '\t' -> append("\\t")
            char < ' ' || char > '~' -> append("\\u").append(char.code.toString(16).padStart(4, '0'))
            else -> append(char)
        }
    }
    append('"')
}

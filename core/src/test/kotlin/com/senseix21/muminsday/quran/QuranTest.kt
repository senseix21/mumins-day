package com.senseix21.muminsday.quran

import java.io.File
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * INV-QT1 over the real, verbatim Tanzil files in `content/quran/` (sources and hashes in `content/quran-SOURCES.md`).
 * No Quran text is typed here: every word a test searches for is taken from the files themselves.
 */
class QuranTest {

    private val directory = File("../content/quran")
    private val textBytes = File(directory, "quran-uthmani.txt").readBytes()
    private val searchBytes = File(directory, "quran-simple-clean.txt").readBytes()
    private val text = textBytes.toString(Charsets.UTF_8)
    private val searchText = searchBytes.toString(Charsets.UTF_8)
    private val metadata = File(directory, "quran-data.xml").readText(Charsets.UTF_8)
    private val quran: Quran = (checkQuran(parseText(text), parseText(searchText), parseMetadata(metadata)) as QuranCheck.Verified).quran
    private val memory = MemoryIndex(quran)

    @Test
    fun INV_QT1_114_surahs_and_6236_ayahs_as_the_sources_own_metadata_gives() {
        // 114 and 6236 are the totals of Tanzil's quran-data.xml (<sura> elements, sum of their ayas attributes).
        val surahs = (parseMetadata(metadata) as MetadataParse.Parsed).surahs
        assertEquals(114, surahs.size)
        assertEquals(6236, surahs.sumOf { it.ayahCount })
        assertEquals(114, quran.surahs.size)
        assertEquals(6236, quran.verses.size)
        assertEquals(6236, quran.searchVerses.size)
    }

    @Test
    fun INV_QT1_every_surah_has_the_ayah_count_the_metadata_gives_in_both_texts() {
        for (surah in quran.surahs) {
            assertEquals(surah.ayahCount, quran.versesOf(surah.number).size, "surah ${surah.number.value}")
            assertEquals((1..surah.ayahCount).toList(), quran.versesOf(surah.number).map { it.ref.ayah })
            assertEquals(surah.ayahCount, quran.searchVerses.count { it.ref.surah == surah.number })
        }
    }

    @Test
    fun INV_QT1_the_text_round_trips_byte_for_byte_through_the_parser() {
        for ((bytes, source) in listOf(textBytes to text, searchBytes to searchText)) {
            val lines = (parseText(source) as TextParse.Parsed).lines
            assertContentEquals(bytes, render(lines).toByteArray(Charsets.UTF_8))
        }
    }

    @Test
    fun INV_QT1_each_ayah_is_exactly_the_sources_bytes_after_its_second_bar() {
        val sourceLines = text.split('\n').filter { it.isNotEmpty() && !it.startsWith("#") }
        assertEquals(quran.verses.size, sourceLines.size)
        quran.verses.zip(sourceLines).forEach { (verse, line) ->
            val prefix = "${verse.ref.surah.value}|${verse.ref.ayah}|"
            assertTrue(line.startsWith(prefix))
            assertContentEquals(line.substring(prefix.length).toByteArray(Charsets.UTF_8), verse.text.toByteArray(Charsets.UTF_8))
        }
    }

    @Test
    fun INV_QT1_the_copyright_block_is_kept_and_found() {
        val block = notice((parseText(text) as TextParse.Parsed).lines)
        assertTrue("Tanzil Quran Text (Uthmani, Version 1.1)" in block, block)
        assertTrue("CHANGING IT IS NOT ALLOWED" in block)
        assertTrue(text.endsWith(block + "\n"), "the block is the file's last lines")
    }

    @Test
    fun a_missing_or_moved_ayah_or_a_broken_line_is_refused() {
        val lines = text.split('\n')
        val withoutOne = (lines.take(10) + lines.drop(11)).joinToString("\n")
        assertIs<QuranCheck.Refused>(checkQuran(parseText(withoutOne), parseText(searchText), parseMetadata(metadata)))
        val swapped = (listOf(lines[1], lines[0]) + lines.drop(2)).joinToString("\n")
        assertEquals(TextProblemKind.OutOfOrder, (parseText(swapped) as TextParse.Refused).problems.first().kind)
        val broken = (listOf("1|x|TEST") + lines.drop(1)).joinToString("\n")
        assertEquals(TextProblem(1, TextProblemKind.Malformed), (parseText(broken) as TextParse.Refused).problems.first())
        val outOfRange = (lines + "115|1|TEST").joinToString("\n")
        assertTrue(TextProblem(lines.size + 1, TextProblemKind.OutOfRange) in (parseText(outOfRange) as TextParse.Refused).problems)
        assertIs<MetadataParse.Refused>(parseMetadata(metadata.replaceFirst("<sura index=\"114\"", "<surax index=\"114\"")))
    }

    @Test
    fun search_with_or_without_diacritics_gives_the_same_index_words() {
        val marks = Regex("[\\u064B-\\u065F\\u0670\\u06D6-\\u06ED\\u0640]")
        for (verse in quran.verses) {
            assertEquals(indexWords(verse.text), indexWords(marks.replace(verse.text, "")), "${verse.ref}")
        }
    }

    @Test
    fun search_finds_an_ayah_from_its_own_words_typed_with_or_without_diacritics() {
        val random = Random(255)
        repeat(300) {
            val index = random.nextInt(quran.verses.size)
            val ref = quran.verses[index].ref
            for (source in listOf(quran.verses[index].text, quran.searchVerses[index].text)) {
                val words = source.split(' ').filter { indexWords(it).isNotEmpty() }
                val start = random.nextInt(words.size)
                val typed = words.subList(start, minOf(words.size, start + 2)).joinToString(" ")
                val found = lookup(typed, quran.surahs)
                assertIs<Lookup.Words>(found, typed)
                assertTrue(ref in memory.find(found.words), "$ref from \"$typed\"")
            }
        }
    }

    @Test
    fun search_finds_a_known_surah_and_ayah_by_reference() {
        assertEquals(Lookup.Verse(VerseRef(SurahNumber.of(2)!!, 255)), lookup("2:255", quran.surahs))
        assertEquals(Lookup.Verse(VerseRef(SurahNumber.of(2)!!, 255)), lookup("২:২৫৫", quran.surahs))
        assertEquals(Lookup.Verse(VerseRef(SurahNumber.of(114)!!, 6)), lookup(" 114.6 ", quran.surahs))
        assertEquals(Lookup.Verse(VerseRef(SurahNumber.of(1)!!, 7)), lookup("١ ٧", quran.surahs))
        assertEquals(Lookup.Surah(SurahNumber.of(36)!!), lookup("36", quran.surahs))
        assertEquals(Lookup.NoSuchVerse, lookup("1:8", quran.surahs))
        assertEquals(Lookup.NoSuchVerse, lookup("115", quran.surahs))
        assertEquals(Lookup.NoSuchVerse, lookup("0:1", quran.surahs))
        assertEquals(Lookup.Empty, lookup("  ;; ", quran.surahs))
        // The known ayah's own text, looked up by words, includes it.
        val known = quran.verses[quran.indexOf(VerseRef(SurahNumber.of(2)!!, 255))!!]
        val words = lookup(known.text, quran.surahs) as Lookup.Words
        assertEquals(listOf(known.ref), memory.find(words.words))
    }

    @Test
    fun nothing_typed_can_act_as_an_fts_operator() {
        val found = lookup("TEST\" OR \"x* NEAR (y) -z", quran.surahs) as Lookup.Words
        assertEquals(listOf("test", "or", "x", "near", "y", "z"), found.words)
        assertEquals("\"test*\" \"or*\" \"x*\" \"near*\" \"y*\" \"z*\"", found.match)
    }

    @Test
    fun index_words_keep_bengali_marks_and_lower_case_latin() {
        // Bengali letters with a vowel sign and a virama, written as escapes: U+0995 U+09BF, U+09A4 U+09CD U+09A4.
        assertEquals("কি ত্ত test", indexWords("কি, ত্ত। TEST"))
    }

    @Test
    fun a_fake_translation_is_accepted_only_when_complete_and_is_searchable() {
        val fake = TEST_FIXTURE_MARKER + "\n" + quran.verses.joinToString("\n") { "${it.ref.surah.value}|${it.ref.ayah}|TEST TRANSLATION ${it.ref}" }
        val accepted = checkTranslation(quran, parseText(fake), fake.lineSequence().first()) as TranslationCheck.Accepted
        assertTrue(accepted.translation.testFixture)
        val words = lookup("translation 36:1", quran.surahs)
        assertIs<Lookup.Words>(words)
        val incomplete = fake.lineSequence().toList().dropLast(1).joinToString("\n")
        assertIs<TranslationCheck.Refused>(checkTranslation(quran, parseText(incomplete), TEST_FIXTURE_MARKER))
        val found = MemoryIndex(quran, accepted.translation).find((lookup("TEST TRANSLATION", quran.surahs) as Lookup.Words).words)
        assertEquals(6236, found.size)
    }
}

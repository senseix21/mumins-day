package com.senseix21.muminsday.hisnul

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * ADR-27 on OBVIOUSLY FAKE fixtures (`src/test/resources/hisnul-fixture`, every text "TEST ..." or "পরীক্ষা ..."). The
 * real files are checked by [HisnulRealFilesTest] where they exist.
 */
class HisnulMuslimTest {

    private val fixture = File("src/test/resources/hisnul-fixture")
    private val chapters = File(fixture, CHAPTERS_FILE).readText(Charsets.UTF_8)
    private val duas: Map<String, String> = File(fixture, DUAS_FOLDER).listFiles().orEmpty().associate { it.name to it.readText(Charsets.UTF_8) }

    private fun accepted(chaptersText: String = chapters, duaTexts: Map<String, String> = duas): HisnulMuslim =
        assertIs<HisnulCheck.Accepted>(checkHisnulMuslim(chaptersText, duaTexts)).collection

    private fun refused(chaptersText: String = chapters, duaTexts: Map<String, String> = duas): List<String> =
        assertIs<HisnulCheck.Refused>(checkHisnulMuslim(chaptersText, duaTexts)).reasons

    private fun withDua(name: String, edit: (String) -> String): Map<String, String> = duas + (name to edit(duas.getValue(name)))

    @Test
    fun fixture_reads_into_typed_chapters_and_duas() {
        val collection = accepted()
        assertEquals(listOf(ChapterId(0), ChapterId(1)), collection.chapters.map { it.id })
        assertEquals(listOf(1, 2), collection.chapters.map { it.duaCount })
        assertEquals(3, collection.duas.size)
        assertEquals(4, collection.segmentCount)
    }

    @Test
    fun texts_are_the_source_strings_exactly_escapes_decoded_nothing_else_changed() {
        val collection = accepted()
        // The chapter name keeps its zero-width non-joiner; the Arabic keeps its CR LF; quotes and \/ are decoded only.
        assertEquals("পরীক্ষা অধ্যায় এক‌ TEST", collection.chapter(ChapterId(1))?.nameBn)
        val two = collection.duas.single { it.globalId == DuaId(2) }
        assertEquals("TEST ARABIC 2\r\nTEST LINE", two.segments.single().arabic)
        val three = collection.duas.single { it.globalId == DuaId(3) }
        assertEquals("পরীক্ষা অনুবাদ \"৩ক\" / TEST", three.segments[0].translation)
        val one = collection.duas.single { it.globalId == DuaId(1) }.segments.single()
        assertEquals(Segment("TEST BEFORE 1", "TEST ARABIC ১", "পরীক্ষা উচ্চারণ ১", "পরীক্ষা অনুবাদ ১", null, "পরীক্ষা সূত্র ১"), one)
    }

    @Test
    fun a_field_the_source_sent_as_null_stays_absent() {
        val two = accepted().duas.single { it.globalId == DuaId(2) }.segments.single()
        assertNull(two.transliteration)
        assertNull(two.translation)
        assertNull(two.reference)
        assertEquals("TEST AFTER 2", two.after)
    }

    @Test
    fun chapter_order_is_the_list_and_duas_follow_the_source_number_not_the_global_id() {
        val collection = accepted()
        assertEquals(listOf(DuaId(1), DuaId(3), DuaId(2)), collection.duas.map { it.globalId })
        assertEquals(listOf(1, 2), collection.duasOf(ChapterId(1)).map { it.number })
        assertNull(collection.duasOf(ChapterId(0)).single().number)
    }

    @Test
    fun the_chapter_name_comes_from_the_list_even_where_a_dua_file_has_none() {
        // 2.json has "chapname": null, as some real files do; the list's name is the one used.
        assertEquals("পরীক্ষা অধ্যায় এক‌ TEST", accepted().chapter(ChapterId(1))?.nameBn)
    }

    @Test
    fun a_missing_dua_is_refused_by_the_chapter_count() {
        val reasons = refused(duaTexts = duas - "2.json")
        assertTrue(reasons.any { "chapter 1" in it && "2 dua(s)" in it && "hold 1" in it }, reasons.toString())
    }

    @Test
    fun a_file_not_named_after_its_own_id_is_refused() {
        val reasons = refused(duaTexts = duas - "2.json" + ("9.json" to duas.getValue("2.json")))
        assertTrue(reasons.any { "duas/9.json" in it && "holds dua_global_id 2" in it }, reasons.toString())
    }

    @Test
    fun a_dua_in_an_unlisted_chapter_is_refused() {
        val reasons = refused(duaTexts = withDua("1.json") { it.replace("\"chap_id\":0", "\"chap_id\":7") })
        assertTrue(reasons.any { "chapter 7 is not in" in it }, reasons.toString())
    }

    @Test
    fun another_book_is_refused() {
        val reasons = refused(duaTexts = withDua("1.json") { it.replace("\"book_id\":1", "\"book_id\":2") })
        assertTrue(reasons.any { "duas/1.json" in it && "book_id" in it }, reasons.toString())
    }

    @Test
    fun malformed_json_is_a_reason_not_a_crash() {
        val reasons = refused(duaTexts = withDua("3.json") { it.dropLast(3) })
        assertTrue(reasons.any { "duas/3.json: not JSON" in it }, reasons.toString())
        assertTrue(refused(chaptersText = "{").single().startsWith("$CHAPTERS_FILE: not JSON"))
    }

    @Test
    fun a_text_field_of_the_wrong_type_is_refused() {
        val reasons = refused(duaTexts = withDua("1.json") { it.replace("\"bottom\":null", "\"bottom\":5") })
        assertTrue(reasons.any { "\"bottom\" is neither a text nor null" in it }, reasons.toString())
    }

    @Test
    fun a_dua_with_nothing_to_show_is_refused_and_a_note_alone_is_accepted() {
        val noArabic = withDua("2.json") { it.replace("\"arabic\":\"TEST ARABIC 2\\r\\nTEST LINE\"", "\"arabic\":null") }
        assertEquals(listOf(null, null, null, "TEST AFTER 2"), accepted(duaTexts = noArabic).duas.single { it.globalId == DuaId(2) }.segments.single().let { listOf(it.before, it.arabic, it.translation, it.after) })
        val reasons = refused(duaTexts = noArabic + ("2.json" to noArabic.getValue("2.json").replace("\"bottom\":\"TEST AFTER 2\"", "\"bottom\":null")))
        assertTrue(reasons.any { "duas/2.json: no segment has a text to show" in it }, reasons.toString())
    }

    @Test
    fun a_direction_control_in_a_text_is_refused_so_the_isolate_cannot_be_closed_early() {
        for (control in listOf('‮', '⁩')) {
            val reasons = refused(duaTexts = withDua("1.json") { it.replace("TEST ARABIC ১", "TEST ARABIC$control") })
            assertTrue(reasons.any { "direction control" in it }, reasons.toString())
        }
    }

    @Test
    fun a_repeated_dua_number_in_a_chapter_is_refused() {
        val reasons = refused(duaTexts = withDua("2.json") { it.replace("\"dua_id\":2", "\"dua_id\":1") })
        assertTrue(reasons.any { "dua number 1 appears more than once" in it }, reasons.toString())
    }

    @Test
    fun the_list_total_must_equal_its_entries() {
        val reasons = refused(chaptersText = chapters.replace("\"total\":2", "\"total\":3"))
        assertTrue(reasons.single().contains("\"total\" says 3"), reasons.toString())
    }

    @Test
    fun search_is_plain_containment_in_the_bengali_name() {
        val collection = accepted()
        assertEquals(collection.chapters, chaptersMatching(collection, "  "))
        assertEquals(listOf(ChapterId(0)), chaptersMatching(collection, "শূন্য").map { it.id })
        assertEquals(listOf(ChapterId(0), ChapterId(1)), chaptersMatching(collection, " পরীক্ষা ").map { it.id })
        assertEquals(emptyList(), chaptersMatching(collection, "নেই"))
    }
}

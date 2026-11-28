package com.senseix21.muminsday.tools.gate

import com.senseix21.muminsday.content.ContentKind
import com.senseix21.muminsday.content.fields
import com.senseix21.muminsday.content.key
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Fake records only (INV-C1): every value says TEST; the books are TEST BOOK A and TEST BOOK B. */
class KindGateTest {

    private fun record(kind: ContentKind, n: Int, vararg changes: Pair<String, String?>): String {
        val fields = linkedMapOf<String, String?>()
        for (field in kind.fields()) {
            val key = kind.key(field) ?: continue
            if (key == "retired_in") continue
            fields[key] = when (key) {
                "added_in" -> "1"
                "status" -> "\"approved\""
                "review_date" -> "\"2000-01-01\""
                "source", "collection" -> "\"TEST BOOK A\""
                "id" -> "\"test-$n\""
                else -> "\"TEST $key $n\""
            }
        }
        for ((key, value) in changes) if (value == null) fields.remove(key) else fields[key] = value
        return fields.entries.joinToString(", ", "{", "}") { "\"${it.key}\": ${it.value}" }
    }

    private fun file(kind: ContentKind, records: List<String> = (1..30).map { record(kind, it) }, fixture: Boolean = false, extra: String = "") =
        """{ "version": 1, "test_fixture": $fixture, "sources": ["TEST BOOK A", "TEST BOOK B"],$extra "${kind.listKey}": [${records.joinToString(",\n")}] }"""

    @Test
    fun T_BUILD_4_INV_C6_a_complete_file_passes_for_each_kind() {
        for (kind in ContentKind.entries) {
            val report = runKindGate(kind, file(kind), required = false, allowTestFixture = false)
            assertTrue(report.passed, report.lines.toString())
            assertEquals(1, report.lines.size)
            assertTrue("30 approved, 0 retired, 2 source(s)." in report.lines.single(), report.lines.toString())
        }
    }

    @Test
    fun T_BUILD_4_absence_passes_with_a_notice_until_the_kind_is_required() {
        for (kind in ContentKind.entries) {
            val notice = runKindGate(kind, null, required = false, allowTestFixture = false)
            assertTrue(notice.passed)
            assertTrue("no content/${kind.fileName} yet" in notice.lines.single(), notice.lines.toString())
            assertFalse(runKindGate(kind, null, required = true, allowTestFixture = false).passed)
        }
    }

    @Test
    fun INV_C6_the_shipping_gate_refuses_a_test_fixture_and_the_debug_gate_accepts_it() {
        for (kind in ContentKind.entries) {
            val fixture = file(kind, fixture = true)
            val refused = runKindGate(kind, fixture, required = true, allowTestFixture = false)
            assertFalse(refused.passed)
            assertTrue(refused.lines.any { "\"test_fixture\" is true" in it }, refused.lines.toString())
            assertTrue(runKindGate(kind, fixture, required = true, allowTestFixture = true).passed)
        }
    }

    @Test
    fun the_hadith_file_uses_its_own_key_names_and_refuses_dua_keys() {
        val lines = runKindGate(ContentKind.Hadith, file(ContentKind.Hadith, listOf(record(ContentKind.Hadith, 1, "occasion" to "\"TEST\"")) + (2..30).map { record(ContentKind.Hadith, it) }), false, false).lines
        assertTrue(lines.any { "hadith[0] (\"test-1\"): unknown key \"occasion\"" in it }, lines.toString())
        val missing = runKindGate(ContentKind.Hadith, file(ContentKind.Hadith, listOf(record(ContentKind.Hadith, 1, "collection" to null)) + (2..30).map { record(ContentKind.Hadith, it) }), false, false).lines
        assertTrue(missing.any { "\"collection\" is missing" in it }, missing.toString())
    }

    @Test
    fun a_book_outside_the_reviewers_list_is_named() {
        val records = listOf(record(ContentKind.Dua, 1, "source" to "\"TEST BOOK C\"")) + (2..30).map { record(ContentKind.Dua, it) }
        val lines = runKindGate(ContentKind.Dua, file(ContentKind.Dua, records), false, false).lines
        assertTrue(lines.any { "duas[0] (\"test-1\"): \"source\" names a book that is not in the file's \"sources\" list" in it }, lines.toString())
    }

    @Test
    fun shape_errors_are_reported_before_any_judgement() {
        val lines = runKindGate(ContentKind.Dua, file(ContentKind.Dua, extra = " \"extra\": 1,"), false, false).lines
        assertTrue(lines.any { "unknown top-level key \"extra\"" in it }, lines.toString())
        val numberAsText = runKindGate(ContentKind.Dua, file(ContentKind.Dua, listOf(record(ContentKind.Dua, 1, "text_ar" to "5")) + (2..30).map { record(ContentKind.Dua, it) }), false, false).lines
        assertTrue(numberAsText.any { "\"text_ar\" must be text in quotes" in it }, numberAsText.toString())
        assertFalse(runKindGate(ContentKind.Dua, "{", false, false).passed)
        assertFalse(runKindGate(ContentKind.Dua, "[]", false, false).passed)
    }

    @Test
    fun the_debug_fixtures_are_fake_and_pass_the_debug_gate() {
        for (kind in ContentKind.entries) {
            val text = File("../../app/src/debug/assets/${kind.fileName}").readText()
            assertTrue(runKindGate(kind, text, required = true, allowTestFixture = true).passed)
            assertFalse(runKindGate(kind, text, required = true, allowTestFixture = false).passed, "a fixture must never pass the shipping gate")
            // Every text value in the fixture announces itself as a test, so none can be mistaken for content.
            val values = Regex("\"(text_ar|transliteration|text_bn|text_en|occasion|grading|source_edition|edition)\": \"([^\"]*)\"").findAll(text).map { it.groupValues[2] }.toList()
            assertTrue(values.isNotEmpty())
            assertTrue(values.all { it.startsWith("TEST ") || it.startsWith("পরীক্ষা ") }, values.filterNot { it.startsWith("TEST ") }.take(3).toString())
        }
    }
}

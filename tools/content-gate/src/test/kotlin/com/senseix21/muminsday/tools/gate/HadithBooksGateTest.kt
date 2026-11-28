package com.senseix21.muminsday.tools.gate

import com.senseix21.muminsday.hadithbooks.EDITIONS_FOLDER
import com.senseix21.muminsday.hadithbooks.HadithBook
import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * ADR-28's gate over the core module's FAKE fixture (every text "TEST ..." or "পরীক্ষা ..."), copied under each of the
 * eight books' file names; never the real files.
 */
class HadithBooksGateTest {

    private val fixture = File("../../core/src/test/resources/hadithbooks-fixture")
    private val bengali = File(fixture, "ben-test.json").readBytes()
    private val arabic = File(fixture, "ara-test.json").readBytes()
    private val files: Map<String, ByteArray> = HadithBook.entries.flatMap {
        listOf("$EDITIONS_FOLDER/${it.bengaliFile}" to bengali, "$EDITIONS_FOLDER/${it.arabicFile}" to arabic)
    }.toMap() + ("info.min.json" to "{}".toByteArray())

    /** The fixture's counts: 5 numbers, 3 sections, 3 Bengali texts (one is empty), 4 Arabic, 1 graded, 2 unpaired. */
    private fun record(of: Map<String, ByteArray> = files, hadiths: Int = 5): String =
        HadithBook.entries.joinToString("") { book ->
            listOf("hadiths" to hadiths, "sections" to 3, "bengali" to 3, "arabic" to 4, "graded" to 1, "unpaired" to 2)
                .joinToString("") { (what, n) -> "count ${book.key} $what $n\n" }
        } + of.entries.joinToString("") { "sha256 ${sha256(it.value)} ${it.key}\n" }

    private fun problems(sources: String, present: Map<String, ByteArray>?): List<String> =
        runHadithBooksGate(sources, present).also { assertFalse(it.passed, it.lines.joinToString("\n")) }.lines

    @Test
    fun the_fixture_with_its_hashes_and_counts_passes() {
        val report = runHadithBooksGate(record(), files)
        assertTrue(report.passed, report.lines.joinToString("\n"))
        assertTrue("17 files match" in report.lines.single() && "8 books, 40 hadiths" in report.lines.single(), report.lines.single())
    }

    @Test
    fun absent_folder_passes_with_a_notice() {
        val report = runHadithBooksGate("", null)
        assertTrue(report.passed)
        assertTrue("absent" in report.lines.single())
    }

    @Test
    fun one_changed_byte_fails() {
        val name = "$EDITIONS_FOLDER/${HadithBook.Muslim.bengaliFile}"
        val changed = files + (name to bengali.toString(Charsets.UTF_8).replace("TEST SECTION ONE", "TEST SECTION 1").toByteArray())
        assertTrue(problems(record(), changed).any { "$name has SHA-256" in it })
    }

    @Test
    fun an_unrecorded_or_missing_file_fails() {
        assertTrue(problems(record(), files + ("extra.json" to "{}".toByteArray())).any { "extra.json is in content/local" in it })
        val missing = "$EDITIONS_FOLDER/${HadithBook.Malik.arabicFile}"
        assertTrue(problems(record(), files - missing).any { "$missing is recorded but not in" in it })
        assertTrue(problems(record(files - missing), files - missing).any { "$missing is not recorded" in it })
    }

    @Test
    fun counts_that_differ_from_the_record_fail() {
        assertTrue(problems(record(hadiths = 7589), files).any { "bukhari: 5 hadiths, the record says 7589" in it })
        assertTrue(problems(record().replace("count nawawi graded 1\n", ""), files).any { "no `count nawawi graded <n>` line" in it })
    }

    @Test
    fun a_book_whose_editions_disagree_fails_even_with_its_hashes_recorded() {
        val name = "$EDITIONS_FOLDER/${HadithBook.Tirmidhi.arabicFile}"
        val broken = files + (name to arabic.toString(Charsets.UTF_8).replace("TEST GRADE 2", "TEST GRADE X").toByteArray())
        assertTrue(problems(record(broken), broken).any { "tirmidhi: hadith 1: the editions give different grades" in it })
    }
}

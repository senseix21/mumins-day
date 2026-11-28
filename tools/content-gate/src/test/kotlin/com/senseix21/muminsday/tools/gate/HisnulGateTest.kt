package com.senseix21.muminsday.tools.gate

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** ADR-27's gate over the core module's FAKE fixture (every text "TEST ..." or "পরীক্ষা ..."), never the real files. */
class HisnulGateTest {

    private val fixture = File("../../core/src/test/resources/hisnul-fixture")
    private val files: Map<String, ByteArray> = fixture.walkTopDown().filter { it.isFile }
        .associate { it.relativeTo(fixture).invariantSeparatorsPath to it.readBytes() }

    private fun record(of: Map<String, ByteArray> = files, chapters: Int = 2, duas: Int = 3, segments: Int = 4): String =
        "count chapters $chapters\ncount duas $duas\ncount segments $segments\n" +
            of.entries.joinToString("") { "sha256 ${sha256(it.value)} ${it.key}\n" }

    private fun problems(sources: String, present: Map<String, ByteArray>?): List<String> =
        runHisnulGate(sources, present).also { assertFalse(it.passed, it.lines.joinToString("\n")) }.lines

    @Test
    fun the_fixture_with_its_hashes_and_counts_passes() {
        val report = runHisnulGate(record(), files)
        assertTrue(report.passed, report.lines.joinToString("\n"))
        assertTrue("2 chapters, 3 duas, 4 segments" in report.lines.single())
    }

    @Test
    fun absent_folder_passes_with_a_notice() {
        val report = runHisnulGate("", null)
        assertTrue(report.passed)
        assertTrue("absent" in report.lines.single())
    }

    @Test
    fun one_changed_byte_fails() {
        val changed = files + ("duas/1.json" to files.getValue("duas/1.json").toString(Charsets.UTF_8).replace("TEST BEFORE 1", "TEST BEFORE 2").toByteArray())
        assertTrue(problems(record(), changed).any { "duas/1.json has SHA-256" in it })
    }

    @Test
    fun an_unrecorded_or_missing_file_fails() {
        assertTrue(problems(record(), files + ("duas/4.json" to "{}".toByteArray())).any { "duas/4.json is in content/local" in it })
        assertTrue(problems(record(), files - "duas/3.json").any { "duas/3.json is recorded but not in" in it })
    }

    @Test
    fun counts_that_differ_from_the_record_fail() {
        assertTrue(problems(record(duas = 312), files).any { "3 duas, the record says 312" in it })
        assertTrue(problems(record().replace("count segments 4\n", ""), files).any { "no `count segments <n>` line" in it })
    }

    @Test
    fun a_file_that_does_not_read_as_the_collection_fails_even_with_its_hash_recorded() {
        val broken = files + ("duas/2.json" to files.getValue("duas/2.json").toString(Charsets.UTF_8).replace("\"chap_id\":1", "\"chap_id\":9").toByteArray())
        assertTrue(problems(record(broken), broken).any { "chapter 9 is not in" in it })
    }
}

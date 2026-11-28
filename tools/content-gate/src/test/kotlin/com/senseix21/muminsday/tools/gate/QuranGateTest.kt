package com.senseix21.muminsday.tools.gate

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** T-BUILD-6 (INV-QT1) over the real files, and over copies tampered with in memory. The repository is never changed. */
class QuranGateTest {

    private val content = File("../../content")
    private val sources = File(content, "quran-SOURCES.md").readText()
    private val quranDirectory = File(content, "quran")
    private val files: Map<String, ByteArray> = quranDirectory.walkTopDown().filter { it.isFile }
        .associate { it.relativeTo(quranDirectory).invariantSeparatorsPath to it.readBytes() }
    private val paths = setOf("quran-SOURCES.md", "quranenc-terms.txt") + files.keys.map { "quran/$it" }

    private fun run(files: Map<String, ByteArray> = this.files, sources: String = this.sources, paths: Set<String> = this.paths) =
        runQuranGate(sources, files, paths)

    @Test
    fun T_BUILD_6_the_shipped_files_match_their_recorded_hashes_and_describe_114_surahs() {
        val report = run()
        assertTrue(report.passed, report.lines.toString())
        assertEquals(
            listOf(
                "quran gate: passed. 120 files match their recorded SHA-256; 114 surahs, 6236 ayahs, as the metadata gives; " +
                    "2 translation(s): bengali_zakaria 6236 ayahs, bengali_rwwad 6236 ayahs.",
            ),
            report.lines,
        )
    }

    @Test
    fun T_BUILD_6_changing_one_byte_of_the_text_fails_the_build() {
        for (name in files.keys) {
            val tampered = files.getValue(name).copyOf().also { it[it.size / 2] = (it[it.size / 2] + 1).toByte() }
            val report = run(files + (name to tampered))
            assertFalse(report.passed, name)
            assertTrue(report.lines.any { name in it && "must not be changed" in it }, report.lines.toString())
        }
    }

    @Test
    fun T_BUILD_6_a_trailing_newline_or_a_converted_line_ending_fails_the_build() {
        val text = files.getValue(QURAN_TEXT)
        assertFalse(run(files + (QURAN_TEXT to text + '\n'.code.toByte())).passed)
        val crlf = text.toString(Charsets.UTF_8).replace("\n", "\r\n").toByteArray(Charsets.UTF_8)
        assertFalse(run(files + (QURAN_TEXT to crlf)).passed)
    }

    @Test
    fun T_BUILD_6_an_unrecorded_file_or_a_missing_one_fails() {
        assertTrue(run(files + ("extra.txt" to byteArrayOf(1))).lines.any { "extra.txt is in content/quran/ but has no recorded hash" in it })
        assertTrue(run(files - QURAN_METADATA).lines.any { "$QURAN_METADATA is recorded but not in content/quran/" in it })
        assertFalse(run(sources = "").passed)
    }

    private val zakaria = "${TRANSLATIONS}bengali_zakaria/"
    private val rwwad = "${TRANSLATIONS}bengali_rwwad/bengali_rwwad_v1.1.4-xml.1.xml"

    @Test
    fun T_BUILD_6_changing_one_byte_of_the_zakaria_translation_fails_the_build() {
        val name = zakaria + "002.json"
        val tampered = files.getValue(name).copyOf().also { it[it.size / 2] = (it[it.size / 2] + 1).toByte() }
        val report = run(files + (name to tampered))
        assertFalse(report.passed)
        assertTrue(report.lines.any { name in it && "must not be changed" in it }, report.lines.toString())
    }

    @Test
    fun T_BUILD_6_changing_one_byte_of_the_rwwad_translation_fails_the_build() {
        val tampered = files.getValue(rwwad).copyOf().also { it[it.size / 2] = (it[it.size / 2] + 1).toByte() }
        val report = run(files + (rwwad to tampered))
        assertFalse(report.passed)
        assertTrue(report.lines.any { rwwad in it && "must not be changed" in it }, report.lines.toString())
    }

    /** [sources] with [name]'s recorded hash replaced by [bytes]'s, so only the content check can refuse it. */
    private fun recorded(name: String, bytes: ByteArray): String =
        sources.replace(Regex("sha256 [0-9a-f]{64} ${Regex.escape(name)}\\n"), "sha256 ${sha256(bytes)} $name\n")

    @Test
    fun INV_QT1_a_translation_missing_one_ayah_fails_even_with_its_hash_recorded() {
        // Zakaria: drop the last entry of surah 1's response.
        val first = zakaria + "001.json"
        val body = files.getValue(first).toString(Charsets.UTF_8)
        val shorter = (body.substring(0, body.lastIndexOf("},{") + 1) + "]}").toByteArray()
        val zakariaReport = run(files + (first to shorter), recorded(first, shorter))
        assertFalse(zakariaReport.passed)
        assertTrue(zakariaReport.lines.any { "bengali_zakaria" in it && "not numbered like the text" in it }, zakariaReport.lines.toString())
        // Rowwad: drop one whole <aya> element.
        val xml = files.getValue(rwwad).toString(Charsets.UTF_8)
        val start = xml.indexOf("<aya number=\"2\">")
        val cut = (xml.substring(0, start) + xml.substring(xml.indexOf("</aya>", start) + "</aya>".length)).toByteArray()
        val rwwadReport = run(files + (rwwad to cut), recorded(rwwad, cut))
        assertFalse(rwwadReport.passed)
        assertTrue(rwwadReport.lines.any { "bengali_rwwad" in it }, rwwadReport.lines.toString())
    }

    @Test
    fun INV_QT1_a_translation_needs_its_permission_recorded_and_present_in_content() {
        val noPermission = run(sources = sources.replace(Regex("^permission bengali_rwwad .*\\n", RegexOption.MULTILINE), ""))
        assertTrue(noPermission.lines.any { "bengali_rwwad is a translation without" in it }, noPermission.lines.toString())
        assertFalse(run(paths = paths - "quranenc-terms.txt").passed, "the permission file must exist")
    }

    @Test
    fun INV_QT1_only_a_bundled_edition_in_its_own_folder_can_ship() {
        val other = byteArrayOf(1)
        for (path in listOf("${TRANSLATIONS}english_saheeh/001.json", "translation-bn.txt", "${TRANSLATIONS}bengali_rwwad/a/b.xml")) {
            val report = run(files + (path to other), sources + "sha256 ${sha256(other)} $path\n")
            assertFalse(report.passed, path)
            assertTrue(report.lines.any { path in it }, report.lines.toString())
        }
    }

    @Test
    fun the_texts_must_agree_with_the_metadata() {
        val lines = files.getValue(QURAN_SEARCH_TEXT).toString(Charsets.UTF_8).split('\n')
        val shorter = (lines.take(5) + lines.drop(6)).joinToString("\n").toByteArray(Charsets.UTF_8)
        val sources = sources.replace(Regex("sha256 [0-9a-f]{64} $QURAN_SEARCH_TEXT"), "sha256 ${sha256(shorter)} $QURAN_SEARCH_TEXT")
        val report = run(files + (QURAN_SEARCH_TEXT to shorter), sources)
        assertFalse(report.passed)
        assertTrue(report.lines.any { "do not describe one Quran" in it }, report.lines.toString())
    }
}

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
    private val files: Map<String, ByteArray> = File(content, "quran").listFiles().orEmpty().filter { it.isFile }.associate { it.name to it.readBytes() }
    private val paths = setOf("quran-SOURCES.md") + files.keys.map { "quran/$it" }

    private fun run(files: Map<String, ByteArray> = this.files, sources: String = this.sources, paths: Set<String> = this.paths) =
        runQuranGate(sources, files, paths)

    @Test
    fun T_BUILD_6_the_shipped_files_match_their_recorded_hashes_and_describe_114_surahs() {
        val report = run()
        assertTrue(report.passed, report.lines.toString())
        assertEquals(listOf("quran gate: passed. 5 files match their recorded SHA-256; 114 surahs, 6236 ayahs, as the metadata gives; 0 translation(s)."), report.lines)
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

    @Test
    fun INV_QT1_a_translation_needs_a_recorded_hash_and_a_written_permission_in_content() {
        val refs = files.getValue(QURAN_TEXT).toString(Charsets.UTF_8).split('\n').filter { it.isNotEmpty() && !it.startsWith("#") }
            .map { it.split('|').take(2).joinToString("|") }
        val translation = refs.joinToString("\n") { "$it|TEST TRANSLATION" }.toByteArray()
        val hashLine = "sha256 ${sha256(translation)} translation-bn.txt\n"
        val withFile = files + ("translation-bn.txt" to translation)
        val noPermission = run(withFile, sources + hashLine)
        assertTrue(noPermission.lines.any { "translation-bn.txt is a translation without" in it }, noPermission.lines.toString())
        val permissionLine = "permission translation-bn.txt permissions/TEST-PERMISSION.md\n"
        assertFalse(run(withFile, sources + hashLine + permissionLine).passed, "the permission file must exist")
        // With the permission recorded, a complete translation passes (in memory only; nothing like it is in content/).
        val passed = run(withFile, sources + hashLine + permissionLine, paths + "permissions/TEST-PERMISSION.md")
        assertTrue(passed.passed, passed.lines.toString())
        assertTrue("1 translation(s)" in passed.lines.single())
        // The FAKE debug translation, marked as a fixture, can never pass, even with a hash and a permission.
        val fixture = ("# test_fixture\n" + translation.toString(Charsets.UTF_8)).toByteArray()
        val fixtureSources = sources + "sha256 ${sha256(fixture)} translation-bn.txt\n" + permissionLine
        val refused = run(files + ("translation-bn.txt" to fixture), fixtureSources, paths + "permissions/TEST-PERMISSION.md")
        assertTrue(refused.lines.any { "is a test fixture and cannot ship" in it }, refused.lines.toString())
        // An incomplete translation is refused.
        val partial = translation.toString(Charsets.UTF_8).substringBeforeLast('\n').toByteArray()
        assertFalse(run(files + ("translation-bn.txt" to partial), sources + "sha256 ${sha256(partial)} translation-bn.txt\n" + permissionLine, paths + "permissions/TEST-PERMISSION.md").passed)
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

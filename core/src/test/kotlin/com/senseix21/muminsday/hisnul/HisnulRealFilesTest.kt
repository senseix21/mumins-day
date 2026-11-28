package com.senseix21.muminsday.hisnul

import java.io.File
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue

/**
 * ADR-27 over the real files in `content/local/hisnul-muslim/`, which exist only on a machine that fetched them: they are
 * never committed, so on CI or a fresh clone every test here is SKIPPED with that reason. No dua text is typed here:
 * every string compared is taken from the files themselves.
 */
class HisnulRealFilesTest {

    private val directory = File("../content/local/hisnul-muslim")
    private val sources = File("../content/hisnul-muslim-SOURCES.md")

    private fun present() = assumeTrue(
        directory.isDirectory,
        "SKIPPED: content/local/hisnul-muslim is absent (CI or a fresh clone; ADR-27). tools/fetch-hisnul-muslim.sh fetches it.",
    )

    private fun files(): Map<String, ByteArray> = directory.walkTopDown().filter { it.isFile && !it.name.startsWith(".") }
        .associate { it.relativeTo(directory).invariantSeparatorsPath to it.readBytes() }

    private fun collection(files: Map<String, ByteArray>): HisnulMuslim = assertIs<HisnulCheck.Accepted>(
        checkHisnulMuslim(
            files.getValue(CHAPTERS_FILE).toString(Charsets.UTF_8),
            files.filterKeys { it.startsWith("$DUAS_FOLDER/") }.mapKeys { it.key.removePrefix("$DUAS_FOLDER/") }.mapValues { it.value.toString(Charsets.UTF_8) },
        ),
    ).collection

    @Test
    fun every_file_has_the_sha256_recorded_in_sources_and_nothing_is_unrecorded() {
        present()
        val recorded = Regex("^sha256 ([0-9a-f]{64}) (\\S+)$", RegexOption.MULTILINE).findAll(sources.readText(Charsets.UTF_8))
            .associate { it.groupValues[2] to it.groupValues[1] }
        val files = files()
        assertEquals(313, recorded.size)
        assertEquals(recorded.keys, files.keys)
        for ((name, bytes) in files) {
            val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
            assertEquals(recorded[name], hash, name)
        }
    }

    @Test
    fun the_collection_has_312_duas_in_133_chapters_with_the_counts_of_the_fetch() {
        present()
        val collection = collection(files())
        assertEquals(133, collection.chapters.size)
        assertEquals((0..132).toList(), collection.chapters.map { it.id.value })
        assertEquals(312, collection.duas.size)
        val segments = collection.duas.flatMap { it.segments }
        assertEquals(426, segments.size)
        assertEquals(414, segments.count { it.arabic != null })
        assertEquals(409, segments.count { it.translation != null })
        assertEquals(390, segments.count { it.transliteration != null })
        assertEquals(315, segments.count { it.reference != null })
        // Arabic without Bengali (KNOWN at the fetch); the screen shows only what the source has.
        assertEquals(6, segments.count { it.arabic != null && it.translation == null })
        assertEquals(16, collection.duas.count { dua -> dua.segments.all { it.translation == null } })
        assertEquals(11, collection.duas.count { dua -> dua.segments.all { it.translation == null && it.arabic == null } })
    }

    @Test
    fun every_text_shown_is_the_exact_string_in_its_file() {
        present()
        val files = files()
        val collection = collection(files)
        var compared = 0
        for (dua in collection.duas) {
            val raw = files.getValue("$DUAS_FOLDER/${dua.globalId.value}.json").toString(Charsets.UTF_8)
            for (segment in dua.segments) {
                for (text in listOfNotNull(segment.before, segment.arabic, segment.transliteration, segment.translation, segment.after, segment.reference)) {
                    assertTrue("\"${jsonEscaped(text)}\"" in raw, "dua ${dua.globalId.value}: a text is not found as written in its file")
                    compared++
                }
            }
        }
        // 160 notes before, 414 Arabic, 390 transliterations, 409 translations, 121 notes after, 315 references.
        assertEquals(1809, compared)
        val list = files.getValue(CHAPTERS_FILE).toString(Charsets.UTF_8)
        for (chapter in collection.chapters) assertTrue("\"chapname\":\"${jsonEscaped(chapter.nameBn)}\"" in list, "chapter ${chapter.id.value}")
    }

    /** The escaping the files use (KNOWN: raw UTF-8, with \r, \n, \" and \\ escaped), to look a decoded value up in its file. */
    private fun jsonEscaped(text: String): String = buildString {
        for (char in text) {
            when (char) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\r' -> append("\\r")
                '\n' -> append("\\n")
                '\t' -> append("\\t")
                else -> append(char)
            }
        }
    }
}

package com.senseix21.muminsday.hadithbooks

import java.io.File
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue

/**
 * ADR-28 over the real files in `content/local/hadith/`, which exist only on a machine that fetched them: they are never
 * committed, so on CI or a fresh clone every test here is SKIPPED with that reason. No hadith text is typed here: every
 * string compared is taken from the files themselves. One book at a time, so at most two editions are in memory.
 */
class HadithBooksRealFilesTest {

    private val directory = File("../content/local/hadith")
    private val sources = File("../content/hadith-books-SOURCES.md").readText(Charsets.UTF_8)

    private fun present() = assumeTrue(
        directory.isDirectory,
        "SKIPPED: content/local/hadith is absent (CI or a fresh clone; ADR-28). tools/fetch-hadith-books.sh fetches it.",
    )

    private fun text(book: HadithBook, file: String) = File(directory, "$EDITIONS_FOLDER/$file").readText(Charsets.UTF_8)

    private fun read(book: HadithBook): Book =
        assertIs<BookCheck.Accepted>(checkHadithBook(book, text(book, book.bengaliFile), text(book, book.arabicFile)), book.key).book

    private val counts: Map<String, Int> = Regex("^count (\\S+ \\S+) ([0-9]+)$", RegexOption.MULTILINE).findAll(sources)
        .associate { it.groupValues[1] to it.groupValues[2].toInt() }

    @Test
    fun every_file_has_the_sha256_recorded_in_sources_and_nothing_is_unrecorded() {
        present()
        val recorded = Regex("^sha256 ([0-9a-f]{64}) (\\S+)$", RegexOption.MULTILINE).findAll(sources)
            .associate { it.groupValues[2] to it.groupValues[1] }
        val files = directory.walkTopDown().filter { it.isFile && !it.name.startsWith(".") }.associateBy { it.relativeTo(directory).invariantSeparatorsPath }
        assertEquals(18, recorded.size)
        assertEquals(recorded.keys, files.keys)
        for ((name, file) in files) {
            val hash = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }
            assertEquals(recorded[name], hash, name)
        }
    }

    @Test
    fun every_book_reads_with_the_counts_recorded_in_sources() {
        present()
        assertEquals(HadithBook.entries.size * 6, counts.size)
        for (book in HadithBook.entries) {
            val read = read(book)
            val found = bookCounts(read)
            for ((what, count) in found) assertEquals(counts["${book.key} $what"], count, "${book.key} $what")
            assertEquals(read.hadiths.size, read.sections.sumOf { it.hadithCount }, "${book.key}: every hadith is in one section")
        }
    }

    @Test
    fun every_text_shown_is_the_exact_string_in_its_file() {
        present()
        var compared = 0
        for (book in HadithBook.entries) {
            val bengali = text(book, book.bengaliFile)
            val arabic = text(book, book.arabicFile)
            val read = assertIs<BookCheck.Accepted>(checkHadithBook(book, bengali, arabic)).book
            val bengaliTexts = rawValues(bengali, "\"text\":\"")
            val arabicTexts = rawValues(arabic, "\"text\":\"")
            val grades = rawValues(bengali, "\"grade\":\"")
            val numbers = Regex("\"hadithnumber\":([0-9.]+),").findAll(bengali).map { it.groupValues[1] }.toSet()
            for (hadith in read.hadiths) {
                hadith.bengali?.let { assertTrue(jsonEscaped(it) in bengaliTexts, "${book.key} ${hadith.number.written}: Bengali"); compared++ }
                hadith.arabic?.let { assertTrue(jsonEscaped(it) in arabicTexts, "${book.key} ${hadith.number.written}: Arabic"); compared++ }
                for (grade in hadith.grades) assertTrue(jsonEscaped(grade.grade) in grades, "${book.key} ${hadith.number.written}: grade")
                assertTrue(hadith.number.written in numbers, "${book.key} ${hadith.number.written}: the number as written")
            }
            for (section in read.sections) assertTrue("\"${section.id.value}\":\"${jsonEscaped(section.name)}\"" in bengali, "${book.key} section ${section.id.value}")
        }
        // Each of the 36,432 hadiths, in each edition that has it: 72,864 texts.
        assertEquals(72_864, compared)
    }

    private fun bookCounts(book: Book): Map<String, Int> = mapOf(
        "hadiths" to book.hadiths.size,
        "sections" to book.sections.size,
        "bengali" to book.hadiths.count { !it.bengali.isNullOrBlank() },
        "arabic" to book.hadiths.count { !it.arabic.isNullOrBlank() },
        "graded" to book.hadiths.count { it.grades.isNotEmpty() },
        "unpaired" to book.onlyBengali + book.onlyArabic,
    )

    /** Every raw (still escaped) string value that follows [opening] in [file], read up to its unescaped closing quote. */
    private fun rawValues(file: String, opening: String): Set<String> {
        val values = HashSet<String>()
        var at = file.indexOf(opening)
        while (at >= 0) {
            val start = at + opening.length
            var end = start
            while (file[end] != '"') end += if (file[end] == '\\') 2 else 1
            values += file.substring(start, end)
            at = file.indexOf(opening, end)
        }
        return values
    }

    /** The escaping the files use (KNOWN: raw UTF-8, with \" and \\ escaped), to look a decoded value up in its file. */
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

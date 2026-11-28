package com.senseix21.muminsday.tools.gate

import com.senseix21.muminsday.hadithbooks.Book
import com.senseix21.muminsday.hadithbooks.BookCheck
import com.senseix21.muminsday.hadithbooks.EDITIONS_FOLDER
import com.senseix21.muminsday.hadithbooks.HadithBook
import com.senseix21.muminsday.hadithbooks.checkHadithBook
import java.io.File
import kotlin.system.exitProcess

/** `sha256 <64 hex> <path under content/local/hadith/>` in `content/hadith-books-SOURCES.md`. */
private val HASH_LINE = Regex("^sha256 ([0-9a-f]{64}) (\\S+)$", RegexOption.MULTILINE)

/** `count <book key> <what> <n>` in the same record. */
private val COUNT_LINE = Regex("^count ([a-z]+) ([a-z]+) ([0-9]+)$", RegexOption.MULTILINE)

/** What each book's `count` lines must state, in this order. */
internal val COUNTED: List<String> = listOf("hadiths", "sections", "bengali", "arabic", "graded", "unpaired")

/**
 * ADR-28. [files] is every file under `content/local/hadith/` by its path there, or null when that folder does not exist
 * (CI, a fresh clone, a release machine): then there is nothing to package and the gate passes with a notice. When the
 * files are there, every one must have the SHA-256 recorded in [sources], none may be unrecorded or missing, each of the
 * eight books' two editions must read as one book (`checkHadithBook`), and every count must be the record's.
 */
fun runHadithBooksGate(sources: String, files: Map<String, ByteArray>?): GateReport {
    if (files == null) {
        return GateReport(true, listOf("hadith books gate: passed. content/local/hadith is absent, so no build packages it (ADR-28)."))
    }
    val problems = mutableListOf<String>()
    val recorded = HASH_LINE.findAll(sources).associate { it.groupValues[2] to it.groupValues[1] }
    val counts = COUNT_LINE.findAll(sources).associate { "${it.groupValues[1]} ${it.groupValues[2]}" to it.groupValues[3].toInt() }
    if (recorded.isEmpty()) problems += "content/hadith-books-SOURCES.md records no `sha256 <hex> <path>` line"
    for (book in HadithBook.entries) {
        for (what in COUNTED) if ("${book.key} $what" !in counts) problems += "content/hadith-books-SOURCES.md has no `count ${book.key} $what <n>` line"
        for (name in listOf(book.bengaliFile, book.arabicFile)) {
            if ("$EDITIONS_FOLDER/$name" !in recorded) problems += "$EDITIONS_FOLDER/$name is not recorded"
        }
    }
    for ((name, hash) in recorded) {
        val bytes = files[name]
        when {
            bytes == null -> problems += "$name is recorded but not in content/local/hadith/"
            sha256(bytes) != hash -> problems += "$name has SHA-256 ${sha256(bytes)}, recorded $hash: the file must stay as fetched"
        }
    }
    for (name in (files.keys - recorded.keys).sorted()) problems += "$name is in content/local/hadith/ but has no recorded hash"
    if (problems.isNotEmpty()) return failed(problems)

    var total = 0
    for (book in HadithBook.entries) {
        val bengali = files.getValue("$EDITIONS_FOLDER/${book.bengaliFile}").toString(Charsets.UTF_8)
        val arabic = files.getValue("$EDITIONS_FOLDER/${book.arabicFile}").toString(Charsets.UTF_8)
        when (val check = checkHadithBook(book, bengali, arabic)) {
            is BookCheck.Refused -> problems += check.reasons.map { "${book.key}: $it" }
            is BookCheck.Accepted -> {
                total += check.book.hadiths.size
                for ((what, count) in countsOf(check.book)) {
                    if (counts["${book.key} $what"] != count) problems += "${book.key}: $count $what, the record says ${counts["${book.key} $what"]}"
                }
            }
        }
    }
    if (problems.isNotEmpty()) return failed(problems)
    return GateReport(
        true,
        listOf(
            "hadith books gate: passed. ${recorded.size} files match their recorded SHA-256; ${HadithBook.entries.size} books, $total hadiths, " +
                "every count as recorded. UNREVIEWED: packaged into debug builds only (ADR-28).",
        ),
    )
}

/** The six counts the record states for a book; `HadithBooksRealFilesTest` counts the same way. */
internal fun countsOf(book: Book): Map<String, Int> = mapOf(
    "hadiths" to book.hadiths.size,
    "sections" to book.sections.size,
    "bengali" to book.hadiths.count { !it.bengali.isNullOrBlank() },
    "arabic" to book.hadiths.count { !it.arabic.isNullOrBlank() },
    "graded" to book.hadiths.count { it.grades.isNotEmpty() },
    "unpaired" to book.onlyBengali + book.onlyArabic,
)

private fun failed(problems: List<String>) = GateReport(false, listOf("hadith books gate: FAILED. ${problems.size} problem(s):") + problems.map { "  - $it" })

/** Arguments: the path of `content/hadith-books-SOURCES.md` and of `content/local/hadith/`. */
object HadithBooksMain {
    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size == 2) { "usage: <hadith-books-SOURCES.md> <content/local/hadith>" }
        val sourcesFile = File(args[0])
        val sources = if (sourcesFile.isFile) sourcesFile.readText(Charsets.UTF_8) else ""
        val folder = File(args[1])
        // Hidden files (a Finder `.DS_Store`) are not content; the debug staging skips them too.
        val files = if (!folder.isDirectory) {
            null
        } else {
            folder.walkTopDown().filter { it.isFile && !it.name.startsWith(".") }
                .associate { it.relativeTo(folder).invariantSeparatorsPath to it.readBytes() }
        }
        val report = runHadithBooksGate(sources, files)
        report.lines.forEach(::println)
        if (!report.passed) exitProcess(1)
    }
}

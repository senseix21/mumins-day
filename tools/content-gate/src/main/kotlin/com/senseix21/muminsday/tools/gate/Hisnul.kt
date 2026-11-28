package com.senseix21.muminsday.tools.gate

import com.senseix21.muminsday.hisnul.CHAPTERS_FILE
import com.senseix21.muminsday.hisnul.DUAS_FOLDER
import com.senseix21.muminsday.hisnul.HisnulCheck
import com.senseix21.muminsday.hisnul.checkHisnulMuslim
import java.io.File
import kotlin.system.exitProcess

/** `sha256 <64 hex> <path under content/local/hisnul-muslim/>` in `content/hisnul-muslim-SOURCES.md`. */
private val HASH_LINE = Regex("^sha256 ([0-9a-f]{64}) (\\S+)$", RegexOption.MULTILINE)

/** `count <chapters|duas|segments> <n>` in the same record. */
private val COUNT_LINE = Regex("^count (chapters|duas|segments) ([0-9]+)$", RegexOption.MULTILINE)

/**
 * ADR-27. [files] is every file under `content/local/hisnul-muslim/` by its path there, or null when that folder does not
 * exist (CI, a fresh clone, a release machine): then there is nothing to package and the gate passes with a notice.
 * When the files are there, every one must have the SHA-256 recorded in [sources], none may be unrecorded or missing,
 * they must read as one collection (`checkHisnulMuslim`), and the chapters, duas and segments must be as many as the
 * record's `count` lines say.
 */
fun runHisnulGate(sources: String, files: Map<String, ByteArray>?): GateReport {
    if (files == null) {
        return GateReport(true, listOf("hisnul muslim gate: passed. content/local/hisnul-muslim is absent, so no build packages it (ADR-27)."))
    }
    val problems = mutableListOf<String>()
    val recorded = HASH_LINE.findAll(sources).associate { it.groupValues[2] to it.groupValues[1] }
    val counts = COUNT_LINE.findAll(sources).associate { it.groupValues[1] to it.groupValues[2].toInt() }
    if (recorded.isEmpty()) problems += "content/hisnul-muslim-SOURCES.md records no `sha256 <hex> <path>` line"
    for (name in listOf("chapters", "duas", "segments")) if (name !in counts) problems += "content/hisnul-muslim-SOURCES.md has no `count $name <n>` line"
    for ((name, hash) in recorded) {
        val bytes = files[name]
        when {
            bytes == null -> problems += "$name is recorded but not in content/local/hisnul-muslim/"
            sha256(bytes) != hash -> problems += "$name has SHA-256 ${sha256(bytes)}, recorded $hash: the file must stay as fetched"
        }
    }
    for (name in (files.keys - recorded.keys).sorted()) problems += "$name is in content/local/hisnul-muslim/ but has no recorded hash"
    if (problems.isNotEmpty()) return failed(problems)

    val duaTexts = files.filterKeys { it.startsWith("$DUAS_FOLDER/") }
        .mapKeys { it.key.removePrefix("$DUAS_FOLDER/") }.mapValues { it.value.toString(Charsets.UTF_8) }
    val chapters = files[CHAPTERS_FILE] ?: return failed(listOf("$CHAPTERS_FILE is required"))
    return when (val check = checkHisnulMuslim(chapters.toString(Charsets.UTF_8), duaTexts)) {
        is HisnulCheck.Refused -> failed(check.reasons)
        is HisnulCheck.Accepted -> {
            val found = mapOf("chapters" to check.collection.chapters.size, "duas" to check.collection.duas.size, "segments" to check.collection.segmentCount)
            val wrong = found.filter { (name, count) -> counts[name] != count }.map { (name, count) -> "$count $name, the record says ${counts[name]}" }
            if (wrong.isNotEmpty()) {
                failed(wrong)
            } else {
                GateReport(
                    true,
                    listOf(
                        "hisnul muslim gate: passed. ${recorded.size} files match their recorded SHA-256; ${found["chapters"]} chapters, " +
                            "${found["duas"]} duas, ${found["segments"]} segments. UNREVIEWED: packaged into debug builds only (ADR-27).",
                    ),
                )
            }
        }
    }
}

private fun failed(problems: List<String>) = GateReport(false, listOf("hisnul muslim gate: FAILED. ${problems.size} problem(s):") + problems.map { "  - $it" })

/** Arguments: the path of `content/hisnul-muslim-SOURCES.md` and of `content/local/hisnul-muslim/`. */
object HisnulMain {
    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size == 2) { "usage: <hisnul-muslim-SOURCES.md> <content/local/hisnul-muslim>" }
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
        val report = runHisnulGate(sources, files)
        report.lines.forEach(::println)
        if (!report.passed) exitProcess(1)
    }
}

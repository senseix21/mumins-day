package com.senseix21.muminsday.tools.gate

import com.senseix21.muminsday.quran.QuranCheck
import com.senseix21.muminsday.quran.TranslationCheck
import com.senseix21.muminsday.quran.checkQuran
import com.senseix21.muminsday.quran.checkTranslation
import com.senseix21.muminsday.quran.parseMetadata
import com.senseix21.muminsday.quran.parseText
import java.io.File
import java.security.MessageDigest
import kotlin.system.exitProcess

/** `sha256 <64 hex> <file>` in `content/quran-SOURCES.md`. */
private val HASH_LINE = Regex("^sha256 ([0-9a-f]{64}) (\\S+)$", RegexOption.MULTILINE)

/** `permission <translation file> <path under content/ of the written permission>`. */
private val PERMISSION_LINE = Regex("^permission (\\S+) (\\S+)$", RegexOption.MULTILINE)

/** The three files the reader is built from; the gate parses them as the app will. */
const val QURAN_TEXT: String = "quran-uthmani.txt"
const val QURAN_SEARCH_TEXT: String = "quran-simple-clean.txt"
const val QURAN_METADATA: String = "quran-data.xml"

fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

/**
 * T-BUILD-6 (INV-QT1). [sources] is the text of `content/quran-SOURCES.md`; [files] is every file in `content/quran/`
 * by name; [contentPaths] are the paths that exist under `content/`, for the permission check. Fails when a file's
 * SHA-256 differs from its recorded line, when a file is not recorded, when a recorded file is missing, when a
 * translation has no recorded written permission, or when the text, search text and metadata do not describe the same
 * 114 surahs.
 */
fun runQuranGate(sources: String, files: Map<String, ByteArray>, contentPaths: Set<String>): GateReport {
    val problems = mutableListOf<String>()
    val recorded = HASH_LINE.findAll(sources).associate { it.groupValues[2] to it.groupValues[1] }
    if (recorded.isEmpty()) problems += "content/quran-SOURCES.md records no `sha256 <hex> <file>` line"
    for ((name, hash) in recorded) {
        val bytes = files[name]
        when {
            bytes == null -> problems += "$name is recorded but not in content/quran/"
            sha256(bytes) != hash -> problems += "$name has SHA-256 ${sha256(bytes)}, recorded $hash: the text must not be changed (INV-QT1)"
        }
    }
    for (name in files.keys - recorded.keys) problems += "$name is in content/quran/ but has no recorded hash"

    val permissions = PERMISSION_LINE.findAll(sources).associate { it.groupValues[1] to it.groupValues[2] }
    for (name in files.keys.filter { it.startsWith("translation-") }) {
        val permission = permissions[name]
        if (permission == null || permission !in contentPaths) {
            problems += "$name is a translation without a `permission $name <file>` line naming a file in content/ (INV-QT1)"
        }
    }

    for (name in listOf(QURAN_TEXT, QURAN_SEARCH_TEXT, QURAN_METADATA)) if (name !in files) problems += "$name is required"
    if (problems.isNotEmpty()) return GateReport(false, listOf("quran gate: FAILED. ${problems.size} problem(s):") + problems.map { "  - $it" })

    fun text(name: String) = files.getValue(name).toString(Charsets.UTF_8)
    return when (val check = checkQuran(parseText(text(QURAN_TEXT)), parseText(text(QURAN_SEARCH_TEXT)), parseMetadata(text(QURAN_METADATA)))) {
        is QuranCheck.Verified -> {
            // A translation ships only complete, numbered like the text, and never as the FAKE debug fixture.
            val translations = files.keys.filter { it.startsWith("translation-") }.sorted()
            val refused = translations.mapNotNull { name ->
                val body = text(name)
                when (val translation = checkTranslation(check.quran, parseText(body), body.lineSequence().first())) {
                    is TranslationCheck.Refused -> "$name: ${translation.reason}"
                    is TranslationCheck.Accepted -> if (translation.translation.testFixture) "$name is a test fixture and cannot ship" else null
                }
            }
            if (refused.isNotEmpty()) {
                GateReport(false, listOf("quran gate: FAILED. ${refused.size} problem(s):") + refused.map { "  - $it" })
            } else {
                GateReport(
                    true,
                    listOf(
                        "quran gate: passed. ${recorded.size} files match their recorded SHA-256; ${check.quran.surahs.size} surahs, " +
                            "${check.quran.verses.size} ayahs, as the metadata gives; ${translations.size} translation(s).",
                    ),
                )
            }
        }
        is QuranCheck.Refused -> GateReport(false, listOf("quran gate: FAILED. The files do not describe one Quran:") + check.reasons.map { "  - $it" })
    }
}

/** Arguments: the path of `content/quran-SOURCES.md`, of `content/quran/`, and of `content/`. */
object QuranMain {
    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size == 3) { "usage: <quran-SOURCES.md> <content/quran> <content>" }
        val sourcesFile = File(args[0])
        val sources = if (sourcesFile.isFile) sourcesFile.readText(Charsets.UTF_8) else ""
        // Hidden files (a Finder `.DS_Store`) are not content; the app's staging skips them too.
        val files = File(args[1]).listFiles().orEmpty().filter { it.isFile && !it.name.startsWith(".") }.associate { it.name to it.readBytes() }
        val content = File(args[2])
        val paths = content.walkTopDown().filter { it.isFile }.map { it.relativeTo(content).invariantSeparatorsPath }.toSet()
        val report = runQuranGate(sources, files, paths)
        report.lines.forEach(::println)
        if (!report.passed) exitProcess(1)
    }
}

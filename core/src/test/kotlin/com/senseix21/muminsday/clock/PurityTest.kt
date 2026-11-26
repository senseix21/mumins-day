package com.senseix21.muminsday.clock

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** INV-T3 gate: the pure core reads no clock, zone, locale or Android API. A text search, as CLAUDE.md describes. */
class PurityTest {
    private val forbidden = listOf(
        "import android.",
        "systemDefault(",
        "Locale.getDefault",
        "Instant.now(",
        "LocalDate.now(",
        "System.currentTimeMillis",
    )

    @Test
    fun T_PURE_main_sources_contain_no_forbidden_calls() {
        val root = File("src/main/kotlin")
        assertTrue(root.isDirectory, "expected ${root.absolutePath} (tests run in the :core project directory)")
        val files = root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        assertTrue(files.isNotEmpty(), "no sources scanned")
        val hits = files.flatMap { file ->
            file.readLines().withIndex().flatMap { (index, line) ->
                forbidden.filter { it in line }.map { "${file.path}:${index + 1}: $it" }
            }
        }
        assertTrue(hits.isEmpty(), "forbidden in pure code:\n" + hits.joinToString("\n"))
    }
}

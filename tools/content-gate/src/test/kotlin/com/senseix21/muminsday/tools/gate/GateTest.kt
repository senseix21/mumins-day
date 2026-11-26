package com.senseix21.muminsday.tools.gate

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Fixtures are obviously fake records. No real deed, source or grading text appears here (INV-C1). */
class GateTest {

    private fun record(n: Int, vararg changes: Pair<String, String?>): String {
        val fields = linkedMapOf<String, String?>(
            "id" to "\"test-$n\"",
            "text_bn" to "\"TEST DEED $n BN\"",
            "text_en" to "\"TEST DEED $n EN\"",
            "source_book" to "\"Bukhari\"",
            "source_number" to "\"TEST-$n\"",
            "source_edition" to "\"TEST EDITION\"",
            "grading" to "\"TEST GRADING\"",
            "reviewer" to "\"TEST REVIEWER\"",
            "review_date" to "\"2026-01-01\"",
            "status" to "\"approved\"",
            "added_in" to "1",
        )
        for ((key, value) in changes) if (value == null) fields.remove(key) else fields[key] = value
        return fields.entries.joinToString(", ", "{", "}") { "\"${it.key}\": ${it.value}" }
    }

    private fun file(records: List<String>, version: String = "1") =
        """{ "version": $version, "deeds": [${records.joinToString(",\n")}] }"""

    private fun thirty(vararg replaced: Pair<Int, String>): List<String> {
        val byNumber = replaced.toMap()
        return (1..30).map { byNumber[it] ?: record(it) }
    }

    private fun failure(json: String): List<String> {
        val report = runGate(json, required = false)
        assertFalse(report.passed, "expected a failure, got ${report.lines}")
        return report.lines
    }

    @Test
    fun T_BUILD_4_thirty_complete_approved_records_pass() {
        val report = runGate(file(thirty()), required = false)
        assertTrue(report.passed, report.lines.toString())
        assertEquals(listOf("content gate: passed. Version 1: 30 approved, 0 retired."), report.lines)
    }

    @Test
    fun T_BUILD_4_a_missing_file_passes_only_while_it_is_not_required() {
        assertTrue(runGate(null, required = false).passed)
        assertFalse(runGate(null, required = true).passed)
    }

    @Test
    fun T_BUILD_4_a_draft_fails_the_build_and_is_named() {
        val lines = failure(file(thirty(7 to record(7, "status" to "\"draft\""))))
        assertTrue(lines.any { "deeds[6] (\"test-7\")" in it && "draft" in it }, lines.toString())
    }

    @Test
    fun T_BUILD_4_a_record_without_a_reviewer_or_a_review_date_fails() {
        val lines = failure(file(thirty(3 to record(3, "reviewer" to null), 9 to record(9, "review_date" to "\"\""))))
        assertTrue(lines.any { "test-3" in it && "\"reviewer\" is missing" in it }, lines.toString())
        assertTrue(lines.any { "test-9" in it && "\"review_date\" is missing" in it }, lines.toString())
    }

    @Test
    fun T_BUILD_4_every_required_key_is_enforced() {
        val required = listOf(
            "id", "text_bn", "text_en", "source_book", "source_number", "source_edition",
            "grading", "reviewer", "review_date", "status", "added_in",
        )
        for (key in required) {
            val lines = failure(file(thirty(5 to record(5, key to null))))
            assertTrue(lines.any { "\"$key\" is missing" in it }, "$key: $lines")
        }
    }

    @Test
    fun T_BUILD_4_fewer_than_thirty_approved_fails() {
        val lines = failure(file((1..29).map { record(it) }))
        assertTrue(lines.any { "fewer than 30 approved" in it }, lines.toString())
    }

    @Test
    fun T_BUILD_4_all_problems_are_listed_at_once() {
        val lines = failure(
            file(thirty(2 to record(2, "source_book" to "\"Other\""), 4 to record(4, "id" to "\"test-1\""), 8 to record(8, "grading" to null))),
        )
        assertTrue(lines.first().contains("3 problem(s)"), lines.toString())
    }

    @Test
    fun T_BUILD_4_retired_records_are_allowed_and_counted_apart() {
        val retired = record(31, "status" to "\"retired\"", "retired_in" to "2")
        val report = runGate(file(thirty() + retired, version = "2"), required = true)
        assertEquals(listOf("content gate: passed. Version 2: 30 approved, 1 retired."), report.lines)
    }

    @Test
    fun version_numbers_may_be_written_as_numbers_or_as_text() {
        assertTrue(runGate(file(thirty(1 to record(1, "added_in" to "\"1\""))), required = false).passed)
    }

    @Test
    fun a_text_field_that_is_not_text_is_refused_rather_than_stringified() {
        for (value in listOf("5", "true", "[\"TEST\"]", "{\"a\": \"TEST\"}")) {
            val lines = failure(file(thirty(6 to record(6, "text_en" to value))))
            assertTrue(lines.any { "test-6" in it && "\"text_en\" must be text in quotes" in it }, "$value: $lines")
        }
        val lines = failure(file(thirty(6 to record(6, "added_in" to "1.5"))))
        assertTrue(lines.any { "\"added_in\" must be a whole number" in it }, lines.toString())
    }

    @Test
    fun a_misspelt_key_is_reported_as_unknown() {
        val broken = record(12, "reviewer" to null).dropLast(1) + ", \"reviwer\": \"TEST REVIEWER\"}"
        val lines = failure(file(thirty(12 to broken)))
        assertTrue(lines.any { "unknown key \"reviwer\"" in it }, lines.toString())
    }

    @Test
    fun a_file_of_the_wrong_shape_is_refused() {
        assertTrue(failure("not json at all {").any { "not valid JSON" in it })
        assertTrue(failure("").isNotEmpty())
        assertTrue(failure("[]").any { "top level must be an object" in it })
        assertTrue(failure("""{ "deeds": [] }""").any { "\"version\" must be a whole number" in it })
        assertTrue(failure("""{ "version": 1 }""").any { "\"deeds\" must be a list" in it })
        assertTrue(failure("""{ "version": 1.5, "deeds": [] }""").any { "\"version\" must be a whole number" in it })
        assertTrue(failure("""{ "version": 1, "deeds": ["x"] }""").any { "deeds[0] must be an object" in it })
        assertTrue(failure("""{ "version": 1, "deeds": [], "extra": 1 }""").any { "unknown top-level key \"extra\"" in it })
        assertTrue(failure("""{ "version": 0, "deeds": [] }""").any { "\"version\" must be 1 or more" in it })
    }
}

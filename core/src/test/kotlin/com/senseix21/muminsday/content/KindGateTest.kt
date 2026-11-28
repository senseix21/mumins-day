package com.senseix21.muminsday.content

import com.senseix21.muminsday.deed.Status
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** T-BUILD-4 for duas and hadith (INV-C1, INV-C3, INV-C6). Fake records only, see Support.kt. */
class KindGateTest {

    private val kinds = ContentKind.entries

    private fun record(index: Int, id: String?, field: KindField?) = Place.InRecord(index, id, field)

    @Test
    fun T_BUILD_4_INV_C6_thirty_complete_approved_records_pass_for_each_kind() {
        for (kind in kinds) {
            val content = accepted(kind)
            assertEquals(30, content.approved.size, "$kind")
            assertEquals(kind, content.kind)
            assertEquals(testSources, content.sources)
            assertEquals(false, content.testFixture)
            assertEquals((1..30).map { "test-$it" }, content.approved.map { it.id.value }, "file order is rotation order")
        }
    }

    @Test
    fun INV_C6_record_shapes_follow_the_plan() {
        assertEquals(
            listOf(
                "id", "text_ar", "transliteration", "text_bn", "text_en", "occasion", "source", "source_number",
                "source_edition", "grading", "reviewer", "review_date", "status", "added_in", "retired_in",
            ),
            ContentKind.Dua.fields().map { ContentKind.Dua.key(it) },
        )
        assertEquals(
            listOf(
                "id", "text_ar", "text_bn", "text_en", "collection", "number", "edition", "grading", "reviewer",
                "review_date", "status", "added_in", "retired_in",
            ),
            ContentKind.Hadith.fields().map { ContentKind.Hadith.key(it) },
        )
        assertIs<Body.DuaBody>(accepted(ContentKind.Dua).approved.first().body)
        assertIs<Body.HadithBody>(accepted(ContentKind.Hadith).approved.first().body)
    }

    @Test
    fun T_BUILD_4_INV_C1_every_required_field_missing_or_blank_gives_exactly_one_Missing_for_it() {
        for (kind in kinds) {
            for (field in kind.fields() - KindField.RetiredIn) {
                for (broken in listOf<(Map<KindField, String>) -> Map<KindField, String>>({ it - field }, { it + (field to " ") })) {
                    // 31 records, so a record that loses its status still leaves 30 approved.
                    val problems = problemsOf(kind, rawFile(kind, count = 31).withRecord(6, broken))
                    // The problem names the record by its raw id, which is gone or blank when the id is the broken field.
                    val id = broken(rawRecord(kind, 7))[KindField.Id]
                    val expected = listOf(KindProblem(record(6, id, field), KindProblemKind.Missing))
                    assertEquals(expected, problems, "$kind $field")
                }
            }
        }
    }

    @Test
    fun T_BUILD_4_INV_C1_a_draft_is_refused_and_named() {
        for (kind in kinds) {
            val problems = problemsOf(kind, rawFile(kind, count = 31).withRecord(30) { it + (KindField.Status to "draft") })
            assertEquals(listOf(KindProblem(record(30, "test-31", KindField.Status), KindProblemKind.NotApproved)), problems)
        }
    }

    @Test
    fun ADR_22_a_book_that_is_not_in_the_reviewers_list_is_refused() {
        for (kind in kinds) {
            val problems = problemsOf(kind, rawFile(kind).withRecord(2) { it + (KindField.SourceBook to "TEST BOOK C") })
            assertEquals(listOf(KindProblem(record(2, "test-3", KindField.SourceBook), KindProblemKind.NotInSources)), problems)
        }
    }

    @Test
    fun ADR_22_the_sources_list_must_exist_name_something_and_name_each_book_once() {
        val kind = ContentKind.Hadith
        assertTrue(KindProblem(Place.InFile(FileField.Sources), KindProblemKind.Missing) in problemsOf(kind, rawFile(kind, sources = null)))
        assertTrue(KindProblem(Place.InFile(FileField.Sources), KindProblemKind.Missing) in problemsOf(kind, rawFile(kind, sources = emptyList())))
        assertEquals(
            listOf(KindProblem(Place.InFile(FileField.Sources), KindProblemKind.Missing)),
            problemsOf(kind, rawFile(kind, sources = testSources + " ")),
        )
        assertEquals(
            listOf(KindProblem(Place.InFile(FileField.Sources), KindProblemKind.DuplicateSource)),
            problemsOf(kind, rawFile(kind, sources = testSources + testSources[0])),
        )
    }

    @Test
    fun INV_C6_a_test_fixture_is_refused_unless_the_gate_is_for_the_debug_fixture() {
        for (kind in kinds) {
            val fixture = rawFile(kind, testFixture = true)
            assertEquals(listOf(KindProblem(Place.InFile(FileField.TestFixture), KindProblemKind.TestFixture)), problemsOf(kind, fixture))
            val content = (checkKind(kind, fixture, allowTestFixture = true) as KindCheck.Accepted).content
            assertTrue(content.testFixture)
            assertEquals(listOf(KindProblem(Place.InFile(FileField.TestFixture), KindProblemKind.Missing)), problemsOf(kind, rawFile(kind, testFixture = null)))
        }
    }

    @Test
    fun INV_C1_fewer_than_thirty_approved_records_are_refused() {
        for (kind in kinds) {
            assertEquals(listOf(KindProblem(Place.InFile(FileField.Records), KindProblemKind.TooFewApproved)), problemsOf(kind, rawFile(kind, count = 29)))
        }
    }

    @Test
    fun the_version_must_be_present_and_at_least_one() {
        val kind = ContentKind.Dua
        assertEquals(listOf(KindProblem(Place.InFile(FileField.Version), KindProblemKind.Missing)), problemsOf(kind, rawFile(kind, version = null)))
        assertEquals(listOf(KindProblem(Place.InFile(FileField.Version), KindProblemKind.Invalid)), problemsOf(kind, rawFile(kind, version = 0)))
    }

    @Test
    fun a_field_this_kind_does_not_have_is_refused() {
        val problems = problemsOf(ContentKind.Hadith, rawFile(ContentKind.Hadith).withRecord(0) { it + (KindField.Occasion to "TEST") })
        assertEquals(listOf(KindProblem(record(0, "test-1", KindField.Occasion), KindProblemKind.Invalid)), problems)
    }

    @Test
    fun INV_L3_a_text_carrying_a_direction_control_is_refused() {
        for (control in listOf('‪', '‫', '‬', '‭', '‮', '⁦', '⁧', '⁨', '⁩')) {
            val problems = problemsOf(ContentKind.Dua, rawFile(ContentKind.Dua).withRecord(4) { it + (KindField.TextAr to "TEST${control}AR") })
            assertEquals(listOf(KindProblem(record(4, "test-5", KindField.TextAr), KindProblemKind.DirectionControl)), problems, "U+%04X".format(control.code))
        }
    }

    @Test
    fun duplicate_ids_are_reported_on_the_later_record() {
        val kind = ContentKind.Hadith
        val problems = problemsOf(kind, rawFile(kind, count = 31).withRecord(30) { it + (KindField.Id to "test-2") })
        assertEquals(listOf(KindProblem(record(30, "test-2", KindField.Id), KindProblemKind.DuplicateId)), problems)
    }

    @Test
    fun retired_records_are_kept_for_audit_and_never_rotate() {
        val kind = ContentKind.Dua
        val file = rawFile(kind, count = 31, version = 2).withRecord(30) { it + (KindField.Status to "retired") + (KindField.RetiredIn to "2") }
        val content = accepted(kind, file)
        assertEquals(30, content.approved.size)
        assertEquals(listOf("test-31"), content.retired.map { it.id.value })
        assertEquals(Status.Retired, content.retired.single().status)
        val mismatch = problemsOf(kind, rawFile(kind, count = 31, version = 2).withRecord(30) { it + (KindField.Status to "retired") })
        assertEquals(listOf(KindProblem(record(30, "test-31", KindField.RetiredIn), KindProblemKind.StatusMismatch)), mismatch)
    }

    @Test
    fun every_problem_is_listed_in_one_pass() {
        val kind = ContentKind.Dua
        val file = rawFile(kind, testFixture = true, sources = testSources + testSources[1])
            .withRecord(0) { it - KindField.Reviewer }
            .withRecord(1) { it + (KindField.ReviewDate to "TEST") }
        val found = problemsOf(kind, file).map { it.kind }.toSet()
        assertEquals(setOf(KindProblemKind.TestFixture, KindProblemKind.DuplicateSource, KindProblemKind.Missing, KindProblemKind.Invalid), found)
    }
}

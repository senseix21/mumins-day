package com.senseix21.muminsday.deed

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class GateTest {

    private val version = 3
    // Two spare approved records, so one broken status in a test is not also a TooFewApproved.
    private val valid = rawDeeds(32)

    private fun with(index: Int, change: (RawDeed) -> RawDeed): List<RawDeed> =
        valid.mapIndexed { i, raw -> if (i == index) change(raw) else raw }

    private fun single(records: List<RawDeed>): Problem = problemsOf(version, records).single()

    @Test
    fun T_BUILD_4_INV_C1_thirty_valid_records_are_accepted() {
        val thirty = rawDeeds(30)
        val content = accepted(version, thirty)
        assertEquals(30, content.approved.size)
        assertEquals(thirty.map { it.id }, content.approved.map { it.id.value })
        assertEquals(version, content.version)
    }

    private val blankers: Map<Field, (RawDeed) -> RawDeed> = mapOf(
        Field.Id to { r -> r.copy(id = null) },
        Field.TextBn to { r -> r.copy(textBn = null) },
        Field.TextEn to { r -> r.copy(textEn = null) },
        Field.SourceBook to { r -> r.copy(sourceBook = null) },
        Field.SourceNumber to { r -> r.copy(sourceNumber = null) },
        Field.SourceEdition to { r -> r.copy(sourceEdition = null) },
        Field.Grading to { r -> r.copy(grading = null) },
        Field.Reviewer to { r -> r.copy(reviewer = null) },
        Field.ReviewDate to { r -> r.copy(reviewDate = null) },
        Field.Status to { r -> r.copy(status = null) },
        Field.AddedIn to { r -> r.copy(addedIn = null) },
    )

    @Test
    fun T_BUILD_4_INV_C1_every_required_field_missing_gives_exactly_one_Missing_for_it() {
        assertEquals(11, blankers.size)
        for ((field, nulled) in blankers) {
            val asBlank: (RawDeed) -> RawDeed = { r ->
                val n = nulled(r)
                when (field) {
                    Field.Id -> n.copy(id = "  ")
                    Field.TextBn -> n.copy(textBn = "")
                    Field.TextEn -> n.copy(textEn = " ")
                    Field.SourceBook -> n.copy(sourceBook = "")
                    Field.SourceNumber -> n.copy(sourceNumber = "")
                    Field.SourceEdition -> n.copy(sourceEdition = " ")
                    Field.Grading -> n.copy(grading = "")
                    Field.Reviewer -> n.copy(reviewer = "\t")
                    Field.ReviewDate -> n.copy(reviewDate = "")
                    Field.Status -> n.copy(status = "")
                    Field.AddedIn -> n.copy(addedIn = "")
                    Field.RetiredIn -> n
                }
            }
            for (change in listOf(nulled, asBlank)) {
                val broken = with(7, change)
                val problem = single(broken)
                assertEquals(Problem(7, broken[7].id, field, ProblemKind.Missing), problem, "field $field")
            }
        }
    }

    @Test
    fun T_BUILD_4_INV_C1_retiredIn_may_be_absent_on_an_approved_record() {
        assertEquals(null, valid[0].retiredIn)
        accepted(version, valid)
    }

    @Test
    fun T_BUILD_4_INV_C1_draft_is_not_approved() {
        val problem = single(valid + rawDeed(41, status = "draft"))
        assertEquals(Problem(32, "test-41", Field.Status, ProblemKind.NotApproved), problem)
    }

    @Test
    fun T_BUILD_4_INV_C1_unknown_status_and_unknown_book_and_bad_date_are_invalid() {
        for (status in listOf("Approved", "APPROVED", "published", "approved ")) {
            assertEquals(Problem(2, "test-3", Field.Status, ProblemKind.Invalid), single(with(2) { it.copy(status = status) }), status)
        }
        for (book in listOf("Tirmidhi", "bukhari", "muslim", "Bukhari ")) {
            assertEquals(Problem(2, "test-3", Field.SourceBook, ProblemKind.Invalid), single(with(2) { it.copy(sourceBook = book) }), book)
        }
        for (date in listOf("2026-13-01", "2026-02-30", "26-01-01", "2026-1-1", "yesterday", "2026/01/01")) {
            assertEquals(Problem(2, "test-3", Field.ReviewDate, ProblemKind.Invalid), single(with(2) { it.copy(reviewDate = date) }), date)
        }
    }

    @Test
    fun T_BUILD_4_INV_C1_status_and_retiredIn_must_agree() {
        val retiredWithout = valid + rawDeed(41, status = "retired", addedIn = 1).copy(retiredIn = null)
        assertEquals(Problem(32, "test-41", Field.RetiredIn, ProblemKind.StatusMismatch), single(retiredWithout))
        val approvedWith = with(4) { it.copy(retiredIn = "2") }
        assertEquals(Problem(4, "test-5", Field.RetiredIn, ProblemKind.StatusMismatch), single(approvedWith))
    }

    @Test
    fun T_BUILD_4_INV_C1_version_numbers_must_be_whole_numbers_not_from_the_future_and_in_order() {
        assertEquals(Problem(1, "test-2", Field.AddedIn, ProblemKind.FromTheFuture), single(with(1) { it.copy(addedIn = "4") }))
        for (bad in listOf("0", "-1", "one", "1.5", "")) {
            val expected = if (bad.isEmpty()) ProblemKind.Missing else ProblemKind.Invalid
            assertEquals(Problem(1, "test-2", Field.AddedIn, expected), single(with(1) { it.copy(addedIn = bad) }), bad)
        }
        val retiredFuture = valid + rawDeed(41, status = "retired", addedIn = 1, retiredIn = 4)
        assertEquals(Problem(32, "test-41", Field.RetiredIn, ProblemKind.FromTheFuture), single(retiredFuture))
        val retiredBeforeAdded = valid + rawDeed(41, status = "retired", addedIn = 3, retiredIn = 2)
        assertEquals(Problem(32, "test-41", Field.RetiredIn, ProblemKind.Invalid), single(retiredBeforeAdded))
        val retiredSameVersion = valid + rawDeed(41, status = "retired", addedIn = 2, retiredIn = 2)
        accepted(version, retiredSameVersion)
    }

    @Test
    fun T_BUILD_4_INV_C1_duplicate_id_is_reported_on_the_later_record() {
        val problem = single(with(10) { it.copy(id = valid[3].id) })
        assertEquals(Problem(10, "test-4", Field.Id, ProblemKind.DuplicateId), problem)
    }

    @Test
    fun T_BUILD_4_INV_C1_fewer_than_thirty_approved_is_too_few() {
        assertEquals(Problem(null, null, null, ProblemKind.TooFewApproved), single(rawDeeds(29)))
        assertEquals(listOf(Problem(null, null, null, ProblemKind.TooFewApproved)), problemsOf(version, emptyList()))
    }

    @Test
    fun T_BUILD_4_INV_C1_retired_records_are_accepted_but_excluded_from_rotation() {
        val retired = (41..45).map { rawDeed(it, status = "retired", addedIn = 1, retiredIn = 2) }
        val content = accepted(version, rawDeeds(30) + retired)
        assertEquals(30, content.approved.size)
        assertEquals(5, content.retired.size)
        assertTrue(content.approved.none { it.status == Status.Retired })
        assertTrue(content.retired.all { it.status == Status.Retired && it.retiredIn == 2 })
        assertEquals(null, content.find(content.retired[0].id))
    }

    @Test
    fun T_BUILD_4_INV_C1_text_length_counts_code_points_not_utf16_units() {
        val supplementary = "😀" // one code point, two UTF-16 units; an emoji, not religious text
        val ninety = supplementary.repeat(90)
        assertEquals(180, ninety.length)
        accepted(version, with(0) { it.copy(textEn = ninety, textBn = ninety) })
        val ninetyOne = supplementary.repeat(91)
        assertEquals(Problem(0, "test-1", Field.TextEn, ProblemKind.TooLong), single(with(0) { it.copy(textEn = ninetyOne) }))
        assertEquals(Problem(0, "test-1", Field.TextBn, ProblemKind.TooLong), single(with(0) { it.copy(textBn = ninetyOne) }))
        accepted(version, with(0) { it.copy(textEn = "A".repeat(90)) })
        assertEquals(Problem(0, "test-1", Field.TextEn, ProblemKind.TooLong), single(with(0) { it.copy(textEn = "A".repeat(91)) }))
    }

    @Test
    fun T_BUILD_4_INV_C1_version_below_one_is_invalid() {
        assertEquals(listOf(Problem(null, null, null, ProblemKind.Invalid)).first(), problemsOf(0, valid).first())
    }

    @Test
    fun T_BUILD_4_INV_C1_every_problem_is_collected_in_one_pass() {
        val broken = with(2) { it.copy(reviewer = null) }
            .mapIndexed { i, raw -> if (i == 9) raw.copy(sourceBook = "Tirmidhi", textEn = "A".repeat(91)) else raw }
        val problems = problemsOf(version, broken)
        assertEquals(
            setOf(
                Problem(2, "test-3", Field.Reviewer, ProblemKind.Missing),
                Problem(9, "test-10", Field.SourceBook, ProblemKind.Invalid),
                Problem(9, "test-10", Field.TextEn, ProblemKind.TooLong),
            ),
            problems.toSet(),
        )
        assertEquals(3, problems.size)
    }

    @Test
    fun T_BUILD_4_INV_C1_a_rejection_never_has_an_empty_list() {
        val cases = listOf(emptyList(), valid.take(3), with(0) { it.copy(id = null) }, valid + rawDeed(42, status = "draft"))
        for (records in cases) {
            val check = checkContent(version, records)
            assertIs<ContentCheck.Rejected>(check)
            assertTrue(check.problems.isNotEmpty())
        }
    }

    @Test
    fun T_BUILD_4_INV_C1_provenance_check_does_not_look_at_text() {
        val ok = checkProvenance(
            version,
            RawProvenance("x-1", "Muslim", "TEST-1", "TEST EDITION", "TEST GRADING", "TEST REVIEWER", "2026-01-01", "approved", "1", null),
        )
        assertIs<Checked.Ok<Provenance>>(ok)
    }
}

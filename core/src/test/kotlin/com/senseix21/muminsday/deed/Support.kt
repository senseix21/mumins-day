package com.senseix21.muminsday.deed

import java.time.LocalDate

/** Obviously fake records only. No real deed, source or grading text appears anywhere in tests (INV-C1). */
fun rawDeed(n: Int, status: String = "approved", addedIn: Int = 1, retiredIn: Int? = null) = RawDeed(
    id = "test-$n",
    textBn = "TEST DEED $n BN",
    textEn = "TEST DEED $n EN",
    sourceBook = if (n % 2 == 0) "Bukhari" else "Muslim",
    sourceNumber = "TEST-$n",
    sourceEdition = "TEST EDITION",
    grading = "TEST GRADING",
    reviewer = "TEST REVIEWER",
    reviewDate = "2026-01-01",
    status = status,
    addedIn = addedIn.toString(),
    retiredIn = retiredIn?.toString(),
)

fun rawDeeds(count: Int, firstNumber: Int = 1): List<RawDeed> = (firstNumber until firstNumber + count).map { rawDeed(it) }

fun accepted(version: Int, records: List<RawDeed>): DeedContent =
    (checkContent(version, records) as? ContentCheck.Accepted)?.content
        ?: error("fixture was rejected: ${checkContent(version, records)}")

/** A content with exactly [count] approved deeds, built directly so [count] may be below [MIN_APPROVED]. */
fun contentOf(count: Int, version: Int = 1, firstNumber: Int = 1): DeedContent {
    val deeds = rawDeeds(count, firstNumber).map { raw ->
        val ok = checkDeed(1, raw) as Checked.Ok
        ok.value
    }
    return DeedContent(version, deeds, emptyList())
}

fun problemsOf(version: Int, records: List<RawDeed>): List<Problem> = when (val check = checkContent(version, records)) {
    is ContentCheck.Accepted -> emptyList()
    is ContentCheck.Rejected -> check.problems
}

val someDay: LocalDate = LocalDate.of(2026, 11, 26)

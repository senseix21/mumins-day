package com.senseix21.muminsday.content

/**
 * Obviously fake records only. No real dua, hadith, book, grading or source text appears anywhere in tests (INV-C1):
 * every value says TEST, and the book names are TEST BOOK A and TEST BOOK B.
 */
val testSources: List<String> = listOf("TEST BOOK A", "TEST BOOK B")

fun rawRecord(kind: ContentKind, n: Int, status: String = "approved", addedIn: Int = 1, retiredIn: Int? = null): Map<KindField, String> {
    val label = kind.name.uppercase()
    val values = mapOf(
        KindField.Id to "test-$n",
        KindField.TextAr to "TEST $label $n AR",
        KindField.Transliteration to "TEST $label $n TRANSLITERATION",
        KindField.TextBn to "TEST $label $n BN",
        KindField.TextEn to "TEST $label $n EN",
        KindField.Occasion to "TEST OCCASION $n",
        KindField.SourceBook to testSources[n % 2],
        KindField.SourceNumber to "TEST-$n",
        KindField.SourceEdition to "TEST EDITION",
        KindField.Grading to "TEST GRADING",
        KindField.Reviewer to "TEST REVIEWER",
        KindField.ReviewDate to "2000-01-01",
        KindField.Status to status,
        KindField.AddedIn to addedIn.toString(),
    ) + (retiredIn?.let { mapOf(KindField.RetiredIn to it.toString()) } ?: emptyMap())
    return values.filterKeys { kind.key(it) != null }
}

fun rawFile(
    kind: ContentKind,
    count: Int = 30,
    version: Int? = 1,
    testFixture: Boolean? = false,
    sources: List<String?>? = testSources,
): RawKindFile = RawKindFile(version, testFixture, sources, (1..count).map { rawRecord(kind, it) })

fun accepted(kind: ContentKind, file: RawKindFile = rawFile(kind)): KindContent =
    (checkKind(kind, file, allowTestFixture = false) as? KindCheck.Accepted)?.content
        ?: error("fixture was rejected: ${checkKind(kind, file, allowTestFixture = false)}")

fun problemsOf(kind: ContentKind, file: RawKindFile, allowTestFixture: Boolean = false): List<KindProblem> =
    when (val check = checkKind(kind, file, allowTestFixture)) {
        is KindCheck.Accepted -> emptyList()
        is KindCheck.Rejected -> check.problems
    }

fun RawKindFile.withRecord(index: Int, change: (Map<KindField, String>) -> Map<KindField, String>): RawKindFile =
    copy(records = records.mapIndexed { at, record -> if (at == index) change(record) else record })

package com.senseix21.muminsday.zakat

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class RulesTest {

    private fun problems(edit: RawZakatRules.() -> RawZakatRules): List<ZakatProblem> =
        assertIs<ZakatRulesCheck.Rejected>(checkZakatRules(fakeRaw().edit())).problems

    @Test
    fun `a complete signed file is accepted and keeps what the reviewer wrote`() {
        val rules = fakeRules()
        assertEquals("TEST REVIEWER", rules.reviewer)
        assertEquals(Metal.Gold, rules.nisabBasis)
        assertEquals(setOf(WealthKind.Cash, WealthKind.Gold, WealthKind.Silver, WealthKind.Other), rules.wealthCounted)
        assertTrue(rules.isTestFixture)
    }

    @Test
    fun `a draft is not signed and is refused by name`() {
        assertEquals(listOf(ZakatProblem(ZakatField.Status, ZakatProblemKind.NotApproved)), problems { copy(status = "draft") })
    }

    @Test
    fun `every field is required and a blank counts as missing`() {
        assertEquals(ZakatProblemKind.Missing, problems { copy(reviewer = " ") }.single().kind)
        assertEquals(ZakatProblemKind.Missing, problems { copy(ratePercent = null) }.single().kind)
        assertEquals(ZakatProblemKind.Missing, problems { copy(conversionSource = "") }.single().kind)
        assertEquals(ZakatProblemKind.Missing, problems { copy(deductDebts = null) }.single().kind)
        assertEquals(ZakatProblemKind.Missing, problems { copy(wealthCounted = emptyList()) }.single().kind)
    }

    @Test
    fun `numbers must be plain and positive, a rate at most one hundred`() {
        for (bad in listOf("0", "-1", "1e3", "1,5", "abc", ".5", "1.", "0.0")) {
            assertEquals(ZakatProblem(ZakatField.GramsPerBhori, ZakatProblemKind.Invalid), problems { copy(gramsPerBhori = bad) }.single(), bad)
        }
        assertEquals(ZakatProblemKind.Invalid, problems { copy(ratePercent = "100.01") }.single().kind)
        assertTrue(checkZakatRules(fakeRaw().copy(ratePercent = "100")) is ZakatRulesCheck.Accepted)
    }

    @Test
    fun `an unknown basis, wealth name or date is refused and a repeated wealth name too`() {
        assertEquals(ZakatField.NisabBasis, problems { copy(nisabBasis = "platinum") }.single().field)
        assertEquals(ZakatField.WealthCounted, problems { copy(wealthCounted = listOf("cash", "land")) }.single().field)
        assertEquals(ZakatField.WealthCounted, problems { copy(wealthCounted = listOf("cash", "cash")) }.single().field)
        assertEquals(ZakatField.ReviewDate, problems { copy(reviewDate = "01/01/2000") }.single().field)
        assertEquals(ZakatField.Version, problems { copy(version = "0") }.single().field)
    }

    @Test
    fun `all problems are collected at once`() {
        assertEquals(3, problems { copy(status = "draft", ratePercent = "x", reviewer = null) }.size)
    }

    /**
     * INV-Z1: Claude's code holds no rule number. Every numeric literal in this package's source must be one of the scale
     * constants named here; the rules (nisab, rate, conversions) are data. 0 and 1 are arithmetic identities, 30 is the
     * digits kept in a division, 2 the paisa, 100 the notation of a percent.
     */
    @Test
    fun `no rule number appears in the zakat source`() {
        val allowed = setOf("0", "1", "2", "30", "100")
        val directory = File("src/main/kotlin/com/senseix21/muminsday/zakat")
        val files = directory.listFiles { file -> file.extension == "kt" }.orEmpty()
        assertTrue(files.isNotEmpty(), "found no source in ${directory.absolutePath}")
        val literal = Regex("(?<![A-Za-z_0-9.\"])[0-9]+(\\.[0-9]+)?(?![A-Za-z_0-9\"])")
        for (file in files) {
            val code = file.readLines().filterNot { it.trim().startsWith("*") || it.trim().startsWith("/*") || it.trim().startsWith("//") }
                .joinToString("\n") { it.substringBefore(" // ") }.replace(Regex("\"[^\"\\n]*\""), "\"\"")
            for (match in literal.findAll(code)) assertTrue(match.value in allowed, "${file.name}: unexplained number ${match.value}")
        }
    }
}

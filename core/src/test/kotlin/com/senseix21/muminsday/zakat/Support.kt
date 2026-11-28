package com.senseix21.muminsday.zakat

import java.math.BigDecimal
import java.time.LocalDate

/**
 * FAKE rules for tests (INV-Z1). Every number here is invented to make the arithmetic easy to check by hand and is not a
 * ruling: 1 bhori is 10 grams, 20 ana, 100 ratti; the nisab is 100 grams of gold or 700 of silver; the rate is 10 percent.
 */
internal fun fakeRaw(): RawZakatRules = RawZakatRules(
    version = "1",
    reviewer = "TEST REVIEWER",
    reviewDate = "2000-01-01",
    status = "approved",
    testFixture = true,
    nisabBasis = "gold",
    nisabGoldGrams = "100",
    nisabSilverGrams = "700",
    ratePercent = "10",
    gramsPerBhori = "10",
    anaPerBhori = "20",
    rattiPerAna = "5",
    conversionSource = "TEST SOURCE",
    wealthCounted = listOf("cash", "gold", "silver", "other"),
    deductDebts = true,
    requiresHawl = false,
    notes = "FAKE",
)

internal fun fakeRules(edit: RawZakatRules.() -> RawZakatRules = { this }): ZakatRules =
    when (val check = checkZakatRules(fakeRaw().edit())) {
        is ZakatRulesCheck.Accepted -> check.rules
        is ZakatRulesCheck.Rejected -> error("fake rules rejected: ${check.problems}")
    }

internal val day: LocalDate = LocalDate.of(2000, 1, 1)

internal fun taka(value: String): Taka = checkNotNull(Taka.of(BigDecimal(value)))
internal fun amount(value: String): Quantity = checkNotNull(Quantity.of(BigDecimal(value)))
internal fun price(perBhori: String): MetalPrice = checkNotNull(MetalPrice.of(taka(perBhori), day))

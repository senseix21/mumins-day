package com.senseix21.muminsday.tools.gate

import com.senseix21.muminsday.zakat.RawZakatRules
import com.senseix21.muminsday.zakat.ZakatField
import com.senseix21.muminsday.zakat.ZakatProblem
import com.senseix21.muminsday.zakat.ZakatProblemKind
import com.senseix21.muminsday.zakat.ZakatRulesCheck
import com.senseix21.muminsday.zakat.checkZakatRules
import groovy.json.JsonException
import groovy.json.JsonSlurper

private val KEYS: Map<ZakatField, String> = mapOf(
    ZakatField.Version to "version",
    ZakatField.Reviewer to "reviewer",
    ZakatField.ReviewDate to "review_date",
    ZakatField.Status to "status",
    ZakatField.TestFixture to "test_fixture",
    ZakatField.NisabBasis to "nisab_basis",
    ZakatField.NisabGoldGrams to "nisab_gold_grams",
    ZakatField.NisabSilverGrams to "nisab_silver_grams",
    ZakatField.RatePercent to "rate_percent",
    ZakatField.GramsPerBhori to "grams_per_bhori",
    ZakatField.AnaPerBhori to "ana_per_bhori",
    ZakatField.RattiPerAna to "ratti_per_ana",
    ZakatField.ConversionSource to "conversion_source",
    ZakatField.WealthCounted to "wealth_counted",
    ZakatField.DeductDebts to "deduct_debts",
    ZakatField.RequiresHawl to "requires_hawl",
    ZakatField.Notes to "notes",
)

/** `version` may be written as a JSON integer; every other text is a JSON string, every flag a JSON boolean. */
private val NUMERIC = setOf(ZakatField.Version)

/**
 * T-BUILD-4 for `content/zakat-rules.json` (INV-Z1). [json] is its text, or null when it does not exist: until the
 * reviewer signs rules there is nothing to ship, so absence passes with a notice unless [required]. A present file must
 * be complete and signed; [allowTestFixture] is false for the real file, so a file of fake numbers cannot ship, and true
 * for the debug fixture.
 */
fun runZakatGate(json: String?, required: Boolean, allowTestFixture: Boolean, name: String = "content/zakat-rules.json"): GateReport {
    if (json == null) {
        return if (required) {
            GateReport(false, listOf("zakat gate: FAILED. $name does not exist and this build requires it."))
        } else {
            GateReport(true, listOf("zakat gate: no $name yet, nothing to check."))
        }
    }
    val root = try {
        JsonSlurper().parseText(json)
    } catch (e: JsonException) {
        return zakatFailed(name, listOf("the file is not valid JSON: ${e.message?.lineSequence()?.firstOrNull()}"))
    } catch (e: IllegalArgumentException) {
        return zakatFailed(name, listOf("the file is empty"))
    }
    if (root !is Map<*, *>) return zakatFailed(name, listOf("the top level must be an object"))

    val shape = mutableListOf<String>()
    for (key in root.keys) if (key !in KEYS.values) shape += "unknown key \"$key\""
    fun read(field: ZakatField): String? = when (val value = root[KEYS.getValue(field)]) {
        null -> null
        is String -> value
        is Int -> if (field in NUMERIC) value.toString() else null.also { shape += "\"${KEYS.getValue(field)}\" must be text in quotes" }
        else -> null.also { shape += "\"${KEYS.getValue(field)}\" must be text in quotes" }
    }
    fun flag(field: ZakatField): Boolean? = when (val value = root[KEYS.getValue(field)]) {
        null -> null
        is Boolean -> value
        else -> null.also { shape += "\"${KEYS.getValue(field)}\" must be true or false" }
    }
    val wealth = when (val value = root["wealth_counted"]) {
        null -> null
        is List<*> -> value.map { it as? String ?: "?".also { shape += "\"wealth_counted\" must hold only text" } }
        else -> null.also { shape += "\"wealth_counted\" must be a list" }
    }
    val raw = RawZakatRules(
        version = read(ZakatField.Version),
        reviewer = read(ZakatField.Reviewer),
        reviewDate = read(ZakatField.ReviewDate),
        status = read(ZakatField.Status),
        testFixture = flag(ZakatField.TestFixture),
        nisabBasis = read(ZakatField.NisabBasis),
        nisabGoldGrams = read(ZakatField.NisabGoldGrams),
        nisabSilverGrams = read(ZakatField.NisabSilverGrams),
        ratePercent = read(ZakatField.RatePercent),
        gramsPerBhori = read(ZakatField.GramsPerBhori),
        anaPerBhori = read(ZakatField.AnaPerBhori),
        rattiPerAna = read(ZakatField.RattiPerAna),
        conversionSource = read(ZakatField.ConversionSource),
        wealthCounted = wealth,
        deductDebts = flag(ZakatField.DeductDebts),
        requiresHawl = flag(ZakatField.RequiresHawl),
        notes = read(ZakatField.Notes),
    )
    if (shape.isNotEmpty()) return zakatFailed(name, shape)

    return when (val check = checkZakatRules(raw)) {
        is ZakatRulesCheck.Accepted ->
            if (check.rules.isTestFixture && !allowTestFixture) {
                zakatFailed(name, listOf("\"test_fixture\" is true: a file of fake numbers cannot be the shipped rules"))
            } else {
                GateReport(true, listOf("zakat gate: passed. $name is signed by ${check.rules.reviewer} on ${check.rules.reviewDate}."))
            }
        is ZakatRulesCheck.Rejected -> zakatFailed(name, check.problems.map(::describe))
    }
}

private fun zakatFailed(name: String, problems: List<String>): GateReport =
    GateReport(false, listOf("zakat gate: FAILED. ${problems.size} problem(s) in $name:") + problems.map { "  - $it" })

private fun describe(problem: ZakatProblem): String {
    val key = "\"${KEYS.getValue(problem.field)}\""
    return when (problem.kind) {
        ZakatProblemKind.Missing -> "$key is missing or blank"
        ZakatProblemKind.Invalid -> "$key has a value that is not allowed"
        ZakatProblemKind.NotApproved -> "$key is \"draft\": only rules the reviewer has signed (\"approved\") may be in the file"
    }
}

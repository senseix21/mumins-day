package com.senseix21.muminsday.tools.gate

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** FAKE numbers only (INV-Z1): they make the shapes checkable and are not a ruling. */
class ZakatGateTest {

    private fun file(vararg changes: Pair<String, String?>): String {
        val fields = linkedMapOf<String, String?>(
            "version" to "1",
            "reviewer" to "\"TEST REVIEWER\"",
            "review_date" to "\"2000-01-01\"",
            "status" to "\"approved\"",
            "test_fixture" to "false",
            "nisab_basis" to "\"gold\"",
            "nisab_gold_grams" to "\"100\"",
            "nisab_silver_grams" to "\"700\"",
            "rate_percent" to "\"10\"",
            "grams_per_bhori" to "\"10\"",
            "ana_per_bhori" to "\"20\"",
            "ratti_per_ana" to "\"5\"",
            "conversion_source" to "\"TEST SOURCE\"",
            "wealth_counted" to "[\"cash\", \"gold\"]",
            "deduct_debts" to "true",
            "requires_hawl" to "false",
            "notes" to "\"FAKE\"",
        )
        for ((key, value) in changes) if (value == null) fields.remove(key) else fields[key] = value
        return fields.entries.joinToString(", ", "{", "}") { "\"${it.key}\": ${it.value}" }
    }

    private fun run(json: String?, required: Boolean = false, fixture: Boolean = false) = runZakatGate(json, required, fixture)

    @Test
    fun `T-BUILD-4 a complete signed file passes`() {
        val report = run(file())
        assertTrue(report.passed, report.lines.toString())
        assertTrue(report.lines.single().contains("TEST REVIEWER"))
    }

    @Test
    fun `T-BUILD-4 a missing file passes with a notice only while it is not required`() {
        assertTrue(run(null).passed)
        assertTrue(run(null).lines.single().contains("nothing to check"))
        assertFalse(run(null, required = true).passed)
    }

    @Test
    fun `T-BUILD-4 an unsigned file fails and says which field`() {
        val report = run(file("status" to "\"draft\""))
        assertFalse(report.passed)
        assertTrue(report.lines.any { it.contains("\"status\"") && it.contains("draft") }, report.lines.toString())
    }

    @Test
    fun `T-BUILD-4 an incomplete file lists every missing field`() {
        val report = run(file("reviewer" to null, "rate_percent" to null, "conversion_source" to "\"\""))
        assertFalse(report.passed)
        assertEquals(4, report.lines.size, report.lines.toString())
    }

    @Test
    fun `T-BUILD-4 a number written as a JSON number or a flag as text is refused`() {
        assertFalse(run(file("rate_percent" to "2.5")).passed)
        assertFalse(run(file("deduct_debts" to "\"true\"")).passed)
        assertFalse(run(file("wealth_counted" to "\"cash\"")).passed)
    }

    @Test
    fun `T-BUILD-4 an unknown key or broken JSON fails`() {
        assertFalse(run(file("extra" to "1")).passed)
        assertFalse(run("{ not json").passed)
        assertFalse(run("").passed)
        assertFalse(run("[]").passed)
    }

    @Test
    fun `T-BUILD-4 a test fixture is allowed only for the debug fixture`() {
        assertFalse(run(file("test_fixture" to "true")).passed)
        assertTrue(run(file("test_fixture" to "true"), fixture = true).passed)
    }
}

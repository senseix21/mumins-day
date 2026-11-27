package com.senseix21.muminsday.ui

import com.senseix21.muminsday.health.BrandSteps
import com.senseix21.muminsday.health.Health
import com.senseix21.muminsday.health.HealthFacts
import com.senseix21.muminsday.health.assessHealth
import com.senseix21.muminsday.planner.Salah
import com.senseix21.muminsday.store.AlarmKind
import com.senseix21.muminsday.store.AlarmLogRow
import com.senseix21.muminsday.store.LoggedAlarm
import com.senseix21.muminsday.store.Outcome
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class ReportTest {
    private val now = Instant.parse("2026-11-27T06:00:00Z")
    private val planned = Instant.parse("2026-11-27T00:00:00Z")
    private val row = AlarmLogRow(1, LoggedAlarm(Salah.Fajr, AlarmKind.Adhan, planned), planned.plusSeconds(4), Outcome.Rang)

    private fun facts(health: Health, log: List<AlarmLogRow>) =
        ReportFacts("0.1.0", "16 (API 36)", "Google", "Pixel", health, log)

    @Test
    fun `the report carries versions, model, health and the log`() {
        val bad = assessHealth(now, HealthFacts(false, true, true, BrandSteps.NotNeeded, emptyList()))
        val text = reportText(facts(bad, listOf(row)))
        assertContains(text, "App version: 0.1.0")
        assertContains(text, "Android: 16 (API 36)")
        assertContains(text, "Phone: Google Pixel")
        assertContains(text, "exact alarms not allowed")
        assertContains(text, "Fajr Adhan planned 2026-11-27T00:00:00Z +4s Rang")
    }

    @Test
    fun `an armed phone and an empty log still say so in words`() {
        val text = reportText(facts(Health.Armed, emptyList()))
        assertContains(text, "no problem found")
        assertContains(text, "no alarms logged")
    }

    @Test
    fun `the report holds no place and no identifier fields`() {
        val text = reportText(facts(Health.Armed, listOf(row))).lowercase()
        for (word in listOf("latitude", "longitude", "district", "imei", "android_id", "serial", "email", "account")) {
            assertFalse(word in text, word)
        }
    }
}

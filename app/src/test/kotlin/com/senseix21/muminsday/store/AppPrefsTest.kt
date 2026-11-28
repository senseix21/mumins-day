package com.senseix21.muminsday.store

import com.senseix21.muminsday.districts.DistrictId
import com.senseix21.muminsday.l10n.Language
import com.senseix21.muminsday.quran.Edition
import com.senseix21.muminsday.quran.SurahNumber
import com.senseix21.muminsday.quran.VerseRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppPrefsTest {
    private val chosen = AppPrefs(
        Language.English, SilentMode.AlwaysRing, onboarded = true, districtId = DistrictId.of("dhaka"),
        quranPosition = VerseRef(SurahNumber.of(2)!!, 255), quranTranslation = Edition.BengaliRwwad,
    )

    @Test
    fun `nothing stored gives the defaults - Bengali, respect silent mode, not onboarded`() {
        val prefs = parseAppPrefs(emptyMap<String, String>())
        assertEquals(Language.Bengali, prefs.language)
        assertEquals(SilentMode.RespectSilent, prefs.silentMode)
        assertFalse(prefs.onboarded)
        assertEquals(null, prefs.districtId)
    }

    @Test
    fun `Ramadan mode is off by default, the user's own switch, and survives a round trip`() {
        assertFalse(parseAppPrefs(emptyMap<String, String>()).ramadanMode)
        assertFalse(AppPrefs().ramadanMode)
        val on = chosen.copy(ramadanMode = true)
        assertTrue(parseAppPrefs(encodeAppPrefs(on)).ramadanMode)
        assertEquals(on, parseAppPrefs(encodeAppPrefs(on)))
        assertEquals(chosen, parseAppPrefs(encodeAppPrefs(on.copy(ramadanMode = false))))
        // Anything but the word "true" is off, so a damaged value cannot switch the screen over.
        for (bad in listOf("", "True", "1", "yes", "on")) assertFalse(parseAppPrefs(encodeAppPrefs(on) + ("app.ramadanMode" to bad)).ramadanMode, bad)
        assertTrue("app.ramadanMode" in appPrefKeys())
    }

    // ADR-24: the khatam mention in the nudge is off unless the user turns it on.
    @Test
    fun `the khatam mention in the nudge is off by default and survives a round trip`() {
        assertFalse(parseAppPrefs(emptyMap<String, String>()).khatamInNudge)
        assertFalse(AppPrefs().khatamInNudge)
        val on = chosen.copy(khatamInNudge = true)
        assertEquals(on, parseAppPrefs(encodeAppPrefs(on)))
        for (bad in listOf("", "True", "1")) assertFalse(parseAppPrefs(encodeAppPrefs(on) + ("app.khatamInNudge" to bad)).khatamInNudge, bad)
        assertTrue("app.khatamInNudge" in appPrefKeys())
    }

    @Test
    fun `the Hijri adjustment defaults to none, survives a round trip and ignores anything but -1, 0 and 1`() {
        assertEquals(com.senseix21.muminsday.hijri.HijriAdjustment.None, AppPrefs().hijriAdjustment)
        for (adjustment in com.senseix21.muminsday.hijri.HijriAdjustment.entries) {
            val prefs = chosen.copy(hijriAdjustment = adjustment)
            assertEquals(prefs, parseAppPrefs(encodeAppPrefs(prefs)))
        }
        for (bad in listOf("", "2", "-2", "x", "1.0")) {
            assertEquals(com.senseix21.muminsday.hijri.HijriAdjustment.None, parseAppPrefs(mapOf("app.hijriAdjustment" to bad)).hijriAdjustment, bad)
        }
    }

    @Test
    fun `what is written reads back the same`() {
        assertEquals(chosen, parseAppPrefs(encodeAppPrefs(chosen)))
    }

    @Test
    fun `unknown values fall back one by one`() {
        val stored = encodeAppPrefs(chosen) + mapOf("app.language" to "Klingon", "app.districtId" to "  ")
        val parsed = parseAppPrefs(stored)
        assertEquals(Language.Bengali, parsed.language)
        assertEquals(SilentMode.AlwaysRing, parsed.silentMode)
        assertEquals(null, parsed.districtId)
        assertEquals(chosen.copy(language = Language.Bengali, districtId = null), parsed)
        parseAppPrefs(mapOf("app.onboarded" to 3, "app.silentMode" to null))
    }

    @Test
    fun `G27 - writing planner settings leaves app preferences and unknown keys alone`() {
        val existing = encodeSettings(defaultSettings).keys + encodeAppPrefs(chosen).keys + "something.else" + "v1.retiredKey"
        val edits = settingsEdits(existing, defaultSettings)
        assertEquals(setOf("v1.retiredKey"), edits.remove)
        assertTrue(edits.put.keys.all { it.startsWith("v1.") })
    }

    @Test
    fun `writing app preferences leaves planner settings alone and drops a cleared district`() {
        val existing = encodeSettings(defaultSettings).keys + encodeAppPrefs(chosen).keys
        val edits = appPrefsEdits(existing, chosen.copy(districtId = null))
        assertEquals(setOf("app.districtId"), edits.remove)
        assertTrue(edits.put.keys.all { it.startsWith("app.") })
        assertEquals(appPrefKeys(), encodeAppPrefs(chosen).keys)
    }

    @Test
    fun `the Quran position survives a round trip and anything malformed is no position`() {
        assertEquals(VerseRef(SurahNumber.of(2)!!, 255), parseAppPrefs(encodeAppPrefs(chosen)).quranPosition)
        for (bad in listOf("", "2", "2:", ":5", "0:1", "115:1", "2:0", "2:-1", "x:y")) {
            assertEquals(null, parseAppPrefs(mapOf("app.quranPosition" to bad)).quranPosition, bad)
        }
        assertEquals(setOf("app.quranPosition"), appPrefsEdits(encodeAppPrefs(chosen).keys, chosen.copy(quranPosition = null)).remove)
    }

    // ADR-26: the app never picks a translation; the user does, and the choice survives a round trip.
    @Test
    fun `no translation is chosen until the user picks one, and an unknown key is no choice`() {
        assertEquals(null, AppPrefs().quranTranslation)
        assertEquals(null, parseAppPrefs(emptyMap<String, String>()).quranTranslation)
        for (edition in Edition.entries) assertEquals(edition, parseAppPrefs(encodeAppPrefs(chosen.copy(quranTranslation = edition))).quranTranslation)
        for (bad in listOf("", "zakaria", "BengaliRwwad", "english_saheeh")) {
            assertEquals(null, parseAppPrefs(mapOf("app.quranTranslation" to bad)).quranTranslation, bad)
        }
        assertEquals(setOf("app.quranTranslation"), appPrefsEdits(encodeAppPrefs(chosen).keys, chosen.copy(quranTranslation = null)).remove)
    }
}

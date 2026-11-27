package com.senseix21.muminsday.store

import com.senseix21.muminsday.districts.DistrictId
import com.senseix21.muminsday.l10n.Language
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppPrefsTest {
    private val chosen = AppPrefs(Language.English, SilentMode.AlwaysRing, onboarded = true, districtId = DistrictId.of("dhaka"))

    @Test
    fun `nothing stored gives the defaults - Bengali, respect silent mode, not onboarded`() {
        val prefs = parseAppPrefs(emptyMap<String, String>())
        assertEquals(Language.Bengali, prefs.language)
        assertEquals(SilentMode.RespectSilent, prefs.silentMode)
        assertFalse(prefs.onboarded)
        assertEquals(null, prefs.districtId)
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
}

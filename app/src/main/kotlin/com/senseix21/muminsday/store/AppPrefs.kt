package com.senseix21.muminsday.store

import com.senseix21.muminsday.districts.DistrictId
import com.senseix21.muminsday.hijri.HijriAdjustment
import com.senseix21.muminsday.l10n.Language
import com.senseix21.muminsday.quran.Edition
import com.senseix21.muminsday.quran.SurahNumber
import com.senseix21.muminsday.quran.VerseRef

/** F1 #15: whether the Adhan sounds when the phone is on silent or vibrate. */
enum class SilentMode { RespectSilent, AlwaysRing }

/**
 * Choices that are not part of the planner's input: what the user picked on the first-run screens. Stored beside the
 * planner settings, under their own prefix, and so backed up with them.
 */
data class AppPrefs(
    val language: Language = Language.Bengali,
    /** The spec's default (F1 #15, decided 2026-11-27). */
    val silentMode: SilentMode = SilentMode.RespectSilent,
    /** False until the first-run screens are finished; false again for a fresh install. */
    val onboarded: Boolean = false,
    /** Which district the place came from, so the settings screen can name it. Null until one is picked. */
    val districtId: DistrictId? = null,
    /**
     * Ramadan mode (F2 #5, G33). The user turns it on and off. When the calculated Hijri month is Ramadan the home
     * screen offers it, but only a tap turns it on, because the calculated date can be a day out. Off by default.
     */
    val ramadanMode: Boolean = false,
    /** The user's move of the calculated Hijri date, one day either way (INV-H1). */
    val hijriAdjustment: HijriAdjustment = HijriAdjustment.None,
    /** Where the Quran reader was left (phase 14). Backed up with the settings, so a new phone opens at the same ayah. */
    val quranPosition: VerseRef? = null,
    /** The daily nudge also mentions a waiting khatam portion (ADR-24). Off by default; it adds no notification. */
    val khatamInNudge: Boolean = false,
    /**
     * The Bengali translation shown under each ayah and searched (ADR-26). Null until the user picks one: the app never
     * picks a translation for them, so the Quran screen asks, and until then shows the Arabic alone.
     */
    val quranTranslation: Edition? = null,
)

private const val A = "app."

/** Parse at the edge: each value that is missing or unknown takes its default on its own; nothing throws. */
fun parseAppPrefs(stored: Map<String, *>): AppPrefs {
    fun text(key: String): String? = stored[A + key] as? String
    val defaults = AppPrefs()
    return AppPrefs(
        language = Language.entries.firstOrNull { it.name == text("language") } ?: defaults.language,
        silentMode = SilentMode.entries.firstOrNull { it.name == text("silentMode") } ?: defaults.silentMode,
        onboarded = text("onboarded") == "true",
        districtId = text("districtId")?.let(DistrictId::of),
        ramadanMode = text("ramadanMode") == "true",
        hijriAdjustment = HijriAdjustment.ofDays(text("hijriAdjustment")?.toIntOrNull() ?: 0),
        quranPosition = text("quranPosition")?.let(::parseVerseRef),
        khatamInNudge = text("khatamInNudge") == "true",
        quranTranslation = text("quranTranslation")?.let(Edition::ofKey),
    )
}

fun encodeAppPrefs(prefs: AppPrefs): Map<String, String> = buildMap {
    put(A + "language", prefs.language.name)
    put(A + "silentMode", prefs.silentMode.name)
    put(A + "onboarded", prefs.onboarded.toString())
    put(A + "ramadanMode", prefs.ramadanMode.toString())
    put(A + "hijriAdjustment", prefs.hijriAdjustment.days.toString())
    put(A + "khatamInNudge", prefs.khatamInNudge.toString())
    prefs.districtId?.let { put(A + "districtId", it.value) }
    prefs.quranPosition?.let { put(A + "quranPosition", "${it.surah.value}:${it.ayah}") }
    prefs.quranTranslation?.let { put(A + "quranTranslation", it.key) }
}

/** "2:255" back to a position; anything else is no position. Whether the ayah exists is checked when the reader opens. */
private fun parseVerseRef(text: String): VerseRef? {
    val surah = text.substringBefore(':').toIntOrNull()?.let(SurahNumber::of) ?: return null
    val ayah = text.substringAfter(':', "").toIntOrNull()?.takeIf { it >= 1 } ?: return null
    return VerseRef(surah, ayah)
}

/** The keys [encodeAppPrefs] can write, so a write removes a district or a position that is no longer set. */
fun appPrefKeys(): Set<String> = encodeAppPrefs(
    AppPrefs(
        districtId = DistrictId.of("x"),
        quranPosition = SurahNumber.of(1)?.let { VerseRef(it, 1) },
        quranTranslation = Edition.entries.first(),
    ),
).keys

/** What one write does to the settings file: every key to put, and the stale keys to remove. Nothing else is touched. */
class Edits(val remove: Set<String>, val put: Map<String, String>)

/**
 * G27: the planner settings own the `v1.` keys and only those. A write replaces them and leaves every other key (the
 * app preferences) alone, so one writer cannot wipe another's values.
 */
fun settingsEdits(existingKeys: Set<String>, settings: com.senseix21.muminsday.planner.PlannerSettings): Edits {
    val put = encodeSettings(settings)
    return Edits(remove = existingKeys.filter { it.startsWith(SETTINGS_PREFIX) && it !in put }.toSet(), put = put)
}

fun appPrefsEdits(existingKeys: Set<String>, prefs: AppPrefs): Edits {
    val put = encodeAppPrefs(prefs)
    return Edits(remove = existingKeys.filter { it.startsWith(A) && it !in put }.toSet(), put = put)
}

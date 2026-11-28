package com.senseix21.muminsday.store

import android.content.SharedPreferences
import com.senseix21.muminsday.clock.AsrConvention
import com.senseix21.muminsday.clock.ClockSettings
import com.senseix21.muminsday.clock.Latitude
import com.senseix21.muminsday.clock.Longitude
import com.senseix21.muminsday.clock.Meters
import com.senseix21.muminsday.clock.Minutes
import com.senseix21.muminsday.clock.Calibration
import com.senseix21.muminsday.clock.Offsets
import com.senseix21.muminsday.clock.Place
import com.senseix21.muminsday.clock.Prayer
import com.senseix21.muminsday.planner.PlannerSettings
import com.senseix21.muminsday.planner.PreReminder
import com.senseix21.muminsday.planner.Reminder
import com.senseix21.muminsday.planner.ReminderMode
import com.senseix21.muminsday.planner.Reminders
import com.senseix21.muminsday.planner.Salah
import java.time.DateTimeException
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.ResolverStyle

/** INV-T6: a per-prayer offset is at most this many minutes either way. */
const val MAX_OFFSET_MINUTES: Int = 30

/** Most minutes before Fajr that eating may be asked to stop (target; the reviewer rules on it, Q3). */
const val MAX_SEHRI_MARGIN_MINUTES: Int = 60

/** The file name, and the prefix of every key. A change of meaning gets a new prefix, never a reused key. */
const val SETTINGS_FILE: String = "settings"
/** Every planner-settings key starts with this; [settingsEdits] touches no other. */
const val SETTINGS_PREFIX: String = "v1."
private const val V = SETTINGS_PREFIX

/**
 * What a user has not chosen yet. The place is Dhaka until a district is picked (ASSUMED coordinates; the district
 * list replaces them, Q4). Every reminder is Off: nothing rings until the user turns it on, so a default can never
 * wake a phone that was never asked to.
 */
val defaultSettings: PlannerSettings = PlannerSettings(
    place = Place(
        latitude = checkNotNull(Latitude.of(23.8103)),
        longitude = checkNotNull(Longitude.of(90.4125)),
        elevation = checkNotNull(Meters.of(0.0)),
        zone = ZoneId.of("Asia/Dhaka"),
    ),
    // The Foundation's timetable is what mosques in Bangladesh follow (ADR-16); the user's offsets start from it.
    clock = ClockSettings(calibration = Calibration.islamicFoundation),
    reminders = Reminders.allOff,
)

/**
 * Parse at the edge: [stored] is `SharedPreferences.all`, untrusted. Each setting that is missing or invalid takes its
 * default, on its own, and nothing throws. The place is one unit: a half-valid place is not a place.
 *
 * Every value is stored as a String, because preferences have no double and one type means one way to be wrong.
 */
fun parseSettings(stored: Map<String, *>): PlannerSettings {
    fun text(key: String): String? = stored[V + key] as? String
    fun int(key: String): Int? = text(key)?.toIntOrNull()

    val defaults = defaultSettings

    val asr = AsrConvention.entries.firstOrNull { it.name == text("asr") } ?: defaults.clock.asr
    val sehriMargin = int("sehriMargin")?.takeIf { it in 0..MAX_SEHRI_MARGIN_MINUTES }
        ?.let(::Minutes) ?: defaults.clock.sehriMargin
    var offsets = Offsets.none
    for (prayer in Prayer.entries) {
        val minutes = int("offset.${prayer.name}")?.takeIf { it in -MAX_OFFSET_MINUTES..MAX_OFFSET_MINUTES } ?: continue
        offsets = offsets.with(prayer, Minutes(minutes))
    }

    var reminders = defaults.reminders
    for (salah in Salah.entries) {
        val mode = ReminderMode.entries.firstOrNull { it.name == text("reminder.${salah.name}.mode") } ?: continue
        // Absent or out of range means no pre-reminder; it never invalidates the mode.
        val pre = int("reminder.${salah.name}.pre")?.let(PreReminder::of)
        reminders = reminders.with(salah, Reminder(mode, pre))
    }

    return PlannerSettings(
        place = parsePlace(::text) ?: defaults.place,
        clock = ClockSettings(asr, offsets, sehriMargin, defaults.clock.calibration),
        reminders = reminders,
        deedNudge = parseNudgeTime(text("deedNudge")),
    )
}

/** "HH:mm" and nothing else; absent or anything different is off, so a damaged value never rings at a wrong hour. */
private fun parseNudgeTime(stored: String?): LocalTime? {
    if (stored == null) return null
    return try {
        LocalTime.parse(stored, NUDGE_FORMAT)
    } catch (_: DateTimeParseException) {
        null
    }
}

// STRICT, because the default lenient-ish style reads "24:00" as midnight, which would ring a damaged value at 00:00.
private val NUDGE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm").withResolverStyle(ResolverStyle.STRICT)

private fun parsePlace(text: (String) -> String?): Place? {
    val latitude = text("place.lat")?.toDoubleOrNull()?.let(Latitude::of) ?: return null
    val longitude = text("place.lon")?.toDoubleOrNull()?.let(Longitude::of) ?: return null
    val elevation = text("place.elevation")?.toDoubleOrNull()?.let(Meters::of) ?: return null
    val zone = try {
        ZoneId.of(text("place.zone") ?: return null)
    } catch (_: DateTimeException) {
        return null
    }
    return Place(latitude, longitude, elevation, zone)
}

/** The inverse of [parseSettings]: every key, so a write replaces the whole file and leaves no stale value behind. */
fun encodeSettings(settings: PlannerSettings): Map<String, String> = buildMap {
    put(V + "asr", settings.clock.asr.name)
    put(V + "sehriMargin", settings.clock.sehriMargin.value.toString())
    for (prayer in Prayer.entries) put(V + "offset.${prayer.name}", settings.clock.offsets[prayer].value.toString())
    for (salah in Salah.entries) {
        val reminder = settings.reminders[salah]
        put(V + "reminder.${salah.name}.mode", reminder.mode.name)
        // 0 is "none"; PreReminder.of(0) is null, so it reads back as no pre-reminder.
        put(V + "reminder.${salah.name}.pre", (reminder.preReminder?.minutes ?: 0).toString())
    }
    put(V + "place.lat", settings.place.latitude.degrees.toString())
    put(V + "place.lon", settings.place.longitude.degrees.toString())
    put(V + "place.elevation", settings.place.elevation.value.toString())
    put(V + "place.zone", settings.place.zone.id)
    // Off is the absence of the key, so settingsEdits removes it when the user turns the nudge off.
    settings.deedNudge?.let { put(V + "deedNudge", it.format(NUDGE_FORMAT)) }
}

/** The only reader and writer of the settings file. Planner settings and app preferences share it under different prefixes. */
class SettingsStore(private val preferences: SharedPreferences) {

    fun read(): PlannerSettings = parseSettings(preferences.all)

    fun write(settings: PlannerSettings) = apply(settingsEdits(preferences.all.keys, settings))

    fun readApp(): AppPrefs = parseAppPrefs(preferences.all)

    fun writeApp(prefs: AppPrefs) = apply(appPrefsEdits(preferences.all.keys, prefs))

    private fun apply(edits: Edits) {
        preferences.edit().apply {
            edits.remove.forEach(::remove)
            edits.put.forEach { (key, value) -> putString(key, value) }
        }.apply()
    }
}

/**
 * G27: "I did the brand's battery steps" belongs to this phone, so it lives in its own file, which backup rules leave
 * out. A restore onto another brand's phone must ask again. Nothing else is stored here.
 */
class DeviceStore(private val preferences: SharedPreferences) {
    fun brandStepsConfirmed(): Boolean = preferences.getBoolean("brandStepsConfirmed", false)

    fun confirmBrandSteps() {
        preferences.edit().putBoolean("brandStepsConfirmed", true).apply()
    }
}

const val DEVICE_FILE: String = "device"

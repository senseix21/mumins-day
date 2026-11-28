package com.senseix21.muminsday.store

import com.senseix21.muminsday.clock.AsrConvention
import com.senseix21.muminsday.clock.Calibration
import com.senseix21.muminsday.clock.ClockSettings
import com.senseix21.muminsday.clock.Minutes
import com.senseix21.muminsday.clock.Offsets
import com.senseix21.muminsday.clock.Prayer
import com.senseix21.muminsday.planner.PreReminder
import com.senseix21.muminsday.planner.Reminder
import com.senseix21.muminsday.planner.ReminderMode
import com.senseix21.muminsday.planner.Reminders
import com.senseix21.muminsday.planner.Salah
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals

class SettingsTest {

    private val chosen = defaultSettings.copy(
        clock = ClockSettings(
            asr = AsrConvention.Shafi,
            offsets = Offsets.none.with(Prayer.Fajr, Minutes(-4)).with(Prayer.Isha, Minutes(30)),
            sehriMargin = Minutes(5),
            // Not stored: the calibration is fixed by the app (ADR-16), so a round trip must keep the default one.
            calibration = defaultSettings.clock.calibration,
        ),
        reminders = Reminders.allOff
            .with(Salah.Fajr, Reminder(ReminderMode.Adhan, PreReminder.of(15)))
            .with(Salah.Asr, Reminder(ReminderMode.Silent)),
    )

    @Test
    fun `nothing stored gives the defaults`() {
        assertEquals(defaultSettings, parseSettings(emptyMap<String, String>()))
    }

    @Test
    fun `the deed nudge is off by default and round trips as HH-mm`() {
        assertEquals(null, defaultSettings.deedNudge)
        assertEquals(null, parseSettings(emptyMap<String, String>()).deedNudge)
        val nudged = chosen.copy(deedNudge = LocalTime.of(7, 5))
        assertEquals("07:05", encodeSettings(nudged)["v1.deedNudge"])
        assertEquals(nudged, parseSettings(encodeSettings(nudged)))
        assertEquals(LocalTime.of(0, 0), parseSettings(encodeSettings(chosen.copy(deedNudge = LocalTime.MIDNIGHT))).deedNudge)
        assertEquals(LocalTime.of(23, 59), parseSettings(encodeSettings(chosen.copy(deedNudge = LocalTime.of(23, 59)))).deedNudge)
    }

    @Test
    fun `a damaged nudge time is off, never a wrong hour, and the rest survives`() {
        for (bad in listOf("", "9:00", "24:00", "07:60", "07:05:30", "seven", "-1:00", "\u0000")) {
            val parsed = parseSettings(encodeSettings(chosen) + ("v1.deedNudge" to bad))
            assertEquals(chosen, parsed, "'$bad'")
        }
        assertEquals(chosen, parseSettings(encodeSettings(chosen) + ("v1.deedNudge" to 700)))
    }

    @Test
    fun `turning the nudge off removes its key`() {
        val on = encodeSettings(chosen.copy(deedNudge = LocalTime.of(9, 0))).keys
        assertEquals(setOf("v1.deedNudge"), settingsEdits(on, chosen).remove)
    }

    @Test
    fun `defaults ring nothing`() {
        Salah.entries.forEach { assertEquals(ReminderMode.Off, defaultSettings.reminders[it].mode) }
    }

    @Test
    fun `what is written reads back the same`() {
        assertEquals(chosen, parseSettings(encodeSettings(chosen)))
        assertEquals(defaultSettings, parseSettings(encodeSettings(defaultSettings)))
    }

    @Test
    fun `every invalid value falls back on its own and the rest survive`() {
        val stored = encodeSettings(chosen) + mapOf(
            "v1.asr" to "Maliki",
            "v1.sehriMargin" to "-3",
            "v1.offset.Fajr" to "31",
            "v1.offset.Isha" to "abc",
            "v1.reminder.Fajr.mode" to "Shout",
            "v1.reminder.Asr.pre" to "9999",
        )
        val parsed = parseSettings(stored)
        assertEquals(defaultSettings.clock.asr, parsed.clock.asr)
        assertEquals(defaultSettings.clock.sehriMargin, parsed.clock.sehriMargin)
        assertEquals(Minutes(0), parsed.clock.offsets[Prayer.Fajr])
        assertEquals(Minutes(0), parsed.clock.offsets[Prayer.Isha])
        assertEquals(ReminderMode.Off, parsed.reminders[Salah.Fajr].mode)
        // An out-of-range pre-reminder drops the pre-reminder, not the mode.
        assertEquals(Reminder(ReminderMode.Silent), parsed.reminders[Salah.Asr])
    }

    @Test
    fun `a half valid place is replaced whole`() {
        for (bad in listOf("v1.place.lat" to "91", "v1.place.lon" to "NaN", "v1.place.elevation" to "-1", "v1.place.zone" to "Mars/Base")) {
            assertEquals(defaultSettings.place, parseSettings(encodeSettings(chosen) + bad).place, bad.toString())
        }
    }

    @Test
    fun `values of the wrong type or from another version are ignored`() {
        val stored = mapOf<String, Any?>("v1.asr" to 3, "v1.sehriMargin" to 5, "asr" to "Shafi", "v2.asr" to "Shafi")
        assertEquals(defaultSettings, parseSettings(stored))
    }

    @Test
    fun `garbage in any key never throws`() {
        val keys = encodeSettings(chosen).keys
        for (junk in listOf("", " ", "\u0000", "1e999", "-0", "9".repeat(50), "null")) {
            parseSettings(keys.associateWith { junk })
        }
        parseSettings(keys.associateWith { null })
    }

    @Test
    fun `the app starts from the Islamic Foundation calibration and storage cannot change it`() {
        assertEquals(Calibration.islamicFoundation, defaultSettings.clock.calibration)
        assertEquals(Calibration.islamicFoundation, parseSettings(emptyMap<String, Any>()).clock.calibration)
        assertEquals(Calibration.islamicFoundation, parseSettings(mapOf("v1.calibration" to "none")).clock.calibration)
    }
}

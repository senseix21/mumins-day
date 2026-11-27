package com.senseix21.muminsday.store

import com.senseix21.muminsday.clock.AsrConvention
import com.senseix21.muminsday.clock.ClockSettings
import com.senseix21.muminsday.clock.Minutes
import com.senseix21.muminsday.clock.Offsets
import com.senseix21.muminsday.clock.Prayer
import com.senseix21.muminsday.planner.PreReminder
import com.senseix21.muminsday.planner.Reminder
import com.senseix21.muminsday.planner.ReminderMode
import com.senseix21.muminsday.planner.Reminders
import com.senseix21.muminsday.planner.Salah
import kotlin.test.Test
import kotlin.test.assertEquals

class SettingsTest {

    private val chosen = defaultSettings.copy(
        clock = ClockSettings(
            asr = AsrConvention.Shafi,
            offsets = Offsets.none.with(Prayer.Fajr, Minutes(-4)).with(Prayer.Isha, Minutes(30)),
            sehriMargin = Minutes(5),
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
}

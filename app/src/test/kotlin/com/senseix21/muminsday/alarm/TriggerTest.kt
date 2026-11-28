package com.senseix21.muminsday.alarm

import com.senseix21.muminsday.deed.DayCard
import com.senseix21.muminsday.deed.DeedId
import com.senseix21.muminsday.planner.AlarmId
import com.senseix21.muminsday.planner.AlarmSpec
import com.senseix21.muminsday.planner.Reminder
import com.senseix21.muminsday.planner.ReminderMode
import com.senseix21.muminsday.planner.Reminders
import com.senseix21.muminsday.planner.RingMode
import com.senseix21.muminsday.planner.Salah
import com.senseix21.muminsday.store.AlarmKind
import com.senseix21.muminsday.store.LoggedAlarm
import com.senseix21.muminsday.store.SilentMode
import com.senseix21.muminsday.store.defaultSettings
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TriggerTest {
    private val at = Instant.parse("2026-11-27T10:00:00Z")
    private val date = LocalDate.of(2026, 11, 27)

    private fun prayer(mode: RingMode) = AlarmSpec.PrayerAlarm(AlarmId.prayer(date, Salah.Asr), at, Salah.Asr, mode, at)

    @Test
    fun `only the Adhan uses setAlarmClock`() {
        assertEquals(Method.AlarmClock, methodFor(prayer(RingMode.Adhan)))
        assertEquals(Method.ExactWhileIdle, methodFor(prayer(RingMode.Tone)))
        assertEquals(Method.ExactWhileIdle, methodFor(prayer(RingMode.Silent)))
        assertEquals(Method.ExactWhileIdle, methodFor(AlarmSpec.PreReminderAlarm(AlarmId.preReminder(date, Salah.Asr), at.minusSeconds(60), Salah.Asr, at)))
        assertEquals(Method.ExactWhileIdle, methodFor(AlarmSpec.Replan(AlarmId.replan(date), at)))
        assertEquals(Method.ExactWhileIdle, methodFor(AlarmSpec.DeedNudge(AlarmId.deedNudge(date), at)))
    }

    @Test
    fun `the nudge keeps the queue alive on its own but is not a prayer reminder`() {
        val nudgeOnly = defaultSettings.copy(deedNudge = LocalTime.of(9, 0))
        assertEquals(false, alarmsNeeded(defaultSettings))
        assertEquals(true, alarmsNeeded(nudgeOnly))
        assertEquals(false, remindersEnabled(nudgeOnly), "the home screen still says prayer reminders are off")
        val one = defaultSettings.copy(reminders = Reminders.allOff.with(Salah.Isha, Reminder(ReminderMode.Silent)))
        assertEquals(true, alarmsNeeded(one))
    }

    @Test
    fun `a nudge's extras read back as a nudge, and it is not a logged reminder`() {
        val spec = AlarmSpec.DeedNudge(AlarmId.deedNudge(date), at)
        val extras = extrasFor(spec)
        assertEquals(Trigger.Nudge, parseTrigger(extras.kind, extras.salah, extras.plannedAtMillis))
        assertEquals(KIND_NUDGE, extras.kind)
        assertEquals(null, extras.salah)
    }

    @Test
    fun `the nudge is posted unless today's card is done, and a day with no card is not done`() {
        val card = DayCard(date, DeedId.of("test-1")!!, contentVersion = 1, swapped = false, doneAt = null)
        assertEquals(true, nudgeNeeded(null))
        assertEquals(true, nudgeNeeded(card))
        assertEquals(false, nudgeNeeded(card.copy(doneAt = at)))
    }

    @Test
    fun `reminders are enabled when any prayer is not Off`() {
        assertEquals(false, remindersEnabled(defaultSettings))
        val one = defaultSettings.copy(reminders = Reminders.allOff.with(Salah.Isha, Reminder(ReminderMode.Silent)))
        assertEquals(true, remindersEnabled(one))
    }

    @Test
    fun `an alarm's extras read back as the same trigger`() {
        for (mode in RingMode.entries) {
            val spec = prayer(mode)
            val extras = extrasFor(spec)
            assertEquals(
                Trigger.Reminder(LoggedAlarm.of(spec)!!),
                parseTrigger(extras.kind, extras.salah, extras.plannedAtMillis),
            )
        }
        val pre = AlarmSpec.PreReminderAlarm(AlarmId.preReminder(date, Salah.Fajr), at.minusSeconds(900), Salah.Fajr, at)
        val extras = extrasFor(pre)
        assertEquals(Trigger.Reminder(LoggedAlarm(Salah.Fajr, AlarmKind.PreReminder, pre.at)), parseTrigger(extras.kind, extras.salah, extras.plannedAtMillis))
        val replan = extrasFor(AlarmSpec.Replan(AlarmId.replan(date), at))
        assertEquals(Trigger.Replan, parseTrigger(replan.kind, replan.salah, replan.plannedAtMillis))
    }

    @Test
    fun `an intent that cannot be read is Unreadable, never a guess`() {
        assertEquals(Trigger.Unreadable, parseTrigger(null, null, null))
        assertEquals(Trigger.Unreadable, parseTrigger("Adhan", "Fajr", null))
        assertEquals(Trigger.Unreadable, parseTrigger("Nonsense", "Fajr", 1L))
        assertEquals(Trigger.Unreadable, parseTrigger("Adhan", "Nonsense", 1L))
        assertEquals(Trigger.Unreadable, parseTrigger("Adhan", null, 1L))
        assertEquals(Trigger.Test(Instant.ofEpochMilli(5)), parseTrigger(KIND_TEST, null, 5L))
    }

    @Test
    fun `late rule - ten minutes still rings, ten minutes and a second does not`() {
        val planned = at
        assertEquals(Action.Ring(Sound.Adhan), chooseAction(AlarmKind.Adhan, planned, planned.plus(Duration.ofMinutes(5)), false, false))
        assertEquals(Action.Ring(Sound.Adhan), chooseAction(AlarmKind.Adhan, planned, planned.plus(Duration.ofMinutes(10)), false, false))
        assertEquals(Action.Notify(NoticeReason.TooLate), chooseAction(AlarmKind.Adhan, planned, planned.plus(Duration.ofMinutes(15)), false, false))
        assertEquals(Action.Notify(NoticeReason.TooLate), chooseAction(AlarmKind.Tone, planned, planned.plusSeconds(601), false, false))
    }

    @Test
    fun `silent kinds and a call in progress never ring`() {
        assertEquals(Action.Notify(NoticeReason.Silent), chooseAction(AlarmKind.Silent, at, at, false, false))
        assertEquals(Action.Notify(NoticeReason.PreReminder), chooseAction(AlarmKind.PreReminder, at, at, false, false))
        assertEquals(Action.Notify(NoticeReason.PhoneBusy), chooseAction(AlarmKind.Adhan, at, at, true, false))
        assertEquals(Action.Ring(Sound.Tone), chooseAction(AlarmKind.Tone, at, at, false, false))
    }

    @Test
    fun `silent mode - respected only when chosen and the ringer is not normal`() {
        assertEquals(true, mutedByUser(SilentMode.RespectSilent, ringerIsNormal = false))
        assertEquals(false, mutedByUser(SilentMode.RespectSilent, ringerIsNormal = true))
        assertEquals(false, mutedByUser(SilentMode.AlwaysRing, ringerIsNormal = false))
        assertEquals(false, mutedByUser(SilentMode.AlwaysRing, ringerIsNormal = true))
        assertEquals(AudioUse.Alarm, audioUseFor(SilentMode.AlwaysRing))
        assertEquals(AudioUse.Notification, audioUseFor(SilentMode.RespectSilent))
    }

    @Test
    fun `a muted phone gets a silent notification, a late alarm is still too late`() {
        assertEquals(Action.Notify(NoticeReason.SilentMode), chooseAction(AlarmKind.Adhan, at, at, false, true))
        assertEquals(Action.Notify(NoticeReason.SilentMode), chooseAction(AlarmKind.Tone, at, at, false, true))
        assertEquals(Action.Notify(NoticeReason.PhoneBusy), chooseAction(AlarmKind.Adhan, at, at, true, true))
        assertEquals(Action.Notify(NoticeReason.TooLate), chooseAction(AlarmKind.Adhan, at, at.plusSeconds(601), false, true))
    }

    @Test
    fun `brands that need steps`() {
        assertTrue(brandNeedsSteps("Xiaomi"))
        assertTrue(brandNeedsSteps(" realme "))
        assertEquals(false, brandNeedsSteps("Google"))
    }

    @Test
    fun `sine wave has the right length, stays inside the range and starts and ends at zero`() {
        val wave = sineWave(22_050, Duration.ofSeconds(2), 440.0, Duration.ofMillis(100))
        assertEquals(44_100, wave.size)
        assertEquals(0, wave.first().toInt())
        assertTrue(kotlin.math.abs(wave.last().toInt()) < 100)
        assertTrue(wave.any { kotlin.math.abs(it.toInt()) > 10_000 })
        assertTrue(wave.all { kotlin.math.abs(it.toInt()) <= Short.MAX_VALUE / 2 + 1 })
    }

    @Test
    fun `a sound no longer than its own fade does not break`() {
        assertEquals(3, sineWave(1000, Duration.ofMillis(3), 440.0, Duration.ofSeconds(1)).size)
    }
}

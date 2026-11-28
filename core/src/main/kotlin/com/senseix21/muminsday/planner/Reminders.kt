package com.senseix21.muminsday.planner

import com.senseix21.muminsday.clock.ClockSettings
import com.senseix21.muminsday.clock.Place
import com.senseix21.muminsday.clock.Prayer
import java.time.Duration
import java.time.LocalTime

/** The five prayers that can ring. Sehri end and sunrise are not prayers to call, so they are not representable here. */
public enum class Salah(public val prayer: Prayer) {
    Fajr(Prayer.Fajr),
    Dhuhr(Prayer.Dhuhr),
    Asr(Prayer.Asr),
    Maghrib(Prayer.Maghrib),
    Isha(Prayer.Isha),
}

/** [Off] means nothing at all is planned for that prayer, its pre-reminder included. */
public enum class ReminderMode { Adhan, Tone, Silent, Off }

/**
 * Whole minutes before a prayer, 1..120. Zero is not representable, so a pre-reminder can never share an instant with
 * its own prayer (they would collide in the alarm queue and ring together).
 */
@JvmInline
public value class PreReminder private constructor(public val minutes: Int) {
    public val duration: Duration get() = Duration.ofMinutes(minutes.toLong())

    public companion object {
        public const val MAX_MINUTES: Int = 120

        /** Null when [minutes] is outside 1..[MAX_MINUTES]. */
        public fun of(minutes: Int): PreReminder? =
            if (minutes in 1..MAX_MINUTES) PreReminder(minutes) else null
    }
}

public data class Reminder(val mode: ReminderMode, val preReminder: PreReminder? = null)

/** One [Reminder] per [Salah]. */
public class Reminders private constructor(private val bySalah: List<Reminder>) {

    public operator fun get(salah: Salah): Reminder = bySalah[salah.ordinal]

    public fun with(salah: Salah, reminder: Reminder): Reminders =
        Reminders(bySalah.toMutableList().also { it[salah.ordinal] = reminder })

    override fun equals(other: Any?): Boolean = other is Reminders && other.bySalah == bySalah
    override fun hashCode(): Int = bySalah.hashCode()
    override fun toString(): String = Salah.entries.joinToString(prefix = "Reminders(", postfix = ")") { "$it=${this[it]}" }

    public companion object {
        public val allOff: Reminders = all(ReminderMode.Off)

        /** Every prayer in [mode], no pre-reminders. */
        public fun all(mode: ReminderMode): Reminders = Reminders(List(Salah.entries.size) { Reminder(mode) })
    }
}

/**
 * [deedNudge] is the local time of day for the optional daily deed nudge (F4, G35); null means off, which is the default.
 * It is independent of [reminders]: the nudge is planned whether every prayer is Off or not.
 */
public data class PlannerSettings(
    val place: Place,
    val clock: ClockSettings,
    val reminders: Reminders,
    val deedNudge: LocalTime? = null,
)

package com.senseix21.muminsday.planner

import com.senseix21.muminsday.clock.Prayer
import com.senseix21.muminsday.clock.Reason
import java.time.Instant
import java.time.LocalDate

/** What a prayer alarm does when it fires. There is no Off: an Off prayer has no alarm. */
public enum class RingMode { Adhan, Tone, Silent }

public sealed interface AlarmSpec {
    public val id: AlarmId
    public val at: Instant

    public data class PrayerAlarm(
        override val id: AlarmId,
        override val at: Instant,
        val salah: Salah,
        val mode: RingMode,
        val prayerAt: Instant,
    ) : AlarmSpec {
        init {
            require(at == prayerAt) { "a prayer alarm rings at the prayer: at=$at prayerAt=$prayerAt" }
        }
    }

    /** `at` is the prayer's time minus the user's pre-reminder, always strictly before [prayerAt]. */
    public data class PreReminderAlarm(
        override val id: AlarmId,
        override val at: Instant,
        val salah: Salah,
        val prayerAt: Instant,
    ) : AlarmSpec {
        init {
            require(at < prayerAt) { "a pre-reminder precedes its prayer: at=$at prayerAt=$prayerAt" }
        }
    }

    /** Asks the shell to plan again; it carries nothing else. */
    public data class Replan(override val id: AlarmId, override val at: Instant) : AlarmSpec
}

/** A local day the clock could not compute (INV-T7). The day gets no alarms; the times are never guessed. */
public data class Gap(val date: LocalDate, val prayer: Prayer, val reason: Reason)

public data class Plan(val alarms: List<AlarmSpec>, val gaps: List<Gap>)

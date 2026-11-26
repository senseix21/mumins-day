package com.senseix21.muminsday.planner

import com.senseix21.muminsday.clock.ClockResult
import com.senseix21.muminsday.clock.PrayerClock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/**
 * How far ahead alarms are held. 48 h so that losing the replan (a killed app, a missed trigger) still leaves a full
 * day of prayers queued (founder decision; the spec said 24 h, ADR-3). One constant, as ADR-3 promises.
 */
public val PLAN_WINDOW: Duration = Duration.ofHours(48)

/**
 * The replan rings this long after local midnight, not at it, so the clock has certainly turned to the new date and a
 * replan that fires a little early (alarm slop) still plans from the new day.
 */
public val REPLAN_AFTER_MIDNIGHT: Duration = Duration.ofMinutes(5)

/**
 * `plan(now, settings)`: every alarm the shell must register, as a pure function of its arguments (INV-S2, INV-S4).
 * The shell cancels all of [AlarmId.all], then registers exactly this plan. It is never persisted.
 */
public class Planner(private val clock: PrayerClock) {

    public fun plan(now: Instant, settings: PlannerSettings): Plan {
        val zone = settings.place.zone
        val windowEnd = now.plus(PLAN_WINDOW)

        // A pre-reminder in the window may belong to a prayer up to MAX_MINUTES past it, so look that much further.
        // At most 4 local dates result, even across a one-hour DST gap: 48 h + 2 h + 1 h of skipped clock is under
        // 72 h of wall time, and 72 h from any moment touches at most 4 dates. That is why AlarmId has 4 day slots.
        val firstDate = now.atZone(zone).toLocalDate()
        val lastDate = windowEnd.plus(Duration.ofMinutes(PreReminder.MAX_MINUTES.toLong())).atZone(zone).toLocalDate()

        val alarms = mutableListOf<AlarmSpec>()
        val gaps = mutableListOf<Gap>()
        var date = firstDate
        while (!date.isAfter(lastDate)) {
            when (val result = clock.dayTimes(date, settings.place, settings.clock)) {
                is ClockResult.NotComputable -> gaps += Gap(date, result.prayer, result.reason)
                is ClockResult.Computed -> for (salah in Salah.entries) {
                    val reminder = settings.reminders[salah]
                    val ring = when (reminder.mode) {
                        ReminderMode.Adhan -> RingMode.Adhan
                        ReminderMode.Tone -> RingMode.Tone
                        ReminderMode.Silent -> RingMode.Silent
                        ReminderMode.Off -> continue
                    }
                    val prayerAt = result.times[salah.prayer]
                    // Each alarm is tested on its own instant: a past pre-reminder goes while its prayer stays.
                    if (prayerAt.isInWindow(now, windowEnd)) {
                        alarms += AlarmSpec.PrayerAlarm(AlarmId.prayer(date, salah), prayerAt, salah, ring, prayerAt)
                    }
                    val preReminder = reminder.preReminder ?: continue
                    val preAt = prayerAt.minus(preReminder.duration)
                    if (preAt.isInWindow(now, windowEnd)) {
                        alarms += AlarmSpec.PreReminderAlarm(AlarmId.preReminder(date, salah), preAt, salah, prayerAt)
                    }
                }
            }
            date = date.plusDays(1)
        }

        // Always present, even when every reminder is Off: whether reminders are enabled at all is the shell's
        // decision, and it simply does not register a plan then.
        alarms += replan(now, firstDate, settings)

        return Plan(
            alarms = alarms.sortedWith(compareBy<AlarmSpec> { it.at }.thenBy { it.id.value }),
            gaps = gaps,
        )
    }

    /**
     * Strictly after `now`: an alarm at `now` is the one firing or already fired, and registering it again would ring
     * twice (INV-S5). The end is inclusive.
     */
    private fun Instant.isInWindow(now: Instant, windowEnd: Instant): Boolean = this > now && this <= windowEnd

    /** 00:05 local today if that is still ahead, else tomorrow's. `atStartOfDay(zone)` is correct across DST gaps. */
    private fun replan(now: Instant, today: LocalDate, settings: PlannerSettings): AlarmSpec.Replan {
        val zone = settings.place.zone
        fun at(date: LocalDate): Instant = date.atStartOfDay(zone).toInstant().plus(REPLAN_AFTER_MIDNIGHT)
        val date = if (at(today) > now) today else today.plusDays(1)
        val replanAt = at(date)
        return AlarmSpec.Replan(AlarmId.replan(replanAt.atZone(zone).toLocalDate()), replanAt)
    }
}

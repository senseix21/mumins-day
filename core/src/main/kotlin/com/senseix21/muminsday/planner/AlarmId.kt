package com.senseix21.muminsday.planner

import java.time.LocalDate

/**
 * An alarm id in a closed space of 64, so the shell can cancel everything before it registers a plan (ADR-3, INV-S4).
 *
 * Layout: `daySlot * 16 + kindSlot`. `daySlot` is the local date of the prayer the alarm belongs to, modulo 4; the
 * window never touches more than 4 consecutive local dates (see [Planner]). `kindSlot` is `salah.ordinal * 2` for the
 * prayer, `+ 1` for its pre-reminder, and 15 for the replan. Slots 10..14 are unused, kept so the layout stays at 16
 * per day and a sixth prayer-like alarm needs no renumbering.
 *
 * The id depends only on (local date, salah, main or pre), never on `now`, so replanning gives the same alarm the same
 * id and registering it again replaces it (INV-S4).
 */
@JvmInline
public value class AlarmId private constructor(public val value: Int) {

    public companion object {
        private const val DAY_SLOTS = 4
        private const val KINDS_PER_DAY = 16
        private const val REPLAN_KIND = 15

        public val SPACE: IntRange = 0 until DAY_SLOTS * KINDS_PER_DAY

        /** Every id the app can ever register. */
        public val all: List<AlarmId> = SPACE.map { AlarmId(it) }

        /** For reading an id back from an intent. Null outside [SPACE]. */
        public fun of(value: Int): AlarmId? = if (value in SPACE) AlarmId(value) else null

        public fun prayer(date: LocalDate, salah: Salah): AlarmId = at(date, salah.ordinal * 2)

        public fun preReminder(date: LocalDate, salah: Salah): AlarmId = at(date, salah.ordinal * 2 + 1)

        public fun replan(date: LocalDate): AlarmId = at(date, REPLAN_KIND)

        private fun at(date: LocalDate, kindSlot: Int): AlarmId =
            AlarmId(date.toEpochDay().mod(DAY_SLOTS) * KINDS_PER_DAY + kindSlot)
    }
}

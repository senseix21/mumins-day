package com.senseix21.muminsday.planner

import com.senseix21.muminsday.clock.AsrConvention
import com.senseix21.muminsday.clock.Case
import com.senseix21.muminsday.clock.ClockResult
import com.senseix21.muminsday.clock.ClockSettings
import com.senseix21.muminsday.clock.DayTimes
import com.senseix21.muminsday.clock.Latitude
import com.senseix21.muminsday.clock.Longitude
import com.senseix21.muminsday.clock.Meters
import com.senseix21.muminsday.clock.Minutes
import com.senseix21.muminsday.clock.Offsets
import com.senseix21.muminsday.clock.Place
import com.senseix21.muminsday.clock.Prayer
import com.senseix21.muminsday.clock.PrayerClock
import com.senseix21.muminsday.clock.Reason
import com.senseix21.muminsday.clock.cases
import com.senseix21.muminsday.clock.clock
import com.senseix21.muminsday.clock.computed
import com.senseix21.muminsday.clock.dhaka
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale
import java.util.TimeZone
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val planner = Planner(clock)

private class Scenario(val case: Case, val now: Instant, val settings: PlannerSettings) {
    override fun toString() = "now=$now date=${case.date} place=${case.place} settings=${settings.clock} ${settings.reminders}"
}

private fun randomClockSettings(random: Random) = ClockSettings(
    asr = AsrConvention.entries.random(random),
    offsets = Prayer.entries.fold(Offsets.none) { offsets, prayer -> offsets.with(prayer, Minutes(random.nextInt(-30, 31))) },
    sehriMargin = Minutes(random.nextInt(0, 31)),
)

private fun randomReminders(random: Random) = Salah.entries.fold(Reminders.allOff) { reminders, salah ->
    val preReminder = if (random.nextBoolean()) PreReminder.of(random.nextInt(1, PreReminder.MAX_MINUTES + 1)) else null
    reminders.with(salah, Reminder(ReminderMode.entries.random(random), preReminder))
}

/** Bangladesh places and dates from Support.kt; `now` is anywhere in the local day, sometimes with nanoseconds. */
private fun scenarios(count: Int, seed: Long): List<Scenario> {
    val random = Random(seed)
    return cases(count, seed).map { case ->
        val startOfDay = case.date.atStartOfDay(case.place.zone).toInstant()
        val now = startOfDay.plusSeconds(random.nextLong(0, 86_400)).plusNanos(if (random.nextBoolean()) random.nextLong(0, 1_000_000_000) else 0)
        Scenario(case, now, PlannerSettings(case.place, randomClockSettings(random), randomReminders(random)))
    }
}

private fun Scenario.plan() = planner.plan(now, settings)

private fun Instant.localDate(zone: ZoneId): LocalDate = atZone(zone).toLocalDate()

/** The local date of the prayer an alarm belongs to; not defined for the replan. */
private fun AlarmSpec.prayerDate(zone: ZoneId): LocalDate? = when (this) {
    is AlarmSpec.PrayerAlarm -> prayerAt.localDate(zone)
    is AlarmSpec.PreReminderAlarm -> prayerAt.localDate(zone)
    is AlarmSpec.Replan -> null
}

private data class Entry(val at: Instant, val salah: Salah, val label: String)

private val entryOrder = compareBy<Entry>({ it.at }, { it.salah.ordinal }, { it.label })

private fun RingMode.label() = "prayer:$name"

/**
 * INV-S1, derived independently of the planner: ask the clock for every date around `now` and apply the window rule to
 * each time by itself.
 */
private fun assertComplete(now: Instant, settings: PlannerSettings, plan: Plan, where: String) {
    val zone = settings.place.zone
    val end = now.plus(PLAN_WINDOW)
    val first = now.localDate(zone)
    val expected = mutableListOf<Entry>()
    for (offset in -1L..5L) {
        val result = clock.dayTimes(first.plusDays(offset), settings.place, settings.clock)
        if (result !is ClockResult.Computed) continue
        for (salah in Salah.entries) {
            val reminder = settings.reminders[salah]
            val ring = when (reminder.mode) {
                ReminderMode.Adhan -> RingMode.Adhan
                ReminderMode.Tone -> RingMode.Tone
                ReminderMode.Silent -> RingMode.Silent
                ReminderMode.Off -> continue
            }
            val t = result.times[salah.prayer]
            if (t > now && t <= end) expected += Entry(t, salah, ring.label())
            val pre = reminder.preReminder ?: continue
            val preAt = t.minus(pre.duration)
            if (preAt > now && preAt <= end) expected += Entry(preAt, salah, "pre")
        }
    }
    val actual = plan.alarms.mapNotNull {
        when (it) {
            is AlarmSpec.PrayerAlarm -> Entry(it.at, it.salah, it.mode.label())
            is AlarmSpec.PreReminderAlarm -> Entry(it.at, it.salah, "pre")
            is AlarmSpec.Replan -> null
        }
    }
    assertEquals(expected.sortedWith(entryOrder), actual.sortedWith(entryOrder), "INV-S1 completeness: $where")
}

private fun assertIdsDistinctAndInSpace(plan: Plan, where: String) {
    val ids = plan.alarms.map { it.id }
    assertEquals(ids.size, ids.toSet().size, "ids distinct: $where")
    assertTrue(AlarmId.all.containsAll(ids), "ids inside AlarmId.all: $where")
}

private fun assertOneReplan(now: Instant, zone: ZoneId, plan: Plan, where: String) {
    val replans = plan.alarms.filterIsInstance<AlarmSpec.Replan>()
    assertEquals(1, replans.size, "exactly one replan: $where")
    val replan = replans.single()
    assertTrue(replan.at > now, "replan after now: $where")
    assertEquals(LocalTime.of(0, 5), replan.at.atZone(zone).toLocalTime(), "replan at 00:05 local: $where")
    assertEquals(AlarmId.replan(replan.at.localDate(zone)), replan.id, "replan id from its own date: $where")
}

class PlannerTest {

    @Test
    fun INV_S1_completeness_for_random_places_dates_and_settings() {
        for (scenario in scenarios(count = 1500, seed = 21)) {
            assertComplete(scenario.now, scenario.settings, scenario.plan(), "$scenario")
        }
    }

    @Test
    fun INV_S1_corollary_next_fajr_is_always_planned_and_five_adhans_give_about_ten() {
        for (scenario in scenarios(count = 800, seed = 22)) {
            val zone = scenario.settings.place.zone
            val fajrOn = scenario.settings.copy(reminders = scenario.settings.reminders.with(Salah.Fajr, Reminder(ReminderMode.Adhan)))
            val nextFajr = (-1L..3L)
                .map { computed(clock.dayTimes(scenario.now.localDate(zone).plusDays(it), fajrOn.place, fajrOn.clock)).fajr }
                .filter { it > scenario.now }
                .min()
            val fajrs = planner.plan(scenario.now, fajrOn).alarms.filterIsInstance<AlarmSpec.PrayerAlarm>().filter { it.salah == Salah.Fajr }
            assertTrue(fajrs.any { it.at == nextFajr }, "next Fajr $nextFajr in plan: $scenario")

            val allAdhan = scenario.settings.copy(reminders = Reminders.all(ReminderMode.Adhan))
            val count = planner.plan(scenario.now, allAdhan).alarms.count { it is AlarmSpec.PrayerAlarm }
            // Each prayer recurs about every 24 h (a few minutes apart), so a 48 h window holds it twice; a prayer
            // whose two occurrences straddle the window's edge by those minutes counts once or three times. One such
            // prayer is typical at most; 9..11 allows one prayer off by one in either direction.
            assertTrue(count in 9..11, "$count prayer alarms: $scenario")
        }
    }

    @Test
    fun T_PROP_7_every_prayer_alarm_is_the_clocks_time_for_that_local_date() {
        for (scenario in scenarios(count = 1000, seed = 23)) {
            val zone = scenario.settings.place.zone
            for (alarm in scenario.plan().alarms.filterIsInstance<AlarmSpec.PrayerAlarm>()) {
                val times = computed(clock.dayTimes(alarm.at.localDate(zone), scenario.settings.place, scenario.settings.clock))
                assertEquals(times[alarm.salah.prayer], alarm.at, "$alarm in $scenario")
            }
        }
    }

    @Test
    fun T_PROP_7_plan_does_not_depend_on_the_jvm_default_zone_or_locale() {
        val subjects = scenarios(count = 150, seed = 24)
        val baseline = subjects.map { it.plan() }
        val originalZone = TimeZone.getDefault()
        val originalLocale = Locale.getDefault()
        try {
            val zones = listOf("UTC", "America/New_York", "Asia/Dhaka", "Pacific/Kiritimati")
            val locales = listOf(Locale.ROOT, Locale.forLanguageTag("bn-BD"), Locale.forLanguageTag("ar-SA"), Locale.forLanguageTag("th-TH-u-nu-thai"))
            for (zoneId in zones) {
                for (locale in locales) {
                    TimeZone.setDefault(TimeZone.getTimeZone(zoneId))
                    Locale.setDefault(locale)
                    for ((index, scenario) in subjects.withIndex()) {
                        assertEquals(baseline[index], scenario.plan(), "default zone $zoneId locale $locale: $scenario")
                    }
                }
            }
        } finally {
            TimeZone.setDefault(originalZone)
            Locale.setDefault(originalLocale)
        }
    }

    @Test
    fun a_dhaka_place_gives_the_same_plan_whatever_zone_the_phone_is_in() {
        val place = Place(Latitude.of(23.8103)!!, Longitude.of(90.4125)!!, Meters.of(8.0)!!, ZoneId.of("Asia/Dhaka"))
        val settings = PlannerSettings(place, ClockSettings(), Reminders.all(ReminderMode.Adhan))
        val now = LocalDateTime.of(2026, 11, 26, 23, 40).atZone(ZoneId.of("Asia/Dhaka")).toInstant()
        val originalZone = TimeZone.getDefault()
        try {
            val plans = listOf("Pacific/Kiritimati", "America/New_York", "Asia/Dhaka").map { zoneId ->
                TimeZone.setDefault(TimeZone.getTimeZone(zoneId))
                planner.plan(now, settings)
            }
            assertEquals(plans[2], plans[0])
            assertEquals(plans[2], plans[1])
        } finally {
            TimeZone.setDefault(originalZone)
        }
    }

    @Test
    fun INV_S4_plan_is_deterministic() {
        for (scenario in scenarios(count = 300, seed = 25)) {
            assertEquals(scenario.plan(), scenario.plan(), "$scenario")
        }
    }

    @Test
    fun INV_S4_replanning_later_keeps_ids_and_instants_of_what_is_still_ahead() {
        val random = Random(26)
        for (scenario in scenarios(count = 1000, seed = 26)) {
            val later = scenario.now.plusSeconds(random.nextLong(0, 6 * 3600 + 1))
            val first = planner.plan(scenario.now, scenario.settings)
            val second = planner.plan(later, scenario.settings)
            val where = "later=$later $scenario"
            val stillAhead = first.alarms.filter { it.at > later }
            assertTrue(second.alarms.containsAll(stillAhead.filterNot { it is AlarmSpec.Replan }), "kept: $where")
            // The replan may move (it is the next 00:05 after `now`), but only forward, and never by changing ids of prayers.
            val dueByFirst = second.alarms.filter { it.at <= scenario.now.plus(PLAN_WINDOW) && it !is AlarmSpec.Replan }
            assertTrue(first.alarms.containsAll(dueByFirst), "nothing new inside the old window: $where")
            for (alarm in stillAhead) {
                val same = second.alarms.filter { it.id == alarm.id }
                assertEquals(1, same.size, "id ${alarm.id} once in the later plan: $where")
                if (alarm !is AlarmSpec.Replan) assertEquals(alarm, same.single(), "same alarm under the same id: $where")
            }
        }
    }

    @Test
    fun INV_S5_nothing_at_or_before_now_and_the_boundary_is_exact() {
        for (scenario in scenarios(count = 500, seed = 27)) {
            val plan = scenario.plan()
            assertTrue(plan.alarms.all { it.at > scenario.now }, "no alarm at or before now: $scenario")

            val everything = PlannerSettings(
                scenario.settings.place,
                scenario.settings.clock,
                Salah.entries.fold(Reminders.allOff) { r, s -> r.with(s, Reminder(ReminderMode.Adhan, PreReminder.of(10))) },
            )
            val target = planner.plan(scenario.now, everything).alarms.filterIsInstance<AlarmSpec.PrayerAlarm>().first()
            val pre = planner.plan(scenario.now, everything).alarms.filterIsInstance<AlarmSpec.PreReminderAlarm>().first()
            for (alarm in listOf(target, pre)) {
                val atInstant = planner.plan(alarm.at, everything).alarms
                val oneNanoEarlier = planner.plan(alarm.at.minusNanos(1), everything).alarms
                assertTrue(atInstant.none { it.id == alarm.id && it.at == alarm.at }, "omitted when now == at: $alarm")
                assertTrue(oneNanoEarlier.any { it.id == alarm.id && it.at == alarm.at }, "included 1 ns before: $alarm")
            }
        }
    }

    @Test
    fun INV_S5_window_end_is_inclusive() {
        val scenario = scenarios(count = 1, seed = 28).single()
        val everything = scenario.settings.copy(reminders = Reminders.all(ReminderMode.Adhan))
        val lastInWindow = planner.plan(scenario.now, everything).alarms.filterIsInstance<AlarmSpec.PrayerAlarm>().last()
        val exactlyAtEnd = lastInWindow.at.minus(PLAN_WINDOW)
        assertTrue(planner.plan(exactlyAtEnd, everything).alarms.any { it.id == lastInWindow.id && it.at == lastInWindow.at }, "at == now + 48 h is in")
        assertTrue(planner.plan(exactlyAtEnd.minusNanos(1), everything).alarms.none { it.id == lastInWindow.id && it.at == lastInWindow.at }, "1 ns beyond is out")
    }

    @Test
    fun a_past_pre_reminder_is_dropped_while_its_prayer_stays_and_a_pre_reminder_in_the_window_keeps_a_prayer_beyond_it() {
        val place = scenarios(count = 1, seed = 29).single().case.place
        val date = LocalDate.of(2026, 6, 10)
        val reminders = Reminders.allOff.with(Salah.Asr, Reminder(ReminderMode.Silent, PreReminder.of(30)))
        val settings = PlannerSettings(place, ClockSettings(), reminders)
        val asr = computed(clock.dayTimes(date, place, settings.clock)).asr

        val between = planner.plan(asr.minusSeconds(10 * 60), settings).alarms
        assertTrue(between.none { it is AlarmSpec.PreReminderAlarm && it.prayerAt == asr }, "pre-reminder already passed")
        assertTrue(between.any { it is AlarmSpec.PrayerAlarm && it.at == asr }, "prayer stays")

        val farBefore = asr.minus(PLAN_WINDOW).minusSeconds(10 * 60)
        val outside = planner.plan(farBefore, settings).alarms
        assertTrue(outside.any { it is AlarmSpec.PreReminderAlarm && it.prayerAt == asr }, "pre-reminder inside the window")
        assertTrue(outside.none { it is AlarmSpec.PrayerAlarm && it.at == asr }, "prayer just beyond the window")
    }

    /** A prayer just after local midnight whose pre-reminder falls before it: the date range must reach past the window. */
    @Test
    fun a_pre_reminder_inside_the_window_is_found_when_its_prayer_is_on_the_next_local_date() {
        val place = Place(Latitude.of(60.0)!!, Longitude.of(10.0)!!, Meters.of(0.0)!!, ZoneId.of("Europe/Oslo"))
        val zone = place.zone
        val justAfterMidnight = PrayerClock { date, _, _ ->
            val start = date.atStartOfDay(zone).toInstant()
            fun at(minutes: Long) = start.plus(Duration.ofMinutes(minutes))
            ClockResult.Computed(DayTimes(at(20), at(30), at(60), at(300), at(600), at(900), at(1200)))
        }
        val settings = PlannerSettings(place, ClockSettings(), Reminders.allOff.with(Salah.Fajr, Reminder(ReminderMode.Adhan, PreReminder.of(120))))
        val windowEnd = LocalDateTime.of(2026, 6, 10, 23, 0).atZone(zone).toInstant()
        val plan = Planner(justAfterMidnight).plan(windowEnd.minus(PLAN_WINDOW), settings)
        val pre = plan.alarms.filterIsInstance<AlarmSpec.PreReminderAlarm>().single { it.prayerAt.localDate(zone) == LocalDate.of(2026, 6, 11) }
        assertEquals(LocalDateTime.of(2026, 6, 10, 22, 30), pre.at.atZone(zone).toLocalDateTime())
        assertTrue(plan.alarms.none { it is AlarmSpec.PrayerAlarm && it.prayerAt.localDate(zone) == LocalDate.of(2026, 6, 11) })
    }

    @Test
    fun ids_are_distinct_and_in_the_closed_space_across_random_plans() {
        for (scenario in scenarios(count = 1500, seed = 30)) {
            assertIdsDistinctAndInSpace(scenario.plan(), "$scenario")
        }
    }

    @Test
    fun ids_depend_only_on_date_salah_and_kind() {
        val zone = dhaka
        for (scenario in scenarios(count = 500, seed = 31)) {
            val later = scenario.now.plus(Duration.ofHours(3))
            for (now in listOf(scenario.now, later)) {
                for (alarm in planner.plan(now, scenario.settings).alarms) {
                    when (alarm) {
                        is AlarmSpec.PrayerAlarm -> assertEquals(AlarmId.prayer(alarm.at.localDate(zone), alarm.salah), alarm.id, "$alarm")
                        is AlarmSpec.PreReminderAlarm -> assertEquals(AlarmId.preReminder(alarm.prayerAt.localDate(zone), alarm.salah), alarm.id, "$alarm")
                        is AlarmSpec.Replan -> assertEquals(AlarmId.replan(alarm.at.localDate(zone)), alarm.id, "$alarm")
                    }
                }
            }
        }
    }

    @Test
    fun alarm_id_layout() {
        assertEquals(64, AlarmId.SPACE.count())
        assertEquals(AlarmId.SPACE.toList(), AlarmId.all.map { it.value })
        assertNull(AlarmId.of(-1))
        assertNull(AlarmId.of(64))
        assertNotNull(AlarmId.of(0))
        assertNotNull(AlarmId.of(63))

        val date = LocalDate.ofEpochDay(20_000) // 20000 mod 4 == 0
        assertEquals(0, AlarmId.prayer(date, Salah.Fajr).value)
        assertEquals(1, AlarmId.preReminder(date, Salah.Fajr).value)
        assertEquals(8, AlarmId.prayer(date, Salah.Isha).value)
        assertEquals(9, AlarmId.preReminder(date, Salah.Isha).value)
        assertEquals(15, AlarmId.replan(date).value)
        assertEquals(16 + 4, AlarmId.prayer(date.plusDays(1), Salah.Asr).value)
        assertEquals(AlarmId.replan(date), AlarmId.replan(date.plusDays(4)), "four day slots, then they repeat")
        assertEquals(3 * 16 + 15, AlarmId.replan(LocalDate.ofEpochDay(-1)).value, "negative epoch days use floorMod")

        val produced = (0L until 8L).flatMap { day ->
            Salah.entries.flatMap { listOf(AlarmId.prayer(date.plusDays(day), it), AlarmId.preReminder(date.plusDays(day), it)) } +
                AlarmId.replan(date.plusDays(day))
        }.map { it.value }.toSet()
        assertTrue(produced.none { it % 16 in 10..14 }, "slots 10..14 are never used")
        assertEquals(4 * 11, produced.size)
    }

    @Test
    fun replan_is_one_strictly_ahead_at_most_a_day_and_five_minutes_away_at_local_0005() {
        for (scenario in scenarios(count = 1500, seed = 32)) {
            val plan = scenario.plan()
            assertOneReplan(scenario.now, dhaka, plan, "$scenario")
            val replan = plan.alarms.filterIsInstance<AlarmSpec.Replan>().single()
            assertTrue(replan.at <= scenario.now.plus(Duration.ofHours(24)).plus(REPLAN_AFTER_MIDNIGHT), "at most 24h05m: $scenario")
        }
    }

    @Test
    fun replan_around_midnight_is_exact() {
        val settings = PlannerSettings(scenarios(count = 1, seed = 33).single().case.place, ClockSettings(), Reminders.allOff)
        fun replanFor(local: LocalDateTime) =
            planner.plan(local.atZone(dhaka).toInstant(), settings).alarms.single().at.atZone(dhaka).toLocalDateTime()
        val day = LocalDate.of(2026, 11, 26)
        assertEquals(day.atTime(0, 5), replanFor(day.atStartOfDay()))
        assertEquals(day.atTime(0, 5), replanFor(day.atTime(0, 4, 59, 999_999_999)))
        assertEquals(day.plusDays(1).atTime(0, 5), replanFor(day.atTime(0, 5)), "now == 00:05 is the one firing, so the next day's")
        assertEquals(day.plusDays(1).atTime(0, 5), replanFor(day.atTime(23, 59)))
    }

    @Test
    fun all_off_leaves_only_the_replan() {
        for (scenario in scenarios(count = 300, seed = 34)) {
            val plan = planner.plan(scenario.now, scenario.settings.copy(reminders = Reminders.allOff))
            assertEquals(1, plan.alarms.size, "$scenario")
            assertTrue(plan.alarms.single() is AlarmSpec.Replan)
        }
    }

    @Test
    fun an_off_prayer_has_neither_alarm_even_with_a_pre_reminder_set() {
        for (scenario in scenarios(count = 400, seed = 35)) {
            for (salah in Salah.entries) {
                val reminders = Reminders.all(ReminderMode.Adhan).with(salah, Reminder(ReminderMode.Off, PreReminder.of(15)))
                val alarms = planner.plan(scenario.now, scenario.settings.copy(reminders = reminders)).alarms
                assertTrue(alarms.none { (it is AlarmSpec.PrayerAlarm && it.salah == salah) || (it is AlarmSpec.PreReminderAlarm && it.salah == salah) }, "$salah: $scenario")
                assertTrue(alarms.any { it is AlarmSpec.PrayerAlarm && it.salah != salah }, "the others remain: $scenario")
            }
        }
    }

    @Test
    fun INV_T7_a_day_the_clock_cannot_compute_is_a_gap_with_no_alarms() {
        for (scenario in scenarios(count = 200, seed = 36)) {
            val zone = scenario.settings.place.zone
            val everything = scenario.settings.copy(reminders = Reminders.all(ReminderMode.Adhan).with(Salah.Isha, Reminder(ReminderMode.Silent, PreReminder.of(20))))
            val normal = planner.plan(scenario.now, everything)
            for (shift in 0L..2L) {
                val bad = scenario.now.localDate(zone).plusDays(shift)
                val failing = PrayerClock { date, place, settings ->
                    if (date == bad) ClockResult.NotComputable(Prayer.Fajr, Reason.SunNeverReachesAngle) else clock.dayTimes(date, place, settings)
                }
                val plan = Planner(failing).plan(scenario.now, everything)
                val where = "bad=$bad $scenario"
                assertEquals(listOf(Gap(bad, Prayer.Fajr, Reason.SunNeverReachesAngle)), plan.gaps, "one gap: $where")
                assertTrue(plan.alarms.none { it.prayerDate(zone) == bad }, "no alarm for the gap day: $where")
                assertEquals(normal.alarms.filter { it.prayerDate(zone) != bad }, plan.alarms, "other days unaffected, replan kept: $where")
                assertTrue(normal.gaps.isEmpty())
            }
        }
    }

    private val losAngeles = Place(Latitude.of(34.05)!!, Longitude.of(-118.24)!!, Meters.of(90.0)!!, ZoneId.of("America/Los_Angeles"))

    @Test
    fun los_angeles_across_both_dst_changes_is_complete_distinct_and_has_one_replan() {
        val random = Random(37)
        val weeks = listOf(LocalDate.of(2026, 3, 5), LocalDate.of(2026, 10, 29)) // spring forward Mar 8, fall back Nov 1
        for (start in weeks) {
            var now = start.atStartOfDay(losAngeles.zone).toInstant()
            val stop = start.plusDays(8).atStartOfDay(losAngeles.zone).toInstant()
            while (now < stop) {
                val settings = PlannerSettings(losAngeles, randomClockSettings(random), randomReminders(random))
                val plan = planner.plan(now, settings)
                val where = "now=$now ${settings.clock} ${settings.reminders}"
                assertComplete(now, settings, plan, where)
                assertIdsDistinctAndInSpace(plan, where)
                assertOneReplan(now, losAngeles.zone, plan, where)
                assertTrue(plan.gaps.isEmpty(), where)
                now = now.plusSeconds(5 * 3600 + 7 * 60)
            }
        }
    }

    /**
     * Distinctness needs the plan to touch at most 4 consecutive local dates. Checked where it is hardest: zones with
     * a daylight-saving change (the one-hour gap makes 48 h of real time 49 h of wall time), a 30-minute change, and
     * one with a date-line shift, with every alarm on, a 120-minute pre-reminder, and `now` swept through two years.
     * Gaps are allowed here (high-latitude zones can fail); the ids of what is planned must still be distinct.
     */
    @Test
    fun ids_stay_distinct_across_dst_changes_in_many_zones() {
        val zones = listOf(
            "America/Los_Angeles" to 34.05 to -118.24, "Europe/London" to 51.5 to -0.12, "Australia/Sydney" to -33.87 to 151.2,
            "Australia/Lord_Howe" to -31.55 to 159.08, "America/Santiago" to -33.45 to -70.67, "Pacific/Auckland" to -36.85 to 174.76,
            "Pacific/Kiritimati" to 1.87 to -157.4, "Asia/Kathmandu" to 27.7 to 85.3, "Asia/Dhaka" to 23.8 to 90.4,
        )
        val reminders = Reminders.all(ReminderMode.Adhan).let { all -> Salah.entries.fold(all) { r, s -> r.with(s, Reminder(ReminderMode.Adhan, PreReminder.of(120))) } }
        for ((zoneAndLatitude, longitude) in zones) {
            val (zoneId, latitude) = zoneAndLatitude
            val zone = ZoneId.of(zoneId)
            val place = Place(Latitude.of(latitude)!!, Longitude.of(longitude)!!, Meters.of(0.0)!!, zone)
            val settings = PlannerSettings(place, ClockSettings(), reminders)
            var now = LocalDate.of(2026, 1, 1).atTime(23, 30).atZone(zone).toInstant()
            val stop = LocalDate.of(2028, 1, 1).atStartOfDay(zone).toInstant()
            while (now < stop) {
                val plan = planner.plan(now, settings)
                val where = "zone=$zoneId now=$now"
                assertIdsDistinctAndInSpace(plan, where)
                val epochDays = plan.alarms.mapNotNull { it.prayerDate(zone)?.toEpochDay() }
                if (epochDays.isNotEmpty()) assertTrue(epochDays.max() - epochDays.min() <= 3, "at most 4 consecutive dates: $where")
                assertEquals(1, plan.alarms.count { it is AlarmSpec.Replan }, where)
                now = now.plusSeconds(7 * 3600 + 13 * 60)
            }
        }
    }

    @Test
    fun example_dhaka_plan_for_a_thursday_noon() {
        val place = Place(Latitude.of(23.8103)!!, Longitude.of(90.4125)!!, Meters.of(8.0)!!, dhaka)
        val reminders = Reminders.all(ReminderMode.Adhan).with(Salah.Fajr, Reminder(ReminderMode.Adhan, PreReminder.of(15)))
        val now = LocalDateTime.of(2026, 11, 26, 12, 0).atZone(dhaka).toInstant()
        val plan = planner.plan(now, PlannerSettings(place, ClockSettings(), reminders))
        println("Plan at $now (2026-11-26 12:00 Asia/Dhaka), window ${PLAN_WINDOW.toHours()} h")
        for (alarm in plan.alarms) {
            val kind = when (alarm) {
                is AlarmSpec.PrayerAlarm -> "${alarm.salah} ${alarm.mode}"
                is AlarmSpec.PreReminderAlarm -> "${alarm.salah} pre-reminder"
                is AlarmSpec.Replan -> "Replan"
            }
            println("  id=${alarm.id.value.toString().padStart(2)}  ${alarm.at.atZone(dhaka).toLocalDateTime()}  $kind")
        }
        println("  gaps=${plan.gaps}")
        val first = plan.alarms.first()
        assertTrue(first is AlarmSpec.PrayerAlarm && first.salah == Salah.Asr && first.at.localDate(dhaka) == LocalDate.of(2026, 11, 26), "$first")
        assertEquals(emptyList(), plan.gaps)
    }
}

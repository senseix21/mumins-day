package com.senseix21.muminsday.widget

import com.senseix21.muminsday.clock.ClockResult
import com.senseix21.muminsday.clock.ClockSettings
import com.senseix21.muminsday.clock.DayTimes
import com.senseix21.muminsday.clock.Latitude
import com.senseix21.muminsday.clock.Longitude
import com.senseix21.muminsday.clock.Meters
import com.senseix21.muminsday.clock.Place
import com.senseix21.muminsday.clock.Prayer
import com.senseix21.muminsday.clock.PrayerClock
import com.senseix21.muminsday.clock.Reason
import com.senseix21.muminsday.health.BrandSteps
import com.senseix21.muminsday.health.Health
import com.senseix21.muminsday.health.HealthFacts
import com.senseix21.muminsday.health.assessHealth
import com.senseix21.muminsday.planner.Salah
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WidgetModelTest {

    private val zone = ZoneId.of("Asia/Dhaka")
    private val place = Place(Latitude.of(23.8)!!, Longitude.of(90.4)!!, Meters.of(0.0)!!, zone)
    private val settings = ClockSettings()
    private val day = LocalDate.of(2026, 11, 27)

    private fun local(date: LocalDate, hour: Int) = date.atTime(hour, 0).atZone(zone).toInstant()

    /** Fake clock: Fajr 05:00, Dhuhr 12, Asr 15, Maghrib 17, Isha 19, on every date except [broken]. */
    private fun clock(vararg broken: LocalDate) = PrayerClock { date, _, _ ->
        if (date in broken) {
            ClockResult.NotComputable(Prayer.Fajr, Reason.SunNeverReachesAngle)
        } else {
            ClockResult.Computed(
                DayTimes(
                    sehriEnd = local(date, 4), fajr = local(date, 5), sunrise = local(date, 6), dhuhr = local(date, 12),
                    asr = local(date, 15), maghrib = local(date, 17), isha = local(date, 19),
                ),
            )
        }
    }

    private val armed: Health = Health.Armed
    private val degraded: Health = assessHealth(
        Instant.parse("2026-11-27T00:00:00Z"),
        HealthFacts(
            exactAlarmsAllowed = false,
            notificationsAllowed = true,
            exemptFromBatteryOptimisation = true,
            brandSteps = BrandSteps.NotNeeded,
            alarmLog = emptyList(),
        ),
    )

    @Test
    fun `the next prayer is named with its instant`() {
        val model = nextPrayerModel(clock(), local(day, 13), place, settings, armed)
        assertEquals(NextPrayerModel.Found(Salah.Asr, local(day, 15), remindersDegraded = false), model)
    }

    @Test
    fun `after Isha the next prayer is tomorrow's Fajr`() {
        val model = nextPrayerModel(clock(), local(day, 21), place, settings, armed)
        assertEquals(NextPrayerModel.Found(Salah.Fajr, local(day.plusDays(1), 5), remindersDegraded = false), model)
    }

    @Test
    fun `a day that cannot be computed gives Unknown and never a guess`() {
        val model = nextPrayerModel(clock(day, day.plusDays(1)), local(day, 13), place, settings, armed)
        assertEquals(NextPrayerModel.Unknown(remindersDegraded = false), model)
    }

    @Test
    fun `degraded health is carried into both models in words-ready form`() {
        assertIs<Health.Degraded>(degraded)
        assertTrue(nextPrayerModel(clock(), local(day, 13), place, settings, degraded).remindersDegraded)
        assertTrue(nextPrayerModel(clock(day, day.plusDays(1)), local(day, 13), place, settings, degraded).remindersDegraded)
        assertTrue(fiveTimesModel(clock(), local(day, 13), place, settings, degraded).remindersDegraded)
    }

    @Test
    fun `the five-times model lists the five in order with today's instants`() {
        val model = fiveTimesModel(clock(), local(day, 13), place, settings, armed)
        assertEquals(Salah.entries, model.rows.map { it.salah })
        assertEquals(listOf(5, 12, 15, 17, 19).map { local(day, it) }, model.rows.map { it.at })
    }

    @Test
    fun `the five-times model follows the local date, not the UTC date`() {
        // 2026-11-27 20:00 UTC is 02:00 on the 28th in Dhaka: the rows are the 28th's.
        val model = fiveTimesModel(clock(), Instant.parse("2026-11-27T20:00:00Z"), place, settings, armed)
        assertEquals(local(day.plusDays(1), 5), model.rows.first().at)
    }

    @Test
    fun `a day that cannot be computed gives five rows with no time`() {
        val model = fiveTimesModel(clock(day), local(day, 13), place, settings, armed)
        assertEquals(5, model.rows.size)
        model.rows.forEach { assertNull(it.at) }
    }
}

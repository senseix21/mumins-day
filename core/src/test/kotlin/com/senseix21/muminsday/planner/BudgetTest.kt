package com.senseix21.muminsday.planner

import com.senseix21.muminsday.clock.ClockResult
import com.senseix21.muminsday.clock.ClockSettings
import com.senseix21.muminsday.clock.Latitude
import com.senseix21.muminsday.clock.Longitude
import com.senseix21.muminsday.clock.Meters
import com.senseix21.muminsday.clock.Place
import com.senseix21.muminsday.clock.clock
import com.senseix21.muminsday.clock.dhaka
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Time budgets from PLAN phase 1 and 2: a year of daily times under 100 ms and one plan under 50 ms (both targets).
 * The bounds asserted here are deliberately loose (20x the targets) so a slow or busy machine does not make the build
 * flaky; the real numbers are printed. Each measurement is the best of several runs after a warm-up, because the first
 * calls pay for JIT compilation and class loading, which a phone pays once per process too but is not the steady state.
 * The 100 ms target is for the minimum phone and is measured there in phase 7; this JVM number is a smoke test only.
 */
class BudgetTest {
    private val dhakaPlace = Place(Latitude.of(23.81)!!, Longitude.of(90.41)!!, Meters.of(10.0)!!, dhaka)

    private fun bestOfMillis(runs: Int, work: () -> Unit): Double {
        repeat(3) { work() }
        return (1..runs).minOf {
            val start = System.nanoTime()
            work()
            (System.nanoTime() - start) / 1_000_000.0
        }
    }

    @Test
    fun `a year of daily times for one place`() {
        val settings = ClockSettings()
        val firstDay = LocalDate.of(2027, 1, 1)
        var computed = 0
        val millis = bestOfMillis(10) {
            computed = (0 until 365).count { clock.dayTimes(firstDay.plusDays(it.toLong()), dhakaPlace, settings) is ClockResult.Computed }
        }
        println("BUDGET year of times (365 days, Dhaka): %.2f ms best of 10 after warm-up (target 100 ms)".format(millis))
        assertEquals(365, computed)
        assertTrue(millis < 2_000.0, "a year of times took $millis ms; the target is 100 ms, the loose bound 2000 ms")
    }

    @Test
    fun `one plan`() {
        val planner = Planner(clock)
        val settings = PlannerSettings(dhakaPlace, ClockSettings(), Reminders.all(ReminderMode.Adhan))
        val now = Instant.parse("2027-03-10T06:00:00Z")
        var alarms = 0
        val millis = bestOfMillis(10) { alarms = planner.plan(now, settings).alarms.size }
        println("BUDGET one plan (48 h, five Adhans, Dhaka): %.2f ms best of 10 after warm-up, %d alarms (target 50 ms)".format(millis, alarms))
        assertTrue(alarms > 0)
        assertTrue(millis < 1_000.0, "one plan took $millis ms; the target is 50 ms, the loose bound 1000 ms")
    }
}

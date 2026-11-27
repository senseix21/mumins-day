package com.senseix21.muminsday.bearing

import io.kotest.property.Arb
import io.kotest.property.RandomSource
import io.kotest.property.arbitrary.double
import java.time.Duration
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CompassTest {
    private val qibla = Bearing.ofOrNull(277.6)!!
    private fun field(v: Double) = MicroTesla.of(v)
    private fun facts(
        magnetometer: Boolean = true,
        accuracy: SensorAccuracy = SensorAccuracy.High,
        field: MicroTesla? = field(44.0),
        declination: Degrees? = Degrees.of(0.5),
    ) = CompassFacts(magnetometer, accuracy, field, declination, qibla)

    private fun reasons(f: CompassFacts) = assertIs<CompassState.Untrusted>(compassState(f)).reasons

    @Test
    fun good_facts_are_trusted_and_the_target_is_corrected_for_declination() {
        val state = assertIs<CompassState.Trusted>(compassState(facts()))
        assertEquals(277.1, state.magneticTarget.degrees, 1e-9)
    }

    @Test
    fun T_INST_12_core_less_than_high_accuracy_is_untrusted_at_every_lower_level() {
        for (level in listOf(SensorAccuracy.Unreliable, SensorAccuracy.Low, SensorAccuracy.Medium)) {
            assertEquals(listOf(UntrustedReason.LowAccuracy), reasons(facts(accuracy = level)), "$level")
        }
    }

    @Test
    fun the_field_limits_are_inclusive_and_outside_them_warns() {
        assertIs<CompassState.Trusted>(compassState(facts(field = field(EARTH_FIELD_MIN_MICROTESLA))))
        assertIs<CompassState.Trusted>(compassState(facts(field = field(EARTH_FIELD_MAX_MICROTESLA))))
        assertEquals(listOf(UntrustedReason.FieldTooWeak), reasons(facts(field = field(29.99))))
        assertEquals(listOf(UntrustedReason.FieldTooStrong), reasons(facts(field = field(60.01))))
    }

    @Test
    fun no_reading_yet_is_untrusted_and_no_declination_draws_no_needle() {
        assertEquals(listOf(UntrustedReason.NoReading), reasons(facts(field = null)))
        val state = assertIs<CompassState.Untrusted>(compassState(facts(declination = null)))
        assertEquals(listOf(UntrustedReason.NoDeclination), state.reasons)
        assertNull(state.magneticTarget)
    }

    @Test
    fun reasons_add_up_and_a_greyed_needle_still_has_its_target() {
        val state = assertIs<CompassState.Untrusted>(compassState(facts(accuracy = SensorAccuracy.Low, field = field(120.0))))
        assertEquals(listOf(UntrustedReason.LowAccuracy, UntrustedReason.FieldTooStrong), state.reasons)
        assertEquals(277.1, state.magneticTarget!!.degrees, 1e-9)
    }

    @Test
    fun T_INST_13_core_no_magnetometer_is_no_sensor_whatever_else_is_claimed() {
        assertEquals(CompassState.NoSensor, compassState(facts(magnetometer = false)))
        assertEquals(CompassState.NoSensor, compassState(facts(magnetometer = false, accuracy = SensorAccuracy.Unreliable, field = null, declination = null)))
    }

    /** INV-Q2 as a property: over random facts, Trusted happens only when every condition holds, and Untrusted is never empty. */
    @Test
    fun property_trusted_only_when_everything_is_good() {
        val rs = RandomSource.seeded(61)
        repeat(5_000) {
            val accuracy = SensorAccuracy.entries[(Arb.double(0.0, 3.999).sample(rs).value).toInt()]
            val strength = if (Arb.double(0.0, 1.0).sample(rs).value < 0.1) null else field(Arb.double(0.0, 120.0).sample(rs).value)
            val declination = if (Arb.double(0.0, 1.0).sample(rs).value < 0.1) null else Degrees.of(Arb.double(-30.0, 30.0).sample(rs).value)
            val f = CompassFacts(true, accuracy, strength, declination, Bearing.ofOrNull(Arb.double(0.0, 359.9).sample(rs).value)!!)
            val expectTrusted = accuracy == SensorAccuracy.High && strength != null &&
                strength.value in EARTH_FIELD_MIN_MICROTESLA..EARTH_FIELD_MAX_MICROTESLA && declination != null
            when (val state = compassState(f)) {
                is CompassState.Trusted -> assertTrue(expectTrusted, "trusted on $f")
                is CompassState.Untrusted -> {
                    assertTrue(!expectTrusted, "untrusted on $f")
                    assertTrue(state.reasons.isNotEmpty())
                    assertEquals(declination == null, state.magneticTarget == null)
                }
                CompassState.NoSensor -> error("has a magnetometer")
            }
        }
    }

    @Test
    fun lay_flat_prompt_at_the_limit() {
        val flat = Degrees.of(0.0)!!
        assertTrue(!shouldLayFlat(Degrees.of(25.0)!!, flat))
        assertTrue(shouldLayFlat(Degrees.of(-25.1)!!, flat))
        assertTrue(shouldLayFlat(flat, Degrees.of(60.0)!!))
    }

    @Test
    fun needle_angle_is_target_minus_heading_wrapped() {
        assertEquals(10.0, needleAngle(Bearing.ofOrNull(350.0)!!, Bearing.ofOrNull(0.0)!!).degrees, 1e-9)
        assertEquals(350.0, needleAngle(Bearing.ofOrNull(10.0)!!, Bearing.ofOrNull(0.0)!!).degrees, 1e-9)
    }

    @Test
    fun degrees_and_microtesla_reject_nonsense() {
        assertNull(Degrees.of(Double.NaN)); assertNull(MicroTesla.of(-1.0)); assertNull(MicroTesla.of(Double.POSITIVE_INFINITY))
    }
}

class HeadingFilterTest {
    private fun at(heading: Double, millis: Long) = HeadingSample(Bearing.ofOrNull(heading)!!, Duration.ofMillis(millis))
    private fun apart(a: Double, b: Double): Double = abs((a - b + 540.0).mod(360.0) - 180.0)

    @Test
    fun the_first_sample_is_taken_as_it_is() {
        assertEquals(at(123.0, 5), smoothHeading(null, at(123.0, 5)))
    }

    @Test
    fun across_north_it_goes_through_zero_and_not_through_south() {
        val out = smoothHeading(at(359.0, 0), at(1.0, 200)) // dt equals the time constant, so half way
        assertEquals(0.0, apart(out.heading.degrees, 0.0), 1e-9)
    }

    @Test
    fun an_old_or_simultaneous_sample_changes_nothing() {
        val previous = at(90.0, 1000)
        assertEquals(previous, smoothHeading(previous, at(200.0, 1000)))
        assertEquals(previous, smoothHeading(previous, at(200.0, 900)))
    }

    @Test
    fun property_stays_in_range_and_moves_along_the_short_arc_toward_the_sample() {
        val rs = RandomSource.seeded(62)
        repeat(10_000) {
            val from = at(Arb.double(0.0, 359.999).sample(rs).value, 0)
            val to = at(Arb.double(0.0, 359.999).sample(rs).value, 1 + (Arb.double(0.0, 5_000.0).sample(rs).value).toLong())
            val out = smoothHeading(from, to).heading.degrees
            assertTrue(out >= 0.0 && out < 360.0, "range: $out")
            val before = apart(from.heading.degrees, to.heading.degrees)
            // Never further from the sample, and the two legs add up to the whole short arc: it did not go the long way.
            assertTrue(apart(out, to.heading.degrees) <= before + 1e-9, "closer")
            assertEquals(before, apart(from.heading.degrees, out) + apart(out, to.heading.degrees), 1e-6, "on the short arc")
        }
    }

    @Test
    fun property_repeating_a_sample_converges_to_it_across_the_wrap() {
        val rs = RandomSource.seeded(63)
        repeat(200) {
            val target = Arb.double(0.0, 359.999).sample(rs).value
            var state = at((target + 170.0).mod(360.0), 0)
            val sample = at(target, 0)
            var last = apart(state.heading.degrees, target)
            for (step in 1..60) {
                state = smoothHeading(state, sample.copy(at = Duration.ofMillis(step * 50L)))
                val now = apart(state.heading.degrees, target)
                assertTrue(now <= last + 1e-9, "monotone")
                last = now
            }
            assertTrue(last < 1.0, "converged: $last")
        }
    }

    @Test
    fun a_long_gap_lands_on_the_sample() {
        val out = smoothHeading(at(10.0, 0), at(250.0, 600_000))
        assertEquals(0.0, apart(out.heading.degrees, 250.0), 0.1)
    }
}

package com.senseix21.muminsday.clock

import io.kotest.property.Arb
import io.kotest.property.RandomSource
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.int
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import java.util.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

class CalcTimesClockTest {

    private fun times(case: Case, settings: ClockSettings = ClockSettings()): DayTimes =
        computed(clock.dayTimes(case.date, case.place, settings))

    private fun describe(case: Case) = "date=${case.date} place=${case.place}"

    @Test
    fun T_PROP_1_order_and_local_date() {
        for (asr in AsrConvention.entries) {
            for (case in cases(count = 1500, seed = 1)) {
                val t = times(case, ClockSettings(asr = asr))
                val where = "${describe(case)} asr=$asr"
                assertTrue(t.sehriEnd <= t.fajr, "sehriEnd <= fajr: $where")
                assertTrue(t.fajr < t.sunrise, "fajr < sunrise: $where")
                assertTrue(t.sunrise < t.dhuhr, "sunrise < dhuhr: $where")
                assertTrue(t.dhuhr < t.asr, "dhuhr < asr: $where")
                assertTrue(t.asr < t.maghrib, "asr < maghrib: $where")
                assertTrue(t.maghrib < t.isha, "maghrib < isha: $where")
                for (prayer in Prayer.entries) {
                    assertEquals(case.date, t[prayer].atZone(dhaka).toLocalDate(), "$prayer local date: $where")
                }
            }
        }
    }

    /**
     * Day attribution must hold in any zone, not only UTC+6: the planner will rely on it for a phone whose zone is
     * not the place's (INV-S2). Cities on both sides of UTC and of the equator, every day of two years, so every
     * daylight-saving change is included.
     */
    @Test
    fun T_PROP_1_order_and_local_date_hold_in_other_zones() {
        val cities = listOf(
            Triple(34.05, -118.24, "America/Los_Angeles"),
            Triple(21.31, -157.86, "Pacific/Honolulu"),
            Triple(-23.55, -46.63, "America/Sao_Paulo"),
            Triple(-1.29, 36.82, "Africa/Nairobi"),
            Triple(35.68, 139.69, "Asia/Tokyo"),
            Triple(-36.85, 174.76, "Pacific/Auckland"),
        )
        for ((latitude, longitude, zoneId) in cities) {
            val zone = ZoneId.of(zoneId)
            val place = Place(Latitude.of(latitude)!!, Longitude.of(longitude)!!, Meters.of(0.0)!!, zone)
            var date = LocalDate.of(2026, 1, 1)
            while (date.year < 2028) {
                val t = computed(clock.dayTimes(date, place, ClockSettings()))
                val where = "date=$date zone=$zoneId"
                val inOrder = Prayer.entries.map { t[it] }
                assertEquals(inOrder.sorted(), inOrder, "order: $where")
                for (prayer in Prayer.entries) {
                    assertEquals(date, t[prayer].atZone(zone).toLocalDate(), "$prayer local date: $where")
                }
                date = date.plusDays(1)
            }
        }
    }

    @Test
    fun T_PROP_1_shafi_asr_is_before_hanafi_asr() {
        for (case in cases(count = 1500, seed = 2)) {
            val shafi = times(case, ClockSettings(asr = AsrConvention.Shafi)).asr
            val hanafi = times(case, ClockSettings(asr = AsrConvention.Hanafi)).asr
            assertTrue(shafi < hanafi, "shafi < hanafi: ${describe(case)}")
        }
    }

    @Test
    fun T_UNIT_2_iftar_is_maghrib_and_sehri_margin() {
        for (case in cases(count = 500, seed = 3)) {
            val base = times(case)
            assertEquals(base.maghrib, base.iftar)
            for (margin in listOf(0, 5, 10)) {
                val t = times(case, ClockSettings(sehriMargin = Minutes(margin)))
                // Sehri end is floor(raw fajr - margin) and fajr is ceil(raw fajr). For a raw value strictly between
                // two minutes ceil = floor + 1, so the gap is margin + 1; for an exact minute it is margin.
                val extra = Duration.between(t.sehriEnd, t.fajr).toMinutes() - margin
                assertTrue(extra == 0L || extra == 1L, "fajr - sehriEnd - margin = $extra: ${describe(case)} margin=$margin")
                // Margin must not move Fajr itself.
                assertEquals(base.fajr, t.fajr)
            }
            assertEquals(base.sehriEnd, times(case, ClockSettings(sehriMargin = Minutes(0))).sehriEnd)
        }
    }

    @Test
    fun T_UNIT_2_fajr_offset_does_not_move_sehri_end() {
        val case = cases(1, seed = 4).single()
        val base = times(case)
        val moved = times(case, ClockSettings(offsets = Offsets.none.with(Prayer.Fajr, Minutes(7))))
        assertEquals(base.sehriEnd, moved.sehriEnd)
        assertEquals(base.fajr.plusSeconds(7 * 60), moved.fajr)
    }

    @Test
    fun T_PROP_3_determinism_across_default_zone_and_locale() {
        val savedZone = TimeZone.getDefault()
        val savedLocale = Locale.getDefault()
        try {
            val sample = cases(count = 150, seed = 5)
            val expected = sample.map { clock.dayTimes(it.date, it.place, ClockSettings()) }
            val zones = listOf("UTC", "America/New_York", "Asia/Dhaka", "Pacific/Kiritimati")
            val locales = listOf("bn-BD", "ar-SA", "tr-TR", "en-US").map(Locale::forLanguageTag)
            for (zone in zones) for (locale in locales) {
                TimeZone.setDefault(TimeZone.getTimeZone(zone))
                Locale.setDefault(locale)
                val actual = sample.map { clock.dayTimes(it.date, it.place, ClockSettings()) }
                assertEquals(expected, actual, "default zone=$zone locale=$locale")
                // Explicit zone-stability assertion: the instants are the same whatever the JVM default zone.
                assertEquals(
                    expected.map { computed(it).fajr },
                    actual.map { computed(it).fajr },
                    "fajr instants under default zone $zone",
                )
            }
        } finally {
            TimeZone.setDefault(savedZone)
            Locale.setDefault(savedLocale)
        }
    }

    @Test
    fun T_UNIT_5_rounding_down() {
        fun down(hours: Double) = roundToWholeMinutes(hours, Rounding.Down)
        val minute = 1.0 / 60.0
        val second = 1.0 / 3600.0
        assertEquals(300, down(5.0))
        assertEquals(307, down(5.0 + 7 * minute))
        assertEquals(307, down(5.0 + 7 * minute + second))
        assertEquals(306, down(5.0 + 7 * minute - second))
        // Whole minute minus noise must stay on that minute, not drop one.
        assertEquals(307, down(5.0 + 7 * minute - 1e-13))
        assertEquals(307, down(5.0 + 7 * minute + 1e-13))
        assertEquals(0, down(0.0))
    }

    @Test
    fun T_UNIT_5_rounding_up() {
        fun up(hours: Double) = roundToWholeMinutes(hours, Rounding.Up)
        val minute = 1.0 / 60.0
        val second = 1.0 / 3600.0
        assertEquals(300, up(5.0))
        assertEquals(307, up(5.0 + 7 * minute))
        assertEquals(308, up(5.0 + 7 * minute + second))
        assertEquals(307, up(5.0 + 7 * minute - second))
        // The epsilon case: a whole minute plus noise must not be pushed to the next minute.
        assertEquals(307, up(5.0 + 7 * minute + 1e-13))
        assertEquals(307, up(5.0 + 7 * minute - 1e-13))
        assertEquals(1440, up(24.0))
    }

    @Test
    fun T_PROP_6_offset_linearity() {
        val rs = RandomSource.seeded(6)
        val prayers = Arb.element(Prayer.entries)
        val shifts = Arb.int(-30..30)
        for (case in cases(count = 1500, seed = 7)) {
            val prayer = prayers.sample(rs).value
            val shift = shifts.sample(rs).value
            val asr = Arb.element(AsrConvention.entries).sample(rs).value
            val base = times(case, ClockSettings(asr = asr))
            val moved = times(case, ClockSettings(asr = asr, offsets = Offsets.none.with(prayer, Minutes(shift))))
            val where = "${describe(case)} prayer=$prayer shift=$shift asr=$asr"
            for (other in Prayer.entries) {
                val expected = if (other == prayer) base[other].plusSeconds(shift * 60L) else base[other]
                assertEquals(expected, moved[other], "$other: $where")
            }
        }
    }

    @Test
    fun T_UNIT_11_polar_summer_is_not_computable() {
        val place = Place(Latitude.of(65.0)!!, Longitude.of(25.0)!!, Meters.of(0.0)!!, ZoneId.of("UTC"))
        val result = clock.dayTimes(LocalDate.of(2026, 6, 21), place, ClockSettings())
        assertEquals(ClockResult.NotComputable(Prayer.SehriEnd, Reason.SunNeverReachesAngle), result)
    }

    @Test
    fun continuity_day_to_day_moves_at_most_three_minutes() {
        fun minuteOfDay(instant: Instant) = instant.atZone(dhaka).toLocalTime().toSecondOfDay() / 60
        for (case in cases(count = 60, seed = 8)) {
            var previous = times(case)
            for (offsetDays in 1L..60L) {
                val next = computed(clock.dayTimes(case.date.plusDays(offsetDays), case.place, ClockSettings()))
                for (prayer in Prayer.entries) {
                    val moved = kotlin.math.abs(minuteOfDay(next[prayer]) - minuteOfDay(previous[prayer]))
                    if (moved > 3) fail("$prayer moved $moved min on ${case.date.plusDays(offsetDays)}: ${describe(case)}")
                }
                previous = next
            }
        }
    }

    @Test
    fun dhaka_sanity_not_a_reference_timetable_check() {
        // Wide plausible windows only. This is NOT a comparison with a reference timetable (INV-T4 / T-FIX-4 is
        // separate and needs the agreed source, Q2).
        val dhakaPlace = Place(Latitude.of(23.8103)!!, Longitude.of(90.4125)!!, Meters.of(8.0)!!, dhaka)
        val t = computed(clock.dayTimes(LocalDate.of(2026, 11, 26), dhakaPlace, ClockSettings()))
        for (prayer in Prayer.entries) println("Dhaka 2026-11-26 $prayer ${t[prayer].atZone(dhaka).toLocalTime()}")
        fun local(prayer: Prayer) = t[prayer].atZone(dhaka).toLocalTime().toSecondOfDay() / 60.0 / 60.0
        assertTrue(local(Prayer.SehriEnd) in 4.5..5.5)
        assertTrue(local(Prayer.Fajr) in 4.5..5.5)
        assertTrue(local(Prayer.Sunrise) in 6.0..6.5)
        assertTrue(local(Prayer.Dhuhr) in 11.5..12.0)
        assertTrue(local(Prayer.Asr) in 14.5..16.0) // Hanafi (the default) is the later of the two Asr times
        assertTrue(local(Prayer.Maghrib) in 17.0..17.5)
        assertTrue(local(Prayer.Isha) in 18.0..19.0)
        assertFalse(t.sehriEnd > t.fajr)
    }
}

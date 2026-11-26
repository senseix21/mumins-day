package com.senseix21.muminsday.bearing

import com.senseix21.muminsday.clock.Latitude
import com.senseix21.muminsday.clock.Longitude
import io.kotest.property.Arb
import io.kotest.property.RandomSource
import io.kotest.property.arbitrary.double
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class QiblaTest {

    private fun bearing(latitude: Double, longitude: Double): Double =
        assertIs<QiblaDirection.Toward>(qiblaDirection(Latitude.of(latitude)!!, Longitude.of(longitude)!!)).bearing.degrees

    /** Smallest angle between two bearings, so 359.9 and 0.1 are 0.2 apart. */
    private fun apart(a: Double, b: Double): Double = abs((a - b + 540.0).mod(360.0) - 180.0)

    /**
     * T-UNIT-10 (INV-Q1). Expected values are WGS84 geodesic azimuths from geographiclib 2.x
     * (`Geodesic.WGS84.Inverse(lat, lon, 21.42247, 39.826198)['azi1']`, run 2026-11-26): a different model
     * (ellipsoid) and a different implementation from the code under test. City coordinates are approximate centres
     * and only inputs here; the official district table is Q4.
     */
    @Test
    fun T_UNIT_10_eight_divisional_cities_against_an_independent_geodesic() {
        val cities = listOf(
            Triple("Dhaka", 23.8103 to 90.4125, 277.5870),
            Triple("Chattogram", 22.3569 to 91.7832, 279.2734),
            Triple("Khulna", 22.8456 to 89.5403, 278.2576),
            Triple("Rajshahi", 24.3745 to 88.6042, 276.5031),
            Triple("Sylhet", 24.8949 to 91.8687, 277.0384),
            Triple("Barishal", 22.7010 to 90.3535, 278.6033),
            Triple("Rangpur", 25.7439 to 89.2752, 275.3735),
            Triple("Mymensingh", 24.7471 to 90.4203, 276.7123),
        )
        for ((name, at, expected) in cities) {
            val actual = bearing(at.first, at.second)
            assertTrue(apart(actual, expected) < 0.05, "$name: got $actual, geodesic says $expected")
        }
    }

    /**
     * The same bearing derived another way: put both points on the unit sphere as vectors and measure the Kaaba's
     * direction against the local north and east axes. Catches a slip in the trigonometric formula.
     */
    @Test
    fun T_UNIT_10_agrees_with_a_vector_derivation_anywhere_on_earth() {
        val rs = RandomSource.seeded(21)
        val latitudes = Arb.double(-89.0, 89.0)
        val longitudes = Arb.double(-180.0, 180.0)
        repeat(5_000) {
            val latitude = latitudes.sample(rs).value
            val longitude = longitudes.sample(rs).value
            val direction = qiblaDirection(Latitude.of(latitude)!!, Longitude.of(longitude)!!)
            if (direction !is QiblaDirection.Toward) return@repeat

            val (phi, lambda) = Math.toRadians(latitude) to Math.toRadians(longitude)
            val (kPhi, kLambda) = Math.toRadians(KAABA_LATITUDE) to Math.toRadians(KAABA_LONGITUDE)
            val kaaba = doubleArrayOf(cos(kPhi) * cos(kLambda), cos(kPhi) * sin(kLambda), sin(kPhi))
            val north = doubleArrayOf(-sin(phi) * cos(lambda), -sin(phi) * sin(lambda), cos(phi))
            val east = doubleArrayOf(-sin(lambda), cos(lambda), 0.0)
            fun dot(a: DoubleArray, b: DoubleArray) = a[0] * b[0] + a[1] * b[1] + a[2] * b[2]
            val expected = Math.toDegrees(atan2(dot(kaaba, east), dot(kaaba, north))).mod(360.0)

            assertTrue(apart(direction.bearing.degrees, expected) < 1e-6, "lat=$latitude lon=$longitude")
        }
    }

    @Test
    fun INV_Q1_known_directions() {
        // Due north of the Kaaba faces south; due south faces north; on its parallel just east, close to west.
        assertTrue(apart(bearing(60.0, KAABA_LONGITUDE), 180.0) < 1e-9)
        assertTrue(apart(bearing(-30.0, KAABA_LONGITUDE), 0.0) < 1e-9)
        assertTrue(apart(bearing(KAABA_LATITUDE, KAABA_LONGITUDE + 0.01), 270.0) < 0.01)
        // All of Bangladesh faces a little north of west.
        val rs = RandomSource.seeded(22)
        repeat(2_000) {
            val degrees = bearing(Arb.double(20.5, 26.7).sample(rs).value, Arb.double(88.0, 92.7).sample(rs).value)
            assertTrue(degrees in 273.0..282.0, "bearing $degrees")
        }
    }

    @Test
    fun INV_Q1_at_the_kaaba_and_opposite_it_there_is_no_single_direction() {
        val everywhere = QiblaDirection.Everywhere
        assertEquals(everywhere, qiblaDirection(Latitude.of(KAABA_LATITUDE)!!, Longitude.of(KAABA_LONGITUDE)!!))
        assertEquals(everywhere, qiblaDirection(Latitude.of(-KAABA_LATITUDE)!!, Longitude.of(KAABA_LONGITUDE - 180.0)!!))
        // A hundred metres away there is one again.
        assertIs<QiblaDirection.Toward>(qiblaDirection(Latitude.of(KAABA_LATITUDE + 0.001)!!, Longitude.of(KAABA_LONGITUDE)!!))
    }

    /**
     * F3 failure mode 4: with no location the screen uses the stored district. Measured here: anywhere in the
     * country, moving 50 km changes the bearing by under half a degree, well inside the 1-degree target.
     */
    @Test
    fun F3_bearing_moves_less_than_half_a_degree_over_50_km_in_bangladesh() {
        var largest = 0.0
        var latitude = 20.5
        while (latitude <= 26.7) {
            var longitude = 88.0
            while (longitude <= 92.7) {
                val here = bearing(latitude, longitude)
                val kmPerDegreeEast = 111.2 * cos(Math.toRadians(latitude))
                for ((north, east) in listOf(50.0 to 0.0, -50.0 to 0.0, 0.0 to 50.0, 0.0 to -50.0, 35.36 to 35.36, 35.36 to -35.36)) {
                    val there = bearing(latitude + north / 111.2, longitude + east / kmPerDegreeEast)
                    largest = maxOf(largest, apart(here, there))
                }
                longitude += 0.1
            }
            latitude += 0.1
        }
        println("largest bearing change over 50 km in Bangladesh: %.3f degrees".format(largest))
        assertTrue(largest < 0.5, "largest change $largest")
    }

    @Test
    fun bearing_is_always_in_the_half_open_range() {
        assertEquals(0.0, Bearing.ofOrNull(360.0)!!.degrees)
        assertEquals(350.0, Bearing.ofOrNull(-10.0)!!.degrees)
        assertEquals(10.0, Bearing.ofOrNull(730.0)!!.degrees)
        assertEquals(0.0, Bearing.ofOrNull(-1e-17)!!.degrees)
        assertNull(Bearing.ofOrNull(Double.NaN))
        assertNull(Bearing.ofOrNull(Double.POSITIVE_INFINITY))
        val rs = RandomSource.seeded(23)
        repeat(5_000) {
            val degrees = Bearing.ofOrNull(Arb.double(-1e6, 1e6).sample(rs).value)!!.degrees
            assertTrue(degrees >= 0.0 && degrees < 360.0, "$degrees")
        }
    }

    @Test
    fun INV_Q1_magnetic_bearing_subtracts_east_declination() {
        val qibla = Bearing.ofOrNull(277.6)!!
        assertTrue(apart(magneticBearing(qibla, 0.0).degrees, 277.6) < 1e-9)
        // Magnetic north 2 degrees east of true: the compass must read 2 less.
        assertTrue(apart(magneticBearing(qibla, 2.0).degrees, 275.6) < 1e-9)
        assertTrue(apart(magneticBearing(qibla, -0.5).degrees, 278.1) < 1e-9)
        assertTrue(apart(magneticBearing(Bearing.ofOrNull(1.0)!!, 3.0).degrees, 358.0) < 1e-9)
    }

    @Test
    fun compass_point_names_the_nearest_cardinal_and_the_offset() {
        fun point(degrees: Double) = compassPoint(Bearing.ofOrNull(degrees)!!)
        assertEquals(CompassPoint.Exactly(Cardinal.North), point(0.0))
        assertEquals(CompassPoint.Exactly(Cardinal.West), point(270.0))
        assertEquals(CompassPoint.Exactly(Cardinal.West), point(270.4))
        assertEquals(CompassPoint.Exactly(Cardinal.North), point(359.6))
        // Dhaka: the spec's own example, "8 degrees north of west".
        assertEquals(CompassPoint.Between(Cardinal.West, 8, Cardinal.North), point(277.587))
        assertEquals(CompassPoint.Between(Cardinal.West, 10, Cardinal.South), point(260.0))
        assertEquals(CompassPoint.Between(Cardinal.North, 30, Cardinal.East), point(30.0))
        assertEquals(CompassPoint.Between(Cardinal.East, 30, Cardinal.North), point(60.0))
        // Halfway is told from north or south.
        assertEquals(CompassPoint.Between(Cardinal.North, 45, Cardinal.East), point(45.0))
        assertEquals(CompassPoint.Between(Cardinal.South, 45, Cardinal.East), point(135.0))
        assertEquals(CompassPoint.Between(Cardinal.South, 45, Cardinal.West), point(225.0))
        assertEquals(CompassPoint.Between(Cardinal.North, 45, Cardinal.West), point(315.0))
    }

    /** Turning the words back into an angle lands within half a degree of the bearing, for every bearing. */
    @Test
    fun compass_point_round_trips_for_every_tenth_of_a_degree() {
        fun angle(cardinal: Cardinal) = cardinal.ordinal * 90.0
        for (tenth in 0 until 3600) {
            val degrees = tenth / 10.0
            val back = when (val point = compassPoint(Bearing.ofOrNull(degrees)!!)) {
                is CompassPoint.Exactly -> angle(point.cardinal)
                is CompassPoint.Between -> {
                    assertTrue(point.degrees in 1..45, "$point")
                    val clockwise = (angle(point.toward) - angle(point.from)).mod(360.0) == 90.0
                    assertTrue(clockwise || (angle(point.from) - angle(point.toward)).mod(360.0) == 90.0, "neighbours: $point")
                    angle(point.from) + if (clockwise) point.degrees else -point.degrees
                }
            }
            assertTrue(apart(back, degrees) <= 0.5 + 1e-9, "$degrees -> $back")
        }
    }
}

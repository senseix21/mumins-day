package com.senseix21.muminsday.districts

import com.senseix21.muminsday.clock.Latitude
import com.senseix21.muminsday.clock.Longitude
import java.io.File
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class NearestTest {

    private fun lat(degrees: Double) = checkNotNull(Latitude.of(degrees))
    private fun lon(degrees: Double) = checkNotNull(Longitude.of(degrees))

    private fun records(points: List<Triple<String, Double, Double>>) = points.map { (id, la, lo) ->
        RawDistrict(id, "TEST $id BN", "TEST $id", la, lo, 5.0)
    }

    private fun accepted(points: List<Triple<String, Double, Double>>): List<District> =
        assertIs<DistrictCheck.Accepted>(parseDistricts(records(points))).districts

    private val fake = accepted(listOf(Triple("test-a", 23.0, 90.0), Triple("test-b", 24.0, 90.0), Triple("test-c", 23.0, 91.5)))

    @Test
    fun `a position next to a headquarters point is Near that district`() {
        val result = assertIs<NearestDistrict.Near>(nearestDistrict(lat(23.01), lon(90.01), fake))
        assertEquals("test-a", result.district.id.value)
        assertTrue(result.distance.value < 2_000.0)
    }

    @Test
    fun `the nearest wins, not the first`() {
        val result = assertIs<NearestDistrict.Near>(nearestDistrict(lat(23.95), lon(90.0), fake))
        assertEquals("test-b", result.district.id.value)
    }

    @Test
    fun `just inside 50 km is Near and just beyond asks`() {
        // One degree of latitude is about 111.2 km, so 0.44 degrees is about 48.9 km and 0.46 is about 51.1 km.
        assertIs<NearestDistrict.Near>(nearestDistrict(lat(23.0 - 0.44), lon(90.0), listOf(fake[0])))
        val far = assertIs<NearestDistrict.NeedsConfirmation>(nearestDistrict(lat(23.0 - 0.46), lon(90.0), listOf(fake[0])))
        assertEquals(WhyAsk.TooFarFromAnyDistrict, far.why)
    }

    @Test
    fun `a position outside Bangladesh asks, whatever the distance`() {
        // Kolkata is inside neither the box's longitude range nor near any fake district; Dubai is far outside.
        val dubai = assertIs<NearestDistrict.NeedsConfirmation>(nearestDistrict(lat(25.2), lon(55.3), fake))
        assertEquals(WhyAsk.OutsideBangladesh, dubai.why)
        // Just west of the box but only a few kilometres from a district: still asks, the box decides first.
        val edge = accepted(listOf(Triple("test-edge", 23.0, 88.2)))
        val west = assertIs<NearestDistrict.NeedsConfirmation>(nearestDistrict(lat(23.0), lon(87.99), edge))
        assertEquals(WhyAsk.OutsideBangladesh, west.why)
    }

    @Test
    fun `kilometres are rounded to the nearest whole one`() {
        assertEquals(0, checkNotNull(com.senseix21.muminsday.clock.Meters.of(499.0)).wholeKilometers())
        assertEquals(1, checkNotNull(com.senseix21.muminsday.clock.Meters.of(500.0)).wholeKilometers())
        assertEquals(49, checkNotNull(com.senseix21.muminsday.clock.Meters.of(48_900.0)).wholeKilometers())
    }

    @Test
    fun `no districts is its own answer, never a guess`() {
        assertEquals(NearestDistrict.NoDistricts, nearestDistrict(lat(23.0), lon(90.0), emptyList()))
    }

    @Test
    fun `distance is symmetric, zero to itself and known for one degree of latitude`() {
        val d = distanceBetween(lat(23.0), lon(90.0), lat(24.0), lon(90.0)).value
        assertEquals(111_195.0, d, 100.0)
        assertEquals(d, distanceBetween(lat(24.0), lon(90.0), lat(23.0), lon(90.0)).value, 1e-6)
        assertEquals(0.0, distanceBetween(lat(23.0), lon(90.0), lat(23.0), lon(90.0)).value, 1e-9)
    }

    @Test
    fun `property - the answer is never farther than any other district's point`() {
        val random = Random(20261128)
        repeat(300) {
            val la = lat(random.nextDouble(20.0, 27.0))
            val lo = lon(random.nextDouble(87.5, 93.0))
            val answer = when (val r = nearestDistrict(la, lo, fake)) {
                is NearestDistrict.Near -> r.district to r.distance
                is NearestDistrict.NeedsConfirmation -> r.district to r.distance
                NearestDistrict.NoDistricts -> error("fake list is not empty")
            }
            for (other in fake) {
                assertTrue(answer.second.value <= distanceBetween(la, lo, other.latitude, other.longitude).value + 1e-6, "round $it")
            }
        }
    }

    @Test
    fun `each of the 64 real headquarters points finds its own district`() {
        val text = File("../content/districts.json").readText()
        val entry = Regex("\"id\": \"([a-z-]+)\".*?\"latitude\": ([0-9.]+), \"longitude\": ([0-9.]+)")
        val points = entry.findAll(text).map { Triple(it.groupValues[1], it.groupValues[2].toDouble(), it.groupValues[3].toDouble()) }.toList()
        assertEquals(64, points.size)
        val districts = accepted(points)
        for (district in districts) {
            val result = assertIs<NearestDistrict.Near>(nearestDistrict(district.latitude, district.longitude, districts), district.id.value)
            assertEquals(district.id, result.district.id)
            assertTrue(result.distance.value < 1.0)
        }
    }

    @Test
    fun `every real district's neighbourhood is Near, and the Bay of Bengal asks`() {
        val text = File("../content/districts.json").readText()
        val entry = Regex("\"id\": \"([a-z-]+)\".*?\"latitude\": ([0-9.]+), \"longitude\": ([0-9.]+)")
        val districts = accepted(entry.findAll(text).map { Triple(it.groupValues[1], it.groupValues[2].toDouble(), it.groupValues[3].toDouble()) }.toList())
        // Open water south of Cox's Bazar, inside the generous box, farther than 50 km from every headquarters.
        val sea = assertIs<NearestDistrict.NeedsConfirmation>(nearestDistrict(lat(20.6), lon(91.0), districts))
        assertEquals(WhyAsk.TooFarFromAnyDistrict, sea.why)
    }
}

package com.senseix21.muminsday.districts

import java.time.ZoneId
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Fixtures are obviously fake districts. No real place name or coordinate list is invented here (Q4 is open). */
class DistrictsTest {

    private fun record(n: Int) = RawDistrict(
        id = "test-$n",
        nameBn = "TEST DISTRICT $n BN",
        nameEn = "TEST DISTRICT $n",
        latitude = 22.0 + n / 100.0,
        longitude = 90.0 + n / 100.0,
        elevation = 10.0,
    )

    private fun problems(records: List<RawDistrict>): List<DistrictProblem> =
        assertIs<DistrictCheck.Rejected>(parseDistricts(records)).problems

    private fun kinds(records: List<RawDistrict>) = problems(records).map { it.field to it.kind }

    @Test
    fun valid_records_are_accepted_in_file_order() {
        val result = assertIs<DistrictCheck.Accepted>(parseDistricts((1..3).map(::record)))
        assertEquals(listOf("test-1", "test-2", "test-3"), result.districts.map { it.id.value })
        val first = result.districts.first()
        assertEquals(22.01, first.latitude.degrees)
        assertEquals(10.0, first.elevation.value)
        assertEquals(ZoneId.of("Asia/Dhaka"), first.place(ZoneId.of("Asia/Dhaka")).zone)
    }

    @Test
    fun an_empty_list_is_rejected() {
        assertEquals(listOf(DistrictProblem(null, null, null, DistrictProblemKind.NoDistricts)), problems(emptyList()))
    }

    @Test
    fun missing_and_blank_fields() {
        val bad = RawDistrict(id = " ", nameBn = null, nameEn = "", latitude = null, longitude = null, elevation = null)
        assertEquals(
            listOf(
                DistrictField.Id to DistrictProblemKind.Blank,
                DistrictField.NameBn to DistrictProblemKind.Missing,
                DistrictField.NameEn to DistrictProblemKind.Blank,
                DistrictField.Latitude to DistrictProblemKind.Missing,
                DistrictField.Longitude to DistrictProblemKind.Missing,
                DistrictField.Elevation to DistrictProblemKind.Missing,
            ),
            kinds(listOf(bad)),
        )
        assertEquals(DistrictProblemKind.Missing, problems(listOf(record(1).copy(id = null))).single().kind)
    }

    @Test
    fun coordinates_must_be_valid_and_inside_bangladesh() {
        assertEquals(listOf(DistrictField.Latitude to DistrictProblemKind.OutOfRange), kinds(listOf(record(1).copy(latitude = 91.0))))
        assertEquals(listOf(DistrictField.Longitude to DistrictProblemKind.OutOfRange), kinds(listOf(record(1).copy(longitude = -181.0))))
        assertEquals(listOf(DistrictField.Latitude to DistrictProblemKind.NotFinite), kinds(listOf(record(1).copy(latitude = Double.NaN))))
        assertEquals(listOf(DistrictField.Longitude to DistrictProblemKind.NotFinite), kinds(listOf(record(1).copy(longitude = Double.POSITIVE_INFINITY))))
        // Delhi, a valid coordinate in the wrong country; and a swapped latitude and longitude.
        assertEquals(
            listOf(DistrictField.Latitude to DistrictProblemKind.OutsideBangladesh),
            kinds(listOf(record(1).copy(latitude = 28.6, longitude = 77.2).copy(longitude = 90.0))),
        )
        assertEquals(
            listOf(
                DistrictField.Latitude to DistrictProblemKind.OutsideBangladesh,
                DistrictField.Longitude to DistrictProblemKind.OutsideBangladesh,
            ),
            kinds(listOf(record(1).copy(latitude = 90.0, longitude = 23.0))),
        )
    }

    @Test
    fun elevation_is_not_negative_or_absurd() {
        assertEquals(listOf(DistrictField.Elevation to DistrictProblemKind.OutOfRange), kinds(listOf(record(1).copy(elevation = -1.0))))
        assertEquals(listOf(DistrictField.Elevation to DistrictProblemKind.OutOfRange), kinds(listOf(record(1).copy(elevation = 5000.0))))
        assertEquals(listOf(DistrictField.Elevation to DistrictProblemKind.NotFinite), kinds(listOf(record(1).copy(elevation = Double.NaN))))
        assertIs<DistrictCheck.Accepted>(parseDistricts(listOf(record(1).copy(elevation = 0.0))))
    }

    @Test
    fun duplicate_ids_are_reported_on_the_later_record() {
        val found = problems(listOf(record(1), record(2), record(1).copy(nameEn = "TEST OTHER")))
        assertEquals(listOf(DistrictProblem(2, "test-1", DistrictField.Id, DistrictProblemKind.DuplicateId)), found)
    }

    @Test
    fun every_problem_is_reported_not_just_the_first() {
        val found = problems(
            listOf(record(1).copy(latitude = 99.0), record(2), record(3).copy(nameBn = ""), record(2).copy(elevation = -3.0)),
        )
        assertEquals(listOf(0, 2, 3, 3), found.map { it.recordIndex })
    }

    @Test
    fun property_random_valid_records_are_accepted_unchanged() {
        val random = Random(11)
        repeat(300) {
            val records = (1..random.nextInt(1, 70)).map { n ->
                record(n).copy(
                    latitude = random.nextDouble(BANGLADESH_LATITUDES.start, BANGLADESH_LATITUDES.endInclusive),
                    longitude = random.nextDouble(BANGLADESH_LONGITUDES.start, BANGLADESH_LONGITUDES.endInclusive),
                    elevation = random.nextDouble(0.0, MAX_ELEVATION_METERS),
                )
            }
            val accepted = assertIs<DistrictCheck.Accepted>(parseDistricts(records))
            assertEquals(records.map { it.id }, accepted.districts.map { it.id.value })
            assertEquals(records.map { it.latitude }, accepted.districts.map { it.latitude.degrees })
            assertEquals(records.map { it.longitude }, accepted.districts.map { it.longitude.degrees })
        }
    }

    @Test
    fun property_one_injected_fault_per_record_is_reported_exactly_once_each() {
        val random = Random(12)
        val faults: List<(RawDistrict) -> RawDistrict> = listOf(
            { it.copy(id = null) },
            { it.copy(nameBn = "  ") },
            { it.copy(nameEn = null) },
            { it.copy(latitude = random.nextDouble(27.0, 89.0)) },
            { it.copy(longitude = random.nextDouble(-179.0, 87.0)) },
            { it.copy(elevation = -random.nextDouble(0.1, 50.0)) },
        )
        repeat(300) {
            val count = random.nextInt(1, 40)
            val broken = (1..count).filter { random.nextBoolean() }.toSet()
            val records = (1..count).map { n -> if (n in broken) faults.random(random)(record(n)) else record(n) }
            val result = parseDistricts(records)
            if (broken.isEmpty()) {
                assertIs<DistrictCheck.Accepted>(result)
            } else {
                val found = assertIs<DistrictCheck.Rejected>(result).problems
                assertEquals(broken.map { it - 1 }.sorted(), found.map { it.recordIndex }, "one problem per faulty record")
                assertTrue(found.none { it.kind == DistrictProblemKind.DuplicateId })
            }
        }
    }
}

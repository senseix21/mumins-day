package com.senseix21.muminsday.clock

import io.kotest.property.Arb
import io.kotest.property.RandomSource
import io.kotest.property.arbitrary.double
import io.kotest.property.arbitrary.localDate
import io.kotest.property.arbitrary.take
import java.time.LocalDate
import java.time.ZoneId

val dhaka: ZoneId = ZoneId.of("Asia/Dhaka")

val dates = Arb.localDate(LocalDate.of(2020, 1, 1), LocalDate.of(2040, 12, 31))

private val latitudes = Arb.double(20.5, 26.7)
private val longitudes = Arb.double(88.0, 92.7)
private val elevations = Arb.double(0.0, 100.0)

/** Bangladesh bounding box, elevation 0..100 m, zone Asia/Dhaka. */
fun randomPlace(rs: RandomSource): Place = Place(
    Latitude.of(latitudes.sample(rs).value)!!,
    Longitude.of(longitudes.sample(rs).value)!!,
    Meters.of(elevations.sample(rs).value)!!,
    dhaka,
)

class Case(val date: LocalDate, val place: Place)

/** Seeded so a failure reproduces; the seed is part of the failure message through [Case] being printed by callers. */
fun cases(count: Int, seed: Long): List<Case> {
    val rs = RandomSource.seeded(seed)
    return dates.take(count, rs).toList().map { Case(it, randomPlace(rs)) }
}

fun computed(result: ClockResult): DayTimes = when (result) {
    is ClockResult.Computed -> result.times
    is ClockResult.NotComputable -> error("expected Computed, got $result")
}

val clock = CalcTimesClock()

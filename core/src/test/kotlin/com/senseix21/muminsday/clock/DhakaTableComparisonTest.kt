package com.senseix21.muminsday.clock

import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Comparison with the Dhaka Ramadan 2026 table at https://kivabe.com/ramadan/en/?zilla=1, which says it
 * follows the Islamic Foundation, Bangladesh (secondary copy; not the Foundation's own file). Prints differences in
 * whole minutes (engine minus table). Day 20 (10 Mar) Fajr 4:49 is a probable typo on the page and is skipped.
 *
 * Measured 2026-11-27: with Karachi and Hanafi Asr, a reference longitude of 90.2 E reproduces the table's Iftar on all
 * 30 days and its Fajr within one minute. At the usual city centre (90.41 E) the engine is 1 to 2 minutes earlier on
 * Fajr and up to 1 minute earlier on Iftar: so the method matches and the reference point is what differs (G48).
 * The table is a secondary copy of the Foundation's and covers one city and one month: this is evidence, not T-FIX-4.
 */
class DhakaTableComparisonTest {
    private data class Row(val month: Int, val day: Int, val sehri: Int, val fajr: Int, val iftar: Int)

    private val rows = listOf(
        Row(2, 19, 312, 315, 1078),
        Row(2, 20, 311, 314, 1078),
        Row(2, 21, 311, 314, 1079),
        Row(2, 22, 310, 313, 1079),
        Row(2, 23, 309, 312, 1080),
        Row(2, 24, 308, 311, 1080),
        Row(2, 25, 308, 311, 1081),
        Row(2, 26, 307, 310, 1081),
        Row(2, 27, 306, 309, 1082),
        Row(2, 28, 305, 308, 1082),
        Row(3, 1, 305, 308, 1083),
        Row(3, 2, 304, 307, 1083),
        Row(3, 3, 303, 306, 1084),
        Row(3, 4, 302, 305, 1084),
        Row(3, 5, 301, 304, 1085),
        Row(3, 6, 300, 303, 1085),
        Row(3, 7, 299, 302, 1086),
        Row(3, 8, 298, 301, 1086),
        Row(3, 9, 297, 300, 1087),
        Row(3, 10, 297, 289, 1087),
        Row(3, 11, 296, 298, 1087),
        Row(3, 12, 295, 297, 1088),
        Row(3, 13, 294, 297, 1088),
        Row(3, 14, 293, 296, 1089),
        Row(3, 15, 292, 295, 1089),
        Row(3, 16, 291, 294, 1090),
        Row(3, 17, 290, 293, 1090),
        Row(3, 18, 289, 292, 1090),
        Row(3, 19, 288, 290, 1091),
        Row(3, 20, 287, 289, 1091),
    )

    private fun minutes(i: java.time.Instant, zone: ZoneId): Int {
        val t = i.atZone(zone).toLocalTime(); return t.hour * 60 + t.minute
    }

    private fun run(label: String, lat: Double, lon: Double, elevation: Double): Pair<List<Int>, List<Int>> {
        val zone = ZoneId.of("Asia/Dhaka")
        val place = Place(Latitude.of(lat)!!, Longitude.of(lon)!!, Meters.of(elevation)!!, zone)
        val clock = CalcTimesClock()
        val fajr = mutableListOf<Int>(); val iftar = mutableListOf<Int>(); val gap = mutableListOf<Int>()
        for (r in rows) {
            val date = LocalDate.of(2026, r.month, r.day)
            val times = (clock.dayTimes(date, place, ClockSettings()) as ClockResult.Computed).times
            val isTypo = r.month == 3 && r.day == 10
            if (!isTypo) fajr += minutes(times.fajr, zone) - r.fajr
            iftar += minutes(times.maghrib, zone) - r.iftar
            gap += r.fajr - r.sehri
        }
        fun s(l: List<Int>) = "min=${l.min()} max=${l.max()} mean=${"%.2f".format(l.average())} hist=${l.groupingBy { it }.eachCount().toSortedMap()}"
        println("COMPARE[$label] lat=$lat lon=$lon elev=$elevation")
        println("COMPARE[$label]   Fajr  engine-table: ${s(fajr)}")
        println("COMPARE[$label]   Iftar engine-table: ${s(iftar)}")
        println("COMPARE[$label]   table Fajr minus Sehri end (minutes): ${s(gap)}")
        return fajr to iftar
    }

    @Test
    fun engineReproducesTheTableAtTheReferenceLongitude() {
        val (fajr, iftar) = run("reference", 23.8103, 90.2, 8.0)
        assertTrue(iftar.all { it == 0 }, "Iftar differences: $iftar")
        assertTrue(fajr.all { it in -1..0 }, "Fajr differences: $fajr")
    }

    @Test
    fun report() {
        run("city-centre", 23.8103, 90.4125, 8.0)
        run("open-admin-data", 23.788, 90.251, 8.0)
        run("ramna", 23.7370, 90.3960, 8.0)
        for (lon in listOf(89.80, 89.90, 90.00, 90.10, 90.20, 90.30, 90.41)) run("scan", 23.8103, lon, 8.0)
    }
}

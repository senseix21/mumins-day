package com.senseix21.muminsday.content

import com.senseix21.muminsday.deed.alternateFor
import com.senseix21.muminsday.deed.contentOf
import com.senseix21.muminsday.deed.deedFor
import io.kotest.property.Arb
import io.kotest.property.RandomSource
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.long
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/** T-PROP-8 (INV-C2) for the shared rotation, and the proof that deeds, duas and hadith rotate by the one rule. */
class RotationTest {

    private val rs = RandomSource.seeded(1313)
    private val epochDays = Arb.long(-30_000L, 60_000L)

    @Test
    fun T_PROP_8_INV_C2_any_window_of_N_days_shows_every_record_once_and_the_sequence_repeats() {
        repeat(300) {
            val n = Arb.int(4, 120).sample(rs).value
            val rotation = (1..n).toList()
            val start = LocalDate.ofEpochDay(epochDays.sample(rs).value)
            val window = (0 until n).map { pickForDay(start.plusDays(it.toLong()), rotation) }
            assertEquals(rotation.toSet(), window.toSet(), "n=$n start=$start")
            assertEquals(pickForDay(start, rotation), pickForDay(start.plusDays(n.toLong()), rotation))
        }
    }

    @Test
    fun G4_the_alternate_differs_from_yesterday_today_and_tomorrow() {
        repeat(300) {
            val n = Arb.int(4, 120).sample(rs).value
            val rotation = (1..n).toList()
            val date = LocalDate.ofEpochDay(epochDays.sample(rs).value)
            val alternate = alternateForDay(date, rotation)
            for (offset in -1L..1L) assertNotEquals(pickForDay(date.plusDays(offset), rotation), alternate, "n=$n date=$date")
        }
    }

    @Test
    fun INV_C2_deeds_duas_and_hadith_use_the_same_rule() {
        val deeds = contentOf(30)
        val duas = accepted(ContentKind.Dua)
        val hadith = accepted(ContentKind.Hadith)
        repeat(300) {
            val date = LocalDate.ofEpochDay(epochDays.sample(rs).value)
            val index = deeds.approved.indexOf(deedFor(date, deeds))
            assertEquals(index, duas.approved.indexOf(duas.forDay(date)))
            assertEquals(index, hadith.approved.indexOf(hadith.forDay(date)))
            assertEquals(alternateFor(date, deeds), alternateForDay(date, deeds.approved))
        }
    }

    @Test
    fun INV_C2_same_date_same_record_and_dates_before_1970_rotate_the_same_way() {
        val duas = accepted(ContentKind.Dua)
        assertEquals(duas.approved.last(), duas.forDay(LocalDate.ofEpochDay(-1)))
        val date = LocalDate.of(2026, 11, 28)
        assertEquals(duas.forDay(date), accepted(ContentKind.Dua).forDay(date))
    }
}

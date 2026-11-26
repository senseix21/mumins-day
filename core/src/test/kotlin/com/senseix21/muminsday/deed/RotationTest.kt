package com.senseix21.muminsday.deed

import io.kotest.property.Arb
import io.kotest.property.RandomSource
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.long
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class RotationTest {

    private val rs = RandomSource.seeded(42)
    private val epochDays = Arb.long(-30_000L, 60_000L) // about 1888 to 2134

    @Test
    fun T_PROP_8_INV_C2_same_date_and_content_give_the_same_deed() {
        val content = contentOf(30)
        repeat(200) {
            val date = LocalDate.ofEpochDay(epochDays.sample(rs).value)
            assertEquals(deedFor(date, content), deedFor(date, content))
            assertEquals(deedFor(date, content), deedFor(date, accepted(1, rawDeeds(30))))
        }
    }

    @Test
    fun T_PROP_8_INV_C2_any_window_of_N_days_shows_every_approved_deed_exactly_once() {
        val sizes = Arb.int(4, 80)
        repeat(200) {
            val n = sizes.sample(rs).value
            val content = contentOf(n)
            val start = LocalDate.ofEpochDay(epochDays.sample(rs).value)
            val shown = (0 until n).map { deedFor(start.plusDays(it.toLong()), content).id }
            assertEquals(content.approved.map { it.id }.toSet(), shown.toSet(), "n=$n start=$start")
            assertEquals(n, shown.toSet().size)
        }
    }

    @Test
    fun T_PROP_8_INV_C2_windows_cover_dates_before_1970() {
        val content = contentOf(30)
        for (start in listOf(LocalDate.of(1969, 12, 20), LocalDate.of(1900, 1, 1), LocalDate.of(1970, 1, 1))) {
            val shown = (0 until 30).map { deedFor(start.plusDays(it.toLong()), content).id }.toSet()
            assertEquals(30, shown.size, "start=$start")
        }
        // the floored modulo: epoch day -1 is the last deed, not a negative index
        assertEquals(content.approved.last(), deedFor(LocalDate.ofEpochDay(-1), content))
    }

    @Test
    fun T_PROP_8_INV_C2_the_sequence_repeats_after_N_days() {
        repeat(200) {
            val n = Arb.int(4, 80).sample(rs).value
            val content = contentOf(n)
            val date = LocalDate.ofEpochDay(epochDays.sample(rs).value)
            assertEquals(deedFor(date, content), deedFor(date.plusDays(n.toLong()), content))
        }
    }

    @Test
    fun T_PROP_8_INV_C2_the_alternate_differs_from_yesterday_today_and_tomorrow_and_is_half_a_cycle_away() {
        for (n in 4..80) {
            val content = contentOf(n)
            repeat(25) {
                val date = LocalDate.ofEpochDay(epochDays.sample(rs).value)
                val alternate = alternateFor(date, content)
                assertNotEquals(deedFor(date.minusDays(1), content), alternate, "n=$n date=$date")
                assertNotEquals(deedFor(date, content), alternate, "n=$n date=$date")
                assertNotEquals(deedFor(date.plusDays(1), content), alternate, "n=$n date=$date")
                assertEquals(deedFor(date.plusDays((n / 2).toLong()), content), alternate, "n=$n date=$date")
            }
        }
    }

    @Test
    fun T_PROP_8_INV_C2_retired_records_never_appear() {
        val retired = (31..40).map { rawDeed(it, status = "retired", addedIn = 1, retiredIn = 2) }
        val content = accepted(2, rawDeeds(30) + retired)
        val retiredIds = content.retired.map { it.id }.toSet()
        assertEquals(10, retiredIds.size)
        repeat(300) {
            val date = LocalDate.ofEpochDay(epochDays.sample(rs).value)
            assertTrue(deedFor(date, content).id !in retiredIds)
            assertTrue(alternateFor(date, content).id !in retiredIds)
        }
    }
}

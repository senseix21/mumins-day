package com.senseix21.muminsday.deed

import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class DayCardTest {

    private val day = someDay
    private val v1 = accepted(1, rawDeeds(30))
    private val early = Instant.parse("2026-11-26T03:00:00Z")
    private val late = Instant.parse("2026-11-26T20:00:00Z")

    private fun fresh(content: DeedContent = v1) = cardFor(day, content, null)

    @Test
    fun INV_C2_a_fresh_card_is_the_computed_deed_current_version_not_swapped_not_done() {
        val card = fresh()
        assertEquals(DayCard(day, deedFor(day, v1).id, 1, swapped = false, doneAt = null), card)
    }

    @Test
    fun G5_a_stored_card_survives_a_version_bump_that_appends_deeds() {
        val stored = fresh()
        // Appended deeds change N, and with it the deed the date would compute to.
        val v2 = accepted(2, rawDeeds(30) + rawDeeds(7, firstNumber = 31))
        assertNotEquals(stored.deedId, deedFor(day, v2).id, "fixture must make the computed deed differ")
        assertEquals(stored, cardFor(day, v2, stored))
        assertEquals(1, cardFor(day, v2, stored).contentVersion)
    }

    @Test
    fun G5_a_stored_card_survives_a_version_bump_that_changes_the_order() {
        val stored = fresh()
        val reordered = accepted(2, rawDeeds(30).reversed())
        assertNotEquals(stored.deedId, deedFor(day, reordered).id)
        assertEquals(stored, cardFor(day, reordered, stored))
    }

    @Test
    fun G5_a_stored_card_whose_deed_was_retired_is_recomputed_keeping_doneAt_and_resetting_swapped() {
        val swappedAndDone = markDone((swap(fresh(), v1) as SwapResult.Swapped).card, early)
        val shown = v1.find(swappedAndDone.deedId)
        assertEquals(shown?.id, swappedAndDone.deedId)
        val records = rawDeeds(30).map { if (it.id == swappedAndDone.deedId.value) rawDeed(1, "retired", 1, 2).copy(id = it.id) else it } +
            rawDeeds(2, firstNumber = 31)
        val v2 = accepted(2, records)
        assertNull(v2.find(swappedAndDone.deedId))

        val card = cardFor(day, v2, swappedAndDone)
        assertEquals(deedFor(day, v2).id, card.deedId)
        assertEquals(2, card.contentVersion)
        assertEquals(false, card.swapped)
        assertEquals(early, card.doneAt)
        assertEquals(day, card.date)
    }

    @Test
    fun G5_a_stored_card_whose_deed_was_removed_from_the_file_is_recomputed() {
        val stored = fresh()
        val without = accepted(2, rawDeeds(31).filter { it.id != stored.deedId.value })
        assertEquals(deedFor(day, without).id, cardFor(day, without, stored).deedId)
    }

    @Test
    fun G5_a_stored_row_for_another_date_is_a_caller_bug() {
        val other = fresh().copy(date = day.minusDays(1))
        assertFailsWith<IllegalArgumentException> { cardFor(day, v1, other) }
    }

    @Test
    fun F4_one_swap_per_day_then_not_available() {
        val first = swap(fresh(), v1)
        assertIs<SwapResult.Swapped>(first)
        assertEquals(alternateFor(day, v1).id, first.card.deedId)
        assertEquals(true, first.card.swapped)
        assertEquals(SwapResult.NotAvailable(SwapUnavailable.AlreadySwapped), swap(first.card, v1))
    }

    /**
     * A card kept across a content update can hold the deed that is the alternate under the new content. A swap must
     * still change what the user sees. Checked for every deed a stored card could hold, on many days and sizes.
     */
    @Test
    fun F4_a_swap_always_changes_the_deed_even_for_a_card_kept_across_an_update() {
        for (count in listOf(4, 5, 30, 31, 47)) {
            val content = contentOf(count, version = 2)
            for (offset in 0L until 40L) {
                val date = day.plusDays(offset)
                for (held in content.approved) {
                    val stored = DayCard(date, held.id, contentVersion = 1, swapped = false, doneAt = null)
                    val swapped = (swap(cardFor(date, content, stored), content) as SwapResult.Swapped).card
                    kotlin.test.assertNotEquals(held.id, swapped.deedId, "count=$count date=$date held=${held.id}")
                    kotlin.test.assertNotNull(content.find(swapped.deedId))
                }
            }
        }
    }

    @Test
    fun F4_no_swap_once_the_day_is_done() {
        assertEquals(SwapResult.NotAvailable(SwapUnavailable.AlreadyDone), swap(markDone(fresh(), early), v1))
    }

    @Test
    fun F4_a_swapped_card_is_kept_by_cardFor() {
        val swapped = (swap(fresh(), v1) as SwapResult.Swapped).card
        assertEquals(swapped, cardFor(day, v1, swapped))
    }

    @Test
    fun F4_markDone_is_idempotent_and_keeps_the_first_instant() {
        val once = markDone(fresh(), early)
        assertEquals(early, once.doneAt)
        assertEquals(once, markDone(once, late))
    }

    @Test
    fun F4_moving_the_clock_back_does_not_uncomplete_a_day() {
        val done = markDone(fresh(), early)
        assertEquals(early, cardFor(day, v1, done).doneAt)
        // A later date asked for does not touch the earlier stored card: cards are per date and values.
        val tomorrow = cardFor(day.plusDays(1), v1, null)
        assertNull(tomorrow.doneAt)
        assertEquals(early, cardFor(day, v1, done).doneAt)
    }

    @Test
    fun INV_C4_weekDots_are_seven_oldest_first_ending_with_today() {
        val today = LocalDate.of(2026, 11, 26)
        val dots = weekDots(today, setOf(today, today.minusDays(2), today.minusDays(6)))
        assertEquals(listOf(true, false, false, false, true, false, true), dots)
        assertEquals(7, weekDots(today, emptySet()).size)
        assertEquals(List(7) { false }, weekDots(today, emptySet()))
    }

    @Test
    fun INV_C4_weekDots_ignore_dates_outside_the_seven_days() {
        val today = LocalDate.of(2026, 11, 26)
        val outside = setOf(today.minusDays(7), today.minusDays(100), today.plusDays(1), today.plusDays(30))
        assertEquals(List(7) { false }, weekDots(today, outside))
        assertEquals(weekDots(today, setOf(today)), weekDots(today, setOf(today) + outside))
    }
}

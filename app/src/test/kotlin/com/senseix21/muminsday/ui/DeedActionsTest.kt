package com.senseix21.muminsday.ui

import com.senseix21.muminsday.deed.ContentCheck
import com.senseix21.muminsday.deed.DayCard
import com.senseix21.muminsday.deed.DeedContent
import com.senseix21.muminsday.deed.RawDeed
import com.senseix21.muminsday.deed.SwapResult
import com.senseix21.muminsday.deed.cardFor
import com.senseix21.muminsday.deed.checkContent
import com.senseix21.muminsday.deed.markDone
import com.senseix21.muminsday.deed.swap
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Fake records only (INV-C1): nothing here is deed text. */
class DeedActionsTest {

    private val content: DeedContent = run {
        val records = (1..30).map { n ->
            RawDeed(
                "test-$n", "TEST DEED $n BN", "TEST DEED $n", "Bukhari", "TEST $n", "TEST SOURCE", "TEST GRADING",
                "TEST REVIEWER", "2000-01-01", "approved", "1", null,
            )
        }
        (checkContent(1, records) as ContentCheck.Accepted).content
    }
    private val date = LocalDate.of(2026, 11, 27)
    private val fresh: DayCard = cardFor(date, content, null)

    @Test
    fun `a fresh card can be marked done and swapped`() {
        assertEquals(DeedActions(canMarkDone = true, canShowAnother = true), actionsFor(fresh))
    }

    @Test
    fun `after a swap the card can only be marked done`() {
        val swapped = (swap(fresh, content) as SwapResult.Swapped).card
        assertEquals(DeedActions(canMarkDone = true, canShowAnother = false), actionsFor(swapped))
    }

    @Test
    fun `a done card rests`() {
        val done = markDone(fresh, Instant.parse("2026-11-27T06:00:00Z"))
        assertEquals(DeedActions(canMarkDone = false, canShowAnother = false), actionsFor(done))
    }

    // The screen must offer "show me another" exactly when core's swap would do it, in every state.
    @Test
    fun `the swap button is shown exactly when core would swap`() {
        val states = listOf(
            fresh,
            fresh.copy(swapped = true),
            markDone(fresh, Instant.EPOCH),
            markDone(fresh, Instant.EPOCH).copy(swapped = true),
        )
        for (card in states) {
            assertEquals(swap(card, content) is SwapResult.Swapped, actionsFor(card).canShowAnother, "$card")
        }
        assertIs<SwapResult.Swapped>(swap(fresh, content))
        assertFalse(actionsFor(markDone(fresh, Instant.EPOCH)).canMarkDone)
        assertTrue(actionsFor(fresh).canMarkDone)
    }
}

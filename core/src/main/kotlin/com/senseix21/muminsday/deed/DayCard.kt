package com.senseix21.muminsday.deed

import java.time.Instant
import java.time.LocalDate

/**
 * The row the shell stores for one local date (G5): which deed was shown, under which content version, whether the
 * user swapped it, and when it was done. Holds no count of any kind (INV-C4).
 */
public data class DayCard(
    val date: LocalDate,
    val deedId: DeedId,
    val contentVersion: Int,
    val swapped: Boolean,
    val doneAt: Instant?,
)

/**
 * Today's card (G5). With no stored row it is computed. A stored row stays as it is, even across a content update,
 * unless its deed is no longer approved: retired text must stop showing at once (F4 failure mode 5), and a day that
 * was completed stays completed. [stored] must be for [date]; anything else is a bug in the caller.
 */
public fun cardFor(date: LocalDate, content: DeedContent, stored: DayCard?): DayCard {
    if (stored == null) {
        return DayCard(date, deedFor(date, content).id, content.version, swapped = false, doneAt = null)
    }
    require(stored.date == date) { "stored card is for ${stored.date}, not $date" }
    // A null here means "not in rotation any more", which is a real answer and not a failure.
    if (content.find(stored.deedId) != null) return stored
    return stored.copy(deedId = deedFor(date, content).id, contentVersion = content.version, swapped = false)
}

public enum class SwapUnavailable { AlreadySwapped, AlreadyDone }

public sealed interface SwapResult {
    public data class Swapped(val card: DayCard) : SwapResult
    public data class NotAvailable(val reason: SwapUnavailable) : SwapResult
}

/**
 * One swap a day, and none once the day is done. No penalty, nothing recorded beyond [DayCard.swapped].
 *
 * A swap always changes the deed. Normally the alternate is half a cycle from the card's deed; but a card kept across
 * a content update (G5) may happen to hold the very deed that is the alternate under the new content, and then the
 * one after it is used.
 */
public fun swap(card: DayCard, content: DeedContent): SwapResult = when {
    card.swapped -> SwapResult.NotAvailable(SwapUnavailable.AlreadySwapped)
    card.doneAt != null -> SwapResult.NotAvailable(SwapUnavailable.AlreadyDone)
    else -> SwapResult.Swapped(
        card.copy(deedId = alternateTo(card, content).id, swapped = true, contentVersion = content.version),
    )
}

private fun alternateTo(card: DayCard, content: DeedContent): Deed {
    val alternate = alternateFor(card.date, content)
    if (alternate.id != card.deedId) return alternate
    val count = content.approved.size
    return content.approved[(card.date.toEpochDay() + count / 2 + 1).mod(count)]
}

/** Idempotent: a second tap keeps the first instant. */
public fun markDone(card: DayCard, at: Instant): DayCard =
    if (card.doneAt != null) card else card.copy(doneAt = at)

/**
 * The soft week view: seven dots, oldest first, the last one is [today]. Dots only, as a list of booleans: no count, no
 * streak length, nothing that could be shown as a number (INV-C4). Dates outside the seven days are ignored.
 */
public fun weekDots(today: LocalDate, doneDates: Set<LocalDate>): List<Boolean> =
    (6L downTo 0L).map { daysAgo -> today.minusDays(daysAgo) in doneDates }

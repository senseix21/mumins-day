package com.senseix21.muminsday.ui

import com.senseix21.muminsday.deed.DayCard

/**
 * Which buttons the card shows. Mirrors the rules in core's `swap` and `markDone`: once the day is done the card rests
 * (F4 story 2), and "show me another" is once a day (F4 failure mode 3). Nothing here counts or remembers anything else
 * (INV-C4).
 */
data class DeedActions(val canMarkDone: Boolean, val canShowAnother: Boolean)

fun actionsFor(card: DayCard): DeedActions {
    val open = card.doneAt == null
    return DeedActions(canMarkDone = open, canShowAnother = open && !card.swapped)
}

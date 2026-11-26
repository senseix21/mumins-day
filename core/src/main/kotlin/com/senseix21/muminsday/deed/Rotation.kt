package com.senseix21.muminsday.deed

import java.time.LocalDate

/**
 * INV-C2: the deed first computed for a date is a pure function of the date and the content. [date] is the user's
 * local calendar date, supplied by the caller; this function reads no clock. Every approved deed appears once in any
 * N consecutive days. `mod` is the floored one, so dates before 1970 rotate the same way.
 */
public fun deedFor(date: LocalDate, content: DeedContent): Deed =
    content.approved[date.toEpochDay().mod(content.approved.size)]

/**
 * G4: "show me another" is the deed half a cycle away, which differs from yesterday's, today's and tomorrow's deed
 * because N is at least [MIN_ROTATION]. Also a pure function of (date, content).
 */
public fun alternateFor(date: LocalDate, content: DeedContent): Deed {
    val count = content.approved.size
    return content.approved[(date.toEpochDay() + count / 2).mod(count)]
}

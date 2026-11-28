package com.senseix21.muminsday.content

import java.time.LocalDate

/**
 * INV-C2 for every content kind (deeds, duas, hadith): the record for a date is a pure function of the date and the
 * rotation, which is the approved records in file order. Every record appears once in any N consecutive days, and the
 * sequence repeats after N. [date] is the user's local calendar date, supplied by the caller; nothing here reads a clock.
 * `mod` is the floored one, so dates before 1970 rotate the same way. [rotation] is never empty: each kind's content
 * type refuses to exist with fewer than four records (G4).
 */
public fun <T> pickForDay(date: LocalDate, rotation: List<T>): T = rotation[date.toEpochDay().mod(rotation.size)]

/**
 * G4: "show me another" is the record half a cycle away, which differs from yesterday's, today's and tomorrow's when
 * the rotation has at least four records. Also a pure function of (date, rotation).
 */
public fun <T> alternateForDay(date: LocalDate, rotation: List<T>): T =
    rotation[(date.toEpochDay() + rotation.size / 2).mod(rotation.size)]

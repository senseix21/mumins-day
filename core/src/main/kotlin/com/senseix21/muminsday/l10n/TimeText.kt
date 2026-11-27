package com.senseix21.muminsday.l10n

import java.time.Instant
import java.time.ZoneId

public enum class ClockStyle { Hour12, Hour24 }

/**
 * Where each part of the day begins, by local hour 0-23. ASSUMED: the common usage, not yet confirmed by a native
 * speaker (listed for review in app/l10n-review.txt). A boundary here is a rule, so it lives in core; the words are not.
 */
internal fun dayPartOf(hour: Int): DayPart = when (hour) {
    in 4..5 -> DayPart.Dawn
    in 6..11 -> DayPart.Morning
    in 12..14 -> DayPart.Noon
    in 15..16 -> DayPart.Afternoon
    in 17..19 -> DayPart.Evening
    else -> DayPart.Night
}

/**
 * The wall-clock time of [instant] in [zone], to the minute (seconds are dropped, never rounded: rounding is the
 * clock's job, INV-T5). Bengali: `সকাল ৬:০৫` or `০৬:০৫`; English: `6:05 am` or `06:05`. Digits are Bengali for
 * Bengali and never Latin (INV-L2). Nothing here reads the default zone or locale (INV-T3).
 */
public fun timeText(instant: Instant, zone: ZoneId, style: ClockStyle, words: Words): String {
    val local = instant.atZone(zone)
    val hour = local.hour
    val minute = local.minute.toString().padStart(2, '0')
    val text = when (style) {
        ClockStyle.Hour24 -> "${hour.toString().padStart(2, '0')}:$minute"
        ClockStyle.Hour12 -> {
            val clockHour = if (hour % 12 == 0) 12 else hour % 12
            when (words.language) {
                Language.Bengali -> "${words.dayParts.of(dayPartOf(hour))} $clockHour:$minute"
                Language.English -> "$clockHour:$minute ${if (hour < 12) words.am else words.pm}"
            }
        }
    }
    return digits(text, words.language)
}

/** `শুক্রবার, ২৭ নভেম্বর ২০২৬` / `Friday, 27 November 2026`: the Gregorian date of [instant] in [zone]. */
public fun dateText(instant: Instant, zone: ZoneId, words: Words): String {
    val local = instant.atZone(zone)
    val weekday = words.weekdays[local.dayOfWeek.ordinal]
    val month = words.months[local.monthValue - 1]
    return digits("$weekday, ${local.dayOfMonth} $month ${local.year}", words.language)
}

/** A whole number as the user reads it. */
public fun numberText(count: Int, language: Language): String = digits(count.toString(), language)

private fun digits(text: String, language: Language): String = when (language) {
    Language.Bengali -> toBengaliDigits(text)
    Language.English -> text
}

package com.senseix21.muminsday.l10n

import java.lang.Character.UnicodeScript

/** U+2068 FIRST STRONG ISOLATE and U+2069 POP DIRECTIONAL ISOLATE (INV-L3). */
public const val FIRST_STRONG_ISOLATE: Char = '⁨'
public const val POP_DIRECTIONAL_ISOLATE: Char = '⁩'

/** [text] shielded from its surroundings: its direction cannot reorder the neighbouring text, digits or punctuation. */
public fun isolate(text: String): String = "$FIRST_STRONG_ISOLATE$text$POP_DIRECTIONAL_ISOLATE"

/**
 * Wraps every run of Arabic-script text in [text] in an isolate (INV-L3). A run is Arabic letters, plus the spaces and
 * combining marks between them; spaces before the first or after the last letter stay outside. Text that is not
 * Arabic is returned as it is. Call it once: text already isolated would be wrapped again.
 */
public fun isolateArabicRuns(text: String): String = buildString {
    val points = text.codePoints().toArray()
    var index = 0
    while (index < points.size) {
        if (!isArabicLetter(points[index])) {
            appendCodePoint(points[index])
            index++
            continue
        }
        // The run ends at the last Arabic letter reached through letters, marks and spaces.
        var end = index
        var scan = index
        while (scan < points.size && (isArabicLetter(points[scan]) || isRunFiller(points[scan]))) {
            if (isArabicLetter(points[scan]) || Character.getType(points[scan]) == Character.NON_SPACING_MARK.toInt()) end = scan
            scan++
        }
        append(FIRST_STRONG_ISOLATE)
        for (at in index..end) appendCodePoint(points[at])
        append(POP_DIRECTIONAL_ISOLATE)
        index = end + 1
    }
}

private fun isArabicLetter(point: Int): Boolean = UnicodeScript.of(point) == UnicodeScript.ARABIC && Character.isLetter(point)

private fun isRunFiller(point: Int): Boolean =
    Character.isWhitespace(point) || Character.getType(point) == Character.NON_SPACING_MARK.toInt()

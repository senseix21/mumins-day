package com.senseix21.muminsday.l10n

private const val BENGALI_ZERO = '০'

/**
 * Maps ASCII 0-9 to Bengali ০-৯ (U+09E6..U+09EF) and leaves every other character as it is (INV-L2). All ten digits
 * are mapped; upstream's loop stopped one short and left 9 in ASCII.
 */
public fun toBengaliDigits(text: String): String = buildString(text.length) {
    for (character in text) {
        append(if (character in '0'..'9') BENGALI_ZERO + (character - '0') else character)
    }
}

package com.senseix21.muminsday.l10n

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

private val PLAIN_DECIMAL = Regex("[0-9]+(\\.[0-9]+)?")

/**
 * What a person typed into a number field, as a number: Bengali or Latin digits, one optional point. Anything else
 * (blank, a sign, a comma, a letter) is null, so a mistyped amount is refused and never read as a different one.
 */
public fun parseTypedNumber(text: String): BigDecimal? {
    val latin = buildString {
        for (character in text.trim()) append(if (character in '০'..'৯') '0' + (character - '০') else character)
    }
    return if (PLAIN_DECIMAL.matches(latin)) BigDecimal(latin) else null
}

/**
 * [value] to [scale] decimal places in the direction the caller chooses, with trailing zeros dropped, in the language's
 * digits (INV-L2). The direction is the caller's because it matters: an amount owed rounds up (INV-Z1).
 */
public fun decimalText(value: BigDecimal, scale: Int, mode: RoundingMode, language: Language): String {
    val rounded = value.setScale(scale, mode).stripTrailingZeros()
    val plain = if (rounded.scale() < 0) rounded.setScale(0).toPlainString() else rounded.toPlainString()
    return when (language) {
        Language.Bengali -> toBengaliDigits(plain)
        Language.English -> plain
    }
}

/** `২৭ নভেম্বর ২০২৬` / `27 November 2026`. */
public fun localDateText(date: LocalDate, words: Words): String =
    digits("${date.dayOfMonth} ${words.months[date.monthValue - 1]} ${date.year}", words.language)

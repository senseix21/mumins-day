package com.senseix21.muminsday.l10n

import kotlin.math.absoluteValue

public enum class PluralForm { One, Other }

/**
 * Which form of a counted phrase to use. Bengali (CLDR rule `i = 0 or n = 1`, ASSUMED from memory of CLDR, not
 * checked against it here) treats 0 as singular; English does not. The sign is ignored, as CLDR does.
 */
public fun pluralForm(language: Language, count: Int): PluralForm {
    val magnitude = count.toLong().absoluteValue
    return when (language) {
        Language.Bengali -> if (magnitude <= 1L) PluralForm.One else PluralForm.Other
        Language.English -> if (magnitude == 1L) PluralForm.One else PluralForm.Other
    }
}

public fun <T> choosePlural(language: Language, count: Int, one: T, other: T): T = when (pluralForm(language, count)) {
    PluralForm.One -> one
    PluralForm.Other -> other
}

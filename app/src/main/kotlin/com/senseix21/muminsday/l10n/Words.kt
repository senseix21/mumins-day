package com.senseix21.muminsday.l10n

import android.content.Context
import android.content.res.Configuration
import com.senseix21.muminsday.R
import java.util.Locale

/**
 * The copy core's formatters print, read from string resources in [language] whatever the device's own language is.
 * Core holds no words (INV-L1); this is where they come from.
 */
fun wordsFor(context: Context, language: Language): Words {
    val configuration = Configuration(context.resources.configuration)
    // Bengali is the default resource set, so "bn" resolves to values/ and "en" to values-en/.
    configuration.setLocale(Locale.forLanguageTag(if (language == Language.Bengali) "bn" else "en"))
    val resources = context.createConfigurationContext(configuration).resources
    return Words(
        language = language,
        dayParts = DayPartWords(
            dawn = resources.getString(R.string.day_part_dawn),
            morning = resources.getString(R.string.day_part_morning),
            noon = resources.getString(R.string.day_part_noon),
            afternoon = resources.getString(R.string.day_part_afternoon),
            evening = resources.getString(R.string.day_part_evening),
            night = resources.getString(R.string.day_part_night),
        ),
        am = resources.getString(R.string.time_am),
        pm = resources.getString(R.string.time_pm),
        months = resources.getStringArray(R.array.months).toList(),
        weekdays = resources.getStringArray(R.array.weekdays).toList(),
    )
}

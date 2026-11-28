package com.senseix21.muminsday.l10n

public enum class Language { Bengali, English }

/** The parts of a Bengali day, in the order of a day starting at dawn. Where each begins is [dayPartOf]'s rule. */
public enum class DayPart { Dawn, Morning, Noon, Afternoon, Evening, Night }

public data class DayPartWords(
    val dawn: String,
    val morning: String,
    val noon: String,
    val afternoon: String,
    val evening: String,
    val night: String,
) {
    public fun of(part: DayPart): String = when (part) {
        DayPart.Dawn -> dawn
        DayPart.Morning -> morning
        DayPart.Noon -> noon
        DayPart.Afternoon -> afternoon
        DayPart.Evening -> evening
        DayPart.Night -> night
    }
}

/**
 * Every word the formatters in this package print. It is copy, not logic: the app builds it from string resources and
 * core never holds a word of its own, so a native speaker's edit to a resource is the whole change (INV-L1).
 *
 * Bengali 12-hour time uses [dayParts]; English uses [am] and [pm]. Both languages carry all of them so the two
 * resource files have the same keys.
 *
 * [months] runs January to December and [weekdays] Monday to Sunday (java.time order); the app's string-array
 * lengths are checked by the T-BUILD-5 gate, and a wrong length here is a programming error that fails at once.
 */
public data class Words(
    val language: Language,
    val dayParts: DayPartWords,
    val am: String,
    val pm: String,
    val months: List<String>,
    val weekdays: List<String>,
    /** The twelve Hijri months, Muharram first. */
    val hijriMonths: List<String>,
) {
    init {
        require(months.size == 12) { "months needs 12 names, got ${months.size}" }
        require(hijriMonths.size == 12) { "hijriMonths needs 12 names, got ${hijriMonths.size}" }
        require(weekdays.size == 7) { "weekdays needs 7 names, got ${weekdays.size}" }
    }
}

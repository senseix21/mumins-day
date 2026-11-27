package com.senseix21.muminsday.ui

import android.content.Context
import com.senseix21.muminsday.R
import com.senseix21.muminsday.bearing.Bearing
import com.senseix21.muminsday.bearing.Cardinal
import com.senseix21.muminsday.bearing.CompassPoint
import com.senseix21.muminsday.clock.Countdown
import com.senseix21.muminsday.l10n.ClockStyle
import com.senseix21.muminsday.l10n.Language
import com.senseix21.muminsday.l10n.Words
import com.senseix21.muminsday.l10n.dateText
import com.senseix21.muminsday.l10n.numberText
import com.senseix21.muminsday.l10n.timeText
import com.senseix21.muminsday.l10n.toBengaliDigits
import com.senseix21.muminsday.l10n.wordsFor
import com.senseix21.muminsday.planner.ReminderMode
import com.senseix21.muminsday.planner.Salah
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** What a time cannot be computed shows as (F2 #4). Not a time, so no digit and nothing to translate. */
const val NO_TIME: String = "—"

/**
 * Everything a screen prints, so the screens never format a time, date or number themselves (CLAUDE.md: the UI asks
 * `l10n`). [context] already carries the chosen language ([BaseActivity]); [language] says which digits to use.
 */
class Texts(private val context: Context, val language: Language) {
    private val words: Words = wordsFor(context, language)

    fun time(instant: Instant, zone: ZoneId): String = timeText(instant, zone, ClockStyle.Hour12, words)

    fun date(instant: Instant, zone: ZoneId): String = dateText(instant, zone, words)

    /** Monday to Sunday, as the language's own word. */
    fun weekday(date: LocalDate): String = words.weekdays[date.dayOfWeek.value - 1]

    fun number(count: Int): String = numberText(count, language)

    /** Text that came from a data file: in Bengali any Latin digit in it is shown as a Bengali one (INV-L2). */
    fun data(text: String): String = if (language == Language.Bengali) toBengaliDigits(text) else text

    fun salah(salah: Salah): String = context.getString(
        when (salah) {
            Salah.Fajr -> R.string.prayer_fajr
            Salah.Dhuhr -> R.string.prayer_dhuhr
            Salah.Asr -> R.string.prayer_asr
            Salah.Maghrib -> R.string.prayer_maghrib
            Salah.Isha -> R.string.prayer_isha
        },
    )

    fun mode(mode: ReminderMode): String = context.getString(
        when (mode) {
            ReminderMode.Adhan -> R.string.mode_adhan
            ReminderMode.Tone -> R.string.mode_tone
            ReminderMode.Silent -> R.string.mode_silent
            ReminderMode.Off -> R.string.mode_off
        },
    )

    fun minutes(count: Int): String =
        context.resources.getQuantityString(R.plurals.minutes_count, count, number(count))

    fun hours(count: Int): String = context.resources.getQuantityString(R.plurals.hours_count, count, number(count))

    /** "3 hours 25 minutes", "25 minutes", "2 hours". Never an empty string. */
    fun countdown(left: Countdown): String = when {
        left.hours > 0 && left.minutes > 0 -> "${hours(left.hours)} ${minutes(left.minutes)}"
        left.hours > 0 -> hours(left.hours)
        else -> minutes(left.minutes)
    }

    /** A signed offset: "+2 minutes", "-5 minutes". The sign is a plain character in both languages. */
    fun signedMinutes(minutes: Int): String = (if (minutes > 0) "+" else if (minutes < 0) "-" else "") + minutes(kotlin.math.abs(minutes))

    /** Whole degrees, in the language's own digits: "278". The caller adds the degree sign through its string. */
    fun degrees(bearing: Bearing): String = number(Math.round(bearing.degrees).toInt() % 360)

    /** "west", or "8 degrees north of west" (the structure is core's, the words are resources). */
    fun compassPoint(point: CompassPoint): String = when (point) {
        is CompassPoint.Exactly -> cardinal(point.cardinal)
        is CompassPoint.Between ->
            context.getString(R.string.compass_between, cardinal(point.from), number(point.degrees), toward(point.toward))
    }

    private fun cardinal(cardinal: Cardinal): String = context.getString(
        when (cardinal) {
            Cardinal.North -> R.string.cardinal_north
            Cardinal.East -> R.string.cardinal_east
            Cardinal.South -> R.string.cardinal_south
            Cardinal.West -> R.string.cardinal_west
        },
    )

    private fun toward(cardinal: Cardinal): String = context.getString(
        when (cardinal) {
            Cardinal.North -> R.string.cardinal_north_toward
            Cardinal.East -> R.string.cardinal_east_toward
            Cardinal.South -> R.string.cardinal_south_toward
            Cardinal.West -> R.string.cardinal_west_toward
        },
    )
}

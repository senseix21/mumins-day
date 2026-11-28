package com.senseix21.muminsday.khatam

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * A plan to read the whole Quran by [target] (phase 15, ADR-24). It holds where the reader is and nothing about the days
 * between: no field can say a day went by without reading, so no screen can count one (INV-C4, in the spirit of INV-C5).
 * A test checks the member names.
 *
 * [position] is how far the user has read. [markedOn] is the local date of the last mark and [dayStart] the position at
 * the start of that date, so that today's portion stays the same however many times the screen is opened, and a mark
 * can be taken back. [end] is the Quran's own end, copied from the verified text when the plan was made, so a reminder
 * can be decided without opening the text. Construct with [startKhatam] or [restoreKhatam].
 */
public class Khatam internal constructor(
    public val startedOn: LocalDate,
    public val target: LocalDate,
    public val end: AyahIndex,
    public val position: AyahIndex,
    public val markedOn: LocalDate,
    public val dayStart: AyahIndex,
) {
    /** The same plan, finishing on [newTarget]. Where the user has read to does not move. */
    public fun withTarget(newTarget: LocalDate): Khatam = Khatam(startedOn, newTarget, end, position, markedOn, dayStart)

    internal fun marked(on: LocalDate, newDayStart: AyahIndex, newPosition: AyahIndex): Khatam =
        Khatam(startedOn, target, end, newPosition, on, newDayStart)

    override fun equals(other: Any?): Boolean = other is Khatam && other.startedOn == startedOn && other.target == target &&
        other.end == end && other.position == position && other.markedOn == markedOn && other.dayStart == dayStart

    override fun hashCode(): Int = listOf(startedOn, target, end, position, markedOn, dayStart).hashCode()

    override fun toString(): String = "Khatam($startedOn..$target, at ${position.value}/${end.value}, marked $markedOn from ${dayStart.value})"
}

/** A new plan from the first ayah, made on [today]. [target] may be today; a date before it is refused by the screen. */
public fun startKhatam(today: LocalDate, target: LocalDate, end: AyahIndex): Khatam =
    Khatam(today, target, end, AyahIndex.START, today, AyahIndex.START)

public sealed interface KhatamRestore {
    public data class Restored(val khatam: Khatam) : KhatamRestore

    /** The stored row does not describe a plan (it is skipped, not shown wrong). */
    public data object Unreadable : KhatamRestore
}

/** Parse at the edge: a stored plan is used only if `0 <= dayStart <= position <= end` and the Quran is not empty. */
public fun restoreKhatam(
    startedOn: LocalDate,
    target: LocalDate,
    end: Int,
    position: Int,
    markedOn: LocalDate,
    dayStart: Int,
): KhatamRestore =
    if (end > 0 && dayStart in 0..position && position <= end) {
        KhatamRestore.Restored(Khatam(startedOn, target, AyahIndex(end), AyahIndex(position), markedOn, AyahIndex(dayStart)))
    } else {
        KhatamRestore.Unreadable
    }

/** What the plan says on one local date. Every case is said without blame: a portion, done, finished, or a new date. */
public sealed interface KhatamDay {
    /** Today's [portion], from where the day began. [resumeAt] is the first ayah not yet read in it. */
    public data class ToRead(val portion: AyahRange, val resumeAt: AyahIndex) : KhatamDay

    /** Today's [portion] is read. [more] is an optional next stretch of the same length, for anyone who wants to read on. */
    public data class Read(val portion: AyahRange, val more: AyahRange) : KhatamDay

    /** The whole Quran is read, on the target date, before it, or after it: the same message in every case. */
    public data object Complete : KhatamDay

    /** The chosen date is before today. No portion can be worked out until the user picks a new date. */
    public data class NewDateNeeded(val resumeAt: AyahIndex) : KhatamDay
}

/** Where [today] begins: a later date starts from where the user had read; the marked date, or an earlier one (travel), keeps its start. */
public fun startOfDay(khatam: Khatam, today: LocalDate): AyahIndex =
    if (today > khatam.markedOn) khatam.position else khatam.dayStart

/**
 * Today's portion, recomputed from scratch every day: what is left from the start of [today], shared out evenly over the
 * days from [today] to the target, both included, rounded up so the plan always ends by the target. A day without
 * reading changes nothing but the next portion's length; the plan never counts it. Pure: [today] is the place's local
 * date, worked out by the caller from the clock and the place's zone (INV-T3).
 */
public fun khatamToday(khatam: Khatam, today: LocalDate): KhatamDay {
    // A date before the last mark (the phone moved west across midnight, or its clock went back) is that marked day.
    val date = maxOf(today, khatam.markedOn)
    if (khatam.position >= khatam.end) return KhatamDay.Complete
    if (khatam.target < date) return KhatamDay.NewDateNeeded(khatam.position)
    val start = startOfDay(khatam, date)
    val days = ChronoUnit.DAYS.between(date, khatam.target) + 1
    val left = (khatam.end - start).toLong()
    val length = ((left + days - 1) / days).toInt()
    val portion = AyahRange(start, start + length)
    return if (khatam.position >= portion.end) {
        KhatamDay.Read(portion, AyahRange(khatam.position, minOf(khatam.end, khatam.position + length)))
    } else {
        KhatamDay.ToRead(portion, khatam.position)
    }
}

/** True when the plan has a portion waiting today: what the daily nudge may mention (ADR-24). Done, finished or a passed date say nothing. */
public fun portionWaits(khatam: Khatam, today: LocalDate): Boolean = khatamToday(khatam, today) is KhatamDay.ToRead

/**
 * The user read up to, not including, [through] on [today]. Never moves back and never past the end. On a date earlier
 * than the last mark (the phone moved west across midnight) the mark joins that day rather than starting a new one.
 */
public fun markRead(khatam: Khatam, today: LocalDate, through: AyahIndex): Khatam {
    val newDay = today > khatam.markedOn
    val dayStart = if (newDay) khatam.position else khatam.dayStart
    val position = maxOf(khatam.position, minOf(through, khatam.end))
    return khatam.marked(if (newDay) today else khatam.markedOn, dayStart, position)
}

/** Takes back what was marked on [today]. A day with no mark is left as it is. */
public fun undoToday(khatam: Khatam, today: LocalDate): Khatam =
    if (today > khatam.markedOn) khatam else khatam.marked(khatam.markedOn, khatam.dayStart, khatam.dayStart)

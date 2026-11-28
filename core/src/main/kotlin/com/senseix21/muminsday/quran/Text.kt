package com.senseix21.muminsday.quran

/**
 * The Quran text as a source file holds it (INV-QT1). Nothing here changes a character: a parse keeps every line of the
 * file, so [render] gives back the source's exact text, and every ayah's [Verse.text] is the exact substring after its
 * second `|`. The file format is Tanzil's "Text (with aya numbers)": `surah|ayah|text` lines and `#` comment lines
 * (`content/quran-SOURCES.md`).
 */
public sealed interface Line {
    public data class Verse(val ref: VerseRef, val text: String) : Line

    /** A comment, a blank line, anything that is not an ayah: kept as it is so nothing is lost. */
    public data class Other(val raw: String) : Line
}

/** A surah number, 1 to 114. */
@JvmInline
public value class SurahNumber private constructor(public val value: Int) : Comparable<SurahNumber> {
    override fun compareTo(other: SurahNumber): Int = value.compareTo(other.value)

    public companion object {
        public const val COUNT: Int = 114

        /** Null outside 1 to 114. */
        public fun of(value: Int): SurahNumber? = if (value in 1..COUNT) SurahNumber(value) else null
    }
}

/** Surah and ayah. Whether the ayah exists in that surah is a question for a [Quran], not for the pair. */
public data class VerseRef(val surah: SurahNumber, val ayah: Int) {
    override fun toString(): String = "${surah.value}:$ayah"
}

public enum class TextProblemKind {
    /** A line that is not a comment, not blank, and not `surah|ayah|text` with whole numbers. */
    Malformed,

    /** A surah number outside 1 to 114 or an ayah number below 1. */
    OutOfRange,

    /** Not the next ayah after the one before it (the file must be in order with no gap and no repeat). */
    OutOfOrder,

    /** An ayah with no text. */
    Empty,
}

/** [line] is 1-based, as an editor shows it. */
public data class TextProblem(val line: Int, val kind: TextProblemKind)

public sealed interface TextParse {
    public data class Parsed(val lines: List<Line>) : TextParse {
        public val verses: List<Line.Verse> get() = lines.filterIsInstance<Line.Verse>()
    }

    public data class Refused(val problems: List<TextProblem>) : TextParse
}

/**
 * Reads [source] line by line, splitting on `\n` only, so a `\r` stays part of its line and [render] is exact. Ayahs
 * must come in order: 1:1, 1:2, ... and each surah starts at ayah 1. Collects every problem.
 */
public fun parseText(source: String): TextParse {
    val lines = mutableListOf<Line>()
    val problems = mutableListOf<TextProblem>()
    var previous: VerseRef? = null
    source.split('\n').forEachIndexed { index, raw ->
        val number = index + 1
        if (raw.isBlank() || raw.startsWith("#")) {
            lines += Line.Other(raw)
            return@forEachIndexed
        }
        val first = raw.indexOf('|')
        val second = if (first < 0) -1 else raw.indexOf('|', first + 1)
        val surahValue = if (second < 0) null else raw.substring(0, first).toIntOrNull()
        val ayah = if (second < 0) null else raw.substring(first + 1, second).toIntOrNull()
        if (surahValue == null || ayah == null) {
            problems += TextProblem(number, TextProblemKind.Malformed)
            lines += Line.Other(raw)
            return@forEachIndexed
        }
        val surah = SurahNumber.of(surahValue)
        if (surah == null || ayah < 1) {
            problems += TextProblem(number, TextProblemKind.OutOfRange)
            lines += Line.Other(raw)
            return@forEachIndexed
        }
        val ref = VerseRef(surah, ayah)
        if (!follows(previous, ref)) problems += TextProblem(number, TextProblemKind.OutOfOrder)
        previous = ref
        val text = raw.substring(second + 1)
        if (text.isBlank()) problems += TextProblem(number, TextProblemKind.Empty)
        lines += Line.Verse(ref, text)
    }
    return if (problems.isEmpty()) TextParse.Parsed(lines) else TextParse.Refused(problems)
}

/** The source again, exactly: every line as it was read, joined by the `\n` it was split on. */
public fun render(lines: List<Line>): String = lines.joinToString("\n") { line ->
    when (line) {
        is Line.Verse -> "${line.ref.surah.value}|${line.ref.ayah}|${line.text}"
        is Line.Other -> line.raw
    }
}

/** The comment lines of a parse, in order: the source's own copyright block, to be shown and copied as it is. */
public fun notice(lines: List<Line>): String =
    lines.filterIsInstance<Line.Other>().map { it.raw }.filter { it.startsWith("#") }.joinToString("\n")

private fun follows(previous: VerseRef?, next: VerseRef): Boolean = when {
    previous == null -> next.surah.value == 1 && next.ayah == 1
    next.surah == previous.surah -> next.ayah == previous.ayah + 1
    else -> next.surah.value == previous.surah.value + 1 && next.ayah == 1
}

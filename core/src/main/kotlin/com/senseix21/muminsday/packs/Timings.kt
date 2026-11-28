package com.senseix21.muminsday.packs

import com.senseix21.muminsday.quran.SurahNumber
import com.senseix21.muminsday.quran.VerseRef
import java.time.Duration

/** When one ayah is heard in a recitation: from [start], up to but not including [end]. */
public data class AyahTiming(val ref: VerseRef, val start: Duration, val end: Duration)

public enum class TimingProblemKind { Malformed, OutOfRange, OutOfOrder, EmptyOrBackwards, Overlaps, NoAyahs }

public data class TimingProblem(val line: Int, val kind: TimingProblemKind)

public sealed interface TimingsParse {
    public data class Parsed(val timings: List<AyahTiming>) : TimingsParse

    public data class Refused(val problems: List<TimingProblem>) : TimingsParse
}

/**
 * The verse timings format of an audio pack (ADR-25), plain text, one ayah per line, `#` and blank lines ignored:
 *
 * `surah|ayah|start_ms|end_ms`
 *
 * Milliseconds from the start of the audio file. Ayahs in Quran order, each after the one before (a pack may hold one
 * surah, or a few ayahs), `end_ms > start_ms`, and no ayah starts before the previous one ends; gaps are allowed. Whether
 * each ayah exists in its surah is not checked here: the text is not needed to follow the audio.
 */
public fun parseTimings(text: String): TimingsParse {
    val timings = mutableListOf<AyahTiming>()
    val problems = mutableListOf<TimingProblem>()
    text.split('\n').forEachIndexed { index, raw ->
        val line = raw.removeSuffix("\r")
        if (line.isBlank() || line.startsWith("#")) return@forEachIndexed
        val number = index + 1
        val fields = line.split('|')
        val values = fields.map { field -> field.takeIf { it.isNotEmpty() && it.all { c -> c in '0'..'9' } }?.toLongOrNull() }
        if (fields.size != TIMING_LINE_FIELDS || values.any { it == null }) return@forEachIndexed run { problems += TimingProblem(number, TimingProblemKind.Malformed) }
        val (surahValue, ayah, start, end) = values.map { checkNotNull(it) }
        val surah = if (surahValue <= SurahNumber.COUNT && ayah in 1L..Int.MAX_VALUE.toLong()) SurahNumber.of(surahValue.toInt()) else null
        if (surah == null) return@forEachIndexed run { problems += TimingProblem(number, TimingProblemKind.OutOfRange) }
        val timing = AyahTiming(VerseRef(surah, ayah.toInt()), Duration.ofMillis(start), Duration.ofMillis(end))
        val previous = timings.lastOrNull()
        when {
            end <= start -> problems += TimingProblem(number, TimingProblemKind.EmptyOrBackwards)
            previous != null && !after(previous.ref, timing.ref) -> problems += TimingProblem(number, TimingProblemKind.OutOfOrder)
            previous != null && timing.start < previous.end -> problems += TimingProblem(number, TimingProblemKind.Overlaps)
        }
        timings += timing
    }
    if (timings.isEmpty() && problems.isEmpty()) problems += TimingProblem(0, TimingProblemKind.NoAyahs)
    return if (problems.isEmpty()) TimingsParse.Parsed(timings) else TimingsParse.Refused(problems)
}

public sealed interface AyahAt {
    public data class At(val ref: VerseRef) : AyahAt

    /** Before the first ayah, in a gap, or after the last: no ayah is shown as current. */
    public data object Between : AyahAt
}

/** The ayah being recited at [position] in the audio. [timings] are a [TimingsParse.Parsed] list: ordered, no overlap. */
public fun ayahAt(timings: List<AyahTiming>, position: Duration): AyahAt {
    var low = 0
    var high = timings.size - 1
    while (low <= high) {
        val middle = (low + high) ushr 1
        val timing = timings[middle]
        when {
            position < timing.start -> high = middle - 1
            position >= timing.end -> low = middle + 1
            else -> return AyahAt.At(timing.ref)
        }
    }
    return AyahAt.Between
}

private fun after(previous: VerseRef, next: VerseRef): Boolean =
    next.surah > previous.surah || (next.surah == previous.surah && next.ayah > previous.ayah)

private const val TIMING_LINE_FIELDS = 4

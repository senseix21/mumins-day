package com.senseix21.muminsday.khatam

import com.senseix21.muminsday.quran.JUZ_COUNT
import com.senseix21.muminsday.quran.SurahInfo
import com.senseix21.muminsday.quran.SurahNumber
import com.senseix21.muminsday.quran.VerseRef

/**
 * A place in the Quran counted in ayahs from its start (ADR-24): 0 is before 1:1, and [QuranLayout.end] is after the
 * last ayah. The khatam plan counts in ayahs because the bundled Tanzil metadata gives every surah's ayah count exactly;
 * a page or a juz is a printed edition's division, and an ayah count is the same in every edition of this text.
 */
@JvmInline
public value class AyahIndex(public val value: Int) : Comparable<AyahIndex> {
    override fun compareTo(other: AyahIndex): Int = value.compareTo(other.value)

    public operator fun plus(ayahs: Int): AyahIndex = AyahIndex(value + ayahs)

    public operator fun minus(other: AyahIndex): Int = value - other.value

    public companion object {
        public val START: AyahIndex = AyahIndex(0)
    }
}

/** The ayahs from [first] up to, not including, [end]. Never empty when a plan hands it out. */
public data class AyahRange(val first: AyahIndex, val end: AyahIndex) {
    public val ayahs: Int get() = end - first
}

/** A juz number, 1 to 30. */
@JvmInline
public value class JuzNumber(public val value: Int)

/**
 * The bundled Quran's shape, built from the verified metadata: how many ayahs each surah has and where each juz begins.
 * Turns an [AyahIndex] into the surah:ayah the reader opens at, and back. Construct with [buildLayout].
 */
public class QuranLayout internal constructor(private val counts: IntArray, private val juzStarts: IntArray) {
    private val firsts: IntArray = IntArray(counts.size).also { firsts ->
        var running = 0
        counts.forEachIndexed { index, count ->
            firsts[index] = running
            running += count
        }
    }

    /** After the last ayah: the whole Quran's ayah count (6236 for the bundled text). */
    public val end: AyahIndex = AyahIndex(counts.sum())

    /** The ayah at [index], which must be before [end]. */
    public fun refAt(index: AyahIndex): VerseRef {
        require(index >= AyahIndex.START && index < end) { "no ayah at ${index.value}" }
        var surah = firsts.size - 1
        while (firsts[surah] > index.value) surah--
        // SurahNumber.of cannot fail here: surah is 0..113.
        return VerseRef(checkNotNull(SurahNumber.of(surah + 1)), index.value - firsts[surah] + 1)
    }

    /** The last ayah of a non-empty [range]. */
    public fun lastOf(range: AyahRange): VerseRef = refAt(AyahIndex(range.end.value - 1))

    /** Null when [ref] is not an ayah of this text. */
    public fun indexOf(ref: VerseRef): AyahIndex? =
        if (ref.ayah in 1..counts[ref.surah.value - 1]) AyahIndex(firsts[ref.surah.value - 1] + ref.ayah - 1) else null

    /** The juz [index] is in. */
    public fun juzOf(index: AyahIndex): JuzNumber {
        var juz = juzStarts.size - 1
        while (juzStarts[juz] > index.value) juz--
        return JuzNumber(juz + 1)
    }
}

public sealed interface LayoutCheck {
    public data class Built(val layout: QuranLayout) : LayoutCheck

    public data class Refused(val reason: String) : LayoutCheck
}

/** [surahs] from `parseMetadata`, [juzStarts] from `parseJuzStarts`: every juz must start at an ayah that exists. */
public fun buildLayout(surahs: List<SurahInfo>, juzStarts: List<VerseRef>): LayoutCheck {
    if (surahs.size != SurahNumber.COUNT) return LayoutCheck.Refused("${surahs.size} surahs")
    if (juzStarts.size != JUZ_COUNT) return LayoutCheck.Refused("${juzStarts.size} juz")
    val layout = QuranLayout(surahs.map { it.ayahCount }.toIntArray(), IntArray(0))
    val starts = juzStarts.map { layout.indexOf(it) ?: return LayoutCheck.Refused("juz starts at $it, which is not an ayah") }
    if (starts.first() != AyahIndex.START || starts.zipWithNext().any { (a, b) -> b <= a }) return LayoutCheck.Refused("juz starts out of order")
    return LayoutCheck.Built(QuranLayout(surahs.map { it.ayahCount }.toIntArray(), starts.map { it.value }.toIntArray()))
}

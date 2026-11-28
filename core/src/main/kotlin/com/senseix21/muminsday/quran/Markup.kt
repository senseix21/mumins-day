package com.senseix21.muminsday.quran

/**
 * QuranEnc's footnotes carry `<b>…</b>` around some headings (G65). Showing those as bold is rendering the source's
 * own markup, not changing its words: [shown] is the source with exactly the tag characters taken out, and every
 * other character stays, in order (INV-QT1). Founder's yes, 2026-11-28.
 */
public data class Marked(val shown: String, val bold: List<IntRange>)

private const val OPEN = "<b>"
private const val CLOSE = "</b>"

/**
 * Pairs each `<b>` with the next `</b>`. Anything else (an unpaired, nested or stray tag, any other markup) means the
 * markup is not what we understand, so the text is returned exactly as given, with nothing bold.
 */
public fun boldMarkup(source: String): Marked {
    val shown = StringBuilder(source.length)
    val bold = mutableListOf<IntRange>()
    var index = 0
    while (index < source.length) {
        val open = source.indexOf(OPEN, index)
        val strayClose = source.indexOf(CLOSE, index)
        if (open < 0) {
            if (strayClose >= 0) return Marked(source, emptyList())
            shown.append(source, index, source.length)
            break
        }
        if (strayClose in 0 until open) return Marked(source, emptyList())
        val close = source.indexOf(CLOSE, open + OPEN.length)
        if (close < 0) return Marked(source, emptyList())
        if (source.indexOf(OPEN, open + OPEN.length) in (open + OPEN.length) until close) return Marked(source, emptyList())
        shown.append(source, index, open)
        val start = shown.length
        shown.append(source, open + OPEN.length, close)
        if (shown.length > start) bold += start until shown.length
        index = close + CLOSE.length
    }
    return Marked(shown.toString(), bold)
}

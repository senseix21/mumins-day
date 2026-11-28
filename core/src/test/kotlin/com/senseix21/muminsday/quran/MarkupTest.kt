package com.senseix21.muminsday.quran

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MarkupTest {
    @Test
    fun boldPairsBecomeRangesAndOnlyTheTagsGo() {
        val marked = boldMarkup("<b> সূরার নাম: </b>\nপাঠ <b>দুই</b> শেষ")
        assertEquals(" সূরার নাম: \nপাঠ দুই শেষ", marked.shown)
        assertEquals(listOf(" সূরার নাম: ", "দুই"), marked.bold.map { marked.shown.substring(it) })
    }

    @Test
    fun textWithoutMarkupIsUnchanged() {
        val text = "রহমান, রহীম [১] আল্লাহ্‌র নামে [২]"
        assertEquals(Marked(text, emptyList()), boldMarkup(text))
    }

    @Test
    fun markupWeDoNotUnderstandIsShownAsIs() {
        for (odd in listOf("a <b> b", "a </b> b", "</b>a<b>", "<b>a<b>b</b></b>", "<b>a</b> c </b>")) {
            assertEquals(Marked(odd, emptyList()), boldMarkup(odd), odd)
        }
    }

    /** INV-QT1: removing the tags is the only change; putting them back at the ranges gives the source again. */
    @Test
    fun onlyTagCharactersAreRemovedProperty() {
        val random = Random(20261128)
        val pieces = listOf("ক", "খ ", "[১]", "\n", "<", ">", "b", "/", "<b>", "</b>")
        repeat(2000) {
            val source = buildString { repeat(random.nextInt(0, 12)) { append(pieces[random.nextInt(pieces.size)]) } }
            val marked = boldMarkup(source)
            if (marked.bold.isEmpty()) {
                assertTrue(marked.shown == source || marked.shown == source.replace("<b></b>", ""), source)
            } else {
                val rebuilt = StringBuilder(marked.shown)
                marked.bold.asReversed().forEach { range ->
                    rebuilt.insert(range.last + 1, "</b>")
                    rebuilt.insert(range.first, "<b>")
                }
                assertEquals(source.replace("<b></b>", ""), rebuilt.toString(), source)
            }
        }
    }
}

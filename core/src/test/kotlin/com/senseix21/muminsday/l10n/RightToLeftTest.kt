package com.senseix21.muminsday.l10n

import java.text.Bidi
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** INV-L3 for whole Arabic texts placed in a Bengali or English line. Arabic is written as escapes, never as words. */
class RightToLeftTest {

    // Arabic letters only, not a word: U+0628 U+062A U+062B.
    private val arabicLetters = "بتث"

    @Test
    fun INV_L3_the_text_is_wrapped_in_one_right_to_left_isolate_and_not_touched() {
        val random = Random(2067)
        repeat(500) {
            val inner = buildString { repeat(random.nextInt(0, 30)) { append(random.nextInt(0x20, 0x700).toChar()) } }
            val wrapped = rightToLeft(inner)
            assertEquals(RIGHT_TO_LEFT_ISOLATE, wrapped.first())
            assertEquals(POP_DIRECTIONAL_ISOLATE, wrapped.last())
            assertEquals(inner, wrapped.substring(1, wrapped.length - 1))
        }
    }

    @Test
    fun INV_L3_a_text_that_starts_with_latin_or_digits_is_still_laid_out_right_to_left() {
        // A fake record such as "TEST DUA 1" has no Arabic letter at all; the isolate still makes its paragraph RTL.
        for (inner in listOf("TEST 1 $arabicLetters", "12 $arabicLetters", arabicLetters)) {
            val wrapped = "x " + rightToLeft(inner)
            val bidi = Bidi(wrapped, Bidi.DIRECTION_LEFT_TO_RIGHT)
            // Inside a right-to-left isolate every character is at level 1 (Arabic) or 2 (Latin or digits within RTL),
            // never at the paragraph's level 0; without the isolate "TEST" would be at level 0.
            for (index in 3 until wrapped.length - 1) {
                assertTrue(bidi.getLevelAt(index) >= 1, "level ${bidi.getLevelAt(index)} at $index for \"$inner\"")
            }
            assertEquals(0, Bidi("x $inner", Bidi.DIRECTION_LEFT_TO_RIGHT).getLevelAt(0))
        }
    }

    @Test
    fun INV_L3_digits_and_punctuation_around_the_isolate_keep_their_left_to_right_order() {
        val line = "(১) " + rightToLeft(arabicLetters) + " ২:৩"
        val bidi = Bidi(line, Bidi.DIRECTION_LEFT_TO_RIGHT)
        // Everything outside the isolate stays at the paragraph's level 0.
        for (index in line.indices) {
            val inside = index > line.indexOf(RIGHT_TO_LEFT_ISOLATE) && index < line.indexOf(POP_DIRECTIONAL_ISOLATE)
            if (!inside && line[index] != RIGHT_TO_LEFT_ISOLATE && line[index] != POP_DIRECTIONAL_ISOLATE) {
                assertEquals(0, bidi.getLevelAt(index), "index $index '${line[index]}'")
            }
        }
    }
}

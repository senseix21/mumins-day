package com.senseix21.muminsday.l10n

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BengaliDigitsTest {

    @Test
    fun T_UNIT_9_all_ten_digits_map() {
        val bengali = "০১২৩৪৫৬৭৮৯"
        for ((index, expected) in bengali.withIndex()) {
            assertEquals(expected.toString(), toBengaliDigits(index.toString()), "digit $index")
            assertEquals('০' + index, expected, "U+09E6 + $index")
        }
        assertEquals(bengali, toBengaliDigits("0123456789"))
        assertEquals("৯", toBengaliDigits("9"), "upstream's loop skipped 9")
    }

    @Test
    fun T_UNIT_9_other_characters_are_untouched() {
        assertEquals("৫:৩০ am, ১২/০৩ - x", toBengaliDigits("5:30 am, 12/03 - x"))
        assertEquals("", toBengaliDigits(""))
        // Digits that are already Bengali or are Arabic-Indic stay as they are.
        assertEquals("০১২৩৪৫৬৭৮৯", toBengaliDigits("০১২৩৪৫৬৭৮৯"))
        assertEquals("٠١٢٣٤٥٦٧٨٩ ۰۱۲", toBengaliDigits("٠١٢٣٤٥٦٧٨٩ ۰۱۲"))
        assertEquals("😀৪😀", toBengaliDigits("😀4😀"))
    }

    @Test
    fun T_UNIT_9_property_over_random_strings() {
        val pool = ("0123456789" + "০১২৩৪৫৬৭৮৯" + "٠١٢٣٤٥٦٧٨٩" + "abcXYZ :/-.,\n" + "ঢাকা" + "😀🇧🇩").toList()
        val random = Random(9)
        repeat(3000) {
            val text = String(CharArray(random.nextInt(0, 40)) { pool.random(random) })
            val mapped = toBengaliDigits(text)
            assertEquals(text.length, mapped.length, "length: $text")
            assertFalse(mapped.any { it in '0'..'9' }, "ASCII digit left in $mapped")
            for ((before, after) in text.zip(mapped)) {
                if (before in '0'..'9') {
                    assertEquals('০' + (before - '0'), after, "digit in $text")
                } else {
                    assertEquals(before, after, "non-digit changed in $text")
                }
            }
            assertTrue(toBengaliDigits(mapped) == mapped, "idempotent: $text")
        }
    }
}

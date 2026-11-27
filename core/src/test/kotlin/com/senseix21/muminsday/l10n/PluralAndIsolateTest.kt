package com.senseix21.muminsday.l10n

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PluralAndIsolateTest {

    @Test
    fun plural_forms() {
        assertEquals(PluralForm.One, pluralForm(Language.Bengali, 0))
        assertEquals(PluralForm.One, pluralForm(Language.Bengali, 1))
        assertEquals(PluralForm.Other, pluralForm(Language.Bengali, 2))
        assertEquals(PluralForm.Other, pluralForm(Language.English, 0))
        assertEquals(PluralForm.One, pluralForm(Language.English, 1))
        assertEquals(PluralForm.One, pluralForm(Language.English, -1))
        assertEquals(PluralForm.Other, pluralForm(Language.English, Int.MIN_VALUE))
        assertEquals("one", choosePlural(Language.English, 1, "one", "other"))
        assertEquals("other", choosePlural(Language.English, 5, "one", "other"))
    }

    @Test
    fun isolating_whole_text() {
        assertEquals("⁨x⁩", isolate("x"))
    }

    // Arabic written as escapes so the source holds no bidirectional text: U+0627 U+0644 U+0644 U+0647 and a space and U+0623.
    private val arabicWord = "الله"
    private val arabicTwo = "$arabicWord أكبر"

    @Test
    fun arabic_runs_are_wrapped_and_neighbours_are_not() {
        assertEquals("a ⁨$arabicWord⁩, 5 b", isolateArabicRuns("a $arabicWord, 5 b"))
        assertEquals("⁨$arabicTwo⁩ x", isolateArabicRuns("$arabicTwo x"))
        assertEquals("no arabic, 12:30", isolateArabicRuns("no arabic, 12:30"))
        assertEquals("", isolateArabicRuns(""))
        // Two runs separated by Latin text are two isolates.
        assertEquals("⁨$arabicWord⁩ x ⁨$arabicWord⁩", isolateArabicRuns("$arabicWord x $arabicWord"))
    }

    @Test
    fun property_isolates_are_balanced_and_nothing_else_changes() {
        val pool = (
            "abc XYZ 0123456789 .,:;!-" + "সকাল" + "اللهأكبر ًِ" + "\n\t"
            ).toList()
        val random = Random(31)
        repeat(5000) {
            val text = String(CharArray(random.nextInt(0, 40)) { pool.random(random) })
            val wrapped = isolateArabicRuns(text)
            var depth = 0
            for (character in wrapped) {
                when (character) {
                    FIRST_STRONG_ISOLATE -> depth++
                    POP_DIRECTIONAL_ISOLATE -> depth--
                }
                assertTrue(depth in 0..1, "unbalanced or nested in '$wrapped' from '$text'")
            }
            assertEquals(0, depth, "unclosed in '$wrapped'")
            assertEquals(text, wrapped.filter { it != FIRST_STRONG_ISOLATE && it != POP_DIRECTIONAL_ISOLATE }, "text changed")
            assertFalse(wrapped.contains("$FIRST_STRONG_ISOLATE$POP_DIRECTIONAL_ISOLATE"), "empty isolate in '$wrapped'")
            // An isolate never starts or ends on a space: spaces around a run stay outside it.
            assertFalse(Regex("⁨\\s|\\s⁩").containsMatchIn(wrapped), "padded isolate in '$wrapped'")
        }
    }
}

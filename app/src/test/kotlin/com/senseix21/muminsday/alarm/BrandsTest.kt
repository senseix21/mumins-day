package com.senseix21.muminsday.alarm

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BrandsTest {
    @Test
    fun `every brand that needs steps resolves to an array`() {
        BRANDS_THAT_NEED_STEPS.forEach { maker ->
            assertNotNull(brandStepsArray(maker), maker)
            assertTrue(brandNeedsSteps(maker), maker)
        }
    }

    @Test
    fun `a brand resolves to an array only if it is in the set`() {
        listOf("google", "nokia", "hmd global", "honor", "").forEach { maker ->
            assertNull(brandStepsArray(maker), maker)
            assertFalse(brandNeedsSteps(maker), maker)
        }
    }

    @Test
    fun `the manufacturer string is trimmed and lowercased`() {
        assertTrue(brandNeedsSteps("Xiaomi"))
        assertTrue(brandNeedsSteps(" realme "))
        assertTrue(brandNeedsSteps("SAMSUNG"))
        assertEquals(brandStepsArray("Tecno"), brandStepsArray("infinix"))
        assertEquals(brandStepsArray("tecno"), brandStepsArray("ITEL"))
    }

    @Test
    fun `only the brands without a page of their own show the Tecno note`() {
        assertTrue(showsTecnoStepsAsNote("Infinix"))
        assertTrue(showsTecnoStepsAsNote("itel"))
        assertFalse(showsTecnoStepsAsNote("tecno"))
        assertFalse(showsTecnoStepsAsNote("samsung"))
    }
}

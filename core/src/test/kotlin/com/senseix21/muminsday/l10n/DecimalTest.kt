package com.senseix21.muminsday.l10n

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DecimalTest {
    @Test
    fun `typed numbers accept Bengali and Latin digits and refuse everything else`() {
        assertEquals(BigDecimal("12.5"), parseTypedNumber("১২.৫"))
        assertEquals(BigDecimal("12.5"), parseTypedNumber(" 12.5 "))
        assertEquals(BigDecimal("0"), parseTypedNumber("০"))
        for (bad in listOf("", " ", "-1", "1,5", "1.", ".5", "1e3", "abc", "1 2", "১২a")) assertNull(parseTypedNumber(bad), bad)
    }

    @Test
    fun `the rounding direction is the caller's and Bengali shows Bengali digits`() {
        val value = BigDecimal("1700.001")
        assertEquals("1700.01", decimalText(value, 2, RoundingMode.CEILING, Language.English))
        assertEquals("1700", decimalText(value, 2, RoundingMode.FLOOR, Language.English))
        assertEquals("১৭০০.০১", decimalText(value, 2, RoundingMode.CEILING, Language.Bengali))
        assertEquals("1000", decimalText(BigDecimal("1E+3"), 2, RoundingMode.CEILING, Language.English))
    }
}

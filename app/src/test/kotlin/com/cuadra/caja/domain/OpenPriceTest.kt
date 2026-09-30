package com.cuadra.caja.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenPriceTest {
    private fun type(start: OpenPriceEntry, keys: String, decimals: Int = 2) = keys.fold(start) { e, c -> if (c == '.') e.dot(decimals) else e.digit(c, decimals) }

    @Test fun typingBuildsThePrice() {
        val e = type(OpenPriceEntry(), "45.5")
        assertEquals("45.5", e.text); assertEquals(4550L, e.minor(2))
    }

    @Test fun emptyOrZeroIsNotAllowed() {
        assertNull(OpenPriceEntry().minor(2))
        assertNull(type(OpenPriceEntry(), "0").minor(2))
        assertNull(type(OpenPriceEntry(), "0.00").minor(2))
        assertEquals(1L, type(OpenPriceEntry(), "0.01").minor(2))
    }

    @Test fun decimalsFollowTheCurrency() {
        assertEquals("12.34", type(OpenPriceEntry(), "12.345").text)
        assertEquals("150", OpenPriceEntry("150").dot(0).text)   // sin decimales no hay separador
        assertEquals(150L, type(OpenPriceEntry(), "150", decimals = 0).minor(0))
        assertEquals(1234567L, type(OpenPriceEntry(), "1234.567", decimals = 3).minor(3))
    }

    @Test fun leadingZerosAndSecondDotAreIgnored() {
        assertEquals("7", type(OpenPriceEntry(), "007").text)
        assertEquals("0.5", type(OpenPriceEntry(), ".5").text)
        assertEquals("1.5", type(OpenPriceEntry(), "1.5.").text)
    }

    @Test fun suggestedPriceStartsFilledAndTheFirstKeyReplacesIt() {
        val s = OpenPriceEntry.start(5000, 2)
        assertEquals("50", s.text); assertEquals(5000L, s.minor(2))   // se puede agregar tal cual
        assertEquals("4", s.digit('4', 2).text)
        assertEquals("45", s.digit('4', 2).digit('5', 2).text)
        assertEquals("0.", s.dot(2).text)
    }

    @Test fun backspaceEditsTheSuggestedPrice() {
        val s = OpenPriceEntry.start(4550, 2)
        assertEquals("45.5", s.text)
        assertEquals("45.", s.backspace().text)
        assertEquals("45.7", s.backspace().digit('7', 2).text)   // ya no es «fresco»: la tecla agrega
        assertEquals("", OpenPriceEntry("4").backspace().text)
    }

    @Test fun noSuggestionStartsEmpty() {
        assertEquals("", OpenPriceEntry.start(0, 2).text)
        assertTrue(!OpenPriceEntry.start(0, 2).fresh)
    }

    @Test fun hugeAmountsDoNotOverflow() {
        val e = type(OpenPriceEntry(), "9".repeat(30))
        assertEquals(CashTender.MAX_INTEGER_DIGITS, e.text.length)
        assertEquals(99_999_999_999_900L, e.minor(2))
    }
}

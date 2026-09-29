package com.cuadra.caja.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AmountEntryTest {
    /** Teclea una cadena: dígitos, '.' punto, 'x' multiplicar, '<' borrar. */
    private fun type(text: String, decimals: Int = 2): AmountEntry {
        var e = AmountEntry()
        for (c in text) e = when (c) {
            '.' -> e.dot(decimals)
            'x' -> e.times()
            '<' -> e.backspace()
            else -> e.digit(c, decimals)
        }
        return e
    }

    @Test fun aPlainAmountIsThePriceOfOneUnit() {
        assertEquals(EnteredAmount(1000, 8500), type("85").result(2))
        assertEquals(EnteredAmount(1000, 8550), type("85.5").result(2))
        assertEquals("85", type("85").display)
    }

    @Test fun multiplierIsTypedAsQuantityTimesPrice() {
        val e = type("3x25")
        assertEquals("3 × 25", e.display)
        assertEquals(EnteredAmount(3000, 2500), e.result(2))
        assertEquals(7500L, e.totalMinor(2))
    }

    @Test fun fractionalQuantitiesForWeight() {
        // 0.75 lb × C$ 90 = C$ 67.50
        val e = type("0.75x90")
        assertEquals(EnteredAmount(750, 9000), e.result(2))
        assertEquals(6750L, e.totalMinor(2))
    }

    @Test fun thePriceOnlyAllowsTheDecimalsOfTheCurrency() {
        assertEquals("85.55", type("2x85.559", 2).price)
        assertEquals("85", type("2x85.", 0).price)             // sin decimales: el punto se ignora
        assertEquals("85.5", type("2x85.5", 1).price)
    }

    @Test fun aPlainAmountWithTooManyDecimalsForTheCurrencyIsNotValid() {
        assertEquals("85.559", type("85.5599").display)         // hasta 3 por si era una cantidad
        assertNull(type("85.559").result(2))
        assertNull(type("85.5").result(0))
    }

    @Test fun noLeadingZerosButDecimalsStartWithZero() {
        assertEquals("7", type("007").display)
        assertEquals("0.5", type(".5").display)
        assertEquals("0", AmountEntry().display)
    }

    @Test fun backspaceGoesBackThroughThePriceThenTheMultiplierThenTheQuantity() {
        val e = type("3x2")
        assertEquals("3 × 0", e.backspace().display)
        assertTrue(e.backspace().multiplying)
        assertEquals("3", e.backspace().backspace().display)
        assertFalse(e.backspace().backspace().multiplying)
        assertTrue(e.backspace().backspace().backspace().isEmpty)
    }

    @Test fun multiplierNeedsAValidQuantityFirst() {
        assertFalse(type("x").multiplying)
        assertFalse(type("0x").multiplying)
        assertFalse(type("3.x").multiplying)
        assertTrue(type("3x").multiplying)
        assertFalse(type("3x").times().let { it.copy(price = "5") }.times().price.isEmpty())   // no se puede multiplicar dos veces
    }

    @Test fun incompleteOrZeroEntriesHaveNoResult() {
        assertNull(AmountEntry().result(2))
        assertNull(type("0").result(2))
        assertNull(type("3x").result(2))
        assertNull(type("3x0").result(2))
    }

    @Test fun zeroCannotBeMultipliedSoItJustBecomesTheTypedNumber() {
        assertEquals("25", type("0x25").display)
    }

    @Test fun veryLongNumbersAreCapped() {
        assertEquals(12, type("9".repeat(30)).quantity.length)
    }
}

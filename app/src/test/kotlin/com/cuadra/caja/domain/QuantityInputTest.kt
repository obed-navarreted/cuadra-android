package com.cuadra.caja.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class QuantityInputTest {
    private fun set(milli: Long) = QuantityEdit.Set(milli)

    @Test fun wholeNumbersParse() {
        assertEquals(set(15_000), QuantityInput.parse("15", allowDecimals = false))
        assertEquals(set(1_000), QuantityInput.parse(" 1 ", allowDecimals = false))
        assertEquals(set(100_000_000), QuantityInput.parse("100000", allowDecimals = false))
    }

    @Test fun decimalsParseForWeighedLinesWithDotOrComma() {
        assertEquals(set(500), QuantityInput.parse("0.5", true))
        assertEquals(set(750), QuantityInput.parse("0,75", true))
        assertEquals(set(500), QuantityInput.parse(".5", true))
        assertEquals(set(2_250), QuantityInput.parse("2.25", true))
        assertEquals(set(1_234), QuantityInput.parse("1.234", true))
        assertEquals(set(2_000), QuantityInput.parse("2.0", true))
    }

    @Test fun moreThanThreeDecimalsIsInvalid() {
        assertEquals(QuantityEdit.Invalid, QuantityInput.parse("1.2345", true))
    }

    @Test fun decimalsAreRejectedOnUnitLinesButTrailingZerosAreFine() {
        assertEquals(QuantityEdit.Invalid, QuantityInput.parse("1.5", false))
        assertEquals(set(2_000), QuantityInput.parse("2.0", false))
    }

    @Test fun zeroMeansRemoveTheLine() {
        assertEquals(QuantityEdit.Remove, QuantityInput.parse("0", false))
        assertEquals(QuantityEdit.Remove, QuantityInput.parse("0.0", true))
        assertEquals(QuantityEdit.Remove, QuantityInput.parse("000", false))
        assertEquals(QuantityEdit.Remove, QuantityInput.parse("0,000", true))
    }

    @Test fun garbageIsInvalid() {
        for (t in listOf("", " ", "abc", "-3", "1e3", "1..2", "--", ".", "1,2,3", "12a", "100001", "99999999999999999999")) {
            assertEquals("«$t»", QuantityEdit.Invalid, QuantityInput.parse(t, true))
        }
    }

    @Test fun sanitizeKeepsDigitsAndOneSeparator() {
        assertEquals("15", QuantityInput.sanitize("1a5", false))
        assertEquals("1.5", QuantityInput.sanitize("1,5", true))
        assertEquals("15", QuantityInput.sanitize("1.5", false))
        assertEquals("1.25", QuantityInput.sanitize("1.2.5", true))
        assertEquals("1.234", QuantityInput.sanitize("1.23456", true))
        assertEquals("1234567890", QuantityInput.sanitize("123456789012", false))
    }

    @Test fun prefillIsReadable() {
        assertEquals("15", QuantityInput.toText(15_000))
        assertEquals("0.75", QuantityInput.toText(750))
        assertEquals("1.5", QuantityInput.toText(1_500))
    }

    @Test fun decimalsAllowedOnlyWhereTheyMakeSense() {
        val unit = CartLine("a", "p", null, "Refresco", null, 100, null, 2000)
        assertEquals(false, unit.allowsDecimals)
        assertEquals(true, unit.copy(byWeight = true).allowsDecimals)
        assertEquals(true, unit.copy(quantityMilli = 750).allowsDecimals)
        assertEquals(true, unit.copy(productId = null).allowsDecimals)
    }
}

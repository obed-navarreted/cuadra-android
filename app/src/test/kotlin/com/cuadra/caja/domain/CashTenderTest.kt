package com.cuadra.caja.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CashTenderTest {
    @Test fun typingStepByStep() {
        assertEquals(500L, CashTender.parseMinor("5", 2))
        assertEquals(500L, CashTender.parseMinor("5.", 2))
        assertEquals(550L, CashTender.parseMinor("5.5", 2))
        assertEquals(550L, CashTender.parseMinor("5.50", 2))
        assertEquals(505L, CashTender.parseMinor("5.05", 2))
        assertEquals(50L, CashTender.parseMinor(".5", 2))
    }

    @Test fun emptyIsNotAnAmountAndLoneDotBecomesZero() {
        assertNull(CashTender.parseMinor("", 2))
        assertEquals("0.", CashTender.sanitize(".", 2))
        assertEquals(0L, CashTender.parseMinor(".", 2))
        assertNull(CashTender.parseMinor("abc", 2))
        assertEquals(0L, CashTender.parseMinor("0", 2))
    }

    @Test fun extraDecimalsAreIgnoredWhileTyping() {
        assertEquals("5.55", CashTender.sanitize("5.555", 2))
        assertEquals("5.5", CashTender.sanitize("5.5", 2))
        assertEquals(555L, CashTender.parseMinor("5.559", 2))
    }

    @Test fun leadingZerosAreDropped() {
        assertEquals("7", CashTender.sanitize("007", 2))
        assertEquals("0", CashTender.sanitize("000", 2))
        assertEquals("0.5", CashTender.sanitize("00.5", 2))
        assertEquals("0.", CashTender.sanitize("0.", 2))
        assertEquals("0.5", CashTender.sanitize(".5", 2))
    }

    @Test fun commaCountsAsDotAndOnlyOneSeparator() {
        assertEquals("85.5", CashTender.sanitize("85,5", 2))
        assertEquals("1.25", CashTender.sanitize("1.2.5", 2))
        assertEquals("12", CashTender.sanitize("1a2-", 2))
    }

    @Test fun zeroDecimalCurrenciesHaveNoSeparator() {
        assertEquals("1500", CashTender.sanitize("1500.75", 0))
        assertEquals("1500", CashTender.sanitize("1500,7", 0))
        assertEquals(1500L, CashTender.parseMinor("1500", 0))
        assertEquals(1500L, CashTender.parseMinor("1500.", 0))
    }

    @Test fun hugeValuesDoNotOverflow() {
        val huge = "9".repeat(40)
        assertEquals(CashTender.MAX_INTEGER_DIGITS, CashTender.sanitize(huge, 2).length)
        assertEquals(999_999_999_999_00L, CashTender.parseMinor(huge, 2))
        assertNotNullOrNull(CashTender.parseMinor(huge + ".99", 2))
    }

    private fun assertNotNullOrNull(v: Long?) = assertTrue(v == null || v > 0)

    @Test fun sanitizeIsIdempotent() {
        listOf("5.", "0.5", "1500", "12.65", "").forEach { assertEquals(it, CashTender.sanitize(CashTender.sanitize(it, 2), 2)) }
    }

    @Test fun toTextRoundTrips() {
        assertEquals("12.65", CashTender.toText(1265, 2))
        assertEquals("100", CashTender.toText(10000, 2))
        assertEquals("0.05", CashTender.toText(5, 2))
        assertEquals("5.5", CashTender.toText(550, 2))
        assertEquals("0", CashTender.toText(0, 2))
        assertEquals("2000", CashTender.toText(2000, 0))
        listOf(1L, 99L, 100L, 1265L, 123456789L).forEach { assertEquals(it, CashTender.parseMinor(CashTender.toText(it, 2), 2)) }
    }

    @Test fun changeIsExactToTheMinorUnit() {
        // 100.00 − 87.35 = 12.65 (con doubles saldría 12.650000000000006).
        val r = CashTender.change(8735, CashTender.parseMinor("100.00", 2)!!)
        assertEquals(1265L, r.changeMinor)
        assertEquals(0L, r.missingMinor)
        assertTrue(r.enough)
    }

    @Test fun receivedExactlyEqualHasNoChange() {
        val r = CashTender.change(8735, 8735)
        assertEquals(0L, r.changeMinor)
        assertTrue(r.enough)
    }

    @Test fun receivedLessReportsWhatIsMissing() {
        val r = CashTender.change(8735, CashTender.parseMinor("80", 2)!!)
        assertEquals(735L, r.missingMinor)
        assertEquals(0L, r.changeMinor)
        assertFalse(r.enough)
    }

    @Test fun suggestionsForZeroDecimalCurrenciesUseWholeBills() {
        assertEquals(5000L, CashSuggestions.forAmount(4500, "CLP", 0).first())
        assertTrue(CashSuggestions.forAmount(87_35, "NIO", 2).contains(100_00))
    }
}

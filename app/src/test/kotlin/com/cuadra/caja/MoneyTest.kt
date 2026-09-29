package com.cuadra.caja

import com.cuadra.caja.core.model.Currency
import com.cuadra.caja.core.model.Money
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoneyTest {
    private val nio = Currency.of("NIO")
    private val esNi = Locale.forLanguageTag("es-NI")
    private val enUs = Locale.forLanguageTag("en-US")

    @Test fun formatsWithSymbolAndGrouping() {
        assertEquals("C$ 342.50", Money(34250).format(nio, esNi))
        assertEquals("C$ 1,234.50", Money(123450).format(nio, esNi))
    }

    @Test fun separatorsFollowTheBusinessCountryNotTheUserLanguage() {
        // El mismo negocio nicaragüense se ve igual aunque la persona use la app en inglés.
        assertEquals(Money(123450).format(nio, esNi), Money(123450).format(nio, enUs))
    }

    @Test fun zeroDecimalCurrencyHasNoFraction() {
        assertEquals("$ 1,500", Money(1500).format(Currency.of("CLP"), Locale.forLanguageTag("es-CL")).replace('.', ','))
    }

    @Test fun unknownCurrencyFallsBackToItsCode() {
        assertEquals("XYZ 1.00", Money(100).format(Currency.of("xyz"), enUs))
    }

    @Test fun parsesKeyboardInputToMinorUnits() {
        assertEquals(Money(8500), Money.parse("85", 2))
        assertEquals(Money(8550), Money.parse("85.5", 2))
        assertEquals(Money(8550), Money.parse("85,50", 2))
        assertEquals(Money(50), Money.parse(".5", 2))
        assertEquals(Money(85), Money.parse("85", 0))
    }

    @Test fun rejectsInvalidOrTooPreciseInput() {
        assertNull(Money.parse("", 2))
        assertNull(Money.parse("abc", 2))
        assertNull(Money.parse("1.234", 2))
        assertNull(Money.parse("-5", 2))
        assertNull(Money.parse("1.5", 0))
        assertNull(Money.parse("99999999999999999999", 2))
    }

    @Test fun arithmeticThrowsOnOverflowInsteadOfWrapping() {
        var threw = false
        try { Money(Long.MAX_VALUE) + Money(1) } catch (_: ArithmeticException) { threw = true }
        assertEquals(true, threw)
        assertEquals(Money(150), Money(100) + Money(50))
    }
}

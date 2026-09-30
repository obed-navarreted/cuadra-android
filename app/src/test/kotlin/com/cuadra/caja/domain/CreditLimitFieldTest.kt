package com.cuadra.caja.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Límite de crédito en el editor del cliente: abrir y guardar sin cambios lo conserva (antes C$ 500 pasaba a C$ 50 000). */
class CreditLimitFieldTest {
    @Test fun editingAndSavingWithoutChangesKeepsTheLimit() {
        for (minor in listOf(50_000L, 1L, 125_050L, 0L, 99_999_999_999L)) {
            assertEquals(minor, CreditLimitField.parse(CreditLimitField.toText(minor, 2), 2))
        }
        assertEquals(1_000L, CreditLimitField.parse(CreditLimitField.toText(1_000L, 0), 0))   // moneda sin centavos
    }

    @Test fun theFieldShowsANormalAmount() {
        assertEquals("500", CreditLimitField.toText(50_000L, 2))
        assertEquals("1250.5", CreditLimitField.toText(125_050L, 2))
        assertEquals("", CreditLimitField.toText(null, 2))
        assertEquals(50_000L, CreditLimitField.parse("500", 2))
        assertEquals(50_050L, CreditLimitField.parse("500,50", 2))
    }

    @Test fun emptyOrInvalidMeansNoLimit() {
        assertNull(CreditLimitField.parse("", 2))
        assertNull(CreditLimitField.parse("  ", 2))
        assertNull(CreditLimitField.parse("abc", 2))
    }
}

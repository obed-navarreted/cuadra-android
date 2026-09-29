package com.cuadra.caja.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class SaleMathTest {
    // Estos casos son los mismos que valida el servidor (SaleTest.lineRoundingIsHalfUpWithoutFloatingPoint).
    @Test fun roundsHalfUpLikeTheServer() {
        assertEquals(33, SaleMath.lineTotal(100, 333))     // 33.3
        assertEquals(13, SaleMath.lineTotal(25, 500))      // 12.5 → 13
        assertEquals(0, SaleMath.lineTotal(1, 1))          // 0.001
        assertEquals(11000, SaleMath.lineTotal(5500, 2000))
        assertEquals(6750, SaleMath.lineTotal(9000, 750))
    }

    @Test fun appliesDiscountAndRejectsNegativeLines() {
        assertEquals(500, SaleMath.lineTotal(1000, 1000, 500))
        assertThrows(IllegalArgumentException::class.java) { SaleMath.lineTotal(1000, 1000, 1500) }
    }

    @Test fun neverOverflowsALong() {
        assertThrows(IllegalArgumentException::class.java) { SaleMath.lineTotal(SaleMath.MAX_MINOR, 999_999_999) }
    }

    @Test fun weightFromAmountForBulkProducts() {
        // C$ 70 de queso a C$ 90/lb = 0.778 lb
        assertEquals(778L, SaleMath.quantityForAmount(7000, 9000))
        // Y de vuelta: 0.778 lb cuesta C$ 70.02 → el redondeo es al más cercano, no exacto
        assertEquals(7002, SaleMath.lineTotal(9000, 778))
        assertEquals(1000L, SaleMath.quantityForAmount(9000, 9000))
        assertNull(SaleMath.quantityForAmount(0, 9000))
        assertNull(SaleMath.quantityForAmount(100, 0))
        assertNull(SaleMath.quantityForAmount(1, 10_000_000))   // menos de una milésima
    }
}

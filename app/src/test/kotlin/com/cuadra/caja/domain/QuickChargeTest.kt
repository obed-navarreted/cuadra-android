package com.cuadra.caja.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickChargeTest {
    private fun e(vararg keys: Char) = keys.fold(AmountEntry()) { acc, c -> if (c == '.') acc.dot(2) else acc.digit(c, 2) }

    @Test fun aNumberAloneIsThePendingAmount() {
        val p = QuickCharge.pending(e('1', '0', '0'), 2)!!
        assertEquals(10_000L, p.totalMinor)
        assertEquals(10_000L, p.unitPriceMinor)
        assertEquals(1000L, p.quantityMilli)
    }

    @Test fun theChargeIsTheCartPlusThePending() {
        val p = QuickCharge.pending(e('1', '0', '0'), 2, cartTotalMinor = 25_000)
        assertEquals(35_000L, QuickCharge.total(25_000, p))
        assertTrue(QuickCharge.canCharge(25_000, p))
    }

    @Test fun aQuantityTimesPriceExpressionIsEvaluatedLikeAdd() {
        val p = QuickCharge.pending(AmountEntry("5", "20", true), 2)!!
        assertEquals(10_000L, p.totalMinor)
        assertEquals(5000L, p.quantityMilli)
        assertEquals(2_000L, p.unitPriceMinor)
        assertEquals(AmountEntry("5", "20", true).totalMinor(2), p.totalMinor)
    }

    @Test fun anIncompleteExpressionIsNotPending() {
        assertNull(QuickCharge.pending(AmountEntry("5", "", true), 2))
    }

    @Test fun zeroEmptyAndLoneDotAreNotPending() {
        assertNull(QuickCharge.pending(AmountEntry(), 2))
        assertNull(QuickCharge.pending(e('0'), 2))
        assertNull(QuickCharge.pending(AmountEntry().dot(2), 2))
        assertNull(QuickCharge.pending(AmountEntry("0.00"), 2))
    }

    @Test fun anAmountOverTheLimitIsNotPending() {
        assertNull(QuickCharge.pending(AmountEntry("999999999999"), 2))
        assertNull(QuickCharge.pending(AmountEntry("9999999999"), 2, cartTotalMinor = SaleMath.MAX_MINOR))
        assertNull(QuickCharge.pending(AmountEntry("9999999", "9999999", true), 2))
    }

    @Test fun theButtonsAreOffOnlyWithNothingPendingAndAnEmptyCart() {
        assertFalse(QuickCharge.canCharge(0, null))
        assertFalse(QuickCharge.canHold(cartEmpty = true, pending = null))
        val p = QuickCharge.pending(e('7'), 2)
        assertNotNull(p)
        assertTrue(QuickCharge.canCharge(0, p))
        assertTrue(QuickCharge.canHold(cartEmpty = true, pending = p))
        assertTrue(QuickCharge.canHold(cartEmpty = false, pending = null))
        assertTrue(QuickCharge.canCharge(500, null))
    }
}

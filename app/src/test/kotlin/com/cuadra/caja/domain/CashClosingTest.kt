package com.cuadra.caja.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CashClosingTest {
    // El ejemplo del plan y de las pruebas del servidor (CashTest.theClosingMathMatchesTheWorkedExampleOfThePlan).
    private val plan = ClosingBreakdown(
        cashSalesMinor = 842_000, cashSalesCount = 64, creditPaymentsCashMinor = 115_000, depositsMinor = 0, expensesCashMinor = 185_000, withdrawalsMinor = 100_000,
        transferMinor = 234_000, cardMinor = 112_000, creditNewMinor = 186_000,
    )

    @Test fun theWorkedExampleOfThePlanGivesAThirtyShortfall() {
        val expected = CashClosing.expected(100_000, plan)
        assertEquals(772_000L, expected)                          // 1,000 + 8,420 + 1,150 − 1,850 − 1,000 = 7,720
        val diff = CashClosing.difference(769_000, expected)
        assertEquals(-3_000L, diff)
        assertEquals(ClosingResult.SHORT, CashClosing.result(diff))
    }

    @Test fun otherPaymentMethodsNeverTouchTheDrawer() {
        val onlyOthers = ClosingBreakdown(transferMinor = 500_000, cardMinor = 300_000, otherMinor = 1000, creditNewMinor = 900_000)
        assertEquals(50_000L, CashClosing.expected(50_000, onlyOthers))
    }

    @Test fun depositsAddAndWithdrawalsAndCashExpensesSubtract() {
        assertEquals(850L, CashClosing.expected(1_000, ClosingBreakdown(depositsMinor = 200, expensesCashMinor = 50, withdrawalsMinor = 300))) 
        assertEquals(60_000L, CashClosing.expected(0, ClosingBreakdown(cashSalesMinor = 100_000, depositsMinor = 10_000, expensesCashMinor = 20_000, withdrawalsMinor = 30_000)))
    }

    @Test fun balancedShortAndOverAreTold() {
        assertEquals(ClosingResult.BALANCED, CashClosing.result(0))
        assertEquals(ClosingResult.SHORT, CashClosing.result(-1))
        assertEquals(ClosingResult.OVER, CashClosing.result(1))
    }

    @Test fun theNoteIsOnlyDemandedAboveTheThreshold() {
        assertFalse(CashClosing.needsNote(-5_000, null, null))            // sin umbral: nunca
        assertTrue(CashClosing.needsNote(-5_000, 1_000, null))
        assertTrue(CashClosing.needsNote(5_000, 1_000, "   "))            // una nota en blanco no cuenta
        assertFalse(CashClosing.needsNote(-5_000, 1_000, "se cayó un billete"))
        assertFalse(CashClosing.needsNote(-500, 1_000, null))             // por debajo del umbral
        assertFalse(CashClosing.needsNote(-1_000, 1_000, null))           // igual al umbral no supera
    }

    @Test fun denominationsScaleWithTheCurrencyAndAreSortedDescending() {
        val nio = Denominations.forCurrency("NIO", 2)
        assertEquals(listOf(100_000L, 50_000L, 20_000L, 10_000L, 5_000L, 2_000L, 1_000L, 500L, 100L), nio)
        assertEquals(nio.sortedDescending(), nio)
        assertEquals(100L, Denominations.forCurrency("COP", 0).last())   // sin decimales la unidad menor es la unidad: 100 pesos, no 10,000
        assertEquals(listOf(10_000L, 5_000L, 2_000L, 1_000L, 500L, 100L), Denominations.forCurrency("USD", 2))
        assertEquals(Denominations.forCurrency("NIO", 2), Denominations.forCurrency("ZZZ", 2))   // moneda desconocida: la lista por defecto
    }

    @Test fun countingBillsAddsUpAndTheJsonKeepsWhatWasCounted() {
        val counts = mapOf(50_000L to 14, 20_000L to 3, 1_000L to 0, 500L to 2)
        assertEquals(50_000L * 14 + 20_000L * 3 + 500L * 2, Denominations.total(counts))
        assertEquals("{\"50000\":14,\"20000\":3,\"500\":2}", Denominations.toJson(counts))    // los ceros no se guardan
        assertEquals(0L, Denominations.total(mapOf(100L to -3)))                               // nunca resta
    }
}

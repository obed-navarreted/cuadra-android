package com.cuadra.caja.domain

import java.util.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CreditAllocationTest {
    private val open = listOf(OpenCredit("c3", 300, 3000), OpenCredit("c1", 100, 1000), OpenCredit("c2", 200, 2000))

    @Test fun splitsOldestFirstLikeTheServer() {
        // Ejemplo de las pruebas del servidor: 2500 → 1000 al más viejo y 1500 al siguiente.
        assertEquals(listOf(Allocation("c1", 1000), Allocation("c2", 1500)), CreditAllocation.fifo(open, 2500))
    }

    @Test fun aSmallPaymentOnlyTouchesTheOldest() {
        assertEquals(listOf(Allocation("c1", 400)), CreditAllocation.fifo(open, 400))
    }

    @Test fun anOverpaymentNeverLosesMoney() {
        val a = CreditAllocation.fifo(open, 10_000)
        assertEquals(10_000L, a.sumOf { it.amountMinor })            // el excedente queda en el último abono
        assertEquals(listOf("c1", "c2", "c3"), a.map { it.creditId })
        assertEquals(1000L, a[0].amountMinor)
        assertEquals(2000L, a[1].amountMinor)
        assertEquals(7000L, a[2].amountMinor)
    }

    @Test fun paidCreditsAreSkippedAndNothingOpenGivesNothing() {
        assertEquals(listOf(Allocation("c2", 100)), CreditAllocation.fifo(listOf(OpenCredit("c1", 1, 0), OpenCredit("c2", 2, 500)), 100))
        assertTrue(CreditAllocation.fifo(listOf(OpenCredit("c1", 1, 0)), 100).isEmpty())
    }

    @Test fun theSameCreatedAtBreaksTiesById() {
        assertEquals(listOf(Allocation("a", 50)), CreditAllocation.fifo(listOf(OpenCredit("b", 1, 100), OpenCredit("a", 1, 100)), 50))
    }

    @Test fun balanceAndStatusFollowTheLedgerRule() {
        assertEquals(3000L, CreditAllocation.balance(5000, "OPEN", 2000))
        assertEquals(0L, CreditAllocation.balance(1000, "OPEN", 1500))       // pasado de pago: nunca negativo
        assertEquals(0L, CreditAllocation.balance(5000, "WRITTEN_OFF", 0))
        assertEquals(0L, CreditAllocation.balance(5000, "CANCELLED", 0))
        assertEquals("PAID", CreditAllocation.statusFor("OPEN", 0))
        assertEquals("OPEN", CreditAllocation.statusFor("PAID", 100))         // un abono anulado lo reabre
        assertEquals("WRITTEN_OFF", CreditAllocation.statusFor("WRITTEN_OFF", 0))
    }

    /** Propiedad: para cualquier abono, lo repartido suma exactamente el abono y nunca sobrepasa el saldo salvo en el último. */
    @Test fun randomPaymentsAlwaysAddUp() {
        val rnd = Random(7)
        repeat(300) {
            val credits = (1..rnd.nextInt(6) + 1).map { OpenCredit("c$it", rnd.nextInt(1000).toLong(), rnd.nextInt(5000).toLong()) }
            val amount = (rnd.nextInt(20_000) + 1).toLong()
            val a = CreditAllocation.fifo(credits, amount)
            if (credits.none { it.balanceMinor > 0 }) assertTrue(a.isEmpty()) else {
                assertEquals(amount, a.sumOf { it.amountMinor })
                a.dropLast(1).forEach { al -> assertEquals(credits.first { it.id == al.creditId }.balanceMinor >= al.amountMinor, true) }
            }
        }
    }
}

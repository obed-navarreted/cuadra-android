package com.cuadra.caja.domain

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** docs/adr/0017: la ventana de 60 minutos del cajero (misma regla que el servidor). */
class CashierWindowTest {
    private val now = 10_000_000_000L
    private val minute = 60_000L

    private fun sees(role: String?, status: String = "COMPLETED", me: String? = "kevin", by: String? = "kevin", completedAt: Long? = now - 30 * minute, createdAt: Long = now - 40 * minute, at: Long = now) =
        CashierWindow.sees(role, me, status, by, by, completedAt, createdAt, at)

    @Test fun boundaryIsExactlySixtyMinutes() {
        assertTrue("59:59 se ve", sees("CASHIER", completedAt = now - (59 * 60 + 59) * 1000))
        assertTrue("60:00 se ve", sees("CASHIER", completedAt = now - 60 * minute))
        assertFalse("60:01 ya no", sees("CASHIER", completedAt = now - 60 * minute - 1_000))
        assertFalse("60:00.001 ya no", sees("CASHIER", completedAt = now - 60 * minute - 1))
    }

    @Test fun anAgingRowDropsOutAsTheClockAdvances() {
        val completed = now - 59 * minute
        assertTrue(sees("CASHIER", completedAt = completed, at = now))
        assertFalse(sees("CASHIER", completedAt = completed, at = now + 2 * minute))
    }

    @Test fun onlyHisOwnSales() {
        assertFalse(CashierWindow.sees("CASHIER", "kevin", "COMPLETED", "lucia", "lucia", now - minute, now - 2 * minute, now))
        // Cobro en caja: la tomó otra persona y la cobró él (o al revés): es suya.
        assertTrue(CashierWindow.sees("CASHIER", "kevin", "COMPLETED", "lucia", "kevin", now - minute, now - 2 * minute, now))
        assertTrue(CashierWindow.sees("CASHIER", "kevin", "COMPLETED", "kevin", "lucia", now - minute, now - 2 * minute, now))
        assertFalse(CashierWindow.sees("CASHIER", null, "COMPLETED", "kevin", "kevin", now - minute, now - 2 * minute, now))
    }

    @Test fun notCompletedUsesCreatedAt() {
        assertTrue(sees("CASHIER", status = "CANCELLED", completedAt = null, createdAt = now - 10 * minute))
        assertFalse(sees("CASHIER", status = "CANCELLED", completedAt = null, createdAt = now - 90 * minute))
    }

    @Test fun parkedAndOpenTicketsAreTheBusinessWorkingTickets() {
        assertTrue(CashierWindow.sees("CASHIER", "kevin", "PARKED", "lucia", null, null, now - 5 * 60 * minute, now))
        assertTrue(CashierWindow.sees("CASHIER", "kevin", "OPEN", "kevin", null, null, now - 5 * 60 * minute, now))
    }

    @Test fun ownerAndAdminSeeEverythingAndSwitchingFromCashierToAdminShowsItAgain() {
        val old = now - 3 * 60 * minute
        assertFalse(CashierWindow.sees("CASHIER", "kevin", "COMPLETED", "kevin", "kevin", old, old, now))
        // Mismo teléfono, ahora con un admin (y también la venta de otra persona, de ayer).
        assertTrue(CashierWindow.sees("ADMIN", "ana", "COMPLETED", "kevin", "kevin", old, old, now))
        assertTrue(CashierWindow.sees("OWNER", "dueno", "COMPLETED", "lucia", "lucia", now - 30 * 24 * 60 * minute, now - 30 * 24 * 60 * minute, now))
        assertTrue(CashierWindow.sees(null, null, "COMPLETED", "lucia", "lucia", old, old, now))
    }

    @Test fun appliesOnlyToCashiers() {
        assertTrue(CashierWindow.applies("CASHIER"))
        assertFalse(CashierWindow.applies("ADMIN"))
        assertFalse(CashierWindow.applies("OWNER"))
        assertFalse(CashierWindow.applies(null))
    }

    @Test fun tickerEmitsTheCurrentTimeFirst() = runTest {
        assertEquals(42L, CashierWindow.ticker({ 42L }).first())
    }
}

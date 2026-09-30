package com.cuadra.caja.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CreditRulesTest {
    private fun block(req: Boolean = false, lim: Boolean = false, has: Boolean = true, save: Boolean = false, limit: Long? = 1_000, balance: Long = 0, amount: Long = 500) =
        CreditRules.block(req, lim, has, save, limit, balance, amount)

    @Test fun withoutRulesNothingBlocks() {
        assertNull(block(has = false)); assertNull(block(balance = 900, amount = 900))
    }

    @Test fun requiringACustomerBlocksAFreeNameButNotASavedOrPickedCustomer() {
        assertEquals(CreditRules.Block.CUSTOMER_REQUIRED, block(req = true, has = false))
        assertNull(block(req = true, has = true))
        assertNull(block(req = true, has = false, save = true))
    }

    @Test fun theLimitOnlyBlocksWhenEnforcedAndExceeded() {
        assertEquals(CreditRules.Block.OVER_LIMIT, block(lim = true, balance = 600, amount = 500))
        assertNull(block(lim = true, balance = 500, amount = 500))          // justo en el límite: pasa
        assertNull(block(lim = true, limit = null, balance = 99_999))       // sin límite: nunca bloquea
        assertNull(block(lim = false, balance = 600, amount = 500))         // avisa, no bloquea
    }

    @Test fun defaultDueDate() {
        assertEquals(LocalDate.of(2026, 10, 14), CreditRules.dueDate(15, LocalDate.of(2026, 9, 29)))
        assertEquals(LocalDate.of(2026, 9, 29), CreditRules.dueDate(0, LocalDate.of(2026, 9, 29)))
        assertNull(CreditRules.dueDate(null, LocalDate.of(2026, 9, 29)))
    }
}

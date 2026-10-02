package com.cuadra.caja.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** «Vendido hoy»: un cajero ve solo lo que cobró él; dueño y admins, todo el negocio. */
class SoldTodayTest {
    @Test fun aCashierCountsOnlyOwnSales() = assertEquals("kevin", SoldToday.memberFilter("CASHIER", "kevin"))

    @Test fun ownersAndAdminsSeeTheBusinessTotal() {
        assertNull(SoldToday.memberFilter("OWNER", "ana"))
        assertNull(SoldToday.memberFilter("ADMIN", "ana"))
        assertNull(SoldToday.memberFilter(null, null))
        assertNull(SoldToday.memberFilter("CASHIER", null))
    }
}

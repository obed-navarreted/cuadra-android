package com.cuadra.caja.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CartTest {
    private var n = 0
    private val id = { "id${n++}" }

    @Test fun sameProductAddsOneUnitInsteadOfDuplicatingTheLine() {
        var cart = Cart()
        repeat(100) { cart = cart.addProduct("p1", "741", "Coca-Cola", "1.5 L", 5500, null, newId = id) }
        assertEquals(1, cart.lines.size)
        // 100 lecturas = exactamente 100 unidades (criterio de aceptación del plan, 6.x)
        assertEquals(100_000L, cart.lines[0].quantityMilli)
        assertEquals(550_000L, cart.totalMinor)
    }

    @Test fun freeSalesAreNeverMerged() {
        val cart = Cart().addFree(8500, null, newId = id).addFree(8500, "Queso seco", newId = id)
        assertEquals(2, cart.lines.size)
        assertEquals(Cart.FREE_NAME, cart.lines[0].name)
        assertEquals("Queso seco", cart.lines[1].name)
        assertEquals(17000L, cart.totalMinor)
    }

    @Test fun decrementingToZeroRemovesTheLine() {
        var cart = Cart().addProduct("p1", null, "Pan", null, 1000, null, newId = id)
        val line = cart.lines[0].id
        cart = cart.changeQuantity(line, 1000).changeQuantity(line, -1000)
        assertEquals(1000L, cart.lines[0].quantityMilli)
        cart = cart.changeQuantity(line, -1000)
        assertTrue(cart.isEmpty)
    }

    @Test fun weightQuantitiesAndDiscountAreCappedAtTheSubtotal() {
        var cart = Cart().addProduct("q", null, "Queso seco", null, 9000, null, quantityMilli = 750, newId = id)
        assertEquals(6750L, cart.totalMinor)
        cart = cart.withDiscount(1_000_000)
        assertEquals(0L, cart.totalMinor)
        assertEquals(6750L, cart.discountMinor)
    }

    @Test fun mixedLinesAddUpLikeTheReceiptInThePlan() {
        val cart = Cart()
            .addProduct("c", null, "Coca-Cola", "1.5 L", 5500, null, quantityMilli = 2000, newId = id)
            .addProduct("q", null, "Queso seco", null, 9000, null, quantityMilli = 750, newId = id)
        assertEquals(17750L, cart.totalMinor)
    }

    @Test fun openPriceLinesKeepTheirTypedPriceWhenTheQuantityChanges() {
        var n = 0
        val id = { "l${++n}" }
        val cart = Cart()
            .addProduct("o", null, "Reparación", null, 4500, null, newId = id)
            .addProduct("o", null, "Reparación", null, 7000, null, newId = id)   // otro precio: otra línea
            .addProduct("o", null, "Reparación", null, 4500, null, newId = id)   // mismo precio: suma una unidad
        assertEquals(2, cart.lines.size)
        assertEquals(2000L, cart.lines[0].quantityMilli)
        val more = cart.changeQuantity("l1", 1000)
        assertEquals(4500L, more.lines[0].unitPriceMinor)
        assertEquals(13500L + 7000L, more.totalMinor)
    }
}

package com.cuadra.caja.domain

import com.cuadra.caja.data.local.ProductEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** «Editar producto» desde la venta: al guardar, las líneas del recibo toman el nombre y el precio nuevos y conservan su cantidad. */
class CartRefreshProductTest {
    private var n = 0
    private val id = { "l${n++}" }

    private fun product(name: String = "Cuajada", price: Long = 2500, pricing: String = Pricing.FIXED, variant: String? = null, cost: Long? = null, barcode: String? = "741") =
        ProductEntity("p1", barcode, null, name, variant, null, "UNIT", pricing, price, cost, false, null, null, false, 0, null, true, 1)

    @Test fun priceAndNameRefreshAndTheQuantityIsKept() {
        val cart = Cart().addProduct("p1", "741", "Cuajada", null, 2500, null, quantityMilli = 3000, newId = id).addFree(1000, "Bolsa", newId = id)
        val r = cart.refreshProduct(product(name = "Cuajada fresca", price = 3000, variant = "1 lb", cost = 1800))
        val line = r.cart.lines.first { it.productId == "p1" }
        assertEquals("Cuajada fresca", line.name)
        assertEquals("1 lb", line.variant)
        assertEquals(3000L, line.unitPriceMinor)
        assertEquals(1800L, line.unitCostMinor)
        assertEquals(3000L, line.quantityMilli)
        assertTrue(r.priceChanged)
        // Una línea libre (sin producto) no se toca.
        assertEquals(1000L, r.cart.lines.first { it.productId == null }.unitPriceMinor)
        assertEquals(9000L + 1000L, r.cart.totalMinor)
    }

    @Test fun noPriceChangeMeansNoNotice() {
        val cart = Cart().addProduct("p1", "741", "Cuajada", null, 2500, null, newId = id)
        val r = cart.refreshProduct(product(name = "Cuajada nueva"))
        assertFalse(r.priceChanged)
        assertEquals("Cuajada nueva", r.cart.lines.single().name)
    }

    @Test fun openPriceKeepsThePriceTypedInThisSale() {
        val cart = Cart().addProduct("p1", null, "Servicio", null, 1800, null, newId = id)
        val r = cart.refreshProduct(product(name = "Servicio a domicilio", price = 0, pricing = Pricing.OPEN))
        assertEquals(1800L, r.cart.lines.single().unitPriceMinor)
        assertFalse(r.priceChanged)
        assertEquals("Servicio a domicilio", r.cart.lines.single().name)
    }

    @Test fun linesLeftAtTheSamePriceAreMergedIntoOne() {
        // Dos líneas del mismo producto a precios distintos (uno viejo y uno de precio abierto antes): al unificar el precio se suman.
        val cart = Cart(listOf(
            CartLine("a", "p1", "741", "Cuajada", null, 2500, null, 2000),
            CartLine("b", "p1", "741", "Cuajada", null, 3000, null, 1000),
        ))
        val r = cart.refreshProduct(product(price = 3000))
        assertEquals(1, r.cart.lines.size)
        assertEquals(3000L, r.cart.lines.single().quantityMilli)
        assertTrue(r.priceChanged)
    }

    @Test fun byWeightFlagFollowsThePricing() {
        val cart = Cart().addProduct("p1", null, "Queso", null, 9000, null, quantityMilli = 750, newId = id)
        assertTrue(cart.refreshProduct(product(price = 9500, pricing = Pricing.BY_WEIGHT)).cart.lines.single().byWeight)
    }
}

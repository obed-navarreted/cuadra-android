package com.cuadra.caja.domain

import com.cuadra.caja.data.local.ProductEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class ProductPayloadTest {
    private val old = ProductEntity(
        id = "p1", barcode = "7501", shortCode = "12", name = "Cuajada", variant = "500 g", categoryId = "c1", unit = "LB", pricing = "BY_WEIGHT",
        priceMinor = 2500, costMinor = 1800, isQuick = true, quickPosition = 3, color = "#ff0000", trackStock = true, stockMilli = 5000,
        minStockMilli = 2000, active = true, rev = 9,
    )

    @Test fun editingThePriceKeepsEverythingTheEditorDoesNotShow() {
        val p = ProductPayload.build(old, " Cuajada fresca ", 3000, old.costMinor, old.unit, old.trackStock, old.minStockMilli)
        assertEquals("Cuajada fresca", p.name)
        assertEquals(3000L, p.priceMinor)
        assertEquals("7501", p.barcode); assertEquals("12", p.shortCode); assertEquals("500 g", p.variant); assertEquals("c1", p.categoryId)
        assertEquals("BY_WEIGHT", p.pricing); assertEquals(true, p.isQuick); assertEquals(3, p.quickPosition); assertEquals("#ff0000", p.color)
        assertEquals(1800L, p.costMinor); assertEquals(true, p.trackStock); assertEquals(2000L, p.minStockMilli); assertEquals("LB", p.unit)
        assertEquals(true, p.active)
    }

    @Test fun editNeverChangesActive() {
        assertFalse(ProductPayload.build(old.copy(active = false), "x", 1, null, "UNIT", false, null).active)
    }

    @Test fun newProductGetsDefaults() {
        val p = ProductPayload.build(null, "Nuevo", 100, null, "UNIT", false, null)
        assertEquals("FIXED", p.pricing); assertEquals(true, p.active); assertFalse(p.isQuick); assertNull(p.barcode); assertNull(p.quickPosition)
    }

    @Test fun deactivateOnlyFlipsActive() {
        val p = ProductPayload.withActive(old, false)
        assertFalse(p.active)
        assertEquals(ProductPayload.build(old, old.name, old.priceMinor, old.costMinor, old.unit, old.trackStock, old.minStockMilli).copy(active = false), p)
    }

    // ---------- cambio parcial (PRODUCT_PATCH) ----------

    @Test fun anEditSendsOnlyTheFieldsThatChangedWithTheBaseRevision() {
        val patch = ProductPayload.patch(old, ProductPayload.build(old, "Cuajada fresca", old.priceMinor, old.costMinor, old.unit, old.trackStock, old.minStockMilli))!!
        assertEquals("""{"set":{"name":"Cuajada fresca"},"baseRev":9}""", patch.toString())   // ni precio ni costo: no se revierten
    }

    @Test fun frequentsAndReorderOnlyTouchIsQuickAndPosition() {
        val patch = ProductPayload.patch(old, ProductPayload.withFrequent(old, false, null))!!
        assertEquals("""{"set":{"isQuick":false,"quickPosition":null},"baseRev":9}""", patch.toString())
        assertEquals("""{"set":{"quickPosition":1},"baseRev":9}""", ProductPayload.patch(old, ProductPayload.withFrequent(old, true, 1)).toString())
    }

    @Test fun clearingTheCostSendsAnExplicitNullAndNothingChangedSendsNothing() {
        assertEquals("""{"set":{"priceMinor":2600,"costMinor":null},"baseRev":9}""",
            ProductPayload.patch(old, ProductPayload.build(old, old.name, 2600, null, old.unit, old.trackStock, old.minStockMilli)).toString())
        assertNull(ProductPayload.patch(old, ProductPayload.build(old, old.name, old.priceMinor, old.costMinor, old.unit, old.trackStock, old.minStockMilli)))
        assertEquals("""{"set":{"active":false},"baseRev":9}""", ProductPayload.patch(old, ProductPayload.withActive(old, false)).toString())
    }
}

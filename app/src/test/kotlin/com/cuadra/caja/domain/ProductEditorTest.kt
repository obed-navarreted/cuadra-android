package com.cuadra.caja.domain

import com.cuadra.caja.data.local.ProductEntity
import com.cuadra.caja.data.remote.ProductInputDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductEditorTest {
    private val old = ProductEntity(
        id = "p1", barcode = "7501", shortCode = "12", name = "Cuajada", variant = "500 g", categoryId = "c1", unit = "LB", pricing = "BY_WEIGHT",
        priceMinor = 2500, costMinor = 1800, isQuick = true, quickPosition = 3, color = "#ff0000", trackStock = true, stockMilli = 5000,
        minStockMilli = 2000, active = false, rev = 9,
    )

    private fun valid(old: ProductEntity?, f: ProductForm, owner: String? = null): ProductInputDto =
        (ProductEditor.build(old, f, 2, owner) as ProductEditorResult.Valid).input

    private fun errors(old: ProductEntity?, f: ProductForm, owner: String? = null): Set<ProductError> =
        (ProductEditor.build(old, f, 2, owner) as ProductEditorResult.Invalid).errors

    @Test fun openWithoutPriceIsValidAndSendsZero() {
        val p = valid(null, ProductForm(name = "Pan", pricing = Pricing.OPEN))
        assertEquals("OPEN", p.pricing); assertEquals(0L, p.priceMinor)
    }

    @Test fun openWithSuggestedPriceKeepsIt() {
        assertEquals(4500L, valid(null, ProductForm(name = "Pan", pricing = Pricing.OPEN, price = "45")).priceMinor)
    }

    @Test fun fixedAndByWeightNeedAPrice() {
        assertEquals(setOf(ProductError.PRICE_REQUIRED), errors(null, ProductForm(name = "Pan", pricing = Pricing.FIXED)))
        assertEquals(setOf(ProductError.PRICE_REQUIRED), errors(null, ProductForm(name = "Queso", pricing = Pricing.BY_WEIGHT, price = "  ")))
    }

    @Test fun invalidPriceOrCostOrMinimumIsReported() {
        assertEquals(setOf(ProductError.PRICE_INVALID), errors(null, ProductForm(name = "x", price = "1.234")))
        assertEquals(setOf(ProductError.PRICE_INVALID), errors(null, ProductForm(name = "x", price = "abc")))
        assertEquals(setOf(ProductError.COST_INVALID), errors(null, ProductForm(name = "x", price = "1", cost = "1.999")))
        assertEquals(setOf(ProductError.MIN_STOCK_INVALID), errors(null, ProductForm(name = "x", price = "1", trackStock = true, minStock = "x")))
    }

    @Test fun nameIsRequiredAndTrimmed() {
        assertEquals(setOf(ProductError.NAME_REQUIRED), errors(null, ProductForm(name = "   ", price = "1")))
        assertEquals("Pan", valid(null, ProductForm(name = "  Pan  ", price = "1")).name)
    }

    @Test fun emptyCostIsNullNeverZero() {
        assertNull(valid(null, ProductForm(name = "x", price = "1", cost = "")).costMinor)
        assertNull(valid(null, ProductForm(name = "x", price = "1", cost = "   ")).costMinor)
        assertEquals(0L, valid(null, ProductForm(name = "x", price = "1", cost = "0")).costMinor)   // 0 escrito a propósito sí se respeta
        assertEquals(1250L, valid(null, ProductForm(name = "x", price = "1", cost = "12.5")).costMinor)
    }

    @Test fun blankCodesAndVariantAreNull() {
        val p = valid(null, ProductForm(name = "x", price = "1", barcode = "  ", shortCode = " ", variant = ""))
        assertNull(p.barcode); assertNull(p.shortCode); assertNull(p.variant); assertNull(p.categoryId)
    }

    @Test fun codesAreTrimmed() {
        val p = valid(null, ProductForm(name = "x", price = "1", barcode = " 7501234567890 ", shortCode = " A1 ", variant = " 2 L "))
        assertEquals("7501234567890", p.barcode); assertEquals("A1", p.shortCode); assertEquals("2 L", p.variant)
    }

    @Test fun duplicateBarcodeIsAnErrorOnlyWhenThereIsACode() {
        assertEquals(setOf(ProductError.BARCODE_IN_USE), errors(null, ProductForm(name = "x", price = "1", barcode = "7501"), owner = "Cuajada"))
        assertNull(valid(null, ProductForm(name = "x", price = "1", barcode = ""), owner = "Cuajada").barcode)
    }

    @Test fun unitOnlyMattersForByWeight() {
        assertEquals("UNIT", valid(null, ProductForm(name = "x", price = "1", pricing = Pricing.FIXED, unit = "KG")).unit)
        assertEquals("UNIT", valid(null, ProductForm(name = "x", pricing = Pricing.OPEN, unit = "KG")).unit)
        assertEquals("KG", valid(null, ProductForm(name = "x", price = "1", pricing = Pricing.BY_WEIGHT, unit = "KG")).unit)
        assertEquals("LB", valid(null, ProductForm(name = "x", price = "1", pricing = Pricing.BY_WEIGHT, unit = "UNIT")).unit)
    }

    @Test fun editingAFixedProductKeepsItsUnit() {
        val fixedLiters = old.copy(pricing = "FIXED", unit = "L")
        assertEquals("L", valid(fixedLiters, ProductEditor.formOf(fixedLiters, 2)).unit)
        // Pasar de por peso a fijo suelta la unidad de peso.
        assertEquals("UNIT", valid(old, ProductEditor.formOf(old, 2).copy(pricing = Pricing.FIXED)).unit)
    }

    @Test fun editingWithoutTouchingKeepsEverything() {
        val p = valid(old, ProductEditor.formOf(old, 2))
        assertEquals("7501", p.barcode); assertEquals("12", p.shortCode); assertEquals("500 g", p.variant); assertEquals("c1", p.categoryId)
        assertEquals("BY_WEIGHT", p.pricing); assertEquals("LB", p.unit); assertEquals(2500L, p.priceMinor); assertEquals(1800L, p.costMinor)
        assertTrue(p.isQuick); assertEquals(3, p.quickPosition); assertEquals("#ff0000", p.color)
        assertTrue(p.trackStock); assertEquals(2000L, p.minStockMilli)
        assertFalse(p.active)   // el editor nunca cambia «activo»
    }

    @Test fun editingAddsABarcodeAndKeepsTheRest() {
        val noCode = old.copy(barcode = null)
        val p = valid(noCode, ProductEditor.formOf(noCode, 2).copy(barcode = "7509999"))
        assertEquals("7509999", p.barcode); assertEquals("c1", p.categoryId); assertEquals(3, p.quickPosition)
    }

    @Test fun editingAnOpenProductRoundTrips() {
        val open = old.copy(pricing = "OPEN", priceMinor = 0, unit = "UNIT", costMinor = null)
        val form = ProductEditor.formOf(open, 2)
        assertEquals("", form.price)
        val p = valid(open, form)
        assertEquals("OPEN", p.pricing); assertEquals(0L, p.priceMinor); assertNull(p.costMinor)
    }

    @Test fun categoryCanBeClearedOrChosen() {
        assertNull(valid(old, ProductEditor.formOf(old, 2).copy(categoryId = null)).categoryId)
        assertEquals("c9", valid(old, ProductEditor.formOf(old, 2).copy(categoryId = "c9")).categoryId)
    }

    @Test fun zeroDecimalCurrency() {
        val p = (ProductEditor.build(null, ProductForm(name = "x", price = "150"), 0) as ProductEditorResult.Valid).input
        assertEquals(150L, p.priceMinor)
        assertTrue(ProductEditor.build(null, ProductForm(name = "x", price = "1.5"), 0) is ProductEditorResult.Invalid)
    }

    @Test fun newProductDefaults() {
        val p = valid(null, ProductForm(name = "x", price = "1"))
        assertEquals("FIXED", p.pricing); assertTrue(p.active); assertFalse(p.isQuick); assertNull(p.quickPosition); assertNull(p.color)
    }
}

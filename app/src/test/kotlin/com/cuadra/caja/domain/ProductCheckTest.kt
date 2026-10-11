package com.cuadra.caja.domain

import com.cuadra.caja.data.local.ProductEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** «Comprobar producto»: existe / no existe / el mismo código en 12 y 13 dígitos / dígito de control malo. */
class ProductCheckTest {
    private fun product(id: String, barcode: String?, name: String = id) =
        ProductEntity(id, barcode, null, name, null, null, "UNIT", Pricing.FIXED, 2500, null, false, null, null, false, 0, null, true, 1)

    private val catalog = listOf(product("lapiz", "4006381333931", "Lápiz"), product("coca", "036000291452", "Coca"), product("sin", null, "Sin código"))
    private val lookup: suspend (String) -> ProductEntity? = { code -> catalog.firstOrNull { p -> p.barcode != null && p.barcode in Barcodes.forms(code) } }

    @Test fun existingCodeIsFound() = runBlocking {
        val r = ProductChecker.check(" 4006381333931\n", lookup) as ProductCheck.Found
        assertEquals("Lápiz", r.product.name)
        assertEquals("4006381333931", r.code)
    }

    @Test fun upcAWithTheLeadingZeroIsTheSameProduct() = runBlocking {
        // Guardado como UPC-A (12) y leído como EAN-13 (con un 0 delante), y al revés.
        assertEquals("coca", (ProductChecker.check("0036000291452", lookup) as ProductCheck.Found).product.id)
        assertEquals("lapiz", (ProductChecker.check("4006381333931", lookup) as ProductCheck.Found).product.id)
    }

    @Test fun unknownCodeWithAValidCheckDigitOffersToCreateWithoutWarning() = runBlocking {
        val r = ProductChecker.check("5901234123457", lookup) as ProductCheck.Missing
        assertEquals("5901234123457", r.code)
        assertFalse(r.badChecksum)
    }

    @Test fun badCheckDigitWarnsButStillOffersToCreate() = runBlocking {
        val r = ProductChecker.check("5901234123450", lookup) as ProductCheck.Missing
        assertTrue(r.badChecksum)
        assertEquals("5901234123450", r.code)
    }

    @Test fun internalCodesAndLettersNeverWarn() = runBlocking {
        assertFalse((ProductChecker.check("ABC-123", lookup) as ProductCheck.Missing).badChecksum)
        assertFalse((ProductChecker.check("9001", lookup) as ProductCheck.Missing).badChecksum)
    }

    @Test fun emptyReadingChecksNothing() = runBlocking { assertNull(ProductChecker.check("   ", lookup)) }

    @Test fun listQueryUsesTheStoredBarcodeOrTheName() {
        assertEquals("036000291452", ProductChecker.listQuery(catalog[1]))
        assertEquals("Sin código", ProductChecker.listQuery(catalog[2]))
    }
}

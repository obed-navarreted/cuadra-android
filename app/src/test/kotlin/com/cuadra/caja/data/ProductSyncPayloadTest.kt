package com.cuadra.caja.data

import com.cuadra.caja.data.remote.CategoryDto
import com.cuadra.caja.data.remote.CategoryInputDto
import com.cuadra.caja.data.remote.ProductDto
import com.cuadra.caja.data.sync.toEntity
import com.cuadra.caja.domain.Pricing
import com.cuadra.caja.domain.ProductEditor
import com.cuadra.caja.domain.ProductEditorResult
import com.cuadra.caja.domain.ProductForm
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** El cuerpo de PRODUCT_UPSERT / CATEGORY_UPSERT que sale del teléfono y lo que entra del servidor con precio abierto. */
class ProductSyncPayloadTest {
    private val json = Json { encodeDefaults = true; explicitNulls = false }

    @Test fun openProductPayloadHasPricingOpenZeroPriceAndNoNullCost() {
        val input = (ProductEditor.build(null, ProductForm(name = "Servicio", pricing = Pricing.OPEN), 2) as ProductEditorResult.Valid).input
        val o: JsonObject = json.encodeToString(input).let { Json.parseToJsonElement(it).jsonObject }
        assertEquals("OPEN", o.getValue("pricing").jsonPrimitive.content)
        assertEquals("0", o.getValue("priceMinor").jsonPrimitive.content)
        assertFalse("costMinor" in o)      // costo vacío: ausente (null), nunca 0
        assertFalse("barcode" in o)
        assertTrue(o.getValue("active").jsonPrimitive.boolean)
    }

    @Test fun serverProductWithOpenPricingBecomesAnEntity() {
        val dto = Json { ignoreUnknownKeys = true }.decodeFromString<ProductDto>(
            """{"id":"p1","name":"Servicio","unit":"UNIT","pricing":"OPEN","priceMinor":0,"isQuick":true,"trackStock":false,"stockMilli":0,"active":true,"updatedAt":"2026-09-29T10:00:00Z","rev":3}""",
        )
        val e = dto.toEntity()
        assertEquals("OPEN", e.pricing); assertEquals(0L, e.priceMinor); assertEquals(null, e.costMinor)
    }

    @Test fun categoriesComeInAndGoOut() {
        val e = Json { ignoreUnknownKeys = true }.decodeFromString<CategoryDto>("""{"id":"c1","name":"Bebidas","active":true,"rev":2,"extra":1}""").toEntity()
        assertEquals("Bebidas", e.name); assertTrue(e.active); assertEquals(2L, e.rev)
        assertEquals("""{"name":"Bebidas","active":true}""", json.encodeToString(CategoryInputDto("Bebidas", true)))
    }
}

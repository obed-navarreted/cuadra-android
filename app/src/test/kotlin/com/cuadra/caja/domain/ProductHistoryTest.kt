package com.cuadra.caja.domain

import com.cuadra.caja.data.remote.FieldChangeDto
import com.cuadra.caja.data.remote.ProductHistoryEntryDto
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

class ProductHistoryTest {
    private val es = object : HistoryStrings {
        override fun fieldName(field: String) = mapOf("priceMinor" to "Precio", "name" to "Nombre", "costMinor" to "Costo", "trackStock" to "Lleva existencias")[field] ?: field
        override fun unitName(code: String) = code
        override fun pricingName(code: String) = code
        override val deactivated = "Dado de baja"
        override val reactivated = "Reactivado"
        override val yes = "Sí"
        override val no = "No"
        override val empty = "vacío"
        override fun created(who: String, price: String?) = if (price != null) "Creado por $who a $price" else "Creado por $who"
        override fun changed(field: String, from: String, to: String) = "$field: $from → $to"
    }
    private val money = { m: Long -> "C$ %d.%02d".format(m / 100, m % 100) }
    private fun entry(action: String, vararg c: Pair<String, FieldChangeDto>) =
        ProductHistoryEntryDto(1, action, "m1", "Kevin", "CASHIER", "2026-09-29T10:00:00Z", mapOf(*c), "Cuajada")
    private fun ch(from: kotlinx.serialization.json.JsonElement?, to: kotlinx.serialization.json.JsonElement?) = FieldChangeDto(from, to)

    @Test fun priceChangeUsesMoneyFormatter() {
        val e = entry("product.update", "priceMinor" to ch(JsonPrimitive(2500), JsonPrimitive(3000)))
        assertEquals(listOf("Precio: C$ 25.00 → C$ 30.00"), ProductHistory.lines(e, es, money))
    }

    @Test fun nameChange() {
        val e = entry("product.update", "name" to ch(JsonPrimitive("Cuajada"), JsonPrimitive("Cuajada fresca")))
        assertEquals(listOf("Nombre: Cuajada → Cuajada fresca"), ProductHistory.lines(e, es, money))
    }

    @Test fun activeToggle() {
        assertEquals(listOf("Dado de baja"), ProductHistory.lines(entry("product.update", "active" to ch(JsonPrimitive(true), JsonPrimitive(false))), es, money))
        assertEquals(listOf("Reactivado"), ProductHistory.lines(entry("product.update", "active" to ch(JsonPrimitive(false), JsonPrimitive(true))), es, money))
        assertEquals(listOf("Dado de baja"), ProductHistory.lines(entry("product.deactivate", "active" to ch(JsonPrimitive(true), JsonPrimitive(false))), es, money))
    }

    @Test fun createdShowsWhoAndPrice() {
        val e = entry("product.create", "name" to ch(null, JsonPrimitive("Cuajada")), "priceMinor" to ch(JsonNull, JsonPrimitive(2500)))
        assertEquals(listOf("Creado por Kevin a C$ 25.00"), ProductHistory.lines(e, es, money))
        assertEquals(listOf("Creado por Kevin"), ProductHistory.lines(entry("product.create"), es, money))
    }

    @Test fun nullToValueAndBooleans() {
        val e = entry("product.update", "costMinor" to ch(JsonNull, JsonPrimitive(1800)), "trackStock" to ch(JsonPrimitive(false), JsonPrimitive(true)))
        assertEquals(listOf("Costo: vacío → C$ 18.00", "Lleva existencias: No → Sí"), ProductHistory.lines(e, es, money))
    }

    @Test fun valueToNull() {
        val e = entry("product.update", "costMinor" to ch(JsonPrimitive(1800), null))
        assertEquals(listOf("Costo: C$ 18.00 → vacío"), ProductHistory.lines(e, es, money))
    }

    @Test fun openPriceProductCreatedDoesNotClaimAZeroPrice() {
        val e = entry("product.create", "name" to ch(null, JsonPrimitive("Servicio")), "priceMinor" to ch(null, JsonPrimitive(0)))
        assertEquals(listOf("Creado por Kevin"), ProductHistory.lines(e, es, money))
        val suggested = entry("product.create", "priceMinor" to ch(null, JsonPrimitive(500)))
        assertEquals(listOf("Creado por Kevin a C$ 5.00"), ProductHistory.lines(suggested, es, money))
    }

    @Test fun changingToOpenPricingIsReadable() {
        val named = object : HistoryStrings by es { override fun pricingName(code: String) = if (code == "OPEN") "Precio abierto" else "Precio fijo" }
        val e = entry("product.update", "pricing" to ch(JsonPrimitive("FIXED"), JsonPrimitive("OPEN")))
        assertEquals(listOf("pricing: Precio fijo → Precio abierto"), ProductHistory.lines(e, named, money))
    }
}

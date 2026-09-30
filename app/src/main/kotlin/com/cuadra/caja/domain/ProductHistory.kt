package com.cuadra.caja.domain

import com.cuadra.caja.data.remote.ProductHistoryEntryDto
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.longOrNull

/** Los textos que dependen del idioma; la pantalla los da con `stringResource`, las pruebas con un doble. */
interface HistoryStrings {
    fun fieldName(field: String): String
    fun unitName(code: String): String
    fun pricingName(code: String): String
    val deactivated: String
    val reactivated: String
    val yes: String
    val no: String
    val empty: String
    fun created(who: String, price: String?): String
    fun changed(field: String, from: String, to: String): String
}

/** Convierte una entrada del historial en frases legibles. Lógica pura: dinero con `money`, textos con `HistoryStrings`. */
object ProductHistory {
    private val ORDER = listOf(
        "name", "variant", "priceMinor", "costMinor", "unit", "pricing", "barcode", "shortCode", "categoryId", "isQuick", "quickPosition",
        "color", "trackStock", "minStockMilli", "active",
    )

    fun lines(e: ProductHistoryEntryDto, s: HistoryStrings, money: (Long) -> String): List<String> {
        val ch = e.changes
        if (e.action == "product.create") {
            // El servidor no guarda el modo de cobro al crear: un precio de 0 es un producto de precio abierto (uno fijo en cero no se puede vender),
            // y no se cuenta «C$ 0.00» como si fuera su precio.
            val price = ch["priceMinor"]?.to.asLong()?.takeUnless { it == 0L }?.let(money)
            return listOf(s.created(e.actorName ?: "?", price))
        }
        if (e.action == "product.deactivate") return listOf(s.deactivated)
        val out = mutableListOf<String>()
        val keys = ORDER.filter { it in ch } + ch.keys.filter { it !in ORDER }
        for (k in keys) {
            val c = ch.getValue(k)
            if (k == "active") { out += if (c.to.asBool() == false) s.deactivated else s.reactivated; continue }
            out += s.changed(s.fieldName(k), value(k, c.from, s, money), value(k, c.to, s, money))
        }
        return out
    }

    private fun value(field: String, v: JsonElement?, s: HistoryStrings, money: (Long) -> String): String {
        if (v == null || v is JsonNull) return s.empty
        val p = v as? JsonPrimitive ?: return v.toString()
        return when (field) {
            "priceMinor", "costMinor" -> p.longOrNull?.let(money) ?: p.content
            "minStockMilli" -> p.longOrNull?.let { java.math.BigDecimal.valueOf(it, 3).stripTrailingZeros().toPlainString() } ?: p.content
            "unit" -> s.unitName(p.content)
            "pricing" -> s.pricingName(p.content)
            else -> p.booleanOrNull?.let { if (it) s.yes else s.no } ?: p.content.ifBlank { s.empty }
        }
    }

    private fun JsonElement?.asLong(): Long? = (this as? JsonPrimitive)?.takeIf { it !is JsonNull }?.longOrNull
    private fun JsonElement?.asBool(): Boolean? = (this as? JsonPrimitive)?.takeIf { it !is JsonNull }?.booleanOrNull
}

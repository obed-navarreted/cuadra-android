package com.cuadra.caja.domain

import com.cuadra.caja.data.local.ProductEntity
import com.cuadra.caja.data.remote.ProductInputDto
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Arma lo que se guarda al editar un producto. Un producto NUEVO viaja completo (`PRODUCT_UPSERT`); uno que ya existe viaja como cambio parcial
 * (`patch` → `PRODUCT_PATCH`, solo lo que cambió). Todo campo que el editor no muestra se conserva del producto existente.
 */
object ProductPayload {
    fun build(old: ProductEntity?, name: String, priceMinor: Long, costMinor: Long?, unit: String, trackStock: Boolean, minStockMilli: Long?): ProductInputDto =
        ProductInputDto(
            barcode = old?.barcode, shortCode = old?.shortCode, name = name.trim(), variant = old?.variant, categoryId = old?.categoryId, unit = unit,
            pricing = old?.pricing ?: "FIXED", priceMinor = priceMinor, costMinor = costMinor, isQuick = old?.isQuick ?: false,
            quickPosition = old?.quickPosition, color = old?.color, trackStock = trackStock, minStockMilli = minStockMilli, active = old?.active ?: true,
        )

    /** Dar de baja o reactivar: todo igual, solo cambia `active`. */
    fun withActive(old: ProductEntity, active: Boolean): ProductInputDto =
        build(old, old.name, old.priceMinor, old.costMinor, old.unit, old.trackStock, old.minStockMilli).copy(active = active)

    /** Marcar/quitar de frecuentes u ordenarlos: todo igual, solo cambian `isQuick` y `quickPosition`. */
    fun withFrequent(old: ProductEntity, frequent: Boolean, position: Int?): ProductInputDto =
        build(old, old.name, old.priceMinor, old.costMinor, old.unit, old.trackStock, old.minStockMilli).copy(isQuick = frequent, quickPosition = position)

    /**
     * Cambio PARCIAL para el servidor (`PRODUCT_PATCH`): solo los campos que cambiaron respecto de lo que el teléfono tenía, más la revisión base.
     * Así un teléfono con datos viejos que solo reordena frecuentes o corrige el nombre no revierte un precio o costo más nuevos. Nulo = nada cambió.
     */
    fun patch(old: ProductEntity, input: ProductInputDto): JsonObject? {
        val set = LinkedHashMap<String, JsonElement>()
        fun put(field: String, before: Any?, after: Any?) {
            if (before == after) return
            set[field] = when (after) {
                null -> JsonNull
                is String -> JsonPrimitive(after)
                is Number -> JsonPrimitive(after)
                is Boolean -> JsonPrimitive(after)
                else -> JsonPrimitive(after.toString())
            }
        }
        put("barcode", old.barcode, input.barcode)
        put("shortCode", old.shortCode, input.shortCode)
        put("name", old.name, input.name)
        put("variant", old.variant, input.variant)
        put("categoryId", old.categoryId, input.categoryId)
        put("unit", old.unit, input.unit)
        put("pricing", old.pricing, input.pricing)
        put("priceMinor", old.priceMinor, input.priceMinor)
        put("costMinor", old.costMinor, input.costMinor)
        put("isQuick", old.isQuick, input.isQuick)
        put("quickPosition", old.quickPosition, input.quickPosition)
        put("color", old.color, input.color)
        put("trackStock", old.trackStock, input.trackStock)
        put("minStockMilli", old.minStockMilli, input.minStockMilli)
        put("active", old.active, input.active)
        if (set.isEmpty()) return null
        return JsonObject(mapOf("set" to JsonObject(set), "baseRev" to JsonPrimitive(old.rev)))
    }
}

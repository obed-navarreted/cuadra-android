package com.cuadra.caja.domain

import java.util.UUID

/** Una línea del recibo. `id` lo genera el teléfono (UUID) y viaja tal cual al servidor. */
data class CartLine(
    val id: String,
    val productId: String?,
    val barcode: String?,
    val name: String,
    val variant: String?,
    val unitPriceMinor: Long,
    val unitCostMinor: Long?,
    val quantityMilli: Long,
    val discountMinor: Long = 0,
) {
    val totalMinor: Long get() = SaleMath.lineTotal(unitPriceMinor, quantityMilli, discountMinor)
}

/** El recibo en curso. Inmutable: cada operación devuelve uno nuevo, así la pantalla siempre refleja un estado coherente. */
data class Cart(val lines: List<CartLine> = emptyList(), val discountMinor: Long = 0) {

    val subtotalMinor: Long get() = lines.fold(0L) { acc, l -> Math.addExact(acc, l.totalMinor) }
    val totalMinor: Long get() = subtotalMinor - discountMinor.coerceIn(0, subtotalMinor)
    val isEmpty: Boolean get() = lines.isEmpty()

    /** Producto de catálogo con precio fijo: si ya está en la cuenta suma una unidad en vez de duplicar la línea. */
    fun addProduct(
        productId: String, barcode: String?, name: String, variant: String?, priceMinor: Long, costMinor: Long?,
        quantityMilli: Long = 1000, newId: () -> String = { UUID.randomUUID().toString() },
    ): Cart {
        val existing = lines.indexOfFirst { it.productId == productId && it.unitPriceMinor == priceMinor && it.discountMinor == 0L }
        if (existing >= 0) {
            val line = lines[existing]
            return copy(lines = lines.toMutableList().also { it[existing] = line.copy(quantityMilli = Math.addExact(line.quantityMilli, quantityMilli)) })
        }
        return copy(lines = lines + CartLine(newId(), productId, barcode, name, variant, priceMinor, costMinor, quantityMilli))
    }

    /** Venta libre: un monto con descripción opcional ("Varios" si no hay). Nunca se agrupa con otra. */
    fun addFree(amountMinor: Long, description: String?, quantityMilli: Long = 1000, newId: () -> String = { UUID.randomUUID().toString() }): Cart {
        val name = description?.trim().takeUnless { it.isNullOrEmpty() } ?: FREE_NAME
        return copy(lines = lines + CartLine(newId(), null, null, name, null, amountMinor, null, quantityMilli))
    }

    fun setQuantity(lineId: String, quantityMilli: Long): Cart =
        if (quantityMilli <= 0) remove(lineId) else copy(lines = lines.map { if (it.id == lineId) it.copy(quantityMilli = quantityMilli) else it })

    fun changeQuantity(lineId: String, deltaMilli: Long): Cart {
        val line = lines.firstOrNull { it.id == lineId } ?: return this
        return setQuantity(lineId, Math.addExact(line.quantityMilli, deltaMilli))
    }

    fun setPrice(lineId: String, priceMinor: Long): Cart = copy(lines = lines.map { if (it.id == lineId) it.copy(unitPriceMinor = priceMinor) else it })

    fun remove(lineId: String): Cart = copy(lines = lines.filterNot { it.id == lineId })

    fun withDiscount(amountMinor: Long): Cart = copy(discountMinor = amountMinor.coerceIn(0, subtotalMinor))

    companion object {
        /** Nombre de las líneas sin descripción. Es el nombre "de datos" guardado en la venta, no un texto de pantalla. */
        const val FREE_NAME = "Varios"
    }
}

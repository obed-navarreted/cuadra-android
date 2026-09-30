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
    /** Producto que se vende por peso: su cantidad admite decimales al escribirla a mano. (No se guarda en la venta.) */
    val byWeight: Boolean = false,
) {
    val totalMinor: Long get() = SaleMath.lineTotal(unitPriceMinor, quantityMilli, discountMinor)

    /** ¿Se puede escribir una cantidad con decimales? Por peso, con cantidad ya fraccionaria o venta libre del teclado (3 × 25, 0.5 × 40). */
    val allowsDecimals: Boolean get() = byWeight || productId == null || quantityMilli % 1000L != 0L

    /** Nombre con variante, tal como se muestra. */
    val label: String get() = listOfNotNull(name, variant).joinToString(" · ")

    /**
     * ¿Se puede cambiar la descripción? Solo en las líneas agregadas A MANO (teclado, sin producto del catálogo); un producto conserva su nombre
     * (solo cambia su cantidad).
     */
    val descriptionEditable: Boolean get() = productId == null

    /** Lo que se muestra en el campo de descripción al editarla: vacío si la línea quedó con el nombre por omisión («Varios»). */
    val descriptionText: String get() = if (descriptionEditable && name == Cart.FREE_NAME) "" else name
}

/** El recibo en curso. Inmutable: cada operación devuelve uno nuevo, así la pantalla siempre refleja un estado coherente. */
data class Cart(val lines: List<CartLine> = emptyList(), val discountMinor: Long = 0) {

    val subtotalMinor: Long get() = lines.fold(0L) { acc, l -> Math.addExact(acc, l.totalMinor) }
    val totalMinor: Long get() = subtotalMinor - discountMinor.coerceIn(0, subtotalMinor)
    val isEmpty: Boolean get() = lines.isEmpty()

    /** Lo que muestra el botón «Recibo · N»: cuántas líneas tiene el recibo. */
    val lineCount: Int get() = lines.size

    /** Producto de catálogo con precio fijo: si ya está en la cuenta suma una unidad en vez de duplicar la línea. */
    fun addProduct(
        productId: String, barcode: String?, name: String, variant: String?, priceMinor: Long, costMinor: Long?,
        quantityMilli: Long = 1000, newId: () -> String = { UUID.randomUUID().toString() }, byWeight: Boolean = false,
    ): Cart {
        val existing = lines.indexOfFirst { it.productId == productId && it.unitPriceMinor == priceMinor && it.discountMinor == 0L }
        if (existing >= 0) {
            val line = lines[existing]
            return copy(lines = lines.toMutableList().also { it[existing] = line.copy(quantityMilli = Math.addExact(line.quantityMilli, quantityMilli)) })
        }
        return copy(lines = lines + CartLine(newId(), productId, barcode, name, variant, priceMinor, costMinor, quantityMilli, byWeight = byWeight))
    }

    /** Venta libre: un monto con descripción opcional ("Varios" si no hay). Nunca se agrupa con otra. */
    fun addFree(amountMinor: Long, description: String?, quantityMilli: Long = 1000, newId: () -> String = { UUID.randomUUID().toString() }): Cart {
        return copy(lines = lines + CartLine(newId(), null, null, freeName(description), null, amountMinor, null, quantityMilli))
    }

    fun setQuantity(lineId: String, quantityMilli: Long): Cart =
        if (quantityMilli <= 0) remove(lineId) else copy(lines = lines.map { if (it.id == lineId) it.copy(quantityMilli = quantityMilli) else it })

    fun changeQuantity(lineId: String, deltaMilli: Long): Cart {
        val line = lines.firstOrNull { it.id == lineId } ?: return this
        return setQuantity(lineId, Math.addExact(line.quantityMilli, deltaMilli))
    }

    /**
     * Editar una línea antes de cobrar: la cantidad (0 o menos la quita) y, solo si es una línea manual, la descripción (vacía = «Varios»).
     * En un producto del catálogo `description` se ignora.
     */
    fun editLine(lineId: String, description: String?, quantityMilli: Long): Cart {
        if (quantityMilli <= 0) return remove(lineId)
        return copy(lines = lines.map { l ->
            if (l.id != lineId) l
            else l.copy(quantityMilli = quantityMilli, name = if (l.descriptionEditable && description != null) freeName(description) else l.name)
        })
    }

    fun setPrice(lineId: String, priceMinor: Long): Cart = copy(lines = lines.map { if (it.id == lineId) it.copy(unitPriceMinor = priceMinor) else it })

    fun remove(lineId: String): Cart = copy(lines = lines.filterNot { it.id == lineId })

    fun withDiscount(amountMinor: Long): Cart = copy(discountMinor = amountMinor.coerceIn(0, subtotalMinor))

    companion object {
        /** Largo máximo de la descripción de una línea manual. */
        const val MAX_DESCRIPTION = 80

        /** La descripción limpia de una línea manual, o «Varios» si no hay. */
        fun freeName(description: String?): String = description?.trim()?.take(MAX_DESCRIPTION)?.trim().takeUnless { it.isNullOrEmpty() } ?: FREE_NAME

        /** Nombre de las líneas sin descripción. Es el nombre "de datos" guardado en la venta, no un texto de pantalla. */
        const val FREE_NAME = "Varios"
    }
}

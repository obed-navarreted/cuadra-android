package com.cuadra.caja.domain

/** Lo escrito en el teclado ya convertido: `quantityMilli` × `unitPriceMinor`. */
data class EnteredAmount(val quantityMilli: Long, val unitPriceMinor: Long)

/**
 * Teclado numérico de la venta libre. Se escribe un monto ("85"), o con multiplicador ("3 × 25"). Los decimales del monto
 * dependen de la moneda; la cantidad admite hasta 3 (0.750 lb). Es inmutable: cada tecla devuelve un estado nuevo.
 */
data class AmountEntry(val quantity: String = "", val price: String = "", val multiplying: Boolean = false) {

    private val editing: String get() = if (multiplying) price else quantity

    private fun withEditing(value: String) = if (multiplying) copy(price = value) else copy(quantity = value)

    fun digit(c: Char, decimals: Int): AmountEntry {
        require(c in '0'..'9')
        val cur = editing
        // Antes del "×" lo escrito puede ser un monto o una cantidad (0.750 lb): se admiten hasta 3 decimales y `result` valida el resto.
        // El precio (después del "×") solo admite los decimales de la moneda.
        val allowed = if (multiplying) decimals else 3
        val dot = cur.indexOf('.')
        if (dot >= 0 && cur.length - dot - 1 >= allowed) return this
        // Sin ceros a la izquierda ("007" → "7"), pero "0.5" sí.
        val next = if (cur == "0") c.toString() else cur + c
        return if (next.length > 12) this else withEditing(next)
    }

    fun dot(decimals: Int): AmountEntry {
        val cur = editing
        if (cur.contains('.') || (multiplying && decimals == 0)) return this
        return withEditing(if (cur.isEmpty()) "0." else "$cur.")
    }

    /** Pasa a escribir el precio: "3" → "3 ×". Solo si ya hay una cantidad válida. */
    fun times(): AmountEntry = if (!multiplying && quantity.isNotEmpty() && quantity != "0" && !quantity.endsWith(".")) copy(multiplying = true) else this

    fun backspace(): AmountEntry = when {
        multiplying && price.isNotEmpty() -> copy(price = price.dropLast(1))
        multiplying -> copy(multiplying = false)
        else -> copy(quantity = quantity.dropLast(1))
    }

    fun clear() = AmountEntry()

    val isEmpty: Boolean get() = quantity.isEmpty() && price.isEmpty() && !multiplying

    /** El texto tal como se muestra: "85", "3 × 25". */
    val display: String get() = if (multiplying) "$quantity × ${price.ifEmpty { "0" }}" else quantity.ifEmpty { "0" }

    /**
     * Interpreta lo escrito. Sin multiplicador, todo es el precio de una unidad. Devuelve null si falta algo o no es válido
     * (monto 0, demasiados decimales para la moneda).
     */
    fun result(decimals: Int): EnteredAmount? {
        if (multiplying) {
            val qty = Money3.parse(quantity, 3) ?: return null
            val unit = com.cuadra.caja.core.model.Money.parse(price, decimals)?.minor ?: return null
            return if (qty > 0 && unit > 0) EnteredAmount(qty, unit) else null
        }
        val unit = com.cuadra.caja.core.model.Money.parse(quantity, decimals)?.minor ?: return null
        return if (unit > 0) EnteredAmount(1000, unit) else null
    }

    /** Total de lo escrito, para mostrarlo en el botón "Agregar C$ 75". */
    fun totalMinor(decimals: Int): Long? = result(decimals)?.let { runCatching { SaleMath.lineTotal(it.unitPriceMinor, it.quantityMilli) }.getOrNull() }
}

/** Cantidad con hasta N decimales a milésimas ("0.75" → 750). */
internal object Money3 {
    fun parse(text: String, maxDecimals: Int): Long? {
        val clean = text.trim()
        if (!clean.matches(Regex("\\d+(\\.\\d*)?|\\.\\d+"))) return null
        val bd = runCatching { java.math.BigDecimal(if (clean.startsWith(".")) "0$clean" else clean) }.getOrNull() ?: return null
        if (bd.stripTrailingZeros().scale() > maxDecimals) return null
        return runCatching { bd.movePointRight(3).longValueExact() }.getOrNull()
    }
}

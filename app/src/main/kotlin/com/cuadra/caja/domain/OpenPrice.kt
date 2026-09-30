package com.cuadra.caja.domain

/**
 * Lo que se escribe en el diálogo «Precio de …» de un producto de precio abierto. Trabaja con TEXTO y la moneda del negocio (decimales),
 * igual que el efectivo recibido (`CashTender`): sin flotantes.
 * Si arranca con el precio sugerido (`fresh`), la primera tecla lo REEMPLAZA (quien ya sabe que hoy cuesta 45 no quiere «5045»); borrar lo edita.
 */
data class OpenPriceEntry(val text: String = "", val fresh: Boolean = false) {
    fun digit(d: Char, decimals: Int): OpenPriceEntry {
        if (d !in '0'..'9') return this
        val base = if (fresh) "" else text
        return OpenPriceEntry(CashTender.sanitize(base + d, decimals), false)
    }

    fun dot(decimals: Int): OpenPriceEntry {
        if (decimals == 0) return this
        val base = if (fresh) "" else text
        if (base.contains('.')) return copy(fresh = false)
        return OpenPriceEntry(CashTender.sanitize(base.ifEmpty { "0" } + ".", decimals), false)
    }

    fun backspace(): OpenPriceEntry = OpenPriceEntry(text.dropLast(1), false)

    /** El precio escrito en unidad menor; null si está vacío o es 0 (un producto de precio abierto no se regala: se necesita un precio mayor que cero). */
    fun minor(decimals: Int): Long? = CashTender.parseMinor(text, decimals)?.takeIf { it > 0 }

    companion object {
        /** Empieza con el precio sugerido del producto (si tiene) y listo para reemplazarlo. */
        fun start(suggestedMinor: Long, decimals: Int): OpenPriceEntry =
            if (suggestedMinor > 0) OpenPriceEntry(CashTender.toText(suggestedMinor, decimals), fresh = true) else OpenPriceEntry()
    }
}

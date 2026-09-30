package com.cuadra.caja.domain

/** Resultado de leer la cantidad escrita a mano en una línea del recibo. */
sealed interface QuantityEdit {
    /** Cantidad nueva válida, en milésimas. */
    data class Set(val milli: Long) : QuantityEdit

    /** Escribió 0: se quita la línea (la pantalla lo confirma con un botón «Eliminar línea»). */
    data object Remove : QuantityEdit

    /** Vacío, con letras, con decimales donde no caben o demasiado grande. */
    data object Invalid : QuantityEdit
}

/**
 * Cantidad escrita a mano: «15», «0.5», «0,75». Acepta coma o punto; hasta 3 decimales (0.750 lb) y solo si la línea admite decimales
 * (productos por peso, cantidades ya fraccionarias, venta libre). Tope de 100,000 unidades para no desbordar el total.
 */
object QuantityInput {
    const val MAX_MILLI = 100_000_000L
    const val MAX_LENGTH = 10

    fun parse(text: String, allowDecimals: Boolean): QuantityEdit {
        val clean = text.trim().replace(',', '.')
        if (!clean.matches(Regex("\\d+(\\.\\d{0,3})?|\\.\\d{1,3}"))) return QuantityEdit.Invalid
        val bd = runCatching { java.math.BigDecimal(if (clean.startsWith(".")) "0$clean" else clean) }.getOrNull() ?: return QuantityEdit.Invalid
        if (!allowDecimals && bd.stripTrailingZeros().scale() > 0) return QuantityEdit.Invalid
        val milli = runCatching { bd.movePointRight(3).longValueExact() }.getOrNull() ?: return QuantityEdit.Invalid
        return when {
            milli == 0L -> QuantityEdit.Remove
            milli > MAX_MILLI -> QuantityEdit.Invalid
            else -> QuantityEdit.Set(milli)
        }
    }

    /** Lo que se deja escribir en el campo: dígitos y un solo separador (la coma se vuelve punto), con los decimales permitidos. */
    fun sanitize(raw: String, allowDecimals: Boolean): String {
        val sb = StringBuilder()
        var dot = false
        var decimals = 0
        for (ch in raw.replace(',', '.')) {
            when {
                ch.isDigit() && ch in '0'..'9' -> { if (!dot || decimals < 3) { sb.append(ch); if (dot) decimals++ } }
                ch == '.' && allowDecimals && !dot -> { dot = true; sb.append(ch) }
            }
        }
        return sb.toString().take(MAX_LENGTH)
    }

    /** Cantidad legible para prellenar el campo: 15000 → «15», 750 → «0.75». */
    fun toText(milli: Long): String = java.math.BigDecimal.valueOf(milli, 3).stripTrailingZeros().toPlainString()
}

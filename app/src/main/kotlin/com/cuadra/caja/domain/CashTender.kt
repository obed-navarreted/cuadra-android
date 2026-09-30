package com.cuadra.caja.domain

import com.cuadra.caja.core.model.Money

/** Cuánto falta o cuánto se devuelve al recibir `receivedMinor` por una cuenta de `dueMinor`. Todo en unidad menor (enteros). */
data class ChangeResult(val changeMinor: Long, val missingMinor: Long) {
    val enough: Boolean get() = missingMinor == 0L
}

/**
 * Escribir a mano lo recibido en efectivo. Trabaja con TEXTO (lo que ve la persona) y lo convierte a unidad menor sin decimales
 * flotantes: 100.00 − 87.35 = 12.65 exacto.
 */
object CashTender {
    /** Máximo de dígitos enteros: más que eso no es un monto real y evita desbordar el Long. */
    const val MAX_INTEGER_DIGITS = 12

    /**
     * Limpia lo escrito: solo dígitos y un separador decimal (la coma cuenta como punto), sin ceros a la izquierda ("007" → "7", "0.5" se queda),
     * sin más decimales de los que tiene la moneda (con 0 decimales no hay separador). Es idempotente.
     */
    fun sanitize(raw: String, decimals: Int): String {
        val sb = StringBuilder()
        var dot = false
        var afterDot = 0
        for (ch in raw) {
            // Sin decimales en la moneda, lo que venga después del separador se descarta (no se pega a los enteros).
            if ((ch == '.' || ch == ',') && decimals == 0) break
            when {
                ch in '0'..'9' -> when {
                    dot -> if (afterDot < decimals) { sb.append(ch); afterDot++ }
                    else -> sb.append(ch)
                }
                (ch == '.' || ch == ',') && !dot && decimals > 0 -> { dot = true; if (sb.isEmpty()) sb.append('0'); sb.append('.') }
            }
        }
        val text = sb.toString()
        val intPart = text.substringBefore('.')
        val rest = text.substring(intPart.length)
        val trimmed = intPart.trimStart('0').ifEmpty { if (intPart.isEmpty()) "" else "0" }.take(MAX_INTEGER_DIGITS)
        return trimmed + rest
    }

    /** Lo escrito en unidad menor; null si está vacío o no es un monto ("" y "." no lo son; "5." vale 5). */
    fun parseMinor(text: String, decimals: Int): Long? {
        val clean = sanitize(text, decimals)
        if (clean.isEmpty() || clean == ".") return null
        return Money.parse(clean, decimals)?.minor
    }

    /** El monto como texto editable ("12.65", "1000" sin decimales) para rellenar el campo desde un botón. */
    fun toText(minor: Long, decimals: Int): String {
        if (decimals == 0) return minor.toString()
        val scale = pow10(decimals)
        val frac = (minor % scale).toString().padStart(decimals, '0').trimEnd('0')
        return (minor / scale).toString() + if (frac.isEmpty()) "" else ".$frac"
    }

    fun change(dueMinor: Long, receivedMinor: Long): ChangeResult =
        if (receivedMinor >= dueMinor) ChangeResult(receivedMinor - dueMinor, 0) else ChangeResult(0, dueMinor - receivedMinor)

    private fun pow10(n: Int): Long {
        var r = 1L
        repeat(n) { r *= 10 }
        return r
    }
}

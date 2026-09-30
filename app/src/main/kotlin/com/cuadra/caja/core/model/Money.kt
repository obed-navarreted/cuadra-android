package com.cuadra.caja.core.model

import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale

/** Moneda de un negocio: símbolo y decimales. Los montos viajan siempre como enteros en unidad menor. */
data class Currency(val code: String, val symbol: String, val decimals: Int) {
    companion object {
        private val KNOWN = listOf(
            Currency("NIO", "C$", 2), Currency("HNL", "L", 2), Currency("GTQ", "Q", 2), Currency("CRC", "₡", 0),
            Currency("USD", "$", 2), Currency("MXN", "$", 2), Currency("COP", "$", 0), Currency("PEN", "S/", 2),
            Currency("EUR", "€", 2), Currency("DOP", "RD$", 2), Currency("CLP", "$", 0), Currency("PYG", "₲", 0), Currency("ARS", "$", 2),
        ).associateBy { it.code }

        /** Una moneda desconocida se muestra con su código y 2 decimales, nunca falla. */
        fun of(code: String): Currency = KNOWN[code.uppercase()] ?: Currency(code.uppercase(), code.uppercase(), 2)
    }
}

/** Dinero en unidad menor (centavos). Sin Double: no hay errores de redondeo. */
@JvmInline
value class Money(val minor: Long) {

    operator fun plus(other: Money) = Money(Math.addExact(minor, other.minor))
    operator fun minus(other: Money) = Money(Math.subtractExact(minor, other.minor))
    operator fun compareTo(other: Money) = minor.compareTo(other.minor)

    /**
     * Texto con símbolo delante ("C$ 1,234.50"). `locale` debe ser el del PAÍS del negocio (es-NI, es-MX...):
     * el separador decimal lo decide el país donde se vende, no el idioma de la persona que usa la app.
     */
    fun format(currency: Currency, locale: Locale): String {
        val nf = NumberFormat.getNumberInstance(locale).apply {
            minimumFractionDigits = currency.decimals
            maximumFractionDigits = currency.decimals
            isGroupingUsed = true
        }
        val amount = BigDecimal.valueOf(minor).movePointLeft(currency.decimals)
        return "${currency.symbol} ${nf.format(amount)}"
    }

    companion object {
        val ZERO = Money(0)

        /**
         * Convierte lo escrito en el teclado ("85", "85.5", "85,50") a unidad menor.
         * Devuelve null si no es un monto válido, tiene más decimales de los permitidos o desborda.
         */
        fun parse(text: String, decimals: Int): Money? {
            val clean = text.trim().replace(',', '.')
            if (clean.isEmpty() || !clean.matches(Regex("\\d+(\\.\\d*)?|\\.\\d+"))) return null
            val decimal = try { BigDecimal(if (clean.startsWith(".")) "0$clean" else clean) } catch (_: NumberFormatException) { return null }
            if (decimal.stripTrailingZeros().scale() > decimals) return null
            return try {
                Money(decimal.movePointRight(decimals).longValueExact())
            } catch (_: ArithmeticException) {
                null
            }
        }
    }
}

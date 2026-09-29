package com.cuadra.caja.domain

/** Todo lo que movió una caja durante un turno. Mismos conceptos y misma regla que el servidor (`ShiftService.breakdown`). */
data class ClosingBreakdown(
    val cashSalesMinor: Long = 0, val cashSalesCount: Int = 0, val creditPaymentsCashMinor: Long = 0, val depositsMinor: Long = 0,
    val expensesCashMinor: Long = 0, val withdrawalsMinor: Long = 0,
    /** Lo que entró por otros medios: no toca el cajón, pero el cierre lo muestra aparte. */
    val transferMinor: Long = 0, val cardMinor: Long = 0, val otherMinor: Long = 0, val creditNewMinor: Long = 0, val cancelledCount: Int = 0,
)

enum class ClosingResult { BALANCED, SHORT, OVER }

object CashClosing {
    /**
     * esperado = fondo inicial + ventas en efectivo + abonos de fiado en efectivo + entradas − gastos pagados del cajón − retiros.
     * Ventas en efectivo es lo que cubrió la venta (sin el vuelto); las ventas por transferencia, tarjeta o fiado no entran.
     */
    fun expected(openingFloatMinor: Long, b: ClosingBreakdown): Long =
        openingFloatMinor + b.cashSalesMinor + b.creditPaymentsCashMinor + b.depositsMinor - b.expensesCashMinor - b.withdrawalsMinor

    /** contado − esperado: negativo = faltante, positivo = sobrante. */
    fun difference(countedMinor: Long, expectedMinor: Long): Long = countedMinor - expectedMinor

    fun result(differenceMinor: Long): ClosingResult = when {
        differenceMinor == 0L -> ClosingResult.BALANCED
        differenceMinor < 0 -> ClosingResult.SHORT
        else -> ClosingResult.OVER
    }

    /** Si el negocio fija un umbral, una diferencia mayor exige nota. Sin umbral nunca se exige (igual que el servidor). */
    fun needsNote(differenceMinor: Long, thresholdMinor: Long?, note: String?): Boolean =
        thresholdMinor != null && kotlin.math.abs(differenceMinor) > thresholdMinor && note.isNullOrBlank()
}

/** Billetes y monedas para contar el efectivo, según la moneda del negocio. */
object Denominations {
    private val DEFAULT = listOf(1000L, 500L, 200L, 100L, 50L, 20L, 10L, 5L, 1L)
    private val BY_CURRENCY = mapOf(
        "NIO" to DEFAULT,
        "USD" to listOf(100L, 50L, 20L, 10L, 5L, 1L),
        "EUR" to listOf(200L, 100L, 50L, 20L, 10L, 5L, 2L, 1L),
        "GTQ" to listOf(200L, 100L, 50L, 20L, 10L, 5L, 1L),
        "HNL" to listOf(500L, 200L, 100L, 50L, 20L, 10L, 5L, 2L, 1L),
        "CRC" to listOf(50000L, 20000L, 10000L, 5000L, 2000L, 1000L, 500L, 100L),
        "MXN" to listOf(1000L, 500L, 200L, 100L, 50L, 20L, 10L, 5L, 2L, 1L),
        "COP" to listOf(100000L, 50000L, 20000L, 10000L, 5000L, 2000L, 1000L, 500L, 200L, 100L),
        "PEN" to listOf(200L, 100L, 50L, 20L, 10L, 5L, 2L, 1L),
    )

    /** Denominaciones de mayor a menor, en la unidad menor de la moneda (centavos). */
    fun forCurrency(code: String, decimals: Int): List<Long> {
        var scale = 1L
        repeat(decimals) { scale *= 10 }
        return (BY_CURRENCY[code.uppercase()] ?: DEFAULT).map { it * scale }
    }

    fun total(counts: Map<Long, Int>): Long = counts.entries.sumOf { (value, n) -> value * n.coerceAtLeast(0) }

    /** Guardado en el turno (JSON simple `{"10000":3,"5000":2}`) para poder ver después con qué billetes se contó. */
    fun toJson(counts: Map<Long, Int>): String =
        counts.filterValues { it > 0 }.entries.sortedByDescending { it.key }.joinToString(",", "{", "}") { "\"${it.key}\":${it.value}" }
}

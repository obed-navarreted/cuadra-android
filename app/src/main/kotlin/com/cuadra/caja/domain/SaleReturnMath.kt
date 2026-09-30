package com.cuadra.caja.domain

import java.math.BigInteger

/** Una línea de una venta cobrada, con lo ya devuelto (cantidad y monto), para calcular una devolución. */
data class ReturnableLine(val id: String, val name: String, val quantityMilli: Long, val lineTotalMinor: Long, val returnedMilli: Long = 0, val returnedMinor: Long = 0) {
    val remainingMilli: Long get() = (quantityMilli - returnedMilli).coerceAtLeast(0)
}

/** CASH: sale del cajón · SAME: por los mismos medios del pago · CREDIT_NOTE: baja el fiado de la venta. */
enum class RefundMethod { CASH, SAME, CREDIT_NOTE }

/**
 * La cuenta de una devolución, IGUAL a la del servidor (`ReturnService`): el descuento de la cuenta se reparte entre las líneas en proporción (el residuo a la
 * última); devolver TODO lo que queda de una línea devuelve exactamente su neto pendiente; una parte, en proporción (mitad hacia arriba). Nunca más de lo
 * cobrado. El servidor recalcula y manda; esto es lo que se muestra antes y lo que se guarda sin conexión.
 */
object SaleReturnMath {
    const val MIN_REASON = 5

    fun mulDivHalfUp(a: Long, b: Long, c: Long): Long {
        if (c == 0L) return 0
        val qr = BigInteger.valueOf(a).multiply(BigInteger.valueOf(b)).divideAndRemainder(BigInteger.valueOf(c))
        val q = qr[0].toLong()
        return if (qr[1].shiftLeft(1) >= BigInteger.valueOf(c)) q + 1 else q
    }

    fun lineNets(lineTotals: List<Long>, subtotalMinor: Long, discountMinor: Long): List<Long> {
        var allocated = 0L
        return lineTotals.mapIndexed { i, total ->
            val share = if (i == lineTotals.lastIndex) discountMinor - allocated else if (subtotalMinor == 0L) 0 else mulDivHalfUp(discountMinor, total, subtotalMinor)
            allocated += share
            (total - share).coerceAtLeast(0)
        }
    }

    /** Monto por línea de lo elegido (`milli` por id de línea; se ignora lo que pase de lo que queda). */
    fun amounts(lines: List<ReturnableLine>, subtotalMinor: Long, discountMinor: Long, milli: Map<String, Long>): Map<String, Long> {
        val nets = lineNets(lines.map { it.lineTotalMinor }, subtotalMinor, discountMinor)
        val out = LinkedHashMap<String, Long>()
        lines.forEachIndexed { i, l ->
            val q = (milli[l.id] ?: 0).coerceAtMost(l.remainingMilli)
            if (q <= 0) return@forEachIndexed
            out[l.id] = (if (q == l.remainingMilli) nets[i] - l.returnedMinor else mulDivHalfUp(nets[i], q, l.quantityMilli)).coerceAtLeast(0)
        }
        return out
    }

    fun total(lines: List<ReturnableLine>, subtotalMinor: Long, discountMinor: Long, totalMinor: Long, alreadyReturnedMinor: Long, milli: Map<String, Long>): Long =
        amounts(lines, subtotalMinor, discountMinor, milli).values.sum().coerceAtMost((totalMinor - alreadyReturnedMinor).coerceAtLeast(0))

    /** Algo elegido pasa de lo que queda por devolver en su línea. */
    fun exceeds(lines: List<ReturnableLine>, milli: Map<String, Long>): Boolean = lines.any { (milli[it.id] ?: 0) > it.remainingMilli }

    fun reasonOk(reason: String): Boolean = reason.trim().length >= MIN_REASON

    /** La nota de crédito solo si la venta fue (en parte) a fiado. */
    fun methods(paymentMethods: List<String>): List<RefundMethod> =
        if ("CREDIT" in paymentMethods) RefundMethod.entries else listOf(RefundMethod.CASH, RefundMethod.SAME)

    /** Se puede devolver algo: cobrada y con alguna línea pendiente. */
    fun canReturn(status: String, lines: List<ReturnableLine>): Boolean = status == "COMPLETED" && lines.any { it.remainingMilli > 0 }

    /**
     * Quién puede devolver: dueño y admins, cualquier venta cobrada; un cajero, solo las SUYAS y de la MISMA jornada (el servidor lo exige igual:
     * RETURN_NOT_ALLOWED).
     */
    fun allowed(role: String?, soldByMe: Boolean, sameBusinessDay: Boolean): Boolean = role == "OWNER" || role == "ADMIN" || (role == "CASHIER" && soldByMe && sameBusinessDay)
}

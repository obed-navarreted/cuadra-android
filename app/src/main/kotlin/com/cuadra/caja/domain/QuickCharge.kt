package com.cuadra.caja.domain

/**
 * Cobro rápido desde la calculadora (decisión del dueño): cuando el cliente se lleva una sola cosa, se escribe el monto y se toca «Cobrar» sin pasar por «Agregar».
 * Lo que la calculadora tiene en este momento es el MONTO PENDIENTE: se interpreta exactamente como lo hace «Agregar» (`AmountEntry.result`, mismos límites, misma
 * línea «Varios» sin agrupar) y, al cobrar, apartar o enviar a caja, entra primero como una línea del recibo.
 */
object QuickCharge {
    /** Lo escrito listo para entrar al recibo: precio, cantidad y lo que suma (`totalMinor`). */
    data class Pending(val unitPriceMinor: Long, val quantityMilli: Long, val totalMinor: Long)

    /**
     * Lo pendiente de la calculadora, o `null` si no hay nada que cobrar: vacía, en 0, una expresión incompleta («5×»), con demasiados decimales, o un monto que solo
     * o sumado al recibo (`cartTotalMinor`) pasa del límite de un importe.
     */
    fun pending(entry: AmountEntry, decimals: Int, cartTotalMinor: Long = 0): Pending? {
        val r = entry.result(decimals) ?: return null
        val total = runCatching { SaleMath.lineTotal(r.unitPriceMinor, r.quantityMilli) }.getOrNull() ?: return null
        if (total <= 0) return null
        val sum = runCatching { Math.addExact(cartTotalMinor, total) }.getOrNull() ?: return null
        if (sum > SaleMath.MAX_MINOR) return null
        return Pending(r.unitPriceMinor, r.quantityMilli, total)
    }

    /** Lo que se cobrará: el recibo más lo pendiente (0 si no hay). */
    fun total(cartTotalMinor: Long, pending: Pending?): Long = cartTotalMinor + (pending?.totalMinor ?: 0)

    /** «Cobrar», «Apartar» y «Enviar a caja» se encienden con líneas en el recibo o con algo pendiente (y, para cobrar, que sume más de 0). */
    fun canCharge(cartTotalMinor: Long, pending: Pending?): Boolean = total(cartTotalMinor, pending) > 0
    fun canHold(cartEmpty: Boolean, pending: Pending?): Boolean = !cartEmpty || pending != null
}

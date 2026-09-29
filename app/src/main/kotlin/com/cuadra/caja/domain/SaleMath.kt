package com.cuadra.caja.domain

import java.math.BigInteger

/**
 * Aritmética de la venta: idéntica a la del servidor (`SaleMath.java`) para que el total que se muestra en la caja
 * sea el que el servidor va a aceptar. Enteros en unidad menor y cantidades en milésimas; sin Double.
 */
object SaleMath {
    const val MAX_MINOR = 1_000_000_000_000L
    private val THOUSAND = BigInteger.valueOf(1000)

    /** round_half_up(precio × cantidad / 1000) − descuento. Lanza si el resultado sería negativo o desbordado. */
    fun lineTotal(unitPriceMinor: Long, quantityMilli: Long, discountMinor: Long = 0): Long {
        val (q, r) = BigInteger.valueOf(unitPriceMinor).multiply(BigInteger.valueOf(quantityMilli)).divideAndRemainder(THOUSAND)
        val gross = if (r.shiftLeft(1) >= THOUSAND) q + BigInteger.ONE else q
        val net = gross - BigInteger.valueOf(discountMinor)
        require(net.signum() >= 0) { "El descuento supera el importe de la línea" }
        require(net <= BigInteger.valueOf(MAX_MINOR)) { "Importe demasiado grande" }
        return net.toLong()
    }

    /**
     * Cantidad (milésimas) que corresponde a un monto para un producto por peso: "C$ 70 de queso a C$ 90 la libra" → 0.778 lb.
     * Redondea al más cercano; devuelve null si el precio es 0 o el monto es tan pequeño que no llega a una milésima.
     */
    fun quantityForAmount(amountMinor: Long, unitPriceMinor: Long): Long? {
        if (unitPriceMinor <= 0 || amountMinor <= 0) return null
        val num = BigInteger.valueOf(amountMinor).multiply(THOUSAND)
        val den = BigInteger.valueOf(unitPriceMinor)
        val (q, r) = num.divideAndRemainder(den)
        val milli = if (r.shiftLeft(1) >= den) q + BigInteger.ONE else q
        return if (milli.signum() <= 0 || milli > BigInteger.valueOf(999_999_999L)) null else milli.toLong()
    }
}

package com.cuadra.caja.domain

import com.cuadra.caja.core.model.Money

/**
 * El límite de crédito de un cliente en el campo del editor. Se guarda en unidad menor (centavos) y se muestra/escribe como monto normal ("500",
 * "1250.5"): la ida y la vuelta usan la MISMA escala, así abrir el editor y guardar sin cambios conserva el límite (antes se cargaba "50000" y se leía
 * como C$ 50 000).
 */
object CreditLimitField {
    /** Unidad menor → texto del campo; vacío si no hay límite. */
    fun toText(limitMinor: Long?, decimals: Int): String = limitMinor?.let { CashTender.toText(it, decimals) }.orEmpty()

    /** Texto del campo → unidad menor; null si está vacío o no es un monto. */
    fun parse(text: String, decimals: Int): Long? = text.takeIf { it.isNotBlank() }?.let { Money.parse(it, decimals)?.minor }
}

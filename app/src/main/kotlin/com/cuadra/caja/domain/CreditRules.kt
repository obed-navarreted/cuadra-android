package com.cuadra.caja.domain

import java.time.LocalDate

/**
 * Reglas de fiado que el dueño fija en «Ajustes del negocio» y que la caja debe respetar ANTES de cobrar (el servidor rechazaría la venta a destiempo,
 * ya con el cliente lejos). `requiresCustomer`: todo fiado lleva un cliente de la lista; `limitEnforced`: el límite de crédito del cliente bloquea en vez de avisar.
 */
object CreditRules {
    enum class Block { CUSTOMER_REQUIRED, OVER_LIMIT }

    fun block(
        requiresCustomer: Boolean, limitEnforced: Boolean, hasCustomer: Boolean, saveAsCustomer: Boolean,
        limitMinor: Long?, balanceMinor: Long, amountMinor: Long,
    ): Block? = when {
        // «Guardar como cliente» crea el cliente al cobrar: cuenta como cliente.
        requiresCustomer && !hasCustomer && !saveAsCustomer -> Block.CUSTOMER_REQUIRED
        limitEnforced && hasCustomer && limitMinor != null && balanceMinor + amountMinor > limitMinor -> Block.OVER_LIMIT
        else -> null
    }

    /** Vencimiento de un fiado nuevo: la jornada de la venta más los días por defecto del negocio (nulo = sin fecha). */
    fun dueDate(defaultDueDays: Int?, saleDay: LocalDate): LocalDate? = defaultDueDays?.let { saleDay.plusDays(it.toLong()) }
}

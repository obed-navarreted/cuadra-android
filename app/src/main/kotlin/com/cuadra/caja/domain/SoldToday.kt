package com.cuadra.caja.domain

/**
 * «Vendido hoy» del encabezado de la caja: con un CAJERO atendiendo, solo lo que cobró ÉL en la jornada (no ve lo del resto del negocio, igual que en Ventas y en
 * el resumen del servidor); el dueño y los admins ven el total del negocio que conoce este teléfono.
 */
object SoldToday {
    /** De quién se suman las ventas: el id de la persona (cajero) o `null` = todo el negocio. */
    fun memberFilter(role: String?, memberId: String?): String? = if (role == "CASHIER" && memberId != null) memberId else null
}

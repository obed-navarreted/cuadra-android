package com.cuadra.caja.domain

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Visibilidad del cajero (docs/adr/0017): un CAJERO ve solo sus propias ventas (las que tomó o cobró) de los últimos 60 minutos; dueño y admin, todo. El servidor lo
 * hace cumplir en REST; el teléfono baja el negocio completo (el cursor es del teléfono, no de la persona) y por eso cada lectura LOCAL de ventas pasa por aquí.
 * Las cuentas apartadas u abiertas no son ventas todavía (son del negocio, para retomarlas): se ven siempre, como en el servidor.
 * Una venta cobrada hace exactamente 60:00 todavía se ve; a los 60:01 ya no. La persona que cambia de cajero a admin vuelve a ver todo (no se guarda nada filtrado).
 */
object CashierWindow {
    const val MINUTES = 60L
    const val MILLIS = MINUTES * 60_000L

    /** Cada cuánto se vuelve a evaluar la ventana mientras la pantalla está a la vista (las filas viejas salen solas). */
    const val TICK_MILLIS = 20_000L

    fun applies(role: String?): Boolean = role == "CASHIER"

    /** Desde cuándo se ve (inclusive). */
    fun since(nowMillis: Long): Long = nowMillis - MILLIS

    fun within(atMillis: Long?, nowMillis: Long): Boolean = atMillis != null && atMillis >= since(nowMillis)

    /** ¿Esta venta local la ve esta persona ahora? `completedAt ?: createdAt` es la hora que cuenta. */
    fun sees(
        role: String?, memberId: String?, status: String, createdBy: String?, completedBy: String?, completedAt: Long?, createdAt: Long, nowMillis: Long,
    ): Boolean {
        if (!applies(role)) return true
        if (status == "PARKED" || status == "OPEN") return true
        val own = memberId != null && (createdBy == memberId || completedBy == memberId)
        return own && within(completedAt ?: createdAt, nowMillis)
    }

    /** La hora actual, ahora y cada `TICK_MILLIS`: lo que hace que las filas envejezcan solas. */
    fun ticker(now: () -> Long = System::currentTimeMillis, every: Long = TICK_MILLIS): Flow<Long> = flow {
        while (true) { emit(now()); delay(every) }
    }
}

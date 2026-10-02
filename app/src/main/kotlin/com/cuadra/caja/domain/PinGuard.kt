package com.cuadra.caja.domain

/**
 * Bloqueo por intentos de PIN fallidos (PLAN.md 4.4): 5 fallos → espera de 30 s, que se duplica con cada nuevo fallo hasta 15 min.
 * Vive en memoria; un reinicio de la app reinicia el contador (el servidor además avisa a los admins de los bloqueos).
 */
class PinGuard(private val now: () -> Long = System::currentTimeMillis) {
    private var failures = 0
    private var lockedUntil = 0L

    /** Milisegundos que faltan para poder intentar de nuevo; 0 si se puede. */
    fun waitMillis(): Long = (lockedUntil - now()).coerceAtLeast(0)

    fun isLocked() = waitMillis() > 0

    /** Devuelve true si este fallo activó (o extendió) el bloqueo. */
    fun recordFailure(): Boolean {
        failures++
        if (failures < MAX_ATTEMPTS) return false
        val steps = failures - MAX_ATTEMPTS
        lockedUntil = now() + (BASE_LOCK_MS shl steps.coerceAtMost(5)).coerceAtMost(MAX_LOCK_MS)
        return true
    }

    /** Al cerrar sesión o cambiar de negocio: el conteo de fallos era de otra sesión. */
    fun reset() = recordSuccess()

    fun recordSuccess() {
        failures = 0
        lockedUntil = 0
    }

    companion object {
        const val MAX_ATTEMPTS = 5
        const val BASE_LOCK_MS = 30_000L
        const val MAX_LOCK_MS = 15 * 60_000L
    }
}

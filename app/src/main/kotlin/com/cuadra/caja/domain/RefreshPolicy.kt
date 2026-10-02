package com.cuadra.caja.domain

/**
 * Las listas se refrescan CADA VEZ que se abren (decisión del propietario): nunca se ve una lista vieja hasta tocar un filtro. Por qué se pide refrescar:
 * - SHOWN: se abrió la pantalla (sección de la barra, pantalla de Más, «Por cobrar en caja» o Apartadas);
 * - RESUMED: la app volvió al frente con esa pantalla abierta;
 * - PERSON_CHANGED: entró otra persona con su PIN;
 * - PULLED: la persona deslizó hacia abajo («tirar para refrescar»);
 * - PERIODIC: «Por cobrar en caja» se refresca sola mientras está a la vista (los meseros envían cuentas desde otros teléfonos).
 */
enum class RefreshTrigger { SHOWN, RESUMED, PERSON_CHANGED, PULLED, PERIODIC }

/**
 * Antirrebote: toques rápidos no disparan llamadas repetidas. Nunca dos a la vez; y, salvo que la persona lo pida a propósito (deslizar) o cambie la persona,
 * no otra dentro de `minGapMillis` desde la anterior. Sin Android: se prueba con un reloj falso.
 */
class RefreshThrottle(private val minGapMillis: Long = 2_000L, private val now: () -> Long = System::currentTimeMillis) {
    private var inFlight = false
    private var lastStart: Long? = null

    @Synchronized
    fun tryStart(trigger: RefreshTrigger): Boolean {
        if (inFlight) return false
        val last = lastStart
        val forced = trigger == RefreshTrigger.PULLED || trigger == RefreshTrigger.PERSON_CHANGED
        if (!forced && last != null && now() - last < minGapMillis) return false
        inFlight = true
        lastStart = now()
        return true
    }

    @Synchronized
    fun finish() { inFlight = false }
}

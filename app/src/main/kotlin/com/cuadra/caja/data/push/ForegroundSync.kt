package com.cuadra.caja.data.push

import com.cuadra.caja.domain.PushPolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * La sincronización con la app a la vista: al volver a la app, al abrir la caja, cuando llega un aviso de Firebase y, de respaldo, cada 30 s si los avisos al
 * instante no funcionan en este teléfono (cada 5 min si sí). Barata: una bajada sin cambios es una consulta corta, y nunca corren dos a la vez
 * (`SyncCoordinator`).
 */
class ForegroundSync(
    private val scope: CoroutineScope, private val run: suspend () -> Unit, private val pushActive: () -> Boolean,
    private val now: () -> Long = System::currentTimeMillis,
) {
    @Volatile var visible: Boolean = false
        private set
    @Volatile private var lastKick = 0L
    private var loop: Job? = null

    /** La app volvió al frente: sincroniza ya y empieza el respaldo periódico. */
    @Synchronized
    fun onForeground() {
        visible = true
        kick()
        if (loop?.isActive == true) return
        loop = scope.launch {
            while (isActive && visible) {
                delay(PushPolicy.pollEvery(pushActive()))
                if (visible) runCatching { run() }
            }
        }
    }

    @Synchronized
    fun onBackground() {
        visible = false
        loop?.cancel()
        loop = null
    }

    /** Abrir la caja (o volver al frente): una sincronización, salvo que haya habido otra hace un instante. */
    fun kick() {
        val t = now()
        if (t - lastKick < PushPolicy.KICK_GAP_MILLIS) return
        lastKick = t
        scope.launch { runCatching { run() } }
    }

    /** Llegó un aviso de Firebase con la app a la vista: se sincroniza ya, sin esperar. */
    fun pushed() {
        lastKick = now()
        scope.launch { runCatching { run() } }
    }
}

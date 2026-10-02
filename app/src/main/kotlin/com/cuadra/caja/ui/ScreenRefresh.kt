package com.cuadra.caja.ui

import com.cuadra.caja.domain.RefreshThrottle
import com.cuadra.caja.domain.RefreshTrigger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Cómo terminó un refresco: con o sin conexión (sin conexión se muestra lo guardado y «Sin conexión», nunca un error). */
enum class RefreshResult { DONE, OFFLINE }

/**
 * El refresco de UNA pantalla (ver `RefreshTrigger`): mientras corre, `refreshing` (la pantalla deja lo que ya tenía a la vista con un indicador pequeño, sin
 * vaciarse) y, al terminar, `offline`. Con antirrebote (`RefreshThrottle`). `work` hace el trabajo: recargar del servidor, sincronizar, o ambas.
 */
class ScreenRefresh(
    private val scope: CoroutineScope,
    private val throttle: RefreshThrottle = RefreshThrottle(),
    private val work: suspend (RefreshTrigger) -> RefreshResult,
) {
    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()
    private val _offline = MutableStateFlow(false)
    val offline: StateFlow<Boolean> = _offline.asStateFlow()

    /** Pide un refresco; devuelve si se hizo (false = descartado por el antirrebote). */
    fun request(trigger: RefreshTrigger): Boolean {
        if (!throttle.tryStart(trigger)) return false
        _refreshing.value = true
        scope.launch {
            try {
                _offline.value = runCatching { work(trigger) }.getOrDefault(RefreshResult.DONE) == RefreshResult.OFFLINE
            } finally {
                throttle.finish()
                _refreshing.value = false
            }
        }
        return true
    }
}

/** Sincroniza ahora (sube lo pendiente y baja lo nuevo): el refresco de las pantallas que muestran datos del teléfono. */
suspend fun com.cuadra.caja.AppContainer.pullNow(): RefreshResult =
    if (sync.run() is com.cuadra.caja.data.sync.SyncResult.Offline) RefreshResult.OFFLINE else RefreshResult.DONE

package com.cuadra.caja.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cuadra.caja.AppContainer
import com.cuadra.caja.data.local.NotificationEntity
import com.cuadra.caja.data.remote.ApiFailure
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Preferencias de avisos de esta persona (se cargan del servidor: solo con conexión). `prefs == null` mientras carga o si falló. */
data class PrefsUi(val open: Boolean = false, val loading: Boolean = false, val offline: Boolean = false, val prefs: Map<String, Boolean>? = null)

/** Orden en que se muestran los tipos en las preferencias. */
val NOTIFICATION_TYPES = listOf(
    "LOW_STOCK", "OUT_OF_STOCK", "SHIFT_CLOSED", "SHIFT_DIFFERENCE", "SHIFT_NOT_CLOSED", "SALE_DELETED", "DEVICE_STALE", "PIN_LOCKOUT", "MEMBER_JOINED", "DAILY_SUMMARY", "SCHEDULED",
)

class NotificationsViewModel(private val c: AppContainer) : ViewModel() {
    val items: StateFlow<List<NotificationEntity>> = c.notifications.inbox().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val unread: StateFlow<Int> = c.notifications.unreadCount().stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    private val _prefs = MutableStateFlow(PrefsUi())
    val prefs: StateFlow<PrefsUi> = _prefs.asStateFlow()

    /** Marca como leída y devuelve el enlace al que lleva (o a la propia bandeja si no trae). */
    fun open(n: NotificationEntity): String? {
        viewModelScope.launch { c.notifications.markRead(n.id) }
        return n.deepLink
    }

    fun markAllRead() {
        viewModelScope.launch { c.notifications.markAllRead() }
    }

    fun openPrefs() {
        _prefs.value = PrefsUi(open = true, loading = true)
        viewModelScope.launch {
            c.schedules.preferences().fold(
                onSuccess = { p -> _prefs.update { it.copy(loading = false, offline = false, prefs = p) } },
                onFailure = { e -> _prefs.update { it.copy(loading = false, offline = e is ApiFailure.Offline) } },
            )
        }
    }

    fun closePrefs() {
        _prefs.value = PrefsUi()
    }

    fun toggle(type: String, enabled: Boolean) {
        // Cambio inmediato en pantalla; si el servidor no lo acepta, vuelve al valor real.
        _prefs.update { it.copy(prefs = it.prefs?.plus(type to enabled)) }
        viewModelScope.launch {
            c.schedules.setPreference(type, enabled).fold(
                onSuccess = { p -> _prefs.update { it.copy(prefs = p, offline = false) } },
                onFailure = { e -> _prefs.update { it.copy(prefs = it.prefs?.plus(type to !enabled), offline = e is ApiFailure.Offline) } },
            )
        }
    }
}

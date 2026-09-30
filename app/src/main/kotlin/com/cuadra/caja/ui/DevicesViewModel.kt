package com.cuadra.caja.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cuadra.caja.AppContainer
import com.cuadra.caja.R
import com.cuadra.caja.data.remote.DeviceDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DevicesUi(
    val loading: Boolean = true,
    val devices: List<DeviceDto> = emptyList(),
    val loadError: ErrorMessage? = null,
    /** El teléfono de esta persona: no se puede revocar desde aquí (se quedaría sin acceso). */
    val thisDeviceId: String? = null,
    val revoking: DeviceDto? = null,
    val revokeError: ErrorMessage? = null,
    val notice: ErrorMessage? = null,
)

/** Teléfonos vinculados al negocio (dueño y admin): lista y revocar. Cada persona que entra con el código del negocio aparece aquí (ADR 0012). */
class DevicesViewModel(private val c: AppContainer) : ViewModel(), DevicesActions {
    private val _ui = MutableStateFlow(DevicesUi())
    val ui: StateFlow<DevicesUi> = _ui.asStateFlow()
    val business = c.db.directory().business().stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        viewModelScope.launch { c.sessionStore.flow.collect { s -> _ui.update { it.copy(thisDeviceId = s.deviceId) } } }
    }

    override fun enter() {
        _ui.update { it.copy(notice = null, revoking = null) }
        load()
    }

    override fun load() {
        _ui.update { it.copy(loading = true, loadError = null) }
        viewModelScope.launch {
            c.team.devices().fold(
                onSuccess = { list -> _ui.update { it.copy(loading = false, devices = list) } },
                onFailure = { e -> _ui.update { it.copy(loading = false, loadError = e.teamError()) } },
            )
        }
    }

    override fun dismissNotice() = _ui.update { it.copy(notice = null) }

    override fun askRevoke(d: DeviceDto) = _ui.update { it.copy(revoking = d, revokeError = null) }
    override fun cancelRevoke() = _ui.update { it.copy(revoking = null, revokeError = null) }

    override fun confirmRevoke() {
        val d = _ui.value.revoking ?: return
        viewModelScope.launch {
            c.team.revokeDevice(d.id).fold(
                onSuccess = { _ui.update { it.copy(revoking = null, notice = ErrorMessage(R.string.dev_revoked, detail = d.name)) }; load() },
                onFailure = { e -> _ui.update { it.copy(revokeError = e.teamError()) } },
            )
        }
    }
}

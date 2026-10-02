package com.cuadra.caja.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cuadra.caja.AppContainer
import com.cuadra.caja.R
import com.cuadra.caja.data.local.BusinessEntity
import com.cuadra.caja.data.remote.ApiFailure
import com.cuadra.caja.data.remote.UpdateBusinessBody
import com.cuadra.caja.domain.BusinessSettingsRules
import com.cuadra.caja.domain.DeleteBusinessRules
import com.cuadra.caja.domain.NotifyDraft
import com.cuadra.caja.domain.NotifyRules
import com.cuadra.caja.domain.SettingsDraft
import com.cuadra.caja.domain.SettingsError
import com.cuadra.caja.domain.TeamRules
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** La hoja de «Eliminar el negocio»: nombre escrito, casilla «Entiendo que…» y el resultado de la llamada. */
data class DeleteUi(val typed: String = "", val understood: Boolean = false, val busy: Boolean = false, val error: ErrorMessage? = null)

/** Cuentas por cobrar en caja que se anularían al apagar «Cobro en caja» (lo dice el servidor). */
data class DiscardQueue(val count: Int, val totalMinor: Long)

data class SettingsUi(
    /** Se está pidiendo el negocio al servidor. */
    val loading: Boolean = true,
    val offline: Boolean = false,
    val loadError: ErrorMessage? = null,
    val role: String? = null,
    /** Lo guardado (lo último que dijo el servidor) y lo que se está editando: solo lo que difiere se envía. */
    val base: SettingsDraft? = null,
    val draft: SettingsDraft? = null,
    val notifyBase: NotifyDraft? = null,
    val notify: NotifyDraft? = null,
    val notifyOffline: Boolean = false,
    val notifyLoading: Boolean = false,
    val saving: Boolean = false,
    val notifySaving: Boolean = false,
    val moduleSaving: Boolean = false,
    /** Aviso sobre el formulario: lo que salió bien o el error de la última acción. */
    val notice: TeamNotice? = null,
    /** Qué campo impide guardar (se muestra al intentar guardar). */
    val validation: SettingsError? = null,
    val notifyNotice: TeamNotice? = null,
    /** El aviso de la ADR 0011 antes de guardar un cambio de zona o de corte. */
    val confirmDayRule: Boolean = false,
    /** El aviso de «Desactivar y anular» al apagar el cobro en caja con cuentas pendientes. */
    val discardQueue: DiscardQueue? = null,
    /** Lo escrito en el buscador de zonas; nulo = cerrado. */
    val zoneQuery: String? = null,
    val delete: DeleteUi? = null,
) {
    /** Eliminar el negocio (y el código de acceso) es solo del dueño. */
    val isOwner: Boolean get() = DeleteBusinessRules.canDelete(role)
    /** Dueño y administrador editan los ajustes (ADR 0012, 2026-10-01); el cajero ni siquiera ve la pantalla. */
    val canEdit: Boolean get() = role == TeamRules.OWNER || role == TeamRules.ADMIN
    val patch: UpdateBusinessBody? get() = if (base != null && draft != null) BusinessSettingsRules.patch(draft, base) else null
    val dirty: Boolean get() = patch?.isEmpty == false
    val notifyDirty: Boolean get() = notify != null && notify != notifyBase
}

/**
 * «Ajustes del negocio»: el dueño y los administradores editan (el servidor lo exige igual); solo el dueño elimina el negocio. Guarda con `PUT /b/{id}` solo lo que cambió y actualiza el
 * negocio en Room para que la app entera reaccione (Caja, Fiados, Cierre del día).
 */
class SettingsViewModel(private val c: AppContainer) : ViewModel(), SettingsActions {
    private val _ui = MutableStateFlow(SettingsUi())
    val ui: StateFlow<SettingsUi> = _ui.asStateFlow()
    val business: StateFlow<BusinessEntity?> = c.db.directory().business().stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        viewModelScope.launch { c.sessionStore.flow.collect { s -> _ui.update { it.copy(role = s.memberRole) } } }
        viewModelScope.launch {
            c.db.directory().business().collect { b ->
                // La primera vez (o si no hay nada que perder) el formulario parte de lo que hay en el teléfono; luego lo actualiza `enter`/`save`.
                if (b != null && !_ui.value.dirty) SettingsDraft.of(b).let { d -> _ui.update { it.copy(base = d, draft = d) } }
            }
        }
    }

    override fun enter() {
        _ui.update { it.copy(notice = null, notifyNotice = null, validation = null, confirmDayRule = false, zoneQuery = null, delete = null, loading = true, loadError = null) }
        viewModelScope.launch {
            c.settings.refresh().fold(
                onSuccess = { e -> if (!_ui.value.dirty) SettingsDraft.of(e).let { d -> _ui.update { it.copy(loading = false, offline = false, base = d, draft = d) } } else _ui.update { it.copy(loading = false, offline = false) } },
                onFailure = { e ->
                    val offline = e is ApiFailure.Offline
                    _ui.update { it.copy(loading = false, offline = offline, loadError = if (offline) null else e.settingsError()) }
                },
            )
        }
        loadNotify()
    }

    override fun retry() = enter()

    private fun loadNotify() {
        _ui.update { it.copy(notifyLoading = true) }
        viewModelScope.launch {
            c.settings.notifySettings().fold(
                onSuccess = { s -> if (!_ui.value.notifyDirty) NotifyDraft.of(s).let { d -> _ui.update { it.copy(notifyLoading = false, notifyOffline = false, notifyBase = d, notify = d) } } else _ui.update { it.copy(notifyLoading = false) } },
                onFailure = { e -> _ui.update { it.copy(notifyLoading = false, notifyOffline = e is ApiFailure.Offline) } },
            )
        }
    }

    override fun update(d: SettingsDraft) = _ui.update { it.copy(draft = d, notice = null, validation = null) }
    override fun updateNotify(n: NotifyDraft) = _ui.update { it.copy(notify = n, notifyNotice = null) }

    override fun discard() = _ui.update { it.copy(draft = it.base, validation = null, notice = null) }

    override fun save() {
        val s = _ui.value
        val d = s.draft ?: return
        val base = s.base ?: return
        if (!s.canEdit || s.saving) return
        BusinessSettingsRules.validate(d)?.let { err -> _ui.update { it.copy(validation = err) }; return }
        if (!s.dirty) return
        if (BusinessSettingsRules.changesDayRule(d, base)) { _ui.update { it.copy(confirmDayRule = true) }; return }
        doSave()
    }

    override fun confirmSave() { _ui.update { it.copy(confirmDayRule = false) }; doSave() }
    override fun cancelConfirm() = _ui.update { it.copy(confirmDayRule = false) }

    override fun confirmDiscard() { _ui.update { it.copy(discardQueue = null) }; doSave(confirmDiscard = true) }
    override fun cancelDiscard() = _ui.update { it.copy(discardQueue = null) }

    private fun doSave(confirmDiscard: Boolean = false) {
        val patch = _ui.value.patch?.takeIf { !it.isEmpty }?.let { if (confirmDiscard) it.copy(confirmDiscardPending = true) else it } ?: return
        _ui.update { it.copy(saving = true, notice = null) }
        viewModelScope.launch {
            c.settings.update(patch).fold(
                onSuccess = { e -> SettingsDraft.of(e).let { d -> _ui.update { it.copy(saving = false, base = d, draft = d, notice = TeamNotice(ErrorMessage(R.string.set_saved), false)) } } },
                onFailure = { e ->
                    if (e is ApiFailure.Http && e.code == "REGISTER_QUEUE_NOT_EMPTY") _ui.update { it.copy(saving = false, discardQueue = DiscardQueue(e.count ?: 0, e.totalMinor ?: 0)) }
                    else _ui.update { it.copy(saving = false, notice = TeamNotice(e.settingsError(), true)) }
                },
            )
        }
    }

    override fun setModule(key: String, on: Boolean) {
        if (!_ui.value.canEdit || _ui.value.moduleSaving) return
        _ui.update { it.copy(moduleSaving = true, notice = null) }
        viewModelScope.launch {
            c.settings.update(UpdateBusinessBody(modules = mapOf(key to on))).fold(
                onSuccess = { _ui.update { it.copy(moduleSaving = false, notice = TeamNotice(ErrorMessage(R.string.set_saved), false)) } },
                onFailure = { e -> _ui.update { it.copy(moduleSaving = false, notice = TeamNotice(e.settingsError(), true)) } },
            )
        }
    }

    // ---------- zona horaria ----------
    override fun openZonePicker() = _ui.update { it.copy(zoneQuery = "") }
    override fun zoneQuery(q: String) = _ui.update { it.copy(zoneQuery = q) }
    override fun closeZonePicker() = _ui.update { it.copy(zoneQuery = null) }
    override fun pickZone(id: String) = _ui.update { s -> s.copy(zoneQuery = null, draft = s.draft?.copy(timezone = id), notice = null, validation = null) }

    // ---------- avisos del negocio ----------
    override fun saveNotify() {
        val n = _ui.value.notify ?: return
        if (!_ui.value.canEdit || _ui.value.notifySaving) return
        NotifyRules.validate(n)?.let { err -> _ui.update { it.copy(notifyNotice = TeamNotice(err.message(), true)) }; return }
        _ui.update { it.copy(notifySaving = true, notifyNotice = null) }
        viewModelScope.launch {
            c.settings.saveNotifySettings(n.toDto()).fold(
                onSuccess = { s -> NotifyDraft.of(s).let { d -> _ui.update { it.copy(notifySaving = false, notifyBase = d, notify = d, notifyNotice = TeamNotice(ErrorMessage(R.string.set_saved), false)) } } },
                onFailure = { e -> _ui.update { it.copy(notifySaving = false, notifyNotice = TeamNotice(e.settingsError(), true)) } },
            )
        }
    }

    // ---------- eliminar el negocio ----------
    override fun openDelete() { if (_ui.value.isOwner) _ui.update { it.copy(delete = DeleteUi()) } }
    override fun updateDelete(d: DeleteUi) = _ui.update { it.copy(delete = d.copy(error = null)) }
    override fun closeDelete() { if (_ui.value.delete?.busy != true) _ui.update { it.copy(delete = null) } }

    override fun confirmDelete() {
        val s = _ui.value
        val d = s.delete ?: return
        val name = s.base?.name ?: business.value?.name.orEmpty()
        if (!DeleteBusinessRules.canConfirm(d.typed, name, d.understood, d.busy) || !s.isOwner) return
        _ui.update { it.copy(delete = d.copy(busy = true, error = null)) }
        viewModelScope.launch {
            // Si sale bien, la sesión de este teléfono se cierra y AppRoot vuelve a la entrada: esta pantalla desaparece sola.
            c.settings.deleteBusiness().onFailure { e -> _ui.update { it.copy(delete = it.delete?.copy(busy = false, error = e.deleteError())) } }
        }
    }

    override fun dismissNotice() = _ui.update { it.copy(notice = null, notifyNotice = null) }
}

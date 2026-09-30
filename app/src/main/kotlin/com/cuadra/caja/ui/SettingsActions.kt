package com.cuadra.caja.ui

import com.cuadra.caja.domain.NotifyDraft
import com.cuadra.caja.domain.SettingsDraft

/** Lo que «Ajustes del negocio» le pide al ViewModel. Cuerpos vacíos por omisión: la guardia de diseño usa `object : SettingsActions {}`. */
interface SettingsActions {
    fun enter() {}
    fun retry() {}
    fun update(d: SettingsDraft) {}
    fun updateNotify(n: NotifyDraft) {}
    fun discard() {}
    fun save() {}
    fun confirmSave() {}
    fun cancelConfirm() {}
    fun setModule(key: String, on: Boolean) {}
    fun openZonePicker() {}
    fun zoneQuery(q: String) {}
    fun pickZone(id: String) {}
    fun closeZonePicker() {}
    fun saveNotify() {}
    fun openDelete() {}
    fun updateDelete(d: DeleteUi) {}
    fun closeDelete() {}
    fun confirmDelete() {}
    fun dismissNotice() {}
}

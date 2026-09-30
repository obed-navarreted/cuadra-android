package com.cuadra.caja.ui

import com.cuadra.caja.data.local.MemberEntity
import com.cuadra.caja.data.remote.DeviceDto
import com.cuadra.caja.domain.AccountDraft

/** Lo que la lista de personas le pide al ViewModel. Cuerpos vacíos por omisión: la guardia de diseño usa `object : TeamActions {}`. */
interface TeamActions {
    fun enter() {}
    fun refresh() {}
    fun open(m: MemberEntity) {}
    fun closeSheet() {}
    fun openEdit(m: MemberEntity) {}
    fun openConfirm(m: MemberEntity, kind: ConfirmKind) {}
    fun openPin(m: MemberEntity) {}
    fun openNew() {}
    fun updateDialog(d: TeamDialog) {}
    fun closeDialog() {}
    fun save() {}
    fun confirm() {}
    fun dismissNotice() {}
    /** Código del negocio: abrir «Renovar» y «Elegir mi propio código» (solo el dueño). */
    fun openRenewCode() {}
    fun openChooseCode() {}
}

interface DevicesActions {
    fun enter() {}
    fun load() {}
    fun askRevoke(d: DeviceDto) {}
    fun cancelRevoke() {}
    fun confirmRevoke() {}
    fun dismissNotice() {}
}

interface AccountActions {
    fun update(d: AccountDraft) {}
    fun save() {}
    /** «Eliminar mi cuenta» (dueño con Google): abrir, escribir la palabra, confirmar, cerrar. */
    fun openDeleteAccount() {}
    fun typeDeleteWord(text: String) {}
    fun closeDeleteAccount() {}
    fun confirmDeleteAccount() {}
}

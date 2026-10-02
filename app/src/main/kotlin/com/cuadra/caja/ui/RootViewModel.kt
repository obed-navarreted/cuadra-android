package com.cuadra.caja.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cuadra.caja.AppContainer
import com.cuadra.caja.data.local.BusinessEntity
import com.cuadra.caja.data.session.Session
import com.cuadra.caja.data.sync.SyncStatus
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Pantalla que corresponde según el acceso del teléfono. */
enum class Gate { LOADING, SIGNED_OUT, ONBOARDING, PICK_MEMBER, CHANGE_PIN, READY, ACCESS_DISABLED }

class RootViewModel(private val c: AppContainer) : ViewModel() {
    val session: StateFlow<Session?> = c.sessionStore.flow.stateIn(viewModelScope, SharingStarted.Eagerly, null)
        // La base cambia con el negocio de la sesión (ADR 0014): estos tres se vuelven a suscribir a la de cada negocio.
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val business: StateFlow<BusinessEntity?> = c.databases.active.flatMapLatest { it.directory().business() }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val syncStatus: StateFlow<SyncStatus> = c.sync.status
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val pending: StateFlow<Int> = c.databases.active.flatMapLatest { it.outbox().pendingCount() }.stateIn(viewModelScope, SharingStarted.Eagerly, 0)
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val failed: StateFlow<Int> = c.databases.active.flatMapLatest { it.outbox().failedCount() }.stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    fun gate(s: Session?): Gate = when {
        s == null -> Gate.LOADING
        // Teléfono personal de alguien dado de baja: solo esta pantalla (sigue enviando lo de antes de la baja).
        s.accessDisabled && s.deviceToken != null -> Gate.ACCESS_DISABLED
        s.deviceToken != null && s.businessId != null -> if (s.memberId == null) Gate.PICK_MEMBER else if (s.pinChangePending) Gate.CHANGE_PIN else Gate.READY
        s.userToken != null -> Gate.ONBOARDING
        else -> Gate.SIGNED_OUT
    }

    fun syncNow() {
        viewModelScope.launch { c.sync.run() }
    }

    fun lock() {
        viewModelScope.launch { c.auth.lock() }
    }

    fun signOut() {
        viewModelScope.launch { c.auth.signOut() }
    }

    /** Salir de un negocio donde el acceso fue desactivado: se olvida el acceso y se borran sus datos del teléfono (ya no son de esta persona). */
    fun leaveDisabledAccess() {
        viewModelScope.launch { c.settings.wipeLocal() }
    }

    fun dismissDisabledNotice() {
        viewModelScope.launch { c.sessionStore.dismissDisabledNotice() }
    }
}

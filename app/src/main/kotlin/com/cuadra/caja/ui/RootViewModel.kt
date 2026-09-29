package com.cuadra.caja.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cuadra.caja.AppContainer
import com.cuadra.caja.data.local.BusinessEntity
import com.cuadra.caja.data.session.Session
import com.cuadra.caja.data.sync.SyncStatus
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Pantalla que corresponde según el acceso del teléfono. */
enum class Gate { LOADING, SIGNED_OUT, ONBOARDING, PICK_MEMBER, READY }

class RootViewModel(private val c: AppContainer) : ViewModel() {
    val session: StateFlow<Session?> = c.sessionStore.flow.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val business: StateFlow<BusinessEntity?> = c.db.directory().business().stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val syncStatus: StateFlow<SyncStatus> = c.sync.status
    val pending: StateFlow<Int> = c.db.outbox().pendingCount().stateIn(viewModelScope, SharingStarted.Eagerly, 0)
    val failed: StateFlow<Int> = c.db.outbox().failedCount().stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    fun gate(s: Session?): Gate = when {
        s == null -> Gate.LOADING
        s.deviceToken != null && s.businessId != null -> if (s.memberId != null) Gate.READY else Gate.PICK_MEMBER
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
}

package com.cuadra.caja.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cuadra.caja.AppContainer
import com.cuadra.caja.data.local.BusinessEntity
import com.cuadra.caja.data.remote.ApiFailure
import com.cuadra.caja.domain.ActivityFeed
import com.cuadra.caja.domain.ActivityKind
import com.cuadra.caja.domain.ActivityRow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ActivityUi(
    val rows: List<ActivityRow> = emptyList(),
    val kind: ActivityKind = ActivityKind.ALL,
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val offline: Boolean = false,
    val error: ErrorMessage? = null,
    /** Siguiente página por pedir y si el servidor todavía tiene más. */
    val nextPage: Int = 0,
    val hasMore: Boolean = false,
    /** Si esta persona puede ver la lista (dueño y admin). */
    val allowed: Boolean = true,
) {
    val visible: List<ActivityRow> get() = ActivityFeed.filter(rows, kind)
}

interface ActivityActions {
    fun enter() {}
    fun refresh() {}
    fun more() {}
    fun kind(k: ActivityKind) {}
}

/** «Actividad» (solo el dueño): quién hizo qué, por páginas; el filtro por tipo se aplica a lo ya descargado y «Ver más» trae la siguiente página. */
class ActivityViewModel(private val c: AppContainer) : ViewModel(), ActivityActions {
    private val _ui = MutableStateFlow(ActivityUi())
    val ui: StateFlow<ActivityUi> = _ui.asStateFlow()
    val business: StateFlow<BusinessEntity?> = c.db.directory().business().stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Refresco al abrir, al volver al frente y al deslizar (con antirrebote): la primera página otra vez; lo de antes sigue a la vista. */
    val refresher = ScreenRefresh(viewModelScope) {
        _ui.update { it.copy(loading = true, error = null) }
        load(0, replace = true)
        if (_ui.value.offline) RefreshResult.OFFLINE else RefreshResult.DONE
    }

    override fun enter() {
        _ui.update { it.copy(kind = ActivityKind.ALL) }
        refresher.request(com.cuadra.caja.domain.RefreshTrigger.SHOWN)
    }

    override fun refresh() {
        _ui.update { it.copy(loading = true, error = null) }
        viewModelScope.launch { load(0, replace = true) }
    }

    override fun more() {
        val s = _ui.value
        if (s.loadingMore || s.loading || !s.hasMore) return
        _ui.update { it.copy(loadingMore = true, error = null) }
        viewModelScope.launch { load(s.nextPage, replace = false) }
    }

    override fun kind(k: ActivityKind) = _ui.update { it.copy(kind = k) }

    private suspend fun load(page: Int, replace: Boolean) {
        c.plan.activity(page).fold(
            onSuccess = { p ->
                val fresh = p.items.map(ActivityFeed::row)
                _ui.update { it.copy(loading = false, loadingMore = false, offline = false, error = null, rows = if (replace) fresh else it.rows + fresh, nextPage = page + 1, hasMore = !p.last) }
            },
            onFailure = { e ->
                val offline = e is ApiFailure.Offline
                _ui.update { it.copy(loading = false, loadingMore = false, offline = offline, error = if (offline) null else e.settingsError()) }
            },
        )
    }
}

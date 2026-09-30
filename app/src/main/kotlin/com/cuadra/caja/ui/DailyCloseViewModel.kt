package com.cuadra.caja.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cuadra.caja.AppContainer
import com.cuadra.caja.data.remote.ApiFailure
import com.cuadra.caja.data.sync.calendar
import com.cuadra.caja.domain.BusinessCalendar
import com.cuadra.caja.domain.DailyClose
import com.cuadra.caja.domain.DayCloseCard
import com.cuadra.caja.domain.RangeChoice
import com.cuadra.caja.domain.RangePreset
import com.cuadra.caja.domain.RangePresets
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DailyCloseUi(
    val range: RangeChoice = RangeChoice(),
    val loading: Boolean = false,
    /** Lo último que se pudo consultar (se conserva en memoria; con `stale` se avisa que puede no estar al día). */
    val cards: List<DayCloseCard>? = null,
    /** El cierre del día vive en el servidor: sin conexión se dice «necesita conexión». */
    val needsConnection: Boolean = false,
    val error: ErrorMessage? = null,
    val stale: Boolean = false,
    /** Teléfonos del negocio que aún no envían todo (el cierre puede estar incompleto) y lo pendiente en ESTE teléfono. */
    val syncWarnings: List<com.cuadra.caja.data.remote.DeviceSyncDto> = emptyList(),
    val localPending: Int = 0,
)

interface DailyCloseActions {
    fun setRange(choice: RangeChoice) {}
    fun retry() {}
}

/**
 * Cierre del día (dueño y admin): una tarjeta por jornada del negocio, calculada por el servidor (`reports/daily-close`). Nada se abre ni se cierra a mano,
 * no hay efectivo contado ni diferencias. Necesita conexión.
 */
class DailyCloseViewModel(private val c: AppContainer, private val now: () -> Long = System::currentTimeMillis) : ViewModel(), DailyCloseActions {
    private val _ui = MutableStateFlow(DailyCloseUi())
    val ui: StateFlow<DailyCloseUi> = _ui.asStateFlow()
    val calendar: StateFlow<BusinessCalendar?> = c.db.directory().business().map { it?.calendar() }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    private var job: Job? = null

    /** Abre la pantalla con un atajo (p. ej. «Ver cierre de ayer» desde el Resumen). */
    fun open(preset: RangePreset) = setRange(RangeChoice(preset))

    override fun setRange(choice: RangeChoice) {
        _ui.update { it.copy(range = choice) }
        retry()
    }

    override fun retry() {
        job?.cancel()
        _ui.update { it.copy(loading = true, error = null, needsConnection = false) }
        job = viewModelScope.launch {
            val cal = calendar.filterNotNull().first()
            val r = RangePresets.resolve(_ui.value.range, cal, now())
            c.reports.dailyClose(r.from, r.to).fold(
                onSuccess = { dto -> _ui.update { it.copy(loading = false, cards = DailyClose.cards(dto), stale = false, syncWarnings = dto.syncWarnings.filter { w -> w.deviceId != c.sessionStore.current().deviceId }) } },
                onFailure = { e ->
                    val offline = e is ApiFailure.Offline
                    _ui.update { it.copy(loading = false, needsConnection = offline, error = if (offline) null else e.errorMessage(), stale = it.cards != null) }
                },
            )
        }
    }

    init {
        retry()
        viewModelScope.launch { c.db.outbox().pendingCount().collect { n -> _ui.update { it.copy(localPending = n) } } }
    }
}

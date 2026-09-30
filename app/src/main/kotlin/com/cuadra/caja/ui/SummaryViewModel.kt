package com.cuadra.caja.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cuadra.caja.AppContainer
import com.cuadra.caja.data.local.BusinessEntity
import com.cuadra.caja.data.local.MethodAmountRow
import com.cuadra.caja.data.local.SalesTotals
import com.cuadra.caja.data.local.TopProductRow
import com.cuadra.caja.data.sync.calendar
import com.cuadra.caja.domain.BusinessCalendar
import com.cuadra.caja.domain.ExpenseSplit
import com.cuadra.caja.domain.Profit
import com.cuadra.caja.domain.ProfitReport
import com.cuadra.caja.domain.RangeChoice
import com.cuadra.caja.domain.RangePresets
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class SummaryData(
    val sales: SalesTotals, val methods: List<MethodAmountRow>, val profit: Profit, val top: List<TopProductRow>, val receivableMinor: Long, val lowStock: Int,
)

/** Lo que la pantalla de resumen le pide al ViewModel (y a la vista previa de la guardia de diseño). */
interface SummaryActions {
    fun choose(choice: RangeChoice) {}
}

/**
 * Resumen del negocio con lo que ESTE teléfono tiene (ventas, gastos y fiados sincronizados más lo pendiente). Se calcula en el teléfono para que
 * funcione sin conexión, con la misma fórmula que el servidor y el panel web (ver `ProfitReport`). Las jornadas salen del calendario del NEGOCIO
 * (zona, corte e historial de reglas), nunca de la zona del teléfono.
 */
class SummaryViewModel(private val c: AppContainer, private val now: () -> Instant = Instant::now) : ViewModel(), SummaryActions {
    val choice = MutableStateFlow(RangeChoice())
    val business: StateFlow<BusinessEntity?> = c.db.directory().business().stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val calendar: StateFlow<BusinessCalendar?> = business.map { it?.calendar() }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    override fun choose(choice: RangeChoice) {
        this.choice.value = choice
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val data: StateFlow<SummaryData?> = combine(choice, calendar.filterNotNull()) { ch, cal -> RangePresets.resolve(ch, cal, now().toEpochMilli()) }.flatMapLatest { range ->
        val from = range.startMillis
        val to = range.endMillis
        val r = c.db.reports()
        val core: Flow<Triple<SalesTotals, List<MethodAmountRow>, Profit>> = combine(r.salesTotals(from, to), r.byMethod(from, to), r.profitLines(from, to), r.expenseSplit(from, to)) { sales, methods, lines, split ->
            Triple(sales, methods, ProfitReport.compute(sales.totalMinor, lines, ExpenseSplit(split.operatingMinor, split.purchasesMinor)))
        }
        combine(core, r.topProducts(from, to, 5), r.receivable(), c.inventory.reviewCount()) { (sales, methods, profit), top, receivable, low ->
            SummaryData(sales, methods, profit, top, receivable, low)
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
}

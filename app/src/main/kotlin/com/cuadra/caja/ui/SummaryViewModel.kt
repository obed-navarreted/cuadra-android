package com.cuadra.caja.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cuadra.caja.AppContainer
import com.cuadra.caja.data.local.BusinessEntity
import com.cuadra.caja.data.local.MethodAmountRow
import com.cuadra.caja.data.local.SalesTotals
import com.cuadra.caja.data.local.TopProductRow
import com.cuadra.caja.domain.BusinessDay
import com.cuadra.caja.domain.ExpenseSplit
import com.cuadra.caja.domain.Profit
import com.cuadra.caja.domain.ProfitReport
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

enum class SummaryPeriod { TODAY, WEEK, MONTH }

data class SummaryData(
    val sales: SalesTotals, val methods: List<MethodAmountRow>, val profit: Profit, val top: List<TopProductRow>, val receivableMinor: Long, val lowStock: Int,
)

/**
 * Resumen del negocio con lo que ESTE teléfono tiene (ventas, gastos y fiados sincronizados más lo pendiente). Se calcula en el teléfono para que
 * funcione sin conexión, con la misma fórmula que el servidor y el panel web (ver `ProfitReport`).
 */
class SummaryViewModel(private val c: AppContainer, private val now: () -> Instant = Instant::now) : ViewModel() {
    val period = MutableStateFlow(SummaryPeriod.TODAY)
    val business: StateFlow<BusinessEntity?> = c.db.directory().business().stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun select(p: SummaryPeriod) {
        period.value = p
    }

    /** Ventana [desde, hasta) en milisegundos, con jornadas del negocio (corte 02:00 por defecto). */
    private fun window(p: SummaryPeriod, b: BusinessEntity): Pair<Long, Long> {
        val zone = runCatching { ZoneId.of(b.timezone) }.getOrDefault(ZoneId.systemDefault())
        val cutoff = runCatching { LocalTime.parse(b.dayCutoff) }.getOrDefault(LocalTime.of(2, 0))
        val today = BusinessDay.of(now(), zone, cutoff)
        val first = when (p) {
            SummaryPeriod.TODAY -> today.date
            SummaryPeriod.WEEK -> today.date.minusDays(6)
            SummaryPeriod.MONTH -> today.date.withDayOfMonth(1)
        }
        return BusinessDay.forDate(first, zone, cutoff).startMillis to today.endMillis
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val data: StateFlow<SummaryData?> = combine(period, business.filterNotNull()) { p, b -> window(p, b) }.flatMapLatest { (from, to) ->
        val r = c.db.reports()
        val core: Flow<Triple<SalesTotals, List<MethodAmountRow>, Profit>> = combine(r.salesTotals(from, to), r.byMethod(from, to), r.profitLines(from, to), r.expenseSplit(from, to)) { sales, methods, lines, split ->
            Triple(sales, methods, ProfitReport.compute(sales.totalMinor, lines, ExpenseSplit(split.operatingMinor, split.purchasesMinor)))
        }
        combine(core, r.topProducts(from, to, 5), r.receivable(), c.inventory.reviewCount()) { (sales, methods, profit), top, receivable, low ->
            SummaryData(sales, methods, profit, top, receivable, low)
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
}

package com.cuadra.caja.ui

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cuadra.caja.AppContainer
import com.cuadra.caja.R
import com.cuadra.caja.core.model.Money
import com.cuadra.caja.data.local.BusinessEntity
import com.cuadra.caja.data.local.CashMovementEntity
import com.cuadra.caja.data.local.ExpenseCategoryEntity
import com.cuadra.caja.data.local.ExpenseEntity
import com.cuadra.caja.data.local.ExpenseTotals
import com.cuadra.caja.data.local.ShiftEntity
import com.cuadra.caja.data.repo.CloseResult
import com.cuadra.caja.data.session.Session
import com.cuadra.caja.data.sync.calendar
import com.cuadra.caja.domain.BusinessCalendar
import com.cuadra.caja.domain.RangeChoice
import com.cuadra.caja.domain.RangePresets
import kotlinx.coroutines.flow.filterNotNull
import com.cuadra.caja.domain.CashClosing
import com.cuadra.caja.domain.ClosingBreakdown
import com.cuadra.caja.domain.Denominations
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Una fila de la lista de gastos: gastos y retiros/entradas se mezclan por hora. */
sealed interface CashRow {
    val at: Long
    data class Expense(val e: ExpenseEntity, val category: String?, override val at: Long = e.occurredAt) : CashRow
    data class Movement(val m: CashMovementEntity, override val at: Long = m.occurredAt) : CashRow
}

data class ExpenseDraft(val amount: String = "", val description: String = "", val categoryId: String? = null, val source: String = "CASH_DRAWER")
data class MovementDraft(val kind: String = "DEPOSIT", val amount: String = "", val reason: String = "")

/** Lo que se va a anular: un gasto o un movimiento, con el motivo escrito. */
data class VoidTarget(val id: String, val isMovement: Boolean, val reason: String = "")

data class CountDraft(val counts: Map<Long, Int> = emptyMap(), val counted: String = "", val note: String = "", val showCounter: Boolean = false, val forceReason: String? = null)

/** Administrar las categorías de gastos: cuál se está renombrando (`editingId`; «nueva» si `adding`), el nombre escrito y el resultado de la última llamada. */
data class CategoryManagerUi(val editingId: String? = null, val adding: Boolean = false, val name: String = "", val saving: Boolean = false, val error: ErrorMessage? = null) {
    val editing: Boolean get() = adding || editingId != null
}

data class CashUi(
    /** Periodo elegido en el selector compartido (jornadas del negocio). */
    val range: RangeChoice = RangeChoice(),
    val expenseDraft: ExpenseDraft? = null,
    val movementDraft: MovementDraft? = null,
    val voidTarget: VoidTarget? = null,
    val categoryManager: CategoryManagerUi? = null,
    val showShift: Boolean = false,
    val openFloat: String? = null,
    val count: CountDraft = CountDraft(),
    val closedShift: ShiftEntity? = null,
    @StringRes val messageRes: Int? = null,
    val noteRequired: Boolean = false,
)

/** Lo contado: lo escrito a mano, o la suma de los billetes si se usó el contador. */
fun CountDraft.countedMinor(decimals: Int): Long? {
    if (counted.isNotBlank()) return Money.parse(counted, decimals)?.minor
    return counts.takeIf { it.isNotEmpty() }?.let { Denominations.total(it) }
}

class CashViewModel(private val c: AppContainer) : ViewModel(), CashActions {

    /** Refresco al abrir la pantalla, al volver al frente y al deslizar: sincroniza (sube lo pendiente y baja lo nuevo). */
    val refresher = ScreenRefresh(viewModelScope) { c.pullNow() }
    private val _ui = MutableStateFlow(CashUi())
    val ui: StateFlow<CashUi> = _ui.asStateFlow()

    val business: StateFlow<BusinessEntity?> = c.db.directory().business().stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val session: StateFlow<Session?> = c.sessionStore.flow.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val canManage: StateFlow<Boolean> = session.map { it?.memberRole == "OWNER" || it?.memberRole == "ADMIN" }.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val categories: StateFlow<List<ExpenseCategoryEntity>> = c.expenses.categories().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private val allCategories: StateFlow<List<ExpenseCategoryEntity>> = c.expenses.allCategories().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val currentShift: StateFlow<ShiftEntity?> = c.shifts.current().stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val recentShifts: StateFlow<List<ShiftEntity>> = c.shifts.recent().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** El turno abierto y lo que llevan las ventas, en vivo. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val breakdown: StateFlow<ClosingBreakdown> = currentShift.flatMapLatest { s -> if (s == null) flowOf(ClosingBreakdown()) else c.shifts.breakdown(s) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ClosingBreakdown())

    val suggestedFloat = MutableStateFlow(0L)

    val calendar: StateFlow<BusinessCalendar?> = business.map { it?.calendar() }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Ventana [desde, hasta) de la elección, con las jornadas del NEGOCIO (zona, corte e historial), nunca con la zona del teléfono. */
    private val window: Flow<Pair<Long, Long>> = combine(_ui.map { it.range }, calendar.filterNotNull()) { ch, cal ->
        val r = RangePresets.resolve(ch, cal, System.currentTimeMillis())
        r.startMillis to r.endMillis
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val totals: StateFlow<ExpenseTotals> = window.flatMapLatest { (f, t) -> c.expenses.totals(f, t) }.stateIn(viewModelScope, SharingStarted.Eagerly, ExpenseTotals(0, 0, 0))

    /** Un cajero solo ve lo que él mismo anotó (el servidor ya filtra los movimientos; los gastos propios se reconocen por autor). */
    @OptIn(ExperimentalCoroutinesApi::class)
    val rows: StateFlow<List<CashRow>> = window.flatMapLatest { (f, t) ->
        combine(c.expenses.expenses(f, t), c.expenses.movements(f, t), allCategories) { ex, mv, cats ->
            // Un nombre propio (categoría creada o renombrada) manda sobre la clave de fábrica; las archivadas siguen dando nombre a sus gastos viejos.
            val names = cats.associate { it.id to (it.name?.takeIf { n -> n.isNotBlank() } ?: it.key.orEmpty()) }
            (ex.map { CashRow.Expense(it, it.categoryId?.let(names::get)) } + mv.map { CashRow.Movement(it) }).sortedByDescending { it.at }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // ---------- navegación ----------
    override fun setRange(choice: RangeChoice) = _ui.update { it.copy(range = choice) }
    override fun dismissMessage() = _ui.update { it.copy(messageRes = null) }

    override fun openExpense() = _ui.update { it.copy(expenseDraft = ExpenseDraft()) }
    override fun updateExpense(d: ExpenseDraft) = _ui.update { it.copy(expenseDraft = d) }
    override fun closeExpense() = _ui.update { it.copy(expenseDraft = null) }

    override fun openMovement(kind: String) = _ui.update { it.copy(movementDraft = MovementDraft(kind)) }
    override fun updateMovement(d: MovementDraft) = _ui.update { it.copy(movementDraft = d) }
    override fun closeMovement() = _ui.update { it.copy(movementDraft = null) }

    override fun askVoid(t: VoidTarget) = _ui.update { it.copy(voidTarget = t) }
    override fun updateVoid(reason: String) = _ui.update { s -> s.copy(voidTarget = s.voidTarget?.copy(reason = reason)) }
    override fun closeVoid() = _ui.update { it.copy(voidTarget = null) }

    override fun showShift(show: Boolean) {
        _ui.update { it.copy(showShift = show, openFloat = null, count = CountDraft(), closedShift = null, noteRequired = false) }
        if (show) viewModelScope.launch { suggestedFloat.value = c.shifts.suggestedFloat() }
    }

    override fun updateOpenFloat(text: String?) = _ui.update { it.copy(openFloat = text) }
    override fun updateCount(d: CountDraft) = _ui.update { it.copy(count = d, noteRequired = false) }

    // ---------- acciones ----------
    override fun saveExpense() {
        val d = _ui.value.expenseDraft ?: return
        val minor = Money.parse(d.amount, decimals())?.minor?.takeIf { it > 0 } ?: return
        // El cajero solo saca del cajón; el resto de orígenes es de quien administra (el servidor lo exige igual).
        val source = if (canManage.value) d.source else "CASH_DRAWER"
        viewModelScope.launch {
            c.expenses.addExpense(minor, d.description, d.categoryId, source)
            _ui.update { it.copy(expenseDraft = null) }
        }
    }

    override fun saveMovement() {
        val d = _ui.value.movementDraft ?: return
        val minor = Money.parse(d.amount, decimals())?.minor?.takeIf { it > 0 } ?: return
        if (d.kind == "WITHDRAWAL" && !canManage.value) return
        viewModelScope.launch {
            c.expenses.addMovement(d.kind, minor, d.reason)
            _ui.update { it.copy(movementDraft = null) }
        }
    }

    // ---------- categorías de gastos (dueño y admin, con conexión) ----------
    override fun openCategories() = _ui.update { it.copy(categoryManager = CategoryManagerUi()) }
    override fun closeCategories() = _ui.update { it.copy(categoryManager = null) }
    override fun startCategoryEdit(id: String?) = _ui.update { s ->
        s.copy(categoryManager = CategoryManagerUi(editingId = id, adding = id == null, name = id?.let { i -> categories.value.firstOrNull { it.id == i }?.let { c -> c.name?.takeIf { n -> n.isNotBlank() } ?: c.key.orEmpty() } }.orEmpty()))
    }
    override fun updateCategoryName(name: String) = _ui.update { s -> s.copy(categoryManager = s.categoryManager?.copy(name = name.take(60), error = null)) }
    override fun cancelCategoryEdit() = _ui.update { it.copy(categoryManager = CategoryManagerUi()) }

    override fun saveCategory() {
        val m = _ui.value.categoryManager ?: return
        if (!m.editing || m.saving || m.name.isBlank()) return
        _ui.update { it.copy(categoryManager = m.copy(saving = true, error = null)) }
        viewModelScope.launch {
            c.expenses.saveCategory(m.editingId, m.name).fold(
                onSuccess = { _ui.update { it.copy(categoryManager = CategoryManagerUi()) } },
                onFailure = { e -> _ui.update { it.copy(categoryManager = m.copy(saving = false, error = e.settingsError())) } },
            )
        }
    }

    override fun archiveCategory(id: String) {
        val m = _ui.value.categoryManager ?: return
        if (m.saving) return
        _ui.update { it.copy(categoryManager = m.copy(saving = true, error = null)) }
        viewModelScope.launch {
            c.expenses.saveCategory(id, null, active = false).fold(
                onSuccess = { _ui.update { it.copy(categoryManager = CategoryManagerUi()) } },
                onFailure = { e -> _ui.update { it.copy(categoryManager = m.copy(saving = false, error = e.settingsError())) } },
            )
        }
    }

    override fun confirmVoid() {
        val t = _ui.value.voidTarget ?: return
        if (!canManage.value) return
        viewModelScope.launch {
            if (t.isMovement) c.expenses.voidMovement(t.id, t.reason.trim().ifEmpty { null }) else c.expenses.voidExpense(t.id, t.reason.trim().ifEmpty { null })
            _ui.update { it.copy(voidTarget = null) }
        }
    }

    override fun openShift() {
        val text = _ui.value.openFloat ?: return
        val minor = Money.parse(text.ifBlank { "0" }, decimals())?.minor ?: return
        viewModelScope.launch {
            if (c.shifts.open(minor) == null) _ui.update { it.copy(messageRes = R.string.shift_no_register) }
            else _ui.update { it.copy(openFloat = null) }
        }
    }

    /** Lo contado: lo escrito a mano, o la suma de los billetes si se usó el contador. */
    fun countedMinor(): Long? = _ui.value.count.countedMinor(decimals())

    override fun closeShift() {
        val shift = currentShift.value ?: return
        val d = _ui.value.count
        val counted = countedMinor() ?: return
        viewModelScope.launch {
            when (val r = c.shifts.close(shift.id, counted, d.counts.takeIf { d.counted.isBlank() }, d.note, force = d.forceReason != null, forcedReason = d.forceReason)) {
                is CloseResult.Closed -> _ui.update { it.copy(closedShift = r.shift, count = CountDraft()) }
                is CloseResult.NoteRequired -> _ui.update { it.copy(noteRequired = true) }
                CloseResult.NotFound, CloseResult.AlreadyClosed -> _ui.update { it.copy(messageRes = R.string.shift_already_closed) }
            }
        }
    }

    fun setModule(key: String, on: Boolean) {
        viewModelScope.launch { c.auth.setModule(key, on) }
    }

    /** Esperado ahora mismo para un turno abierto. */
    fun expectedNow(shift: ShiftEntity, b: ClosingBreakdown) = CashClosing.expected(shift.openingFloatMinor, b)

    private fun decimals() = business.value?.let { com.cuadra.caja.core.model.Currency.of(it.currency).decimals } ?: 2
}

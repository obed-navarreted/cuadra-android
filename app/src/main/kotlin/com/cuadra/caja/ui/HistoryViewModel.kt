package com.cuadra.caja.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cuadra.caja.AppContainer
import com.cuadra.caja.data.local.MemberEntity
import com.cuadra.caja.data.local.SaleEntity
import com.cuadra.caja.data.remote.ApiFailure
import com.cuadra.caja.data.remote.SalesTotalsDto
import com.cuadra.caja.data.sync.calendar
import com.cuadra.caja.data.sync.toView
import com.cuadra.caja.domain.BusinessCalendar
import com.cuadra.caja.domain.RangeChoice
import com.cuadra.caja.domain.RangePresets
import com.cuadra.caja.domain.SaleDeletion
import com.cuadra.caja.domain.SaleLists
import com.cuadra.caja.domain.SaleQuery
import com.cuadra.caja.domain.SaleView
import com.cuadra.caja.domain.printing.PrintNotice
import com.cuadra.caja.domain.printing.PrinterBadge
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** El motivo que se está escribiendo para eliminar una venta. `undo`: es «Anular esta venta» (la última propia, en los primeros 5 minutos). */
data class DeleteDraft(val saleId: String, val reason: String = "", val undo: Boolean = false, val tooLate: Boolean = false)

/**
 * «Devolver productos»: la cantidad escrita por línea (texto, en unidades), el motivo y cómo se devuelve el dinero. `returnId` se fija al abrir: si se
 * confirma dos veces (doble toque) no se duplica.
 */
data class ReturnDraft(
    val sale: SaleView, val texts: Map<String, String> = emptyMap(), val reason: String = "", val method: com.cuadra.caja.domain.RefundMethod = com.cuadra.caja.domain.RefundMethod.CASH,
    val returnId: String = java.util.UUID.randomUUID().toString(), val saving: Boolean = false,
) {
    val milli: Map<String, Long> get() = texts.mapValues { (_, v) -> parseQty(v) }
    val lines get() = sale.returnable()
    val exceeds: Boolean get() = com.cuadra.caja.domain.SaleReturnMath.exceeds(lines, milli)
    val estimate: Long get() = com.cuadra.caja.domain.SaleReturnMath.total(lines, sale.subtotalMinor, sale.discountMinor, sale.totalMinor, sale.returnedMinor, milli)
    val canConfirm: Boolean get() = !saving && milli.values.any { it > 0 } && !exceeds && com.cuadra.caja.domain.SaleReturnMath.reasonOk(reason)

    companion object {
        fun parseQty(text: String): Long = text.trim().replace(',', '.').toBigDecimalOrNull()?.takeIf { it.signum() > 0 }?.movePointRight(3)?.setScale(0, java.math.RoundingMode.HALF_UP)?.toLong() ?: 0
    }
}

data class HistoryUi(
    val range: RangeChoice = RangeChoice(),
    val query: SaleQuery = SaleQuery(),
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    /** La lista completa que trajo el servidor (solo dueño/admin, con conexión). */
    val sales: List<SaleView> = emptyList(),
    val hasMore: Boolean = false,
    val totals: SalesTotalsDto? = null,
    /** El servidor no se pudo consultar: se muestran las ventas de este teléfono. */
    val offline: Boolean = false,
    val error: ErrorMessage? = null,
    val detail: SaleView? = null,
    val delete: DeleteDraft? = null,
    /** Lo que se puede hacer con la venta abierta: devolver productos y «Anular esta venta» (la última propia, primeros 5 minutos). */
    val canReturn: Boolean = false, val undoable: Boolean = false,
    val returnDraft: ReturnDraft? = null,
    /** La devolución que se acaba de guardar (para «Imprimir comprobante»). */
    val lastReturn: com.cuadra.caja.domain.SaleReturnView? = null,
    /** Estado de la impresora (`OFF` = opción desactivada: no hay botón de imprimir) y el resultado del último intento. */
    val printer: PrinterBadge = PrinterBadge.OFF,
    val printNotice: PrintNotice? = null,
)

/** Lo que la pantalla de ventas le pide al ViewModel (y a la guardia de diseño, con cuerpos vacíos). */
interface HistoryActions {
    fun setRange(choice: RangeChoice) {}
    fun toggleStatus(status: String) {}
    fun setMethod(method: String?) {}
    fun setMember(memberId: String?) {}
    fun refresh() {}
    fun loadMore() {}
    fun open(sale: SaleView) {}
    fun closeDetail() {}
    fun askDelete(saleId: String) {}
    fun updateReason(text: String) {}
    fun closeDelete() {}
    fun confirmDelete() {}
    /** «Imprimir recibo» del detalle (las eliminadas llevan el aviso «ANULADA»). */
    fun printSale(sale: SaleView) {}
    fun askUndo(saleId: String) {}
    fun askReturn() {}
    fun setReturnQty(itemId: String, text: String) {}
    fun returnAll(itemId: String) {}
    fun setReturnReason(text: String) {}
    fun setReturnMethod(method: com.cuadra.caja.domain.RefundMethod) {}
    fun closeReturn() {}
    fun confirmReturn() {}
    /** «Imprimir comprobante» de la devolución recién hecha. */
    fun printReturn() {}
}

/**
 * Ventas. Cajero: las suyas, guardadas en el teléfono (las últimas 100). Dueño y admin: la lista COMPLETA del servidor con rango, estado, método y persona
 * (necesita conexión; sin ella se ven las de este teléfono). Eliminar una venta va por la cola de salida, así funciona también sin conexión.
 */
class HistoryViewModel(private val c: AppContainer, private val now: () -> Long = System::currentTimeMillis) : ViewModel(), HistoryActions {
    private val _ui = MutableStateFlow(HistoryUi())
    val ui: StateFlow<HistoryUi> = _ui.asStateFlow()

    val role: StateFlow<String?> = c.sessionStore.flow.map { it.memberRole }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    private val memberName: StateFlow<String?> = c.sessionStore.flow.map { it.memberName }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val manager: StateFlow<Boolean> = role.map { it == "OWNER" || it == "ADMIN" }.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val calendar: StateFlow<BusinessCalendar?> = c.db.directory().business().map { it?.calendar() }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val members: StateFlow<List<MemberEntity>> = c.db.directory().activeMembers().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Las ventas de este teléfono: la lista del cajero y la de respaldo del dueño/admin sin conexión. */
    private val memberId: StateFlow<String?> = c.sessionStore.flow.map { it.memberId }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /**
     * Las ventas de este teléfono: la lista del cajero y la de respaldo del dueño/admin sin conexión. Un cajero ve SOLO las suyas, aunque el teléfono
     * guarde más (p. ej. las que bajó un admin que usó este mismo teléfono).
     */
    val local: StateFlow<List<SaleView>> = combine(c.sales.recent(100), role, memberId) { list: List<SaleEntity>, r, me ->
        list.filter { r != "CASHIER" || it.createdByMemberId == me }.map { it.toView(emptyList(), emptyList()) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private var job: Job? = null
    private var nextPage = 0

    /** Eliminaciones ya pedidas que el servidor todavía no confirma: la lista las muestra anuladas aunque la respuesta sea anterior. */
    private val pendingCancels = mutableMapOf<String, SaleView>()

    init {
        c.printer.badge.onEach { b -> _ui.update { it.copy(printer = b) } }.launchIn(viewModelScope)
        viewModelScope.launch {
            combine(manager, calendar) { m, cal -> m && cal != null }.filter { it }.first()
            refresh()
        }
    }

    override fun setRange(choice: RangeChoice) {
        _ui.update { it.copy(range = choice) }
        refresh()
    }

    override fun toggleStatus(status: String) {
        _ui.update { it.copy(query = it.query.toggle(status)) }
        refresh()
    }

    override fun setMethod(method: String?) {
        _ui.update { it.copy(query = it.query.copy(method = method)) }
        refresh()
    }

    override fun setMember(memberId: String?) {
        _ui.update { it.copy(query = it.query.copy(memberId = memberId)) }
        refresh()
    }

    override fun refresh() = load(reset = true)

    override fun loadMore() {
        if (_ui.value.hasMore && !_ui.value.loadingMore && !_ui.value.loading) load(reset = false)
    }

    private fun load(reset: Boolean) {
        val cal = calendar.value ?: return
        if (!manager.value) return
        val state = _ui.value
        val range = RangePresets.resolve(state.range, cal, now())
        val page = if (reset) 0 else nextPage
        job?.cancel()
        _ui.update { if (reset) it.copy(loading = true, error = null) else it.copy(loadingMore = true) }
        job = viewModelScope.launch {
            val statuses = state.query.statuses.toList()
            val pages = statuses.map { st -> async { c.reports.sales(range.from, range.to, st, state.query.method, state.query.memberId, page) } }.awaitAll()
            val totals = if (reset) c.reports.salesReport(range.from, range.to).getOrNull()?.sales else _ui.value.totals
            val failure = pages.firstOrNull { it.isFailure }?.exceptionOrNull()
            if (failure != null) {
                _ui.update { it.copy(loading = false, loadingMore = false, offline = failure is ApiFailure.Offline, error = if (failure is ApiFailure.Offline) null else failure.errorMessage(), hasMore = false) }
                return@launch
            }
            val fetched = pages.map { p -> p.getOrThrow() }
            val incoming = fetched.flatMap { it.items }.map { it.toView() }.map { v -> if (v.cancelled) v.also { pendingCancels.remove(it.id) } else pendingCancels[v.id] ?: v }
            nextPage = page + 1
            _ui.update { s ->
                val merged = SaleLists.merge(if (reset) emptyList() else s.sales, incoming)
                s.copy(loading = false, loadingMore = false, offline = false, error = null, sales = visible(merged, s.query), hasMore = fetched.any { !it.last }, totals = totals)
            }
        }
    }

    /** Una venta que se acaba de eliminar sale de la lista si el filtro no incluye las eliminadas. */
    private fun visible(list: List<SaleView>, q: SaleQuery) = list.filter { (if (it.cancelled) "CANCELLED" else "COMPLETED") in q.statuses }

    override fun open(sale: SaleView) {
        _ui.update { it.copy(detail = sale, printNotice = null, lastReturn = null, canReturn = false, undoable = false) }
        viewModelScope.launch {
            // Las filas de este teléfono no traen las líneas: se leen del propio teléfono (con sus devoluciones, también las pendientes).
            val full = if (sale.items.isEmpty() && sale.payments.isEmpty()) c.sales.view(sale.id) ?: sale else sale
            val (canReturn, undoable) = actionsFor(full)
            _ui.update { if (it.detail?.id == full.id) it.copy(detail = full, canReturn = canReturn, undoable = undoable) else it }
        }
    }

    /** ¿Se puede devolver algo de esta venta? ¿Se ofrece «Anular esta venta»? (la última propia en este teléfono, primeros 5 minutos). */
    private suspend fun actionsFor(sale: SaleView): Pair<Boolean, Boolean> {
        val me = memberId.value
        val cal = calendar.value
        val completed = sale.completedAtMillis
        val sameDay = cal != null && completed != null && cal.dateOf(completed) == cal.dateOf(now())
        val canReturn = com.cuadra.caja.domain.SaleReturnMath.canReturn(sale.status, sale.returnable()) &&
            com.cuadra.caja.domain.SaleReturnMath.allowed(role.value, sale.completedById != null && sale.completedById == me, sameDay)
        val last = c.sales.lastCompletedBy(me)?.id
        val undoable = com.cuadra.caja.domain.SaleUndo.canUndo(sale.status, sale.completedById, me, last == sale.id, sale.returnedMinor, completed, now())
        return canReturn to undoable
    }

    override fun closeDetail() = _ui.update { it.copy(detail = null, delete = null, printNotice = null, returnDraft = null, lastReturn = null) }

    override fun askUndo(saleId: String) = _ui.update { it.copy(delete = DeleteDraft(saleId, undo = true)) }

    override fun askReturn() = _ui.update { s -> s.detail?.let { s.copy(returnDraft = ReturnDraft(it)) } ?: s }
    override fun setReturnQty(itemId: String, text: String) = _ui.update { s -> s.copy(returnDraft = s.returnDraft?.let { d -> d.copy(texts = d.texts + (itemId to text.take(12))) }) }
    override fun returnAll(itemId: String) = _ui.update { s ->
        s.copy(returnDraft = s.returnDraft?.let { d -> d.lines.firstOrNull { it.id == itemId }?.let { l -> d.copy(texts = d.texts + (itemId to com.cuadra.caja.ui.screens.qtyText(l.remainingMilli))) } ?: d })
    }
    override fun setReturnReason(text: String) = _ui.update { s -> s.copy(returnDraft = s.returnDraft?.copy(reason = text.take(300))) }
    override fun setReturnMethod(method: com.cuadra.caja.domain.RefundMethod) = _ui.update { s -> s.copy(returnDraft = s.returnDraft?.copy(method = method)) }
    override fun closeReturn() = _ui.update { it.copy(returnDraft = null) }

    override fun confirmReturn() {
        val d = _ui.value.returnDraft ?: return
        if (!d.canConfirm) return
        _ui.update { it.copy(returnDraft = d.copy(saving = true)) }
        viewModelScope.launch {
            val saved = runCatching { c.sales.returnItems(d.sale, d.milli, d.reason, d.method, d.returnId) }.getOrNull()
            val refreshed = c.sales.view(d.sale.id) ?: saved?.let { r ->
                // La venta no está en este teléfono (lista del servidor): se muestra con la devolución agregada hasta que el servidor la confirme.
                val returned = r.lines.associate { it.saleItemId to it.quantityMilli }
                d.sale.copy(returnedMinor = d.sale.returnedMinor + r.totalMinor, returns = d.sale.returns + r,
                    items = d.sale.items.map { it.copy(returnedMilli = it.returnedMilli + (returned[it.id] ?: 0)) })
            } ?: d.sale
            val (canReturn, undoable) = actionsFor(refreshed)
            _ui.update { s ->
                s.copy(returnDraft = null, detail = refreshed, lastReturn = saved, canReturn = canReturn, undoable = undoable,
                    sales = s.sales.map { if (it.id == refreshed.id) refreshed else it })
            }
            // El dueño/admin ve la lista del servidor: se refresca cuando la devolución ya se envió.
            if (manager.value && saved != null) {
                var waited = 0
                while (c.db.outbox().countFor(saved.id) > 0 && waited < 30) { delay(500); waited++ }
                refresh()
            }
        }
    }

    override fun printReturn() {
        val s = _ui.value
        val ret = s.lastReturn ?: return
        val sale = s.detail ?: return
        _ui.update { it.copy(printNotice = null) }
        viewModelScope.launch {
            val notice = PrintNotice.of(c.printer.printReturn(sale, ret))
            _ui.update { it.copy(printNotice = notice) }
        }
    }

    override fun printSale(sale: SaleView) {
        _ui.update { it.copy(printNotice = null) }
        viewModelScope.launch {
            // Las filas de este teléfono pueden venir sin líneas: se lee la venta completa del teléfono antes de armar el recibo.
            val full = if (sale.items.isEmpty() && sale.payments.isEmpty()) c.sales.view(sale.id) ?: sale else sale
            val notice = PrintNotice.of(c.printer.printSale(full))
            _ui.update { it.copy(printNotice = notice) }
        }
    }

    override fun askDelete(saleId: String) = _ui.update { it.copy(delete = DeleteDraft(saleId)) }
    override fun updateReason(text: String) = _ui.update { s -> s.copy(delete = s.delete?.copy(reason = text.take(SaleDeletion.MAX_REASON))) }
    override fun closeDelete() = _ui.update { it.copy(delete = null) }

    override fun confirmDelete() {
        val d = _ui.value.delete ?: return
        if (!d.undo && !SaleDeletion.canDelete(role.value, statusOf(d.saleId))) return
        val reason = SaleDeletion.clean(d.reason) ?: return
        val who = memberName.value
        viewModelScope.launch {
            // «Anular esta venta»: se vuelve a comprobar al confirmar (pudieron pasar los 5 minutos con la hoja abierta).
            if (d.undo) {
                val sale = _ui.value.detail?.takeIf { it.id == d.saleId } ?: c.sales.view(d.saleId)
                if (sale == null || !actionsFor(sale).second) {
                    _ui.update { it.copy(delete = d.copy(tooLate = true), undoable = false) }
                    return@launch
                }
            }
            c.sales.cancel(d.saleId, reason)
            val at = now()
            // Se ve anulada de inmediato (con quién, cuándo y por qué); la lista del servidor se refresca cuando la eliminación ya se envió.
            _ui.update { s ->
                val old = s.sales.firstOrNull { it.id == d.saleId } ?: s.detail?.takeIf { it.id == d.saleId }
                val done = old?.copy(status = "CANCELLED", cancelledBy = who, cancelledAtMillis = at, cancelReason = reason)
                done?.let { pendingCancels[it.id] = it }
                s.copy(
                    delete = null, undoable = false, canReturn = false, detail = if (s.detail?.id == d.saleId) done ?: s.detail else s.detail,
                    sales = visible(s.sales.map { if (it.id == d.saleId && done != null) done else it }, s.query),
                    totals = s.totals?.let { t -> old?.let { t.copy(count = (t.count - 1).coerceAtLeast(0), totalMinor = t.totalMinor - it.totalMinor, cancelledCount = t.cancelledCount + 1) } ?: t },
                )
            }
            if (manager.value) {
                var waited = 0
                while (c.db.outbox().countFor(d.saleId) > 0 && waited < 30) { delay(500); waited++ }
                refresh()
            }
        }
    }

    private fun statusOf(id: String): String = _ui.value.sales.firstOrNull { it.id == id }?.status ?: _ui.value.detail?.takeIf { it.id == id }?.status ?: local.value.firstOrNull { it.id == id }?.status ?: "COMPLETED"
}

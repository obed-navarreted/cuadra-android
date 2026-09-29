package com.cuadra.caja.ui

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cuadra.caja.AppContainer
import com.cuadra.caja.R
import com.cuadra.caja.core.model.Currency
import com.cuadra.caja.core.model.Money
import com.cuadra.caja.data.local.BusinessEntity
import com.cuadra.caja.data.local.ProductEntity
import com.cuadra.caja.data.local.PurchaseRow
import com.cuadra.caja.data.local.SupplierBalance
import com.cuadra.caja.data.local.SupplierEntity
import com.cuadra.caja.data.repo.PurchaseLine
import com.cuadra.caja.domain.Money3
import java.util.UUID
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

enum class PurchasesTab { PURCHASES, SUPPLIERS }

/** Compra en armado. `paid` vacío = todo queda por pagar; por defecto se propone pagar todo. */
data class PurchaseDraft(
    val supplierId: String? = null, val supplierName: String = "", val lines: List<PurchaseLine> = emptyList(), val paid: String? = null, val paidSource: String = "CASH_DRAWER",
    val note: String = "", val picking: Boolean = false, val pickQuery: String = "", val lineEditor: LineDraft? = null, val error: Boolean = false,
)

/** Línea en edición: de un producto del catálogo, o solo con nombre. */
data class LineDraft(val productId: String? = null, val name: String = "", val quantity: String = "", val cost: String = "", val lineId: String? = null)

/** A quién se paga: una compra concreta, o a un proveedor (se reparte de la compra más vieja a la más nueva). */
data class PayDraft(val purchaseId: String?, val supplierId: String?, val title: String, val owedMinor: Long, val amount: String, val source: String = "CASH_DRAWER", val note: String = "")

data class VoidDraft(val purchaseId: String?, val paymentId: String?, val reason: String = "")

data class SupplierDraft(val id: String? = null, val name: String = "", val phone: String = "", val notes: String = "", val error: Boolean = false)

data class PurchasesUi(
    val tab: PurchasesTab = PurchasesTab.PURCHASES,
    val onlyOwed: Boolean = false,
    val supplierFilter: String? = null,
    val draft: PurchaseDraft? = null,
    val detailId: String? = null,
    val pay: PayDraft? = null,
    val void: VoidDraft? = null,
    val supplierEditor: SupplierDraft? = null,
    @StringRes val messageRes: Int? = null,
)

class PurchasesViewModel(private val c: AppContainer) : ViewModel() {
    private val _ui = MutableStateFlow(PurchasesUi())
    val ui: StateFlow<PurchasesUi> = _ui.asStateFlow()

    val business: StateFlow<BusinessEntity?> = c.db.directory().business().stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val suppliers: StateFlow<List<SupplierEntity>> = c.purchases.suppliers().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val balances: StateFlow<Map<String, Long>> = c.purchases.balances().map { list: List<SupplierBalance> -> list.associate { it.supplierId to it.balanceMinor } }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())
    val totalOwed: StateFlow<Long> = c.purchases.totalOwed().stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val purchases: StateFlow<List<PurchaseRow>> = _ui.map { it.onlyOwed to it.supplierFilter }.flatMapLatest { (owed, s) -> c.purchases.purchases(owed, s) }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val detailItems = _ui.map { it.detailId }.flatMapLatest { id -> if (id == null) flowOf(emptyList()) else c.purchases.items(id) }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val detailPayments = _ui.map { it.detailId }.flatMapLatest { id -> if (id == null) flowOf(emptyList()) else c.purchases.payments(id) }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val pickResults: StateFlow<List<ProductEntity>> = _ui.map { it.draft?.pickQuery.orEmpty() }.flatMapLatest { c.products.search(it) }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun setTab(t: PurchasesTab) = _ui.update { it.copy(tab = t, supplierFilter = if (t == PurchasesTab.SUPPLIERS) null else it.supplierFilter) }
    fun setOnlyOwed(v: Boolean) = _ui.update { it.copy(onlyOwed = v) }
    fun filterSupplier(id: String?) = _ui.update { it.copy(supplierFilter = id, tab = PurchasesTab.PURCHASES) }
    fun openDetail(id: String?) = _ui.update { it.copy(detailId = id) }
    fun dismissMessage() = _ui.update { it.copy(messageRes = null) }

    // ---------- nueva compra ----------
    fun newPurchase(supplierId: String? = null) = _ui.update { it.copy(draft = PurchaseDraft(supplierId = supplierId)) }
    fun closeDraft() = _ui.update { it.copy(draft = null) }
    fun updateDraft(d: PurchaseDraft) = _ui.update { it.copy(draft = d.copy(error = false)) }
    fun removeLine(id: String) = _ui.value.draft?.let { d -> updateDraft(d.copy(lines = d.lines.filterNot { it.id == id })) }

    fun pickProduct(p: ProductEntity) = _ui.value.draft?.let { d ->
        updateDraft(d.copy(picking = false, pickQuery = "", lineEditor = LineDraft(p.id, p.name + (p.variant?.let { v -> " · $v" } ?: ""), "", p.costMinor?.let { plain(it) }.orEmpty())))
    }

    fun pickFree() = _ui.value.draft?.let { d -> updateDraft(d.copy(picking = false, lineEditor = LineDraft())) }
    fun updateLine(l: LineDraft) = _ui.value.draft?.let { d -> updateDraft(d.copy(lineEditor = l)) }
    fun closeLine() = _ui.value.draft?.let { d -> updateDraft(d.copy(lineEditor = null)) }

    fun editLine(line: PurchaseLine) = _ui.value.draft?.let { d ->
        updateDraft(d.copy(lineEditor = LineDraft(line.productId, line.name, java.math.BigDecimal.valueOf(line.quantityMilli, 3).stripTrailingZeros().toPlainString(), plain(line.unitCostMinor), line.id)))
    }

    fun saveLine() {
        val d = _ui.value.draft ?: return
        val l = d.lineEditor ?: return
        val qty = Money3.parse(l.quantity, 3)?.takeIf { it > 0 }
        val cost = Money.parse(l.cost.ifBlank { "0" }, decimals())?.minor
        if (l.name.isBlank() || qty == null || cost == null) { updateDraft(d.copy(error = true)); return }
        val line = PurchaseLine(l.lineId ?: UUID.randomUUID().toString(), l.productId, l.name.trim(), qty, cost)
        val lines = if (l.lineId != null) d.lines.map { if (it.id == l.lineId) line else it } else d.lines + line
        updateDraft(d.copy(lines = lines, lineEditor = null))
    }

    fun totalOf(d: PurchaseDraft) = d.lines.sumOf { it.totalMinor }

    fun savePurchase() {
        val d = _ui.value.draft ?: return
        val total = totalOf(d)
        if (d.lines.isEmpty()) { updateDraft(d.copy(error = true)); return }
        val paid = if (d.paid == null) total else Money.parse(d.paid.ifBlank { "0" }, decimals())?.minor
        if (paid == null || paid < 0 || paid > total) { updateDraft(d.copy(error = true)); return }
        viewModelScope.launch {
            val saved = c.purchases.register(d.supplierId, d.supplierName, d.lines, paid, d.paidSource.takeIf { paid > 0 }, d.note)
            if (saved == null) updateDraft(d.copy(error = true)) else _ui.update { it.copy(draft = null) }
        }
    }

    // ---------- pagos ----------
    fun askPayPurchase(row: PurchaseRow) {
        val owed = (row.purchase.totalMinor - row.paidMinor).coerceAtLeast(0)
        if (owed > 0) _ui.update { it.copy(pay = PayDraft(row.purchase.id, null, row.purchase.supplierName.orEmpty(), owed, plain(owed))) }
    }

    fun askPaySupplier(s: SupplierEntity, owed: Long) {
        if (owed > 0) _ui.update { it.copy(pay = PayDraft(null, s.id, s.name, owed, plain(owed))) }
    }

    fun updatePay(d: PayDraft) = _ui.update { it.copy(pay = d) }
    fun closePay() = _ui.update { it.copy(pay = null) }

    fun confirmPay() {
        val d = _ui.value.pay ?: return
        val amount = Money.parse(d.amount, decimals())?.minor?.takeIf { it > 0 } ?: return
        viewModelScope.launch {
            if (d.purchaseId != null) c.purchases.pay(d.purchaseId, amount, d.source, d.note)
            else if (d.supplierId != null) c.purchases.payOldestFirst(d.supplierId, amount, d.source)
            _ui.update { it.copy(pay = null) }
        }
    }

    // ---------- anular ----------
    fun askVoidPurchase(id: String) = _ui.update { it.copy(void = VoidDraft(id, null)) }
    fun askVoidPayment(id: String) = _ui.update { it.copy(void = VoidDraft(null, id)) }
    fun updateVoid(reason: String) = _ui.update { s -> s.copy(void = s.void?.copy(reason = reason)) }
    fun closeVoid() = _ui.update { it.copy(void = null) }

    fun confirmVoid() {
        val v = _ui.value.void ?: return
        viewModelScope.launch {
            if (v.purchaseId != null) c.purchases.voidPurchase(v.purchaseId, v.reason) else if (v.paymentId != null) c.purchases.voidPayment(v.paymentId, v.reason)
            _ui.update { it.copy(void = null) }
        }
    }

    // ---------- proveedores ----------
    fun newSupplier() = _ui.update { it.copy(supplierEditor = SupplierDraft()) }
    fun editSupplier(s: SupplierEntity) = _ui.update { it.copy(supplierEditor = SupplierDraft(s.id, s.name, s.phone.orEmpty(), s.notes.orEmpty())) }
    fun updateSupplier(d: SupplierDraft) = _ui.update { it.copy(supplierEditor = d.copy(error = false)) }
    fun closeSupplier() = _ui.update { it.copy(supplierEditor = null) }

    fun saveSupplier() {
        val d = _ui.value.supplierEditor ?: return
        viewModelScope.launch {
            val saved = c.purchases.saveSupplier(d.id, d.name, d.phone, d.notes)
            if (saved == null) updateSupplier(d.copy(error = true)) else _ui.update { s ->
                // Si el proveedor se creó armando una compra, queda elegido.
                s.copy(supplierEditor = null, draft = s.draft?.copy(supplierId = saved.id))
            }
        }
    }

    private fun decimals() = business.value?.let { Currency.of(it.currency).decimals } ?: 2
    private fun plain(minor: Long) = java.math.BigDecimal.valueOf(minor, decimals()).toPlainString()
}

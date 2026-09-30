package com.cuadra.caja.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cuadra.caja.AppContainer
import com.cuadra.caja.core.model.Money
import com.cuadra.caja.data.local.BusinessEntity
import com.cuadra.caja.data.local.CreditItem
import com.cuadra.caja.data.local.CreditTotals
import com.cuadra.caja.data.local.CustomerEntity
import com.cuadra.caja.data.repo.ManualResult
import com.cuadra.caja.data.repo.PayResult
import com.cuadra.caja.data.repo.PaymentReceipt
import com.cuadra.caja.data.repo.SaveCustomer
import com.cuadra.caja.data.session.Session
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.cuadra.caja.R

enum class CreditFilter { OPEN, WITH, WITHOUT, OLD, PAID }
enum class LedgerMode { CREDITS, CUSTOMERS }

/** Una fila de la lista: los fiados de un mismo cliente se agrupan; los de "solo nota" se muestran sueltos. */
sealed interface LedgerRow {
    val sortKey: Long
    data class Customer(val customer: CustomerEntity, val credits: List<CreditItem>, val balanceMinor: Long, override val sortKey: Long) : LedgerRow
    data class Note(val item: CreditItem, override val sortKey: Long) : LedgerRow
}

/** A quién se abona: a un fiado concreto o a un cliente (se reparte del más viejo al más nuevo). */
data class PayTarget(val creditId: String?, val customerId: String?, val title: String, val balanceMinor: Long, val phone: String?, val settleAll: Boolean = false)

data class CustomerDraft(val id: String? = null, val name: String = "", val phone: String = "", val notes: String = "", val limit: String = "", val balanceMinor: Long = 0)

data class ManualDraft(val debtor: String = "", val phone: String = "", val amount: String = "", val note: String = "", val customerId: String? = null)

/** Qué se va a compartir por WhatsApp. El contenido se arma al abrir el diálogo, con el idioma y la moneda del momento. */
sealed interface ShareRequest {
    data class Reminder(val creditId: String?, val customerId: String?) : ShareRequest
    data class Statement(val customerId: String) : ShareRequest
    data class CreditNew(
        val creditId: String?, val customerId: String?, val debtor: String, val phone: String?, val creditedMinor: Long, val paidNowMinor: Long?,
        val items: List<Pair<String, Long>>,
    ) : ShareRequest
    data class Payment(val receipt: PaymentReceipt) : ShareRequest
    /** Comprobante de una venta cobrada (sin cliente: el chat se elige en WhatsApp). `items`: nombre y monto de cada línea. */
    data class Ticket(val items: List<Pair<String, Long>>, val totalMinor: Long) : ShareRequest
}

data class MovementUi(
    val isPayment: Boolean, val at: Long, val amountMinor: Long, val label: String, val member: String?, val voided: Boolean,
    val creditId: String, val paymentId: String?, val groupId: String?,
)

data class CustomerDetailUi(val customer: CustomerEntity, val movements: List<MovementUi>, val openCredits: List<com.cuadra.caja.data.local.CreditEntity>)

data class CreditsUi(
    val mode: LedgerMode = LedgerMode.CREDITS,
    val filter: CreditFilter = CreditFilter.OPEN,
    val query: String = "",
    val pay: PayTarget? = null,
    val manual: ManualDraft? = null,
    val customerEditor: CustomerDraft? = null,
    val openCustomerId: String? = null,
    val linkingCreditId: String? = null,
    val writeOffCreditId: String? = null,
    /** Abono que se va a anular (la hoja pide el motivo) y cliente que se va a archivar (la hoja pide confirmar). */
    val voidPaymentId: String? = null,
    val archiveCustomerId: String? = null,
    val share: ShareRequest? = null,
    val messageRes: Int? = null,
    /** Desde cuántos días un fiado se considera «vencido»: el ajuste del negocio (`creditOverdueDays`). */
    val overdueDays: Int = 30,
)

class CreditsViewModel(private val c: AppContainer) : ViewModel(), CreditsActions {
    private val _ui = MutableStateFlow(CreditsUi())
    val ui: StateFlow<CreditsUi> = _ui.asStateFlow()

    val business: StateFlow<BusinessEntity?> = c.db.directory().business().stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val session: StateFlow<Session?> = c.sessionStore.flow.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    private val country: String? get() = business.value?.country

    /** Quién puede condonar deudas y anular abonos (el servidor lo exige igual). */
    val canManage: StateFlow<Boolean> = session.map { it?.memberRole == "OWNER" || it?.memberRole == "ADMIN" }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** Días para considerar vencido un fiado: lo que el dueño configuró en Ajustes del negocio (30 mientras no se sepa). */
    private val overdueDays: StateFlow<Int> = business.map { it?.creditOverdueDays ?: DEFAULT_OVERDUE_DAYS }.stateIn(viewModelScope, SharingStarted.Eagerly, DEFAULT_OVERDUE_DAYS)

    init {
        viewModelScope.launch { overdueDays.collect { d -> _ui.update { it.copy(overdueDays = d) } } }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val totals: StateFlow<CreditTotals> = overdueDays.flatMapLatest { c.credits.totals(it) }.stateIn(viewModelScope, SharingStarted.Eagerly, CreditTotals(0, 0, 0, 0))

    @OptIn(ExperimentalCoroutinesApi::class)
    private val items: Flow<List<CreditItem>> = combine(_ui.map { it.filter to it.query }.distinctUntilChanged(), overdueDays) { fq, days -> Triple(fq.first, fq.second, days) }.flatMapLatest { (filter, q, days) ->
        when (filter) {
            CreditFilter.OPEN -> c.credits.list("OPEN", "ALL", null, q)
            CreditFilter.WITH -> c.credits.list("OPEN", "WITH", null, q)
            CreditFilter.WITHOUT -> c.credits.list("OPEN", "WITHOUT", null, q)
            CreditFilter.OLD -> c.credits.list("OPEN", "ALL", days, q)
            CreditFilter.PAID -> c.credits.list("PAID", "ALL", null, q)
        }
    }

    /** Los fiados de un mismo cliente son una sola fila con su saldo total; los de "solo nota" van sueltos. Del más viejo al más nuevo. */
    val rows: StateFlow<List<LedgerRow>> = combine(items, c.db.customers().search("", "")) { list, customers ->
        val byId = customers.associateBy { it.id }
        val out = mutableListOf<LedgerRow>()
        list.filter { it.credit.customerId != null }.groupBy { it.credit.customerId!! }.forEach { (id, credits) ->
            val cust = byId[id] ?: return@forEach
            out += LedgerRow.Customer(cust, credits, credits.sumOf { it.credit.balanceMinor }, credits.minOf { it.credit.createdAt })
        }
        list.filter { it.credit.customerId == null }.forEach { out += LedgerRow.Note(it, it.credit.createdAt) }
        out.sortedBy { it.sortKey }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val customers: StateFlow<List<CustomerEntity>> = _ui.map { it.query }.flatMapLatest { c.customers.search(it) }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val detail: StateFlow<CustomerDetailUi?> = _ui.map { it.openCustomerId }.flatMapLatest { id ->
        if (id == null) flowOf(null) else combine(c.customers.observe(id), c.credits.forCustomer(id), c.credits.paymentsForCustomer(id)) { cust, credits, payments ->
            if (cust == null) null else CustomerDetailUi(cust, movements(credits, payments), credits.map { it.credit }.filter { it.status == "OPEN" && it.balanceMinor > 0 })
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private fun movements(credits: List<CreditItem>, payments: List<com.cuadra.caja.data.local.CreditPaymentEntity>): List<MovementUi> {
        val out = mutableListOf<MovementUi>()
        for (i in credits) {
            val cr = i.credit
            if (cr.status == "CANCELLED") continue
            out += MovementUi(false, cr.createdAt, cr.amountMinor, cr.note ?: cr.debtorLabel, cr.createdByName, false, cr.id, null, null)
        }
        for (p in payments) out += MovementUi(true, p.occurredAt, p.amountMinor, p.method, p.createdByName, p.voided, p.creditId, p.id, p.groupId)
        return out.sortedByDescending { it.at }
    }

    // ---------- navegación de la pantalla ----------
    override fun setMode(mode: LedgerMode) = _ui.update { it.copy(mode = mode) }
    override fun setFilter(f: CreditFilter) = _ui.update { it.copy(filter = f) }
    override fun setQuery(q: String) = _ui.update { it.copy(query = q) }
    override fun openCustomer(id: String?) = _ui.update { it.copy(openCustomerId = id) }
    override fun dismissMessage() = _ui.update { it.copy(messageRes = null) }

    // ---------- abonar ----------
    override fun askPay(target: PayTarget) = _ui.update { it.copy(pay = target) }
    override fun closePay() = _ui.update { it.copy(pay = null) }

    override fun confirmPay(amountText: String, method: String, reference: String, sendReceipt: Boolean) {
        val target = _ui.value.pay ?: return
        val amount = Money.parse(amountText, currencyDecimals())?.minor ?: return
        if (amount <= 0) return
        viewModelScope.launch {
            val result = if (target.creditId != null) c.credits.payCredit(target.creditId, amount, method, reference) else c.credits.payCustomer(target.customerId!!, amount, method, reference)
            when (result) {
                is PayResult.Paid -> _ui.update { it.copy(pay = null, share = if (sendReceipt) ShareRequest.Payment(result.receipt) else null) }
                PayResult.NothingOwed, PayResult.NotFound -> _ui.update { it.copy(pay = null) }
            }
        }
    }

    // ---------- fiado sin venta ----------
    override fun openManual() = _ui.update { it.copy(manual = ManualDraft()) }
    override fun updateManual(d: ManualDraft) = _ui.update { it.copy(manual = d) }
    override fun closeManual() = _ui.update { it.copy(manual = null) }

    override fun saveManual() {
        val d = _ui.value.manual ?: return
        val amount = Money.parse(d.amount, currencyDecimals())?.minor ?: 0
        if (amount <= 0 || (d.debtor.isBlank() && d.customerId == null)) return _ui.update { it.copy(messageRes = R.string.manual_debtor_required) }
        viewModelScope.launch {
            when (c.credits.addManual(d.debtor, d.phone, d.customerId, amount, d.note, country)) {
                is ManualResult.Created -> _ui.update { it.copy(manual = null) }
                ManualResult.InvalidPhone -> _ui.update { it.copy(messageRes = R.string.customer_invalid_phone) }
                ManualResult.DebtorRequired -> _ui.update { it.copy(messageRes = R.string.manual_debtor_required) }
            }
        }
    }

    // ---------- clientes ----------
    override fun openCustomerEditor(draft: CustomerDraft) = _ui.update { it.copy(customerEditor = draft) }
    override fun updateCustomerEditor(d: CustomerDraft) = _ui.update { it.copy(customerEditor = d) }
    override fun closeCustomerEditor() = _ui.update { it.copy(customerEditor = null) }

    override fun saveCustomer() {
        val d = _ui.value.customerEditor ?: return
        val limit = com.cuadra.caja.domain.CreditLimitField.parse(d.limit, currencyDecimals())
        viewModelScope.launch {
            when (c.customers.save(d.id, d.name, d.phone, d.notes, limit, country)) {
                is SaveCustomer.Saved -> _ui.update { it.copy(customerEditor = null) }
                SaveCustomer.InvalidPhone -> _ui.update { it.copy(messageRes = R.string.customer_invalid_phone) }
                SaveCustomer.InvalidName -> _ui.update { it.copy(messageRes = R.string.customer_name_required) }
            }
        }
    }

    override fun askLink(creditId: String?) = _ui.update { it.copy(linkingCreditId = creditId) }
    override fun link(customerId: String) {
        val id = _ui.value.linkingCreditId ?: return
        viewModelScope.launch {
            c.credits.link(id, customerId)
            _ui.update { it.copy(linkingCreditId = null) }
        }
    }

    // ---------- condonar / anular ----------
    override fun askWriteOff(creditId: String?) = _ui.update { it.copy(writeOffCreditId = creditId) }
    override fun writeOff(reason: String) {
        val id = _ui.value.writeOffCreditId ?: return
        if (reason.isBlank()) return
        viewModelScope.launch {
            c.credits.writeOff(id, reason.trim())
            _ui.update { it.copy(writeOffCreditId = null) }
        }
    }

    override fun askVoidPayment(paymentId: String?) = _ui.update { it.copy(voidPaymentId = paymentId) }
    override fun voidPayment(reason: String) {
        val id = _ui.value.voidPaymentId ?: return
        viewModelScope.launch {
            c.credits.voidPayment(id, reason.trim().ifEmpty { null })
            _ui.update { it.copy(voidPaymentId = null) }
        }
    }

    override fun askArchiveCustomer(id: String?) = _ui.update { it.copy(archiveCustomerId = id) }
    override fun archiveCustomer() {
        val id = _ui.value.archiveCustomerId ?: return
        viewModelScope.launch {
            val e = c.customers.get(id)
            // Con saldo pendiente no se archiva: la deuda se seguiría cobrando a alguien que ya no aparece en ninguna lista.
            if (e != null && e.balanceMinor <= 0) {
                c.customers.save(id, e.name, e.phone, e.notes, e.creditLimitMinor, country, archived = true)
                _ui.update { it.copy(archiveCustomerId = null, customerEditor = null, openCustomerId = null) }
            } else {
                _ui.update { it.copy(archiveCustomerId = null, messageRes = R.string.customer_archive_needs_zero) }
            }
        }
    }

    // ---------- compartir ----------
    override fun share(request: ShareRequest?) = _ui.update { it.copy(share = request) }

    private fun currencyDecimals(): Int = business.value?.let { com.cuadra.caja.core.model.Currency.of(it.currency).decimals } ?: 2

    companion object {
        /** Umbral de «vencido» mientras no se sepa el del negocio (`creditOverdueDays`, que el dueño cambia en Ajustes del negocio). */
        const val DEFAULT_OVERDUE_DAYS = 30
    }
}

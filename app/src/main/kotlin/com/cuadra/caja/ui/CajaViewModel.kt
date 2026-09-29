package com.cuadra.caja.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cuadra.caja.AppContainer
import com.cuadra.caja.core.model.Currency
import com.cuadra.caja.core.model.Money
import com.cuadra.caja.data.local.BusinessEntity
import com.cuadra.caja.data.local.CustomerEntity
import com.cuadra.caja.data.local.ProductEntity
import com.cuadra.caja.data.local.SaleEntity
import com.cuadra.caja.data.remote.ProductInputDto
import com.cuadra.caja.data.repo.ResumeResult
import com.cuadra.caja.data.repo.SaveCustomer
import com.cuadra.caja.data.repo.ScanLookup
import com.cuadra.caja.data.sync.posViews
import com.cuadra.caja.domain.AmountEntry
import com.cuadra.caja.domain.BusinessDay
import com.cuadra.caja.domain.Cart
import com.cuadra.caja.domain.PayMethod
import com.cuadra.caja.domain.PaymentPlan
import com.cuadra.caja.domain.PhoneNumbers
import com.cuadra.caja.domain.PhoneResult
import com.cuadra.caja.domain.SaleMath
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class PosTab { TYPE, QUICK, LIST }

/** Venta de un producto por peso: se escribe el peso o el monto y la caja calcula el otro. */
data class Weighing(val product: ProductEntity, val byAmount: Boolean = true, val text: String = "") {
    /** Cantidad en milésimas que resulta de lo escrito, o null si aún no es válido. */
    fun quantityMilli(decimals: Int): Long? {
        if (byAmount) {
            val amount = Money.parse(text, decimals)?.minor ?: return null
            return SaleMath.quantityForAmount(amount, product.priceMinor)
        }
        val bd = runCatching { java.math.BigDecimal(text.ifEmpty { return null }) }.getOrNull() ?: return null
        if (bd.stripTrailingZeros().scale() > 3 || bd.signum() <= 0) return null
        return runCatching { bd.movePointRight(3).longValueExact() }.getOrNull()
    }

    fun totalMinor(decimals: Int): Long? = quantityMilli(decimals)?.let { runCatching { SaleMath.lineTotal(product.priceMinor, it) }.getOrNull() }
}

data class CobroUi(
    val plan: PaymentPlan,
    val tenderedText: String = "",
    val debtor: String = "",
    val debtorPhone: String = "",
    /** Cliente elegido de la lista (opcional: un nombre escrito basta). */
    val customer: CustomerEntity? = null,
    val saveAsCustomer: Boolean = false,
    val sendWhatsApp: Boolean = true,
    val phoneInvalid: Boolean = false,
    val nameSuggestions: List<String> = emptyList(),
    val customerMatches: List<CustomerEntity> = emptyList(),
    val otherLabel: String = "",
    val saving: Boolean = false,
    /** Venta ya cobrada: muestra el vuelto y "Nueva venta". */
    val doneChangeMinor: Long? = null,
    /** Si hubo fiado, lo necesario para enviar el detalle por WhatsApp. */
    val doneShare: ShareRequest.CreditNew? = null,
)

data class ProductDraft(val name: String = "", val price: String = "", val byWeight: Boolean = false, val quick: Boolean = true, val barcode: String = "")

sealed interface Notice {
    data object CodeUnknown : Notice
    data object CodeUnknownOffline : Notice
    data object TicketLocked : Notice
    data object InvalidProduct : Notice
}

data class CajaUi(
    val cart: Cart = Cart(),
    val entry: AmountEntry = AmountEntry(),
    val description: String = "",
    val tab: PosTab = PosTab.TYPE,
    val query: String = "",
    val resumedId: String? = null,
    val resumedLabel: String? = null,
    val weighing: Weighing? = null,
    val cobro: CobroUi? = null,
    val draft: ProductDraft? = null,
    val parking: Boolean = false,
    val showParked: Boolean = false,
    val notice: Notice? = null,
    val share: ShareRequest? = null,
)

class CajaViewModel(private val c: AppContainer) : ViewModel() {
    private val _ui = MutableStateFlow(CajaUi())
    val ui: StateFlow<CajaUi> = _ui.asStateFlow()

    val business: StateFlow<BusinessEntity?> = c.db.directory().business().stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Pestañas que este negocio usa, en su orden. */
    val tabs: StateFlow<List<PosTab>> = business.map { b ->
        val views = b?.posViews().orEmpty().mapNotNull { runCatching { PosTab.valueOf(it) }.getOrNull() }
        views.ifEmpty { listOf(PosTab.TYPE) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, listOf(PosTab.TYPE))

    val quick: StateFlow<List<ProductEntity>> = c.products.quick().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val results: StateFlow<List<ProductEntity>> = _ui.map { it.query }.flatMapLatest { c.products.search(it) }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val parked: StateFlow<List<SaleEntity>> = c.sales.parked().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Lo vendido en la jornada actual según lo que este teléfono conoce (efectivo, fiado, etc.). */
    @OptIn(ExperimentalCoroutinesApi::class)
    val soldToday: StateFlow<Long> = business.flatMapLatest { b ->
        if (b == null) flowOf(0L) else {
            val day = BusinessDay.of(Instant.now(), ZoneId.of(b.timezone), LocalTime.parse(b.dayCutoff))
            c.sales.dayTotals(day.startMillis, day.endMillis).map { it.total }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, 0L)

    private val decimals: Int get() = business.value?.let { Currency.of(it.currency).decimals } ?: 2

    // ---------- teclado ----------
    fun key(d: Char) = _ui.update { it.copy(entry = it.entry.digit(d, decimals)) }
    fun dot() = _ui.update { it.copy(entry = it.entry.dot(decimals)) }
    fun times() = _ui.update { it.copy(entry = it.entry.times()) }
    fun backspace() = _ui.update { it.copy(entry = it.entry.backspace()) }
    fun setDescription(text: String) = _ui.update { it.copy(description = text.take(80)) }
    fun setTab(tab: PosTab) = _ui.update { it.copy(tab = tab) }

    fun addEntry() = _ui.update {
        val r = it.entry.result(decimals) ?: return@update it
        it.copy(cart = it.cart.addFree(r.unitPriceMinor, it.description, r.quantityMilli), entry = AmountEntry(), description = "")
    }

    // ---------- productos ----------
    fun tapProduct(p: ProductEntity) {
        if (p.pricing == "BY_WEIGHT") _ui.update { it.copy(weighing = Weighing(p)) }
        else _ui.update { it.copy(cart = it.cart.addProduct(p.id, p.barcode, p.name, p.variant, p.priceMinor, p.costMinor)) }
    }

    fun setQuery(q: String) = _ui.update { it.copy(query = q) }

    /** Lo escrito en el buscador tiene forma de código: se busca exacto (local, UPC/EAN, y si hace falta el servidor). */
    fun submitCode(raw: String) {
        viewModelScope.launch {
            when (val r = c.products.byBarcode(raw)) {
                is ScanLookup.Found -> tapProduct(r.product).also { setQuery("") }
                ScanLookup.Unknown -> _ui.update { it.copy(notice = Notice.CodeUnknown, draft = ProductDraft(barcode = raw.trim())) }
                ScanLookup.UnknownOffline -> _ui.update { it.copy(notice = Notice.CodeUnknownOffline) }
            }
        }
    }

    fun dismissNotice() = _ui.update { it.copy(notice = null) }

    // ---------- pesar ----------
    fun weighingMode(byAmount: Boolean) = _ui.update { s -> s.weighing?.let { s.copy(weighing = it.copy(byAmount = byAmount, text = "")) } ?: s }
    fun weighingKey(d: Char) = _ui.update { s -> s.weighing?.let { s.copy(weighing = it.copy(text = (it.text + d).take(10))) } ?: s }
    fun weighingDot() = _ui.update { s -> s.weighing?.takeIf { !it.text.contains('.') }?.let { s.copy(weighing = it.copy(text = it.text.ifEmpty { "0" } + ".")) } ?: s }
    fun weighingBackspace() = _ui.update { s -> s.weighing?.let { s.copy(weighing = it.copy(text = it.text.dropLast(1))) } ?: s }
    fun weighingCancel() = _ui.update { it.copy(weighing = null) }
    fun weighingPreset(text: String, byAmount: Boolean) = _ui.update { s -> s.weighing?.let { s.copy(weighing = it.copy(byAmount = byAmount, text = text)) } ?: s }

    fun weighingConfirm() = _ui.update { s ->
        val w = s.weighing ?: return@update s
        val qty = w.quantityMilli(decimals) ?: return@update s
        s.copy(cart = s.cart.addProduct(w.product.id, w.product.barcode, w.product.name, w.product.variant, w.product.priceMinor, w.product.costMinor, qty), weighing = null)
    }

    // ---------- recibo ----------
    fun changeQuantity(lineId: String, deltaMilli: Long) = _ui.update { it.copy(cart = it.cart.changeQuantity(lineId, deltaMilli)) }
    fun removeLine(lineId: String) = _ui.update { it.copy(cart = it.cart.remove(lineId)) }
    fun clearCart() = _ui.update { it.copy(cart = Cart(), resumedId = null, resumedLabel = null) }

    // ---------- crear producto ----------
    fun openDraft() = _ui.update { it.copy(draft = ProductDraft()) }
    fun updateDraft(draft: ProductDraft) = _ui.update { it.copy(draft = draft) }
    fun closeDraft() = _ui.update { it.copy(draft = null) }

    fun saveDraft() {
        val d = _ui.value.draft ?: return
        val price = Money.parse(d.price, decimals)?.minor
        if (d.name.isBlank() || price == null || price <= 0) {
            _ui.update { it.copy(notice = Notice.InvalidProduct) }
            return
        }
        viewModelScope.launch {
            val saved = c.products.save(null, ProductInputDto(
                barcode = d.barcode.trim().ifEmpty { null }, name = d.name.trim(), priceMinor = price,
                unit = if (d.byWeight) "LB" else "UNIT", pricing = if (d.byWeight) "BY_WEIGHT" else "FIXED", isQuick = d.quick,
            ))
            _ui.update { it.copy(draft = null, notice = null) }
            if (d.barcode.isNotBlank()) tapProduct(saved)   // vino de un código desconocido: se agrega a la venta que se estaba haciendo
        }
    }

    // ---------- apartar / retomar ----------
    fun askPark() = _ui.update { if (it.cart.isEmpty) it else it.copy(parking = true) }
    fun cancelPark() = _ui.update { it.copy(parking = false) }
    fun toggleParked(show: Boolean) = _ui.update { it.copy(showParked = show) }

    fun park(label: String) {
        val s = _ui.value
        if (s.cart.isEmpty) return
        viewModelScope.launch {
            c.sales.park(s.cart, label.ifBlank { s.resumedLabel }, s.resumedId ?: java.util.UUID.randomUUID().toString())
            _ui.update { it.copy(cart = Cart(), resumedId = null, resumedLabel = null, parking = false, entry = AmountEntry(), description = "") }
        }
    }

    fun resume(saleId: String) {
        viewModelScope.launch {
            // Lo que había en pantalla se aparta antes de retomar otra cuenta: nunca se pierde (PLAN: ARMarket 2.2).
            val current = _ui.value
            when (val r = c.sales.resume(saleId)) {
                is ResumeResult.Ok -> {
                    if (!current.cart.isEmpty) c.sales.park(current.cart, current.resumedLabel, current.resumedId ?: java.util.UUID.randomUUID().toString())
                    _ui.update { it.copy(cart = r.sale.cart, resumedId = r.sale.saleId, resumedLabel = r.sale.label, showParked = false) }
                }
                ResumeResult.Locked -> _ui.update { it.copy(notice = Notice.TicketLocked) }
                ResumeResult.NotFound -> Unit
            }
        }
    }

    fun discardParked(saleId: String) {
        viewModelScope.launch { c.sales.cancel(saleId, null) }
    }

    // ---------- cobro ----------
    fun startCobro() = _ui.update { if (it.cart.isEmpty || it.cart.totalMinor <= 0) it else it.copy(cobro = CobroUi(PaymentPlan.cash(it.cart.totalMinor))) }
    fun cancelCobro() = _ui.update { it.copy(cobro = null) }

    private fun updateCobro(f: (CobroUi) -> CobroUi) = _ui.update { s -> s.cobro?.takeIf { it.doneChangeMinor == null }?.let { s.copy(cobro = f(it)) } ?: s }

    fun toggleMethod(m: PayMethod) = updateCobro { c ->
        val has = c.plan.entries.any { it.method == m }
        val plan = if (has) (if (c.plan.entries.size > 1) c.plan.withoutMethod(m) else c.plan) else c.plan.withMethod(m)
        c.copy(plan = plan, tenderedText = if (plan.entries.none { it.method == PayMethod.CASH }) "" else c.tenderedText)
    }

    fun setAmount(m: PayMethod, text: String) = updateCobro { c ->
        val minor = Money.parse(text, decimals)?.minor ?: 0
        c.copy(plan = c.plan.withAmount(m, minor))
    }

    fun setTendered(minor: Long?) = updateCobro { it.copy(plan = it.plan.withTendered(minor), tenderedText = minor?.let { m -> Money(m).minor.toString() } ?: "") }
    /** Escribir el nombre de quien debe: suelta el cliente elegido antes (el texto manda) y busca sugerencias. */
    fun setDebtor(text: String) {
        updateCobro { it.copy(debtor = text.take(80), customer = null, plan = it.plan.withDebtor(text, it.debtorPhone, null)) }
        viewModelScope.launch {
            val names = c.customers.nameSuggestions(text)
            val matches = if (text.isBlank()) emptyList() else c.customers.search(text).first().take(4)
            updateCobro { it.copy(nameSuggestions = names.filter { n -> matches.none { m -> m.name == n } }, customerMatches = matches) }
        }
    }

    fun setDebtorPhone(text: String) = updateCobro { it.copy(debtorPhone = text.take(20), phoneInvalid = false, plan = it.plan.withDebtor(it.debtor, text, it.customer?.id)) }

    fun pickCustomer(cust: CustomerEntity) = updateCobro {
        it.copy(debtor = cust.name, debtorPhone = cust.phone.orEmpty(), customer = cust, nameSuggestions = emptyList(), customerMatches = emptyList(), phoneInvalid = false,
            plan = it.plan.withDebtor(cust.name, cust.phone, cust.id))
    }

    fun pickName(name: String) = updateCobro { it.copy(debtor = name, nameSuggestions = emptyList(), customerMatches = emptyList(), plan = it.plan.withDebtor(name, it.debtorPhone, null)) }
    fun toggleSaveCustomer() = updateCobro { it.copy(saveAsCustomer = !it.saveAsCustomer) }
    fun toggleSendWhatsApp() = updateCobro { it.copy(sendWhatsApp = !it.sendWhatsApp) }
    fun shareDismiss() = _ui.update { it.copy(share = null) }
    fun shareDone(request: ShareRequest?) = _ui.update { it.copy(share = request) }
    fun setOtherLabel(text: String) = updateCobro { c ->
        c.copy(otherLabel = text.take(40), plan = PaymentPlan(c.plan.totalMinor, c.plan.entries.map { if (it.method == PayMethod.OTHER) it.copy(otherLabel = text.trim().ifEmpty { null }) else it }))
    }
    fun setReference(m: PayMethod, text: String) = updateCobro { it.copy(plan = it.plan.withReference(m, text)) }

    fun confirmCobro() {
        val s = _ui.value
        val cobro = s.cobro ?: return
        if (!cobro.plan.isValid || cobro.saving) return
        val credit = cobro.plan.effective.firstOrNull { it.method == PayMethod.CREDIT }
        val country = business.value?.country
        // Un teléfono a medias no se guarda: se avisa antes de cobrar (con la venta ya hecha no se podría corregir sin editarla).
        if (credit != null && PhoneNumbers.normalize(cobro.debtorPhone, country) == PhoneResult.Invalid) {
            _ui.update { it.copy(cobro = cobro.copy(phoneInvalid = true)) }
            return
        }
        _ui.update { it.copy(cobro = cobro.copy(saving = true)) }
        viewModelScope.launch {
            var plan = cobro.plan
            var customer = cobro.customer
            // "Guardar como cliente": se crea antes de cobrar y el fiado queda vinculado a él desde el principio.
            if (credit != null && cobro.saveAsCustomer && customer == null) {
                val saved = c.customers.save(null, cobro.debtor, cobro.debtorPhone, null, null, country)
                if (saved is SaveCustomer.Saved) {
                    customer = saved.customer
                    plan = plan.withDebtor(cobro.debtor, cobro.debtorPhone, saved.customer.id)
                }
            }
            val saleId = s.resumedId ?: java.util.UUID.randomUUID().toString()
            c.sales.complete(s.cart, plan, saleId, s.resumedLabel)
            val share = credit?.let { cr ->
                val creditId = c.db.credits().idBySale(saleId)
                val credited = plan.effective.filter { it.method == PayMethod.CREDIT }.sumOf { it.amountMinor }
                val paidNow = plan.effective.filter { it.method != PayMethod.CREDIT }.sumOf { it.amountMinor }.takeIf { it > 0 }
                val name = cr.debtorLabel ?: customer?.name ?: cobro.debtor
                val phone = (PhoneNumbers.normalize(cobro.debtorPhone, country) as? PhoneResult.Valid)?.digits ?: customer?.phone
                ShareRequest.CreditNew(
                    creditId, customer?.id ?: cr.customerId, name, phone, credited, paidNow,
                    s.cart.lines.map { l -> (l.name + if (l.quantityMilli != 1000L) " ×" + com.cuadra.caja.ui.screens.qtyText(l.quantityMilli) else "") to l.totalMinor },
                )
            }
            _ui.update { it.copy(cobro = cobro.copy(plan = plan, saving = false, doneChangeMinor = plan.changeMinor, doneShare = share), share = if (cobro.sendWhatsApp) share else null) }
        }
    }

    /** "Nueva venta": limpia el recibo y cierra el cobro terminado. */
    fun finishCobro() = _ui.update { CajaUi(tab = it.tab) }
}

/** Utilidad de pantalla: lista con sincronización pendiente. */
fun Flow<List<SaleEntity>>.asState(scope: kotlinx.coroutines.CoroutineScope): StateFlow<List<SaleEntity>> = stateIn(scope, SharingStarted.Eagerly, emptyList())

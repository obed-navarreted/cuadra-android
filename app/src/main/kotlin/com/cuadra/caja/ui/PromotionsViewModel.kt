package com.cuadra.caja.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cuadra.caja.AppContainer
import com.cuadra.caja.core.model.Currency
import com.cuadra.caja.core.model.Money
import com.cuadra.caja.data.local.BusinessEntity
import com.cuadra.caja.data.local.ProductEntity
import com.cuadra.caja.data.repo.ScanLookup
import com.cuadra.caja.data.sync.calendar
import com.cuadra.caja.domain.ProductSearch
import com.cuadra.caja.domain.PromotionCheck
import com.cuadra.caja.domain.PromotionEditor
import com.cuadra.caja.domain.PromotionError
import com.cuadra.caja.domain.PromotionForm
import com.cuadra.caja.domain.PromotionRule
import com.cuadra.caja.domain.PromotionState
import com.cuadra.caja.domain.ScanCode
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Una fila de la lista: la promoción, su estado hoy y los nombres de sus productos (los primeros). */
data class PromotionRow(val rule: PromotionRule, val state: PromotionState, val productNames: List<String>)

/** El aviso breve del editor al agregar un producto (buscado o escaneado). */
sealed interface PromoNotice {
    data class Added(val name: String) : PromoNotice
    data class Already(val name: String) : PromoNotice
    data class NotEligible(val name: String) : PromoNotice
    data class UnknownCode(val code: String) : PromoNotice
}

/** El editor abierto: `id` nulo = nueva. `products`: los elegidos, en el orden en que se agregaron. */
data class PromotionDraft(
    val id: String? = null, val form: PromotionForm = PromotionForm(), val products: List<ProductEntity> = emptyList(),
    val query: String = "", val results: List<ProductEntity> = emptyList(), val errors: Set<PromotionError> = emptySet(),
    val notice: PromoNotice? = null, val noticeTick: Int = 0, val scanning: Boolean = false, val scanAdded: Int = 0,
    val confirmDelete: Boolean = false, val withDates: Boolean = false,
)

data class PromotionsUi(val draft: PromotionDraft? = null)

interface PromotionsActions {
    fun newPromotion() {}
    fun edit(id: String) {}
    fun close() {}
    fun setName(text: String) {}
    fun setQuantity(text: String) {}
    fun setPrice(text: String) {}
    fun setActive(active: Boolean) {}
    fun setWithDates(on: Boolean) {}
    fun setStartsOn(date: LocalDate?) {}
    fun setEndsOn(date: LocalDate?) {}
    fun setQuery(text: String) {}
    fun addProduct(p: ProductEntity) {}
    fun removeProduct(id: String) {}
    fun openScanner() {}
    fun closeScanner() {}
    fun onScanned(code: String) {}
    fun dismissNotice() {}
    fun save() {}
    fun toggleActive(id: String, active: Boolean) {}
    fun askDelete() {}
    fun cancelDelete() {}
    fun delete() {}
}

/**
 * Productos › Promociones (dueño y admins): la lista con su estado de hoy y el editor. Los productos se eligen buscando (nombre, código corto o de barras) o
 * escaneando en modo continuo (cámara o lector del equipo): cada lectura agrega uno; si ya estaba, un aviso breve. El ejemplo «7 × C$ 45 = C$ 245» lo calcula
 * el mismo motor que la caja. Se guarda en el teléfono y va por la cola (funciona sin conexión).
 */
class PromotionsViewModel(private val c: AppContainer, private val now: () -> Instant = Instant::now) : ViewModel(), PromotionsActions {
    private val _ui = MutableStateFlow(PromotionsUi())
    val ui: StateFlow<PromotionsUi> = _ui.asStateFlow()

    val business: StateFlow<BusinessEntity?> = c.db.directory().business().stateIn(viewModelScope, SharingStarted.Eagerly, null)
    private val catalog: StateFlow<Pair<List<ProductEntity>, ProductSearch.Index>> = c.products.active().map { it to ProductSearch.Index(it) }
        .flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList<ProductEntity>() to ProductSearch.Index(emptyList()))

    private fun today(): LocalDate = business.value?.calendar()?.dateOf(now()) ?: LocalDate.now()
    private val decimals: Int get() = business.value?.let { Currency.of(it.currency).decimals } ?: 2

    val rows: StateFlow<List<PromotionRow>> = combine(c.promotions.rules(), catalog, business) { rules, (all, _), _ ->
        val names = all.associate { it.id to it.name }
        val day = today()
        rules.map { r -> PromotionRow(r, r.state(day), r.productIds.mapNotNull { names[it] }.sorted()) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private fun update(f: (PromotionDraft) -> PromotionDraft) = _ui.update { s -> s.draft?.let { s.copy(draft = f(it)) } ?: s }

    override fun newPromotion() = _ui.update { it.copy(draft = PromotionDraft()) }

    override fun edit(id: String) {
        viewModelScope.launch {
            val r = c.promotions.get(id) ?: return@launch
            val all = catalog.value.first
            val products = r.productIds.mapNotNull { pid -> all.firstOrNull { it.id == pid } ?: c.db.products().get(pid) }
            val form = PromotionForm(r.name, r.quantity.toString(), Money(r.priceMinor).let { com.cuadra.caja.domain.CashTender.toText(it.minor, decimals) }, r.active, r.startsOn, r.endsOn, products.map { it.id })
            _ui.update { it.copy(draft = PromotionDraft(id, form, products, withDates = r.startsOn != null || r.endsOn != null)) }
        }
    }

    override fun close() = _ui.update { it.copy(draft = null) }
    override fun setName(text: String) = update { it.copy(form = it.form.copy(name = text.take(PromotionEditor.MAX_NAME)), errors = it.errors - PromotionError.NAME) }
    override fun setQuantity(text: String) = update { it.copy(form = it.form.copy(quantity = text.filter(Char::isDigit).take(3)), errors = it.errors - PromotionError.QUANTITY) }
    override fun setPrice(text: String) = update { it.copy(form = it.form.copy(price = com.cuadra.caja.domain.CashTender.sanitize(text, decimals)), errors = it.errors - PromotionError.PRICE) }
    override fun setActive(active: Boolean) = update { it.copy(form = it.form.copy(active = active)) }
    override fun setWithDates(on: Boolean) = update { d ->
        if (on) d.copy(withDates = true, form = d.form.copy(startsOn = d.form.startsOn ?: today()))
        else d.copy(withDates = false, form = d.form.copy(startsOn = null, endsOn = null), errors = d.errors - PromotionError.DATES)
    }
    override fun setStartsOn(date: LocalDate?) = update { it.copy(form = it.form.copy(startsOn = date), errors = it.errors - PromotionError.DATES) }
    override fun setEndsOn(date: LocalDate?) = update { it.copy(form = it.form.copy(endsOn = date), errors = it.errors - PromotionError.DATES) }

    /** Buscar por nombre, código corto o de barras (en el catálogo del teléfono, sin tildes). Solo los de precio fijo entran en una promoción. */
    override fun setQuery(text: String) {
        val q = text.take(80)
        val results = if (q.isBlank()) emptyList() else catalog.value.second.search(q, 12)
        update { it.copy(query = q, results = results) }
    }

    override fun addProduct(p: ProductEntity) = add(p, fromScan = false)

    private fun add(p: ProductEntity, fromScan: Boolean) = update { d ->
        val (ids, outcome) = PromotionEditor.add(d.form.productIds, p.id, p.pricing)
        val notice = when (outcome) {
            PromotionEditor.AddOutcome.ADDED -> PromoNotice.Added(p.name)
            PromotionEditor.AddOutcome.ALREADY -> PromoNotice.Already(p.name)
            PromotionEditor.AddOutcome.NOT_ELIGIBLE -> PromoNotice.NotEligible(p.name)
        }
        val added = outcome == PromotionEditor.AddOutcome.ADDED
        d.copy(
            form = d.form.copy(productIds = ids), products = if (added) d.products + p else d.products, errors = if (added) d.errors - PromotionError.PRODUCTS else d.errors,
            notice = notice, noticeTick = d.noticeTick + 1, scanAdded = if (fromScan && added) d.scanAdded + 1 else d.scanAdded,
            // Al agregar desde la búsqueda se limpia el texto para buscar el siguiente.
            query = if (!fromScan && added) "" else d.query, results = if (!fromScan && added) emptyList() else d.results,
        )
    }

    override fun removeProduct(id: String) = update { d -> d.copy(form = d.form.copy(productIds = d.form.productIds - id), products = d.products.filterNot { it.id == id }) }
    override fun openScanner() = update { it.copy(scanning = true, scanAdded = 0) }
    override fun closeScanner() = update { it.copy(scanning = false) }

    /** Una lectura (cámara en modo continuo o lector del equipo): agrega el producto del código; si ya estaba o no existe, un aviso breve. */
    override fun onScanned(code: String) {
        if (_ui.value.draft == null) return
        viewModelScope.launch {
            val r = ScanCode.resolve(code) { candidate -> (c.products.byBarcode(candidate) as? ScanLookup.Found)?.product }
            when (r) {
                is com.cuadra.caja.domain.ScanResolution.Found -> add(r.item, fromScan = true)
                is com.cuadra.caja.domain.ScanResolution.NotFound -> update { it.copy(notice = PromoNotice.UnknownCode(r.code), noticeTick = it.noticeTick + 1) }
                com.cuadra.caja.domain.ScanResolution.Empty -> Unit
            }
        }
    }

    override fun dismissNotice() = update { it.copy(notice = null) }

    override fun save() {
        val d = _ui.value.draft ?: return
        when (val r = PromotionEditor.check(d.form, decimals)) {
            is PromotionCheck.Invalid -> update { it.copy(errors = r.errors) }
            is PromotionCheck.Valid -> {
                _ui.update { it.copy(draft = null) }
                viewModelScope.launch { c.promotions.save(d.id, r.name, r.productIds, r.quantity, r.priceMinor, d.form.active, d.form.startsOn, d.form.endsOn) }
            }
        }
    }

    override fun toggleActive(id: String, active: Boolean) {
        viewModelScope.launch { c.promotions.setActive(id, active) }
    }

    override fun askDelete() = update { it.copy(confirmDelete = true) }
    override fun cancelDelete() = update { it.copy(confirmDelete = false) }
    override fun delete() {
        val id = _ui.value.draft?.id ?: return
        _ui.update { it.copy(draft = null) }
        viewModelScope.launch { c.promotions.delete(id) }
    }

    /** El ejemplo del editor (con el motor de la caja), o nulo si aún falta algo. */
    fun example(d: PromotionDraft): PromotionEditor.Example? = PromotionEditor.example(d.form, decimals, d.products.map { it.priceMinor })
}

package com.cuadra.caja.ui

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cuadra.caja.AppContainer
import com.cuadra.caja.R
import com.cuadra.caja.core.model.Currency
import com.cuadra.caja.core.model.Money
import com.cuadra.caja.data.local.BusinessEntity
import com.cuadra.caja.data.local.CategoryEntity
import com.cuadra.caja.data.local.ProductEntity
import com.cuadra.caja.data.local.ProductProfit
import com.cuadra.caja.data.local.ProductStock
import com.cuadra.caja.data.local.StockMovementEntity
import com.cuadra.caja.data.repo.StockFilter
import com.cuadra.caja.data.remote.ApiFailure
import com.cuadra.caja.data.remote.ProductHistoryEntryDto
import com.cuadra.caja.domain.Money3
import com.cuadra.caja.domain.ProductEditor
import com.cuadra.caja.domain.ProductEditorResult
import com.cuadra.caja.domain.ProductError
import com.cuadra.caja.domain.ProductForm
import com.cuadra.caja.domain.ProductPayload
import com.cuadra.caja.domain.ProductPermissions
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

/** Lo que se está haciendo con la existencia de un producto: contar, dar de baja o devolver. `initial` = primer conteo al empezar a llevar control. */
data class CountDraft2(val productId: String, val kind: StockAction, val initial: Boolean = false, val amount: String = "", val note: String = "")

enum class StockAction { COUNT, DAMAGE, RETURN }

/**
 * Editor de producto: nuevo (`id == null`) o existente. `form` es lo escrito (texto); `barcodeOwner` = nombre del otro producto que ya tiene ese código
 * (se averigua al escribirlo); `newCategory` != null = se está escribiendo una categoría nueva.
 */
data class ProductEditorDraft(
    val id: String? = null,
    val form: ProductForm = ProductForm(),
    val wasTracked: Boolean = false,
    val errors: Set<ProductError> = emptySet(),
    val barcodeOwner: String? = null,
    val newCategory: String? = null,
)

data class InventoryUi(
    val query: String = "",
    val filter: StockFilter = StockFilter.ALL,
    val selectedId: String? = null,
    val editor: ProductEditorDraft? = null,
    /** Categorías de productos que el teléfono conoce (para elegir en el editor). */
    val categories: List<CategoryEntity> = emptyList(),
    val count: CountDraft2? = null,
    val confirmActive: Boolean? = null,
    val history: HistoryState? = null,
    @StringRes val messageRes: Int? = null,
)

/** El historial del producto abierto: solo se puede leer en línea. */
sealed interface HistoryState {
    data object Loading : HistoryState
    data object Offline : HistoryState
    data object Error : HistoryState
    data class Loaded(val entries: List<ProductHistoryEntryDto>) : HistoryState
}

data class ProductDetail(val stock: ProductStock, val movements: List<StockMovementEntity>, val profit: ProductProfit)

class InventoryViewModel(private val c: AppContainer, private val clock: () -> Long = System::currentTimeMillis) : ViewModel(), InventoryActions {
    private val _ui = MutableStateFlow(InventoryUi())
    val ui: StateFlow<InventoryUi> = _ui.asStateFlow()

    val business: StateFlow<BusinessEntity?> = c.db.directory().business().stateIn(viewModelScope, SharingStarted.Eagerly, null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val items: StateFlow<List<ProductStock>> = _ui.map { it.query to it.filter }.flatMapLatest { (q, f) -> c.inventory.stock(q, f) }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** El rol de quien tiene la caja: decide si se ve dar de baja / reactivar. */
    val role: StateFlow<String?> = c.sessionStore.flow.map { it.memberRole }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val canDelete: StateFlow<Boolean> = role.map { ProductPermissions.canDelete(it) }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val reviewCount: StateFlow<Int> = c.inventory.reviewCount().stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val detail: StateFlow<ProductDetail?> = _ui.map { it.selectedId }.flatMapLatest { id ->
        if (id == null) flowOf(null) else {
            val from = clock() - 30L * 24 * 3600 * 1000
            combine(c.inventory.stockOf(id), c.inventory.movements(id), c.inventory.profit(id, from)) { s, m, p -> s?.let { ProductDetail(it, m, p) } }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    override fun setQuery(q: String) = _ui.update { it.copy(query = q) }
    override fun setFilter(f: StockFilter) = _ui.update { it.copy(filter = f) }
    override fun select(id: String?) = _ui.update { it.copy(selectedId = id, history = null, confirmActive = null) }

    override fun loadHistory(productId: String) {
        _ui.update { it.copy(history = HistoryState.Loading) }
        viewModelScope.launch {
            val r = c.products.history(productId)
            val state = r.fold({ HistoryState.Loaded(it) }, { if (it is ApiFailure.Offline) HistoryState.Offline else HistoryState.Error })
            _ui.update { if (it.selectedId == productId) it.copy(history = state) else it }
        }
    }

    // ---------- dar de baja / reactivar: solo dueño y administrador ----------
    override fun askActive(active: Boolean) { if (canDelete.value) _ui.update { it.copy(confirmActive = active) } }
    override fun cancelActive() = _ui.update { it.copy(confirmActive = null) }
    override fun confirmActive() {
        val active = _ui.value.confirmActive ?: return
        val id = _ui.value.selectedId ?: return
        if (!canDelete.value) { cancelActive(); return }
        viewModelScope.launch {
            val old = c.db.products().get(id) ?: return@launch
            c.products.save(id, ProductPayload.withActive(old, active))
            _ui.update { it.copy(confirmActive = null, selectedId = null, history = null) }
        }
    }
    fun dismissMessage() = _ui.update { it.copy(messageRes = null) }

    // ---------- conteo, baja, devolución ----------
    override fun openCount(productId: String, action: StockAction, initial: Boolean) = _ui.update { it.copy(count = CountDraft2(productId, action, initial)) }
    override fun updateCount(d: CountDraft2) = _ui.update { it.copy(count = d) }
    /** Al terminar (o saltar) el conteo inicial se muestra el producto; antes no, para que el conteo no quede tapado por su detalle. */
    override fun closeCount() = _ui.update { it.copy(selectedId = it.count?.takeIf { d -> d.initial }?.productId ?: it.selectedId, count = null) }

    override fun confirmCount() {
        val d = _ui.value.count ?: return
        val qty = Money3.parse(d.amount, 3) ?: return
        viewModelScope.launch {
            when (d.kind) {
                StockAction.COUNT -> c.inventory.count(d.productId, qty, d.initial, d.note)
                StockAction.DAMAGE -> c.inventory.damage(d.productId, qty, d.note)
                StockAction.RETURN -> c.inventory.returned(d.productId, qty, d.note)
            }
            closeCount()
        }
    }

    // ---------- editor de producto ----------
    init {
        viewModelScope.launch { c.products.categories().collect { list -> _ui.update { it.copy(categories = list) } } }
    }

    override fun newProduct() = _ui.update { it.copy(editor = ProductEditorDraft()) }

    override fun edit(p: ProductEntity) {
        val decimals = decimals()
        _ui.update { it.copy(editor = ProductEditorDraft(p.id, ProductEditor.formOf(p, decimals), wasTracked = p.trackStock)) }
        checkBarcode(p.barcode.orEmpty())
    }

    override fun updateEditor(d: ProductEditorDraft) {
        val before = _ui.value.editor
        _ui.update { it.copy(editor = d.copy(errors = emptySet())) }
        if (before == null || before.form.barcode != d.form.barcode) checkBarcode(d.form.barcode)
    }

    /** Al escribir o escanear un código: ¿ya lo tiene otro producto del catálogo? Se avisa antes de guardar. */
    private fun checkBarcode(code: String) {
        val id = _ui.value.editor?.id
        viewModelScope.launch {
            val owner = c.products.ownerOfBarcode(code, id)
            val label = owner?.let { listOfNotNull(it.name, it.variant).joinToString(" · ") }
            _ui.update { s -> s.editor?.takeIf { it.form.barcode == code }?.let { s.copy(editor = it.copy(barcodeOwner = label)) } ?: s }
        }
    }

    override fun closeEditor() = _ui.update { it.copy(editor = null) }

    override fun confirmNewCategory() {
        val d = _ui.value.editor ?: return
        val name = d.newCategory?.trim().orEmpty()
        if (name.isEmpty()) { _ui.update { it.copy(editor = d.copy(newCategory = null)) }; return }
        viewModelScope.launch {
            // Si ya existe una con ese nombre, se elige esa en vez de duplicarla.
            val existing = _ui.value.categories.firstOrNull { it.name.equals(name, ignoreCase = true) }
            val cat = existing ?: c.products.createCategory(name)
            _ui.update { s -> s.editor?.let { e -> s.copy(editor = e.copy(form = e.form.copy(categoryId = cat.id), newCategory = null)) } ?: s }
        }
    }

    override fun saveEditor() {
        val d = _ui.value.editor ?: return
        val decimals = decimals()
        viewModelScope.launch {
            // Una categoría escrita pero no confirmada también se guarda: la persona la escribió para usarla.
            var form = d.form
            val pending = d.newCategory?.trim().orEmpty()
            val owner = c.products.ownerOfBarcode(form.barcode, d.id)
            val ownerLabel = owner?.let { listOfNotNull(it.name, it.variant).joinToString(" · ") }
            val old = d.id?.let { c.db.products().get(it) }
            when (val r = ProductEditor.build(old, form, decimals, ownerLabel)) {
                is ProductEditorResult.Invalid -> _ui.update { s -> s.editor?.let { s.copy(editor = it.copy(errors = r.errors, barcodeOwner = ownerLabel)) } ?: s }
                is ProductEditorResult.Valid -> {
                    var input = r.input
                    if (pending.isNotEmpty()) {
                        val cat = _ui.value.categories.firstOrNull { it.name.equals(pending, ignoreCase = true) } ?: c.products.createCategory(pending)
                        input = input.copy(categoryId = cat.id)
                    }
                    val saved = c.products.save(d.id, input)
                    // Empezar a llevar control pide un conteo inicial: sin él la existencia no significaría nada.
                    if (form.trackStock && !d.wasTracked) _ui.update { it.copy(editor = null, selectedId = null, count = CountDraft2(saved.id, StockAction.COUNT, initial = true)) }
                    else _ui.update { it.copy(editor = null, selectedId = saved.id) }
                }
            }
        }
    }

    private fun decimals() = business.value?.let { Currency.of(it.currency).decimals } ?: 2
}

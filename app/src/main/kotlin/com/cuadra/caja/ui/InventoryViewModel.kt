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
import com.cuadra.caja.data.local.ProductProfit
import com.cuadra.caja.data.local.ProductStock
import com.cuadra.caja.data.local.StockMovementEntity
import com.cuadra.caja.data.remote.ProductInputDto
import com.cuadra.caja.data.repo.StockFilter
import com.cuadra.caja.domain.Money3
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

/** Editor de producto: nuevo (`id == null`) o existente. Los campos de código, categoría y "rápido" se conservan tal cual. */
data class ProductEditorDraft(
    val id: String? = null, val name: String = "", val price: String = "", val cost: String = "", val unit: String = "UNIT", val minStock: String = "",
    val track: Boolean = false, val wasTracked: Boolean = false, val error: Boolean = false,
)

data class InventoryUi(
    val query: String = "",
    val filter: StockFilter = StockFilter.ALL,
    val selectedId: String? = null,
    val editor: ProductEditorDraft? = null,
    val count: CountDraft2? = null,
    @StringRes val messageRes: Int? = null,
)

data class ProductDetail(val stock: ProductStock, val movements: List<StockMovementEntity>, val profit: ProductProfit)

class InventoryViewModel(private val c: AppContainer, private val clock: () -> Long = System::currentTimeMillis) : ViewModel() {
    private val _ui = MutableStateFlow(InventoryUi())
    val ui: StateFlow<InventoryUi> = _ui.asStateFlow()

    val business: StateFlow<BusinessEntity?> = c.db.directory().business().stateIn(viewModelScope, SharingStarted.Eagerly, null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val items: StateFlow<List<ProductStock>> = _ui.map { it.query to it.filter }.flatMapLatest { (q, f) -> c.inventory.stock(q, f) }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val reviewCount: StateFlow<Int> = c.inventory.reviewCount().stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val detail: StateFlow<ProductDetail?> = _ui.map { it.selectedId }.flatMapLatest { id ->
        if (id == null) flowOf(null) else {
            val from = clock() - 30L * 24 * 3600 * 1000
            combine(c.inventory.stockOf(id), c.inventory.movements(id), c.inventory.profit(id, from)) { s, m, p -> s?.let { ProductDetail(it, m, p) } }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun setQuery(q: String) = _ui.update { it.copy(query = q) }
    fun setFilter(f: StockFilter) = _ui.update { it.copy(filter = f) }
    fun select(id: String?) = _ui.update { it.copy(selectedId = id) }
    fun dismissMessage() = _ui.update { it.copy(messageRes = null) }

    // ---------- conteo, baja, devolución ----------
    fun openCount(productId: String, action: StockAction, initial: Boolean = false) = _ui.update { it.copy(count = CountDraft2(productId, action, initial)) }
    fun updateCount(d: CountDraft2) = _ui.update { it.copy(count = d) }
    /** Al terminar (o saltar) el conteo inicial se muestra el producto; antes no, para que el conteo no quede tapado por su detalle. */
    fun closeCount() = _ui.update { it.copy(selectedId = it.count?.takeIf { d -> d.initial }?.productId ?: it.selectedId, count = null) }

    fun confirmCount() {
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
    fun newProduct() = _ui.update { it.copy(editor = ProductEditorDraft()) }

    fun edit(p: ProductEntity) {
        val decimals = decimals()
        _ui.update {
            it.copy(editor = ProductEditorDraft(
                p.id, p.name, plain(p.priceMinor, decimals), p.costMinor?.let { c -> plain(c, decimals) }.orEmpty(), p.unit,
                p.minStockMilli?.let { m -> java.math.BigDecimal.valueOf(m, 3).stripTrailingZeros().toPlainString() }.orEmpty(), p.trackStock, p.trackStock,
            ))
        }
    }

    fun updateEditor(d: ProductEditorDraft) = _ui.update { it.copy(editor = d.copy(error = false)) }
    fun closeEditor() = _ui.update { it.copy(editor = null) }

    fun saveEditor() {
        val d = _ui.value.editor ?: return
        val decimals = decimals()
        val price = Money.parse(d.price, decimals)?.minor
        val cost = if (d.cost.isBlank()) null else Money.parse(d.cost, decimals)?.minor ?: run { _ui.update { it.copy(editor = d.copy(error = true)) }; return }
        val min = if (d.minStock.isBlank()) null else Money3.parse(d.minStock, 3) ?: run { _ui.update { it.copy(editor = d.copy(error = true)) }; return }
        if (d.name.isBlank() || price == null) { _ui.update { it.copy(editor = d.copy(error = true)) }; return }
        viewModelScope.launch {
            val old = d.id?.let { c.db.products().get(it) }
            val input = ProductInputDto(
                barcode = old?.barcode, shortCode = old?.shortCode, name = d.name.trim(), variant = old?.variant, categoryId = old?.categoryId, unit = d.unit,
                pricing = old?.pricing ?: "FIXED", priceMinor = price, costMinor = cost, isQuick = old?.isQuick ?: false, quickPosition = old?.quickPosition, color = old?.color,
                trackStock = d.track, minStockMilli = min, active = old?.active ?: true,
            )
            val saved = c.products.save(d.id, input)
            // Empezar a llevar control pide un conteo inicial: sin él la existencia no significaría nada.
            if (d.track && !d.wasTracked) _ui.update { it.copy(editor = null, selectedId = null, count = CountDraft2(saved.id, StockAction.COUNT, initial = true)) }
            else _ui.update { it.copy(editor = null, selectedId = saved.id) }
        }
    }

    private fun decimals() = business.value?.let { Currency.of(it.currency).decimals } ?: 2
    private fun plain(minor: Long, decimals: Int) = java.math.BigDecimal.valueOf(minor, decimals).toPlainString()
}

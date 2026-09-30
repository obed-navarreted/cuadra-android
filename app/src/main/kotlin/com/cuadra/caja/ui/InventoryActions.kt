package com.cuadra.caja.ui

import com.cuadra.caja.data.local.ProductEntity
import com.cuadra.caja.data.repo.StockFilter

/** Lo que las pantallas le piden al ViewModel. Tiene cuerpos vacíos por omisión: la guardia de diseño usa `object : InventoryActions {}`. */
interface InventoryActions {
    fun setQuery(q: String) {}
    fun setFilter(f: StockFilter) {}
    fun select(id: String?) {}
    fun loadHistory(productId: String) {}
    fun askActive(active: Boolean) {}
    fun cancelActive() {}
    fun confirmActive() {}
    fun openCount(productId: String, action: StockAction, initial: Boolean) {}
    fun openCount(productId: String, action: StockAction) = openCount(productId, action, false)
    fun updateCount(d: CountDraft2) {}
    fun closeCount() {}
    fun confirmCount() {}
    fun newProduct() {}
    fun edit(p: ProductEntity) {}
    fun updateEditor(d: ProductEditorDraft) {}
    fun closeEditor() {}
    fun confirmNewCategory() {}
    fun saveEditor() {}
}

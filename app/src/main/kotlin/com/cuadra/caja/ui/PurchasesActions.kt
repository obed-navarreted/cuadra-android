package com.cuadra.caja.ui

import com.cuadra.caja.data.local.ProductEntity
import com.cuadra.caja.data.local.PurchaseRow
import com.cuadra.caja.data.local.SupplierEntity
import com.cuadra.caja.data.repo.PurchaseLine

/** Lo que las pantallas le piden al ViewModel. Tiene cuerpos vacíos por omisión: la guardia de diseño usa `object : PurchasesActions {}`. */
interface PurchasesActions {
    fun setTab(t: PurchasesTab) {}
    fun setOnlyOwed(v: Boolean) {}
    fun filterSupplier(id: String?) {}
    fun openDetail(id: String?) {}
    fun dismissMessage() {}
    fun newPurchase(supplierId: String?) {}
    fun closeDraft() {}
    fun updateDraft(d: PurchaseDraft) {}
    fun removeLine(id: String) {}
    fun pickProduct(p: ProductEntity) {}
    fun pickFree() {}
    fun updateLine(l: LineDraft) {}
    fun closeLine() {}
    fun editLine(line: PurchaseLine) {}
    fun saveLine() {}
    fun savePurchase() {}
    fun askPayPurchase(row: PurchaseRow) {}
    fun askPaySupplier(s: SupplierEntity, owed: Long) {}
    fun updatePay(d: PayDraft) {}
    fun closePay() {}
    fun confirmPay() {}
    fun askVoidPurchase(id: String) {}
    fun askVoidPayment(id: String) {}
    fun updateVoid(reason: String) {}
    fun closeVoid() {}
    fun confirmVoid() {}
    fun newSupplier() {}
    fun editSupplier(s: SupplierEntity) {}
    fun updateSupplier(d: SupplierDraft) {}
    fun closeSupplier() {}
    fun saveSupplier() {}
}

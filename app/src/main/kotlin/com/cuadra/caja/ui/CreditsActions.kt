package com.cuadra.caja.ui

/** Lo que las pantallas le piden al ViewModel. Tiene cuerpos vacíos por omisión: la guardia de diseño usa `object : CreditsActions {}`. */
interface CreditsActions {
    fun setMode(mode: LedgerMode) {}
    fun setFilter(f: CreditFilter) {}
    fun setQuery(q: String) {}
    fun openCustomer(id: String?) {}
    fun dismissMessage() {}
    fun askPay(target: PayTarget) {}
    fun closePay() {}
    fun confirmPay(amountText: String, method: String, reference: String, sendReceipt: Boolean) {}
    fun openManual() {}
    fun updateManual(d: ManualDraft) {}
    fun closeManual() {}
    fun saveManual() {}
    fun openCustomerEditor(draft: CustomerDraft) {}
    fun openCustomerEditor() = openCustomerEditor(CustomerDraft())
    fun updateCustomerEditor(d: CustomerDraft) {}
    fun closeCustomerEditor() {}
    fun saveCustomer() {}
    fun askLink(creditId: String?) {}
    fun link(customerId: String) {}
    fun askWriteOff(creditId: String?) {}
    fun writeOff(reason: String) {}
    /** Anular un abono pide un motivo (opcional): primero se abre la hoja, luego se confirma. */
    fun askVoidPayment(paymentId: String?) {}
    fun voidPayment(reason: String) {}
    /** Archivar un cliente (sin saldo): sale de la libreta y del cobro, su historial se conserva. */
    fun askArchiveCustomer(id: String?) {}
    fun archiveCustomer() {}
    fun share(request: ShareRequest?) {}
}

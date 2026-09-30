package com.cuadra.caja.ui

import com.cuadra.caja.data.local.CustomerEntity
import com.cuadra.caja.data.local.ProductEntity
import com.cuadra.caja.domain.PayMethod

/** Lo que las pantallas le piden al ViewModel. Tiene cuerpos vacíos por omisión: la guardia de diseño usa `object : CajaActions {}`. */
interface CajaActions {
    fun key(d: Char) {}
    fun dot() {}
    fun times() {}
    fun backspace() {}
    fun setDescription(text: String) {}
    fun setTab(tab: PosTab) {}
    fun addEntry() {}
    fun tapProduct(p: ProductEntity) {}
    fun setQuery(q: String) {}
    fun submitCode(raw: String) {}
    fun openScanner() {}
    fun closeScanner() {}
    fun onScanned(raw: String) {}
    fun createFromCode(code: String) {}
    fun dismissNotice() {}
    fun weighingMode(byAmount: Boolean) {}
    fun weighingKey(d: Char) {}
    fun weighingDot() {}
    fun weighingBackspace() {}
    fun weighingCancel() {}
    fun weighingConfirm() {}
    fun openPriceKey(d: Char) {}
    fun openPriceDot() {}
    fun openPriceBackspace() {}
    fun openPriceCancel() {}
    fun openPriceConfirm() {}
    fun changeQuantity(lineId: String, deltaMilli: Long) {}
    fun clearCart() {}
    fun deleteLine(lineId: String) {}
    fun editQuantity(lineId: String) {}
    fun closeQuantityEdit() {}
    /** Guardar la hoja de edición de una línea: cantidad (0 = quitar) y, solo en líneas manuales, la descripción (`null` = no se toca). */
    fun editLine(lineId: String, description: String?, milli: Long) {}
    fun undoLast() {}
    fun hideUndo() {}
    fun openReceipt() {}
    fun closeReceipt() {}
    fun dismissSwipeHint() {}
    fun openDraft() {}
    /** «Crear producto “x”» desde una búsqueda sin resultados. */
    fun createFromQuery(query: String) {}
    /** Pulsación larga en un producto (quien puede editar productos): menú «Marcar como frecuente / Quitar de frecuentes». */
    fun openProductMenu(p: ProductEntity) {}
    fun closeProductMenu() {}
    fun toggleFrequent(p: ProductEntity) {}
    /** Modo «Ordenar frecuentes» de la pestaña Productos. */
    fun startReorder() {}
    fun finishReorder() {}
    /** −1 = subir, +1 = bajar. */
    fun moveFrequent(productId: String, delta: Int) {}
    fun updateDraft(draft: ProductDraft) {}
    fun closeDraft() {}
    fun saveDraft() {}
    fun askPark() {}
    fun cancelPark() {}
    fun toggleParked(show: Boolean) {}
    fun park(label: String) {}
    fun resume(saleId: String) {}
    fun discardParked(saleId: String) {}
    fun startCobro() {}
    fun cancelCobro() {}
    fun toggleMethod(m: PayMethod) {}
    fun setAmount(m: PayMethod, text: String) {}
    /** «Completar con X»: lo que falta entra como monto de ese método. */
    fun completeWith(m: PayMethod) {}
    fun setTendered(minor: Long?) {}
    fun setTenderedText(raw: String) {}
    fun setDebtor(text: String) {}
    fun setDebtorPhone(text: String) {}
    fun pickCustomer(cust: CustomerEntity) {}
    fun pickName(name: String) {}
    fun toggleSaveCustomer() {}
    fun toggleSendWhatsApp() {}
    fun shareDismiss() {}
    fun shareDone(request: ShareRequest?) {}
    fun setOtherLabel(text: String) {}
    fun setReference(m: PayMethod, text: String) {}
    fun confirmCobro() {}
    fun finishCobro() {}
    /** «Anular esta venta» en la pantalla de venta cobrada (la última propia, primeros 5 minutos; con motivo). */
    fun askUndoSale() {}
    fun setUndoReason(text: String) {}
    fun closeUndo() {}
    fun confirmUndo() {}
    /** «Imprimir recibo» / «Reintentar»: vuelve a imprimir la venta que se acaba de cobrar. */
    fun printAgain() {}
    fun dismissPrintNotice() {}
    /** El indicador de la impresora en la caja: abre sus ajustes. */
    fun openPrinterSettings() {}
}

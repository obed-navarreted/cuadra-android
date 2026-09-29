package com.cuadra.caja.domain

import java.util.UUID

/**
 * Ids derivados de una compra. El teléfono crea sus filas locales con los MISMOS ids que luego crea el servidor (`PurchaseService.derived`),
 * así lo que baja reemplaza a lo local sin duplicarse. Un cambio aquí sin cambiar el servidor duplicaría existencias y pagos.
 */
object PurchaseIds {
    private fun derived(tag: String, a: String, b: String?) = UUID.nameUUIDFromBytes("$tag:$a:${b ?: ""}".toByteArray(Charsets.UTF_8)).toString()

    fun movement(purchaseId: String, lineId: String) = derived("purchase-move", purchaseId, lineId)
    fun reversal(purchaseId: String, lineId: String) = derived("purchase-reversal", purchaseId, lineId)
    fun firstPayment(purchaseId: String) = derived("purchase-pay", purchaseId, null)
}

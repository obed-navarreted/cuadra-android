package com.cuadra.caja.domain

/** Una venta lista para mostrar (lista y detalle), venga del servidor (dueño/admin en línea) o del propio teléfono (cajero, sin conexión). */
data class SaleLineView(
    val name: String, val variant: String?, val quantityMilli: Long, val unitPriceMinor: Long, val discountMinor: Long, val lineTotalMinor: Long,
    /** Id de la línea (para devolverla) y cuánto ya se devolvió de ella. */
    val id: String = "", val returnedMilli: Long = 0,
)

/** Una devolución de la venta, lista para mostrar. `pending`: hecha en este teléfono, aún sin confirmar por el servidor. */
data class SaleReturnView(
    val id: String, val reason: String, val refundMethod: String, val totalMinor: Long, val by: String?, val atMillis: Long,
    val lines: List<ReturnLineView>, val refunds: List<Pair<String, Long>>, val pending: Boolean = false,
)

data class ReturnLineView(val saleItemId: String, val name: String, val quantityMilli: Long, val amountMinor: Long)

/** Una promoción que aplicó la venta: «Promo 3 por C$ 100: −C$ 70». */
data class SalePromotionView(val name: String, val quantity: Int, val priceMinor: Long, val units: Long, val discountMinor: Long)

data class SalePaymentView(
    val method: String, val otherLabel: String?, val amountMinor: Long, val tenderedMinor: Long?, val changeMinor: Long?, val reference: String?, val debtorLabel: String?,
)

data class SaleView(
    val id: String, val status: String, val label: String?, val subtotalMinor: Long, val discountMinor: Long, val totalMinor: Long,
    /** Cuándo se cobró (o se creó, si nunca se cobró). */
    val atMillis: Long, val soldBy: String?, val editedBy: String?, val editedAtMillis: Long?,
    val cancelledBy: String?, val cancelledAtMillis: Long?, val cancelReason: String?,
    val items: List<SaleLineView>, val payments: List<SalePaymentView>, val unsynced: Boolean = false,
    /** Etiquetas para revisar: guardada aparte por conflicto, llegó después de la baja de quien la hizo, hora corregida. */
    val conflict: Boolean = false, val reviewFlag: String? = null,
    /** Lo devuelto (suma) y cada devolución. */
    val returnedMinor: Long = 0, val returns: List<SaleReturnView> = emptyList(),
    /** Quién la cobró (id) y cuándo exactamente (para «Anular esta venta» y la regla de devolución del cajero). */
    val completedById: String? = null, val completedAtMillis: Long? = null,
    /** Quién la tomó (atendió). Con cobro en caja puede ser otra persona que quien la cobró (`soldBy`): «Atendió: Kevin · Cobró: Ana». */
    val takenBy: String? = null,
    /** Cobro en caja: quién la envió a caja y cuándo (si pasó por la caja). */
    val sentBy: String? = null, val sentAtMillis: Long? = null,
    /** Promociones por cantidad aplicadas (su descuento ya está en las líneas). */
    val promotions: List<SalePromotionView> = emptyList(),
) {
    /** «Descuentos por promociones» de esta venta. */
    val promotionDiscountMinor: Long get() = promotions.sumOf { it.discountMinor }

    val cancelled: Boolean get() = status == "CANCELLED"

    /** Las líneas con lo ya devuelto, para calcular una devolución (monto devuelto por línea sale de las devoluciones). */
    fun returnable(): List<ReturnableLine> {
        val amounts = HashMap<String, Long>()
        returns.forEach { r -> r.lines.forEach { l -> amounts[l.saleItemId] = (amounts[l.saleItemId] ?: 0) + l.amountMinor } }
        return items.filter { it.id.isNotEmpty() }.map { ReturnableLine(it.id, it.name, it.quantityMilli, it.lineTotalMinor, it.returnedMilli, amounts[it.id] ?: 0) }
    }
}

/** Lo que se pide al servidor para la lista de ventas del dueño/admin. `statuses` vacío = cobradas. */
data class SaleQuery(
    val statuses: Set<String> = setOf("COMPLETED"), val method: String? = null, val memberId: String? = null,
) {
    /** Alterna un estado sin dejar la lista sin ninguno (al menos uno siempre queda). */
    fun toggle(status: String): SaleQuery {
        val next = if (status in statuses) statuses - status else statuses + status
        return if (next.isEmpty()) this else copy(statuses = next)
    }
}

object SaleLists {
    /** Junta listas de varios estados (cobradas + eliminadas) de la más reciente a la más antigua, sin repetir. */
    fun merge(vararg lists: List<SaleView>): List<SaleView> = lists.flatMap { it }.distinctBy { it.id }.sortedWith(compareByDescending<SaleView> { it.atMillis }.thenBy { it.id })
}

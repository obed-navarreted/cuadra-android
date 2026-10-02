package com.cuadra.caja.data.repo

import com.cuadra.caja.data.local.CreditEntity
import com.cuadra.caja.data.local.Db
import com.cuadra.caja.data.local.OutboxEntity
import com.cuadra.caja.data.local.SaleEntity
import com.cuadra.caja.data.local.SaleItemEntity
import com.cuadra.caja.data.local.SalePaymentEntity
import com.cuadra.caja.data.remote.ApiFailure
import com.cuadra.caja.data.remote.CancelBody
import com.cuadra.caja.data.remote.CuadraApi
import com.cuadra.caja.data.remote.SaleInputDto
import com.cuadra.caja.data.remote.SaleItemInputDto
import com.cuadra.caja.data.remote.SalePaymentInputDto
import com.cuadra.caja.data.remote.apiCall
import com.cuadra.caja.data.session.SessionStore
import com.cuadra.caja.data.sync.calendar
import com.cuadra.caja.data.sync.toView
import com.cuadra.caja.domain.Cart
import com.cuadra.caja.domain.CartLine
import com.cuadra.caja.domain.PayMethod
import com.cuadra.caja.domain.PaymentPlan
import com.cuadra.caja.domain.PhoneNumbers
import com.cuadra.caja.domain.PhoneResult
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Una cuenta apartada lista para retomar. `pendingCheckout`: estaba «Por cobrar en caja» (ADR 0015). */
data class ResumedSale(val saleId: String, val cart: Cart, val label: String?, val pendingCheckout: Boolean = false)

sealed interface ResumeResult {
    data class Ok(val sale: ResumedSale) : ResumeResult

    /** Otro teléfono la tiene abierta en este momento (`by`: quién, si el servidor lo dice: «La está cobrando Ana»). */
    data class Locked(val by: String? = null) : ResumeResult
    data object NotFound : ResumeResult
}

/**
 * Toda operación de venta se guarda en el teléfono y en la cola de salida en UNA transacción: cobrar nunca espera a la red
 * y nunca deja un cambio local sin la operación que lo enviará (PLAN.md 14.1).
 */
class SaleRepository(
    private val db: Db,
    private val api: CuadraApi,
    private val session: SessionStore,
    private val requestSync: () -> Unit,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val json = Json { encodeDefaults = true; explicitNulls = false }

    fun parked(): Flow<List<SaleEntity>> = db.sales().parked()

    /** Cuántas líneas tiene cada cuenta apartada (id → líneas). */
    fun parkedLineCounts(): Flow<Map<String, Int>> = db.sales().parkedLineCounts().map { rows -> rows.associate { it.saleId to it.lines } }

    /** Ids de los productos más vendidos desde `since` (del más al menos vendido), para completar los frecuentes de la caja. */
    fun bestSellers(since: Long, limit: Int = 24): Flow<List<String>> = db.sales().bestSellers(since, limit).map { rows -> rows.map { it.productId } }
    fun recent(limit: Int = 100): Flow<List<SaleEntity>> = db.sales().recent(limit)
    fun dayTotals(from: Long, to: Long) = db.sales().dayTotals(from, to)

    /** Lo cobrado por una persona en el rango («Vendido hoy» de un cajero). */
    fun dayTotalsBy(from: Long, to: Long, memberId: String) = db.sales().dayTotalsBy(from, to, memberId)
    fun dayByMethod(from: Long, to: Long) = db.sales().dayByMethod(from, to)

    suspend fun detail(saleId: String): Triple<SaleEntity, List<SaleItemEntity>, List<SalePaymentEntity>>? {
        val sale = db.sales().get(saleId) ?: return null
        return Triple(sale, db.sales().items(saleId), db.sales().payments(saleId))
    }

    /** Una venta de este teléfono lista para mostrar (detalle), con sus devoluciones. */
    suspend fun view(saleId: String): com.cuadra.caja.domain.SaleView? = detail(saleId)?.let { (s, i, p) -> s.toView(i, p, db.sales().returnsFor(saleId), db.sales().promotions(saleId)) }

    /** Las promociones que aplicó una venta de este teléfono. */
    suspend fun promotionsOf(saleId: String): List<com.cuadra.caja.data.local.SalePromotionEntity> = db.sales().promotions(saleId)

    /** La última venta cobrada por esa persona en este teléfono (solo esa se puede anular en los primeros minutos). */
    suspend fun lastCompletedBy(memberId: String?): SaleEntity? = memberId?.let { db.sales().lastCompletedBy(it) }

    /** Lo devuelto en una jornada según este teléfono (el Resumen lo resta del día en que se devolvió). */
    fun returnedBetween(from: Long, to: Long) = db.sales().returnedBetween(from, to)

    /**
     * Devuelve productos de una venta cobrada (docs/adr/0013). Se guarda en el teléfono (lo devuelto por línea se ve al instante) y va por la cola como
     * SALE_RETURN con un id propio: sin conexión también funciona y reenviarla no la duplica. El servidor recalcula el monto y el reembolso (y manda);
     * la hora es la de AHORA: la devolución cuenta en la jornada de hoy, no en la de la venta. Devuelve la devolución guardada.
     */
    suspend fun returnItems(given: com.cuadra.caja.domain.SaleView, milli: Map<String, Long>, reason: String, method: com.cuadra.caja.domain.RefundMethod,
                            returnId: String = UUID.randomUUID().toString()): com.cuadra.caja.domain.SaleReturnView? {
        // Si la venta está en el teléfono, se usa esa (con sus devoluciones pendientes); si no (lista del servidor), la que se ve en pantalla.
        val saleId = given.id
        val sale = view(saleId)?.takeIf { it.items.isNotEmpty() } ?: given
        if (db.sales().returnsFor(saleId).any { it.id == returnId }) return db.sales().returnsFor(saleId).first { it.id == returnId }.let { SaleReturns.view(it) }
        val lines = sale.returnable()
        val chosen = milli.filterValues { it > 0 }
        require(chosen.isNotEmpty() && !com.cuadra.caja.domain.SaleReturnMath.exceeds(lines, chosen)) { "Cantidades inválidas" }
        require(com.cuadra.caja.domain.SaleReturnMath.reasonOk(reason)) { "Falta el motivo" }
        val amounts = com.cuadra.caja.domain.SaleReturnMath.amounts(lines, sale.subtotalMinor, sale.discountMinor, chosen)
        val total = com.cuadra.caja.domain.SaleReturnMath.total(lines, sale.subtotalMinor, sale.discountMinor, sale.totalMinor, sale.returnedMinor, chosen)
        val time = now()
        val who = session.current().memberName
        val items = lines.filter { (chosen[it.id] ?: 0) > 0 }.map { com.cuadra.caja.data.remote.ReturnItemDto(saleItemId = it.id, name = it.name, quantityMilli = chosen.getValue(it.id), amountMinor = amounts[it.id] ?: 0) }
        // Lo que se muestra mientras no llega el servidor: todo por el medio elegido (el servidor reparte "por el mismo medio" y el fiado exacto).
        val refunds = if (total > 0) listOf(com.cuadra.caja.data.remote.RefundDto(if (method == com.cuadra.caja.domain.RefundMethod.CREDIT_NOTE) "CREDIT" else "CASH", total)) else emptyList()
        val entity = com.cuadra.caja.data.local.SaleReturnEntity(returnId, saleId, reason.trim(), method.name, total, who, time, SaleReturns.encodeItems(items), SaleReturns.encodeRefunds(refunds), 0)
        val input = com.cuadra.caja.data.remote.ReturnInputDto(saleId, items.map { com.cuadra.caja.data.remote.ReturnLineInputDto(it.saleItemId, it.quantityMilli) }, reason.trim(), method.name,
            Instant.ofEpochMilli(time).toString())
        db.inTransaction {
            db.sales().upsertReturn(entity)
            SaleReturns.recomputeReturned(db.sales(), saleId)
            db.outbox().insert(OutboxEntity(opId = UUID.randomUUID().toString(), kind = "SALE_RETURN", entityId = returnId, payload = json.encodeToString(input), createdAt = time))
        }
        requestSync()
        return SaleReturns.view(entity)
    }

    /**
     * Cobra: el recibo queda COMPLETED en el teléfono y se encola su envío. Devuelve el id de la venta. `cart` es el recibo YA con las promociones aplicadas
     * (`PricedCart.cart`: el descuento de cada promoción va en sus líneas) y `promotions`, lo que se aplicó (se guarda tal cual: el servidor no recalcula).
     */
    suspend fun complete(cart: Cart, plan: PaymentPlan, saleId: String = UUID.randomUUID().toString(), label: String? = null,
                         promotions: List<com.cuadra.caja.domain.AppliedPromotion> = emptyList()): String {
        require(!cart.isEmpty) { "No hay nada que cobrar" }
        require(plan.totalMinor == cart.totalMinor && plan.isValid) { "El pago no cubre la venta" }
        return save(saleId, "COMPLETED", cart, plan, label, promotions = promotions)
    }

    /** Aparta la cuenta (nueva o una retomada): queda visible para todos los teléfonos del negocio. Si estaba por cobrar en caja, sigue así. */
    suspend fun park(cart: Cart, label: String?, saleId: String = UUID.randomUUID().toString(), promotions: List<com.cuadra.caja.domain.AppliedPromotion> = emptyList()): String {
        require(!cart.isEmpty) { "No hay nada que apartar" }
        return save(saleId, "PARKED", cart, null, label, promotions = promotions)
    }

    /**
     * «Enviar a caja» (ADR 0015): la cuenta queda apartada y en la lista «Por cobrar en caja» de todos los teléfonos, con su nota (`label`). Sin método de
     * pago ni vuelto: los decide quien cobra. Va por la cola de salida como cualquier cuenta apartada, así funciona sin conexión.
     */
    suspend fun sendToRegister(cart: Cart, note: String?, saleId: String = UUID.randomUUID().toString(),
                               promotions: List<com.cuadra.caja.domain.AppliedPromotion> = emptyList()): String {
        require(!cart.isEmpty) { "No hay nada que enviar" }
        return save(saleId, "PARKED", cart, null, note, sendToRegister = true, promotions = promotions)
    }

    /**
     * Una cuenta por cobrar en caja que se había retomado vuelve a la lista sin cambios (p. ej. «Vaciar» en la caja: anularla pide motivo y se hace desde su
     * detalle). Suelta la reserva en el servidor si hay conexión.
     */
    suspend fun releaseToQueue(saleId: String) {
        val sale = db.sales().get(saleId) ?: return
        if (sale.status == "OPEN") db.sales().upsert(sale.copy(status = "PARKED"))
        session.current().businessId?.let { b -> apiCall { api.unlockSale(b, saleId) } }
    }

    /**
     * Quién tiene abierta (para cobrar o agregar productos) cada cuenta por cobrar en caja en OTRO teléfono: id de la cuenta → nombre. Lo abierto en este
     * teléfono no cuenta. Sin conexión, `null` (la lista sigue como estaba).
     */
    suspend fun registerQueueLocks(): Map<String, String>? {
        val s = session.current()
        val businessId = s.businessId ?: return null
        val rows = apiCall { api.registerQueue(businessId) }.getOrNull() ?: return null
        return rows.mapNotNull { r -> r.lockedBy?.takeIf { r.lockedByDeviceId != s.deviceId }?.let { r.id to it.name } }.toMap()
    }

    private suspend fun save(saleId: String, status: String, cart: Cart, plan: PaymentPlan?, label: String?, sendToRegister: Boolean? = null,
                             promotions: List<com.cuadra.caja.domain.AppliedPromotion> = emptyList()): String {
        val promoRows = promotions.map { com.cuadra.caja.data.remote.SalePromotionDto(it.promotionId.ifEmpty { null }, it.name.take(200), it.quantity, it.priceMinor, it.units, it.discountMinor) }
        val s = session.current()
        val register = db.directory().defaultRegister()?.id
        val time = now()
        val existing = db.sales().get(saleId)
        val createdAt = existing?.createdAt ?: time
        val completedAt = if (status == "COMPLETED") time else null
        val cleanLabel = label?.trim()?.ifEmpty { null }

        val country = db.directory().businessNow()?.country
        val payments = plan?.effective.orEmpty().map {
            // El teléfono del deudor se guarda en formato internacional (el que pide wa.me); si no es válido no se guarda uno a medias.
            val phone = (PhoneNumbers.normalize(it.debtorPhone, country) as? PhoneResult.Valid)?.digits
            PaymentRow(UUID.randomUUID().toString(), it.method, it.otherLabel, it.amountMinor, it.tenderedMinor, it.reference, it.debtorLabel, phone, it.customerId)
        }
        val input = SaleInputDto(
            status = status, label = cleanLabel, cashRegisterId = register, discountMinor = cart.discountMinor.coerceAtMost(cart.subtotalMinor),
            createdAt = Instant.ofEpochMilli(createdAt).toString(), completedAt = completedAt?.let { Instant.ofEpochMilli(it).toString() },
            items = cart.lines.map { it.toInput() },
            payments = payments.map { SalePaymentInputDto(it.id, it.method.name, it.otherLabel, it.amount, it.tendered, it.reference, it.debtorLabel, it.debtorPhone, it.customerId) },
            // Se cobra una cuenta que estaba apartada (retomada = OPEN en el teléfono): si otro teléfono ya la cobró o la descartó, el servidor guarda esta
            // aparte para revisar en vez de perderla.
            fromStatus = if (status == "COMPLETED" && existing != null && (existing.status == "OPEN" || existing.status == "PARKED") && existing.rev > 0) "PARKED" else null,
            sendToRegister = sendToRegister?.takeIf { status == "PARKED" },
            promotions = promoRows,
        )
        // Cobro en caja: enviada ahora = hora y quién; sin indicarlo se conserva lo que tenía (al cobrarla queda quién la envió). Reenviada con productos agregados
        // conserva cuándo llegó (igual que el servidor): no pierde su lugar en la lista.
        val sentAt = if (sendToRegister == true && status == "PARKED") existing?.sentToRegisterAt ?: time else if (sendToRegister == false) null else existing?.sentToRegisterAt
        val sentBy = if (sendToRegister == true && status == "PARKED") s.memberName else if (sendToRegister == false) null else existing?.sentByName
        val discount = input.discountMinor
        db.inTransaction {
            db.sales().upsert(
                SaleEntity(
                    id = saleId, status = status, label = cleanLabel, cashRegisterId = register, subtotalMinor = cart.subtotalMinor, discountMinor = discount,
                    totalMinor = cart.subtotalMinor - discount, createdByMemberId = existing?.createdByMemberId ?: s.memberId,
                    completedByMemberId = if (status == "COMPLETED") s.memberId else null,
                    createdByName = existing?.createdByName ?: s.memberName, completedByName = if (status == "COMPLETED") s.memberName else null,
                    completedAt = completedAt, editedByName = null, cancelledByName = null, cancelReason = null, lockedByDeviceId = null,
                    createdAt = createdAt, updatedAt = time, rev = existing?.rev ?: 0, sentToRegisterAt = sentAt, sentByName = sentBy,
                ),
            )
            db.sales().deleteItems(saleId)
            db.sales().insertItems(cart.lines.mapIndexed { i, l ->
                SaleItemEntity(saleId, l.id, l.productId, l.barcode, l.name, l.variant, l.unitPriceMinor, l.unitCostMinor, l.quantityMilli, l.discountMinor, i)
            })
            db.sales().deletePromotions(saleId)
            db.sales().insertPromotions(promoRows.mapIndexed { i, p -> com.cuadra.caja.data.local.SalePromotionEntity(saleId, i, p.promotionId, p.name, p.quantity, p.priceMinor, p.units, p.discountMinor) })
            db.sales().deletePayments(saleId)
            db.sales().insertPayments(payments.mapIndexed { i, p ->
                SalePaymentEntity(saleId, p.id, p.method.name, p.otherLabel, p.amount, p.tendered?.takeIf { p.method == PayMethod.CASH },
                    if (p.method == PayMethod.CASH) (p.tendered ?: p.amount) - p.amount else null, p.reference, i, p.debtorLabel, p.debtorPhone, p.customerId)
            })
            // Cada pago a fiado de una venta cobrada origina su fiado (mismo id que calcula el servidor); así aparece en la libreta al instante.
            if (status == "COMPLETED") {
                val touched = mutableListOf<String>()
                // Vencimiento por defecto del negocio (Ajustes del negocio): la jornada de la venta más los días configurados; el servidor calcula lo mismo.
                val biz = db.directory().businessNow()
                val due = biz?.let { com.cuadra.caja.domain.CreditRules.dueDate(it.creditDefaultDueDays, it.calendar().dateOf(completedAt ?: time))?.toString() }
                for (p in payments.filter { it.method == PayMethod.CREDIT }) {
                    val customer = p.customerId?.let { db.customers().get(it) }
                    val label = p.debtorLabel ?: customer?.name ?: continue
                    db.credits().upsert(CreditEntity(CreditRepository.saleCreditId(saleId, p.id), saleId, p.customerId, label, p.debtorPhone ?: customer?.phone, p.amount, p.amount, "OPEN",
                        due, null, completedAt ?: time, s.memberName, null, 0))
                    p.customerId?.let { touched += it }
                }
                if (touched.isNotEmpty()) db.customers().recompute(touched.distinct())
            }
            db.outbox().insert(OutboxEntity(opId = UUID.randomUUID().toString(), kind = "SALE_UPSERT", entityId = saleId, payload = json.encodeToString(input), createdAt = time))
        }
        requestSync()
        return saleId
    }

    /**
     * Retoma una cuenta apartada. Intenta bloquearla en el servidor para que otro teléfono no la abra a la vez; sin red se retoma igual
     * (el bloqueo es suave y el servidor decide si hay choque al guardar). Localmente pasa a OPEN, que nunca se sincroniza.
     */
    suspend fun resume(saleId: String): ResumeResult {
        val sale = db.sales().get(saleId) ?: return ResumeResult.NotFound
        val businessId = session.current().businessId
        if (businessId != null) {
            val lock = apiCall { api.lockSale(businessId, saleId) }.exceptionOrNull()
            if (lock is ApiFailure.Http && lock.code == "SALE_LOCKED") return ResumeResult.Locked(lock.memberName)
        }
        val cart = Cart(
            // El descuento de las líneas sale de las promociones: la caja lo vuelve a calcular con las de hoy.
            lines = db.sales().items(saleId).map { CartLine(it.id, it.productId, it.barcode, it.name, it.variant, it.unitPriceMinor, it.unitCostMinor, it.quantityMilli) },
            discountMinor = sale.discountMinor,
        )
        db.sales().upsert(sale.copy(status = "OPEN"))
        return ResumeResult.Ok(ResumedSale(saleId, cart, sale.label, pendingCheckout = sale.sentToRegisterAt != null))
    }

    /**
     * Descarta una cuenta apartada o ELIMINA una cobrada (el servidor decide si el rol puede y exige el motivo: la venta se conserva anulada con quién,
     * cuándo y por qué). Va por la cola de salida, así funciona sin conexión; si la venta no está en el teléfono igual se encola.
     */
    suspend fun cancel(saleId: String, reason: String?) {
        val sale = db.sales().get(saleId)
        val who = session.current().memberName
        val time = now()
        val clean = reason?.trim()?.ifEmpty { null }
        db.inTransaction {
            if (sale != null) db.sales().upsert(sale.copy(status = "CANCELLED", cancelReason = clean, cancelledByName = who, cancelledAt = time, updatedAt = time))
            db.outbox().insert(OutboxEntity(opId = UUID.randomUUID().toString(), kind = "SALE_CANCEL", entityId = saleId,
                payload = json.encodeToString(CancelBody(clean)), createdAt = time))
        }
        requestSync()
    }

    /** Al abrir la app: una cuenta que quedó abierta (cierre del proceso) se recupera como apartada, nunca se pierde. */
    suspend fun recoverOpenAsParked() {
        for (open in db.sales().openSales()) {
            val items = db.sales().items(open.id)
            if (items.isEmpty()) {
                db.sales().upsert(open.copy(status = "CANCELLED", cancelReason = "empty"))
                continue
            }
            // Se aparta tal como estaba (con sus promociones y el descuento ya repartido en las líneas).
            val cart = Cart(items.map { CartLine(it.id, it.productId, it.barcode, it.name, it.variant, it.unitPriceMinor, it.unitCostMinor, it.quantityMilli, it.discountMinor) }, open.discountMinor)
            val promos = db.sales().promotions(open.id).map { com.cuadra.caja.domain.AppliedPromotion(it.promotionId.orEmpty(), it.name, it.quantity, it.priceMinor, it.units / it.quantity.coerceAtLeast(1), it.discountMinor, emptyList()) }
            park(cart, open.label ?: "•", open.id, promos)
        }
    }

    private data class PaymentRow(
        val id: String, val method: PayMethod, val otherLabel: String?, val amount: Long, val tendered: Long?, val reference: String?,
        val debtorLabel: String?, val debtorPhone: String?, val customerId: String?,
    )

    private fun CartLine.toInput() = SaleItemInputDto(id, productId, barcode, name, variant, unitPriceMinor, unitCostMinor, quantityMilli, discountMinor)
}

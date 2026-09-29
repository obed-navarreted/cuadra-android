package com.cuadra.caja.data.repo

import androidx.room.withTransaction
import com.cuadra.caja.data.local.CreditEntity
import com.cuadra.caja.data.local.CuadraDatabase
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
import com.cuadra.caja.domain.Cart
import com.cuadra.caja.domain.CartLine
import com.cuadra.caja.domain.PayMethod
import com.cuadra.caja.domain.PaymentPlan
import com.cuadra.caja.domain.PhoneNumbers
import com.cuadra.caja.domain.PhoneResult
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Una cuenta apartada lista para retomar. */
data class ResumedSale(val saleId: String, val cart: Cart, val label: String?)

sealed interface ResumeResult {
    data class Ok(val sale: ResumedSale) : ResumeResult

    /** Otro teléfono la tiene abierta en este momento. */
    data object Locked : ResumeResult
    data object NotFound : ResumeResult
}

/**
 * Toda operación de venta se guarda en el teléfono y en la cola de salida en UNA transacción: cobrar nunca espera a la red
 * y nunca deja un cambio local sin la operación que lo enviará (PLAN.md 14.1).
 */
class SaleRepository(
    private val db: CuadraDatabase,
    private val api: CuadraApi,
    private val session: SessionStore,
    private val requestSync: () -> Unit,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val json = Json { encodeDefaults = true; explicitNulls = false }

    fun parked(): Flow<List<SaleEntity>> = db.sales().parked()
    fun recent(limit: Int = 100): Flow<List<SaleEntity>> = db.sales().recent(limit)
    fun dayTotals(from: Long, to: Long) = db.sales().dayTotals(from, to)
    fun dayByMethod(from: Long, to: Long) = db.sales().dayByMethod(from, to)

    suspend fun detail(saleId: String): Triple<SaleEntity, List<SaleItemEntity>, List<SalePaymentEntity>>? {
        val sale = db.sales().get(saleId) ?: return null
        return Triple(sale, db.sales().items(saleId), db.sales().payments(saleId))
    }

    /** Cobra: el recibo queda COMPLETED en el teléfono y se encola su envío. Devuelve el id de la venta. */
    suspend fun complete(cart: Cart, plan: PaymentPlan, saleId: String = UUID.randomUUID().toString(), label: String? = null): String {
        require(!cart.isEmpty) { "No hay nada que cobrar" }
        require(plan.totalMinor == cart.totalMinor && plan.isValid) { "El pago no cubre la venta" }
        return save(saleId, "COMPLETED", cart, plan, label)
    }

    /** Aparta la cuenta (nueva o una retomada): queda visible para todos los teléfonos del negocio. */
    suspend fun park(cart: Cart, label: String?, saleId: String = UUID.randomUUID().toString()): String {
        require(!cart.isEmpty) { "No hay nada que apartar" }
        return save(saleId, "PARKED", cart, null, label)
    }

    private suspend fun save(saleId: String, status: String, cart: Cart, plan: PaymentPlan?, label: String?): String {
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
        )
        val discount = input.discountMinor
        db.withTransaction {
            db.sales().upsert(
                SaleEntity(
                    id = saleId, status = status, label = cleanLabel, cashRegisterId = register, subtotalMinor = cart.subtotalMinor, discountMinor = discount,
                    totalMinor = cart.subtotalMinor - discount, createdByMemberId = existing?.createdByMemberId ?: s.memberId,
                    createdByName = existing?.createdByName ?: s.memberName, completedByName = if (status == "COMPLETED") s.memberName else null,
                    completedAt = completedAt, editedByName = null, cancelledByName = null, cancelReason = null, lockedByDeviceId = null,
                    createdAt = createdAt, updatedAt = time, rev = existing?.rev ?: 0,
                ),
            )
            db.sales().deleteItems(saleId)
            db.sales().insertItems(cart.lines.mapIndexed { i, l ->
                SaleItemEntity(saleId, l.id, l.productId, l.barcode, l.name, l.variant, l.unitPriceMinor, l.unitCostMinor, l.quantityMilli, l.discountMinor, i)
            })
            db.sales().deletePayments(saleId)
            db.sales().insertPayments(payments.mapIndexed { i, p ->
                SalePaymentEntity(saleId, p.id, p.method.name, p.otherLabel, p.amount, p.tendered?.takeIf { p.method == PayMethod.CASH },
                    if (p.method == PayMethod.CASH) (p.tendered ?: p.amount) - p.amount else null, p.reference, i, p.debtorLabel, p.debtorPhone, p.customerId)
            })
            // Cada pago a fiado de una venta cobrada origina su fiado (mismo id que calcula el servidor); así aparece en la libreta al instante.
            if (status == "COMPLETED") {
                val touched = mutableListOf<String>()
                for (p in payments.filter { it.method == PayMethod.CREDIT }) {
                    val customer = p.customerId?.let { db.customers().get(it) }
                    val label = p.debtorLabel ?: customer?.name ?: continue
                    db.credits().upsert(CreditEntity(CreditRepository.saleCreditId(saleId, p.id), saleId, p.customerId, label, p.debtorPhone ?: customer?.phone, p.amount, p.amount, "OPEN",
                        null, null, completedAt ?: time, s.memberName, null, 0))
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
            if (lock is ApiFailure.Http && lock.code == "SALE_LOCKED") return ResumeResult.Locked
        }
        val cart = Cart(
            lines = db.sales().items(saleId).map { CartLine(it.id, it.productId, it.barcode, it.name, it.variant, it.unitPriceMinor, it.unitCostMinor, it.quantityMilli, it.discountMinor) },
            discountMinor = sale.discountMinor,
        )
        db.sales().upsert(sale.copy(status = "OPEN"))
        return ResumeResult.Ok(ResumedSale(saleId, cart, sale.label))
    }

    /** Descarta una cuenta apartada (o eliminar una cobrada, si el rol lo permite: el servidor decide). */
    suspend fun cancel(saleId: String, reason: String?) {
        val sale = db.sales().get(saleId) ?: return
        val who = session.current().memberName
        db.withTransaction {
            db.sales().upsert(sale.copy(status = "CANCELLED", cancelReason = reason, cancelledByName = who, updatedAt = now()))
            db.outbox().insert(OutboxEntity(opId = UUID.randomUUID().toString(), kind = "SALE_CANCEL", entityId = saleId,
                payload = json.encodeToString(CancelBody(reason)), createdAt = now()))
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
            val cart = Cart(items.map { CartLine(it.id, it.productId, it.barcode, it.name, it.variant, it.unitPriceMinor, it.unitCostMinor, it.quantityMilli, it.discountMinor) }, open.discountMinor)
            park(cart, open.label ?: "•", open.id)
        }
    }

    private data class PaymentRow(
        val id: String, val method: PayMethod, val otherLabel: String?, val amount: Long, val tendered: Long?, val reference: String?,
        val debtorLabel: String?, val debtorPhone: String?, val customerId: String?,
    )

    private fun CartLine.toInput() = SaleItemInputDto(id, productId, barcode, name, variant, unitPriceMinor, unitCostMinor, quantityMilli, discountMinor)
}

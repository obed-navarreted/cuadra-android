package com.cuadra.caja.data.repo

import androidx.room.withTransaction
import com.cuadra.caja.data.local.CuadraDatabase
import com.cuadra.caja.data.local.ExpenseEntity
import com.cuadra.caja.data.local.OutboxEntity
import com.cuadra.caja.data.local.PurchaseEntity
import com.cuadra.caja.data.local.PurchaseItemEntity
import com.cuadra.caja.data.local.PurchaseRow
import com.cuadra.caja.data.local.StockMovementEntity
import com.cuadra.caja.data.local.SupplierBalance
import com.cuadra.caja.data.local.SupplierEntity
import com.cuadra.caja.data.local.SupplierPaymentEntity
import com.cuadra.caja.data.remote.PurchaseInputDto
import com.cuadra.caja.data.remote.PurchaseLineInputDto
import com.cuadra.caja.data.remote.ReasonBody
import com.cuadra.caja.data.remote.SupplierInputDto
import com.cuadra.caja.data.remote.SupplierPaymentInputDto
import com.cuadra.caja.data.session.SessionStore
import com.cuadra.caja.domain.PhoneNumbers
import com.cuadra.caja.domain.PhoneResult
import com.cuadra.caja.domain.PurchaseIds
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Una línea de la compra en pantalla: un producto del catálogo o solo un nombre escrito. */
data class PurchaseLine(val id: String, val productId: String?, val name: String, val quantityMilli: Long, val unitCostMinor: Long) {
    val totalMinor: Long get() = com.cuadra.caja.domain.SaleMath.lineTotal(unitCostMinor, quantityMilli)
}

/**
 * Compras a proveedores y cuentas por pagar. Todo se guarda en el teléfono y en la cola de salida en una sola transacción: la compra, sus líneas,
 * las existencias que suma, lo pagado y su gasto (que cuenta en el cierre si salió del cajón). El proveedor es opcional: basta un nombre.
 * Una compra no se edita: se anula y se registra de nuevo.
 */
class PurchaseRepository(
    private val db: CuadraDatabase,
    private val session: SessionStore,
    private val requestSync: () -> Unit,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val json = Json { encodeDefaults = true; explicitNulls = false }

    fun suppliers(): Flow<List<SupplierEntity>> = db.inventory().suppliers()
    fun balances(): Flow<List<SupplierBalance>> = db.inventory().supplierBalances()
    fun purchases(onlyOwed: Boolean, supplierId: String?): Flow<List<PurchaseRow>> = db.inventory().purchases(if (onlyOwed) 1 else 0, supplierId)
    fun items(purchaseId: String) = db.inventory().itemsFlow(purchaseId)
    fun payments(purchaseId: String) = db.inventory().payments(purchaseId)
    fun totalOwed(): Flow<Long> = db.inventory().totalOwed()

    suspend fun saveSupplier(id: String?, name: String, phone: String?, notes: String?): SupplierEntity? {
        val clean = name.trim()
        if (clean.isEmpty()) return null
        val supplierId = id ?: UUID.randomUUID().toString()
        val country = db.directory().businessNow()?.country
        val normalized = when (val r = PhoneNumbers.normalize(phone, country)) {
            PhoneResult.None -> null
            is PhoneResult.Valid -> r.digits
            PhoneResult.Invalid -> return null
        }
        val entity = SupplierEntity(supplierId, clean, normalized, notes?.trim()?.ifEmpty { null }, true, db.inventory().supplier(supplierId)?.rev ?: 0)
        db.withTransaction {
            db.inventory().upsertSupplier(entity)
            db.outbox().insert(OutboxEntity(opId = UUID.randomUUID().toString(), kind = "SUPPLIER_UPSERT", entityId = supplierId,
                payload = json.encodeToString(SupplierInputDto(clean, normalized, entity.notes, true)), createdAt = now()))
        }
        requestSync()
        return entity
    }

    /** Registra una compra. `paidMinor` = lo pagado al comprar (0: todo queda por pagar), de `paidSource`. */
    suspend fun register(supplierId: String?, supplierName: String?, lines: List<PurchaseLine>, paidMinor: Long, paidSource: String?, note: String?): PurchaseEntity? {
        if (lines.isEmpty()) return null
        val total = lines.sumOf { it.totalMinor }
        if (paidMinor < 0 || paidMinor > total || (paidMinor > 0 && paidSource == null)) return null
        val s = session.current()
        val id = UUID.randomUUID().toString()
        val time = now()
        val supplier = supplierId?.let { db.inventory().supplier(it) }
        val label = supplier?.name ?: supplierName?.trim()?.ifEmpty { null }
        val cleanNote = note?.trim()?.ifEmpty { null }
        val purchase = PurchaseEntity(id, supplierId, label, total, cleanNote, s.memberName, time, false, null, 0)
        val input = PurchaseInputDto(
            supplierId = supplierId, supplierName = if (supplierId == null) label else null,
            lines = lines.map { PurchaseLineInputDto(it.id, it.productId, it.name, it.quantityMilli, it.unitCostMinor) },
            paidMinor = paidMinor.takeIf { it > 0 }, paidSource = paidSource.takeIf { paidMinor > 0 }, note = cleanNote, occurredAt = Instant.ofEpochMilli(time).toString(),
        )
        db.withTransaction {
            db.inventory().upsertPurchase(purchase)
            db.inventory().insertItems(lines.mapIndexed { i, l -> PurchaseItemEntity(id, l.id, l.productId, l.name, l.quantityMilli, l.unitCostMinor, l.totalMinor, i) })
            for (l in lines) {
                val p = l.productId?.let { db.products().get(it) } ?: continue
                // Último costo de compra: sirve para la ganancia aunque el producto no lleve inventario.
                db.products().upsert(p.copy(costMinor = l.unitCostMinor))
                if (p.trackStock) db.inventory().upsertMovement(StockMovementEntity(PurchaseIds.movement(id, l.id), p.id, "PURCHASE", l.quantityMilli, l.unitCostMinor, "PURCHASE", id, null, s.memberName, s.memberId, time, 0))
            }
            if (paidMinor > 0) recordPayment(PurchaseIds.firstPayment(id), id, supplierId, label, paidMinor, paidSource!!, null, time)
            db.outbox().insert(OutboxEntity(opId = UUID.randomUUID().toString(), kind = "PURCHASE_REGISTER", entityId = id, payload = json.encodeToString(input), createdAt = time))
        }
        requestSync()
        return purchase
    }

    /** Un pago a una compra concreta. Crea su gasto con el mismo id; si salió del cajón, el cierre de caja lo descuenta. */
    suspend fun pay(purchaseId: String, amountMinor: Long, source: String, note: String?): Boolean {
        val purchase = db.inventory().purchase(purchaseId) ?: return false
        if (purchase.voided || amountMinor <= 0) return false
        val id = UUID.randomUUID().toString()
        val time = now()
        val cleanNote = note?.trim()?.ifEmpty { null }
        db.withTransaction {
            recordPayment(id, purchaseId, purchase.supplierId, purchase.supplierName, amountMinor, source, cleanNote, time)
            db.outbox().insert(OutboxEntity(opId = UUID.randomUUID().toString(), kind = "SUPPLIER_PAYMENT", entityId = id,
                payload = json.encodeToString(SupplierPaymentInputDto(purchaseId, amountMinor, source, cleanNote, Instant.ofEpochMilli(time).toString())), createdAt = time))
        }
        requestSync()
        return true
    }

    /** Salda de la compra más vieja a la más nueva, sin pasarse de lo que se debe de cada una. Un pago por compra. */
    suspend fun payOldestFirst(supplierId: String, amountMinor: Long, source: String): Long {
        var left = amountMinor
        val open = db.inventory().purchases(1, supplierId).first().sortedBy { it.purchase.occurredAt }
        for (row in open) {
            if (left <= 0) break
            val part = minOf(left, row.purchase.totalMinor - row.paidMinor)
            if (part > 0 && pay(row.purchase.id, part, source, null)) left -= part
        }
        return amountMinor - left
    }

    suspend fun voidPayment(paymentId: String, reason: String?) {
        val p = db.inventory().payment(paymentId) ?: return
        if (p.voided) return
        db.withTransaction {
            voidPaymentRows(p, reason)
            db.outbox().insert(OutboxEntity(opId = UUID.randomUUID().toString(), kind = "SUPPLIER_PAYMENT_VOID", entityId = paymentId, payload = json.encodeToString(ReasonBody(reason?.trim()?.ifEmpty { null })), createdAt = now()))
        }
        requestSync()
    }

    /** Anular la compra deshace las existencias que sumó y devuelve el dinero de sus pagos. */
    suspend fun voidPurchase(purchaseId: String, reason: String?) {
        val purchase = db.inventory().purchase(purchaseId) ?: return
        if (purchase.voided) return
        val s = session.current()
        val time = now()
        db.withTransaction {
            db.inventory().upsertPurchase(purchase.copy(voided = true, voidReason = reason?.trim()?.ifEmpty { null }))
            for (l in db.inventory().items(purchaseId)) {
                val p = l.productId?.let { db.products().get(it) } ?: continue
                if (p.trackStock) db.inventory().upsertMovement(StockMovementEntity(PurchaseIds.reversal(purchaseId, l.id), p.id, "PURCHASE_REVERSAL", -l.quantityMilli, l.unitCostMinor, "PURCHASE", purchaseId, null, s.memberName, s.memberId, time, 0))
            }
            for (pay in db.inventory().livePayments(purchaseId)) voidPaymentRows(pay, "purchase voided")
            db.outbox().insert(OutboxEntity(opId = UUID.randomUUID().toString(), kind = "PURCHASE_VOID", entityId = purchaseId, payload = json.encodeToString(ReasonBody(reason?.trim()?.ifEmpty { null })), createdAt = time))
        }
        requestSync()
    }

    private suspend fun voidPaymentRows(p: SupplierPaymentEntity, reason: String?) {
        db.inventory().upsertPayment(p.copy(voided = true, voidReason = reason?.trim()?.ifEmpty { null }))
        db.cash().expense(p.id)?.let { db.cash().upsertExpense(it.copy(voided = true, voidReason = reason?.trim()?.ifEmpty { null })) }
    }

    private suspend fun recordPayment(id: String, purchaseId: String, supplierId: String?, supplierName: String?, amountMinor: Long, source: String, note: String?, time: Long) {
        val s = session.current()
        db.inventory().upsertPayment(SupplierPaymentEntity(id, purchaseId, supplierId, amountMinor, source, note, s.memberName, time, false, null, 0))
        val register = if (source == "CASH_DRAWER") db.directory().defaultRegister()?.id else null
        val goods = db.cash().categoryByKey("goods")?.id
        db.cash().upsertExpense(ExpenseEntity(id, goods, if (supplierName == null) "Compra" else "Compra · $supplierName", amountMinor, source, register, s.memberName, s.memberId, time, false, null, 0))
    }
}

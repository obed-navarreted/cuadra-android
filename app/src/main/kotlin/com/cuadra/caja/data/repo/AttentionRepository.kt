package com.cuadra.caja.data.repo

import com.cuadra.caja.data.local.Db
import com.cuadra.caja.data.local.OutboxEntity
import com.cuadra.caja.data.local.SyncStateEntity
import com.cuadra.caja.data.session.SessionStore
import java.util.UUID
import kotlinx.coroutines.flow.Flow

/**
 * «Requiere atención»: lo que el servidor rechazó (o aplicó con algo que revisar) y sus dos salidas.
 * - Reintentar: vuelve a la cola con un id de operación nuevo (el servidor recuerda el rechazo del viejo; una rechazada nunca se aplicó).
 * - Descartar: sale de la cola dejando una línea local (`outbox_discarded`) y el teléfono deshace lo suyo que el servidor nunca aceptó (una venta, un
 *   abono, un gasto…), para que la caja del teléfono y la del servidor vuelvan a coincidir; después baja todo de nuevo.
 */
class AttentionRepository(
    private val db: Db,
    private val session: SessionStore,
    private val requestSync: () -> Unit,
    private val now: () -> Long = System::currentTimeMillis,
) {
    fun items(): Flow<List<OutboxEntity>> = db.outbox().failed()

    suspend fun retry(seq: Long): Boolean {
        val ok = db.outbox().requeue(seq, UUID.randomUUID().toString()) > 0
        if (ok) requestSync()
        return ok
    }

    suspend fun discard(seq: Long): Boolean {
        val op = db.outbox().get(seq) ?: return false
        val s = session.current()
        db.inTransaction {
            db.outbox().discard(seq, now(), s.memberId, s.memberName)
            if (op.state == OutboxEntity.STATE_FAILED) revertLocal(op)
            // Lo que el teléfono tenía de ese registro puede no coincidir con el servidor: se baja todo otra vez (lo pendiente se respeta).
            db.directory().setCursor(SyncStateEntity(cursor = 0, businessId = s.businessId ?: db.directory().syncState()?.businessId))
        }
        requestSync()
        return true
    }

    /** Deshace en el teléfono lo que el servidor NUNCA aceptó (filas sin confirmar, `rev = 0`). Lo que el servidor sí tiene vuelve con la bajada. */
    private suspend fun revertLocal(op: OutboxEntity) {
        val touchedCustomers = mutableSetOf<String>()
        when (op.kind) {
            "SALE_UPSERT" -> if (db.sales().deleteUnconfirmed(op.entityId) > 0) {
                db.sales().deleteItems(op.entityId)
                db.sales().deletePayments(op.entityId)
                touchedCustomers += db.credits().customersOfCredit(op.entityId)
                db.credits().deleteUnconfirmedCredits(op.entityId)
            }
            "CREDIT_UPSERT" -> {
                touchedCustomers += db.credits().customersOfCredit(op.entityId)
                db.credits().deleteUnconfirmedCredits(op.entityId)
            }
            "CREDIT_PAYMENT" -> {
                val credits = db.credits().unconfirmedPaymentCredits(op.entityId)
                db.credits().deleteUnconfirmedPayments(op.entityId)
                if (credits.isNotEmpty()) {
                    db.credits().recompute(credits)
                    touchedCustomers += db.credits().customersOf(credits)
                }
            }
            "EXPENSE_UPSERT" -> db.cash().deleteUnconfirmedExpense(op.entityId)
            "CASH_MOVEMENT_UPSERT" -> db.cash().deleteUnconfirmedMovement(op.entityId)
            // Una devolución que el servidor no aceptó: se quita y la venta vuelve a mostrar lo que queda por devolver.
            "SALE_RETURN" -> {
                val saleId = runCatching { kotlinx.serialization.json.Json.parseToJsonElement(op.payload).let { (it as kotlinx.serialization.json.JsonObject)["saleId"]?.let { v -> (v as kotlinx.serialization.json.JsonPrimitive).content } } }.getOrNull()
                if (db.sales().deleteUnconfirmedReturn(op.entityId) > 0 && saleId != null) SaleReturns.recomputeReturned(db.sales(), saleId)
            }
        }
        // Los saldos de los clientes se recalculan desde sus fiados (también los de una venta a fiado que se quitó).
        if (touchedCustomers.isNotEmpty()) db.customers().recompute(touchedCustomers.toList())
    }
}

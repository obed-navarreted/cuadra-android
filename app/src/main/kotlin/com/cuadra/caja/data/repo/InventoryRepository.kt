package com.cuadra.caja.data.repo

import com.cuadra.caja.data.local.Db
import com.cuadra.caja.data.local.OutboxEntity
import com.cuadra.caja.data.local.ProductProfit
import com.cuadra.caja.data.local.ProductStock
import com.cuadra.caja.data.local.StockMovementEntity
import com.cuadra.caja.data.remote.StockMovementInputDto
import com.cuadra.caja.data.session.SessionStore
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

enum class StockFilter { ALL, TRACKED, REVIEW, UNTRACKED, INACTIVE }

/**
 * Existencias en el teléfono. El stock es la suma de movimientos que solo se agregan: contar, dar de baja o devolver agrega un movimiento
 * (nunca cambia el número "a mano"). Lo que este teléfono mueve cuenta de inmediato como pendiente hasta que el servidor lo confirma.
 */
class InventoryRepository(
    private val db: Db,
    private val session: SessionStore,
    private val requestSync: () -> Unit,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val json = Json { encodeDefaults = true; explicitNulls = false }

    fun stock(query: String, filter: StockFilter): Flow<List<ProductStock>> = db.inventory().stock(query.trim(), filter.name)
    fun stockOf(productId: String): Flow<ProductStock?> = db.inventory().stockOf(productId)
    fun movements(productId: String): Flow<List<StockMovementEntity>> = db.inventory().movements(productId)
    fun reviewCount(): Flow<Int> = db.inventory().reviewCount()
    fun profit(productId: String, from: Long): Flow<ProductProfit> = db.inventory().profit(productId, from)

    /**
     * Conteo físico: la persona dice cuánto hay y el sistema calcula la diferencia. `INITIAL` es el primero (al empezar a llevar control),
     * `ADJUSTMENT` los siguientes. Un conteo sin diferencia también se guarda: es parte del historial.
     */
    suspend fun count(productId: String, countedMilli: Long, initial: Boolean, note: String?) {
        val current = db.inventory().stockNow(productId) ?: return
        if (!current.product.trackStock || countedMilli < 0) return
        record(productId, if (initial) "INITIAL" else "ADJUSTMENT", countedMilli - current.stockNowMilli, StockMovementInputDto(productId, if (initial) "INITIAL" else "ADJUSTMENT", countedMilli = countedMilli, note = note?.trim()?.ifEmpty { null }), note)
    }

    /** Dado de baja (dañado, vencido, consumo propio): resta. */
    suspend fun damage(productId: String, quantityMilli: Long, note: String?) {
        if (quantityMilli <= 0 || db.inventory().stockNow(productId)?.product?.trackStock != true) return
        record(productId, "DAMAGE", -quantityMilli, StockMovementInputDto(productId, "DAMAGE", quantityMilli = quantityMilli, note = note?.trim()?.ifEmpty { null }), note)
    }

    /** Devolución de un cliente: suma. */
    suspend fun returned(productId: String, quantityMilli: Long, note: String?) {
        if (quantityMilli <= 0 || db.inventory().stockNow(productId)?.product?.trackStock != true) return
        record(productId, "RETURN", quantityMilli, StockMovementInputDto(productId, "RETURN", quantityMilli = quantityMilli, note = note?.trim()?.ifEmpty { null }), note)
    }

    private suspend fun record(productId: String, kind: String, delta: Long, input: StockMovementInputDto, note: String?) {
        val s = session.current()
        val id = UUID.randomUUID().toString()
        val time = now()
        val body = input.copy(occurredAt = Instant.ofEpochMilli(time).toString())
        db.inTransaction {
            db.inventory().upsertMovement(StockMovementEntity(id, productId, kind, delta, null, null, null, note?.trim()?.ifEmpty { null }, s.memberName, s.memberId, time, 0))
            db.outbox().insert(OutboxEntity(opId = UUID.randomUUID().toString(), kind = "STOCK_MOVEMENT_ADD", entityId = id, payload = json.encodeToString(body), createdAt = time))
        }
        requestSync()
    }
}

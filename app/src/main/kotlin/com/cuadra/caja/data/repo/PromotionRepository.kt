package com.cuadra.caja.data.repo

import com.cuadra.caja.data.local.Db
import com.cuadra.caja.data.local.OutboxEntity
import com.cuadra.caja.data.local.PromotionEntity
import com.cuadra.caja.data.local.deletePromotion
import com.cuadra.caja.data.local.savePromotion
import com.cuadra.caja.data.remote.PromotionInputDto
import com.cuadra.caja.data.sync.toRule
import com.cuadra.caja.domain.PromotionRule
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Promociones por cantidad del negocio (PENDIENTES.md). Viven en la base del teléfono (se bajan con la sincronización) para que la caja las aplique sin
 * conexión. Crear, cambiar, pausar y borrar (dueño y admins; el servidor lo vuelve a exigir) se guarda aquí y va por la cola de salida en la misma
 * transacción, como un producto.
 */
class PromotionRepository(private val db: Db, private val requestSync: () -> Unit, private val now: () -> Long = System::currentTimeMillis) {
    private val json = Json { encodeDefaults = true; explicitNulls = false }

    /** Todas las promociones con sus productos, como las usa el motor de precios (las pausadas y vencidas también: el motor filtra por la jornada). */
    fun rules(): Flow<List<PromotionRule>> = combine(db.products().promotions(), db.products().promotionProducts()) { promos, links ->
        val byPromo = links.groupBy({ it.promotionId }, { it.productId })
        promos.map { it.toRule(byPromo[it.id].orEmpty().toSet()) }
    }

    suspend fun get(id: String): PromotionRule? = db.products().promotion(id)?.toRule(db.products().promotionProductIds(id).toSet())

    /** Guarda (nueva si `id` es nulo) y la encola. Devuelve su id. */
    suspend fun save(id: String?, name: String, productIds: List<String>, quantity: Int, priceMinor: Long, active: Boolean, startsOn: LocalDate?, endsOn: LocalDate?): String {
        val promoId = id ?: UUID.randomUUID().toString()
        val existing = db.products().promotion(promoId)
        val ids = productIds.distinct()
        val input = PromotionInputDto(name.trim(), ids, quantity, priceMinor, active, startsOn?.toString(), endsOn?.toString())
        db.inTransaction {
            db.products().savePromotion(PromotionEntity(promoId, input.name, quantity, priceMinor, active, input.startsOn, input.endsOn, existing?.rev ?: 0), ids)
            db.outbox().insert(OutboxEntity(opId = UUID.randomUUID().toString(), kind = "PROMOTION_UPSERT", entityId = promoId, payload = json.encodeToString(input), createdAt = now()))
        }
        requestSync()
        return promoId
    }

    /** Pausar o reanudar sin tocar lo demás. */
    suspend fun setActive(id: String, active: Boolean) {
        val r = get(id) ?: return
        save(id, r.name, r.productIds.toList(), r.quantity, r.priceMinor, active, r.startsOn, r.endsOn)
    }

    suspend fun delete(id: String) {
        db.inTransaction {
            db.products().deletePromotion(id)
            db.outbox().insert(OutboxEntity(opId = UUID.randomUUID().toString(), kind = "PROMOTION_DELETE", entityId = id, payload = "{}", createdAt = now()))
        }
        requestSync()
    }
}

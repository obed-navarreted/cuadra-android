package com.cuadra.caja.data.repo

import androidx.room.withTransaction
import com.cuadra.caja.data.local.CuadraDatabase
import com.cuadra.caja.data.local.OutboxEntity
import com.cuadra.caja.data.local.ProductEntity
import com.cuadra.caja.data.remote.ApiFailure
import com.cuadra.caja.data.remote.CuadraApi
import com.cuadra.caja.data.remote.ProductInputDto
import com.cuadra.caja.data.remote.apiCall
import com.cuadra.caja.data.session.SessionStore
import com.cuadra.caja.data.sync.toEntity
import com.cuadra.caja.domain.Barcodes
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Resultado de leer un código. "No existe" solo se afirma si el servidor pudo contestarlo; sin red es otra cosa. */
sealed interface ScanLookup {
    data class Found(val product: ProductEntity) : ScanLookup
    data object Unknown : ScanLookup
    data object UnknownOffline : ScanLookup
}

class ProductRepository(
    private val db: CuadraDatabase,
    private val api: CuadraApi,
    private val session: SessionStore,
    private val requestSync: () -> Unit,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val json = Json { encodeDefaults = true; explicitNulls = false }

    fun quick(): Flow<List<ProductEntity>> = db.products().quick()
    fun search(query: String): Flow<List<ProductEntity>> = if (query.isBlank()) db.products().all() else db.products().search(query.trim())
    fun count(): Flow<Int> = db.products().count()

    /**
     * Orden del plan (6.6): local exacto → equivalencia UPC-A/EAN-13 → consulta puntual al servidor si hay red → guardarlo y repetir.
     * Una lectura normal nunca depende de la red.
     */
    suspend fun byBarcode(raw: String): ScanLookup {
        val code = raw.trim()
        if (code.isEmpty()) return ScanLookup.Unknown
        db.products().byBarcodeForms(Barcodes.forms(code))?.let { return ScanLookup.Found(it) }
        val businessId = session.current().businessId ?: return ScanLookup.Unknown
        val remote = apiCall { api.productByBarcode(businessId, code) }
        return remote.fold(
            onSuccess = {
                db.products().upsert(it.toEntity())
                ScanLookup.Found(it.toEntity())
            },
            onFailure = { if (it is ApiFailure.Offline) ScanLookup.UnknownOffline else ScanLookup.Unknown },
        )
    }

    /** Crea o edita un producto: se guarda en el teléfono y se encola para el servidor en la misma transacción. */
    suspend fun save(id: String?, input: ProductInputDto): ProductEntity {
        val productId = id ?: UUID.randomUUID().toString()
        val entity = ProductEntity(
            id = productId, barcode = input.barcode, shortCode = input.shortCode, name = input.name, variant = input.variant,
            categoryId = input.categoryId, unit = input.unit, pricing = input.pricing, priceMinor = input.priceMinor, costMinor = input.costMinor,
            isQuick = input.isQuick, quickPosition = input.quickPosition, color = input.color, trackStock = input.trackStock,
            stockMilli = db.products().get(productId)?.stockMilli ?: 0, minStockMilli = input.minStockMilli, active = input.active,
            rev = db.products().get(productId)?.rev ?: 0,
        )
        db.withTransaction {
            db.products().upsert(entity)
            db.outbox().insert(OutboxEntity(opId = UUID.randomUUID().toString(), kind = "PRODUCT_UPSERT", entityId = productId,
                payload = json.encodeToString(input), createdAt = now()))
        }
        requestSync()
        return entity
    }
}

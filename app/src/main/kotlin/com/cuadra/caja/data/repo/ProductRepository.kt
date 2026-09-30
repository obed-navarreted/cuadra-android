package com.cuadra.caja.data.repo

import androidx.room.withTransaction
import com.cuadra.caja.data.local.CategoryEntity
import com.cuadra.caja.data.local.CuadraDatabase
import com.cuadra.caja.data.local.OutboxEntity
import com.cuadra.caja.data.local.ProductEntity
import com.cuadra.caja.data.remote.ApiFailure
import com.cuadra.caja.data.remote.CuadraApi
import com.cuadra.caja.data.remote.CategoryInputDto
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

    fun active(): Flow<List<ProductEntity>> = db.products().active()
    fun search(query: String): Flow<List<ProductEntity>> = if (query.isBlank()) db.products().all() else db.products().search(query.trim())
    fun count(): Flow<Int> = db.products().count()

    fun categories(): Flow<List<CategoryEntity>> = db.products().categories()

    /** El otro producto que ya tiene este código en el catálogo del teléfono (el servidor no admite dos), o null. */
    suspend fun ownerOfBarcode(code: String, exceptId: String?): ProductEntity? {
        val trimmed = code.trim()
        if (trimmed.isEmpty()) return null
        return db.products().otherWithBarcode(Barcodes.forms(trimmed), exceptId ?: "")
    }

    /** Categoría nueva desde el editor: se guarda en el teléfono y se encola (CATEGORY_UPSERT) en la misma transacción. */
    suspend fun createCategory(name: String): CategoryEntity {
        val clean = name.trim()
        val entity = CategoryEntity(UUID.randomUUID().toString(), clean, true, 0)
        db.withTransaction {
            db.products().upsertCategory(entity)
            db.outbox().insert(OutboxEntity(opId = UUID.randomUUID().toString(), kind = "CATEGORY_UPSERT", entityId = entity.id,
                payload = json.encodeToString(CategoryInputDto(clean, true)), createdAt = now()))
        }
        requestSync()
        return entity
    }

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

    /** Historial de cambios del producto (solo en línea). Sin red: `ApiFailure.Offline`. */
    suspend fun history(productId: String): Result<List<com.cuadra.caja.data.remote.ProductHistoryEntryDto>> {
        val businessId = session.current().businessId ?: return Result.failure(IllegalStateException("no business"))
        return apiCall { api.productHistory(businessId, productId) }
    }

    /** Varios cambios de productos juntos (ordenar frecuentes): una sola transacción, cada uno con su PRODUCT_UPSERT. */
    suspend fun saveAll(changes: List<Pair<String, ProductInputDto>>) {
        if (changes.isEmpty()) return
        db.withTransaction { changes.forEach { (id, input) -> write(id, input) } }
        requestSync()
    }

    /** Crea o edita un producto: se guarda en el teléfono y se encola para el servidor en la misma transacción. */
    suspend fun save(id: String?, input: ProductInputDto): ProductEntity {
        val entity = db.withTransaction { write(id ?: UUID.randomUUID().toString(), input) }
        requestSync()
        return entity
    }

    private suspend fun write(productId: String, input: ProductInputDto): ProductEntity {
        val old = db.products().get(productId)
        val entity = ProductEntity(
            id = productId, barcode = input.barcode, shortCode = input.shortCode, name = input.name, variant = input.variant,
            categoryId = input.categoryId, unit = input.unit, pricing = input.pricing, priceMinor = input.priceMinor, costMinor = input.costMinor,
            isQuick = input.isQuick, quickPosition = input.quickPosition, color = input.color, trackStock = input.trackStock,
            stockMilli = db.products().get(productId)?.stockMilli ?: 0, minStockMilli = input.minStockMilli, active = input.active,
            rev = db.products().get(productId)?.rev ?: 0,
        )
        db.products().upsert(entity)
        if (old == null) {
            db.outbox().insert(OutboxEntity(opId = UUID.randomUUID().toString(), kind = "PRODUCT_UPSERT", entityId = productId,
                payload = json.encodeToString(input), createdAt = now()))
        } else {
            // Uno que ya existe: solo lo que cambió (un teléfono con datos viejos no revierte el precio o el costo que cambió otro).
            com.cuadra.caja.domain.ProductPayload.patch(old, input)?.let { patch ->
                db.outbox().insert(OutboxEntity(opId = UUID.randomUUID().toString(), kind = "PRODUCT_PATCH", entityId = productId, payload = patch.toString(), createdAt = now()))
            }
        }
        return entity
    }
}

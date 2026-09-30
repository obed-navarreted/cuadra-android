package com.cuadra.caja.data.sync

import androidx.room.withTransaction
import com.cuadra.caja.data.local.CuadraDatabase
import com.cuadra.caja.data.local.mergeMembers
import com.cuadra.caja.data.local.deleteUnconfirmedPayment
import com.cuadra.caja.data.local.SyncStateEntity
import com.cuadra.caja.data.remote.BusinessDto
import com.cuadra.caja.data.remote.CashRegisterDto
import com.cuadra.caja.data.remote.CategoryDto
import com.cuadra.caja.data.remote.CashMovementDto
import com.cuadra.caja.data.remote.NotificationDto
import com.cuadra.caja.data.remote.PurchaseDto
import com.cuadra.caja.data.remote.StockMovementDto
import com.cuadra.caja.data.remote.SupplierDto
import com.cuadra.caja.data.remote.SupplierPaymentDto
import com.cuadra.caja.data.remote.CreditDto
import com.cuadra.caja.data.remote.ExpenseCategoryDto
import com.cuadra.caja.data.remote.ExpenseDto
import com.cuadra.caja.data.remote.ShiftDto
import com.cuadra.caja.data.remote.CreditPaymentDto
import com.cuadra.caja.data.remote.CustomerDto
import com.cuadra.caja.data.remote.TemplateDto
import com.cuadra.caja.data.remote.ChangeDto
import com.cuadra.caja.data.remote.MemberDto
import com.cuadra.caja.data.remote.ProductDto
import com.cuadra.caja.data.remote.SaleDto
import kotlinx.serialization.json.Json

/** `businessId`: el negocio al que está vinculado el teléfono. Solo se envía lo de ese negocio y el cursor es el suyo. */
class RoomSyncStore(private val db: CuadraDatabase, private val businessId: String? = null, private val hasMember: () -> Boolean = { true }) : SyncStore {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun dueOps(limit: Int, now: Long) = db.outbox().due(limit, now, businessId, hasMember())
    override suspend fun acknowledge(seqs: List<Long>) = db.outbox().delete(seqs)
    override suspend fun markFailed(seq: Long, code: String, detail: String?) = db.outbox().markFailed(seq, code, detail)
    override suspend fun markReview(seq: Long, code: String, detail: String?) = db.outbox().markReview(seq, code, detail)
    override suspend fun retryLater(seqs: List<Long>, next: Long, code: String?) = db.outbox().retryLater(seqs, next, code)
    override suspend fun pendingCount() = db.outbox().pendingCountNow()

    /** El cursor guardado solo vale si es de ESTE negocio (uno de otro negocio saltaría su historial): si no, se baja todo desde cero. */
    override suspend fun cursor(): Long {
        val state = db.directory().syncState() ?: return 0L
        return if (state.businessId != null && businessId != null && state.businessId != businessId) 0L else state.cursor
    }

    override suspend fun applyPage(changes: List<ChangeDto>, cursor: Long) {
        db.withTransaction {
            val touchedCredits = linkedSetOf<String>()
            val touchedCustomers = linkedSetOf<String>()
            for (c in changes) apply(c, touchedCredits, touchedCustomers)
            // Los saldos se recalculan siempre desde los abonos que este teléfono conoce (incluidos los aún sin enviar): así el orden en que
            // llegue la información no hace parpadear el saldo ni pisa un abono local pendiente.
            if (touchedCredits.isNotEmpty()) {
                db.credits().recompute(touchedCredits.toList())
                touchedCustomers += db.credits().customersOf(touchedCredits.toList())
            }
            if (touchedCustomers.isNotEmpty()) db.customers().recompute(touchedCustomers.toList())
            db.directory().setCursor(SyncStateEntity(cursor = cursor, businessId = businessId))
        }
    }

    private suspend fun apply(c: ChangeDto, touchedCredits: MutableSet<String>, touchedCustomers: MutableSet<String>) {
        val dir = db.directory()
        when (c.type) {
            "business" -> dir.upsertBusiness(json.decodeFromJsonElement(BusinessDto.serializer(), c.data).toEntity())
            "member" -> dir.mergeMembers(listOf(json.decodeFromJsonElement(MemberDto.serializer(), c.data).toEntity()))
            "cash_register" -> dir.upsertRegisters(listOf(json.decodeFromJsonElement(CashRegisterDto.serializer(), c.data).toEntity()))
            "product" -> {
                val p = json.decodeFromJsonElement(ProductDto.serializer(), c.data)
                // Si hay un cambio local sin enviar sobre este producto, gana el teléfono hasta que se suba.
                if (db.outbox().countFor(p.id) == 0) db.products().upsert(p.toEntity())
            }
            "category" -> {
                val d = json.decodeFromJsonElement(CategoryDto.serializer(), c.data)
                if (db.outbox().countFor(d.id) == 0) db.products().upsertCategory(d.toEntity())
            }
            "sale" -> {
                val s = json.decodeFromJsonElement(SaleDto.serializer(), c.data)
                // Lo mismo con las ventas: una venta con operación pendiente no se pisa con la versión del servidor.
                if (s.status != "OPEN" && db.outbox().countFor(s.id) == 0) {
                    val rows = s.toRows()
                    val sales = db.sales()
                    sales.upsert(rows.sale)
                    sales.deleteItems(s.id); sales.insertItems(rows.items)
                    sales.deletePayments(s.id); sales.insertPayments(rows.payments)
                    // Las devoluciones confirmadas se reemplazan con las del servidor; una hecha aquí sin conexión (pendiente) se conserva y vuelve a contar.
                    sales.deleteConfirmedReturns(s.id)
                    rows.returns.forEach { r -> sales.deleteUnconfirmedReturn(r.id); sales.upsertReturn(r) }
                    com.cuadra.caja.data.repo.SaleReturns.recomputeReturned(sales, s.id)
                }
            }
            "customer" -> {
                val d = json.decodeFromJsonElement(CustomerDto.serializer(), c.data)
                if (db.outbox().countFor(d.id) == 0) {
                    db.customers().upsert(d.toEntity())
                    touchedCustomers += d.id
                }
            }
            "credit" -> {
                val d = json.decodeFromJsonElement(CreditDto.serializer(), c.data)
                db.credits().upsert(d.toEntity())
                touchedCredits += d.id
                d.customerId?.let { touchedCustomers += it }
            }
            "credit_payment" -> {
                val d = json.decodeFromJsonElement(CreditPaymentDto.serializer(), c.data)
                // Un abono repartido se guarda en la cola con el id del grupo; sus hijos (ids derivados) se reconocen por él.
                if (db.outbox().countFor(d.groupId ?: d.id) == 0) {
                    // Un abono que el teléfono guardó a UN fiado y el servidor repartió (ese fiado ya estaba cerrado: se pasó a las otras deudas del
                    // cliente): la fila local sin confirmar con el id del grupo se quita, para no contar el dinero dos veces.
                    d.groupId?.let { g -> db.credits().deleteUnconfirmedPayment(g)?.let { touchedCredits += it } }
                    db.credits().upsertPayments(listOf(d.toEntity()))
                    touchedCredits += d.creditId
                }
            }
            "message_template" -> db.templates().upsert(json.decodeFromJsonElement(TemplateDto.serializer(), c.data).toEntity())
            // Un movimiento que el teléfono creó (mismo id que el del servidor) se reemplaza por el confirmado: deja de contar como pendiente.
            "stock_movement" -> db.inventory().upsertMovement(json.decodeFromJsonElement(StockMovementDto.serializer(), c.data).toEntity())
            "supplier" -> {
                val d = json.decodeFromJsonElement(SupplierDto.serializer(), c.data)
                if (db.outbox().countFor(d.id) == 0) db.inventory().upsertSupplier(d.toEntity())
            }
            "purchase" -> {
                val d = json.decodeFromJsonElement(PurchaseDto.serializer(), c.data)
                if (db.outbox().countFor(d.id) == 0) {
                    db.inventory().upsertPurchase(d.toEntity())
                    db.inventory().deleteItems(d.id)
                    db.inventory().insertItems(d.itemEntities())
                }
            }
            "supplier_payment" -> {
                val d = json.decodeFromJsonElement(SupplierPaymentDto.serializer(), c.data)
                if (db.outbox().countFor(d.id) == 0) db.inventory().upsertPayment(d.toEntity())
            }
            "notification" -> {
                val d = json.decodeFromJsonElement(NotificationDto.serializer(), c.data)
                val old = db.notifications().get(d.id)
                // "Mostrada" es local y "leída" no retrocede: si el teléfono ya la marcó, el servidor todavía puede no saberlo.
                db.notifications().upsert(d.toEntity(shown = old?.shown ?: false, localReadAt = old?.readAt))
            }
            "expense_category" -> db.cash().upsertCategories(listOf(json.decodeFromJsonElement(ExpenseCategoryDto.serializer(), c.data).toEntity()))
            "expense" -> {
                val d = json.decodeFromJsonElement(ExpenseDto.serializer(), c.data)
                if (db.outbox().countFor(d.id) == 0) db.cash().upsertExpense(d.toEntity())
            }
            "cash_movement" -> {
                val d = json.decodeFromJsonElement(CashMovementDto.serializer(), c.data)
                if (db.outbox().countFor(d.id) == 0) db.cash().upsertMovement(d.toEntity())
            }
            "shift" -> {
                val d = json.decodeFromJsonElement(ShiftDto.serializer(), c.data)
                // Mientras este teléfono tiene una apertura o un cierre sin enviar de ese turno, gana lo local.
                if (db.outbox().countFor(d.id) == 0) {
                    db.cash().upsertShift(d.toEntity())
                    // Si otro teléfono ganó la apertura de esta caja, el turno que este abrió por su cuenta se descarta.
                    if (d.status == "OPEN") db.cash().dropOtherOpenShifts(d.cashRegisterId, d.id)
                }
            }
            else -> Unit   // tipos futuros: se ignoran sin fallar
        }
    }
}

/** Adaptador de Retrofit al motor. */
class RetrofitSyncRemote(
    private val api: com.cuadra.caja.data.remote.CuadraApi, private val businessId: String, private val activeMember: () -> String? = { null },
) : SyncRemote {
    /** Sin persona activa (pantalla de PIN, acceso desactivado) la tanda va como quien hizo las operaciones: lo pendiente no espera a que alguien entre. */
    override suspend fun push(body: com.cuadra.caja.data.remote.PushBody) = com.cuadra.caja.data.remote.apiCall {
        api.push(businessId, body, if (activeMember() != null) null else body.ops.firstNotNullOfOrNull { it.memberId })
    }
    override suspend fun pull(since: Long, limit: Int) = com.cuadra.caja.data.remote.apiCall { api.pull(businessId, since, limit) }
    override suspend fun pull(since: Long, limit: Int, pendingOps: Int?) = com.cuadra.caja.data.remote.apiCall { api.pull(businessId, since, limit, pendingOps) }
}


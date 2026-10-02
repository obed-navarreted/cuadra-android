package com.cuadra.caja.data.repo

import com.cuadra.caja.data.local.CashMovementEntity
import com.cuadra.caja.data.local.Db
import com.cuadra.caja.data.local.ExpenseCategoryEntity
import com.cuadra.caja.data.local.ExpenseEntity
import com.cuadra.caja.data.local.ExpenseTotals
import com.cuadra.caja.data.local.OutboxEntity
import com.cuadra.caja.data.remote.CategoryInputDto
import com.cuadra.caja.data.remote.CuadraApi
import com.cuadra.caja.data.remote.ExpenseInputDto
import com.cuadra.caja.data.remote.apiCall
import com.cuadra.caja.data.sync.toEntity
import com.cuadra.caja.data.remote.MovementInputDto
import com.cuadra.caja.data.remote.ReasonBody
import com.cuadra.caja.data.session.SessionStore
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Todo el dinero que sale queda registrado. Solo un gasto pagado DEL CAJÓN afecta el cierre; los demás se anotan para saber cuánto se gasta.
 * Un gasto no se edita: se anula (con motivo) y se registra de nuevo. Se guarda en el teléfono y en la cola de salida en una sola transacción.
 */
class ExpenseRepository(
    private val db: Db,
    private val session: SessionStore,
    private val requestSync: () -> Unit,
    private val api: CuadraApi? = null,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val json = Json { encodeDefaults = true; explicitNulls = false }

    fun categories(): Flow<List<ExpenseCategoryEntity>> = db.cash().categories()
    fun allCategories(): Flow<List<ExpenseCategoryEntity>> = db.cash().allCategories()

    /**
     * Crear, renombrar o archivar una categoría (dueño y admin; solo con conexión: `PUT expense-categories/{id}`). Sin `id` se crea una nueva. `name` nulo no
     * cambia el nombre; `active = false` la archiva (deja de ofrecerse al anotar un gasto, los gastos ya anotados la conservan).
     */
    suspend fun saveCategory(id: String?, name: String?, active: Boolean = true): Result<ExpenseCategoryEntity> {
        val remote = api ?: return Result.failure(IllegalStateException("sin conexión configurada"))
        val businessId = session.current().businessId ?: return Result.failure(IllegalStateException("no business"))
        val categoryId = id ?: UUID.randomUUID().toString()
        return apiCall { remote.saveExpenseCategory(businessId, categoryId, CategoryInputDto(name?.trim()?.ifEmpty { null }, active)) }.map { dto -> dto.toEntity().also { db.cash().upsertCategories(listOf(it)) } }
    }
    fun expenses(from: Long, to: Long): Flow<List<ExpenseEntity>> = db.cash().expenses(from, to)
    fun movements(from: Long, to: Long): Flow<List<CashMovementEntity>> = db.cash().movements(from, to)
    fun totals(from: Long, to: Long): Flow<ExpenseTotals> = db.cash().expenseTotals(from, to)

    suspend fun addExpense(amountMinor: Long, description: String?, categoryId: String?, source: String): ExpenseEntity {
        val s = session.current()
        val id = UUID.randomUUID().toString()
        val time = now()
        val register = db.directory().defaultRegister()?.id
        val text = description?.trim()?.ifEmpty { null }
        val entity = ExpenseEntity(id, categoryId, text, amountMinor, source, register.takeIf { source == "CASH_DRAWER" }, s.memberName, s.memberId, time, false, null, 0)
        val input = ExpenseInputDto(categoryId, text, amountMinor, source, entity.cashRegisterId, Instant.ofEpochMilli(time).toString())
        db.inTransaction {
            db.cash().upsertExpense(entity)
            db.outbox().insert(OutboxEntity(opId = UUID.randomUUID().toString(), kind = "EXPENSE_UPSERT", entityId = id, payload = json.encodeToString(input), createdAt = time))
        }
        requestSync()
        return entity
    }

    suspend fun addMovement(kind: String, amountMinor: Long, reason: String?): CashMovementEntity? {
        val register = db.directory().defaultRegister()?.id ?: return null
        val s = session.current()
        val id = UUID.randomUUID().toString()
        val time = now()
        val text = reason?.trim()?.ifEmpty { null }
        val entity = CashMovementEntity(id, kind, amountMinor, text, register, s.memberName, s.memberId, time, false, null, 0)
        val input = MovementInputDto(kind, amountMinor, text, register, Instant.ofEpochMilli(time).toString())
        db.inTransaction {
            db.cash().upsertMovement(entity)
            db.outbox().insert(OutboxEntity(opId = UUID.randomUUID().toString(), kind = "CASH_MOVEMENT_UPSERT", entityId = id, payload = json.encodeToString(input), createdAt = time))
        }
        requestSync()
        return entity
    }

    /** Anular un gasto (o un retiro/entrada) devuelve el dinero al cálculo del cierre. Solo quien administra; el servidor lo exige igual. */
    suspend fun voidExpense(id: String, reason: String?) {
        val e = db.cash().expense(id) ?: return
        db.inTransaction {
            db.cash().upsertExpense(e.copy(voided = true, voidReason = reason))
            db.outbox().insert(OutboxEntity(opId = UUID.randomUUID().toString(), kind = "EXPENSE_VOID", entityId = id, payload = json.encodeToString(ReasonBody(reason)), createdAt = now()))
        }
        requestSync()
    }

    suspend fun voidMovement(id: String, reason: String?) {
        val m = db.cash().movement(id) ?: return
        db.inTransaction {
            db.cash().upsertMovement(m.copy(voided = true, voidReason = reason))
            db.outbox().insert(OutboxEntity(opId = UUID.randomUUID().toString(), kind = "CASH_MOVEMENT_VOID", entityId = id, payload = json.encodeToString(ReasonBody(reason)), createdAt = now()))
        }
        requestSync()
    }
}

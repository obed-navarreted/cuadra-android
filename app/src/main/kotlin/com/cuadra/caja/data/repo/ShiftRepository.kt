package com.cuadra.caja.data.repo

import com.cuadra.caja.data.local.Db
import com.cuadra.caja.data.local.OutboxEntity
import com.cuadra.caja.data.local.ShiftEntity
import com.cuadra.caja.data.remote.CloseShiftInputDto
import com.cuadra.caja.data.remote.OpenShiftInputDto
import com.cuadra.caja.data.session.SessionStore
import com.cuadra.caja.domain.CashClosing
import com.cuadra.caja.domain.ClosingBreakdown
import com.cuadra.caja.domain.Denominations
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

sealed interface CloseResult {
    data class Closed(val shift: ShiftEntity) : CloseResult
    /** La diferencia supera el umbral del negocio y falta la nota. */
    data class NoteRequired(val differenceMinor: Long) : CloseResult
    data object NotFound : CloseResult
    data object AlreadyClosed : CloseResult
}

/**
 * Turnos y cierre. Un turno es de una caja FÍSICA y todo lo que ocurre en esa caja mientras dura se le asigna por caja y hora (como en el servidor).
 * Lo esperado se recalcula siempre desde las ventas y movimientos que este teléfono conoce; el servidor recalcula con lo de todos los teléfonos al recibir el cierre.
 */
class ShiftRepository(
    private val db: Db,
    private val session: SessionStore,
    private val requestSync: () -> Unit,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val json = Json { encodeDefaults = true; explicitNulls = false }

    suspend fun registerId(): String? = db.directory().defaultRegister()?.id

    /** El turno abierto de esta caja (nulo si no hay). */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun current(): Flow<ShiftEntity?> = db.directory().business().flatMapLatest { registerFlow() }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun registerFlow(): Flow<ShiftEntity?> = kotlinx.coroutines.flow.flow { emit(registerId()) }.flatMapLatest { r -> if (r == null) flowOf(null) else db.cash().openShift(r) }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun recent(limit: Int = 10): Flow<List<ShiftEntity>> = kotlinx.coroutines.flow.flow { emit(registerId()) }.flatMapLatest { r -> if (r == null) flowOf(emptyList()) else db.cash().recentShifts(r, limit) }

    /** Lo que movió la caja en la ventana del turno, en vivo (hasta ahora si está abierto, hasta el cierre si ya cerró). */
    fun breakdown(shift: ShiftEntity): Flow<ClosingBreakdown> =
        db.cash().breakdown(shift.cashRegisterId, shift.openedAt, shift.closedAt ?: Long.MAX_VALUE).map { r ->
            ClosingBreakdown(r.cashSales, r.cashSalesCount, r.creditCash, r.deposits, r.expensesCash, r.withdrawals, r.transfer, r.card, r.other, r.creditNew, r.cancelled)
        }

    /** Sugerencia de fondo inicial: lo contado al cerrar el turno anterior. */
    suspend fun suggestedFloat(): Long = registerId()?.let { db.cash().lastClosed(it)?.countedMinor } ?: 0

    /** Abre un turno. Si ya hay uno abierto en esta caja se devuelve ese: no hay dos a la vez. */
    suspend fun open(floatMinor: Long): ShiftEntity? {
        val register = registerId() ?: return null
        db.cash().openShiftNow(register)?.let { return it }
        val s = session.current()
        val id = UUID.randomUUID().toString()
        val time = now()
        val shift = ShiftEntity(id, register, null, s.memberName, s.memberId, time, floatMinor, null, null, null, null, null, null, null, "OPEN", null, 0, 0)
        val input = OpenShiftInputDto(register, floatMinor, Instant.ofEpochMilli(time).toString())
        db.inTransaction {
            db.cash().upsertShift(shift)
            db.outbox().insert(OutboxEntity(opId = UUID.randomUUID().toString(), kind = "SHIFT_OPEN", entityId = id, payload = json.encodeToString(input), createdAt = time))
        }
        requestSync()
        return shift
    }

    /**
     * Cierra el turno con el efectivo contado. La diferencia se calcula con lo que este teléfono sabe; el servidor la recalcula con todo lo de la caja
     * y, si otro teléfono de la misma caja tiene operaciones sin enviar, el cierre espera (o lo fuerza quien administra).
     */
    suspend fun close(shiftId: String, countedMinor: Long, counts: Map<Long, Int>?, note: String?, force: Boolean = false, forcedReason: String? = null): CloseResult {
        val shift = db.cash().shift(shiftId) ?: return CloseResult.NotFound
        if (shift.status == "CLOSED") return CloseResult.AlreadyClosed
        val time = now()
        val b = breakdown(shift.copy(closedAt = time)).first()
        val expected = CashClosing.expected(shift.openingFloatMinor, b)
        val diff = CashClosing.difference(countedMinor, expected)
        val cleanNote = note?.trim()?.ifEmpty { null }
        if (CashClosing.needsNote(diff, db.directory().businessNow()?.shiftNoteThresholdMinor, cleanNote)) return CloseResult.NoteRequired(diff)
        val s = session.current()
        val denominations = counts?.takeIf { it.values.any { n -> n > 0 } }?.let { Denominations.toJson(it) }
        val closed = shift.copy(closedByName = s.memberName, closedAt = time, expectedAtCloseMinor = expected, countedMinor = countedMinor, differenceMinor = diff, denominations = denominations,
            note = cleanNote, status = "CLOSED", forcedReason = forcedReason)
        val input = CloseShiftInputDto(countedMinor, denominations, cleanNote, Instant.ofEpochMilli(time).toString(), force, forcedReason)
        db.inTransaction {
            db.cash().upsertShift(closed)
            db.outbox().insert(OutboxEntity(opId = UUID.randomUUID().toString(), kind = "SHIFT_CLOSE", entityId = shiftId, payload = json.encodeToString(input), createdAt = time))
        }
        requestSync()
        return CloseResult.Closed(closed)
    }
}

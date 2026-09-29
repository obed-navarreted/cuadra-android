package com.cuadra.caja.data.sync

import com.cuadra.caja.data.local.OutboxEntity
import com.cuadra.caja.data.remote.ApiFailure
import com.cuadra.caja.data.remote.ChangeDto
import com.cuadra.caja.data.remote.OpDto
import com.cuadra.caja.data.remote.PullResponse
import com.cuadra.caja.data.remote.PushBody
import com.cuadra.caja.data.remote.PushResponse
import kotlinx.serialization.json.Json

/** Lo que el motor necesita del servidor. Retrofit lo implementa (`RetrofitSyncRemote`); las pruebas usan un doble. */
interface SyncRemote {
    suspend fun push(body: PushBody): Result<PushResponse>
    suspend fun pull(since: Long, limit: Int): Result<PullResponse>
}

/** Lo que el motor necesita de la base local. Room lo implementa (`RoomSyncStore`). */
interface SyncStore {
    suspend fun dueOps(limit: Int, now: Long): List<OutboxEntity>
    suspend fun acknowledge(seqs: List<Long>)
    suspend fun markFailed(seq: Long, code: String)
    suspend fun retryLater(seqs: List<Long>, next: Long, code: String?)
    suspend fun pendingCount(): Int
    suspend fun cursor(): Long

    /** Guarda la página COMPLETA y el cursor en una sola transacción: si algo falla, el cursor no avanza. */
    suspend fun applyPage(changes: List<ChangeDto>, cursor: Long)
}

sealed interface SyncResult {
    data class Done(val pushed: Int, val rejected: Int, val pulled: Int) : SyncResult

    /** Sin conexión: lo pendiente queda guardado y se reintenta. */
    data object Offline : SyncResult

    /** El servidor no reconoce la sesión o revocó el teléfono: hay que iniciar sesión / vincular de nuevo. Lo pendiente se conserva. */
    data class AuthProblem(val code: String) : SyncResult

    data class Failed(val code: String) : SyncResult
}

/**
 * Sincronización de un teléfono (PLAN.md sección 14): primero sube su cola de salida, después baja los cambios del negocio.
 * Room manda: la pantalla nunca espera a la red.
 */
class SyncEngine(
    private val remote: SyncRemote,
    private val store: SyncStore,
    private val now: () -> Long = System::currentTimeMillis,
) {
    suspend fun run(): SyncResult {
        var pushed = 0
        var rejected = 0

        while (true) {
            val due = store.dueOps(BATCH, now())
            if (due.isEmpty()) break
            val body = PushBody(due.map { OpDto(it.opId, it.kind, it.entityId, Json.parseToJsonElement(it.payload)) }, store.pendingCount())
            val response = remote.push(body).getOrElse { return handleFailure(it, due) }

            val byOp = response.results.associateBy { it.opId }
            val done = mutableListOf<Long>()
            val retry = mutableListOf<OutboxEntity>()
            for (op in due) {
                val r = byOp[op.opId]
                // DUPLICATE con código = el servidor ya vio esta operación y la RECHAZÓ: repetirla no la aplica. Solo un DUPLICATE sin código
                // significa "ya se aplicó". Confundirlos borraría de la cola una venta que el servidor nunca guardó.
                val wasRejected = r != null && (r.status == "REJECTED" || (r.status == "DUPLICATE" && r.code != null))
                when {
                    r == null -> retry += op    // el servidor no la mencionó: se reintenta, no se pierde
                    !wasRejected && (r.status == "APPLIED" || r.status == "DUPLICATE" || r.status == "STALE") -> { done += op.seq; pushed++ }
                    r.code in TRANSIENT -> retry += op
                    else -> { store.markFailed(op.seq, r.code ?: "REJECTED"); rejected++ }
                }
            }
            if (done.isNotEmpty()) store.acknowledge(done)
            retry.forEach { store.retryLater(listOf(it.seq), now() + backoffMillis(it.attempts + 1), "RETRY") }
            // Si nada avanzó en este lote, lo que quedó ya tiene espera futura y `dueOps` no lo devolverá: el bucle termina.
        }

        var pulled = 0
        var since = store.cursor()
        do {
            val page = remote.pull(since, PAGE).getOrElse { return handleFailure(it, emptyList()) }
            store.applyPage(page.changes, page.cursor)
            pulled += page.changes.size
            since = page.cursor
        } while (page.hasMore)
        return SyncResult.Done(pushed, rejected, pulled)
    }

    private suspend fun handleFailure(error: Throwable, due: List<OutboxEntity>): SyncResult = when (error) {
        is ApiFailure.Offline -> {
            reschedule(due, "OFFLINE")
            SyncResult.Offline
        }
        is ApiFailure.Http -> if (error.isAuth || error.code == "DEVICE_REVOKED" || error.code == "BUSINESS_NOT_FOUND") {
            // No se toca la cola: tras volver a entrar, todo lo pendiente se envía con su identidad original.
            SyncResult.AuthProblem(error.code)
        } else {
            reschedule(due, error.code)
            SyncResult.Failed(error.code)
        }
        else -> {
            reschedule(due, "UNKNOWN")
            SyncResult.Failed("UNKNOWN")
        }
    }

    private suspend fun reschedule(ops: List<OutboxEntity>, code: String) {
        ops.forEach { store.retryLater(listOf(it.seq), now() + backoffMillis(it.attempts + 1), code) }
    }

    companion object {
        const val BATCH = 100
        const val PAGE = 200
        /** Rechazos pasajeros: se reintentan con espera. SALE_LOCKED se libera solo (el bloqueo dura 10 min) o al revocar al teléfono que lo tenía; DEVICES_PENDING (cierre de caja) cede cuando el otro teléfono de la caja termina de enviar. BUSINESS_SUSPENDED: el negocio está suspendido; nada se descarta, la cola espera a que se reactive. */
        private val TRANSIENT = setOf("INTERNAL_ERROR", "CONFLICT", "SALE_LOCKED", "DEVICES_PENDING", "BUSINESS_SUSPENDED")

        /** 5 s, 10 s, 20 s… hasta 15 min. */
        fun backoffMillis(attempt: Int): Long = (5_000L shl (attempt - 1).coerceIn(0, 10)).coerceAtMost(15 * 60_000L)
    }
}

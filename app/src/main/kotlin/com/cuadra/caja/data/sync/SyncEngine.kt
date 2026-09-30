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

    /** Igual, informando cuántas operaciones quedan sin enviar (el cierre del día del dueño avisa con ese número). */
    suspend fun pull(since: Long, limit: Int, pendingOps: Int?): Result<PullResponse> = pull(since, limit)
}

/** Lo que el motor necesita de la base local. Room lo implementa (`RoomSyncStore`). */
interface SyncStore {
    suspend fun dueOps(limit: Int, now: Long): List<OutboxEntity>
    suspend fun acknowledge(seqs: List<Long>)
    suspend fun markFailed(seq: Long, code: String, detail: String? = null)

    /** Se aplicó pero alguien debe revisarla (venta guardada aparte por conflicto, o ya descartada en otro teléfono): sale de lo pendiente, queda visible. */
    suspend fun markReview(seq: Long, code: String, detail: String? = null)
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
        var reviewed = 0

        while (true) {
            val due = store.dueOps(BATCH, now())
            if (due.isEmpty()) break
            // Cada operación viaja con quien la HIZO y cuándo: el servidor la aplica con esa persona aunque ahora atienda otra.
            val body = PushBody(due.map { OpDto(it.opId, it.kind, it.entityId, Json.parseToJsonElement(it.payload), it.memberId, java.time.Instant.ofEpochMilli(it.createdAt).toString()) }, store.pendingCount())
            val response = remote.push(body).getOrElse { return handleFailure(it, due) }

            val byOp = response.results.associateBy { it.opId }
            val done = mutableListOf<Long>()
            val retry = mutableListOf<OutboxEntity>()
            for (op in due) {
                val r = byOp[op.opId]
                // DUPLICATE con código = el servidor ya vio esta operación y la RECHAZÓ: repetirla no la aplica. Solo un DUPLICATE sin código
                // significa "ya se aplicó". Confundirlos borraría de la cola una venta que el servidor nunca guardó.
                // Excepción: SALE_CONFLICT_COPY es un aviso sobre algo YA aplicado (la venta se guardó aparte), no un rechazo.
                val review = r != null && r.code in REVIEW_CODES && (r.status == "APPLIED" || r.status == "DUPLICATE")
                val wasRejected = r != null && !review && (r.status == "REJECTED" || (r.status == "DUPLICATE" && r.code != null))
                when {
                    r == null -> retry += op    // el servidor no la mencionó: se reintenta, no se pierde
                    review -> { store.markReview(op.seq, r.code!!, r.detail?.toString()); pushed++; reviewed++ }
                    // Una venta COBRADA que el servidor da por vieja (ya estaba descartada en otro teléfono con el mismo contenido): no se borra en silencio.
                    r.status == "STALE" && isCompletedSale(op) -> { store.markReview(op.seq, "SALE_STALE", null); pushed++; reviewed++ }
                    !wasRejected && (r.status == "APPLIED" || r.status == "DUPLICATE" || r.status == "STALE") -> { done += op.seq; pushed++ }
                    r.code in TRANSIENT -> retry += op
                    else -> { store.markFailed(op.seq, r.code ?: "REJECTED", r.detail?.toString()); rejected++ }
                }
            }
            if (done.isNotEmpty()) store.acknowledge(done)
            retry.forEach { store.retryLater(listOf(it.seq), now() + backoffMillis(it.attempts + 1), "RETRY") }
            // Si nada avanzó en este lote, lo que quedó ya tiene espera futura y `dueOps` no lo devolverá: el bucle termina.
        }

        var pulled = 0
        var since = store.cursor()
        // Lo que quedó pendiente DESPUÉS de subir viaja con la bajada: el servidor sabe si este teléfono aún tiene ventas sin enviar.
        var pending: Int? = store.pendingCount()
        do {
            val page = remote.pull(since, PAGE, pending).getOrElse { return handleFailure(it, emptyList()) }
            pending = null
            store.applyPage(page.changes, page.cursor)
            pulled += page.changes.size
            since = page.cursor
        } while (page.hasMore)
        return SyncResult.Done(pushed, rejected + reviewed, pulled)
    }

    private fun isCompletedSale(op: OutboxEntity): Boolean = op.kind == "SALE_UPSERT" &&
        runCatching { (Json.parseToJsonElement(op.payload) as? kotlinx.serialization.json.JsonObject)?.get("status")?.let { (it as kotlinx.serialization.json.JsonPrimitive).content } == "COMPLETED" }.getOrDefault(false)

    private suspend fun handleFailure(error: Throwable, due: List<OutboxEntity>): SyncResult = when (error) {
        is ApiFailure.Offline -> {
            reschedule(due, "OFFLINE")
            SyncResult.Offline
        }
        // MEMBER_NOT_ACTIVE: la persona activa fue dada de baja (teléfono compartido); ACCESS_DISABLED (401): el teléfono personal de alguien dado de baja.
        // Ninguno se reintenta a ciegas para siempre: la app lo muestra. La cola no se toca.
        is ApiFailure.Http -> if (error.isAuth || error.code == "DEVICE_REVOKED" || error.code == "BUSINESS_NOT_FOUND" || error.code == "MEMBER_NOT_ACTIVE") {
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

        /** Avisos sobre operaciones YA aplicadas que alguien debe revisar. */
        val REVIEW_CODES = setOf("SALE_CONFLICT_COPY")

        /** 5 s, 10 s, 20 s… hasta 15 min. */
        fun backoffMillis(attempt: Int): Long = (5_000L shl (attempt - 1).coerceIn(0, 10)).coerceAtMost(15 * 60_000L)
    }
}

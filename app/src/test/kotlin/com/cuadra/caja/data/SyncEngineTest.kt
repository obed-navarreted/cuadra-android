package com.cuadra.caja.data

import com.cuadra.caja.data.local.OutboxEntity
import com.cuadra.caja.data.remote.ApiFailure
import com.cuadra.caja.data.remote.ChangeDto
import com.cuadra.caja.data.remote.OpResultDto
import com.cuadra.caja.data.remote.PullResponse
import com.cuadra.caja.data.remote.PushBody
import com.cuadra.caja.data.remote.PushResponse
import com.cuadra.caja.data.sync.SyncEngine
import com.cuadra.caja.data.sync.SyncRemote
import com.cuadra.caja.data.sync.SyncResult
import com.cuadra.caja.data.sync.SyncStatus
import com.cuadra.caja.data.sync.SyncStore
import com.cuadra.caja.data.sync.syncStatusOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeStore(ops: List<OutboxEntity> = emptyList(), var cursorValue: Long = 0) : SyncStore {
    val outbox = ops.toMutableList()
    val failed = mutableMapOf<Long, String>()
    val pages = mutableListOf<Pair<Int, Long>>()
    var failApply = false

    override suspend fun dueOps(limit: Int, now: Long) = outbox.filter { it.state == "PENDING" && it.nextAttemptAt <= now && it.seq !in failed }.sortedBy { it.seq }.take(limit)
    override suspend fun acknowledge(seqs: List<Long>) { outbox.removeAll { it.seq in seqs } }
    override suspend fun markFailed(seq: Long, code: String) { failed[seq] = code }
    override suspend fun retryLater(seqs: List<Long>, next: Long, code: String?) {
        outbox.replaceAll { if (it.seq in seqs) it.copy(nextAttemptAt = next, attempts = it.attempts + 1, lastCode = code) else it }
    }
    override suspend fun pendingCount() = outbox.count { it.seq !in failed }
    override suspend fun cursor() = cursorValue
    override suspend fun applyPage(changes: List<ChangeDto>, cursor: Long) {
        if (failApply) error("disco lleno")
        pages += changes.size to cursor
        cursorValue = cursor
    }
}

private class FakeRemote(
    val onPush: (PushBody) -> Result<PushResponse> = { b -> Result.success(PushResponse(b.ops.map { OpResultDto(it.opId, "APPLIED", null, 1) })) },
    val pullPages: MutableList<PullResponse> = mutableListOf(PullResponse(emptyList(), 0, false)),
) : SyncRemote {
    val pushes = mutableListOf<PushBody>()
    val pullSince = mutableListOf<Long>()
    override suspend fun push(body: PushBody): Result<PushResponse> { pushes += body; return onPush(body) }
    override suspend fun pull(since: Long, limit: Int): Result<PullResponse> {
        pullSince += since
        return Result.success(pullPages.removeAt(0))
    }
}

private fun op(seq: Long, entity: String = "e$seq") = OutboxEntity(seq = seq, opId = "op$seq", kind = "SALE_UPSERT", entityId = entity, payload = "{}", createdAt = 0)

class SyncEngineTest {
    private val now = { 1_000_000L }

    @Test fun sendsInBatchesOf100AndAcknowledgesEverythingTheServerAccepted() = runTest {
        val store = FakeStore((1L..250L).map { op(it) })
        val remote = FakeRemote(pullPages = mutableListOf(PullResponse(emptyList(), 5, false)))
        val result = SyncEngine(remote, store, now).run()
        assertEquals(listOf(100, 100, 50), remote.pushes.map { it.ops.size })
        assertTrue(store.outbox.isEmpty())
        assertEquals(SyncResult.Done(pushed = 250, rejected = 0, pulled = 0), result)
    }

    @Test fun duplicateAndStaleCountAsDoneButARejectionIsKeptForAttention() = runTest {
        val store = FakeStore(listOf(op(1), op(2), op(3)))
        val remote = FakeRemote(onPush = { Result.success(PushResponse(listOf(OpResultDto("op1", "DUPLICATE"), OpResultDto("op2", "STALE"), OpResultDto("op3", "REJECTED", "PAYMENT_MISMATCH")))) })
        val result = SyncEngine(remote, store, now).run() as SyncResult.Done
        assertEquals(2, result.pushed)
        assertEquals(1, result.rejected)
        assertEquals("PAYMENT_MISMATCH", store.failed[3])
        assertEquals(listOf(3L), store.outbox.map { it.seq })   // la rechazada NO se borra: alguien debe verla
    }

    @Test fun anInternalErrorOnTheServerIsRetriedWithBackoffNotDropped() = runTest {
        val store = FakeStore(listOf(op(1)))
        val remote = FakeRemote(onPush = { Result.success(PushResponse(listOf(OpResultDto("op1", "REJECTED", "INTERNAL_ERROR")))) })
        SyncEngine(remote, store, now).run()
        val kept = store.outbox.single()
        assertEquals(1, kept.attempts)
        assertEquals(now() + 5_000, kept.nextAttemptAt)
        assertTrue(store.failed.isEmpty())
        // Aún no toca reintentar: una segunda vuelta inmediata no la vuelve a enviar.
        remote.pushes.clear()
        remote.pullPages += PullResponse(emptyList(), 0, false)
        SyncEngine(remote, store, now).run()
        assertTrue(remote.pushes.isEmpty())
    }

    @Test fun aDuplicateThatCarriesAnErrorCodeIsARejectionNotASuccess() = runTest {
        // El servidor ya había rechazado esta operación: repetirla devuelve DUPLICATE + el código. Jamás debe borrarse de la cola como "hecha".
        val store = FakeStore(listOf(op(1)))
        val remote = FakeRemote(onPush = { Result.success(PushResponse(listOf(OpResultDto("op1", "DUPLICATE", "PAYMENT_MISMATCH")))) })
        val result = SyncEngine(remote, store, now).run() as SyncResult.Done
        assertEquals(0, result.pushed)
        assertEquals(1, result.rejected)
        assertEquals("PAYMENT_MISMATCH", store.failed[1])
        assertEquals(1, store.outbox.size)
    }

    @Test fun aTicketLockedByAnotherPhoneIsRetriedLaterNotMarkedAsFailed() = runTest {
        val store = FakeStore(listOf(op(1)))
        val remote = FakeRemote(onPush = { Result.success(PushResponse(listOf(OpResultDto("op1", "REJECTED", "SALE_LOCKED")))) })
        val result = SyncEngine(remote, store, now).run() as SyncResult.Done
        assertEquals(0, result.rejected)
        assertTrue(store.failed.isEmpty())
        assertEquals(now() + 5_000, store.outbox.single().nextAttemptAt)
    }

    @Test fun aClosingBlockedByAnotherPhoneOfTheSameRegisterIsRetriedNotMarkedAsFailed() = runTest {
        val store = FakeStore(listOf(op(1)))
        val remote = FakeRemote(onPush = { Result.success(PushResponse(listOf(OpResultDto("op1", "REJECTED", "DEVICES_PENDING")))) })
        val result = SyncEngine(remote, store, now).run() as SyncResult.Done
        assertEquals(0, result.rejected)
        assertTrue(store.failed.isEmpty())
        assertEquals(1, store.outbox.size)
    }

    @Test fun anOperationTheServerDidNotMentionIsRetriedNotLost() = runTest {
        val store = FakeStore(listOf(op(1), op(2)))
        val remote = FakeRemote(onPush = { Result.success(PushResponse(listOf(OpResultDto("op1", "APPLIED")))) })
        SyncEngine(remote, store, now).run()
        assertEquals(listOf(2L), store.outbox.map { it.seq })
    }

    @Test fun withoutConnectionNothingIsLostAndThePullIsSkipped() = runTest {
        val store = FakeStore(listOf(op(1), op(2)))
        val remote = FakeRemote(onPush = { Result.failure(ApiFailure.Offline(java.io.IOException("sin red"))) })
        val result = SyncEngine(remote, store, now).run()
        assertEquals(SyncResult.Offline, result)
        assertEquals(2, store.outbox.size)
        assertTrue(remote.pullSince.isEmpty())
        assertTrue(store.outbox.all { it.attempts == 1 && it.nextAttemptAt > now() })
    }

    @Test fun anUnauthorizedSessionKeepsThePendingQueueIntact() = runTest {
        val store = FakeStore(listOf(op(1)))
        val remote = FakeRemote(onPush = { Result.failure(ApiFailure.Http(401, "UNAUTHENTICATED", "x")) })
        val result = SyncEngine(remote, store, now).run()
        assertEquals(SyncResult.AuthProblem("UNAUTHENTICATED"), result)
        assertEquals(0, store.outbox.single().attempts)    // ni siquiera cuenta como intento: no es culpa de la operación
    }

    @Test fun pullFollowsPagesAndAdvancesTheCursorPageByPage() = runTest {
        val store = FakeStore(cursorValue = 10)
        val change = ChangeDto("product", 11, JsonNull)
        val remote = FakeRemote(pullPages = mutableListOf(
            PullResponse(listOf(change, change), 12, true), PullResponse(listOf(change), 13, false),
        ))
        val result = SyncEngine(remote, store, now).run()
        assertEquals(listOf(10L, 12L), remote.pullSince)
        assertEquals(listOf(2 to 12L, 1 to 13L), store.pages)
        assertEquals(13L, store.cursorValue)
        assertEquals(3, (result as SyncResult.Done).pulled)
    }

    @Test fun ifSavingAPageFailsTheCursorDoesNotAdvance() = runTest {
        val store = FakeStore(cursorValue = 10).apply { failApply = true }
        val remote = FakeRemote(pullPages = mutableListOf(PullResponse(listOf(ChangeDto("product", 11, JsonNull)), 11, false)))
        val failed = runCatching { SyncEngine(remote, store, now).run() }
        assertTrue(failed.isFailure)
        assertEquals(10L, store.cursorValue)
    }

    @Test fun backoffGrowsAndIsCapped() {
        assertEquals(5_000L, SyncEngine.backoffMillis(1))
        assertEquals(10_000L, SyncEngine.backoffMillis(2))
        assertEquals(20_000L, SyncEngine.backoffMillis(3))
        assertEquals(15 * 60_000L, SyncEngine.backoffMillis(50))
    }

    @Test fun aSuspendedBusinessHttp403KeepsEveryQueuedOperation() = runTest {
        val store = FakeStore(listOf(op(1), op(2)))
        val remote = FakeRemote(onPush = { Result.failure(ApiFailure.Http(403, "BUSINESS_SUSPENDED", "suspendido")) })
        val result = SyncEngine(remote, store, now).run()
        assertEquals(SyncResult.Failed("BUSINESS_SUSPENDED"), result)
        assertEquals(SyncStatus.SUSPENDED, syncStatusOf(result))
        assertTrue(store.failed.isEmpty())
        assertEquals(listOf(1L, 2L), store.outbox.map { it.seq })   // nada se descarta
    }

    @Test fun aSuspendedBusinessRejectionPerOperationIsRetriedNotMarkedAsFailed() = runTest {
        val store = FakeStore(listOf(op(1)))
        val remote = FakeRemote(onPush = { Result.success(PushResponse(listOf(OpResultDto("op1", "REJECTED", "BUSINESS_SUSPENDED")))) })
        val result = SyncEngine(remote, store, now).run() as SyncResult.Done
        assertEquals(0, result.rejected)
        assertTrue(store.failed.isEmpty())
        assertEquals(1, store.outbox.size)
    }

    @Test fun suspendedPullFailureAlsoLeavesTheOutboxAlone() = runTest {
        val store = FakeStore(listOf(op(1)))
        val remote = FakeRemote(onPush = { Result.success(PushResponse(listOf(OpResultDto("op1", "APPLIED", null, 1)))) })
        remote.pullPages.clear()
        val failing = object : SyncRemote by remote {
            override suspend fun pull(since: Long, limit: Int): Result<PullResponse> = Result.failure(ApiFailure.Http(403, "BUSINESS_SUSPENDED", "x"))
        }
        assertEquals(SyncResult.Failed("BUSINESS_SUSPENDED"), SyncEngine(failing, store, now).run())
    }
}

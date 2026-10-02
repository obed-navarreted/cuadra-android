package com.cuadra.caja.data.repo

import com.cuadra.caja.data.local.Db
import com.cuadra.caja.data.local.NotificationEntity
import com.cuadra.caja.data.local.OutboxEntity
import com.cuadra.caja.data.session.SessionStore
import java.util.UUID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

/** Bandeja de la persona activa (y del teléfono). Marcar como leída se guarda al instante y se avisa al servidor con la cola de salida. */
class NotificationRepository(
    private val db: Db,
    private val session: SessionStore,
    private val requestSync: () -> Unit,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val json = Json

    @OptIn(ExperimentalCoroutinesApi::class)
    fun inbox(): Flow<List<NotificationEntity>> = session.flow.map { it.memberId }.flatMapLatest { m -> if (m == null) flowOf(emptyList()) else db.notifications().inbox(m) }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun unreadCount(): Flow<Int> = session.flow.map { it.memberId }.flatMapLatest { m -> if (m == null) flowOf(0) else db.notifications().unreadCount(m) }

    suspend fun markRead(id: String) {
        val n = db.notifications().get(id) ?: return
        if (n.readAt != null) return
        db.inTransaction {
            db.notifications().markRead(id, now())
            db.outbox().insert(OutboxEntity(opId = UUID.randomUUID().toString(), kind = "NOTIFICATION_READ", entityId = id, payload = "{}", createdAt = now()))
        }
        requestSync()
    }

    suspend fun markAllRead() {
        val member = session.current().memberId ?: return
        val unread = db.notifications().unread(member)
        if (unread.isEmpty()) return
        db.inTransaction {
            for (n in unread) {
                db.notifications().markRead(n.id, now())
                db.outbox().insert(OutboxEntity(opId = UUID.randomUUID().toString(), kind = "NOTIFICATION_READ", entityId = n.id, payload = "{}", createdAt = now()))
            }
        }
        requestSync()
    }
}

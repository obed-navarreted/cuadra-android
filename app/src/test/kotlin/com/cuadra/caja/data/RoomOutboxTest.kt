package com.cuadra.caja.data

import androidx.room.Room
import com.cuadra.caja.data.local.BusinessEntity
import com.cuadra.caja.data.local.CuadraDatabase
import com.cuadra.caja.data.local.OutboxEntity
import com.cuadra.caja.data.local.OutboxStamp
import com.cuadra.caja.data.local.ProductEntity
import com.cuadra.caja.data.local.SyncStateEntity
import com.cuadra.caja.data.remote.ChangeDto
import com.cuadra.caja.data.repo.LocalBusinessData
import com.cuadra.caja.data.sync.RoomSyncStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * La cola de salida y la bajada con la base REAL (Room en memoria): una operación rechazada no congela su registro, cada fila lleva quién la hizo y de
 * qué negocio es, «Reintentar» y «Descartar» hacen lo que dicen, y cambiar de negocio no mezcla ni pierde datos.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class RoomOutboxTest {
    private lateinit var db: CuadraDatabase

    @Before fun open() {
        db = Room.inMemoryDatabaseBuilder(org.robolectric.RuntimeEnvironment.getApplication(), CuadraDatabase::class.java).allowMainThreadQueries().build()
        OutboxStamp.memberId = null
        OutboxStamp.businessId = null
    }

    @After fun close() {
        db.close()
        OutboxStamp.memberId = null
        OutboxStamp.businessId = null
    }

    private fun op(entity: String, kind: String = "PRODUCT_UPSERT", business: String? = "b1") =
        OutboxEntity(opId = "op-$entity-${System.nanoTime()}", kind = kind, entityId = entity, payload = "{}", createdAt = 1, businessId = business)

    private fun product(id: String, price: Long, rev: Long) = ProductEntity(id, null, null, "Cuajada", null, null, "UNIT", "FIXED", price, null, false, null, null, false, 0, null, true, rev)

    private fun productChange(id: String, price: Long, rev: Long) = ChangeDto(
        "product", rev,
        Json.parseToJsonElement("""{"id":"$id","name":"Cuajada","unit":"UNIT","pricing":"FIXED","priceMinor":$price,"isQuick":false,"trackStock":false,"stockMilli":0,"active":true,"updatedAt":"2026-09-29T10:00:00Z","rev":$rev}"""),
    )

    @Test fun aRejectedOperationNoLongerFreezesItsRecordSoTheServerVersionIsAcceptedAgain() = runTest {
        db.products().upsert(product("p1", 2500, 1))
        val seq = db.outbox().insert(op("p1"))
        val store = RoomSyncStore(db, "b1")
        // Pendiente: gana lo del teléfono.
        store.applyPage(listOf(productChange("p1", 3000, 5)), 5)
        assertEquals(2500L, db.products().get("p1")!!.priceMinor)
        // Rechazada: ya no cuenta como cambio local pendiente y la versión del servidor entra.
        store.markFailed(seq, "FORBIDDEN", null)
        assertEquals(0, db.outbox().countFor("p1"))
        store.applyPage(listOf(productChange("p1", 3000, 6)), 6)
        assertEquals(3000L, db.products().get("p1")!!.priceMinor)
        // Sigue visible en «Requiere atención» y no se envía.
        assertEquals(1, db.outbox().failed().first().size)
        assertTrue(store.dueOps(10, Long.MAX_VALUE).isEmpty())
        // Lo mismo con una aplicada para revisar (venta guardada aparte).
        val review = db.outbox().insert(op("p2"))
        store.markReview(review, "SALE_CONFLICT_COPY", """{"copySaleId":"x"}""")
        assertEquals(0, db.outbox().countFor("p2"))
        assertEquals(2, db.outbox().failedCount().first())
        assertEquals(0, db.outbox().pendingCountNow())
    }

    @Test fun everyQueuedOperationIsStampedWithWhoDidItAndItsBusiness() = runTest {
        OutboxStamp.memberId = "kevin"
        OutboxStamp.businessId = "b1"
        val first = db.outbox().insert(op("e1", business = null))
        OutboxStamp.memberId = "lucia"   // cambió de cajero
        val second = db.outbox().insert(op("e2", business = null))
        assertEquals("kevin", db.outbox().get(first)!!.memberId)
        assertEquals("lucia", db.outbox().get(second)!!.memberId)
        assertEquals("b1", db.outbox().get(second)!!.businessId)
    }

    @Test fun onlyTheCurrentBusinessOperationsAreSentAndWithoutAnActiveMemberOnlyAttributedOnes() = runTest {
        db.outbox().insert(op("a", business = "b1"))
        db.outbox().insert(op("b", business = "b2"))
        db.outbox().insert(op("legacy", business = null))
        db.outbox().insert(op("kevin", business = "b1").copy(memberId = "kevin"))
        assertEquals(listOf("a", "legacy", "kevin"), RoomSyncStore(db, "b1").dueOps(10, Long.MAX_VALUE).map { it.entityId })
        assertEquals(listOf("b", "legacy"), RoomSyncStore(db, "b2").dueOps(10, Long.MAX_VALUE).map { it.entityId })
        // En la pantalla de PIN (sin persona activa) solo sale lo que dice quién lo hizo.
        assertEquals(listOf("kevin"), RoomSyncStore(db, "b1") { false }.dueOps(10, Long.MAX_VALUE).map { it.entityId })
    }

    @Test fun theCursorBelongsToItsBusiness() = runTest {
        db.directory().setCursor(SyncStateEntity(cursor = 900, businessId = "b1"))
        assertEquals(900L, RoomSyncStore(db, "b1").cursor())
        assertEquals(0L, RoomSyncStore(db, "b2").cursor())
        RoomSyncStore(db, "b2").applyPage(emptyList(), 15)
        assertEquals("b2", db.directory().syncState()!!.businessId)
        assertEquals(15L, RoomSyncStore(db, "b2").cursor())
    }

    @Test fun retryRequeuesWithANewOperationIdAndDiscardKeepsAnAuditLine() = runTest {
        val seq = db.outbox().insert(op("s1", kind = "SALE_UPSERT").copy(memberId = "kevin"))
        db.outbox().markFailed(seq, "CREDIT_LIMIT_EXCEEDED", """{"limitMinor":1}""")
        val oldOpId = db.outbox().get(seq)!!.opId
        assertEquals(1, db.outbox().requeue(seq, "op-new"))
        val again = db.outbox().get(seq)!!
        assertEquals("PENDING", again.state)
        assertEquals("op-new", again.opId)
        assertNotEquals(oldOpId, again.opId)
        assertNull(again.lastCode)
        assertEquals(1, db.outbox().pendingCountNow())
        // Solo una rechazada se reintenta así (una pendiente no cambia de id: podría estar ya aplicada).
        assertEquals(0, db.outbox().requeue(seq, "op-other"))

        db.outbox().markFailed(seq, "CREDIT_LIMIT_EXCEEDED", null)
        assertTrue(db.outbox().discard(seq, 99, "owner", "Doña Ana"))
        assertNull(db.outbox().get(seq))
        val line = db.outbox().discarded().first().single()
        assertEquals("SALE_UPSERT", line.kind)
        assertEquals("kevin", line.memberId)
        assertEquals("CREDIT_LIMIT_EXCEEDED", line.code)
        assertEquals("Doña Ana", line.discardedByName)
    }

    @Test fun switchingBusinessIsBlockedWhileTheOldOneHasUnsentOperationsAndWipesItOtherwise() = runTest {
        db.directory().upsertBusiness(BusinessEntity("b1", "La Esquina", "NI", "NIO", "America/Managua", "es", "02:00", "OFF", "{}", "[]", false))
        db.directory().setCursor(SyncStateEntity(cursor = 900, businessId = "b1"))
        db.products().upsert(product("p1", 2500, 1))
        val local = LocalBusinessData(db)
        val seq = db.outbox().insert(op("p1", business = "b1"))
        // Mismo negocio: nada que hacer.
        assertNull(local.prepareFor("b1"))
        // Otro negocio con algo sin enviar: bloqueado y SIN borrar nada.
        val blocked = local.prepareFor("b2")
        assertNotNull(blocked)
        assertEquals(1, blocked!!.unsent)
        assertEquals("La Esquina", blocked.businessName)
        assertNotNull(db.products().get("p1"))
        // Una rechazada sin resolver también bloquea (se resuelve en «Requiere atención»).
        db.outbox().markFailed(seq, "FORBIDDEN", null)
        assertNotNull(local.prepareFor("b2"))
        assertNotNull(local.blockerForAnyOther())
        // Ya enviado todo: se borran los datos del negocio anterior y su cursor.
        db.outbox().delete(listOf(seq))
        assertNull(local.prepareFor("b2"))
        assertNull(db.products().get("p1"))
        assertNull(db.directory().syncState())
        assertNull(local.currentBusinessId())
    }
}

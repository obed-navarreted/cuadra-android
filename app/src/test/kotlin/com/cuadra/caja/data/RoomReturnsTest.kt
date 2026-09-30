package com.cuadra.caja.data

import androidx.room.Room
import com.cuadra.caja.data.local.CuadraDatabase
import com.cuadra.caja.data.local.OutboxStamp
import com.cuadra.caja.data.local.SaleEntity
import com.cuadra.caja.data.local.SaleItemEntity
import com.cuadra.caja.data.local.SaleReturnEntity
import com.cuadra.caja.data.remote.ChangeDto
import com.cuadra.caja.data.remote.ReturnItemDto
import com.cuadra.caja.data.repo.SaleReturns
import com.cuadra.caja.data.sync.RoomSyncStore
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Devoluciones en el teléfono con la base REAL: lo devuelto por línea sale de las devoluciones (confirmadas + pendientes), una hecha sin conexión no se
 * pierde cuando el servidor todavía no la conoce, y cuando la confirma queda una sola (con los montos del servidor).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class RoomReturnsTest {
    private lateinit var db: CuadraDatabase

    @Before fun open() {
        db = Room.inMemoryDatabaseBuilder(org.robolectric.RuntimeEnvironment.getApplication(), CuadraDatabase::class.java).allowMainThreadQueries().build()
        OutboxStamp.memberId = null
        OutboxStamp.businessId = null
    }

    @After fun close() = db.close()

    private suspend fun seedSale() {
        db.sales().upsert(SaleEntity("s1", "COMPLETED", null, null, 35_000, 3_500, 31_500, "k", "Kevin", "Kevin", 1_000, null, null, null, null, 1_000, 1_000, 7, completedByMemberId = "k"))
        db.sales().insertItems(listOf(SaleItemEntity("s1", "q", null, null, "Queso", null, 10_000, null, 3_000, 0, 0), SaleItemEntity("s1", "c", null, null, "Crema", null, 5_000, null, 1_000, 0, 1)))
    }

    private fun pending(id: String, qty: Long, amount: Long) = SaleReturnEntity(
        id, "s1", "venía roto", "CASH", amount, "Kevin", 2_000, SaleReturns.encodeItems(listOf(ReturnItemDto(saleItemId = "q", name = "Queso", quantityMilli = qty, amountMinor = amount))), "[]", 0,
    )

    private fun saleChange(rev: Long, returnsJson: String, returnedMinor: Long, returnedMilli: Long) = ChangeDto("sale", rev, Json.parseToJsonElement("""
        {"id":"s1","status":"COMPLETED","subtotalMinor":35000,"discountMinor":3500,"totalMinor":31500,"completedAt":"2026-09-20T18:00:00Z","createdAt":"2026-09-20T18:00:00Z",
         "updatedAt":"2026-09-20T18:00:00Z","rev":$rev,"completedBy":{"id":"k","name":"Kevin"},"returnedMinor":$returnedMinor,
         "items":[{"id":"q","name":"Queso","unitPriceMinor":10000,"quantityMilli":3000,"discountMinor":0,"lineTotalMinor":30000,"returnedMilli":$returnedMilli},
                  {"id":"c","name":"Crema","unitPriceMinor":5000,"quantityMilli":1000,"discountMinor":0,"lineTotalMinor":5000}],
         "returns":$returnsJson}""".trimIndent()))

    @Test fun anOfflineReturnCountsAtOnceSurvivesAPullAndIsReplacedByTheConfirmedOne() = runTest {
        seedSale()
        db.sales().upsertReturn(pending("r1", 1_000, 9_000))
        SaleReturns.recomputeReturned(db.sales(), "s1")
        assertEquals(1_000L, db.sales().items("s1").first { it.id == "q" }.returnedMilli)
        assertEquals(9_000L, db.sales().get("s1")!!.returnedMinor)

        // El servidor todavía no la conoce: la venta que baja no la trae, pero la pendiente sigue contando.
        val store = RoomSyncStore(db, "b1")
        store.applyPage(listOf(saleChange(8, "[]", 0, 0)), 8)
        assertEquals(1, db.sales().returnsFor("s1").size)
        assertEquals(1_000L, db.sales().items("s1").first { it.id == "q" }.returnedMilli)

        // Ya confirmada (mismo id): queda UNA, con lo del servidor.
        val confirmed = """[{"id":"r1","saleId":"s1","reason":"venía roto","refundMethod":"CASH","totalMinor":9000,"occurredAt":"2026-09-21T10:00:00Z",
            "items":[{"id":"i1","saleItemId":"q","name":"Queso","quantityMilli":1000,"amountMinor":9000}],"refunds":[{"method":"CASH","amountMinor":9000}]}]"""
        store.applyPage(listOf(saleChange(9, confirmed, 9_000, 1_000)), 9)
        val rows = db.sales().returnsFor("s1")
        assertEquals(1, rows.size)
        assertEquals(9L, rows.single().rev)
        assertEquals(9_000L, db.sales().get("s1")!!.returnedMinor)
        assertEquals(listOf("CASH" to 9_000L), SaleReturns.view(rows.single()).refunds)
    }

    @Test fun discardingARejectedReturnGivesTheQuantityBack() = runTest {
        seedSale()
        db.sales().upsertReturn(pending("r2", 2_000, 18_000))
        SaleReturns.recomputeReturned(db.sales(), "s1")
        assertEquals(2_000L, db.sales().items("s1").first { it.id == "q" }.returnedMilli)
        assertEquals(1, db.sales().deleteUnconfirmedReturn("r2"))
        SaleReturns.recomputeReturned(db.sales(), "s1")
        assertEquals(0L, db.sales().items("s1").first { it.id == "q" }.returnedMilli)
        assertEquals(0L, db.sales().get("s1")!!.returnedMinor)
    }

    @Test fun theLastOwnCompletedSaleIsFound() = runTest {
        seedSale()
        db.sales().upsert(db.sales().get("s1")!!.copy(id = "s2", completedAt = 5_000))
        assertEquals("s2", db.sales().lastCompletedBy("k")?.id)
        assertEquals(null, db.sales().lastCompletedBy("otra"))
    }
}

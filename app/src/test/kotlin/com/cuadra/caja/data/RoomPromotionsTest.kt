package com.cuadra.caja.data

import androidx.room.Room
import com.cuadra.caja.data.local.CuadraDatabase
import com.cuadra.caja.data.local.Db
import com.cuadra.caja.data.local.OutboxStamp
import com.cuadra.caja.data.local.ProductEntity
import com.cuadra.caja.data.local.SaleEntity
import com.cuadra.caja.data.remote.ChangeDto
import com.cuadra.caja.data.repo.PromotionRepository
import com.cuadra.caja.data.repo.ResumeResult
import com.cuadra.caja.data.repo.SaleRepository
import com.cuadra.caja.data.session.SessionStore
import com.cuadra.caja.data.sync.RoomSyncStore
import com.cuadra.caja.domain.Cart
import com.cuadra.caja.domain.PaymentPlan
import com.cuadra.caja.domain.PromotionEngine
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Promociones en el teléfono con la base REAL: llegan por la sincronización (y se quitan si se borran), el editor las encola, una venta guarda lo que aplicó
 * (con el descuento en las líneas y en lo que se envía), retomar una cuenta no duplica el descuento, el resumen suma «Descuentos por promociones» y
 * «Vendido hoy» de un cajero cuenta solo lo suyo.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class RoomPromotionsTest {
    private lateinit var room: CuadraDatabase
    private lateinit var db: Db
    private val json = Json { ignoreUnknownKeys = true }

    @Before fun open() {
        room = Room.inMemoryDatabaseBuilder(org.robolectric.RuntimeEnvironment.getApplication(), CuadraDatabase::class.java).allowMainThreadQueries().build()
        db = room
        OutboxStamp.memberId = null
        OutboxStamp.businessId = null
    }

    @After fun close() = room.close()

    private fun promoChange(rev: Long, active: Boolean = true, deleted: Boolean = false, products: String = "\"tona\",\"victoria\"") = ChangeDto("promotion", rev, Json.parseToJsonElement(
        """{"id":"pr1","name":"Cerveza 3 por C$ 100","productIds":[$products],"quantity":3,"priceMinor":10000,"active":$active,"deleted":$deleted,"rev":$rev}""",
    ))

    @Test fun promotionsArriveBySyncChangeAndDisappearWhenDeleted() = runTest {
        val store = RoomSyncStore(db, "b1")
        val repo = PromotionRepository(db, {})
        store.applyPage(listOf(promoChange(5)), 5)
        val r = repo.rules().first().single()
        assertEquals(setOf("tona", "victoria"), r.productIds)
        assertEquals(10_000, r.priceMinor)
        // Pausada en el servidor (la web): el teléfono la recibe y la caja deja de aplicarla.
        store.applyPage(listOf(promoChange(6, active = false, products = "\"tona\"")), 6)
        val paused = repo.rules().first().single()
        assertEquals(false, paused.active)
        assertEquals(setOf("tona"), paused.productIds)
        store.applyPage(listOf(promoChange(7, deleted = true)), 7)
        assertTrue(repo.rules().first().isEmpty())
    }

    @Test fun aLocalChangeIsQueuedAndWinsUntilItIsSent() = runTest {
        val repo = PromotionRepository(db, {})
        repo.save("pr1", "Cerveza", listOf("tona"), 3, 10_000, true, null, LocalDate.of(2026, 10, 31))
        val op = db.outbox().allOps().single()
        assertEquals("PROMOTION_UPSERT", op.kind)
        val payload = json.parseToJsonElement(op.payload).jsonObject
        assertEquals("2026-10-31", payload["endsOn"]!!.jsonPrimitive.content)
        // Lo que baja del servidor mientras tanto no pisa el cambio sin enviar.
        RoomSyncStore(db, "b1").applyPage(listOf(promoChange(9, active = false)), 9)
        assertEquals(true, repo.get("pr1")!!.active)
        repo.delete("pr1")
        assertNull(repo.get("pr1"))
        assertEquals(listOf("PROMOTION_UPSERT", "PROMOTION_DELETE"), db.outbox().allOps().map { it.kind })
    }

    private fun product(id: String, price: Long) = ProductEntity(id, null, null, id, null, null, "UNIT", "FIXED", price, null, false, null, null, false, 0, null, true, 1)

    @Test fun aSaleStoresWhatItAppliedSendsItAndResumingDoesNotDoubleTheDiscount() = runTest {
        val app = org.robolectric.RuntimeEnvironment.getApplication()
        val session = SessionStore(app)
        val api = com.cuadra.caja.data.remote.ApiFactory.create("http://localhost:1/") { com.cuadra.caja.data.session.Session() }
        val sales = SaleRepository(db, api, session, {})
        db.products().upsert(product("tona", 4_500))
        val rule = com.cuadra.caja.domain.PromotionRule("pr1", "Cerveza 3 por C$ 100", 3, 10_000, setOf("tona"))
        val cart = Cart().addProduct("tona", null, "Toña", null, 4_500, null, 7_000, newId = { "l1" })
        val priced = PromotionEngine.price(cart, listOf(rule), LocalDate.of(2026, 10, 1)) { "FIXED" }
        assertEquals(24_500, priced.totalMinor)

        val id = sales.complete(priced.cart, PaymentPlan.cash(priced.totalMinor), "s1", null, priced.promo.applied)
        val view = sales.view(id)!!
        assertEquals(24_500, view.totalMinor)
        assertEquals(7_000, view.promotionDiscountMinor)
        assertEquals(6L, view.promotions.single().units)
        // Lo que viaja al servidor: la línea con el descuento y la promoción aplicada (el servidor no recalcula).
        val payload = json.parseToJsonElement(db.outbox().allOps().single().payload).jsonObject
        assertEquals(7_000, payload["items"]!!.jsonArray[0].jsonObject["discountMinor"]!!.jsonPrimitive.long)
        assertEquals("pr1", payload["promotions"]!!.jsonArray[0].jsonObject["promotionId"]!!.jsonPrimitive.content)
        assertEquals(24_500, payload["payments"]!!.jsonArray[0].jsonObject["amountMinor"]!!.jsonPrimitive.long)
        // «Descuentos por promociones» en el resumen del teléfono.
        assertEquals(7_000L, db.reports().promotionDiscounts(0, Long.MAX_VALUE).first())

        // Una cuenta apartada con promoción: al retomarla las líneas vuelven SIN descuento (la caja lo calcula otra vez con las de hoy).
        val parked = sales.park(priced.cart, "Mesa 2", "s2", priced.promo.applied)
        val resumed = sales.resume(parked) as ResumeResult.Ok
        assertEquals(0L, resumed.sale.cart.lines.single().discountMinor)
        assertEquals(24_500, PromotionEngine.price(resumed.sale.cart, listOf(rule), LocalDate.of(2026, 10, 1)) { "FIXED" }.totalMinor)
    }

    @Test fun soldTodayOfACashierCountsOnlyTheirSales() = runTest {
        fun sale(id: String, by: String, total: Long) = SaleEntity(id, "COMPLETED", null, null, total, 0, total, by, by, by, 1_000, null, null, null, null, 1_000, 1_000, 1, completedByMemberId = by)
        db.sales().upsert(sale("a", "kevin", 10_000))
        db.sales().upsert(sale("b", "ana", 5_000))
        db.sales().upsert(sale("c", "kevin", 2_500))
        assertEquals(12_500L, db.sales().dayTotalsBy(0, 10_000, "kevin").first().total)
        assertEquals(17_500L, db.sales().dayTotals(0, 10_000).first().total)
    }

    @Test fun aSaleFromTheServerBringsItsPromotions() = runTest {
        val change = ChangeDto("sale", 3, Json.parseToJsonElement("""
            {"id":"s9","status":"COMPLETED","subtotalMinor":24500,"discountMinor":0,"totalMinor":24500,"completedAt":"2026-10-01T18:00:00Z","createdAt":"2026-10-01T18:00:00Z",
             "updatedAt":"2026-10-01T18:00:00Z","rev":3,"completedBy":{"id":"k","name":"Kevin"},
             "items":[{"id":"l1","productId":"tona","name":"Toña","unitPriceMinor":4500,"quantityMilli":7000,"discountMinor":7000,"lineTotalMinor":24500}],
             "promotions":[{"promotionId":"pr1","name":"Cerveza 3 por C$ 100","quantity":3,"priceMinor":10000,"units":6,"discountMinor":7000}],"promotionDiscountMinor":7000}""".trimIndent()))
        RoomSyncStore(db, "b1").applyPage(listOf(change), 3)
        val p = db.sales().promotions("s9").single()
        assertEquals(7_000, p.discountMinor)
        assertEquals(6L, p.units)
    }
}

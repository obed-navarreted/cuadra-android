package com.cuadra.caja.data

import androidx.room.Room
import com.cuadra.caja.data.local.CuadraDatabase
import com.cuadra.caja.data.local.Db
import com.cuadra.caja.data.local.OutboxStamp
import com.cuadra.caja.data.local.SaleEntity
import com.cuadra.caja.data.repo.SaleRepository
import com.cuadra.caja.data.session.SessionStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * docs/adr/0017 en el teléfono con la base REAL: el teléfono guarda el negocio completo (el cursor es suyo), pero un cajero solo lee sus ventas de la última hora
 * (lista, detalle y total del encabezado); al pasar a un admin en el mismo teléfono se ve todo otra vez, sin volver a bajar nada.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class RoomCashierVisibilityTest {
    private lateinit var room: CuadraDatabase
    private lateinit var db: Db
    private val minute = 60_000L
    private var clock = 100_000_000_000L

    @Before fun open() {
        room = Room.inMemoryDatabaseBuilder(org.robolectric.RuntimeEnvironment.getApplication(), CuadraDatabase::class.java).allowMainThreadQueries().build()
        db = room
        OutboxStamp.memberId = null
        OutboxStamp.businessId = null
    }

    @After fun close() = room.close()

    private fun sale(id: String, by: String, agoMin: Long, total: Long = 1_000, status: String = "COMPLETED") = SaleEntity(
        id, status, null, null, total, 0, total, by, by, by, if (status == "PARKED") null else clock - agoMin * minute, null, null, null, null, clock - agoMin * minute, clock - agoMin * minute, 1,
        completedByMemberId = if (status == "PARKED") null else by,
    )

    private fun repo(session: SessionStore) =
        SaleRepository(db, com.cuadra.caja.data.remote.ApiFactory.create("http://localhost:1/") { com.cuadra.caja.data.session.Session() }, session, {}, now = { clock })

    @Test fun aCashierOnlyReadsHisOwnSalesOfTheLastHourAndAnAdminOnTheSamePhoneSeesAll() = runTest {
        val session = SessionStore(org.robolectric.RuntimeEnvironment.getApplication())
        val sales = repo(session)
        db.sales().upsert(sale("mine30", "kevin", 30, 500))
        db.sales().upsert(sale("mine120", "kevin", 120))
        db.sales().upsert(sale("yesterday", "kevin", 24 * 60 + 5))
        db.sales().upsert(sale("hers", "lucia", 10))
        db.sales().upsert(sale("parked", "lucia", 300, status = "PARKED"))

        session.setActiveMember("kevin", "Kevin", "CASHIER")
        assertEquals(listOf("mine30"), sales.recent(100).first().map { it.id })
        assertNotNull(sales.view("mine30"))
        assertNull("2 h: no se alcanza ni por id", sales.view("mine120"))
        assertNull("de otra persona", sales.view("hers"))
        assertNull("de ayer", sales.view("yesterday"))
        assertEquals(500L, sales.lastHourBy("kevin").first().total)

        // Pasa el tiempo: a los 60:01 la de hace 30 minutos todavía no; 31 minutos después, sí sale.
        clock += 31 * minute
        assertEquals(emptyList<String>(), sales.recent(100).first().map { it.id })
        assertEquals(0L, sales.lastHourBy("kevin").first().total)

        // Mismo teléfono, ahora con la admin: todo está ahí (lo del teléfono nunca se filtró al guardarlo).
        session.setActiveMember("ana", "Ana", "ADMIN")
        assertEquals(setOf("mine30", "mine120", "yesterday", "hers"), sales.recent(100).first().map { it.id }.toSet())
        assertNotNull(sales.view("yesterday"))
        assertNotNull(sales.view("hers"))

        // Y de vuelta con el cajero: otra vez solo lo suyo reciente.
        clock -= 31 * minute
        session.setActiveMember("kevin", "Kevin", "CASHIER")
        assertEquals(listOf("mine30"), sales.recent(100).first().map { it.id })
    }
}

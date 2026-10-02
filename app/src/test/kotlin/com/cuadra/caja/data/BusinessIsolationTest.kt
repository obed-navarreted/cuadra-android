package com.cuadra.caja.data

import android.content.Context
import at.favre.lib.crypto.bcrypt.BCrypt
import com.cuadra.caja.data.local.BusinessDatabases
import com.cuadra.caja.data.local.BusinessEntity
import com.cuadra.caja.data.local.CuadraDatabase
import com.cuadra.caja.data.local.LegacyDatabase
import com.cuadra.caja.data.local.MemberEntity
import com.cuadra.caja.data.local.OutboxEntity
import com.cuadra.caja.data.local.OutboxStamp
import com.cuadra.caja.data.local.SaleEntity
import com.cuadra.caja.data.local.SyncStateEntity
import com.cuadra.caja.data.repo.matchPin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * ADR 0014: una base por negocio. Dos negocios en el MISMO teléfono (mismo PIN, misma persona dueña con dos cuentas…): cerrar sesión, entrar con otra
 * cuenta y volver nunca deja ver filas del otro negocio, y la cola sin enviar de cada uno se conserva y no se mezcla.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class BusinessIsolationTest {
    private lateinit var context: Context
    private lateinit var dbs: BusinessDatabases

    @Before fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.getDatabasePath("x").parentFile?.listFiles()?.forEach { it.delete() }
        OutboxStamp.memberId = null
        OutboxStamp.businessId = null
        // Inmediato: la limpieza al cambiar de negocio corre en el acto y la prueba es determinista.
        dbs = BusinessDatabases(context, CoroutineScope(SupervisorJob() + Dispatchers.Unconfined), io = Dispatchers.Unconfined)
    }

    @After fun tearDown() {
        dbs.closeAll()
        OutboxStamp.businessId = null
    }

    /** La limpieza al cambiar de negocio es asíncrona (Room usa su propio hilo): se espera a que termine. */
    private fun waitUntil(cond: () -> Boolean) {
        val end = System.currentTimeMillis() + 5_000
        while (!cond() && System.currentTimeMillis() < end) Thread.sleep(20)
    }

    private val pin = "00000"
    private val hash = BCrypt.withDefaults().hashToString(4, pin.toCharArray())

    private fun member(id: String, name: String, role: String = "OWNER") = MemberEntity(id, name, role, "ACTIVE", true, true, false, null, hash)

    private fun sale(id: String, total: Long) = SaleEntity(id, "COMPLETED", null, null, total, 0, total, "mA", "Obed Navarrete", "Obed Navarrete", 1_000, null, null, null, null, 1_000, 1_000, 7)

    private fun business(id: String, name: String) = BusinessEntity(id, name, "NI", "NIO", "America/Managua", "es", "02:00", "OFF", "{}", "[]", false)

    private fun op(entity: String, business: String?) =
        OutboxEntity(opId = "op-$entity", kind = "SALE_UPSERT", entityId = entity, payload = "{}", createdAt = 1, businessId = business, memberId = "m")

    private suspend fun fill(id: String, name: String, memberId: String, saleTotal: Long) {
        OutboxStamp.businessId = id
        dbs.select(id)
        dbs.directory().upsertBusiness(business(id, name))
        dbs.directory().upsertMembers(listOf(member(memberId, "Dueño de $name")))
        dbs.sales().upsert(sale("sale-$id", saleTotal))
        dbs.directory().setCursor(SyncStateEntity(cursor = 50, businessId = id))
    }

    @Test fun businessBSeesNothingOfBusinessAAndAIsIntactWhenYouComeBack() = runTest {
        fill("A", "Quesería", "mA", 126_500)
        // Cierra sesión y entra otra cuenta: nada de A es alcanzable.
        dbs.select(null)
        assertTrue(dbs.sales().recent(100).first().isEmpty())
        dbs.select("B")
        assertTrue("sales", dbs.sales().recent(100).first().isEmpty())
        assertTrue("members", dbs.directory().activeMembers().first().isEmpty())
        assertNull("business row", dbs.directory().businessNow())
        // Lo que B guarda es de B.
        fill("B", "Pulpería", "mB", 2_000)
        assertEquals(listOf("sale-B"), dbs.sales().recent(100).first().map { it.id })
        assertEquals(listOf("mB"), dbs.directory().activeMembers().first().map { it.id })
        // A sin nada sin enviar: su base se borró al cambiar; al volver empieza de cero y se vuelve a bajar del servidor.
        waitUntil { !dbs.exists("A") }
        assertFalse(dbs.exists("A"))
        dbs.select("A")
        assertTrue(dbs.sales().recent(100).first().isEmpty())
        assertEquals(0L, dbs.directory().syncState()!!.cursor)
        assertEquals("A", dbs.directory().syncState()!!.businessId)
    }

    @Test fun theSamePinInTwoBusinessesIdentifiesOnlyTheMemberOfTheCurrentBusiness() = runTest {
        fill("A", "Quesería", "mA", 100)
        dbs.outbox().insertAdopted(op("x", "A"))   // sin cola, la base de A se borraría al pasar a B
        fill("B", "Pulpería", "mB", 200)
        val candidates = dbs.directory().activeMembers().first().filter { it.pinHash != null }
        assertEquals(listOf("mB"), candidates.map { it.id })
        assertEquals("mB", matchPin(pin, candidates.map { it.id to it.pinHash!! }))
        // Y al volver a A (conservada porque tiene cola) identifica a la de A, no a la de B.
        dbs.select("A")
        val inA = dbs.directory().activeMembers().first().filter { it.pinHash != null }
        assertEquals(listOf("mA"), inA.map { it.id })
        assertEquals("mA", matchPin(pin, inA.map { it.id to it.pinHash!! }))
    }

    @Test fun eachBusinessKeepsItsOwnUnsentQueueAndNothingIsMixedOrLost() = runTest {
        fill("A", "Quesería", "mA", 100)
        dbs.outbox().insertAdopted(op("a1", "A"))
        dbs.select(null)
        assertTrue("con algo sin enviar la base se conserva", dbs.exists("A"))
        fill("B", "Pulpería", "mB", 200)
        dbs.outbox().insertAdopted(op("b1", "B"))
        assertEquals(listOf("op-b1"), dbs.outbox().allOps().map { it.opId })
        dbs.select("A")
        assertEquals(listOf("op-a1"), dbs.outbox().allOps().map { it.opId })
        // Lo suyo sigue ahí: la venta de A también.
        assertEquals(listOf("sale-A"), dbs.sales().recent(100).first().map { it.id })
        // Y una base limpia sí se libera.
        dbs.select(null)
        dbs.select("B")
        assertTrue(dbs.exists("B"))
        dbs.outbox().delete(dbs.outbox().allOps().map { it.seq })
        dbs.select(null)
        waitUntil { !dbs.exists("B") }
        assertFalse(dbs.exists("B"))
    }

    @Test fun aDatabaseThatClaimsToBeOfAnotherBusinessIsDiscardedAndItsForeignQueueRescued() = runTest {
        // Un archivo de B que por error tiene datos de A (cursor de A, una fila de cola de A y una de B).
        val file = BusinessDatabases.fileName("B")
        val raw = CuadraDatabase.create(context, file)
        raw.directory().setCursor(SyncStateEntity(cursor = 999, businessId = "A"))
        raw.directory().upsertBusiness(business("A", "Quesería"))
        raw.sales().upsert(sale("sale-A", 126_500))
        raw.outbox().insertAdopted(op("a1", "A"))
        raw.outbox().insertAdopted(op("b1", "B"))
        raw.close()
        dbs.select("B")
        assertTrue(dbs.sales().recent(100).first().isEmpty())
        assertNull(dbs.directory().businessNow())
        assertEquals("B", dbs.directory().syncState()!!.businessId)
        assertEquals(0L, dbs.directory().syncState()!!.cursor)
        assertEquals(listOf("op-b1"), dbs.outbox().allOps().map { it.opId })
        // La fila de A fue a la base de A.
        assertEquals(listOf("op-a1"), dbs.forBusiness("A").outbox().allOps().map { it.opId })
    }

    @Test fun theLegacySingleDatabaseWithMixedBusinessesIsDistrustedOnlyItsQueueSurvivesInEachBusinessDatabase() = runTest {
        val legacy = CuadraDatabase.create(context, CuadraDatabase.LEGACY_FILE)
        // Datos mezclados de dos negocios (el error del dueño) y colas de los dos, más una fila vieja sin negocio.
        legacy.directory().upsertBusiness(business("RD", "RD"))
        legacy.directory().upsertMembers(listOf(member("mAR", "Obed Navarrete"), member("mRD", "Rifa Díaz")))
        legacy.sales().upsert(sale("sale-AR", 126_500))
        legacy.directory().setCursor(SyncStateEntity(cursor = 77, businessId = "AR"))
        legacy.outbox().insertAdopted(op("ar1", "AR"))
        legacy.outbox().insertAdopted(op("rd1", "RD"))
        legacy.outbox().insertAdopted(op("old", null))
        legacy.close()

        val r = LegacyDatabase.migrate(context, dbs, sessionBusinessId = "RD")
        assertTrue(r.found); assertTrue(r.deleted)
        assertEquals(3, r.moved)
        assertFalse(context.getDatabasePath(CuadraDatabase.LEGACY_FILE).exists())

        dbs.select("RD")
        assertTrue("sales", dbs.sales().recent(100).first().isEmpty())
        assertTrue("members", dbs.directory().activeMembers().first().isEmpty())
        assertEquals(setOf("op-rd1", "op-old"), dbs.outbox().allOps().map { it.opId }.toSet())
        assertEquals(setOf("RD"), dbs.outbox().allOps().mapNotNull { it.businessId }.toSet())
        assertEquals(listOf("op-ar1"), dbs.forBusiness("AR").outbox().allOps().map { it.opId })
        // Sin el archivo viejo no se hace nada más.
        assertFalse(LegacyDatabase.migrate(context, dbs, "RD").found)
    }

    @Test fun legacyOpsWithoutBusinessAndWithoutALinkedBusinessAreDroppedNotGuessed() = runTest {
        val legacy = CuadraDatabase.create(context, CuadraDatabase.LEGACY_FILE)
        legacy.outbox().insertAdopted(op("old", null))
        legacy.close()
        val r = LegacyDatabase.migrate(context, dbs, sessionBusinessId = null)
        assertEquals(0, r.moved)
        assertEquals(1, r.dropped)
    }
}

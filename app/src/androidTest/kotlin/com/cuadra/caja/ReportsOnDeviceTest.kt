package com.cuadra.caja

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.cuadra.caja.data.local.CuadraDatabase
import com.cuadra.caja.domain.ExpenseSplit
import com.cuadra.caja.domain.ProfitReport
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Las consultas del resumen usan aritmética de enteros de SQLite. Se comprueban EN el teléfono con el MISMO escenario que `ReportTest` del servidor
 * (3 ventas, una línea sin costo, un descuento, tres métodos de pago, gastos con uno de mercadería y uno anulado): los números deben ser idénticos.
 */
class ReportsOnDeviceTest {
    private lateinit var db: CuadraDatabase

    private fun ms(iso: String) = Instant.parse(iso).toEpochMilli()

    private fun sql(q: String) = db.openHelper.writableDatabase.execSQL(q)

    private fun sale(id: String, status: String, total: Long, discount: Long, at: String) =
        sql("INSERT INTO sales (id, status, subtotalMinor, discountMinor, totalMinor, completedAt, createdAt, updatedAt, rev) VALUES ('$id', '$status', ${total + discount}, $discount, $total, ${ms(at)}, ${ms(at)}, ${ms(at)}, 0)")

    private fun item(sale: String, id: String, product: String?, name: String, price: Long, cost: Long?, qtyMilli: Long) =
        sql("INSERT INTO sale_items (saleId, id, productId, name, unitPriceMinor, unitCostMinor, quantityMilli, discountMinor, position) VALUES ('$sale', '$id', ${product?.let { "'$it'" } ?: "NULL"}, '$name', $price, ${cost ?: "NULL"}, $qtyMilli, 0, 0)")

    private fun pay(sale: String, id: String, method: String, amount: Long) =
        sql("INSERT INTO sale_payments (saleId, id, method, amountMinor, position) VALUES ('$sale', '$id', '$method', $amount, 0)")

    private fun expense(id: String, amount: Long, category: String?, at: String, voided: Boolean = false) =
        sql("INSERT INTO expenses (id, categoryId, amountMinor, source, occurredAt, voided, rev) VALUES ('$id', ${category?.let { "'$it'" } ?: "NULL"}, $amount, 'BANK', ${ms(at)}, ${if (voided) 1 else 0}, 0)")

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, CuadraDatabase::class.java).build()
        for ((id, key) in listOf("c-util" to "utilities", "c-rent" to "rent", "c-goods" to "goods", "c-other" to "other")) sql("INSERT INTO expense_categories (id, key, active, rev) VALUES ('$id', '$key', 1, 0)")
        val d20 = "2026-09-20T18:00:00Z"
        val d21 = "2026-09-21T18:00:00Z"
        sale("s1", "COMPLETED", 25000, 0, d20); item("s1", "l1", "p-queso", "Queso", 10000, 6000, 2000); item("s1", "l2", "p-crema", "Crema", 5000, 3000, 1000); pay("s1", "a1", "CASH", 25000)
        sale("s2", "COMPLETED", 14000, 0, d20); item("s2", "l1", "p-queso", "Queso", 10000, 6000, 1000); item("s2", "l2", null, "Varios", 4000, null, 1000); pay("s2", "a1", "CASH", 5000); pay("s2", "a2", "TRANSFER", 9000)
        sale("s3", "COMPLETED", 14000, 1000, d21); item("s3", "l1", "p-crema", "Crema", 5000, 3000, 3000); pay("s3", "a1", "CARD", 14000)
        sale("s4", "CANCELLED", 10000, 0, d21); item("s4", "l1", "p-queso", "Queso", 10000, 6000, 1000); pay("s4", "a1", "CASH", 10000)
        expense("e1", 2000, "c-util", d20); expense("e2", 8000, "c-rent", d21); expense("e3", 4000, "c-goods", d21); expense("e4", 999, "c-other", d21, voided = true)
    }

    @After fun tearDown() = db.close()

    private val from get() = ms("2026-09-20T08:00:00Z")   // 02:00 en Managua: comienza la jornada del 20
    private val to get() = ms("2026-09-22T08:00:00Z")

    @Test fun salesAndPaymentMethodsMatchTheServerScenario() = runBlocking {
        val t = db.reports().salesTotals(from, to).first()
        assertEquals(3L, t.count)
        assertEquals(53000L, t.totalMinor)
        assertEquals(1000L, t.discountMinor)
        val methods = db.reports().byMethod(from, to).first().associate { it.method to it.amountMinor }
        assertEquals(mapOf("CASH" to 30000L, "TRANSFER" to 9000L, "CARD" to 14000L), methods)
    }

    @Test fun profitMatchesTheServerToTheLastCent() = runBlocking {
        val lines = db.reports().profitLines(from, to).first()
        assertEquals(30000L, lines.costOfGoodsMinor)
        assertEquals(50000L, lines.costedRevenueMinor)
        assertEquals(54000L, lines.lineRevenueMinor)
        val split = db.reports().expenseSplit(from, to).first()
        assertEquals(10000L, split.operatingMinor)
        assertEquals(4000L, split.purchasesMinor)
        val profit = ProfitReport.compute(db.reports().salesTotals(from, to).first().totalMinor, lines, ExpenseSplit(split.operatingMinor, split.purchasesMinor))
        assertEquals(13000L, profit.estimatedProfitMinor)
        assertEquals(93, profit.costCoveragePercent)
    }

    @Test fun anExpenseBornFromASupplierPaymentIsPurchasesNotOperating() = runBlocking {
        sql("INSERT INTO supplier_payments (id, purchaseId, amountMinor, source, occurredAt, voided, rev) VALUES ('pp1', 'pu1', 700, 'BANK', ${ms("2026-09-21T18:00:00Z")}, 0, 0)")
        expense("pp1", 700, null, "2026-09-21T18:00:00Z")
        val split = db.reports().expenseSplit(from, to).first()
        assertEquals(10000L, split.operatingMinor)
        assertEquals(4700L, split.purchasesMinor)
    }

    @Test fun topProductsAreRankedByRevenueWithTheirProfitAndAFlagForMissingCost() = runBlocking {
        val top = db.reports().topProducts(from, to, 10).first()
        assertEquals(listOf("Queso", "Crema", "Varios"), top.map { it.name })
        assertEquals(30000L, top[0].revenueMinor)
        assertEquals(12000L, top[0].profitMinor)
        assertEquals(3000L, top[0].quantityMilli)
        assertEquals(20000L, top[1].revenueMinor)
        assertEquals(8000L, top[1].profitMinor)
        assertTrue(top[0].fullyCosted)
        assertFalse(top[2].fullyCosted)
    }

    @Test fun receivableAddsOnlyOpenCredits() = runBlocking {
        sql("INSERT INTO credits (id, debtorLabel, amountMinor, balanceMinor, status, createdAt, rev) VALUES ('k1', 'Marta', 5000, 3000, 'OPEN', 1, 0)")
        sql("INSERT INTO credits (id, debtorLabel, amountMinor, balanceMinor, status, createdAt, rev) VALUES ('k2', 'Luis', 2000, 0, 'PAID', 1, 0)")
        sql("INSERT INTO credits (id, debtorLabel, amountMinor, balanceMinor, status, createdAt, rev) VALUES ('k3', 'Ana', 1000, 1000, 'OPEN', 1, 0)")
        assertEquals(4000L, db.reports().receivable().first())
    }
}

package com.cuadra.caja

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.cuadra.caja.data.local.CuadraDatabase
import com.cuadra.caja.data.local.MIGRATION_1_2
import com.cuadra.caja.data.local.MIGRATION_2_3
import com.cuadra.caja.data.local.MIGRATION_3_4
import com.cuadra.caja.data.local.MIGRATION_4_5
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Se ejecuta en el emulador/teléfono: crea la base real de la versión 1 con datos, la migra y valida el esquema contra el exportado. */
class MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), CuadraDatabase::class.java, emptyList(), FrameworkSQLiteOpenHelperFactory())

    @Test
    fun migration1To2KeepsSalesPaymentsAndPendingOperations() {
        helper.createDatabase(DB, 1).apply {
            execSQL("INSERT INTO sales (id, status, label, cashRegisterId, subtotalMinor, discountMinor, totalMinor, createdByMemberId, createdByName, completedByName, completedAt, editedByName, cancelledByName, cancelReason, lockedByDeviceId, createdAt, updatedAt, rev) VALUES ('s1', 'COMPLETED', NULL, NULL, 15502, 0, 15502, 'm1', 'Kevin', 'Kevin', 1000, NULL, NULL, NULL, NULL, 900, 1000, 0)")
            execSQL("INSERT INTO sale_payments (saleId, id, method, otherLabel, amountMinor, tenderedMinor, changeMinor, reference, position) VALUES ('s1', 'p1', 'CREDIT', NULL, 5502, NULL, NULL, 'Dona Karla', 0)")
            execSQL("INSERT INTO outbox (opId, kind, entityId, payload, createdAt, attempts, nextAttemptAt, state, lastCode) VALUES ('op1', 'SALE_UPSERT', 's1', '{}', 1000, 0, 0, 'PENDING', NULL)")
            close()
        }
        val db = helper.runMigrationsAndValidate(DB, 2, true, MIGRATION_1_2)

        // Lo que ya había sigue ahí, en particular lo que aún no se ha enviado al servidor (dinero).
        db.query("SELECT totalMinor FROM sales WHERE id = 's1'").use { c -> c.moveToFirst(); assertEquals(15502L, c.getLong(0)) }
        db.query("SELECT amountMinor, reference, debtorLabel, debtorPhone, customerId FROM sale_payments WHERE id = 'p1'").use { c ->
            c.moveToFirst()
            assertEquals(5502L, c.getLong(0))
            assertEquals("Dona Karla", c.getString(1))
            assertEquals(true, c.isNull(2) && c.isNull(3) && c.isNull(4))     // las columnas nuevas nacen vacías
        }
        db.query("SELECT state FROM outbox WHERE opId = 'op1'").use { c -> c.moveToFirst(); assertEquals("PENDING", c.getString(0)) }

        // Las tablas nuevas existen y funcionan.
        db.execSQL("INSERT INTO customers (id, name, archived, balanceMinor, rev) VALUES ('c1', 'Marta', 0, 0, 0)")
        db.execSQL("INSERT INTO credits (id, debtorLabel, amountMinor, balanceMinor, status, createdAt, rev) VALUES ('cr1', 'Marta', 100, 100, 'OPEN', 1, 0)")
        db.execSQL("INSERT INTO credit_payments (id, creditId, amountMinor, method, occurredAt, voided, rev) VALUES ('cp1', 'cr1', 50, 'CASH', 1, 0, 0)")
        db.execSQL("INSERT INTO message_templates (kind, locale, body, rev) VALUES ('REMINDER', 'es', 'Hola', 0)")
        db.query("SELECT COUNT(*) FROM credits").use { c -> c.moveToFirst(); assertEquals(1, c.getInt(0)) }
    }

    @Test
    fun migration2To3KeepsCreditsAndPaymentsAndAddsTheCashTables() {
        helper.createDatabase(DB2, 2).apply {
            execSQL("INSERT INTO business (id, name, country, currency, timezone, defaultLocale, dayCutoff, inventoryMode, modulesJson, posViewsJson, creditRequiresCustomer) VALUES ('b1', 'La Esquina', 'NI', 'NIO', 'America/Managua', 'es', '02:00', 'OFF', '{}', '[\"TYPE\"]', 0)")
            execSQL("INSERT INTO credits (id, debtorLabel, amountMinor, balanceMinor, status, createdAt, rev) VALUES ('cr1', 'Marta', 5000, 3000, 'OPEN', 1, 0)")
            execSQL("INSERT INTO credit_payments (id, creditId, amountMinor, method, occurredAt, voided, rev) VALUES ('cp1', 'cr1', 2000, 'CASH', 5, 0, 0)")
            execSQL("INSERT INTO outbox (opId, kind, entityId, payload, createdAt, attempts, nextAttemptAt, state, lastCode) VALUES ('op9', 'CREDIT_PAYMENT', 'cp1', '{}', 5, 0, 0, 'PENDING', NULL)")
            close()
        }
        val db = helper.runMigrationsAndValidate(DB2, 3, true, MIGRATION_1_2, MIGRATION_2_3)
        // El abono (y su operación pendiente) sobreviven; la caja de un abono viejo queda vacía y el negocio no exige turno.
        db.query("SELECT amountMinor, cashRegisterId FROM credit_payments WHERE id = 'cp1'").use { c -> c.moveToFirst(); assertEquals(2000L, c.getLong(0)); assertEquals(true, c.isNull(1)) }
        db.query("SELECT shiftRequired, shiftNoteThresholdMinor FROM business WHERE id = 'b1'").use { c -> c.moveToFirst(); assertEquals(0, c.getInt(0)); assertEquals(true, c.isNull(1)) }
        db.query("SELECT state FROM outbox WHERE opId = 'op9'").use { c -> c.moveToFirst(); assertEquals("PENDING", c.getString(0)) }
        db.execSQL("INSERT INTO shifts (id, cashRegisterId, openedAt, openingFloatMinor, status, lateOps, rev) VALUES ('s1', 'r1', 1, 100000, 'OPEN', 0, 0)")
        db.execSQL("INSERT INTO expenses (id, amountMinor, source, occurredAt, voided, rev) VALUES ('e1', 15000, 'CASH_DRAWER', 1, 0, 0)")
        db.execSQL("INSERT INTO cash_movements (id, kind, amountMinor, cashRegisterId, occurredAt, voided, rev) VALUES ('m1', 'DEPOSIT', 50000, 'r1', 1, 0, 0)")
        db.execSQL("INSERT INTO expense_categories (id, key, active, rev) VALUES ('k1', 'goods', 1, 0)")
        db.query("SELECT COUNT(*) FROM shifts").use { c -> c.moveToFirst(); assertEquals(1, c.getInt(0)) }
    }

    @Test
    fun migration3To4KeepsOpenShiftsExpensesAndPendingOperationsAndAddsTheInventoryTables() {
        helper.createDatabase(DB3, 3).apply {
            execSQL("INSERT INTO shifts (id, cashRegisterId, openedAt, openingFloatMinor, status, lateOps, rev) VALUES ('s1', 'r1', 1, 100000, 'OPEN', 0, 0)")
            execSQL("INSERT INTO expenses (id, amountMinor, source, occurredAt, voided, rev) VALUES ('e1', 15000, 'CASH_DRAWER', 1, 0, 0)")
            execSQL("INSERT INTO outbox (opId, kind, entityId, payload, createdAt, attempts, nextAttemptAt, state, lastCode) VALUES ('op7', 'SHIFT_CLOSE', 's1', '{}', 5, 1, 0, 'PENDING', 'RETRY')")
            close()
        }
        val db = helper.runMigrationsAndValidate(DB3, 4, true, MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
        db.query("SELECT state, lastCode FROM outbox WHERE opId = 'op7'").use { c -> c.moveToFirst(); assertEquals("PENDING", c.getString(0)); assertEquals("RETRY", c.getString(1)) }
        db.query("SELECT status FROM shifts WHERE id = 's1'").use { c -> c.moveToFirst(); assertEquals("OPEN", c.getString(0)) }
        db.execSQL("INSERT INTO suppliers (id, name, active, rev) VALUES ('sp1', 'Lácteos', 1, 0)")
        db.execSQL("INSERT INTO purchases (id, supplierId, totalMinor, occurredAt, voided, rev) VALUES ('pu1', 'sp1', 25000, 1, 0, 0)")
        db.execSQL("INSERT INTO purchase_items (purchaseId, id, name, quantityMilli, unitCostMinor, lineTotalMinor, position) VALUES ('pu1', 'l1', 'Leche', 10000, 2500, 25000, 0)")
        db.execSQL("INSERT INTO supplier_payments (id, purchaseId, amountMinor, source, occurredAt, voided, rev) VALUES ('pp1', 'pu1', 10000, 'BANK', 1, 0, 0)")
        db.execSQL("INSERT INTO stock_movements (id, productId, kind, quantityMilli, occurredAt, rev) VALUES ('sm1', 'p1', 'PURCHASE', 10000, 1, 0)")
        db.query("SELECT COUNT(*) FROM purchases").use { c -> c.moveToFirst(); assertEquals(1, c.getInt(0)) }
    }

    @Test
    fun migration4To5KeepsPurchasesAndPendingOperationsAndAddsTheNotificationInbox() {
        helper.createDatabase(DB4, 4).apply {
            execSQL("INSERT INTO suppliers (id, name, active, rev) VALUES ('sp1', 'Lácteos', 1, 0)")
            execSQL("INSERT INTO purchases (id, supplierId, totalMinor, occurredAt, voided, rev) VALUES ('pu1', 'sp1', 25000, 1, 0, 0)")
            execSQL("INSERT INTO outbox (opId, kind, entityId, payload, createdAt, attempts, nextAttemptAt, state, lastCode) VALUES ('op8', 'PURCHASE_REGISTER', 'pu1', '{}', 5, 0, 0, 'PENDING', NULL)")
            close()
        }
        val db = helper.runMigrationsAndValidate(DB4, 5, true, MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
        db.query("SELECT state FROM outbox WHERE opId = 'op8'").use { c -> c.moveToFirst(); assertEquals("PENDING", c.getString(0)) }
        db.query("SELECT totalMinor FROM purchases WHERE id = 'pu1'").use { c -> c.moveToFirst(); assertEquals(25000L, c.getLong(0)) }
        db.execSQL("INSERT INTO notifications (id, type, channel, argsJson, push, createdAt, rev) VALUES ('n1', 'LOW_STOCK', 'STOCK', '{}', 1, 1, 0)")
        db.query("SELECT shown FROM notifications WHERE id = 'n1'").use { c -> c.moveToFirst(); assertEquals(0, c.getInt(0)) }
    }

    private companion object {
        const val DB4 = "migration-test-4"
        const val DB3 = "migration-test-3"
        const val DB2 = "migration-test-2"
        const val DB = "migration-test"
    }
}

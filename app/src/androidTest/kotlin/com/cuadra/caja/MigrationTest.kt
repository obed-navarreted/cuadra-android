package com.cuadra.caja

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.cuadra.caja.data.local.CuadraDatabase
import com.cuadra.caja.data.local.MIGRATION_1_2
import com.cuadra.caja.data.local.MIGRATION_11_12
import com.cuadra.caja.data.local.MIGRATION_12_13
import com.cuadra.caja.data.local.MIGRATION_2_3
import com.cuadra.caja.data.local.MIGRATION_3_4
import com.cuadra.caja.data.local.MIGRATION_4_5
import com.cuadra.caja.data.local.MIGRATION_5_6
import com.cuadra.caja.data.local.MIGRATION_6_7
import com.cuadra.caja.data.local.MIGRATION_7_8
import com.cuadra.caja.data.local.MIGRATION_8_9
import com.cuadra.caja.data.local.MIGRATION_9_10
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

    @Test
    fun migration5To6KeepsPendingOperationsAddsProductCategoriesAndResetsTheSyncCursor() {
        helper.createDatabase(DB5, 5).apply {
            execSQL("INSERT INTO outbox (opId, kind, entityId, payload, createdAt, attempts, nextAttemptAt, state, lastCode) VALUES ('op9', 'PRODUCT_UPSERT', 'p1', '{}', 5, 0, 0, 'PENDING', NULL)")
            execSQL("INSERT INTO sync_state (id, cursor) VALUES (1, 4242)")
            close()
        }
        val db = helper.runMigrationsAndValidate(DB5, 6, true, MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
        db.query("SELECT state FROM outbox WHERE opId = 'op9'").use { c -> c.moveToFirst(); assertEquals("PENDING", c.getString(0)) }
        // Las categorías que ya existían en el servidor nunca se bajaron: el cursor vuelve a 0 una vez para traerlas.
        db.query("SELECT cursor FROM sync_state WHERE id = 1").use { c -> c.moveToFirst(); assertEquals(0L, c.getLong(0)) }
        db.execSQL("INSERT INTO product_categories (id, name, active, rev) VALUES ('c1', 'Bebidas', 1, 1)")
        db.query("SELECT name FROM product_categories WHERE id = 'c1'").use { c -> c.moveToFirst(); assertEquals("Bebidas", c.getString(0)) }
    }

    @Test
    fun migration6To7KeepsBusinessesSalesAndPendingOperationsAndAddsTheDayRules() {
        helper.createDatabase(DB6, 6).apply {
            execSQL("INSERT INTO business (id, name, country, currency, timezone, defaultLocale, dayCutoff, inventoryMode, modulesJson, posViewsJson, creditRequiresCustomer, shiftRequired) VALUES ('b1', 'Tienda', 'NI', 'NIO', 'America/Managua', 'es', '02:00', 'OFF', '{}', '[]', 0, 0)")
            execSQL("INSERT INTO sales (id, status, subtotalMinor, discountMinor, totalMinor, createdAt, updatedAt, rev) VALUES ('s1', 'CANCELLED', 1000, 0, 1000, 1, 1, 5)")
            execSQL("INSERT INTO outbox (opId, kind, entityId, payload, createdAt, attempts, nextAttemptAt, state, lastCode) VALUES ('op10', 'SALE_CANCEL', 's1', '{\"reason\":\"cobrada dos veces\"}', 5, 0, 0, 'PENDING', NULL)")
            execSQL("INSERT INTO sync_state (id, cursor) VALUES (1, 999)")
            close()
        }
        val db = helper.runMigrationsAndValidate(DB6, 7, true, MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
        // Nada se pierde: el negocio sigue igual y sin reglas guardadas (el teléfono usa su zona/corte de siempre hasta bajarlas).
        db.query("SELECT timezone, dayCutoff, dayRulesJson, dayRuleEffectiveFrom FROM business WHERE id = 'b1'").use { c ->
            c.moveToFirst(); assertEquals("America/Managua", c.getString(0)); assertEquals("02:00", c.getString(1)); assertEquals("[]", c.getString(2)); assertEquals(true, c.isNull(3))
        }
        db.query("SELECT status, totalMinor, editedAt, cancelledAt FROM sales WHERE id = 's1'").use { c ->
            c.moveToFirst(); assertEquals("CANCELLED", c.getString(0)); assertEquals(1000L, c.getLong(1)); assertEquals(true, c.isNull(2)); assertEquals(true, c.isNull(3))
        }
        db.query("SELECT state, kind FROM outbox WHERE opId = 'op10'").use { c -> c.moveToFirst(); assertEquals("PENDING", c.getString(0)); assertEquals("SALE_CANCEL", c.getString(1)) }
        // El cursor vuelve a 0 una vez para bajar el negocio con sus reglas de jornada.
        db.query("SELECT cursor FROM sync_state WHERE id = 1").use { c -> c.moveToFirst(); assertEquals(0L, c.getLong(0)) }
        db.execSQL("UPDATE business SET dayRulesJson = '[{\"from\":\"1970-01-01\",\"timezone\":\"America/Managua\",\"dayCutoff\":\"02:00\"}]', dayRuleEffectiveFrom = '2026-10-10' WHERE id = 'b1'")
        db.execSQL("UPDATE sales SET cancelledAt = 12345, editedAt = 999 WHERE id = 's1'")
        db.query("SELECT cancelledAt FROM sales WHERE id = 's1'").use { c -> c.moveToFirst(); assertEquals(12345L, c.getLong(0)) }
    }

    @Test
    fun migration7To8KeepsTheBusinessAndAddsTheCreditRulesWithTheirDefaults() {
        helper.createDatabase(DB7, 7).apply {
            execSQL("INSERT INTO business (id, name, country, currency, timezone, defaultLocale, dayCutoff, inventoryMode, modulesJson, posViewsJson, creditRequiresCustomer, shiftRequired, dayRulesJson) VALUES ('b1', 'Tienda', 'NI', 'NIO', 'America/Managua', 'es', '02:00', 'OFF', '{}', '[]', 1, 0, '[]')")
            execSQL("INSERT INTO sync_state (id, cursor) VALUES (1, 777)")
            close()
        }
        val db = helper.runMigrationsAndValidate(DB7, 8, true, MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
        db.query("SELECT name, creditRequiresCustomer, type, creditDefaultDueDays, creditOverdueDays, creditLimitEnforced FROM business WHERE id = 'b1'").use { c ->
            c.moveToFirst(); assertEquals("Tienda", c.getString(0)); assertEquals(1, c.getInt(1)); assertEquals(true, c.isNull(2)); assertEquals(true, c.isNull(3)); assertEquals(30, c.getInt(4)); assertEquals(0, c.getInt(5))
        }
        db.query("SELECT cursor FROM sync_state WHERE id = 1").use { c -> c.moveToFirst(); assertEquals(0L, c.getLong(0)) }
    }

    @Test
    fun migration8To9KeepsTheBusinessAndAddsTheAccessCode() {
        helper.createDatabase(DB8, 8).apply {
            execSQL("INSERT INTO business (id, name, country, currency, timezone, defaultLocale, dayCutoff, inventoryMode, modulesJson, posViewsJson, creditRequiresCustomer, shiftRequired, dayRulesJson, creditOverdueDays, creditLimitEnforced) VALUES ('b1', 'Tienda', 'NI', 'NIO', 'America/Managua', 'es', '02:00', 'OFF', '{}', '[]', 1, 0, '[]', 30, 0)")
            execSQL("INSERT INTO sync_state (id, cursor) VALUES (1, 777)")
            close()
        }
        val db = helper.runMigrationsAndValidate(DB8, 9, true, MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9)
        db.query("SELECT name, accessCode FROM business WHERE id = 'b1'").use { c -> c.moveToFirst(); assertEquals("Tienda", c.getString(0)); assertEquals(true, c.isNull(1)) }
        db.query("SELECT cursor FROM sync_state WHERE id = 1").use { c -> c.moveToFirst(); assertEquals(0L, c.getLong(0)) }
    }

    @Test
    fun migration9To10KeepsThePendingQueueAndStampsItWithItsBusiness() {
        helper.createDatabase(DB9, 9).apply {
            execSQL("INSERT INTO business (id, name, country, currency, timezone, defaultLocale, dayCutoff, inventoryMode, modulesJson, posViewsJson, creditRequiresCustomer, shiftRequired, dayRulesJson, creditOverdueDays, creditLimitEnforced, accessCode) VALUES ('b1', 'Tienda', 'NI', 'NIO', 'America/Managua', 'es', '02:00', 'OFF', '{}', '[]', 1, 0, '[]', 30, 0, '13085')")
            execSQL("INSERT INTO sync_state (id, cursor) VALUES (1, 777)")
            execSQL("INSERT INTO outbox (opId, kind, entityId, payload, createdAt, attempts, nextAttemptAt, state, lastCode) VALUES ('op1', 'SALE_UPSERT', 's1', '{}', 1000, 0, 0, 'PENDING', NULL)")
            execSQL("INSERT INTO outbox (opId, kind, entityId, payload, createdAt, attempts, nextAttemptAt, state, lastCode) VALUES ('op2', 'CREDIT_PAYMENT', 'p1', '{}', 1001, 1, 0, 'FAILED', 'CREDIT_CLOSED')")
            close()
        }
        val db = helper.runMigrationsAndValidate(DB9, 10, true, MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10)
        // Lo que estaba en la cola sigue ahí (dinero), marcado con el único negocio que había; quién lo hizo no se sabe (nulo = la persona activa).
        db.query("SELECT opId, state, businessId, memberId, lastDetail FROM outbox ORDER BY seq").use { c ->
            c.moveToFirst(); assertEquals("op1", c.getString(0)); assertEquals("PENDING", c.getString(1)); assertEquals("b1", c.getString(2)); assertEquals(true, c.isNull(3)); assertEquals(true, c.isNull(4))
            c.moveToNext(); assertEquals("op2", c.getString(0)); assertEquals("FAILED", c.getString(1)); assertEquals("b1", c.getString(2))
        }
        // El cursor NO se reinicia: queda con su negocio.
        db.query("SELECT cursor, businessId FROM sync_state WHERE id = 1").use { c -> c.moveToFirst(); assertEquals(777L, c.getLong(0)); assertEquals("b1", c.getString(1)) }
        db.execSQL("INSERT INTO outbox_discarded (opId, kind, entityId, payload, createdAt, discardedAt) VALUES ('op3', 'SALE_UPSERT', 's3', '{}', 1, 2)")
        db.query("SELECT COUNT(*) FROM outbox_discarded").use { c -> c.moveToFirst(); assertEquals(1, c.getInt(0)) }
    }

    @Test
    fun migration11To12KeepsSalesAndPendingOperationsAndAddsRegisterCheckout() {
        helper.createDatabase(DB11, 11).apply {
            execSQL("INSERT INTO business (id, name, country, currency, timezone, defaultLocale, dayCutoff, inventoryMode, modulesJson, posViewsJson, creditRequiresCustomer, shiftRequired, dayRulesJson, creditOverdueDays, creditLimitEnforced, accessCode, currencyLocked) VALUES ('b1', 'Tienda', 'NI', 'NIO', 'America/Managua', 'es', '02:00', 'OFF', '{}', '[]', 1, 0, '[]', 30, 0, '13085', 0)")
            execSQL("INSERT INTO sales (id, status, label, subtotalMinor, discountMinor, totalMinor, createdAt, updatedAt, rev, returnedMinor) VALUES ('s1', 'PARKED', 'Mesa 4', 100, 0, 100, 1, 1, 5, 0)")
            execSQL("INSERT INTO sync_state (id, cursor, businessId) VALUES (1, 777, 'b1')")
            execSQL("INSERT INTO outbox (opId, kind, entityId, payload, createdAt, attempts, nextAttemptAt, state, lastCode, businessId) VALUES ('op1', 'SALE_UPSERT', 's1', '{}', 1000, 0, 0, 'PENDING', NULL, 'b1')")
            close()
        }
        val db = helper.runMigrationsAndValidate(DB11, 12, true, MIGRATION_11_12)
        db.query("SELECT label, sentToRegisterAt, sentByName FROM sales WHERE id = 's1'").use { c -> c.moveToFirst(); assertEquals("Mesa 4", c.getString(0)); assertEquals(true, c.isNull(1)); assertEquals(true, c.isNull(2)) }
        db.query("SELECT registerCheckout FROM business WHERE id = 'b1'").use { c -> c.moveToFirst(); assertEquals(0, c.getInt(0)) }
        db.query("SELECT COUNT(*) FROM outbox").use { c -> c.moveToFirst(); assertEquals(1, c.getInt(0)) }
        db.query("SELECT cursor FROM sync_state WHERE id = 1").use { c -> c.moveToFirst(); assertEquals(0L, c.getLong(0)) }
    }

    @Test
    fun migration12To13KeepsSalesAndTheQueueAndAddsPromotions() {
        helper.createDatabase(DB12, 12).apply {
            execSQL("INSERT INTO business (id, name, country, currency, timezone, defaultLocale, dayCutoff, inventoryMode, modulesJson, posViewsJson, creditRequiresCustomer, shiftRequired, dayRulesJson, creditOverdueDays, creditLimitEnforced, accessCode, currencyLocked, registerCheckout) VALUES ('b1', 'Bar', 'NI', 'NIO', 'America/Managua', 'es', '02:00', 'OFF', '{}', '[]', 1, 0, '[]', 30, 0, '13085', 0, 0)")
            execSQL("INSERT INTO sales (id, status, label, subtotalMinor, discountMinor, totalMinor, createdAt, updatedAt, rev, returnedMinor) VALUES ('s1', 'COMPLETED', NULL, 31500, 0, 31500, 1, 1, 5, 0)")
            execSQL("INSERT INTO sale_items (saleId, id, productId, barcode, name, variant, unitPriceMinor, unitCostMinor, quantityMilli, discountMinor, position, returnedMilli) VALUES ('s1', 'l1', 'p1', NULL, 'Toña', NULL, 4500, NULL, 7000, 0, 0, 0)")
            execSQL("INSERT INTO sync_state (id, cursor, businessId) VALUES (1, 777, 'b1')")
            execSQL("INSERT INTO outbox (opId, kind, entityId, payload, createdAt, attempts, nextAttemptAt, state, lastCode, businessId) VALUES ('op1', 'SALE_UPSERT', 's1', '{}', 1000, 0, 0, 'PENDING', NULL, 'b1')")
            close()
        }
        val db = helper.runMigrationsAndValidate(DB12, 13, true, MIGRATION_12_13)
        db.query("SELECT totalMinor FROM sales WHERE id = 's1'").use { c -> c.moveToFirst(); assertEquals(31500L, c.getLong(0)) }
        db.query("SELECT COUNT(*) FROM sale_items WHERE saleId = 's1'").use { c -> c.moveToFirst(); assertEquals(1, c.getInt(0)) }
        db.query("SELECT COUNT(*) FROM outbox").use { c -> c.moveToFirst(); assertEquals(1, c.getInt(0)) }
        db.query("SELECT cursor FROM sync_state WHERE id = 1").use { c -> c.moveToFirst(); assertEquals(0L, c.getLong(0)) }
        // Las tablas nuevas existen y aceptan filas.
        db.execSQL("INSERT INTO promotions (id, name, quantity, priceMinor, active, startsOn, endsOn, rev) VALUES ('pr1', 'Cerveza 3 por C$ 100', 3, 10000, 1, NULL, NULL, 9)")
        db.execSQL("INSERT INTO promotion_products (promotionId, productId) VALUES ('pr1', 'p1')")
        db.execSQL("INSERT INTO sale_promotions (saleId, position, promotionId, name, quantity, priceMinor, units, discountMinor) VALUES ('s1', 0, 'pr1', 'Cerveza 3 por C$ 100', 3, 10000, 6, 7000)")
        db.query("SELECT COUNT(*) FROM promotion_products WHERE productId = 'p1'").use { c -> c.moveToFirst(); assertEquals(1, c.getInt(0)) }
    }

    private companion object {
        const val DB12 = "migration-test-12"
        const val DB11 = "migration-test-11"
        const val DB9 = "migration-test-9"
        const val DB8 = "migration-test-8"
        const val DB7 = "migration-test-7"
        const val DB6 = "migration-test-6"
        const val DB5 = "migration-test-5"
        const val DB4 = "migration-test-4"
        const val DB3 = "migration-test-3"
        const val DB2 = "migration-test-2"
        const val DB = "migration-test"
    }
}

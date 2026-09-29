package com.cuadra.caja.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 1 → 2 (Fase 3): fiado, clientes, abonos y plantillas de mensajes; el pago de una venta gana los datos de quien debe.
 * Es aditiva: no toca productos, ventas ni la cola de salida. Nunca se usa `fallbackToDestructiveMigration` (borraría ventas sin enviar).
 * El SQL es el que Room exporta en `schemas/…/2.json`; la prueba `MigrationTest` valida que el resultado coincida.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `customers` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `phone` TEXT, `notes` TEXT, `creditLimitMinor` INTEGER, `lastReminderAt` INTEGER, `archived` INTEGER NOT NULL, `balanceMinor` INTEGER NOT NULL, `oldestOpenAt` INTEGER, `rev` INTEGER NOT NULL, PRIMARY KEY(`id`))")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_customers_name` ON `customers` (`name`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `credits` (`id` TEXT NOT NULL, `saleId` TEXT, `customerId` TEXT, `debtorLabel` TEXT NOT NULL, `debtorPhone` TEXT, `amountMinor` INTEGER NOT NULL, `balanceMinor` INTEGER NOT NULL, `status` TEXT NOT NULL, `dueDate` TEXT, `note` TEXT, `createdAt` INTEGER NOT NULL, `createdByName` TEXT, `lastReminderAt` INTEGER, `rev` INTEGER NOT NULL, PRIMARY KEY(`id`))")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_credits_customerId` ON `credits` (`customerId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_credits_status` ON `credits` (`status`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_credits_createdAt` ON `credits` (`createdAt`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `credit_payments` (`id` TEXT NOT NULL, `creditId` TEXT NOT NULL, `customerId` TEXT, `groupId` TEXT, `amountMinor` INTEGER NOT NULL, `method` TEXT NOT NULL, `reference` TEXT, `createdByName` TEXT, `occurredAt` INTEGER NOT NULL, `voided` INTEGER NOT NULL, `voidReason` TEXT, `rev` INTEGER NOT NULL, PRIMARY KEY(`id`))")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_credit_payments_creditId` ON `credit_payments` (`creditId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_credit_payments_customerId` ON `credit_payments` (`customerId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_credit_payments_groupId` ON `credit_payments` (`groupId`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `message_templates` (`kind` TEXT NOT NULL, `locale` TEXT NOT NULL, `body` TEXT NOT NULL, `rev` INTEGER NOT NULL, PRIMARY KEY(`kind`, `locale`))")
        db.execSQL("ALTER TABLE `sale_payments` ADD COLUMN `debtorLabel` TEXT")
        db.execSQL("ALTER TABLE `sale_payments` ADD COLUMN `debtorPhone` TEXT")
        db.execSQL("ALTER TABLE `sale_payments` ADD COLUMN `customerId` TEXT")
    }
}

/**
 * 2 → 3 (Fase 4): gastos, movimientos de caja, turnos y categorías; el abono a fiado recuerda su caja y el negocio guarda si el turno es
 * obligatorio y el umbral de nota del cierre. Aditiva: no toca ventas, fiados ni la cola de salida.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `business` ADD COLUMN `shiftRequired` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `business` ADD COLUMN `shiftNoteThresholdMinor` INTEGER")
        db.execSQL("ALTER TABLE `credit_payments` ADD COLUMN `cashRegisterId` TEXT")
        db.execSQL("CREATE TABLE IF NOT EXISTS `expense_categories` (`id` TEXT NOT NULL, `key` TEXT, `name` TEXT, `active` INTEGER NOT NULL, `rev` INTEGER NOT NULL, PRIMARY KEY(`id`))")
        db.execSQL("CREATE TABLE IF NOT EXISTS `expenses` (`id` TEXT NOT NULL, `categoryId` TEXT, `description` TEXT, `amountMinor` INTEGER NOT NULL, `source` TEXT NOT NULL, `cashRegisterId` TEXT, `createdByName` TEXT, `createdById` TEXT, `occurredAt` INTEGER NOT NULL, `voided` INTEGER NOT NULL, `voidReason` TEXT, `rev` INTEGER NOT NULL, PRIMARY KEY(`id`))")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_expenses_occurredAt` ON `expenses` (`occurredAt`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_expenses_cashRegisterId` ON `expenses` (`cashRegisterId`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `cash_movements` (`id` TEXT NOT NULL, `kind` TEXT NOT NULL, `amountMinor` INTEGER NOT NULL, `reason` TEXT, `cashRegisterId` TEXT NOT NULL, `createdByName` TEXT, `createdById` TEXT, `occurredAt` INTEGER NOT NULL, `voided` INTEGER NOT NULL, `voidReason` TEXT, `rev` INTEGER NOT NULL, PRIMARY KEY(`id`))")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_cash_movements_occurredAt` ON `cash_movements` (`occurredAt`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_cash_movements_cashRegisterId` ON `cash_movements` (`cashRegisterId`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `shifts` (`id` TEXT NOT NULL, `cashRegisterId` TEXT NOT NULL, `registerName` TEXT, `openedByName` TEXT, `openedById` TEXT, `openedAt` INTEGER NOT NULL, `openingFloatMinor` INTEGER NOT NULL, `closedByName` TEXT, `closedAt` INTEGER, `expectedAtCloseMinor` INTEGER, `countedMinor` INTEGER, `differenceMinor` INTEGER, `denominations` TEXT, `note` TEXT, `status` TEXT NOT NULL, `forcedReason` TEXT, `lateOps` INTEGER NOT NULL, `rev` INTEGER NOT NULL, PRIMARY KEY(`id`))")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_shifts_cashRegisterId_status` ON `shifts` (`cashRegisterId`, `status`)")
    }
}

/** 3 → 4 (Fase 5): movimientos de existencias, proveedores, compras y pagos a proveedores. Aditiva: no toca lo existente. */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `stock_movements` (`id` TEXT NOT NULL, `productId` TEXT NOT NULL, `kind` TEXT NOT NULL, `quantityMilli` INTEGER NOT NULL, `unitCostMinor` INTEGER, `refType` TEXT, `refId` TEXT, `note` TEXT, `createdByName` TEXT, `createdById` TEXT, `occurredAt` INTEGER NOT NULL, `rev` INTEGER NOT NULL, PRIMARY KEY(`id`))")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_stock_movements_productId_occurredAt` ON `stock_movements` (`productId`, `occurredAt`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_stock_movements_rev` ON `stock_movements` (`rev`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `suppliers` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `phone` TEXT, `notes` TEXT, `active` INTEGER NOT NULL, `rev` INTEGER NOT NULL, PRIMARY KEY(`id`))")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_suppliers_name` ON `suppliers` (`name`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `purchases` (`id` TEXT NOT NULL, `supplierId` TEXT, `supplierName` TEXT, `totalMinor` INTEGER NOT NULL, `note` TEXT, `createdByName` TEXT, `occurredAt` INTEGER NOT NULL, `voided` INTEGER NOT NULL, `voidReason` TEXT, `rev` INTEGER NOT NULL, PRIMARY KEY(`id`))")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_purchases_supplierId` ON `purchases` (`supplierId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_purchases_occurredAt` ON `purchases` (`occurredAt`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `purchase_items` (`purchaseId` TEXT NOT NULL, `id` TEXT NOT NULL, `productId` TEXT, `name` TEXT NOT NULL, `quantityMilli` INTEGER NOT NULL, `unitCostMinor` INTEGER NOT NULL, `lineTotalMinor` INTEGER NOT NULL, `position` INTEGER NOT NULL, PRIMARY KEY(`purchaseId`, `id`))")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_purchase_items_purchaseId` ON `purchase_items` (`purchaseId`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `supplier_payments` (`id` TEXT NOT NULL, `purchaseId` TEXT NOT NULL, `supplierId` TEXT, `amountMinor` INTEGER NOT NULL, `source` TEXT NOT NULL, `note` TEXT, `createdByName` TEXT, `occurredAt` INTEGER NOT NULL, `voided` INTEGER NOT NULL, `voidReason` TEXT, `rev` INTEGER NOT NULL, PRIMARY KEY(`id`))")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_supplier_payments_purchaseId` ON `supplier_payments` (`purchaseId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_supplier_payments_supplierId` ON `supplier_payments` (`supplierId`)")
    }
}

/** 4 → 5 (Fase 6): bandeja de notificaciones. Aditiva. */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `notifications` (`id` TEXT NOT NULL, `type` TEXT NOT NULL, `channel` TEXT NOT NULL, `argsJson` TEXT NOT NULL, `title` TEXT, `body` TEXT, `deepLink` TEXT, `push` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `readAt` INTEGER, `scheduleId` TEXT, `recipientMemberId` TEXT, `recipientDeviceId` TEXT, `rev` INTEGER NOT NULL, `shown` INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(`id`))")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_notifications_createdAt` ON `notifications` (`createdAt`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_notifications_readAt` ON `notifications` (`readAt`)")
    }
}

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

/**
 * 5 → 6: categorías de productos (para elegirlas en el editor). Aditiva. El cursor de sincronización vuelve a 0 una vez: las categorías que ya
 * existían en el servidor nunca se bajaron, y volver a traer todo es seguro (cada tipo respeta lo que el teléfono tiene sin enviar).
 */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `product_categories` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `active` INTEGER NOT NULL, `rev` INTEGER NOT NULL, PRIMARY KEY(`id`))")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_product_categories_name` ON `product_categories` (`name`)")
        db.execSQL("UPDATE sync_state SET cursor = 0")
    }
}

/**
 * 6 → 7 (ADR 0011): historial de zona/corte del negocio y quién/cuándo editó o anuló una venta. Aditiva: nada se borra. El cursor de sincronización vuelve a 0
 * una vez para bajar de nuevo el negocio con sus `dayRules` (sin ellas el teléfono usa la regla única de siempre) y las horas de anulación.
 */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `business` ADD COLUMN `dayRulesJson` TEXT NOT NULL DEFAULT '[]'")
        db.execSQL("ALTER TABLE `business` ADD COLUMN `dayRuleEffectiveFrom` TEXT")
        db.execSQL("ALTER TABLE `sales` ADD COLUMN `editedAt` INTEGER")
        db.execSQL("ALTER TABLE `sales` ADD COLUMN `cancelledAt` INTEGER")
        db.execSQL("UPDATE sync_state SET cursor = 0")
    }
}

/**
 * 7 → 8 (Ajustes del negocio): tipo de negocio y reglas de fiado (vencimiento por defecto, días para «vencido», límite obligatorio). Aditiva: nada se borra.
 * El cursor de sincronización vuelve a 0 una vez para bajar de nuevo el negocio con esos campos.
 */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `business` ADD COLUMN `type` TEXT")
        db.execSQL("ALTER TABLE `business` ADD COLUMN `creditDefaultDueDays` INTEGER")
        db.execSQL("ALTER TABLE `business` ADD COLUMN `creditOverdueDays` INTEGER NOT NULL DEFAULT 30")
        db.execSQL("ALTER TABLE `business` ADD COLUMN `creditLimitEnforced` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("UPDATE sync_state SET cursor = 0")
    }
}

/**
 * 8 → 9 (ADR 0012): código del negocio (`accessCode`, 5 dígitos) para mostrarlo en Equipo. Aditiva: nada se borra. El cursor vuelve a 0 una vez para
 * bajar de nuevo el negocio con su código.
 */
val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `business` ADD COLUMN `accessCode` TEXT")
        db.execSQL("UPDATE sync_state SET cursor = 0")
    }
}

/**
 * 9 → 10 (revisión del flujo sin conexión): cada operación de la cola guarda QUIÉN la hizo y de QUÉ negocio es, más los datos del rechazo; el cursor
 * recuerda su negocio; y lo que se descarta de «Requiere atención» deja una línea en `outbox_discarded`. Aditiva: nada se borra. Lo que ya estaba en la
 * cola es del único negocio que había en el teléfono; quién lo hizo no se sabe (el servidor usará la persona activa, como antes).
 */
val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `outbox` ADD COLUMN `memberId` TEXT")
        db.execSQL("ALTER TABLE `outbox` ADD COLUMN `businessId` TEXT")
        db.execSQL("ALTER TABLE `outbox` ADD COLUMN `lastDetail` TEXT")
        db.execSQL("UPDATE `outbox` SET `businessId` = (SELECT `id` FROM `business` LIMIT 1)")
        db.execSQL("ALTER TABLE `sync_state` ADD COLUMN `businessId` TEXT")
        db.execSQL("UPDATE `sync_state` SET `businessId` = (SELECT `id` FROM `business` LIMIT 1)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `outbox_discarded` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `opId` TEXT NOT NULL, `kind` TEXT NOT NULL, `entityId` TEXT NOT NULL, `payload` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `memberId` TEXT, `businessId` TEXT, `code` TEXT, `discardedAt` INTEGER NOT NULL, `discardedById` TEXT, `discardedByName` TEXT)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_outbox_discarded_discardedAt` ON `outbox_discarded` (`discardedAt`)")
    }
}

/**
 * 10 → 11 (docs/adr/0013): devoluciones (`sale_returns` y lo devuelto por línea), etiquetas para revisar una venta (conflicto, llegó después de la baja,
 * hora corregida), quién la cobró (para «Anular mi última venta») y si la moneda del negocio ya quedó fija. Aditiva: nada se borra. El cursor vuelve a 0
 * una vez para bajar de nuevo ventas y negocio con esos datos.
 */
val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `sales` ADD COLUMN `reviewFlag` TEXT")
        db.execSQL("ALTER TABLE `sales` ADD COLUMN `conflictOfSaleId` TEXT")
        db.execSQL("ALTER TABLE `sales` ADD COLUMN `returnedMinor` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `sales` ADD COLUMN `completedByMemberId` TEXT")
        db.execSQL("UPDATE `sales` SET `completedByMemberId` = `createdByMemberId` WHERE `status` IN ('COMPLETED', 'CANCELLED') AND `completedAt` IS NOT NULL")
        db.execSQL("ALTER TABLE `sale_items` ADD COLUMN `returnedMilli` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("CREATE TABLE IF NOT EXISTS `sale_returns` (`id` TEXT NOT NULL, `saleId` TEXT NOT NULL, `reason` TEXT NOT NULL, `refundMethod` TEXT NOT NULL, `totalMinor` INTEGER NOT NULL, `createdByName` TEXT, `occurredAt` INTEGER NOT NULL, `itemsJson` TEXT NOT NULL, `refundsJson` TEXT NOT NULL, `rev` INTEGER NOT NULL, PRIMARY KEY(`id`))")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sale_returns_saleId` ON `sale_returns` (`saleId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sale_returns_occurredAt` ON `sale_returns` (`occurredAt`)")
        db.execSQL("ALTER TABLE `business` ADD COLUMN `currencyLocked` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("UPDATE sync_state SET cursor = 0")
    }
}

/**
 * 11 → 12 (docs/adr/0015, cobro en caja): el ajuste del negocio y, en cada venta, cuándo y quién la envió a caja. Aditiva: nada se borra. El cursor vuelve
 * a 0 una vez para bajar de nuevo el negocio y las cuentas con esos datos.
 */
val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `sales` ADD COLUMN `sentToRegisterAt` INTEGER")
        db.execSQL("ALTER TABLE `sales` ADD COLUMN `sentByName` TEXT")
        db.execSQL("ALTER TABLE `business` ADD COLUMN `registerCheckout` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("UPDATE sync_state SET cursor = 0")
    }
}

/**
 * 12 → 13 (PENDIENTES.md, «Promociones por cantidad»): las promociones del negocio con sus productos y, en cada venta, las promociones que aplicó.
 * Aditiva: nada se borra. El cursor vuelve a 0 una vez para bajar las promociones que ya existían y las ventas con lo que aplicaron.
 */
val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `sale_promotions` (`saleId` TEXT NOT NULL, `position` INTEGER NOT NULL, `promotionId` TEXT, `name` TEXT NOT NULL, `quantity` INTEGER NOT NULL, `priceMinor` INTEGER NOT NULL, `units` INTEGER NOT NULL, `discountMinor` INTEGER NOT NULL, PRIMARY KEY(`saleId`, `position`))")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sale_promotions_saleId` ON `sale_promotions` (`saleId`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `promotions` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `quantity` INTEGER NOT NULL, `priceMinor` INTEGER NOT NULL, `active` INTEGER NOT NULL, `startsOn` TEXT, `endsOn` TEXT, `rev` INTEGER NOT NULL, PRIMARY KEY(`id`))")
        db.execSQL("CREATE TABLE IF NOT EXISTS `promotion_products` (`promotionId` TEXT NOT NULL, `productId` TEXT NOT NULL, PRIMARY KEY(`promotionId`, `productId`))")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_promotion_products_productId` ON `promotion_products` (`productId`)")
        db.execSQL("UPDATE sync_state SET cursor = 0")
    }
}

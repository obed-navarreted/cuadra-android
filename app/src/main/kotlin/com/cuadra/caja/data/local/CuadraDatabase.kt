package com.cuadra.caja.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Room es la fuente de verdad de la interfaz. Nunca se usa `fallbackToDestructiveMigration`: una migración que borre
 * ventas sin sincronizar destruiría dinero (PLAN.md 13). Cada cambio de esquema lleva su `Migration` y su prueba.
 */
@Database(
    entities = [
        ProductEntity::class, SaleEntity::class, SaleItemEntity::class, SalePaymentEntity::class, OutboxEntity::class,
        MemberEntity::class, BusinessEntity::class, CashRegisterEntity::class, SyncStateEntity::class,
        CustomerEntity::class, CreditEntity::class, CreditPaymentEntity::class, TemplateEntity::class,
        ExpenseCategoryEntity::class, ExpenseEntity::class, CashMovementEntity::class, ShiftEntity::class,
        StockMovementEntity::class, SupplierEntity::class, PurchaseEntity::class, PurchaseItemEntity::class, SupplierPaymentEntity::class,
        NotificationEntity::class, CategoryEntity::class, DiscardedOpEntity::class, SaleReturnEntity::class,
    ],
    version = 11,
    exportSchema = true,
)
abstract class CuadraDatabase : RoomDatabase() {  // MIGRATION_1_2 vive en Migrations.kt
    abstract fun products(): ProductDao
    abstract fun sales(): SaleDao
    abstract fun outbox(): OutboxDao
    abstract fun directory(): DirectoryDao
    abstract fun customers(): CustomerDao
    abstract fun credits(): CreditDao
    abstract fun templates(): TemplateDao
    abstract fun cash(): CashDao
    abstract fun inventory(): InventoryDao
    abstract fun notifications(): NotificationDao
    abstract fun reports(): ReportDao

    companion object {
        fun create(context: Context): CuadraDatabase =
            Room.databaseBuilder(context.applicationContext, CuadraDatabase::class.java, "cuadra.db").addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11).build()
    }
}

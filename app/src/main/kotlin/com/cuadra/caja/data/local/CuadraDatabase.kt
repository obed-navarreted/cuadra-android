package com.cuadra.caja.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.withTransaction

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
        PromotionEntity::class, PromotionProductEntity::class, SalePromotionEntity::class,
    ],
    version = 13,
    exportSchema = true,
)
abstract class CuadraDatabase : RoomDatabase(), Db {  // MIGRATION_1_2 vive en Migrations.kt
    abstract override fun products(): ProductDao
    abstract override fun sales(): SaleDao
    abstract override fun outbox(): OutboxDao
    abstract override fun directory(): DirectoryDao
    abstract override fun customers(): CustomerDao
    abstract override fun credits(): CreditDao
    abstract override fun templates(): TemplateDao
    abstract override fun cash(): CashDao
    abstract override fun inventory(): InventoryDao
    abstract override fun notifications(): NotificationDao
    abstract override fun reports(): ReportDao

    override suspend fun <R> inTransaction(block: suspend () -> R): R = withTransaction(block)

    companion object {
        /** El archivo de la versión anterior: UNA sola base para todos los negocios (ya no se usa; `LegacyDatabase` lo migra y lo borra). */
        const val LEGACY_FILE = "cuadra.db"

        fun create(context: Context, file: String = LEGACY_FILE): CuadraDatabase =
            Room.databaseBuilder(context.applicationContext, CuadraDatabase::class.java, file).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13).build()
    }
}

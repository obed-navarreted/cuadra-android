package com.cuadra.caja.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "products", indices = [Index("barcode"), Index("name"), Index("isQuick")])
data class ProductEntity(
    @PrimaryKey val id: String,
    val barcode: String?, val shortCode: String?, val name: String, val variant: String?, val categoryId: String?,
    val unit: String, val pricing: String, val priceMinor: Long, val costMinor: Long?,
    val isQuick: Boolean, val quickPosition: Int?, val color: String?,
    val trackStock: Boolean, val stockMilli: Long, val minStockMilli: Long?, val active: Boolean, val rev: Long,
)

@Entity(tableName = "sales", indices = [Index("status"), Index("completedAt")])
data class SaleEntity(
    @PrimaryKey val id: String,
    /** OPEN (en pantalla, solo local) | PARKED | COMPLETED | CANCELLED */
    val status: String,
    val label: String?, val cashRegisterId: String?,
    val subtotalMinor: Long, val discountMinor: Long, val totalMinor: Long,
    val createdByMemberId: String?, val createdByName: String?, val completedByName: String?,
    val completedAt: Long?, val editedByName: String?, val cancelledByName: String?, val cancelReason: String?,
    val lockedByDeviceId: String?, val createdAt: Long, val updatedAt: Long,
    /** 0 mientras el servidor no la ha confirmado. */
    val rev: Long,
)

@Entity(tableName = "sale_items", primaryKeys = ["saleId", "id"], indices = [Index("saleId")])
data class SaleItemEntity(
    val saleId: String, val id: String, val productId: String?, val barcode: String?, val name: String, val variant: String?,
    val unitPriceMinor: Long, val unitCostMinor: Long?, val quantityMilli: Long, val discountMinor: Long, val position: Int,
)

@Entity(tableName = "sale_payments", primaryKeys = ["saleId", "id"], indices = [Index("saleId")])
data class SalePaymentEntity(
    val saleId: String, val id: String, val method: String, val otherLabel: String?, val amountMinor: Long,
    val tenderedMinor: Long?, val changeMinor: Long?, val reference: String?, val position: Int,
    /** Solo en fiado: quién debe, su teléfono (formato internacional) y el cliente vinculado, si lo hay. */
    val debtorLabel: String? = null, val debtorPhone: String? = null, val customerId: String? = null,
)

/**
 * Cola de salida: cada cambio de negocio se guarda junto con su operación en la MISMA transacción, así nunca hay un cambio
 * local sin operación que lo envíe. El servidor aplica cada `opId` una sola vez (PLAN.md 14.1).
 */
@Entity(tableName = "outbox", indices = [Index("entityId"), Index("state", "nextAttemptAt")])
data class OutboxEntity(
    @PrimaryKey(autoGenerate = true) val seq: Long = 0,
    val opId: String, val kind: String, val entityId: String, val payload: String,
    val createdAt: Long, val attempts: Int = 0, val nextAttemptAt: Long = 0,
    /** PENDING (se enviará) | FAILED (el servidor la rechazó: requiere atención) */
    val state: String = STATE_PENDING, val lastCode: String? = null,
) {
    companion object {
        const val STATE_PENDING = "PENDING"
        const val STATE_FAILED = "FAILED"
    }
}

@Entity(tableName = "members")
data class MemberEntity(
    @PrimaryKey val id: String, val displayName: String, val role: String, val status: String, val hasGoogle: Boolean,
    val pinSet: Boolean, val pinMustChange: Boolean, val color: String?,
    /** Hash BCrypt para validar el PIN sin conexión. Solo llega a teléfonos vinculados. */
    val pinHash: String?,
)

@Entity(tableName = "business")
data class BusinessEntity(
    @PrimaryKey val id: String, val name: String, val country: String, val currency: String, val timezone: String,
    val defaultLocale: String, val dayCutoff: String, val inventoryMode: String,
    /** JSON: {"credit":true,...} */
    val modulesJson: String, val posViewsJson: String, val creditRequiresCustomer: Boolean,
    @androidx.room.ColumnInfo(defaultValue = "0") val shiftRequired: Boolean = false,
    /** Diferencia de cierre por encima de la cual se exige una nota (nulo = nunca). */
    val shiftNoteThresholdMinor: Long? = null,
)

@Entity(tableName = "cash_registers")
data class CashRegisterEntity(@PrimaryKey val id: String, val name: String, val active: Boolean)

@Entity(tableName = "sync_state")
data class SyncStateEntity(@PrimaryKey val id: Int = 1, val cursor: Long)

@Entity(tableName = "customers", indices = [Index("name")])
data class CustomerEntity(
    @PrimaryKey val id: String, val name: String, val phone: String?, val notes: String?, val creditLimitMinor: Long?,
    val lastReminderAt: Long?, val archived: Boolean,
    /** Se recalculan desde los fiados; nunca se editan a mano (saldo del cliente = suma de los saldos de sus fiados). */
    val balanceMinor: Long, val oldestOpenAt: Long?, val rev: Long,
)

/** Un fiado: una deuda concreta. Siempre trae el nombre de quien debe; el cliente vinculado es opcional. */
@Entity(tableName = "credits", indices = [Index("customerId"), Index("status"), Index("createdAt")])
data class CreditEntity(
    @PrimaryKey val id: String, val saleId: String?, val customerId: String?, val debtorLabel: String, val debtorPhone: String?,
    val amountMinor: Long, val balanceMinor: Long,
    /** OPEN | PAID | WRITTEN_OFF | CANCELLED */
    val status: String,
    val dueDate: String?, val note: String?, val createdAt: Long, val createdByName: String?, val lastReminderAt: Long?, val rev: Long,
)

@Entity(tableName = "credit_payments", indices = [Index("creditId"), Index("customerId"), Index("groupId")])
data class CreditPaymentEntity(
    @PrimaryKey val id: String, val creditId: String, val customerId: String?, val groupId: String?, val amountMinor: Long,
    val method: String, val reference: String?, val createdByName: String?, val occurredAt: Long, val voided: Boolean, val voidReason: String?, val rev: Long,
    /** Caja donde se cobró: un abono en efectivo entra al cierre de esa caja. */
    val cashRegisterId: String? = null,
)

/** Plantilla de mensaje editada por el negocio. Si no hay una guardada se usa la de fábrica de cada idioma. */
@Entity(tableName = "message_templates", primaryKeys = ["kind", "locale"])
data class TemplateEntity(val kind: String, val locale: String, val body: String, val rev: Long)

/** Categoría de gasto. Una de fábrica trae `key` (la app la traduce); si el negocio la renombra guarda `name`. */
@Entity(tableName = "expense_categories")
data class ExpenseCategoryEntity(@PrimaryKey val id: String, val key: String?, val name: String?, val active: Boolean, val rev: Long)

/** Un gasto. Solo el pagado DEL CAJÓN (`CASH_DRAWER`) afecta el cierre. No se edita: se anula y se registra de nuevo. */
@Entity(tableName = "expenses", indices = [Index("occurredAt"), Index("cashRegisterId")])
data class ExpenseEntity(
    @PrimaryKey val id: String, val categoryId: String?, val description: String?, val amountMinor: Long,
    /** CASH_DRAWER | BANK | CARD | OWNER | OTHER */
    val source: String,
    val cashRegisterId: String?, val createdByName: String?, val createdById: String?, val occurredAt: Long, val voided: Boolean, val voidReason: String?, val rev: Long,
)

/** Retiro (el dueño saca dinero) o entrada (se agrega sencillo). Mueven el cajón; no son gasto ni venta. */
@Entity(tableName = "cash_movements", indices = [Index("occurredAt"), Index("cashRegisterId")])
data class CashMovementEntity(
    @PrimaryKey val id: String,
    /** WITHDRAWAL | DEPOSIT */
    val kind: String,
    val amountMinor: Long, val reason: String?, val cashRegisterId: String, val createdByName: String?, val createdById: String?, val occurredAt: Long,
    val voided: Boolean, val voidReason: String?, val rev: Long,
)

/** Turno de una caja física. Lo esperado NO se guarda aquí: se recalcula siempre desde las ventas y movimientos de la caja en la ventana del turno. */
@Entity(tableName = "shifts", indices = [Index("cashRegisterId", "status")])
data class ShiftEntity(
    @PrimaryKey val id: String, val cashRegisterId: String, val registerName: String?, val openedByName: String?, val openedById: String?, val openedAt: Long,
    val openingFloatMinor: Long, val closedByName: String?, val closedAt: Long?,
    /** Foto del esperado al cerrar, para que una operación tardía no reescriba lo que la persona vio y firmó. */
    val expectedAtCloseMinor: Long?, val countedMinor: Long?, val differenceMinor: Long?, val denominations: String?, val note: String?,
    /** OPEN | CLOSED */
    val status: String,
    val forcedReason: String?, val lateOps: Long, val rev: Long,
)

// ---------- Fase 5: inventario, proveedores, compras ----------

/** Un movimiento de existencias (solo se agregan). `rev = 0` mientras el servidor no lo ha confirmado: cuenta como pendiente en la existencia mostrada. */
@Entity(tableName = "stock_movements", indices = [Index("productId", "occurredAt"), Index("rev")])
data class StockMovementEntity(
    @PrimaryKey val id: String, val productId: String,
    /** INITIAL | PURCHASE | PURCHASE_REVERSAL | SALE | SALE_REVERSAL | ADJUSTMENT | DAMAGE | RETURN */
    val kind: String,
    /** Con signo, en milésimas de la unidad del producto. */
    val quantityMilli: Long, val unitCostMinor: Long?, val refType: String?, val refId: String?, val note: String?,
    val createdByName: String?, val createdById: String?, val occurredAt: Long, val rev: Long,
)

@Entity(tableName = "suppliers", indices = [Index("name")])
data class SupplierEntity(@PrimaryKey val id: String, val name: String, val phone: String?, val notes: String?, val active: Boolean, val rev: Long)

/** Una compra. Lo pagado y lo que se debe se recalculan con los pagos que el teléfono conoce (como el fiado). */
@Entity(tableName = "purchases", indices = [Index("supplierId"), Index("occurredAt")])
data class PurchaseEntity(
    @PrimaryKey val id: String, val supplierId: String?, val supplierName: String?, val totalMinor: Long, val note: String?, val createdByName: String?,
    val occurredAt: Long, val voided: Boolean, val voidReason: String?, val rev: Long,
)

@Entity(tableName = "purchase_items", primaryKeys = ["purchaseId", "id"], indices = [Index("purchaseId")])
data class PurchaseItemEntity(
    val purchaseId: String, val id: String, val productId: String?, val name: String, val quantityMilli: Long, val unitCostMinor: Long, val lineTotalMinor: Long, val position: Int,
)

@Entity(tableName = "supplier_payments", indices = [Index("purchaseId"), Index("supplierId")])
data class SupplierPaymentEntity(
    @PrimaryKey val id: String, val purchaseId: String, val supplierId: String?, val amountMinor: Long,
    /** CASH_DRAWER | BANK | CARD | OWNER | OTHER */
    val source: String,
    val note: String?, val createdByName: String?, val occurredAt: Long, val voided: Boolean, val voidReason: String?, val rev: Long,
)

// ---------- Fase 6: notificaciones ----------

/**
 * Una notificación de la bandeja. `shown` es solo local: si el aviso ya se mostró como notificación del sistema (nunca se repite aunque el servidor
 * la vuelva a mandar). `recipientMemberId`/`recipientDeviceId`: en un teléfono compartido cada persona ve lo suyo y lo del teléfono.
 */
@Entity(tableName = "notifications", indices = [Index("createdAt"), Index("readAt")])
data class NotificationEntity(
    @PrimaryKey val id: String, val type: String,
    /** STOCK | CASH | TEAM | SCHEDULED | CREDIT | PLATFORM: el canal de Android. */
    val channel: String,
    /** JSON con los datos del aviso; la app arma el texto en su idioma. */
    val argsJson: String, val title: String?, val body: String?, val deepLink: String?, val push: Boolean, val createdAt: Long, val readAt: Long?,
    val scheduleId: String?, val recipientMemberId: String?, val recipientDeviceId: String?, val rev: Long,
    @androidx.room.ColumnInfo(defaultValue = "0") val shown: Boolean = false,
)

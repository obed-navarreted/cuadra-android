package com.cuadra.caja.data.remote

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

// Espejo de los contratos de la API (backend/.../*Service.java). Todo nullable lo es también allá.

@Serializable data class GoogleLoginBody(val idToken: String)
@Serializable data class LoginDto(val token: String, val expiresAt: String, val userId: String)

@Serializable data class MembershipDto(val businessId: String, val businessName: String, val memberId: String, val role: String, val currency: String, val timezone: String)
@Serializable data class MeDto(val id: String, val email: String, val fullName: String? = null, val photoUrl: String? = null, val locale: String, val platformAdmin: Boolean, val businesses: List<MembershipDto>)

@Serializable data class CreateBusinessBody(val name: String, val type: String? = null, val country: String? = null, val currency: String? = null, val timezone: String? = null, val locale: String? = null)

@Serializable
data class BusinessDto(
    val id: String, val name: String, val type: String? = null, val country: String, val currency: String, val timezone: String,
    val defaultLocale: String, val dayCutoff: String, val inventoryMode: String, val modules: Map<String, Boolean>, val posViews: List<String>,
    val creditRequiresCustomer: Boolean, val creditDefaultDueDays: Int? = null, val creditOverdueDays: Int, val creditLimitEnforced: Boolean,
    val shiftRequired: Boolean, val shiftNoteThresholdMinor: Long? = null, val status: String,
)

@Serializable data class LinkInfoBody(val deviceName: String, val model: String? = null, val osVersion: String? = null, val appVersion: String? = null)
@Serializable data class SelfLinkedDto(val deviceId: String, val deviceToken: String, val cashRegisterId: String? = null)
@Serializable data class LinkRequestCreatedDto(val code: String, val pollSecret: String, val expiresAt: String)
@Serializable data class LinkStatusDto(val status: String, val expiresAt: String, val deviceToken: String? = null, val deviceId: String? = null, val businessId: String? = null)

@Serializable
data class MemberDto(
    val id: String, val displayName: String, val role: String, val status: String, val hasGoogle: Boolean, val pinSet: Boolean,
    val pinMustChange: Boolean, val color: String? = null, val pinHash: String? = null,
)
@Serializable data class PinBody(val pin: String, val mustChangePin: Boolean = false)

@Serializable
data class ProductDto(
    val id: String, val barcode: String? = null, val shortCode: String? = null, val name: String, val variant: String? = null,
    val categoryId: String? = null, val unit: String, val pricing: String, val priceMinor: Long, val costMinor: Long? = null,
    val isQuick: Boolean, val quickPosition: Int? = null, val color: String? = null, val trackStock: Boolean, val stockMilli: Long,
    val minStockMilli: Long? = null, val active: Boolean, val updatedAt: String, val rev: Long,
)

/** Cuerpo de PRODUCT_UPSERT. */
@Serializable
data class ProductInputDto(
    val barcode: String? = null, val shortCode: String? = null, val name: String, val variant: String? = null, val categoryId: String? = null,
    val unit: String = "UNIT", val pricing: String = "FIXED", val priceMinor: Long, val costMinor: Long? = null, val isQuick: Boolean = false,
    val quickPosition: Int? = null, val color: String? = null, val trackStock: Boolean = false, val minStockMilli: Long? = null, val active: Boolean = true,
)

@Serializable data class CategoryDto(val id: String, val name: String, val active: Boolean, val rev: Long)

@Serializable data class CashRegisterDto(val id: String, val name: String, val active: Boolean)

@Serializable data class MemberRefDto(val id: String, val name: String)
@Serializable
data class SaleItemDto(
    val id: String, val productId: String? = null, val barcode: String? = null, val name: String, val variant: String? = null,
    val unitPriceMinor: Long, val unitCostMinor: Long? = null, val quantityMilli: Long, val discountMinor: Long, val lineTotalMinor: Long,
)
@Serializable
data class SalePaymentDto(
    val id: String, val method: String, val otherLabel: String? = null, val amountMinor: Long, val tenderedMinor: Long? = null,
    val changeMinor: Long? = null, val reference: String? = null, val debtorLabel: String? = null, val debtorPhone: String? = null,
    val customerId: String? = null,
)
@Serializable
data class SaleDto(
    val id: String, val status: String, val label: String? = null, val cashRegisterId: String? = null, val deviceId: String? = null,
    val businessDayId: String? = null, val subtotalMinor: Long, val discountMinor: Long, val totalMinor: Long, val createdBy: MemberRefDto? = null,
    val completedBy: MemberRefDto? = null, val completedAt: String? = null, val editedBy: MemberRefDto? = null, val editedAt: String? = null,
    val cancelledBy: MemberRefDto? = null, val cancelledAt: String? = null, val cancelReason: String? = null, val lockedByDeviceId: String? = null,
    val lockedUntil: String? = null, val createdAt: String, val updatedAt: String, val rev: Long,
    val items: List<SaleItemDto> = emptyList(), val payments: List<SalePaymentDto> = emptyList(),
)

/** Cuerpo de SALE_UPSERT. */
@Serializable
data class SaleInputDto(
    val status: String, val label: String? = null, val cashRegisterId: String? = null, val discountMinor: Long = 0, val createdAt: String? = null,
    val completedAt: String? = null, val items: List<SaleItemInputDto> = emptyList(), val payments: List<SalePaymentInputDto> = emptyList(),
)
@Serializable
data class SaleItemInputDto(
    val id: String, val productId: String? = null, val barcode: String? = null, val name: String, val variant: String? = null,
    val unitPriceMinor: Long, val unitCostMinor: Long? = null, val quantityMilli: Long, val discountMinor: Long = 0,
)
@Serializable
data class SalePaymentInputDto(
    val id: String, val method: String, val otherLabel: String? = null, val amountMinor: Long, val tenderedMinor: Long? = null, val reference: String? = null,
    val debtorLabel: String? = null, val debtorPhone: String? = null, val customerId: String? = null,
)
@Serializable data class CancelBody(val reason: String? = null)

@Serializable data class OpDto(val opId: String, val kind: String, val entityId: String, val payload: JsonElement)
@Serializable data class PushBody(val ops: List<OpDto>, val pendingOps: Int? = null)
@Serializable data class OpResultDto(val opId: String, val status: String, val code: String? = null, val rev: Long? = null)
@Serializable data class PushResponse(val results: List<OpResultDto>)

@Serializable data class ChangeDto(val type: String, val rev: Long, val data: JsonElement)
@Serializable data class PullResponse(val changes: List<ChangeDto>, val cursor: Long, val hasMore: Boolean)

@Serializable data class ConfigDto(
    val minAppVersion: String? = null,
    val donationUrl: String? = null,
    val donationMode: String? = null,
    val supportEmail: String? = null,
    val recommendedAppVersion: String? = null,
    val announcement: AnnouncementDto? = null,
)

/** Anuncio de la plataforma: `deepLink` solo se abre si es `cuadra://…`. */
@Serializable data class AnnouncementDto(val id: String, val title: String = "", val body: String = "", val deepLink: String? = null)

/** Problem Details de la API: `code` es estable y es lo que se traduce en pantalla. */
@Serializable data class ProblemDto(val code: String? = null, val detail: String? = null, val status: Int? = null)

// ---------- Fase 3: fiado, clientes, abonos y plantillas ----------

@Serializable
data class CustomerDto(
    val id: String, val name: String, val phone: String? = null, val notes: String? = null, val creditLimitMinor: Long? = null,
    val lastReminderAt: String? = null, val archived: Boolean, val balanceMinor: Long, val oldestOpenAt: String? = null, val rev: Long,
)

/** Cuerpo de CUSTOMER_UPSERT. */
@Serializable
data class CustomerInputDto(val name: String, val phone: String? = null, val notes: String? = null, val creditLimitMinor: Long? = null, val archived: Boolean = false)

@Serializable
data class CreditDto(
    val id: String, val saleId: String? = null, val customerId: String? = null, val customerName: String? = null, val debtorLabel: String,
    val debtorPhone: String? = null, val amountMinor: Long, val balanceMinor: Long, val paidMinor: Long, val status: String, val dueDate: String? = null,
    val note: String? = null, val createdAt: String, val createdByName: String? = null, val ageDays: Long = 0, val lastReminderAt: String? = null, val rev: Long,
)

/** Cuerpo de CREDIT_UPSERT (fiado sin venta). */
@Serializable
data class ManualCreditInputDto(
    val debtorLabel: String, val debtorPhone: String? = null, val customerId: String? = null, val amountMinor: Long, val note: String? = null,
    val dueDate: String? = null, val createdAt: String? = null,
)

@Serializable
data class CreditPaymentDto(
    val id: String, val creditId: String, val customerId: String? = null, val groupId: String? = null, val amountMinor: Long, val method: String,
    val reference: String? = null, val createdByName: String? = null, val occurredAt: String, val voided: Boolean, val voidReason: String? = null,
    val cashRegisterId: String? = null, val rev: Long,
)

/** Cuerpo de CREDIT_PAYMENT: a un fiado (`creditId`) o al cliente (`customerId`, repartido del más viejo al más nuevo). */
@Serializable
data class PayInputDto(val creditId: String? = null, val customerId: String? = null, val amountMinor: Long, val method: String = "CASH", val reference: String? = null, val occurredAt: String? = null)

@Serializable data class EventInputDto(val creditId: String? = null, val customerId: String? = null, val kind: String, val format: String? = null)
@Serializable data class LinkCustomerBody(val customerId: String)
@Serializable data class ReasonBody(val reason: String? = null)
@Serializable data class TemplateDto(val id: String, val kind: String, val locale: String, val body: String, val rev: Long)
@Serializable data class TemplateBody(val kind: String, val locale: String, val body: String)

// ---------- Fase 4: gastos, movimientos de caja y turnos ----------

@Serializable data class ExpenseCategoryDto(val id: String, val key: String? = null, val name: String? = null, val active: Boolean, val rev: Long)
@Serializable data class CategoryInputDto(val name: String? = null, val active: Boolean = true)

@Serializable
data class ExpenseDto(
    val id: String, val categoryId: String? = null, val categoryKey: String? = null, val categoryName: String? = null, val description: String? = null,
    val amountMinor: Long, val source: String, val cashRegisterId: String? = null, val createdByName: String? = null, val createdById: String? = null,
    val occurredAt: String, val voided: Boolean, val voidReason: String? = null, val rev: Long,
)

/** Cuerpo de EXPENSE_UPSERT. */
@Serializable
data class ExpenseInputDto(val categoryId: String? = null, val description: String? = null, val amountMinor: Long, val source: String, val cashRegisterId: String? = null, val occurredAt: String? = null)

@Serializable
data class CashMovementDto(
    val id: String, val kind: String, val amountMinor: Long, val reason: String? = null, val cashRegisterId: String, val createdByName: String? = null,
    val createdById: String? = null, val occurredAt: String, val voided: Boolean, val voidReason: String? = null, val rev: Long,
)

/** Cuerpo de CASH_MOVEMENT_UPSERT. */
@Serializable data class MovementInputDto(val kind: String, val amountMinor: Long, val reason: String? = null, val cashRegisterId: String? = null, val occurredAt: String? = null)

@Serializable
data class ShiftDto(
    val id: String, val cashRegisterId: String, val registerName: String? = null, val openedBy: MemberRefDto, val openedAt: String, val openingFloatMinor: Long,
    val closedBy: MemberRefDto? = null, val closedAt: String? = null, val expectedAtCloseMinor: Long? = null, val countedMinor: Long? = null, val differenceMinor: Long? = null,
    val denominations: String? = null, val note: String? = null, val status: String, val forcedReason: String? = null, val lateOps: Long = 0, val reopenedCount: Int = 0, val rev: Long,
)

/** Cuerpo de SHIFT_OPEN. */
@Serializable data class OpenShiftInputDto(val cashRegisterId: String? = null, val openingFloatMinor: Long, val openedAt: String? = null)

/** Cuerpo de SHIFT_CLOSE. */
@Serializable
data class CloseShiftInputDto(val countedMinor: Long, val denominations: String? = null, val note: String? = null, val closedAt: String? = null, val force: Boolean = false, val forcedReason: String? = null)

@Serializable data class UpdateBusinessBody(val modules: Map<String, Boolean>? = null, val shiftRequired: Boolean? = null, val shiftNoteThresholdMinor: Long? = null)

// ---------- Fase 5: inventario, proveedores y compras ----------

@Serializable
data class StockMovementDto(
    val id: String, val productId: String, val kind: String, val quantityMilli: Long, val unitCostMinor: Long? = null, val refType: String? = null, val refId: String? = null,
    val note: String? = null, val createdByName: String? = null, val createdById: String? = null, val occurredAt: String, val rev: Long,
)

/** Cuerpo de STOCK_MOVEMENT_ADD. INITIAL y ADJUSTMENT llevan lo contado; DAMAGE y RETURN la cantidad en positivo. */
@Serializable
data class StockMovementInputDto(val productId: String, val kind: String, val quantityMilli: Long? = null, val countedMilli: Long? = null, val note: String? = null, val occurredAt: String? = null)

@Serializable data class SupplierDto(val id: String, val name: String, val phone: String? = null, val notes: String? = null, val active: Boolean, val balanceMinor: Long = 0, val rev: Long)

/** Cuerpo de SUPPLIER_UPSERT. */
@Serializable data class SupplierInputDto(val name: String, val phone: String? = null, val notes: String? = null, val active: Boolean = true)

@Serializable data class PurchaseLineDto(val id: String, val productId: String? = null, val name: String, val quantityMilli: Long, val unitCostMinor: Long, val lineTotalMinor: Long)

@Serializable
data class PurchaseDto(
    val id: String, val supplierId: String? = null, val supplierName: String? = null, val totalMinor: Long, val paidMinor: Long = 0, val balanceMinor: Long = 0, val note: String? = null,
    val createdByName: String? = null, val createdById: String? = null, val occurredAt: String, val voided: Boolean, val voidReason: String? = null,
    val lines: List<PurchaseLineDto> = emptyList(), val rev: Long,
)

@Serializable data class PurchaseLineInputDto(val id: String, val productId: String? = null, val name: String? = null, val quantityMilli: Long, val unitCostMinor: Long)

/** Cuerpo de PURCHASE_REGISTER. */
@Serializable
data class PurchaseInputDto(
    val supplierId: String? = null, val supplierName: String? = null, val lines: List<PurchaseLineInputDto>, val paidMinor: Long? = null, val paidSource: String? = null,
    val note: String? = null, val occurredAt: String? = null,
)

@Serializable
data class SupplierPaymentDto(
    val id: String, val purchaseId: String, val supplierId: String? = null, val amountMinor: Long, val source: String, val note: String? = null, val createdByName: String? = null,
    val createdById: String? = null, val occurredAt: String, val voided: Boolean, val voidReason: String? = null, val rev: Long,
)

/** Cuerpo de SUPPLIER_PAYMENT. */
@Serializable data class SupplierPaymentInputDto(val purchaseId: String, val amountMinor: Long, val source: String, val note: String? = null, val occurredAt: String? = null)

// ---------- Fase 6: notificaciones ----------

@Serializable
data class NotificationDto(
    val id: String, val type: String, val channel: String, val args: kotlinx.serialization.json.JsonObject = kotlinx.serialization.json.JsonObject(emptyMap()),
    val title: String? = null, val body: String? = null, val deepLink: String? = null, val push: Boolean = true, val createdAt: String, val readAt: String? = null,
    val scheduleId: String? = null, val recipientMemberId: String? = null, val recipientDeviceId: String? = null, val rev: Long,
)

/** A quién: `all`, por rol o por persona (se pueden combinar). */
@Serializable data class ScheduleAudienceDto(val all: Boolean? = null, val roles: List<String> = emptyList(), val memberIds: List<String> = emptyList(), val deviceIds: List<String> = emptyList())

/** Cuándo: ONCE (`at` = "yyyy-MM-ddTHH:mm" local), DAILY, WEEKLY (`days` ISO 1=lunes…7), MONTHLY (`dayOfMonth`), EVERY_N_DAYS (`everyDays`); `time` = "HH:mm". */
@Serializable
data class ScheduleRuleDto(
    val type: String, val time: String? = null, val at: String? = null, val days: List<Int>? = null, val dayOfMonth: Int? = null, val everyDays: Int? = null,
    val startDate: String? = null, val endDate: String? = null,
)

@Serializable data class ScheduleInputDto(val title: String, val body: String, val deepLink: String? = null, val audience: ScheduleAudienceDto, val rule: ScheduleRuleDto, val active: Boolean = true)

@Serializable
data class ScheduleDto(
    val id: String, val title: String, val body: String, val deepLink: String? = null, val audience: ScheduleAudienceDto, val rule: ScheduleRuleDto, val timezone: String,
    val nextRunAt: String? = null, val lastRunAt: String? = null, val active: Boolean, val sent: Long = 0, val read: Long = 0, val createdAt: String,
)

@Serializable data class ScheduleRunDto(val runAt: String, val status: String, val recipients: Int)
@Serializable data class PreferenceBody(val type: String, val enabled: Boolean)
@Serializable data class ActiveBody(val active: Boolean)
@Serializable data class PushTokenBody(val fcmToken: String, val platform: String = "ANDROID", val locale: String? = null, val appVersion: String? = null)

@Serializable
data class NotificationSettingsDto(val quietStart: String, val quietEnd: String, val summaryEnabled: Boolean, val summaryTime: String, val shiftReminderTime: String? = null, val staleHours: Int)

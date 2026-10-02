package com.cuadra.caja.data.remote

import com.cuadra.caja.domain.DayRuleDto
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
    /** Historial de zona/corte (ADR 0011): la última puede regir desde una jornada futura (`dayRuleEffectiveFrom`). */
    val dayRules: List<DayRuleDto> = emptyList(), val dayRuleEffectiveFrom: String? = null,
    /** Código del negocio (5 dígitos): con él, el usuario y el PIN, cada persona entra en su teléfono (ADR 0012). Solo lo trae el servidor a dueño/admin. */
    val accessCode: String? = null,
    /** La moneda ya no se puede cambiar: hay actividad registrada en ella (antes de la primera venta, sí). */
    val currencyLocked: Boolean = false,
    /** Cobro en caja (ADR 0015). */
    val registerCheckout: Boolean = false,
)

/** Un país con la moneda, zona horaria e idioma que sugiere al crear un negocio (`GET /api/config/countries`). */
@Serializable data class CountryDto(val code: String, val currency: String, val timezone: String, val locale: String = "es")

@Serializable data class LinkInfoBody(val deviceName: String, val model: String? = null, val osVersion: String? = null, val appVersion: String? = null)
@Serializable data class SelfLinkedDto(val deviceId: String, val deviceToken: String, val cashRegisterId: String? = null)

/** Entrar con código del negocio + PIN (`POST /api/auth/member-login`, ADR 0012). */
@Serializable data class MemberLoginBody(
    val businessCode: String, val pin: String,
    val deviceName: String? = null, val model: String? = null, val osVersion: String? = null, val appVersion: String? = null,
)
@Serializable data class MemberLoginResult(
    val deviceId: String, val deviceToken: String, val businessId: String, val memberId: String, val memberName: String, val role: String, val pinMustChange: Boolean = false,
)
@Serializable data class AccessCodeDto(val accessCode: String)
@Serializable data class SetAccessCodeBody(val accessCode: String)

@Serializable
data class MemberDto(
    val id: String, val displayName: String, val role: String, val status: String, val hasGoogle: Boolean, val pinSet: Boolean,
    val pinMustChange: Boolean, val color: String? = null, val pinHash: String? = null,
)
@Serializable data class PinBody(val pin: String, val mustChangePin: Boolean = false)

/** Elevación por PIN verificado (ADR 0012, 2026-10-01): el servidor confirma el PIN de una persona en este teléfono. */
@Serializable data class VerifyPinBody(val pin: String)
@Serializable data class VerifiedPinDto(val memberId: String, val role: String, val baseRole: String? = null, val grantedAt: String? = null, val expiresAt: String)
@Serializable data class DeviceGrantDto(val memberId: String, val grantedAt: String? = null, val expiresAt: String)
/** `GET /api/devices/me`: el rol base del teléfono (el de quien lo vinculó) y los permisos de PIN vigentes. */
@Serializable data class DeviceSelfDto(val deviceId: String, val businessId: String, val kind: String? = null, val baseRole: String? = null, val grants: List<DeviceGrantDto> = emptyList())

// ---------- Equipo: personas y teléfonos (solo en línea) ----------

@Serializable data class CreateMemberBody(val displayName: String, val role: String, val pin: String, val mustChangePin: Boolean = false)

/** Solo viaja lo que cambia (`explicitNulls = false`): un campo en nulo no se envía. */
@Serializable data class UpdateMemberBody(val displayName: String? = null, val role: String? = null, val status: String? = null, val color: String? = null)

@Serializable
data class DeviceDto(
    val id: String, val name: String, val model: String? = null, val appVersion: String? = null, val cashRegisterId: String? = null,
    val cashRegisterName: String? = null, val linkedAt: String? = null, val lastSeenAt: String? = null, val lastSyncAt: String? = null,
    val pendingOps: Int = 0, val revoked: Boolean = false,
)

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
    /** Cuánto de esta línea ya se devolvió. */
    val returnedMilli: Long = 0,
)
@Serializable data class ReturnItemDto(val id: String? = null, val saleItemId: String, val productId: String? = null, val name: String = "", val quantityMilli: Long, val amountMinor: Long = 0)
@Serializable data class RefundDto(val method: String, val amountMinor: Long, val creditId: String? = null)
/** Una devolución (cuenta en la jornada en que se hizo, `occurredAt`). */
@Serializable
data class ReturnDto(
    val id: String, val saleId: String, val reason: String, val refundMethod: String, val totalMinor: Long, val createdBy: MemberRefDto? = null,
    val occurredAt: String, val items: List<ReturnItemDto> = emptyList(), val refunds: List<RefundDto> = emptyList(),
)
/** Cuerpo de SALE_RETURN (cola) y de `PUT sales/{id}/returns/{returnId}`. */
@Serializable
data class ReturnInputDto(val saleId: String, val items: List<ReturnLineInputDto>, val reason: String, val refundMethod: String, val occurredAt: String? = null)
@Serializable data class ReturnLineInputDto(val saleItemId: String, val quantityMilli: Long)
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
    /** Guardada aparte porque chocó con otra versión: revisar. */
    val conflictOfSaleId: String? = null,
    /** LATE_AFTER_DISABLE | CLOCK_ADJUSTED (para revisar). */
    val reviewFlag: String? = null,
    val returnedMinor: Long = 0, val returns: List<ReturnDto> = emptyList(),
    /** Cobro en caja (ADR 0015): cuándo y quién la envió a caja; quién la tiene abierta ahora. */
    val sentToRegisterAt: String? = null, val sentBy: MemberRefDto? = null, val lockedBy: MemberRefDto? = null, val pendingCheckout: Boolean = false,
    /** Promociones por cantidad que aplicó (su descuento ya está en las líneas). */
    val promotions: List<SalePromotionDto> = emptyList(), val promotionDiscountMinor: Long = 0,
)

/** Una promoción aplicada en una venta: «3 por C$ 100», unidades en paquetes y descuento (lo cobrado; el servidor no lo recalcula). */
@Serializable
data class SalePromotionDto(
    val promotionId: String? = null, val name: String, val quantity: Int, val priceMinor: Long, val units: Long, val discountMinor: Long,
)

/** Promoción por cantidad del negocio (sincronización y `GET /promotions`). `deleted`: se quita del teléfono. */
@Serializable
data class PromotionDto(
    val id: String, val name: String, val productIds: List<String> = emptyList(), val quantity: Int, val priceMinor: Long, val active: Boolean = true,
    val startsOn: String? = null, val endsOn: String? = null, val deleted: Boolean = false, val state: String? = null, val rev: Long = 0,
)

/** Cuerpo de PROMOTION_UPSERT (cola) y de `PUT /promotions/{id}`. */
@Serializable
data class PromotionInputDto(
    val name: String, val productIds: List<String>, val quantity: Int, val priceMinor: Long, val active: Boolean = true,
    val startsOn: String? = null, val endsOn: String? = null,
)

/** Cuerpo de SALE_UPSERT. */
@Serializable
data class SaleInputDto(
    val status: String, val label: String? = null, val cashRegisterId: String? = null, val discountMinor: Long = 0, val createdAt: String? = null,
    val completedAt: String? = null, val items: List<SaleItemInputDto> = emptyList(), val payments: List<SalePaymentInputDto> = emptyList(),
    /** "PARKED" al cobrar una cuenta apartada retomada: si ya se cobró o descartó en otro teléfono, el servidor la guarda aparte en vez de perderla. */
    val fromStatus: String? = null,
    /** Cobro en caja (ADR 0015), solo con PARKED: true = «Enviar a caja»; nulo = no cambia (no se envía). */
    val sendToRegister: Boolean? = null,
    /** Promociones aplicadas (su descuento ya va en las líneas). */
    val promotions: List<SalePromotionDto> = emptyList(),
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

/** `memberId`: quién hizo la operación (el servidor la aplica con esa persona); `createdAt`: hora del teléfono al hacerla (ISO-8601). */
@Serializable data class OpDto(val opId: String, val kind: String, val entityId: String, val payload: JsonElement, val memberId: String? = null, val createdAt: String? = null)
@Serializable data class PushBody(val ops: List<OpDto>, val pendingOps: Int? = null)
/** `detail`: datos para explicar un rechazo (límite y saldo, id de la copia de una venta en conflicto…). */
@Serializable data class OpResultDto(val opId: String, val status: String, val code: String? = null, val rev: Long? = null, val detail: kotlinx.serialization.json.JsonObject? = null)
@Serializable data class PushResponse(val results: List<OpResultDto>)

@Serializable data class ChangeDto(val type: String, val rev: Long, val data: JsonElement)
@Serializable data class PullResponse(val changes: List<ChangeDto>, val cursor: Long, val hasMore: Boolean)

@Serializable data class ConfigDto(
    val minAppVersion: String? = null,
    val supportEmail: String? = null,
    /** WhatsApp de contacto y apoyo (solo dígitos con código de país). */
    val supportWhatsapp: String? = null,
    val recommendedAppVersion: String? = null,
    val announcement: AnnouncementDto? = null,
    /** El panel web (la consola de la plataforma está en `panelUrl/console`). */
    val panelUrl: String? = null,
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

/**
 * Actualización parcial del negocio (`PUT /b/{id}`): un campo ausente = sin cambio (`explicitNulls = false` no envía los nulos). Los dos campos que se pueden dejar
 * sin valor (días de vencimiento por defecto y umbral de nota del turno) se limpian con su bandera explícita.
 */
@Serializable
data class UpdateBusinessBody(
    val modules: Map<String, Boolean>? = null, val shiftRequired: Boolean? = null, val shiftNoteThresholdMinor: Long? = null,
    val name: String? = null, val type: String? = null, val timezone: String? = null, val dayCutoff: String? = null, val posViews: List<String>? = null,
    val creditRequiresCustomer: Boolean? = null, val creditDefaultDueDays: Int? = null, val creditOverdueDays: Int? = null, val creditLimitEnforced: Boolean? = null,
    val clearCreditDefaultDueDays: Boolean? = null,
    /** Solo antes de la primera venta (el servidor responde CURRENCY_LOCKED después). */
    val currency: String? = null, val country: String? = null,
    /** Cobro en caja (ADR 0015). */
    val registerCheckout: Boolean? = null,
    /** Al apagar «Cobro en caja» con cuentas por cobrar: true = anularlas (si no, el servidor responde REGISTER_QUEUE_NOT_EMPTY). */
    val confirmDiscardPending: Boolean? = null,
) {
    /** ¿No cambia nada? (para no llamar al servidor con un cuerpo vacío). */
    val isEmpty: Boolean get() = this == UpdateBusinessBody()
}

// ---------- Plan, actividad y ayuda (solo en línea) ----------

@Serializable data class PlanLimitsDto(val members: Int = 0, val devices: Int = 0, val schedules: Int = 0, val reportHistoryDays: Int = 0, val export: Boolean = false, val multipleBusinesses: Boolean = false, val webSections: List<String> = emptyList())
@Serializable data class PlanUsageDto(val members: Int = 0, val devices: Int = 0, val schedules: Int = 0)
@Serializable
data class PlanDto(
    val plan: String, val status: String? = null, val trialEndsAt: String? = null, val currentPeriodEnd: String? = null, val trialing: Boolean = false,
    val trialDaysLeft: Int = 0, val limits: PlanLimitsDto? = null, val usage: PlanUsageDto? = null,
)

@Serializable data class ActivityEntryDto(val id: Long, val action: String, val entity: String? = null, val detail: String? = null, val actorName: String? = null, val byPlatform: Boolean = false, val at: String)

@Serializable
data class TicketBody(
    val category: String, val message: String, val replyToEmail: String? = null, val replyToPhone: String? = null, val diagnostics: String? = null,
    val locale: String? = null, val businessId: String? = null,
)
@Serializable data class TicketCreatedDto(val id: String, val reference: String)

/** Cuerpo de `PUT message-templates/{kind}/{locale}`. */
@Serializable data class TemplateTextBody(val body: String)

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

/** Un cambio de un campo en el historial de un producto: los valores pueden ser texto, número, booleano o nulo. */
@Serializable data class FieldChangeDto(val from: kotlinx.serialization.json.JsonElement? = null, val to: kotlinx.serialization.json.JsonElement? = null)

@Serializable
data class ProductHistoryEntryDto(
    val id: Long, val action: String, val actorMemberId: String? = null, val actorName: String? = null, val actorRole: String? = null,
    val at: String, val changes: Map<String, FieldChangeDto> = emptyMap(), val name: String? = null,
)

// ---------- Ventas y cierre del día (solo en línea: dueño y admins) ----------

@Serializable data class PageDto<T>(val items: List<T>, val page: Int, val size: Int, val total: Long, val last: Boolean)
@Serializable data class MethodAmountDto(val method: String, val amountMinor: Long)
@Serializable data class SalesTotalsDto(
    val count: Long, val totalMinor: Long, val discountMinor: Long = 0, val averageTicketMinor: Long = 0, val cancelledCount: Long = 0,
    val returnsCount: Long = 0, val returnsMinor: Long = 0, val priorCancelledCount: Long = 0, val priorCancelledMinor: Long = 0, val netMinor: Long? = null,
    /** «Descuentos por promociones» del periodo (ya restados del total). */
    val promotionDiscountMinor: Long = 0,
)
/** Una fila de `reports/sales/breakdown` (por persona: `key` = id del miembro, `label` = su nombre). */
@Serializable data class BreakdownRowDto(val key: String? = null, val label: String? = null, val count: Long = 0, val totalMinor: Long = 0)
@Serializable data class SalesReportDto(val sales: SalesTotalsDto, val byMethod: List<MethodAmountDto> = emptyList())

@Serializable
data class DayCloseDto(
    val date: String, val startsAt: String, val endsAt: String, val salesCount: Long, val salesMinor: Long,
    val byMethod: List<MethodAmountDto> = emptyList(), val creditCollected: List<MethodAmountDto> = emptyList(),
    val drawerExpensesMinor: Long = 0, val otherExpensesMinor: Long = 0, val withdrawalsMinor: Long = 0, val depositsMinor: Long = 0,
    val expectedCashMinor: Long = 0, val cancelledCount: Long = 0, val cancelledMinor: Long = 0,
    /** Devoluciones hechas en esta jornada y lo que salió del cajón por ellas. */
    val returnsCount: Long = 0, val returnsMinor: Long = 0, val cashRefundsMinor: Long = 0,
    /** Ventas de jornadas anteriores anuladas en esta (siguen en su día; aquí restan). */
    val priorCancelledCount: Long = 0, val priorCancelledMinor: Long = 0, val priorCancelledCashMinor: Long = 0,
    val netSalesMinor: Long? = null,
    /** Gastos, abonos, retiros y entradas de días anteriores anulados en esta jornada. */
    val laterVoids: List<LaterVoidDto> = emptyList(),
    /** Cobro en caja (ADR 0015): cuentas que seguían por cobrar en caja al terminar la jornada (no son ventas). */
    val pendingCheckoutCount: Long = 0, val pendingCheckoutMinor: Long = 0,
    /** «Descuentos por promociones» de la jornada (ya restados de las ventas). */
    val promotionDiscountMinor: Long = 0,
)
@Serializable data class LaterVoidDto(val kind: String, val count: Long, val amountMinor: Long, val cashEffectMinor: Long)
/** Un teléfono del negocio con operaciones sin enviar o sin sincronizar hace más de una hora. */
@Serializable data class DeviceSyncDto(val deviceId: String, val name: String, val pendingOps: Int = 0, val lastSyncAt: String? = null, val stale: Boolean = false)
@Serializable data class DailyCloseDto(val days: List<DayCloseDto> = emptyList(), val syncWarnings: List<DeviceSyncDto> = emptyList())

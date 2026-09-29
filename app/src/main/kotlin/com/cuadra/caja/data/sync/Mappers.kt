package com.cuadra.caja.data.sync

import com.cuadra.caja.data.local.BusinessEntity
import com.cuadra.caja.data.local.CashRegisterEntity
import com.cuadra.caja.data.local.CashMovementEntity
import com.cuadra.caja.data.local.NotificationEntity
import com.cuadra.caja.data.local.PurchaseEntity
import com.cuadra.caja.data.local.PurchaseItemEntity
import com.cuadra.caja.data.local.StockMovementEntity
import com.cuadra.caja.data.local.SupplierEntity
import com.cuadra.caja.data.local.SupplierPaymentEntity
import com.cuadra.caja.data.local.CreditEntity
import com.cuadra.caja.data.local.ExpenseCategoryEntity
import com.cuadra.caja.data.local.ExpenseEntity
import com.cuadra.caja.data.local.ShiftEntity
import com.cuadra.caja.data.local.CreditPaymentEntity
import com.cuadra.caja.data.local.CustomerEntity
import com.cuadra.caja.data.local.MemberEntity
import com.cuadra.caja.data.local.ProductEntity
import com.cuadra.caja.data.local.SaleEntity
import com.cuadra.caja.data.local.SaleItemEntity
import com.cuadra.caja.data.local.SalePaymentEntity
import com.cuadra.caja.data.local.TemplateEntity
import com.cuadra.caja.data.remote.BusinessDto
import com.cuadra.caja.data.remote.CashRegisterDto
import com.cuadra.caja.data.remote.CashMovementDto
import com.cuadra.caja.data.remote.NotificationDto
import com.cuadra.caja.data.remote.PurchaseDto
import com.cuadra.caja.data.remote.StockMovementDto
import com.cuadra.caja.data.remote.SupplierDto
import com.cuadra.caja.data.remote.SupplierPaymentDto
import com.cuadra.caja.data.remote.CreditDto
import com.cuadra.caja.data.remote.ExpenseCategoryDto
import com.cuadra.caja.data.remote.ExpenseDto
import com.cuadra.caja.data.remote.ShiftDto
import com.cuadra.caja.data.remote.CreditPaymentDto
import com.cuadra.caja.data.remote.CustomerDto
import com.cuadra.caja.data.remote.TemplateDto
import com.cuadra.caja.data.remote.MemberDto
import com.cuadra.caja.data.remote.ProductDto
import com.cuadra.caja.data.remote.SaleDto
import java.time.Instant
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

private val json = Json { ignoreUnknownKeys = true }

private fun millis(iso: String?): Long? = iso?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }

fun ProductDto.toEntity() = ProductEntity(
    id, barcode, shortCode, name, variant, categoryId, unit, pricing, priceMinor, costMinor, isQuick, quickPosition, color, trackStock,
    stockMilli, minStockMilli, active, rev,
)

fun MemberDto.toEntity() = MemberEntity(id, displayName, role, status, hasGoogle, pinSet, pinMustChange, color, pinHash)

fun CashRegisterDto.toEntity() = CashRegisterEntity(id, name, active)

fun BusinessDto.toEntity() = BusinessEntity(
    id, name, country, currency, timezone, defaultLocale, dayCutoff, inventoryMode,
    json.encodeToString(MapSerializer(String.serializer(), Boolean.serializer()), modules),
    json.encodeToString(ListSerializer(String.serializer()), posViews), creditRequiresCustomer, shiftRequired, shiftNoteThresholdMinor,
)

fun BusinessEntity.modules(): Map<String, Boolean> = json.decodeFromString(MapSerializer(String.serializer(), Boolean.serializer()), modulesJson)
fun BusinessEntity.posViews(): List<String> = json.decodeFromString(ListSerializer(String.serializer()), posViewsJson)

/** Lo que el servidor sabe de una venta: cabecera, líneas y pagos listos para guardar. */
data class SaleRows(val sale: SaleEntity, val items: List<SaleItemEntity>, val payments: List<SalePaymentEntity>)

fun SaleDto.toRows(): SaleRows {
    val sale = SaleEntity(
        id = id, status = status, label = label, cashRegisterId = cashRegisterId, subtotalMinor = subtotalMinor, discountMinor = discountMinor,
        totalMinor = totalMinor, createdByMemberId = createdBy?.id, createdByName = createdBy?.name, completedByName = completedBy?.name,
        completedAt = millis(completedAt), editedByName = editedBy?.name, cancelledByName = cancelledBy?.name, cancelReason = cancelReason,
        lockedByDeviceId = lockedByDeviceId, createdAt = millis(createdAt) ?: 0, updatedAt = millis(updatedAt) ?: 0, rev = rev,
    )
    return SaleRows(
        sale,
        items.mapIndexed { i, it -> SaleItemEntity(id, it.id, it.productId, it.barcode, it.name, it.variant, it.unitPriceMinor, it.unitCostMinor, it.quantityMilli, it.discountMinor, i) },
        payments.mapIndexed { i, p ->
            SalePaymentEntity(id, p.id, p.method, p.otherLabel, p.amountMinor, p.tenderedMinor, p.changeMinor, p.reference, i, p.debtorLabel, p.debtorPhone, p.customerId)
        },
    )
}

fun CustomerDto.toEntity() = CustomerEntity(id, name, phone, notes, creditLimitMinor, millis(lastReminderAt), archived, balanceMinor, millis(oldestOpenAt), rev)

fun CreditDto.toEntity() = CreditEntity(
    id, saleId, customerId, debtorLabel, debtorPhone, amountMinor, balanceMinor, status, dueDate, note, millis(createdAt) ?: 0, createdByName, millis(lastReminderAt), rev,
)

fun CreditPaymentDto.toEntity() = CreditPaymentEntity(id, creditId, customerId, groupId, amountMinor, method, reference, createdByName, millis(occurredAt) ?: 0, voided, voidReason, rev, cashRegisterId)

fun TemplateDto.toEntity() = TemplateEntity(kind, locale, body, rev)

fun ExpenseCategoryDto.toEntity() = ExpenseCategoryEntity(id, key, name, active, rev)

fun ExpenseDto.toEntity() = ExpenseEntity(id, categoryId, description, amountMinor, source, cashRegisterId, createdByName, createdById, millis(occurredAt) ?: 0, voided, voidReason, rev)

fun CashMovementDto.toEntity() = CashMovementEntity(id, kind, amountMinor, reason, cashRegisterId, createdByName, createdById, millis(occurredAt) ?: 0, voided, voidReason, rev)

fun ShiftDto.toEntity() = ShiftEntity(
    id, cashRegisterId, registerName, openedBy.name, openedBy.id, millis(openedAt) ?: 0, openingFloatMinor, closedBy?.name, millis(closedAt), expectedAtCloseMinor, countedMinor,
    differenceMinor, denominations, note, status, forcedReason, lateOps, rev,
)

fun StockMovementDto.toEntity() = StockMovementEntity(id, productId, kind, quantityMilli, unitCostMinor, refType, refId, note, createdByName, createdById, millis(occurredAt) ?: 0, rev)

fun SupplierDto.toEntity() = SupplierEntity(id, name, phone, notes, active, rev)

fun PurchaseDto.toEntity() = PurchaseEntity(id, supplierId, supplierName, totalMinor, note, createdByName, millis(occurredAt) ?: 0, voided, voidReason, rev)

fun PurchaseDto.itemEntities() = lines.mapIndexed { i, l -> PurchaseItemEntity(id, l.id, l.productId, l.name, l.quantityMilli, l.unitCostMinor, l.lineTotalMinor, i) }

fun SupplierPaymentDto.toEntity() = SupplierPaymentEntity(id, purchaseId, supplierId, amountMinor, source, note, createdByName, millis(occurredAt) ?: 0, voided, voidReason, rev)

fun NotificationDto.toEntity(shown: Boolean = false, localReadAt: Long? = null) = NotificationEntity(
    id, type, channel, args.toString(), title, body, deepLink, push, millis(createdAt) ?: 0, millis(readAt) ?: localReadAt, scheduleId, recipientMemberId, recipientDeviceId, rev, shown,
)

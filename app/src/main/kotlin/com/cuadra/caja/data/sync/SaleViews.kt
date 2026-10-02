package com.cuadra.caja.data.sync

import com.cuadra.caja.data.local.SaleEntity
import com.cuadra.caja.data.local.SaleItemEntity
import com.cuadra.caja.data.local.SalePaymentEntity
import com.cuadra.caja.data.local.SaleReturnEntity
import com.cuadra.caja.domain.ReturnLineView
import com.cuadra.caja.domain.SaleReturnView
import com.cuadra.caja.data.remote.SaleDto
import com.cuadra.caja.domain.SaleLineView
import com.cuadra.caja.domain.SalePaymentView
import com.cuadra.caja.domain.SaleView
import java.time.Instant

private fun instant(iso: String?): Long? = iso?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }

/** Una venta que trajo el servidor (lista completa del dueño/admin). */
fun SaleDto.toView() = SaleView(
    id, status, label, subtotalMinor, discountMinor, totalMinor, instant(completedAt) ?: instant(createdAt) ?: 0, completedBy?.name ?: createdBy?.name,
    editedBy?.name, instant(editedAt), cancelledBy?.name, instant(cancelledAt), cancelReason,
    items.map { SaleLineView(it.name, it.variant, it.quantityMilli, it.unitPriceMinor, it.discountMinor, it.lineTotalMinor, it.id, it.returnedMilli) },
    payments.map { SalePaymentView(it.method, it.otherLabel, it.amountMinor, it.tenderedMinor, it.changeMinor, it.reference, it.debtorLabel) },
    conflict = conflictOfSaleId != null, reviewFlag = reviewFlag, returnedMinor = returnedMinor,
    returns = returns.map { r ->
        SaleReturnView(r.id, r.reason, r.refundMethod, r.totalMinor, r.createdBy?.name, instant(r.occurredAt) ?: 0,
            r.items.map { ReturnLineView(it.saleItemId, it.name, it.quantityMilli, it.amountMinor) }, r.refunds.map { it.method to it.amountMinor })
    },
    completedById = completedBy?.id, completedAtMillis = instant(completedAt), takenBy = createdBy?.name, sentBy = sentBy?.name, sentAtMillis = instant(sentToRegisterAt),
    promotions = promotions.map { com.cuadra.caja.domain.SalePromotionView(it.name, it.quantity, it.priceMinor, it.units, it.discountMinor) },
)

/** Una venta guardada en este teléfono (cajero, o sin conexión). `rev == 0` = el servidor aún no la confirmó. */
fun SaleEntity.toView(
    items: List<SaleItemEntity>, payments: List<SalePaymentEntity>, returns: List<SaleReturnEntity> = emptyList(),
    promotions: List<com.cuadra.caja.data.local.SalePromotionEntity> = emptyList(),
) = SaleView(
    id, status, label, subtotalMinor, discountMinor, totalMinor, completedAt ?: createdAt, completedByName ?: createdByName,
    editedByName, editedAt, cancelledByName, cancelledAt, cancelReason,
    items.sortedBy { it.position }.map { SaleLineView(it.name, it.variant, it.quantityMilli, it.unitPriceMinor, it.discountMinor, lineTotal(it), it.id, it.returnedMilli) },
    payments.sortedBy { it.position }.map { SalePaymentView(it.method, it.otherLabel, it.amountMinor, it.tenderedMinor, it.changeMinor, it.reference, it.debtorLabel) },
    unsynced = rev == 0L, conflict = conflictOfSaleId != null, reviewFlag = reviewFlag, returnedMinor = returnedMinor,
    returns = returns.map { com.cuadra.caja.data.repo.SaleReturns.view(it) }, completedById = completedByMemberId ?: createdByMemberId, completedAtMillis = completedAt,
    takenBy = createdByName, sentBy = sentByName, sentAtMillis = sentToRegisterAt,
    promotions = promotions.sortedBy { it.position }.map { com.cuadra.caja.domain.SalePromotionView(it.name, it.quantity, it.priceMinor, it.units, it.discountMinor) },
)

/** Igual que el servidor: precio × cantidad (redondeado al mínimo) − descuento de la línea. */
private fun lineTotal(i: SaleItemEntity): Long = Math.floorDiv(i.unitPriceMinor * i.quantityMilli + 500, 1000L) - i.discountMinor

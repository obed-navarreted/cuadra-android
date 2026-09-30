package com.cuadra.caja.data.repo

import com.cuadra.caja.data.local.SaleDao
import com.cuadra.caja.data.local.SaleReturnEntity
import com.cuadra.caja.data.remote.RefundDto
import com.cuadra.caja.data.remote.ReturnItemDto
import com.cuadra.caja.domain.ReturnLineView
import com.cuadra.caja.domain.SaleReturnView
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** Devoluciones guardadas en el teléfono: lo devuelto por línea y en total SIEMPRE sale de ellas (confirmadas + pendientes). */
object SaleReturns {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }

    fun items(r: SaleReturnEntity): List<ReturnItemDto> = runCatching { json.decodeFromString(ListSerializer(ReturnItemDto.serializer()), r.itemsJson) }.getOrDefault(emptyList())
    fun refunds(r: SaleReturnEntity): List<RefundDto> = runCatching { json.decodeFromString(ListSerializer(RefundDto.serializer()), r.refundsJson) }.getOrDefault(emptyList())
    fun encodeItems(items: List<ReturnItemDto>): String = json.encodeToString(ListSerializer(ReturnItemDto.serializer()), items)
    fun encodeRefunds(refunds: List<RefundDto>): String = json.encodeToString(ListSerializer(RefundDto.serializer()), refunds)

    fun view(r: SaleReturnEntity) = SaleReturnView(
        r.id, r.reason, r.refundMethod, r.totalMinor, r.createdByName, r.occurredAt,
        items(r).map { ReturnLineView(it.saleItemId, it.name, it.quantityMilli, it.amountMinor) }, refunds(r).map { it.method to it.amountMinor }, pending = r.rev == 0L,
    )

    /** Vuelve a calcular lo devuelto de cada línea y de la venta con las devoluciones que el teléfono conoce. */
    suspend fun recomputeReturned(dao: SaleDao, saleId: String) {
        val returns = dao.returnsFor(saleId)
        val byItem = HashMap<String, Long>()
        returns.forEach { r -> items(r).forEach { i -> byItem[i.saleItemId] = (byItem[i.saleItemId] ?: 0) + i.quantityMilli } }
        dao.items(saleId).forEach { dao.setReturned(saleId, it.id, byItem[it.id] ?: 0) }
        dao.setSaleReturned(saleId, returns.sumOf { it.totalMinor })
    }
}

package com.cuadra.caja.domain

import com.cuadra.caja.data.remote.DailyCloseDto
import com.cuadra.caja.data.remote.DayCloseDto
import com.cuadra.caja.data.remote.MethodAmountDto
import java.time.Instant
import java.time.LocalDate

/** Montos por método de pago ya agrupados; todo método desconocido cuenta como «otro». */
data class MethodTotals(val cashMinor: Long = 0, val cardMinor: Long = 0, val transferMinor: Long = 0, val creditMinor: Long = 0, val otherMinor: Long = 0) {
    val totalMinor: Long get() = cashMinor + cardMinor + transferMinor + creditMinor + otherMinor
}

/** Una jornada del «Cierre del día»: automático, sin abrir ni cerrar nada, sin efectivo contado ni diferencias. */
data class DayCloseCard(
    val date: LocalDate, val startMillis: Long, val endMillis: Long, val salesCount: Long, val salesMinor: Long,
    val sales: MethodTotals, val collected: MethodTotals,
    val drawerExpensesMinor: Long, val otherExpensesMinor: Long, val withdrawalsMinor: Long, val depositsMinor: Long,
    val expectedCashMinor: Long, val cancelledCount: Long, val cancelledMinor: Long,
    /** Devoluciones de ESTA jornada (de ventas de cualquier día) y lo que salió del cajón por ellas. */
    val returnsCount: Long = 0, val returnsMinor: Long = 0, val cashRefundsMinor: Long = 0,
    /** Ventas de jornadas anteriores anuladas en esta: siguen en su día; aquí restan (su efectivo sale del esperado). */
    val priorCancelledCount: Long = 0, val priorCancelledMinor: Long = 0, val priorCancelledCashMinor: Long = 0,
    val netSalesMinor: Long = salesMinor,
    /** Gastos, abonos, retiros y entradas de días anteriores anulados en esta jornada (kind, cuántos, monto, efecto en el efectivo). */
    val laterVoids: List<LaterVoid> = emptyList(),
) {
    /** ¿Hoy se corrigió algo de días anteriores o se devolvió dinero? (líneas «Correcciones de hoy»). */
    val hasAdjustments: Boolean get() = returnsCount > 0 || priorCancelledCount > 0 || laterVoids.isNotEmpty()
}

data class LaterVoid(val kind: String, val count: Long, val amountMinor: Long, val cashEffectMinor: Long)

object DailyClose {
    /**
     * Efectivo esperado en el cajón: ventas en efectivo + abonos en efectivo + entradas − gastos del cajón − retiros − devoluciones en efectivo − efectivo de
     * ventas de días anteriores anuladas hoy ± anulaciones tardías (misma cuenta que el servidor).
     */
    fun expectedCash(cashSalesMinor: Long, cashCollectedMinor: Long, depositsMinor: Long, drawerExpensesMinor: Long, withdrawalsMinor: Long,
                     cashRefundsMinor: Long = 0, priorCancelledCashMinor: Long = 0, laterCashEffectMinor: Long = 0): Long =
        cashSalesMinor + cashCollectedMinor + depositsMinor - drawerExpensesMinor - withdrawalsMinor - cashRefundsMinor - priorCancelledCashMinor + laterCashEffectMinor

    fun group(list: List<MethodAmountDto>): MethodTotals {
        var t = MethodTotals()
        for (m in list) t = when (m.method) {
            "CASH" -> t.copy(cashMinor = t.cashMinor + m.amountMinor)
            "CARD" -> t.copy(cardMinor = t.cardMinor + m.amountMinor)
            "TRANSFER" -> t.copy(transferMinor = t.transferMinor + m.amountMinor)
            "CREDIT" -> t.copy(creditMinor = t.creditMinor + m.amountMinor)
            else -> t.copy(otherMinor = t.otherMinor + m.amountMinor)
        }
        return t
    }

    fun card(d: DayCloseDto): DayCloseCard = DayCloseCard(
        LocalDate.parse(d.date), Instant.parse(d.startsAt).toEpochMilli(), Instant.parse(d.endsAt).toEpochMilli(), d.salesCount, d.salesMinor,
        group(d.byMethod), group(d.creditCollected), d.drawerExpensesMinor, d.otherExpensesMinor, d.withdrawalsMinor, d.depositsMinor,
        d.expectedCashMinor, d.cancelledCount, d.cancelledMinor,
        d.returnsCount, d.returnsMinor, d.cashRefundsMinor, d.priorCancelledCount, d.priorCancelledMinor, d.priorCancelledCashMinor,
        d.netSalesMinor ?: (d.salesMinor - d.returnsMinor - d.priorCancelledMinor), d.laterVoids.map { LaterVoid(it.kind, it.count, it.amountMinor, it.cashEffectMinor) },
    )

    /** Las jornadas de la respuesta, la más reciente primero (lo último que pasó, arriba). */
    fun cards(dto: DailyCloseDto): List<DayCloseCard> = dto.days.map(::card).sortedByDescending { it.date }
}

package com.cuadra.caja.domain

import com.cuadra.caja.data.remote.DailyCloseDto
import com.cuadra.caja.data.remote.DayCloseDto
import com.cuadra.caja.data.remote.MethodAmountDto
import java.time.LocalDate
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class DailyCloseTest {
    @Test fun expectedCashIsCashSalesPlusCashPaymentsPlusDepositsMinusDrawerExpensesAndWithdrawals() {
        assertEquals(100_000 + 20_000 + 5_000 - 8_000 - 30_000, DailyClose.expectedCash(100_000, 20_000, 5_000, 8_000, 30_000))
        assertEquals(-500, DailyClose.expectedCash(0, 0, 0, 500, 0))
    }

    @Test fun methodsAreGroupedAndUnknownOnesCountAsOther() {
        val t = DailyClose.group(listOf(MethodAmountDto("CASH", 10), MethodAmountDto("CARD", 20), MethodAmountDto("TRANSFER", 30), MethodAmountDto("CREDIT", 40), MethodAmountDto("OTHER", 50), MethodAmountDto("CRYPTO", 5), MethodAmountDto("CASH", 1)))
        assertEquals(MethodTotals(cashMinor = 11, cardMinor = 20, transferMinor = 30, creditMinor = 40, otherMinor = 55), t)
        assertEquals(156, t.totalMinor)
    }

    @Test fun theServerResponseBecomesOneCardPerBusinessDayNewestFirst() {
        // Forma real de la respuesta de `GET /reports/daily-close` (incluye `range`, que se ignora).
        val json = """{"range":{"from":"2026-09-28","to":"2026-09-29"},"days":[
            {"date":"2026-09-28","startsAt":"2026-09-28T08:00:00Z","endsAt":"2026-09-29T08:00:00Z","salesCount":3,"salesMinor":30000,
             "byMethod":[{"method":"CASH","amountMinor":20000},{"method":"CREDIT","amountMinor":10000}],"creditCollected":[{"method":"CASH","amountMinor":2000}],
             "drawerExpensesMinor":1500,"otherExpensesMinor":700,"withdrawalsMinor":5000,"depositsMinor":1000,"expectedCashMinor":16500,"cancelledCount":1,"cancelledMinor":4000},
            {"date":"2026-09-29","startsAt":"2026-09-29T08:00:00Z","endsAt":"2026-09-30T08:00:00Z","salesCount":0,"salesMinor":0,"byMethod":[],"creditCollected":[],
             "drawerExpensesMinor":0,"otherExpensesMinor":0,"withdrawalsMinor":0,"depositsMinor":0,"expectedCashMinor":0,"cancelledCount":0,"cancelledMinor":0}]}"""
        val dto = Json { ignoreUnknownKeys = true }.decodeFromString(DailyCloseDto.serializer(), json)
        val cards = DailyClose.cards(dto)
        assertEquals(listOf(LocalDate.of(2026, 9, 29), LocalDate.of(2026, 9, 28)), cards.map { it.date })
        val day = cards[1]
        assertEquals(3, day.salesCount)
        assertEquals(20_000, day.sales.cashMinor)
        assertEquals(10_000, day.sales.creditMinor)
        assertEquals(2_000, day.collected.cashMinor)
        assertEquals(1_500, day.drawerExpensesMinor)
        assertEquals(700, day.otherExpensesMinor)
        assertEquals(1, day.cancelledCount)
        assertEquals(4_000, day.cancelledMinor)
        // Lo que dice el servidor es lo que la cuenta del teléfono da: 20 000 + 2 000 + 1 000 − 1 500 − 5 000.
        assertEquals(day.expectedCashMinor, DailyClose.expectedCash(day.sales.cashMinor, day.collected.cashMinor, day.depositsMinor, day.drawerExpensesMinor, day.withdrawalsMinor))
        // La ventana de la jornada va del corte al corte (24 h aquí).
        assertEquals(24 * 3600 * 1000L, day.endMillis - day.startMillis)
    }

    @Test fun anEmptyDayHasZeros() {
        val c = DailyClose.card(DayCloseDto("2026-09-29", "2026-09-29T08:00:00Z", "2026-09-30T08:00:00Z", 0, 0))
        assertEquals(0, c.expectedCashMinor)
        assertEquals(MethodTotals(), c.sales)
    }
}

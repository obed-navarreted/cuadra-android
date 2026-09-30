package com.cuadra.caja.domain

import com.cuadra.caja.data.remote.DailyCloseDto
import com.cuadra.caja.data.remote.DayCloseDto
import com.cuadra.caja.data.remote.LaterVoidDto
import com.cuadra.caja.data.remote.MethodAmountDto
import com.cuadra.caja.domain.printing.PrinterSettings
import com.cuadra.caja.domain.printing.SaleReceipts
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Devoluciones, «Anular esta venta», país y moneda, consola de la plataforma y el cierre con correcciones de hoy (docs/adr/0013). */
class SaleReturnsAndUndoTest {
    // El mismo caso que la prueba del servidor: 3 quesos a C$100 y 1 crema a C$50, con C$35 de descuento de la cuenta.
    private val lines = listOf(ReturnableLine("q", "Queso", 3_000, 30_000), ReturnableLine("c", "Crema", 1_000, 5_000))

    @Test fun theDiscountIsSharedAndAPartialReturnIsProportionalLikeTheServer() {
        assertEquals(listOf(27_000L, 4_500L), SaleReturnMath.lineNets(listOf(30_000, 5_000), 35_000, 3_500))
        assertEquals(9_000, SaleReturnMath.total(lines, 35_000, 3_500, 31_500, 0, mapOf("q" to 1_000L)))
        // Lo que queda de una línea se devuelve exacto (sin residuos de redondeo) y nunca más de lo cobrado.
        val after = listOf(lines[0].copy(returnedMilli = 1_000, returnedMinor = 9_000), lines[1])
        assertEquals(18_000, SaleReturnMath.total(after, 35_000, 3_500, 31_500, 9_000, mapOf("q" to 2_000L)))
        assertTrue(SaleReturnMath.exceeds(after, mapOf("q" to 3_000L)))
        assertFalse(SaleReturnMath.exceeds(after, mapOf("q" to 2_000L)))
        assertEquals(3, SaleReturnMath.mulDivHalfUp(5, 1, 2))
        assertEquals(1_000_000_000_000L, SaleReturnMath.mulDivHalfUp(1_000_000_000_000L, 999_999_999L, 999_999_999L))
    }

    @Test fun whoCanReturnAndHow() {
        assertTrue(SaleReturnMath.allowed("OWNER", soldByMe = false, sameBusinessDay = false))
        assertTrue(SaleReturnMath.allowed("CASHIER", soldByMe = true, sameBusinessDay = true))
        assertFalse(SaleReturnMath.allowed("CASHIER", soldByMe = true, sameBusinessDay = false))
        assertFalse(SaleReturnMath.allowed("CASHIER", soldByMe = false, sameBusinessDay = true))
        assertEquals(RefundMethod.entries, SaleReturnMath.methods(listOf("CASH", "CREDIT")))
        assertEquals(listOf(RefundMethod.CASH, RefundMethod.SAME), SaleReturnMath.methods(listOf("CASH")))
        assertFalse(SaleReturnMath.canReturn("CANCELLED", lines))
        assertFalse(SaleReturnMath.canReturn("COMPLETED", lines.map { it.copy(returnedMilli = it.quantityMilli) }))
        assertFalse(SaleReturnMath.reasonOk("roto"))
        assertTrue(SaleReturnMath.reasonOk("venía roto"))
        assertEquals(1_500, com.cuadra.caja.ui.ReturnDraft.parseQty("1,5"))
        assertEquals(0, com.cuadra.caja.ui.ReturnDraft.parseQty("-2"))
    }

    @Test fun onlyTheOwnLastSaleWithinFiveMinutesCanBeUndone() {
        val t0 = 1_700_000_000_000L
        assertTrue(SaleUndo.canUndo("COMPLETED", "kevin", "kevin", true, 0, t0, t0 + 4 * 60_000))
        assertFalse(SaleUndo.canUndo("COMPLETED", "kevin", "kevin", true, 0, t0, t0 + 5 * 60_000 + 1))
        assertFalse(SaleUndo.canUndo("COMPLETED", "kevin", "lucia", true, 0, t0, t0 + 1_000))
        assertFalse(SaleUndo.canUndo("COMPLETED", "kevin", "kevin", false, 0, t0, t0 + 1_000))
        assertFalse(SaleUndo.canUndo("COMPLETED", "kevin", "kevin", true, 500, t0, t0 + 1_000))
        assertFalse(SaleUndo.canUndo("CANCELLED", "kevin", "kevin", true, 0, t0, t0 + 1_000))
        assertEquals(5, SaleUndo.minutesLeft(t0, t0 + 1_000))
        assertEquals(1, SaleUndo.minutesLeft(t0, t0 + 4 * 60_000 + 30_000))
        assertEquals(0, SaleUndo.minutesLeft(t0, t0 + 6 * 60_000))
        // Un reloj que retrocedió (la hora actual antes del cobro) no quita el plazo.
        assertEquals(SaleUndo.WINDOW_MILLIS, SaleUndo.remaining(t0, t0 - 10_000))
    }

    @Test fun theCountrySuggestsCurrencyAndZoneFromThePhone() {
        val list = CountryChoice.FALLBACK
        assertEquals("HN", CountryChoice.suggested(list, "HN", "UTC").code)
        assertEquals("CR", CountryChoice.suggested(list, "XX", "America/Costa_Rica").code)
        assertEquals("NI", CountryChoice.suggested(list, "US", "America/Managua").code)
        assertEquals("NI", CountryChoice.suggested(list, null, "Asia/Tokyo").code)
        val hn = list.first { it.code == "HN" }
        assertEquals(BusinessOrigin("HN", "HNL", "America/Tegucigalpa"), CountryChoice.origin(hn))
        assertEquals(listOf("HNL", "USD"), CountryChoice.currencies(hn, "HNL"))
        assertEquals(listOf("America/Tegucigalpa", "Europe/Madrid"), CountryChoice.timezones(hn, "Europe/Madrid"))
        assertEquals(listOf("America/Tegucigalpa"), CountryChoice.timezones(hn, "no/zone"))
        assertEquals("Honduras", CountryChoice.name("HN", Locale.forLanguageTag("es")))
    }

    @Test fun thePlatformConsoleOpensInTheWebPanel() {
        assertEquals("https://panel.cuadra.app/console", PlatformConsole.url("https://panel.cuadra.app/"))
        assertEquals("http://localhost:5173/console", PlatformConsole.url("http://localhost:5173"))
        assertNull(PlatformConsole.url(null))
        assertNull(PlatformConsole.url("javascript:alert(1)"))
    }

    @Test fun theDailyCloseCarriesTodaysCorrectionsAndTheExpectedCashFormula() {
        val dto = DailyCloseDto(listOf(DayCloseDto(
            "2026-09-21", "2026-09-21T08:00:00Z", "2026-09-22T08:00:00Z", 0, 0, emptyList(), emptyList(),
            returnsCount = 1, returnsMinor = 10_000, cashRefundsMinor = 10_000, priorCancelledCount = 1, priorCancelledMinor = 4_000, priorCancelledCashMinor = 4_000,
            netSalesMinor = -14_000, laterVoids = listOf(LaterVoidDto("EXPENSE_DRAWER", 1, 1_500, 1_500)), expectedCashMinor = -12_500,
        )))
        val card = DailyClose.cards(dto).single()
        assertTrue(card.hasAdjustments)
        assertEquals(-14_000, card.netSalesMinor)
        assertEquals(listOf(LaterVoid("EXPENSE_DRAWER", 1, 1_500, 1_500)), card.laterVoids)
        assertEquals(card.expectedCashMinor, DailyClose.expectedCash(0, 0, 0, 0, 0, card.cashRefundsMinor, card.priorCancelledCashMinor, 1_500))
        assertFalse(DailyClose.card(DayCloseDto("2026-09-20", "2026-09-20T08:00:00Z", "2026-09-21T08:00:00Z", 1, 100, listOf(MethodAmountDto("CASH", 100)))).hasAdjustments)
    }

    @Test fun theReturnReceiptListsWhatCameBackTheRefundAndTheReason() {
        val sale = SaleView(
            "a1b2c3d4-0000", "COMPLETED", null, 35_000, 3_500, 31_500, 0, "Kevin", null, null, null, null, null,
            listOf(SaleLineView("Queso", null, 3_000, 10_000, 0, 30_000, "q"), SaleLineView("Crema", null, 1_000, 5_000, 0, 5_000, "c")), emptyList(),
        )
        val ret = SaleReturnView("ffee0011-2222", "venía roto", "CREDIT_NOTE", 9_000, "Ana", 1_790_000_000_000, listOf(ReturnLineView("q", "Queso", 1_000, 9_000)), listOf("CREDIT" to 9_000L))
        val text = SaleReceipts.returnReceipt(sale, ret, "Quesería", ZoneId.of("America/Managua"), PrinterSettings(), "es") { "C$ " + java.math.BigDecimal.valueOf(it, 2).toPlainString() }.previewText()
        assertTrue(text, text.replace(" ", "").contains("DEVOLUCIÓN"))
        assertTrue(text, text.contains("-C$ 90.00"))
        assertTrue(text, text.contains("Nota de crédito") || text.contains("Nota de credito"))
        assertTrue(text, text.contains("Motivo: venía roto") || text.contains("Motivo: venia roto"))
        assertTrue(text, text.contains("#A1B2C3"))
    }

    @Test fun newRejectionCodesAreExplained() {
        assertEquals(Attention.Reason.UNDO_NOT_ALLOWED, Attention.reasonOf("UNDO_NOT_ALLOWED"))
        assertEquals(Attention.Reason.RETURN_REJECTED, Attention.reasonOf("CREDIT_NOTE_EXCEEDS"))
        assertEquals(Attention.Reason.LATE_OP_REJECTED, Attention.reasonOf("LATE_OP_REJECTED"))
        assertEquals(Attention.What.SALE_RETURN, Attention.describe(1, "SALE_RETURN", "{}", 0, "FAILED", "RETURN_EXCEEDS_SOLD", null, null, null).what)
    }
}

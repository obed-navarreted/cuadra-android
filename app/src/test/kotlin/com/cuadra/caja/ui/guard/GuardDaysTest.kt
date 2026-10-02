package com.cuadra.caja.ui.guard

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import com.cuadra.caja.R
import com.cuadra.caja.domain.RangeChoice
import com.cuadra.caja.domain.RangePreset
import com.cuadra.caja.domain.SaleQuery
import com.cuadra.caja.ui.DailyCloseActions
import com.cuadra.caja.ui.DailyCloseUi
import com.cuadra.caja.ui.DeleteDraft
import com.cuadra.caja.ui.ErrorMessage
import com.cuadra.caja.ui.HistoryActions
import com.cuadra.caja.ui.HistoryUi
import com.cuadra.caja.ui.common.BarcodeField
import com.cuadra.caja.ui.common.RangePicker
import com.cuadra.caja.ui.common.Sheet
import com.cuadra.caja.ui.screens.DailyCloseContent
import com.cuadra.caja.ui.screens.DeleteSaleSheet
import com.cuadra.caja.ui.screens.HistoryContent
import com.cuadra.caja.ui.screens.SaleDetailSheet
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Pantallas del lote «jornada»: selector de rango, Ventas (dueño/admin y cajero), detalle y eliminar venta, Cierre del día y el campo del código de barras. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h900dp-xhdpi", application = android.app.Application::class)
class GuardDaysTest {
    @get:Rule val rule = createEmptyComposeRule()

    private val full = GuardMatrix.FULL
    private val actions = object : HistoryActions {}
    private val members = listOf(Fixtures.member(1, Fixtures.PERSON_LONG, "OWNER"), Fixtures.member(2, "Kevin", "CASHIER"), Fixtures.member(3, Fixtures.NAME_120, "ADMIN"))

    @Test fun rangePickerSalesDetailDeleteAndDailyClose() {
        val runner = GuardRunner(rule, "days")
        runner.run(
            listOf(
                GuardCase("Rango: hoy (en curso)", full) { Picker(RangePreset.TODAY) },
                GuardCase("Rango: ayer", full) { Picker(RangePreset.YESTERDAY) },
                GuardCase("Rango: mes pasado", full) { Picker(RangePreset.LAST_MONTH) },
                GuardCase("Rango: personalizado con año", full) { RangePicker(RangeChoice(RangePreset.CUSTOM, LocalDate.of(2025, 12, 30) to LocalDate.of(2026, 1, 2)), Fixtures.calendar, {}, nowMillis = Fixtures.NOW) },
                GuardCase("Ventas: dueño, lista del servidor", full) { Sales(HistoryUi(sales = Fixtures.serverSales, totals = Fixtures.saleTotals, hasMore = true, query = SaleQuery(setOf("COMPLETED", "CANCELLED")))) },
                GuardCase("Ventas: dueño, más filtros", full) { Sales(HistoryUi(sales = Fixtures.serverSales, totals = Fixtures.saleTotals, query = SaleQuery(setOf("CANCELLED"), "TRANSFER", "m2")), moreFilters = true) },
                GuardCase("Ventas: dueño, ayer y cargando", full) { Sales(HistoryUi(range = RangeChoice(RangePreset.YESTERDAY), loading = true)) },
                // Al abrir Ventas se refresca con el MISMO filtro: la lista de antes sigue a la vista con el indicador pequeño (sin vaciarse).
                GuardCase("Ventas: dueño, refrescando con la lista de antes", full) { Sales(HistoryUi(sales = Fixtures.serverSales, totals = Fixtures.saleTotals, loading = true), refreshing = true) },
                GuardCase("Ventas: cajero, sin conexión al refrescar", full) { Sales(HistoryUi(), manager = false, role = "CASHIER", offlineLocal = true) },
                GuardCase("Ventas: dueño, sin resultados", full) { Sales(HistoryUi(totals = Fixtures.saleTotals.copy(count = 0, totalMinor = 0, cancelledCount = 0))) },
                GuardCase("Ventas: dueño, sin conexión", full) { Sales(HistoryUi(offline = true)) },
                GuardCase("Ventas: dueño, error", full) { Sales(HistoryUi(error = ErrorMessage(R.string.error_generic))) },
                GuardCase("Ventas: cajero", full) { Sales(HistoryUi(), manager = false, role = "CASHIER") },
                GuardCase("Ventas: cajero vacío", full) { Sales(HistoryUi(), manager = false, role = "CASHIER", local = emptyList()) },
                GuardCase("Venta: detalle cobrada (dueño)", full) { Detail(Fixtures.saleView(2, edited = true), "OWNER") },
                GuardCase("Venta: detalle eliminada", full) { Detail(Fixtures.saleView(3, cancelled = true), "ADMIN") },
                GuardCase("Venta: detalle de cajero (sin eliminar)", full) { Detail(Fixtures.saleView(1), "CASHIER") },
                // Cobro en caja: «Atendió: X · Cobró: Y», quién la envió a caja y «Enviar por WhatsApp» (siempre disponible en el detalle).
                GuardCase("Venta: atendió y cobró personas distintas, con WhatsApp", full) { Detail(Fixtures.servedAndCharged, "OWNER", whatsApp = true) },
                GuardCase("Ventas: atendió y cobró personas distintas", full) { Sales(HistoryUi(sales = listOf(Fixtures.servedAndCharged) + Fixtures.serverSales, totals = Fixtures.saleTotals)) },
                GuardCase("Cierre del día: descuentos por promociones", full) { Close(DailyCloseUi(cards = listOf(Fixtures.dayCloses.first().copy(promotionDiscountMinor = Fixtures.HUGE)))) },
                GuardCase("Cierre del día: cuentas por cobrar en caja", full) { Close(DailyCloseUi(cards = listOf(Fixtures.dayCloses.first().copy(pendingCheckoutCount = 12_345, pendingCheckoutMinor = Fixtures.HUGE)))) },
                GuardCase("Venta: eliminar (motivo vacío)", full) { DeleteSaleSheet("", actions) },
                GuardCase("Venta: eliminar (motivo largo)", full) { DeleteSaleSheet(Fixtures.NAME_200, actions) },
                GuardCase("Venta: eliminar (motivo corto)", full) { DeleteSaleSheet("ab", actions) },
                GuardCase("Venta: eliminar (teclado)", GuardMatrix.KEYBOARD) { DeleteSaleSheet(Fixtures.NAME_120, actions) },
                // Devoluciones, «Anular esta venta» y etiquetas para revisar (docs/adr/0013).
                GuardCase("Venta: detalle con devolución y etiquetas (dueño)", full) { Detail(Fixtures.returnedSale, "OWNER", canReturn = true) },
                GuardCase("Venta: detalle recién devuelta, con impresora", full) { Detail(Fixtures.returnedSale, "OWNER", canReturn = true, lastReturn = Fixtures.returnedSale.returns.first(), printer = com.cuadra.caja.domain.printing.PrinterBadge.CONNECTED) },
                GuardCase("Venta: detalle de cajero con «Anular esta venta»", full) { Detail(Fixtures.returnedSale.copy(returnedMinor = 0, returns = emptyList()), "CASHIER", canReturn = true, undoable = true) },
                GuardCase("Devolver: vacío", full) { com.cuadra.caja.ui.screens.ReturnSheet(com.cuadra.caja.ui.ReturnDraft(Fixtures.returnedSale), actions) },
                GuardCase("Devolver: cantidades, de más y motivo corto", full) {
                    com.cuadra.caja.ui.screens.ReturnSheet(com.cuadra.caja.ui.ReturnDraft(Fixtures.returnedSale, texts = mapOf("l1" to "12345.678", "l3" to "1.5"), reason = "ab", method = com.cuadra.caja.domain.RefundMethod.CREDIT_NOTE), actions)
                },
                GuardCase("Devolver: listo (teclado)", GuardMatrix.KEYBOARD) {
                    com.cuadra.caja.ui.screens.ReturnSheet(com.cuadra.caja.ui.ReturnDraft(Fixtures.returnedSale, texts = mapOf("l3" to "1.5"), reason = Fixtures.NAME_120), actions)
                },
                GuardCase("Anular esta venta", full) { DeleteSaleSheet("", actions, undo = true) },
                GuardCase("Anular esta venta: ya pasaron 5 min (teclado)", GuardMatrix.KEYBOARD) { DeleteSaleSheet(Fixtures.NAME_120, actions, undo = true, tooLate = true) },
                GuardCase("Cierre del día: devoluciones, anulaciones tardías y avisos", full) {
                    Close(DailyCloseUi(cards = listOf(Fixtures.adjustedClose) + Fixtures.dayCloses, syncWarnings = Fixtures.syncWarnings, localPending = 12_345))
                },
                GuardCase("Cierre del día: tarjetas", full) { Close(DailyCloseUi(RangeChoice(RangePreset.LAST_7), cards = Fixtures.dayCloses)) },
                GuardCase("Cierre del día: ayer sin conexión", full) { Close(DailyCloseUi(RangeChoice(RangePreset.YESTERDAY), needsConnection = true)) },
                GuardCase("Cierre del día: datos viejos sin conexión", full) { Close(DailyCloseUi(cards = Fixtures.dayCloses.take(1), needsConnection = true, stale = true)) },
                GuardCase("Cierre del día: error", full) { Close(DailyCloseUi(error = ErrorMessage(R.string.error_generic))) },
                GuardCase("Cierre del día: cargando", full) { Close(DailyCloseUi(loading = true)) },
                GuardCase("Código de barras: números", full) { Sheet({}) { BarcodeField(Fixtures.CODE_LONG, {}, {}) } },
                GuardCase("Código de barras: letras", full) { Sheet({}) { BarcodeField("ABC-123-" + Fixtures.CODE_LONG, {}, {}, supportingText = { com.cuadra.caja.ui.common.Text(Fixtures.NAME_120) }) } },
                GuardCase("Código de barras (teclado)", GuardMatrix.KEYBOARD) { Sheet({}) { BarcodeField("ABC-123", {}, {}) } },
            ),
        )
        runner.assertClean()
    }

    @Composable private fun Picker(p: RangePreset) = RangePicker(RangeChoice(p), Fixtures.calendar, {}, nowMillis = Fixtures.NOW)

    @Composable private fun Sales(
        ui: HistoryUi, manager: Boolean = true, role: String = "OWNER", local: List<com.cuadra.caja.domain.SaleView> = Fixtures.localSales, moreFilters: Boolean = false,
        refreshing: Boolean = false, offlineLocal: Boolean = false,
    ) = HistoryContent(ui, local, manager, role, Fixtures.calendar, members, actions, Fixtures.NOW, moreFilters, refreshing = refreshing, offlineLocal = offlineLocal)

    @Composable private fun Detail(
        s: com.cuadra.caja.domain.SaleView, role: String, canReturn: Boolean = false, undoable: Boolean = false, lastReturn: com.cuadra.caja.domain.SaleReturnView? = null,
        printer: com.cuadra.caja.domain.printing.PrinterBadge = com.cuadra.caja.domain.printing.PrinterBadge.OFF, whatsApp: Boolean = false,
    ) {
        val time = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withLocale(androidx.compose.ui.platform.LocalConfiguration.current.locales[0]).withZone(Fixtures.calendar.zone)
        SaleDetailSheet(s, time, com.cuadra.caja.domain.SaleDeletion.canDelete(role, s.status), actions, printer, null, canReturn = canReturn, undoable = undoable, lastReturn = lastReturn, nowMillis = Fixtures.NOW,
            onWhatsApp = if (whatsApp) ({}) else null)
    }

    @Composable private fun Close(ui: DailyCloseUi) = DailyCloseContent(ui, Fixtures.calendar, object : DailyCloseActions {}, {}, Fixtures.NOW)
}

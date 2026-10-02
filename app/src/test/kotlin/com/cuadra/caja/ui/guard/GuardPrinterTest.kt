package com.cuadra.caja.ui.guard

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import com.cuadra.caja.core.i18n.AppLanguage
import com.cuadra.caja.data.printer.PrinterDevice
import com.cuadra.caja.data.sync.SyncStatus
import com.cuadra.caja.domain.PayMethod
import com.cuadra.caja.domain.PaymentEntry
import com.cuadra.caja.domain.SaleView
import com.cuadra.caja.domain.printing.ConnIssue
import com.cuadra.caja.domain.printing.ConnState
import com.cuadra.caja.domain.printing.PrintCharset
import com.cuadra.caja.domain.printing.PrintNotice
import com.cuadra.caja.domain.printing.PrinterBadge
import com.cuadra.caja.domain.printing.PrinterLink
import com.cuadra.caja.domain.printing.PrinterSamples
import com.cuadra.caja.domain.printing.PrinterSettings
import com.cuadra.caja.ui.CajaActions
import com.cuadra.caja.ui.CajaUi
import com.cuadra.caja.ui.HistoryActions
import com.cuadra.caja.ui.PrinterActions
import com.cuadra.caja.ui.PrinterUi
import com.cuadra.caja.ui.PosTab
import com.cuadra.caja.ui.common.PrintNoticePopup
import com.cuadra.caja.ui.screens.CajaContent
import com.cuadra.caja.ui.screens.CobroContent
import com.cuadra.caja.ui.screens.MoreActions
import com.cuadra.caja.ui.screens.MoreScreen
import com.cuadra.caja.ui.screens.PrinterContent
import com.cuadra.caja.ui.screens.SaleDetailSheet
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Todo lo de la impresora térmica en la guardia de diseño, con la matriz completa (letra × ancho × idioma, las dos políticas de letra) y con teclado en lo que tiene campos:
 * Ajustes de la impresora (apagada, activada sin conexión, conectada, USB, permiso negado, sin equipos emparejados, nombres larguísimos, encabezado largo con teclado),
 * el grupo «Avanzado» de Más, el detalle de venta con «Reimprimir», la caja con el indicador, la venta completa con «Imprimir recibo» y los avisos flotantes.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h900dp-xhdpi", application = android.app.Application::class)
class GuardPrinterTest {
    @get:Rule val rule = createEmptyComposeRule()

    private val full = GuardMatrix.FULL
    private val money = com.cuadra.caja.domain.printing.ReceiptFixtures.NIO
    private val zone = Fixtures.calendar.zone

    private val longNames = listOf(
        PrinterDevice("00:11:22:33:44:55", Fixtures.NAME_200, true), PrinterDevice("AA:BB:CC:DD:EE:FF", Fixtures.NAME_120, true), PrinterDevice("11:22:33:44:55:66", Fixtures.LONG_WORD), PrinterDevice("22:33:44:55:66:77", "MTP-II", true),
    )
    private val shortNames = listOf(PrinterDevice("00:11:22:33:44:55", "MTP-II", true), PrinterDevice("AA:BB:CC:DD:EE:FF", "Impresora del mostrador", true), PrinterDevice("11:22:33:44:55:66", "Audífonos de Kevin"))

    private fun settings(vararg change: (PrinterSettings) -> PrinterSettings) = change.fold(PrinterSettings(enabled = true)) { s, f -> f(s) }

    private fun ui(s: PrinterSettings, state: ConnState = ConnState.DISCONNECTED, issue: ConnIssue = ConnIssue.NONE, bonded: List<PrinterDevice> = emptyList(), usb: List<PrinterDevice> = emptyList(), permission: Boolean = true, btOn: Boolean = true, result: PrintNotice? = null, testing: Boolean = false) =
        PrinterUi(
            s, state, issue, btPermission = permission, btOn = btOn, bonded = bonded, usb = usb, testing = testing, result = result,
            preview = PrinterSamples.preview(Fixtures.BUSINESS_NAME, s, "es", money, zone, Fixtures.NOW).previewText(),
        )

    @Composable private fun Printer(ui: PrinterUi) = PrinterContent(ui, object : PrinterActions {}, {}, {}, {}, {})

    private val longHeader = { s: PrinterSettings -> s.copy(address = Fixtures.NAME_120.take(60), phone = Fixtures.PHONE_30, taxId = Fixtures.NAME_60.take(60), footer = Fixtures.NAME_200.take(90)) }

    @Test fun printerSettingsScreen() {
        val runner = GuardRunner(rule, "printer")
        val chosen = { s: PrinterSettings -> s.copy(deviceKey = "AA:BB:CC:DD:EE:FF", deviceName = "Impresora del mostrador") }
        runner.run(
            listOf(
                GuardCase("Impresora: desactivada (por omisión)", full) { Printer(PrinterUi(PrinterSettings())) },
                GuardCase("Impresora: activada, sin conexión ni impresora elegida", full) { Printer(ui(settings(), ConnState.DISCONNECTED, ConnIssue.NO_DEVICE, bonded = shortNames)) },
                GuardCase("Impresora: conectada por Bluetooth", full) { Printer(ui(settings(chosen), ConnState.CONNECTED, bonded = shortNames)) },
                GuardCase("Impresora: conectando", full) { Printer(ui(settings(chosen), ConnState.CONNECTING, bonded = shortNames)) },
                GuardCase("Impresora: error, no se encuentra", full) { Printer(ui(settings(chosen), ConnState.ERROR, ConnIssue.NOT_FOUND, bonded = shortNames)) },
                GuardCase("Impresora: Bluetooth apagado", full) { Printer(ui(settings(chosen), ConnState.ERROR, ConnIssue.BLUETOOTH_OFF, bonded = shortNames, btOn = false)) },
                GuardCase("Impresora: cable USB detectado y conectada", full) { Printer(ui(settings({ it.copy(link = PrinterLink.USB, deviceKey = "0483:5740", deviceName = "STMicroelectronics POS-58") }), ConnState.CONNECTED, usb = listOf(PrinterDevice("0483:5740", "STMicroelectronics POS-58", true), PrinterDevice("1234:abcd", "Teclado USB")))) },
                GuardCase("Impresora: USB sin elegir, pide permiso del cable", full) { Printer(ui(settings({ it.copy(link = PrinterLink.USB) }), ConnState.ERROR, ConnIssue.USB_PERMISSION, usb = listOf(PrinterDevice("0483:5740", "POS-58", true)))) },
                GuardCase("Impresora: USB sin nada conectado", full) { Printer(ui(settings({ it.copy(link = PrinterLink.USB) }), ConnState.ERROR, ConnIssue.NOT_FOUND)) },
                GuardCase("Impresora: permiso de Bluetooth negado", full) { Printer(ui(settings(chosen), ConnState.ERROR, ConnIssue.NO_PERMISSION, permission = false)) },
                GuardCase("Impresora: sin equipos emparejados", full) { Printer(ui(settings(), ConnState.DISCONNECTED, ConnIssue.NO_DEVICE, bonded = emptyList())) },
                GuardCase("Impresora: sin equipos emparejados y Bluetooth apagado", full) { Printer(ui(settings(), ConnState.DISCONNECTED, ConnIssue.BLUETOOTH_OFF, bonded = emptyList(), btOn = false)) },
                GuardCase("Impresora: sin Bluetooth en el teléfono", full) { Printer(ui(settings(), ConnState.DISCONNECTED).copy(hasBluetooth = false)) },
                GuardCase("Impresora: sin USB host en el teléfono", full) { Printer(ui(settings({ it.copy(link = PrinterLink.USB) })).copy(hasUsb = false)) },
                GuardCase("Impresora: nombres de impresora larguísimos", full) {
                    Printer(ui(settings({ it.copy(deviceKey = "00:11:22:33:44:55", deviceName = Fixtures.NAME_200) }), ConnState.CONNECTED, bonded = longNames))
                },
                GuardCase("Impresora: 80 mm, 2 copias, sin acentos, encabezado y pie largos", full) {
                    Printer(ui(settings(chosen, longHeader, { it.copy(widthMm = 80, copies = 2, charset = PrintCharset.ASCII, autoPrint = false) }), ConnState.CONNECTED, bonded = shortNames))
                },
                GuardCase("Impresora: encabezado y pie largos con teclado", GuardMatrix.KEYBOARD) { Printer(ui(settings(chosen, longHeader), ConnState.CONNECTED, bonded = shortNames)) },
                GuardCase("Impresora: prueba enviada", full) { Printer(ui(settings(chosen), ConnState.CONNECTED, bonded = shortNames, result = PrintNotice.PRINTED)) },
                GuardCase("Impresora: prueba sin impresora", full) { Printer(ui(settings(chosen), ConnState.ERROR, ConnIssue.NOT_FOUND, bonded = shortNames, result = PrintNotice.NO_PRINTER)) },
                GuardCase("Impresora: prueba con falla, imprimiendo", full) { Printer(ui(settings(chosen), ConnState.CONNECTED, bonded = shortNames, result = PrintNotice.FAILED, testing = true)) },
            ),
        )
        runner.assertClean()
    }

    @Composable private fun More(open: Boolean, language: AppLanguage = AppLanguage.SPANISH, printer: Boolean = true, reader: Boolean = true) {
        val go: () -> Unit = {}
        MoreScreen(
            Fixtures.BUSINESS_120, Fixtures.PERSON_LONG, SyncStatus.IDLE, 0, 0, language, 12_345,
            MoreActions(onSettings = go, onTeam = go, onActivity = go, onTemplates = go, onNotifications = go, onTextSize = go, onReader = if (reader) go else null, onPrinter = if (printer) go else null, onHelp = go, onMyAccount = go),
            advancedOpen = open,
        )
    }

    @Test fun moreAdvancedGroup() {
        val runner = GuardRunner(rule, "printer-more")
        runner.run(
            listOf(
                GuardCase("Más: Avanzado cerrado", full) { More(false) },
                GuardCase("Más: Avanzado abierto (Impresora y Lector, «Opcional»)", full) { More(true) },
                GuardCase("Más: Avanzado abierto, solo impresora", full) { More(true, reader = false) },
                GuardCase("Más: Avanzado abierto, inglés", full) { More(true, AppLanguage.ENGLISH) },
            ),
        )
        runner.assertClean()
    }

    @Composable private fun Detail(s: SaleView, canDelete: Boolean, printer: PrinterBadge, notice: PrintNotice? = null) {
        val time = com.cuadra.caja.domain.ClockFormat.dateTime(androidx.compose.ui.platform.LocalConfiguration.current.locales[0], Fixtures.calendar.zone)
        SaleDetailSheet(s, time, canDelete, object : HistoryActions {}, printer, notice)
    }

    @Test fun saleDetailWithPrintButton() {
        val runner = GuardRunner(rule, "printer-sale")
        runner.run(
            listOf(
                GuardCase("Venta: detalle con «Reimprimir» (dueño, con eliminar)", full) { Detail(Fixtures.saleView(2, edited = true), true, PrinterBadge.CONNECTED) },
                GuardCase("Venta: detalle con «Reimprimir», impreso", full) { Detail(Fixtures.saleView(2), true, PrinterBadge.CONNECTED, PrintNotice.PRINTED) },
                GuardCase("Venta: detalle con «Reimprimir», sin impresora", full) { Detail(Fixtures.saleView(2), true, PrinterBadge.DISCONNECTED, PrintNotice.NO_PRINTER) },
                GuardCase("Venta: detalle con «Reimprimir», falla", full) { Detail(Fixtures.saleView(2), true, PrinterBadge.CONNECTED, PrintNotice.FAILED) },
                GuardCase("Venta: detalle eliminada con «Reimprimir» (sale ANULADA)", full) { Detail(Fixtures.saleView(3, cancelled = true), false, PrinterBadge.CONNECTING) },
                GuardCase("Venta: detalle de cajero con «Reimprimir», sin eliminar", full) { Detail(Fixtures.saleView(1), false, PrinterBadge.CONNECTED) },
                GuardCase("Venta: detalle sin impresora activada (sin botón)", full) { Detail(Fixtures.saleView(2), true, PrinterBadge.OFF) },
            ),
        )
        runner.assertClean()
    }

    @Composable private fun Caja(ui: CajaUi, readerReady: Boolean = false, business: String = Fixtures.BUSINESS_NAME) =
        CajaContent(ui, listOf(PosTab.MANUAL, PosTab.PRODUCTS), Fixtures.pane, Fixtures.parked, Fixtures.HUGE, business, "Kevin", object : CajaActions {}, {}, readerReady = readerReady)

    @Test fun registerWithPrinterBadgeAndCobroDone() {
        val runner = GuardRunner(rule, "printer-caja")
        val typing = Fixtures.cajaUi(cart = Fixtures.cart15, entry = Fixtures.entry('1', '2', '5'))
        val done = Fixtures.cobro(Fixtures.BIG, PaymentEntry(PayMethod.CASH, Fixtures.BIG), done = Fixtures.HUGE)
        fun cobro(printer: PrinterBadge, notice: PrintNotice? = null) = Fixtures.cajaUi(cart = Fixtures.fullCart, cobro = done).copy(printer = printer, printNotice = notice)
        runner.run(
            listOf(
                GuardCase("Caja: impresora conectada (indicador verde)", full, CAJA_KEYS) { Caja(typing.copy(printer = PrinterBadge.CONNECTED)) },
                GuardCase("Caja: impresora conectando (gris)", full, CAJA_KEYS) { Caja(typing.copy(printer = PrinterBadge.CONNECTING)) },
                GuardCase("Caja: impresora sin conexión (naranja tachada)", full, CAJA_KEYS) { Caja(typing.copy(printer = PrinterBadge.DISCONNECTED)) },
                GuardCase("Caja: teléfono bajo, indicador, lector listo, negocio largo y monto enorme", GuardMatrix.SMALL_PHONE, CAJA_KEYS) {
                    Caja(Fixtures.cajaUi(cart = Fixtures.oneLineCart, entry = Fixtures.bigEntry).copy(printer = PrinterBadge.DISCONNECTED), readerReady = true, business = Fixtures.BUSINESS_120)
                },
                GuardCase("Caja: aviso «Sin impresora conectada» tras vender", full, CAJA_KEYS) { Caja(typing.copy(printer = PrinterBadge.DISCONNECTED, printNotice = PrintNotice.NO_PRINTER)) },
                // El comprobante se imprime solo (sin botón) y los avisos de impresión salen en el aviso flotante de la venta nueva, no aquí.
                GuardCase("Venta completa: impresora conectada (sin botón de imprimir)", full) { CobroContent(cobro(PrinterBadge.CONNECTED), object : CajaActions {}) },
                GuardCase("Venta completa: impresora apagada", full) { CobroContent(cobro(PrinterBadge.OFF), object : CajaActions {}) },
            ),
        )
        runner.assertClean()
    }

    @Composable private fun Popup(n: PrintNotice) = Box(Modifier.fillMaxSize()) { PrintNoticePopup(n, {}, {}, Modifier.align(Alignment.TopCenter)) }

    @Test fun floatingPrintNotices() {
        val runner = GuardRunner(rule, "printer-notice")
        runner.run(
            listOf(
                GuardCase("Aviso flotante: Impreso", full) { Popup(PrintNotice.PRINTED) },
                GuardCase("Aviso flotante: Sin impresora conectada (advertencia, Reintentar, Después)", full) { Popup(PrintNotice.NO_PRINTER) },
                GuardCase("Aviso flotante: No se pudo imprimir", full) { Popup(PrintNotice.FAILED) },
            ),
        )
        runner.assertClean()
    }
}

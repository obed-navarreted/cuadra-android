package com.cuadra.caja.ui.guard

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import com.cuadra.caja.R
import com.cuadra.caja.core.i18n.AppLanguage
import com.cuadra.caja.data.sync.SyncStatus
import com.cuadra.caja.ui.ErrorMessage
import com.cuadra.caja.ui.NotificationsActions
import com.cuadra.caja.ui.PrefsUi
import com.cuadra.caja.ui.SchedulesActions
import com.cuadra.caja.ui.SchedulesUi
import com.cuadra.caja.ui.SummaryActions
import com.cuadra.caja.domain.RangeChoice
import com.cuadra.caja.domain.RangePreset
import com.cuadra.caja.domain.ScheduleDrafts
import com.cuadra.caja.domain.ScheduleWhen
import com.cuadra.caja.domain.ScheduleRepeat
import com.cuadra.caja.ui.common.Text
import com.cuadra.caja.ui.screens.Banner
import com.cuadra.caja.ui.screens.Editor
import com.cuadra.caja.ui.screens.MoreActions
import com.cuadra.caja.ui.screens.MoreScreen
import com.cuadra.caja.ui.screens.ReaderContent
import com.cuadra.caja.ui.ReaderActions
import com.cuadra.caja.ui.ReaderUi
import com.cuadra.caja.data.scanner.ReaderEnv
import com.cuadra.caja.data.scanner.ScanSource
import com.cuadra.caja.data.scanner.ScannerSettings
import com.cuadra.caja.data.scanner.SetupState
import com.cuadra.caja.data.scanner.SetupUi
import com.cuadra.caja.data.scanner.scanRecord
import com.cuadra.caja.ui.screens.NotificationsContent
import com.cuadra.caja.ui.screens.PrefsDialog
import com.cuadra.caja.ui.screens.SchedulesContent
import com.cuadra.caja.ui.screens.SummaryContent
import com.cuadra.caja.ui.screens.UpdateRequiredContent
import com.cuadra.caja.ui.theme.CuadraColors
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h900dp-xhdpi", application = android.app.Application::class)
class GuardMiscTest {
    @get:Rule val rule = createEmptyComposeRule()

    private val full = GuardMatrix.FULL
    private val allModules = mapOf("credit" to true, "expenses" to true, "catalog" to true, "inventory" to true, "team" to true)

    @Test fun moreNotificationsSchedulesSummary() {
        val runner = GuardRunner(rule, "misc")
        runner.run(
            listOf(
                GuardCase("Tamaño de letra: Automático", full) { com.cuadra.caja.ui.screens.TextSizeScreen(com.cuadra.caja.domain.FontSizeChoice.AUTO, {}, {}) },
                GuardCase("Tamaño de letra: Como el sistema", full) { com.cuadra.caja.ui.screens.TextSizeScreen(com.cuadra.caja.domain.FontSizeChoice.SYSTEM, {}, {}) },
                GuardCase("Más: dueño con todo", full) { More(SyncStatus.IDLE, 0, 0, allModules, true) },
                GuardCase("Más: cajero", full) { More(SyncStatus.OFFLINE, 12_345, 0, null, false) },
                GuardCase("Más: admin con Equipo y Actividad, sin módulos de dueño", full) { More(SyncStatus.IDLE, 0, 0, null, true) },
                GuardCase("Más: sincronizando con error", full) { More(SyncStatus.NEEDS_ATTENTION, 3, 12_345, allModules, true) },
                GuardCase("Más: suspendido, idioma inglés", full) { More(SyncStatus.SUSPENDED, 0, 0, allModules, true, AppLanguage.ENGLISH) },
                GuardCase("Más: admin de plataforma (consola en el navegador)", full) { More(SyncStatus.IDLE, 0, 0, allModules, true, platform = true) },
                // «Preferencias de este teléfono»: tamaño de letra + interruptor de WhatsApp + Avanzado (cerrado / abierto), con el interruptor apagado y encendido.
                GuardCase("Más: preferencias del teléfono, WhatsApp apagado", full) { More(SyncStatus.IDLE, 0, 0, allModules, true, whatsApp = false) },
                GuardCase("Más: preferencias del teléfono, WhatsApp encendido", full) { More(SyncStatus.IDLE, 0, 0, allModules, true, whatsApp = true) },
                GuardCase("Más: preferencias, «Pedir descripción al agregar» encendido", full) { More(SyncStatus.IDLE, 0, 0, allModules, true, whatsApp = true, askDescription = true) },
                GuardCase("Más: preferencias con Avanzado abierto (inglés)", full) { More(SyncStatus.IDLE, 0, 0, allModules, true, AppLanguage.ENGLISH, whatsApp = true, advancedOpen = true) },
                GuardCase("Más: preferencias del cajero con Avanzado abierto", full) { More(SyncStatus.IDLE, 0, 0, null, false, whatsApp = false, advancedOpen = true) },
                GuardCase("Lector de códigos: DataWedge detectado, prueba vacía", full) { Reader(ReaderUi(env = ReaderEnv(dataWedge = true, hasCamera = true, hwKeyboard = true))) },
                GuardCase("Lector de códigos: sin DataWedge ni cámara", full) { Reader(ReaderUi(env = ReaderEnv(dataWedge = false, hasCamera = false), settings = ScannerSettings(useCamera = false))) },
                GuardCase("Lector de códigos: lectura de prueba (EAN-13 válido)", full) { Reader(ReaderUi(env = ReaderEnv(true, true, false), last = scanRecord("4006381333931", ScanSource.DATAWEDGE, "EAN-13", 1L), count = 12345)) },
                GuardCase("Lector de códigos: código larguísimo, dígito malo", full) { Reader(ReaderUi(env = ReaderEnv(true, true, true), last = scanRecord(Fixtures.CODE_LONG, ScanSource.WEDGE, null, 1L), count = 99999)) },
                GuardCase("Lector de códigos: EAN malo por cámara", full) { Reader(ReaderUi(last = scanRecord("4006381333932", ScanSource.CAMERA, "EAN-13", 1L), count = 3)) },
                GuardCase("Lector de códigos: configurando", full) { Reader(ReaderUi(env = ReaderEnv(true, true, false), setup = SetupUi(SetupState.RUNNING))) },
                GuardCase("Lector de códigos: configurado", full) { Reader(ReaderUi(env = ReaderEnv(true, true, false), setup = SetupUi(SetupState.OK), settings = ScannerSettings(wedgeSeen = true))) },
                GuardCase("Lector de códigos: DataWedge con error largo", full) { Reader(ReaderUi(env = ReaderEnv(true, true, false), setup = SetupUi(SetupState.FAILED, "RESULT_CODE=" + Fixtures.CODE_LONG))) },
                GuardCase("Lector de códigos: DataWedge sin respuesta", full) { Reader(ReaderUi(env = ReaderEnv(true, true, false), setup = SetupUi(SetupState.NO_ANSWER))) },
                GuardCase("Lector de códigos: lector apagado", full) { Reader(ReaderUi(env = ReaderEnv(true, true, true), settings = ScannerSettings(useReader = false, beep = false))) },
                GuardCase("Requiere atención: rechazadas y para revisar", full) { Attention(Fixtures.attention) },
                GuardCase("Requiere atención: vacía", full) { Attention(emptyList()) },
                GuardCase("Requiere atención: confirmar el PIN de quien la hizo", full) { Attention(Fixtures.attention, confirming = Fixtures.attention.first { it.canConfirmPin }) },
                GuardCase("Requiere atención: confirmar PIN sin conexión", full) {
                    Attention(Fixtures.attention, confirming = Fixtures.attention.first { it.canConfirmPin }, confirm = com.cuadra.caja.ui.PinConfirmUi(pin = "12", offline = true))
                },
                GuardCase("Confirma tu PIN: pantalla de administración", full) { PinConfirm(com.cuadra.caja.ui.PinConfirmUi()) },
                GuardCase("Confirma tu PIN: sin conexión", full) { PinConfirm(com.cuadra.caja.ui.PinConfirmUi(pin = "123", offline = true)) },
                GuardCase("Confirma tu PIN: comprobando", full) { PinConfirm(com.cuadra.caja.ui.PinConfirmUi(pin = "12345", busy = true)) },
                GuardCase("Confirma tu PIN: incorrecto y bloqueado", full) { PinConfirm(com.cuadra.caja.ui.PinConfirmUi(wrong = true, lockedMillis = 899_000)) },
                GuardCase("Confirma tu PIN: el servidor no respondió", full) { PinConfirm(com.cuadra.caja.ui.PinConfirmUi(failed = true, offline = true)) },
                GuardCase("Requiere atención: confirmar descartar", full) { Attention(Fixtures.attention, discarding = Fixtures.attention.first()) },
                GuardCase("Requiere atención: confirmar quitar una para revisar", full) { Attention(Fixtures.attention, discarding = Fixtures.attention.first { it.review }) },
                GuardCase("Acceso desactivado: enviando lo pendiente", full) { com.cuadra.caja.ui.screens.AccessDisabledScreen(Fixtures.BUSINESS_120, 12_345, 99, {}, {}) },
                GuardCase("Acceso desactivado: todo enviado", full) { com.cuadra.caja.ui.screens.AccessDisabledScreen(null, 0, 0, {}, {}) },
                GuardCase("Acceso desactivado: confirmar salir", full) { com.cuadra.caja.ui.screens.AccessDisabledScreen(Fixtures.BUSINESS_120, 3, 1, {}, {}, confirmOpen = true) },
                GuardCase("Aviso: la persona fue dada de baja", full) { com.cuadra.caja.ui.screens.DisabledMemberNotice(Fixtures.PERSON_LONG) {} },
                GuardCase("Actualizar la app", full) { UpdateRequiredContent {} },
                GuardCase("Franja de aviso", full) { Banner(Fixtures.NAME_200, CuadraColors.OrangeSoft, CuadraColors.Orange, {}, {}) },
                GuardCase("Avisos: bandeja", full) { Notifications(Fixtures.notifications, 12_345, PrefsUi(), allowed = false) },
                GuardCase("Avisos: vacía", full) { Notifications(emptyList(), 0, PrefsUi(), allowed = true, canSchedule = false) },
                GuardCase("Avisos: preferencias", full) { PrefsDialog(Fixtures.prefs, object : NotificationsActions {}) },
                GuardCase("Avisos: preferencias sin conexión", full) { PrefsDialog(PrefsUi(open = true, offline = true, prefs = null), object : NotificationsActions {}) },
                GuardCase("Programar: lista", full) { Schedules(SchedulesUi(loading = false, schedules = Fixtures.schedules)) },
                GuardCase("Programar: cargando", full) { Schedules(SchedulesUi(loading = true)) },
                GuardCase("Programar: error", full) { Schedules(SchedulesUi(loading = false, loadError = ErrorMessage(R.string.error_offline))) },
                GuardCase("Programar: vacía", full) { Schedules(SchedulesUi(loading = false)) },
                GuardCase("Programar: historial", full) { Schedules(SchedulesUi(loading = false, schedules = Fixtures.schedules, historyFor = Fixtures.schedules[0], history = Fixtures.scheduleRuns)) },
                GuardCase("Programar: borrar", full) { Schedules(SchedulesUi(loading = false, schedules = Fixtures.schedules, deleteId = "sc1")) },
                GuardCase("Programar: editor (repetir por semana)", full) {
                    Editor(
                        com.cuadra.caja.domain.ScheduleDraft(title = Fixtures.NAME_120, body = Fixtures.NAME_200, all = true, roles = setOf("ADMIN", "CASHIER"), memberIds = setOf("m1", "m3"), link = "cuadra://inventario", whenMode = ScheduleWhen.REPEAT, repeat = ScheduleRepeat.WEEKLY, days = setOf(1, 3, 5)),
                        com.cuadra.caja.domain.DraftError.DAYS, ErrorMessage(R.string.error_offline), false, Fixtures.members, object : SchedulesActions {},
                    )
                },
                GuardCase("Programar: editor (repetir por semana) (teclado)", GuardMatrix.KEYBOARD) {
                    Editor(
                        com.cuadra.caja.domain.ScheduleDraft(title = Fixtures.NAME_120, body = Fixtures.NAME_200, all = true, roles = setOf("ADMIN", "CASHIER"), memberIds = setOf("m1", "m3"), link = "cuadra://inventario", whenMode = ScheduleWhen.REPEAT, repeat = ScheduleRepeat.WEEKLY, days = setOf(1, 3, 5)),
                        com.cuadra.caja.domain.DraftError.DAYS, ErrorMessage(R.string.error_offline), false, Fixtures.members, object : SchedulesActions {},
                    )
                },
                GuardCase("Programar: editor (una vez, enviar ahora)", full) {
                    Editor(com.cuadra.caja.domain.ScheduleDraft(title = "", body = "", whenMode = ScheduleWhen.ONCE, date = "2026-12-31", time = "23:59"), com.cuadra.caja.domain.DraftError.TITLE, null, true, Fixtures.members, object : SchedulesActions {})
                },
                GuardCase("Programar: editor (una vez, enviar ahora) (teclado)", GuardMatrix.KEYBOARD) {
                    Editor(com.cuadra.caja.domain.ScheduleDraft(title = "", body = "", whenMode = ScheduleWhen.ONCE, date = "2026-12-31", time = "23:59"), com.cuadra.caja.domain.DraftError.TITLE, null, true, Fixtures.members, object : SchedulesActions {})
                },
                GuardCase("Resumen: hoy", full) { Summary(SummaryData0(), RangeChoice(RangePreset.TODAY)) },
                GuardCase("Resumen: con descuentos por promociones", full) { Summary(SummaryData0().copy(promotionDiscountMinor = Fixtures.HUGE), RangeChoice(RangePreset.TODAY)) },
                GuardCase("Resumen: ayer con atajo al cierre", full) { Summary(SummaryData0(), RangeChoice(RangePreset.YESTERDAY), yesterday = true) },
                GuardCase("Resumen: rango personalizado", full) { Summary(SummaryData0(), RangeChoice(RangePreset.CUSTOM, LocalDate.of(2025, 1, 1) to LocalDate.of(2026, 12, 31))) },
                GuardCase("Resumen: mes pasado", full) { Summary(SummaryData0(), RangeChoice(RangePreset.LAST_MONTH)) },
                GuardCase("Resumen: cargando", full) { Summary(null, RangeChoice(RangePreset.LAST_7)) },
                GuardCase("Resumen: sin ventas", full) { Summary(Fixtures.summary.copy(sales = com.cuadra.caja.data.local.SalesTotals(0, 0, 0), methods = emptyList(), top = emptyList(), lowStock = 0), RangeChoice(RangePreset.THIS_MONTH)) },
            ),
        )
        runner.assertClean()
    }

    @Composable private fun Attention(
        items: List<com.cuadra.caja.domain.Attention.Item>, discarding: com.cuadra.caja.domain.Attention.Item? = null,
        confirming: com.cuadra.caja.domain.Attention.Item? = null, confirm: com.cuadra.caja.ui.PinConfirmUi = com.cuadra.caja.ui.PinConfirmUi(),
    ) = com.cuadra.caja.ui.screens.AttentionContent(
        com.cuadra.caja.ui.AttentionUi(loading = false, items = items, discarding = discarding, confirming = confirming, confirm = confirm), object : com.cuadra.caja.ui.AttentionActions {}, {}, java.time.ZoneOffset.UTC,
    )

    @Composable private fun PinConfirm(ui: com.cuadra.caja.ui.PinConfirmUi) = com.cuadra.caja.ui.screens.PinConfirmContent(ui, object : com.cuadra.caja.ui.PinConfirmActions {}, {})

    private fun SummaryData0() = Fixtures.summary

    @Composable private fun Summary(data: com.cuadra.caja.ui.SummaryData?, choice: RangeChoice, yesterday: Boolean = false) =
        SummaryContent(data, choice, Fixtures.calendar, object : SummaryActions {}, {}, if (yesterday) ({}) else null, Fixtures.NOW)

    @Composable private fun More(status: SyncStatus, pending: Int, failed: Int, modules: Map<String, Boolean>?, manager: Boolean, language: AppLanguage = AppLanguage.SPANISH, whatsApp: Boolean = false, advancedOpen: Boolean = false, askDescription: Boolean = false, platform: Boolean = false) {
        // `modules != null` = dueño; `manager` = dueño o admin; sin ninguno de los dos = cajero.
        val go: () -> Unit = {}
        MoreScreen(
            Fixtures.BUSINESS_120, Fixtures.PERSON_LONG, status, pending, failed, language, 12_345,
            MoreActions(
                onSettings = if (manager) go else null, onTeam = if (manager) go else null, onActivity = if (manager) go else null, onTemplates = if (manager) go else null,
                onProducts = if (manager) go else null, onInventory = if (manager) go else null, onPurchases = if (manager) go else null,
                onSummary = if (manager) go else null, onDailyClose = if (manager) go else null, onNotifications = go, onReader = go, onPrinter = go, onHelp = go,
                onSupport = if (manager) go else null, onMyAccount = go, onTextSize = go, onAttention = go, onPlatformConsole = if (platform) go else null,
            ),
            advancedOpen = advancedOpen, offerWhatsApp = whatsApp, askDescription = askDescription,
        )
    }

    @Composable private fun Reader(ui: ReaderUi) = ReaderContent(ui, object : ReaderActions {}, {}, {})

    @Composable private fun Notifications(items: List<com.cuadra.caja.data.local.NotificationEntity>, unread: Int, prefs: PrefsUi, allowed: Boolean, canSchedule: Boolean = true) =
        NotificationsContent(items, unread, prefs, allowed, canSchedule, object : NotificationsActions {}, {}, {}, {}, {}, {})

    @Composable private fun Schedules(ui: SchedulesUi) = SchedulesContent(ui, Fixtures.members.map { it }, "America/Managua", object : SchedulesActions {}, {})
}

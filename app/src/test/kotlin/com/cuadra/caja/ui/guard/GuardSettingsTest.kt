package com.cuadra.caja.ui.guard

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import com.cuadra.caja.R
import com.cuadra.caja.data.local.BusinessEntity
import com.cuadra.caja.data.remote.ActivityEntryDto
import com.cuadra.caja.domain.ActivityFeed
import com.cuadra.caja.domain.ActivityKind
import com.cuadra.caja.domain.Diagnostics
import com.cuadra.caja.domain.MessageKind
import com.cuadra.caja.domain.NotifyDraft
import com.cuadra.caja.domain.SettingsDraft
import com.cuadra.caja.domain.SettingsError
import com.cuadra.caja.domain.TicketCategory
import com.cuadra.caja.domain.TicketDraft
import com.cuadra.caja.ui.ActivityActions
import com.cuadra.caja.ui.ActivityUi
import com.cuadra.caja.ui.DeleteUi
import com.cuadra.caja.ui.DiscardQueue
import com.cuadra.caja.ui.ErrorMessage
import com.cuadra.caja.ui.HelpActions
import com.cuadra.caja.ui.HelpTab
import com.cuadra.caja.ui.HelpUi
import com.cuadra.caja.ui.SettingsActions
import com.cuadra.caja.ui.SettingsUi
import com.cuadra.caja.ui.TeamNotice
import com.cuadra.caja.ui.TemplatesActions
import com.cuadra.caja.ui.TemplatesUi
import com.cuadra.caja.ui.screens.ActivityContent
import com.cuadra.caja.ui.screens.HelpContent
import com.cuadra.caja.ui.screens.SettingsContent
import com.cuadra.caja.ui.screens.SupportActions
import com.cuadra.caja.ui.screens.SupportContent
import com.cuadra.caja.ui.screens.TemplatesContent
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Lote 6 de paridad con el panel web: Ajustes del negocio (con su aviso de jornada, selector de zona y hoja de eliminar), Apóyame, Actividad,
 * Ayuda y contacto, Mensajes de WhatsApp. Todas las pantallas y hojas con la matriz completa (letra × ancho × idioma) y, las que tienen campos, con teclado.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h900dp-xhdpi", application = android.app.Application::class)
class GuardSettingsTest {
    @get:Rule val rule = createEmptyComposeRule()

    private val full = GuardMatrix.FULL
    private val kb = GuardMatrix.KEYBOARD

    // ---------- Ajustes del negocio ----------
    private val rulesJson = """[{"from":"1970-01-01","timezone":"America/Managua","dayCutoff":"02:00"},{"from":"2026-10-10","timezone":"America/New_York","dayCutoff":"04:00"}]"""
    private fun biz(name: String = Fixtures.BUSINESS_120, rules: String = "[]", pending: String? = null, modules: String = """{"credit":true,"expenses":true,"catalog":true,"inventory":false,"team":true}""") =
        BusinessEntity("b1", name, "NI", "NIO", "America/Managua", "es", "02:00", "OFF", modules, "[\"TYPE\",\"QUICK\"]", true, dayRulesJson = rules, dayRuleEffectiveFrom = pending, type = "Quesería", creditDefaultDueDays = 15, creditOverdueDays = 45, creditLimitEnforced = true)

    private fun draft(b: BusinessEntity = biz()) = SettingsDraft.of(b)
    private val notify = NotifyDraft("21:30", "07:00", true, "21:00", "24")

    private fun ui(role: String = "OWNER", d: SettingsDraft? = draft(), base: SettingsDraft? = d, notify: NotifyDraft? = this.notify, offline: Boolean = false, loadError: ErrorMessage? = null,
               loading: Boolean = false, validation: SettingsError? = null, notice: TeamNotice? = null, confirm: Boolean = false, zone: String? = null, delete: DeleteUi? = null,
               notifyNotice: TeamNotice? = null, notifyBase: NotifyDraft? = notify, notifyOffline: Boolean = false, saving: Boolean = false, discard: DiscardQueue? = null) =
        SettingsUi(loading = loading, offline = offline, loadError = loadError, role = role, base = base, draft = d, notify = notify, notifyBase = notifyBase, notifyOffline = notifyOffline,
            validation = validation, notice = notice, confirmDayRule = confirm, zoneQuery = zone, delete = delete, notifyNotice = notifyNotice, saving = saving, discardQueue = discard)

    @Composable private fun Settings(ui: SettingsUi, b: BusinessEntity? = biz()) = SettingsContent(ui, b, object : SettingsActions {}, {})

    @Test fun businessSettings() {
        val runner = GuardRunner(rule, "settings")
        val base = draft()
        val dirty = base.copy(name = Fixtures.NAME_120, timezone = "America/New_York", dayCutoff = "02:00", creditOverdueDays = "60")
        val history = biz(rules = rulesJson, pending = "2026-10-10")
        runner.run(
            listOf(
                GuardCase("Ajustes: dueño, sin cambios", full) { Settings(ui()) },
                GuardCase("Ajustes: dueño, sin cambios (teclado)", kb) { Settings(ui()) },
                // Moneda fija con actividad (se explica por qué); antes de la primera venta, país y moneda se eligen (arriba, `biz()` sin actividad).
                GuardCase("Ajustes: moneda fija con ventas", full) { Settings(ui(), biz().copy(currencyLocked = true)) },
                GuardCase("Ajustes: país y moneda antes de vender, otro país", full) { Settings(ui(d = draft().copy(country = "HN", currency = "USD"))) },
                GuardCase("Ajustes: dueño, cambios sin guardar con aviso de jornada y consejo de horario de verano", full) { Settings(ui(d = dirty)) },
                GuardCase("Ajustes: dueño, cambios sin guardar (teclado)", kb) { Settings(ui(d = dirty)) },
                GuardCase("Ajustes: dueño, error de validación", full) { Settings(ui(d = base.copy(name = ""), validation = SettingsError.NAME)) },
                GuardCase("Ajustes: dueño, guardando", full) { Settings(ui(d = dirty, saving = true, notice = TeamNotice(ErrorMessage(R.string.set_saved), false))) },
                GuardCase("Ajustes: dueño, error del servidor", full) { Settings(ui(d = dirty, notice = TeamNotice(ErrorMessage(R.string.set_err_INVALID_TIMEZONE), true))) },
                GuardCase("Ajustes: regla pendiente e historial de reglas", full) { Settings(ui(d = draft(history)), history) },
                GuardCase("Ajustes: administrador, edita (sin zona de peligro)", full) { Settings(ui(role = "ADMIN"), history) },
                GuardCase("Ajustes: administrador, cambios sin guardar", full) { Settings(ui(role = "ADMIN", d = dirty)) },
                GuardCase("Ajustes: administrador, cambios sin guardar (teclado)", kb) { Settings(ui(role = "ADMIN", d = dirty)) },
                GuardCase("Ajustes: cajero, solo lectura", full) { Settings(ui(role = "CASHIER"), history) },
                GuardCase("Ajustes: sin conexión", full) { Settings(ui(offline = true, notify = null, notifyOffline = true)) },
                GuardCase("Ajustes: error de carga", full) { Settings(ui(loadError = ErrorMessage(R.string.error_generic))) },
                GuardCase("Ajustes: cargando", full) { Settings(ui(d = null, base = null, notify = null, loading = true), null) },
                GuardCase("Ajustes: reglas de avisos con cambios y aviso", full) { Settings(ui(notify = notify.copy(staleHours = "720"), notifyBase = notify, notifyNotice = TeamNotice(ErrorMessage(R.string.set_val_stale), true))) },
                GuardCase("Ajustes: reglas de avisos con cambios (teclado)", kb) { Settings(ui(notify = notify.copy(staleHours = "720"), notifyBase = notify)) },
                GuardCase("Ajustes: aviso de jornada antes de guardar", full) { Settings(ui(d = dirty, confirm = true)) },
                GuardCase("Ajustes: aviso al apagar el cobro en caja con cuentas pendientes", full) { Settings(ui(d = draft().copy(registerCheckout = false), discard = DiscardQueue(2, 1_234_567_89L))) },
                GuardCase("Ajustes: aviso al apagar el cobro en caja con una cuenta", full) { Settings(ui(d = draft().copy(registerCheckout = false), discard = DiscardQueue(1, 15000))) },
                GuardCase("Ajustes: selector de zona horaria vacío", full) { Settings(ui(zone = "")) },
                GuardCase("Ajustes: selector de zona horaria vacío (teclado)", kb) { Settings(ui(zone = "")) },
                GuardCase("Ajustes: selector de zona con búsqueda", full) { Settings(ui(zone = "ameri")) },
                GuardCase("Ajustes: selector de zona sin resultados", full) { Settings(ui(zone = "zzzz")) },
                GuardCase("Ajustes: eliminar el negocio, vacío", full) { Settings(ui(delete = DeleteUi())) },
                GuardCase("Ajustes: eliminar el negocio, vacío (teclado)", kb) { Settings(ui(delete = DeleteUi())) },
                GuardCase("Ajustes: eliminar el negocio, listo para confirmar", full) { Settings(ui(delete = DeleteUi(Fixtures.BUSINESS_120.uppercase(), true))) },
                GuardCase("Ajustes: eliminar el negocio, listo para confirmar (teclado)", kb) { Settings(ui(delete = DeleteUi(Fixtures.BUSINESS_120.uppercase(), true))) },
                GuardCase("Ajustes: eliminar el negocio, en curso", full) { Settings(ui(delete = DeleteUi(Fixtures.BUSINESS_120, true, busy = true))) },
                GuardCase("Ajustes: eliminar el negocio, error 403", full) { Settings(ui(delete = DeleteUi(Fixtures.BUSINESS_120, true, error = ErrorMessage(R.string.set_delete_err_forbidden)))) },
            ),
        )
        runner.assertClean()
    }

    // ---------- Apóyame ----------
    @Composable private fun Support(play: Boolean) = SupportContent(Fixtures.EMAIL_LONG, play, object : SupportActions {}, {})

    @Test fun support() {
        val runner = GuardRunner(rule, "support")
        runner.run(
            listOf(
                GuardCase("Apóyame: versión normal", full) { Support(play = false) },
                GuardCase("Apóyame: versión de Play (solo texto y contacto)", full) { Support(play = true) },
            ),
        )
        runner.assertClean()
    }

    // ---------- Actividad ----------
    private fun entry(id: Long, action: String, detail: String? = null, actor: String? = Fixtures.PERSON_LONG, platform: Boolean = false, at: String = "2026-09-29T15:45:00Z") =
        ActivityEntryDto(id, action, null, detail, actor, platform, at)

    private val entries = listOf(
        entry(1, "platform.view_as", "Revisión de un reporte de errores — motivo: ${Fixtures.NAME_200}", null, true),
        entry(2, "sale.complete", "total=123456789"),
        entry(3, "sale.cancel", "COMPLETED: ${Fixtures.NAME_120}"),
        entry(4, "product.create", """{"name":"${Fixtures.NAME_120}","pricing":"FIXED","priceMinor":1234500}"""),
        entry(5, "product.update", """{"changes":{"priceMinor":{"from":1,"to":2},"name":{"from":"a","to":"b"}}}""", at = "2026-09-28T15:45:00Z"),
        entry(6, "member.create", "CASHIER", Fixtures.LONG_WORD),
        entry(7, "business.update", "timezone,day_cutoff"),
        entry(8, "device.revoke", null, "Ana", at = "2026-08-01T15:45:00Z"),
        entry(9, "future.action", null, null),
        entry(10, "platform.plan_changed", null, null, true),
        entry(11, "customer.create", Fixtures.PERSON_LONG),
        entry(12, "credit.payment", "amount=99999999"),
    ).map(ActivityFeed::row)

    @Composable private fun Activity(ui: ActivityUi) = ActivityContent(ui, "America/Managua", object : ActivityActions {}, {})

    @Test fun activity() {
        val runner = GuardRunner(rule, "activity")
        runner.run(
            listOf(
                GuardCase("Actividad: lista completa con la vista de soporte destacada", full) { Activity(ActivityUi(rows = entries, loading = false, hasMore = true)) },
                GuardCase("Actividad: filtro Ventas", full) { Activity(ActivityUi(rows = entries, kind = ActivityKind.SALES, loading = false)) },
                GuardCase("Actividad: filtro Plataforma", full) { Activity(ActivityUi(rows = entries, kind = ActivityKind.PLATFORM, loading = false)) },
                GuardCase("Actividad: filtro Equipo, sin resultados pero con más páginas", full) { Activity(ActivityUi(rows = entries.take(3), kind = ActivityKind.TEAM, loading = false, hasMore = true)) },
                GuardCase("Actividad: vacía", full) { Activity(ActivityUi(loading = false)) },
                GuardCase("Actividad: cargando", full) { Activity(ActivityUi(loading = true)) },
                GuardCase("Actividad: cargando más", full) { Activity(ActivityUi(rows = entries, loading = false, loadingMore = true, hasMore = true)) },
                GuardCase("Actividad: sin conexión", full) { Activity(ActivityUi(rows = entries.take(2), loading = false, offline = true)) },
                GuardCase("Actividad: error", full) { Activity(ActivityUi(loading = false, error = ErrorMessage(R.string.set_err_FORBIDDEN))) },
            ),
        )
        runner.assertClean()
    }

    // ---------- Ayuda ----------
    private val diag = Diagnostics("0.1.0-debug-with-a-very-long-suffix-1234567890", "Android 15 (API 35)", "Samsung Galaxy Tab Active5 Enterprise Edition", "73b237f6-1843-451a-b34f-14e68d883c53", "2026-09-29T15:45:00Z", 12_345, "es")

    @Composable private fun Help(ui: HelpUi, email: String? = "soporte@cuentiva.example") = HelpContent(ui, email, object : HelpActions {}, {})

    @Test fun help() {
        val runner = GuardRunner(rule, "help")
        val long = TicketDraft(TicketCategory.PROBLEM, Fixtures.NAME_200.repeat(4), "una.persona.con.un.correo.muy.largo.de.verdad@empresa-de-ejemplo-internacional.example.com", Fixtures.PHONE_30, true)
        runner.run(
            listOf(
                GuardCase("Ayuda: preguntas, todas cerradas", full) { Help(HelpUi(diagnostics = diag)) },
                GuardCase("Ayuda: preguntas, todas abiertas", full) { Help(HelpUi(diagnostics = diag, expanded = setOf(0, 1, 2, 3))) },
                GuardCase("Ayuda: preguntas, sin correo de soporte", full) { Help(HelpUi(), email = null) },
                GuardCase("Ayuda: correo de soporte larguísimo", full) { Help(HelpUi(), email = "una.direccion.de.soporte.demasiado.larga.para.una.sola.linea@cuentiva-internacional.example.com") },
                GuardCase("Ayuda: escribir, vacío", full) { Help(HelpUi(tab = HelpTab.WRITE, diagnostics = diag)) },
                GuardCase("Ayuda: escribir, vacío (teclado)", kb) { Help(HelpUi(tab = HelpTab.WRITE, diagnostics = diag)) },
                GuardCase("Ayuda: escribir, mensaje larguísimo y datos técnicos", full) { Help(HelpUi(tab = HelpTab.WRITE, draft = long, diagnostics = diag)) },
                GuardCase("Ayuda: escribir, mensaje larguísimo (teclado)", kb) { Help(HelpUi(tab = HelpTab.WRITE, draft = long, diagnostics = diag)) },
                GuardCase("Ayuda: escribir, sin datos técnicos y con error de validación", full) { Help(HelpUi(tab = HelpTab.WRITE, draft = TicketDraft(message = "corto", includeDiagnostics = false), validation = com.cuadra.caja.domain.TicketError.MESSAGE_SHORT)) },
                GuardCase("Ayuda: escribir, enviando", full) { Help(HelpUi(tab = HelpTab.WRITE, draft = long, diagnostics = diag, sending = true)) },
                GuardCase("Ayuda: escribir, demasiados envíos", full) { Help(HelpUi(tab = HelpTab.WRITE, draft = long, diagnostics = diag, error = ErrorMessage(R.string.help_err_too_many))) },
                GuardCase("Ayuda: mensaje enviado con referencia", full) { Help(HelpUi(tab = HelpTab.WRITE, reference = "#1a2b3c4d")) },
            ),
        )
        runner.assertClean()
    }

    // ---------- Plantillas de WhatsApp ----------
    @Composable private fun Templates(ui: TemplatesUi, text: String, saved: String?) = TemplatesContent(ui, text, saved, Fixtures.BUSINESS_120, object : TemplatesActions {}, {})

    @Test fun templates() {
        val runner = GuardRunner(rule, "templates")
        val factory = com.cuadra.caja.domain.MessageTemplates.default(MessageKind.REMINDER, "es")
        val custom = "Hola {cliente}, ${Fixtures.NAME_200} {saldo} {inexistente} {negocio}"
        runner.run(
            listOf(
                GuardCase("Plantillas: texto de fábrica", full) { Templates(TemplatesUi(loading = false), factory, null) },
                GuardCase("Plantillas: texto de fábrica (teclado)", kb) { Templates(TemplatesUi(loading = false), factory, null) },
                GuardCase("Plantillas: texto propio con variable desconocida", full) { Templates(TemplatesUi(loading = false, kind = MessageKind.STATEMENT, locale = "en"), custom, "guardado") },
                GuardCase("Plantillas: texto propio (teclado)", kb) { Templates(TemplatesUi(loading = false, kind = MessageKind.STATEMENT, locale = "en"), custom, "guardado") },
                GuardCase("Plantillas: comprobante de venta, guardando con aviso", full) { Templates(TemplatesUi(loading = false, kind = MessageKind.TICKET, saving = true, notice = TeamNotice(ErrorMessage(R.string.tpl_saved), false)), factory, null) },
                GuardCase("Plantillas: sin conexión y error", full) { Templates(TemplatesUi(loading = false, offline = true, notice = TeamNotice(ErrorMessage(R.string.tpl_err_INVALID_BODY), true)), custom, "guardado") },
                GuardCase("Plantillas: restaurar el de fábrica", full) { Templates(TemplatesUi(loading = false, confirmReset = true), custom, "guardado") },
                GuardCase("Plantillas: texto vacío", full) { Templates(TemplatesUi(loading = false), "", null) },
            ),
        )
        runner.assertClean()
    }
}

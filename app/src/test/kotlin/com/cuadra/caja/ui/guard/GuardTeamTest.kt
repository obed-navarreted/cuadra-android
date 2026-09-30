package com.cuadra.caja.ui.guard

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import com.cuadra.caja.R
import com.cuadra.caja.data.local.MemberEntity
import com.cuadra.caja.data.remote.DeviceDto
import com.cuadra.caja.domain.AccountDraft
import com.cuadra.caja.ui.AccountActions
import com.cuadra.caja.ui.AccountUi
import com.cuadra.caja.ui.ConfirmKind
import com.cuadra.caja.ui.DevicesActions
import com.cuadra.caja.ui.DevicesUi
import com.cuadra.caja.ui.ErrorMessage
import com.cuadra.caja.ui.TeamActions
import com.cuadra.caja.ui.TeamDialog
import com.cuadra.caja.ui.TeamNotice
import com.cuadra.caja.ui.TeamUi
import com.cuadra.caja.ui.screens.AccountContent
import com.cuadra.caja.ui.screens.DevicesContent
import com.cuadra.caja.ui.screens.TeamContent
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Equipo (personas, invitaciones, teléfonos) y «Mi cuenta»: todas las pantallas, hojas y diálogos, con la matriz completa y las variantes con teclado. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h900dp-xhdpi", application = android.app.Application::class)
class GuardTeamTest {
    @get:Rule val rule = createEmptyComposeRule()

    private val full = GuardMatrix.FULL
    private val kb = GuardMatrix.KEYBOARD

    // ---------- datos de peor caso ----------
    private fun m(id: String, name: String, role: String, status: String = "ACTIVE", pinSet: Boolean = true, google: Boolean = false, color: String? = null) =
        MemberEntity(id, name, role, status, google, pinSet, false, color, null)

    private val owner = m("m1", Fixtures.PERSON_LONG, "OWNER", google = true, color = "#1f7a55")
    private val admin = m("m2", "Ana", "ADMIN", color = "#2f6db5")
    private val admin2 = m("m6", Fixtures.NAME_60, "ADMIN", google = true, color = "#8a4fb0")
    private val cashier = m("m3", Fixtures.LONG_WORD, "CASHIER", color = "#c9793a")
    private val disabled = m("m4", Fixtures.NAME_120, "CASHIER", status = "DISABLED", pinSet = false)
    private val kevin = m("m5", "Kevin", "CASHIER", color = "#b5443a")
    private val people = listOf(owner, admin, admin2, cashier, disabled, kevin)

    private fun ui(role: String = "OWNER", id: String? = "m1", loading: Boolean = false, offline: Boolean = false, selected: MemberEntity? = null, dialog: TeamDialog? = null, saving: Boolean = false,
                   loadError: ErrorMessage? = null, notice: TeamNotice? = null) =
        TeamUi(viewerId = id, viewerRole = role, loading = loading, offline = offline, loadError = loadError, selected = selected, dialog = dialog, saving = saving, notice = notice)

    @Composable private fun Team(ui: TeamUi, members: List<MemberEntity> = people) = TeamContent(ui, members, object : TeamActions {}, {}, {}, {})

    private val phone = DeviceDto("d1", "Caja emulador", "Pixel 8", "0.1.0", "r1", "Caja principal", "2026-09-01T10:00:00Z", "2026-09-29T10:00:00Z", "2026-09-29T09:58:00Z", 0, false)
    private val phoneLong = DeviceDto("d2", Fixtures.NAME_120, Fixtures.NAME_60, "10.123.456-rc1", "r1", Fixtures.NAME_60, "2026-09-01T10:00:00Z", null, null, 12_345, false)
    private val phoneRevoked = DeviceDto("d3", "Teléfono viejo", null, null, null, null, null, null, null, 3, true)

    @Composable private fun Devices(ui: DevicesUi) = DevicesContent(ui, "America/Managua", object : DevicesActions {}, {}, {})

    @Composable private fun Account(ui: AccountUi, deleteBusiness: Boolean = false) =
        AccountContent(ui, object : AccountActions {}, {}, onDeleteBusiness = if (deleteBusiness) ({}) else null, onInfo = {}, infoUrl = "https://panel.cuadra.app/eliminar-cuenta")

    @Test fun team() {
        val runner = GuardRunner(rule, "team")
        val editD = TeamDialog.Edit(cashier, Fixtures.NAME_200.take(80), "#c9793a", ErrorMessage(R.string.team_err_CANNOT_MODIFY_OWNER))
        val pinD = TeamDialog.Pin(cashier, "43210", "432", true, ErrorMessage(R.string.team_err_INVALID_PIN))
        val newD = TeamDialog.New(Fixtures.NAME_60, "ADMIN", "12345", "12345", false, ErrorMessage(R.string.error_PLAN_LIMIT_MEMBERS, 3))
        val takenD = TeamDialog.New("Kevin", "CASHIER", "12345", "12345", true, ErrorMessage(R.string.team_err_NAME_TAKEN))
        val renameTaken = TeamDialog.Edit(kevin, "Ana", "#b5443a", ErrorMessage(R.string.team_err_NAME_TAKEN))
        val credentials = TeamDialog.Credentials(Fixtures.BUSINESS_120, "13085", Fixtures.NAME_120.take(80), "12345")
        runner.run(
            listOf(
                GuardCase("Equipo: el dueño ve a todos", full) { Team(ui()) },
                GuardCase("Equipo: un admin", full) { Team(ui("ADMIN", "m2")) },
                GuardCase("Equipo: cargando y sin nadie", full) { Team(ui(loading = true), emptyList()) },
                GuardCase("Equipo: vacío", full) { Team(ui(), emptyList()) },
                GuardCase("Equipo: sin conexión (lista guardada)", full) { Team(ui(offline = true)) },
                GuardCase("Equipo: error de carga", full) { Team(ui(loadError = ErrorMessage(R.string.error_generic))) },
                GuardCase("Equipo: aviso de PIN guardado", full) { Team(ui(notice = TeamNotice(ErrorMessage(R.string.team_pin_saved, detail = Fixtures.PERSON_LONG), false))) },
                GuardCase("Equipo: aviso de error", full) { Team(ui(notice = TeamNotice(ErrorMessage(R.string.team_err_CANNOT_MODIFY_SELF), true))) },
                GuardCase("Equipo: hoja del dueño mirada por un admin (bloqueada)", full) { Team(ui("ADMIN", "m2", selected = owner)) },
                GuardCase("Equipo: hoja de un cajero mirada por un admin", full) { Team(ui("ADMIN", "m2", selected = cashier)) },
                GuardCase("Equipo: hoja de otro admin mirada por un admin", full) { Team(ui("ADMIN", "m2", selected = admin2)) },
                GuardCase("Equipo: hoja de un deshabilitado (habilitar)", full) { Team(ui(selected = disabled)) },
                GuardCase("Equipo: hoja de uno mismo (dueño)", full) { Team(ui(selected = owner)) },
                GuardCase("Equipo: hoja de uno mismo (admin)", full) { Team(ui("ADMIN", "m2", selected = admin)) },
                GuardCase("Equipo: hoja sin permisos (rol desconocido)", full) { Team(ui("", null, selected = kevin)) },
                GuardCase("Equipo: editar", full) { Team(ui(dialog = editD)) },
                GuardCase("Equipo: editar (teclado)", kb) { Team(ui(dialog = editD)) },
                GuardCase("Equipo: confirmar cambio a admin", full) { Team(ui(dialog = TeamDialog.Confirm(cashier, ConfirmKind.MAKE_ADMIN))) },
                GuardCase("Equipo: confirmar cambio a cajero", full) { Team(ui(dialog = TeamDialog.Confirm(admin2, ConfirmKind.MAKE_CASHIER))) },
                GuardCase("Equipo: confirmar deshabilitar", full) { Team(ui(dialog = TeamDialog.Confirm(disabled, ConfirmKind.DISABLE), saving = true)) },
                GuardCase("Equipo: confirmar habilitar", full) { Team(ui(dialog = TeamDialog.Confirm(disabled, ConfirmKind.ENABLE))) },
                GuardCase("Equipo: restablecer PIN (no coincide)", full) { Team(ui(dialog = pinD)) },
                GuardCase("Equipo: restablecer PIN (no coincide) (teclado)", kb) { Team(ui(dialog = pinD)) },
                GuardCase("Equipo: agregar persona con error de plan", full) { Team(ui(dialog = newD)) },
                // El PIN no se repite dentro del negocio: al agregar y al restablecer.
                GuardCase("Equipo: agregar persona con PIN ocupado", full) { Team(ui(dialog = takenD.copy(error = ErrorMessage(R.string.team_err_PIN_TAKEN)))) },
                GuardCase("Equipo: agregar persona con PIN ocupado (teclado)", kb) { Team(ui(dialog = takenD.copy(name = Fixtures.NAME_60, error = ErrorMessage(R.string.team_err_PIN_TAKEN)))) },
                GuardCase("Equipo: restablecer PIN ocupado", full) { Team(ui(dialog = pinD.copy(confirm = "43210", error = ErrorMessage(R.string.team_err_PIN_TAKEN)))) },
                GuardCase("Equipo: agregar persona con error de plan (teclado)", kb) { Team(ui(dialog = newD)) },
                // Código del negocio (arriba de la lista): dueño con todo, admin sin renovar/elegir, sin conexión, con el código más largo posible.
                GuardCase("Equipo: código del negocio (dueño)", full) { Team(ui().copy(businessName = Fixtures.BUSINESS_120, accessCode = "13085")) },
                GuardCase("Equipo: código del negocio (admin)", full) { Team(ui("ADMIN", "m2").copy(businessName = Fixtures.NAME_60, accessCode = "99999")) },
                GuardCase("Equipo: código del negocio aún desconocido", full) { Team(ui().copy(businessName = "", accessCode = null)) },
                GuardCase("Equipo: renovar código", full) { Team(ui().copy(accessCode = "13085", dialog = TeamDialog.RenewCode())) },
                GuardCase("Equipo: renovar código con error", full) { Team(ui().copy(accessCode = "13085", dialog = TeamDialog.RenewCode(ErrorMessage(R.string.error_offline)), saving = true)) },
                GuardCase("Equipo: elegir mi código", full) { Team(ui().copy(accessCode = "13085", dialog = TeamDialog.ChooseCode("123"))) },
                GuardCase("Equipo: elegir mi código ocupado (teclado)", kb) { Team(ui().copy(accessCode = "13085", dialog = TeamDialog.ChooseCode("24680", ErrorMessage(R.string.team_err_ACCESS_CODE_TAKEN)))) },
                GuardCase("Equipo: elegir mi código con 0 al inicio (teclado)", kb) { Team(ui().copy(accessCode = "13085", dialog = TeamDialog.ChooseCode("01234", ErrorMessage(R.string.team_err_INVALID_ACCESS_CODE)))) },
                // Tarjeta de datos para entrar tras crear a alguien: código + PIN (y el nombre, de referencia).
                GuardCase("Equipo: datos para entrar (persona recién creada)", full) { Team(ui().copy(accessCode = "13085", dialog = credentials)) },
                GuardCase("Equipo: agregar con nombre repetido (teclado)", kb) { Team(ui(dialog = takenD)) },
                GuardCase("Equipo: renombrar a un nombre repetido (teclado)", kb) { Team(ui(dialog = renameTaken)) },
                GuardCase("Equipo: agregar persona vacía", full) { Team(ui("ADMIN", "m2", dialog = TeamDialog.New())) },
                GuardCase("Equipo: agregar persona vacía (teclado)", kb) { Team(ui("ADMIN", "m2", dialog = TeamDialog.New())) },
            ),
        )
        runner.assertClean()
    }

    @Test fun devices() {
        val runner = GuardRunner(rule, "team-devices")
        runner.run(
            listOf(
                GuardCase("Teléfonos: lista", full) { Devices(DevicesUi(loading = false, devices = listOf(phone, phoneLong, phoneRevoked), thisDeviceId = "d1")) },
                GuardCase("Teléfonos: cargando", full) { Devices(DevicesUi()) },
                GuardCase("Teléfonos: vacía", full) { Devices(DevicesUi(loading = false)) },
                GuardCase("Teléfonos: error", full) { Devices(DevicesUi(loading = false, loadError = ErrorMessage(R.string.error_offline))) },
                GuardCase("Teléfonos: revocar con pendientes", full) { Devices(DevicesUi(loading = false, devices = listOf(phoneLong), revoking = phoneLong, revokeError = ErrorMessage(R.string.team_err_DEVICE_NOT_FOUND))) },
                GuardCase("Teléfonos: revocar sin pendientes", full) { Devices(DevicesUi(loading = false, devices = listOf(phone.copy(id = "d9")), revoking = phone.copy(id = "d9"))) },
                GuardCase("Teléfonos: aviso de revocado", full) { Devices(DevicesUi(loading = false, devices = listOf(phone), notice = ErrorMessage(R.string.dev_revoked, detail = Fixtures.NAME_120))) },
            ),
        )
        runner.assertClean()
    }

    @Test fun account() {
        val runner = GuardRunner(rule, "team-account")
        val filled = AccountDraft(Fixtures.PERSON_LONG, Fixtures.PERSON_LONG + " Jr", true, "12345", "56789", "5678")
        runner.run(
            listOf(
                GuardCase("Mi cuenta: cajero", full) { Account(AccountUi(loaded = true, draft = AccountDraft("Kevin"))) },
                GuardCase("Mi cuenta: cajero (teclado)", kb) { Account(AccountUi(loaded = true, draft = AccountDraft("Kevin"))) },
                GuardCase("Mi cuenta: nombre larguísimo, PIN sin coincidir, error", full) { Account(AccountUi(loaded = true, draft = filled, error = ErrorMessage(R.string.pin_wrong))) },
                GuardCase("Mi cuenta: nombre larguísimo, PIN sin coincidir, error (teclado)", kb) { Account(AccountUi(loaded = true, draft = filled, error = ErrorMessage(R.string.pin_wrong))) },
                GuardCase("Mi cuenta: bloqueado por intentos", full) { Account(AccountUi(loaded = true, draft = filled.copy(confirm = "56789"), lockedMillis = 899_000)) },
                GuardCase("Mi cuenta: guardado", full) { Account(AccountUi(loaded = true, draft = AccountDraft("Kevin"), saved = true)) },
                GuardCase("Mi cuenta: dueño sin PIN aún", full) { Account(AccountUi(loaded = true, draft = AccountDraft(Fixtures.PERSON_LONG, hasPin = false, pin = "43210", confirm = "43210"))) },
                GuardCase("Mi cuenta: dueño sin PIN aún (teclado)", kb) { Account(AccountUi(loaded = true, draft = AccountDraft(Fixtures.PERSON_LONG, hasPin = false))) },
                // Eliminar la cuenta (Google Play): dueño con negocio (primero eliminarlo), dueño sin negocios, persona del equipo, la hoja de confirmar.
                GuardCase("Mi cuenta: dueño con negocio (eliminar)", full) { Account(AccountUi(loaded = true, draft = AccountDraft(Fixtures.PERSON_LONG), hasGoogle = true, ownedBusinesses = listOf(Fixtures.BUSINESS_120, "Otra tienda")), deleteBusiness = true) },
                GuardCase("Mi cuenta: sin negocios, eliminar cuenta", full) { Account(AccountUi(loaded = true, draft = AccountDraft("Ana"), hasGoogle = true, ownedBusinesses = emptyList())) },
                GuardCase("Mi cuenta: persona del equipo", full) { Account(AccountUi(loaded = true, draft = AccountDraft("Kevin"))) },
                GuardCase("Mi cuenta: confirmar eliminación", full) { Account(AccountUi(loaded = true, draft = AccountDraft("Ana"), hasGoogle = true, ownedBusinesses = emptyList(), deleteOpen = true, deleteTyped = "ELIM", deleteError = ErrorMessage(R.string.account_delete_offline))) },
                GuardCase("Mi cuenta: confirmar eliminación (teclado)", kb) { Account(AccountUi(loaded = true, draft = AccountDraft("Ana"), hasGoogle = true, ownedBusinesses = emptyList(), deleteOpen = true, deleteTyped = "ELIMINAR")) },
                GuardCase("Mi cuenta: guardando", full) { Account(AccountUi(loaded = true, draft = filled.copy(confirm = "56789"), saving = true, error = ErrorMessage(R.string.error_offline))) },
            ),
        )
        runner.assertClean()
    }
}

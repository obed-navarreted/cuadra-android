package com.cuadra.caja.ui.guard

import androidx.compose.ui.test.junit4.createEmptyComposeRule
import com.cuadra.caja.core.i18n.AppLanguage
import com.cuadra.caja.ui.AuthUi
import com.cuadra.caja.ui.ErrorMessage
import com.cuadra.caja.ui.businessSwitchBlocked
import com.cuadra.caja.ui.PinUi
import com.cuadra.caja.R
import com.cuadra.caja.domain.MemberLoginForm
import com.cuadra.caja.domain.NewPinEntry
import com.cuadra.caja.ui.ChangePinActions
import com.cuadra.caja.ui.ChangePinUi
import com.cuadra.caja.ui.MemberLoginActions
import com.cuadra.caja.ui.MemberLoginUi
import com.cuadra.caja.ui.screens.ChangePinContent
import com.cuadra.caja.ui.screens.MemberLoginContent
import com.cuadra.caja.ui.screens.LoginScreen
import com.cuadra.caja.ui.screens.OnboardingScreen
import com.cuadra.caja.ui.screens.PinScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h900dp-xhdpi", application = android.app.Application::class)
class GuardAuthTest {
    @get:Rule val rule = createEmptyComposeRule()

    private val kb = GuardMatrix.KEYBOARD
    private val full = GuardMatrix.FULL

    @Test fun authScreens() {
        val runner = GuardRunner(rule, "auth")
        val actions = object : MemberLoginActions {}
        val filled = MemberLoginForm("13085", "123")
        val complete = MemberLoginForm("13085", "12345")
        runner.run(
            listOf(
                // Los dos caminos: Google (dueño) y código del negocio (equipo).
                GuardCase("Login", full) { LoginScreen(AppLanguage.AUTOMATIC, {}, AuthUi(), {}, {}) },
                GuardCase("Login con error largo", full) { LoginScreen(AppLanguage.SPANISH, {}, AuthUi(busy = true, errorRes = ErrorMessage(R.string.auth_google_failed, detail = Fixtures.NAME_120)), {}, {}) },
                // Entrar con el código del negocio: todos los estados, con y sin teclado del sistema.
                GuardCase("Código del negocio: vacío", full) { MemberLoginContent(MemberLoginUi(), actions, {}) },
                GuardCase("Código del negocio: vacío (teclado)", kb) { MemberLoginContent(MemberLoginUi(), actions, {}) },
                GuardCase("Código del negocio: a medias", full) { MemberLoginContent(MemberLoginUi(filled), actions, {}) },
                GuardCase("Código del negocio: a medias (teclado)", kb) { MemberLoginContent(MemberLoginUi(filled), actions, {}) },
                GuardCase("Código del negocio: entrando", full) { MemberLoginContent(MemberLoginUi(complete, busy = true), actions, {}) },
                GuardCase("Código del negocio: datos incorrectos", full) { MemberLoginContent(MemberLoginUi(complete.copy(pin = ""), error = ErrorMessage(R.string.login_err_INVALID_CREDENTIALS)), actions, {}) },
                GuardCase("Código del negocio: bloqueado 15 min (teclado)", kb) { MemberLoginContent(MemberLoginUi(complete.copy(pin = ""), error = ErrorMessage(R.string.login_err_LOCKED)), actions, {}) },
                GuardCase("Código del negocio: el dueño entra con Google", full) { MemberLoginContent(MemberLoginUi(complete.copy(pin = ""), error = ErrorMessage(R.string.login_err_OWNER_USES_GOOGLE)), actions, {}) },
                GuardCase("Código del negocio: límite de teléfonos", full) { MemberLoginContent(MemberLoginUi(complete.copy(pin = ""), error = ErrorMessage(R.string.login_err_PLAN_LIMIT_DEVICES)), actions, {}) },
                GuardCase("Código del negocio: demasiados intentos", full) { MemberLoginContent(MemberLoginUi(complete.copy(pin = ""), error = ErrorMessage(R.string.login_err_RATE_LIMITED)), actions, {}) },
                GuardCase("Código del negocio: sin internet", full) { MemberLoginContent(MemberLoginUi(complete.copy(pin = ""), error = ErrorMessage(R.string.login_err_offline)), actions, {}) },
                // Cambiar de negocio con operaciones sin enviar del anterior: se bloquea con el nombre del negocio y cuántas quedan.
                GuardCase("Código del negocio: otro negocio con pendientes", kb) { MemberLoginContent(MemberLoginUi(complete.copy(pin = ""), error = com.cuadra.caja.data.repo.BusinessSwitchBlocked(12_345, Fixtures.BUSINESS_120).businessSwitchBlocked()), actions, {}) },
                // Elige tu PIN nuevo.
                GuardCase("PIN nuevo: primero", full) { ChangePinContent(ChangePinUi(NewPinEntry("12")), object : ChangePinActions {}, {}) },
                GuardCase("PIN nuevo: repetir", full) { ChangePinContent(ChangePinUi(NewPinEntry("12345", "12")), object : ChangePinActions {}, {}) },
                GuardCase("PIN nuevo: no coinciden y error de red", full) { ChangePinContent(ChangePinUi(NewPinEntry(mismatch = true), error = ErrorMessage(R.string.error_offline)), object : ChangePinActions {}, {}) },
                GuardCase("PIN nuevo: guardando", full) { ChangePinContent(ChangePinUi(NewPinEntry("12345", "12345"), busy = true), object : ChangePinActions {}, {}) },
                // «Escribe tu PIN» (principal) + la lista de nombres (secundaria).
                GuardCase("PIN: elegir persona", full) { PinScreen(Fixtures.members, PinUi(), true, { _, _ -> }, {}, {}, {}, {}, {}) },
                GuardCase("PIN: escribiendo tu PIN, incorrecto", full) { PinScreen(Fixtures.members, PinUi(pin = "12", wrong = true), false, { _, _ -> }, {}, {}, {}, {}, {}) },
                GuardCase("PIN: tu PIN, comprobando", full) { PinScreen(Fixtures.members, PinUi(pin = "12345", busy = true), false, { _, _ -> }, {}, {}, {}, {}, {}) },
                GuardCase("PIN: tu PIN, espera por intentos", full) { PinScreen(Fixtures.members, PinUi(lockedMillis = 45_000), false, { _, _ -> }, {}, {}, {}, {}, {}) },
                GuardCase("PIN: nadie tiene PIN todavía", full) { PinScreen(Fixtures.members.map { it.copy(pinSet = false, pinHash = null) }, PinUi(), true, { _, _ -> }, {}, {}, {}, {}, {}) },
                GuardCase("PIN: creando, PIN ocupado", full) { PinScreen(Fixtures.members, PinUi(selected = Fixtures.members[2], creating = true, errorRes = ErrorMessage(R.string.team_err_PIN_TAKEN)), true, { _, _ -> }, {}, {}, {}, {}, {}) },
                GuardCase("PIN: escribiendo", full) { PinScreen(Fixtures.members, PinUi(selected = Fixtures.members[0], pin = "1234", wrong = true, lockedMillis = 45_000), false, { _, _ -> }, {}, {}, {}, {}, {}) },
                GuardCase("PIN: creando", full) { PinScreen(Fixtures.members, PinUi(selected = Fixtures.members[2], pin = "12", creating = true), true, { _, _ -> }, {}, {}, {}, {}, {}) },
                GuardCase("PIN: creando, completo", full) { PinScreen(Fixtures.members, PinUi(selected = Fixtures.members[2], pin = "12345", creating = true), true, { _, _ -> }, {}, {}, {}, {}, {}) },
                GuardCase("Negocio nuevo", full) { OnboardingScreen(Fixtures.me, AuthUi(me = Fixtures.me, errorRes = ErrorMessage(R.string.error_offline)), { _, _ -> }, {}, {}, phoneZone = "Europe/Madrid") },
                GuardCase("Elegir negocio: el anterior tiene pendientes", full) { OnboardingScreen(Fixtures.me, AuthUi(me = Fixtures.me, errorRes = com.cuadra.caja.data.repo.BusinessSwitchBlocked(3, null).businessSwitchBlocked()), { _, _ -> }, {}, {}) },
                GuardCase("Negocio nuevo, sin negocios (cargando)", full) { OnboardingScreen(null, AuthUi(), { _, _ -> }, {}, {}) },
                // País (sugiere moneda y zona) con la lista abierta, con y sin teclado; admin de plataforma sin negocio: «Ir a la consola».
                GuardCase("Negocio nuevo: eligiendo país", full) { OnboardingScreen(null, AuthUi(), { _, _ -> }, {}, {}, phoneZone = "America/Tegucigalpa", initialPickCountry = true) },
                GuardCase("Negocio nuevo: eligiendo país (teclado)", kb) { OnboardingScreen(null, AuthUi(), { _, _ -> }, {}, {}, phoneZone = "America/Tegucigalpa", initialPickCountry = true) },
                GuardCase("Negocio nuevo: admin de plataforma", full) {
                    OnboardingScreen(Fixtures.me.copy(platformAdmin = true, businesses = emptyList()), AuthUi(me = Fixtures.me.copy(platformAdmin = true, businesses = emptyList())), { _, _ -> }, {}, {}, onPlatformConsole = {})
                },
                GuardCase("Login con acceso de plataforma", full) { LoginScreen(AppLanguage.ENGLISH, {}, AuthUi(), {}, {}, onPlatformAccess = {}) },
            ),
        )
        runner.assertClean()
    }
}

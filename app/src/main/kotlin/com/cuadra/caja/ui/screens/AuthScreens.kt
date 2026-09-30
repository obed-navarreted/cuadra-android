package com.cuadra.caja.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.imePadding
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import com.cuadra.caja.ui.common.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.cuadra.caja.R
import com.cuadra.caja.core.i18n.AppLanguage
import com.cuadra.caja.data.remote.MeDto
import com.cuadra.caja.ui.ErrorMessage
import com.cuadra.caja.ui.asString
import com.cuadra.caja.ui.AuthUi
import com.cuadra.caja.domain.NewPinEntry
import com.cuadra.caja.domain.PinRules
import com.cuadra.caja.ui.ChangePinActions
import com.cuadra.caja.ui.ChangePinUi
import com.cuadra.caja.ui.ChangePinViewModel
import com.cuadra.caja.ui.MemberLoginActions
import com.cuadra.caja.ui.MemberLoginUi
import com.cuadra.caja.ui.MemberLoginViewModel
import com.cuadra.caja.ui.common.DigitsCodeField
import com.cuadra.caja.ui.common.Keypad
import com.cuadra.caja.ui.common.ScreenFrame
import com.cuadra.caja.ui.common.SectionLabel
import com.cuadra.caja.ui.common.TitleBar
import com.cuadra.caja.ui.common.hint
import com.cuadra.caja.ui.common.ButtonKind
import com.cuadra.caja.ui.common.CuadraButton
import com.cuadra.caja.ui.common.CuadraCard
import com.cuadra.caja.ui.common.LinkAction
import com.cuadra.caja.ui.common.SplitRow
import com.cuadra.caja.ui.common.ChipFlow
import com.cuadra.caja.ui.common.CuadraChip
import com.cuadra.caja.ui.common.VoiceTextField
import com.cuadra.caja.ui.theme.CuadraColors

@Composable
fun ErrorText(msg: ErrorMessage?) {
    if (msg != null) Text(msg.asString(), color = CuadraColors.Red, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
}

@Composable
fun LanguageChips(language: AppLanguage, onChange: (AppLanguage) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.language_label), style = MaterialTheme.typography.labelLarge)
        @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CuadraChip(stringResource(R.string.language_automatic), language == AppLanguage.AUTOMATIC, { onChange(AppLanguage.AUTOMATIC) })
            CuadraChip(stringResource(R.string.language_es), language == AppLanguage.SPANISH, { onChange(AppLanguage.SPANISH) })
            CuadraChip(stringResource(R.string.language_en), language == AppLanguage.ENGLISH, { onChange(AppLanguage.ENGLISH) })
        }
    }
}

/** Entrada con DOS caminos claros (ADR 0012): el dueño con Google, o cualquier persona del equipo con el código del negocio + su usuario + su PIN. */
@Composable
fun LoginScreen(
    language: AppLanguage, onLanguageChange: (AppLanguage) -> Unit, ui: AuthUi, onGoogle: () -> Unit, onMemberLogin: () -> Unit,
    /** «Acceso de plataforma»: abre la consola (y su entrada con usuario y contraseña) en el panel web. */
    onPlatformAccess: (() -> Unit)? = null,
) {
    Column(
        Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 28.dp, vertical = 48.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.displayLarge)
        Text(stringResource(R.string.app_tagline), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        CuadraCard {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.login_owner_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.auth_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                CuadraButton(
                    if (ui.busy) stringResource(R.string.auth_signing_in) else stringResource(R.string.auth_continue_with_google),
                    onGoogle, Modifier.fillMaxWidth(), kind = ButtonKind.PRIMARY, enabled = !ui.busy,
                )
                ErrorText(ui.errorRes)
            }
        }
        CuadraCard {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.login_team_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.login_team_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                CuadraButton(stringResource(R.string.login_enter_with_code), onMemberLogin, Modifier.fillMaxWidth(), enabled = !ui.busy)
            }
        }
        LanguageChips(language, onLanguageChange)
        // Discreto: solo quien administra la plataforma lo necesita (la consola vive en el panel web; aquí no hay consola).
        onPlatformAccess?.let { LinkAction(stringResource(R.string.login_platform_access), it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium) }
    }
}

/** Los puntos del PIN: siempre 5, llenos según lo escrito. */
@Composable
fun PinDots(filled: Int, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        repeat(PinRules.LENGTH) { i ->
            Box(Modifier.padding(horizontal = 6.dp).size(16.dp).background(if (i < filled) CuadraColors.Ink else CuadraColors.Line, CircleShape))
        }
    }
}

/**
 * «Entrar con el código del negocio»: código de 5 números, usuario y PIN de 5 números con el teclado de la app. Al escribir el 5.º número se entra solo
 * (si el código y el usuario ya están). El código, el usuario y el PIN no se dictan por voz.
 */
@Composable
fun MemberLoginContent(ui: MemberLoginUi, actions: MemberLoginActions, onBack: () -> Unit) {
    val f = ui.form
    ScreenFrame(
        Modifier.fillMaxSize().systemBarsPadding().imePadding().padding(horizontal = 24.dp),
        header = {
            TitleBar(Modifier.padding(top = 16.dp), end = { CuadraButton(stringResource(R.string.back), onBack, height = 48, enabled = !ui.busy) }) {
                Text(stringResource(R.string.login_code_title), style = MaterialTheme.typography.headlineMedium)
            }
        },
        footer = {
            CuadraButton(
                stringResource(if (ui.busy) R.string.auth_signing_in else R.string.pin_submit), actions::submit,
                Modifier.fillMaxWidth().padding(bottom = 8.dp), kind = ButtonKind.DARK, height = 48, enabled = f.ready && !ui.busy,
            )
        },
    ) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.login_code_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            DigitsCodeField(f.code, actions::setCode, stringResource(R.string.login_code_label), enabled = !ui.busy, supporting = stringResource(R.string.login_code_help))
            // sin voz: usuario (el nombre con el que el dueño te dio de alta; se escribe tal cual)
            OutlinedTextField(
                f.username, actions::setUsername, Modifier.fillMaxWidth(), enabled = !ui.busy, singleLine = true, label = hint { Text(stringResource(R.string.login_user_label)) },
                supportingText = { Text(stringResource(R.string.login_user_help)) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
            )
            SectionLabel(stringResource(R.string.login_pin_label))
            PinDots(f.pin.length)
            ErrorText(ui.error)
            Keypad(actions::digit, actions::backspace, Modifier.fillMaxWidth())
        }
    }
}

@Composable
fun MemberLoginScreen(vm: MemberLoginViewModel, onBack: () -> Unit) {
    val ui by vm.ui.collectAsState()
    MemberLoginContent(ui, vm, onBack)
}

/** «Elige tu PIN nuevo»: 5 números, dos veces, con el teclado de la app (quien entró con un PIN que el dueño le puso). */
@Composable
fun ChangePinContent(ui: ChangePinUi, actions: ChangePinActions, onSignOut: () -> Unit) {
    val e = ui.entry
    Column(
        Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.newpin_title), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.newpin_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            stringResource(if (e.stage == NewPinEntry.Stage.FIRST) R.string.newpin_first else R.string.newpin_repeat),
            style = MaterialTheme.typography.titleMedium,
        )
        PinDots(e.shown.length)
        if (e.mismatch) Text(stringResource(R.string.newpin_mismatch), color = CuadraColors.Red, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        ErrorText(ui.error)
        Keypad(actions::digit, actions::backspace, Modifier.fillMaxWidth())
        CuadraButton(stringResource(R.string.pin_sign_out), onSignOut, Modifier.fillMaxWidth(), enabled = !ui.busy)
    }
}

@Composable
fun ChangePinScreen(vm: ChangePinViewModel) {
    val ui by vm.ui.collectAsState()
    ChangePinContent(ui, vm, vm::signOut)
}

/**
 * Primer negocio: el nombre (lo único obligatorio) y el país, que sugiere la moneda y la zona horaria (se pueden cambiar aquí; la moneda queda fija con la
 * primera venta). O elegir uno de los negocios que ya tiene. Un administrador de la PLATAFORMA sin negocio no queda atrapado: puede ir a la consola.
 */
@Composable
fun OnboardingScreen(
    me: MeDto?, ui: AuthUi, onCreate: (String, com.cuadra.caja.domain.BusinessOrigin) -> Unit, onUse: (String) -> Unit, onSignOut: () -> Unit,
    phoneZone: String? = null, onPlatformConsole: (() -> Unit)? = null, initialPickCountry: Boolean = false,
) {
    var name by rememberSaveable { mutableStateOf("") }
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val countries = ui.countries
    val suggested = remember(countries) { com.cuadra.caja.domain.CountryChoice.suggested(countries, locale.country, phoneZone) }
    var countryCode by rememberSaveable(suggested.code) { mutableStateOf(suggested.code) }
    val country = countries.firstOrNull { it.code == countryCode } ?: suggested
    var currency by rememberSaveable(country.code) { mutableStateOf(country.currency) }
    var zone by rememberSaveable(country.code) { mutableStateOf(country.timezone) }
    var picking by rememberSaveable { mutableStateOf(initialPickCountry) }
    Column(
        Modifier.fillMaxSize().systemBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.onboarding_title), style = MaterialTheme.typography.headlineMedium)
        if (me?.platformAdmin == true && onPlatformConsole != null) {
            CuadraCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.onboarding_platform_title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.onboarding_platform_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    CuadraButton(stringResource(R.string.onboarding_go_console), onPlatformConsole, Modifier.fillMaxWidth(), kind = ButtonKind.DARK)
                }
            }
        }
        VoiceTextField(
            name, { name = it.take(120) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(stringResource(R.string.onboarding_name_label)) },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
        )
        CuadraCard {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SplitRow(end = { LinkAction(stringResource(R.string.onboarding_country_change), { picking = !picking }) }) {
                    Column {
                        Text(stringResource(R.string.onboarding_country), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(com.cuadra.caja.domain.CountryChoice.name(country.code, locale), fontWeight = FontWeight.ExtraBold)
                    }
                }
                if (picking) ChipFlow {
                    countries.sortedBy { com.cuadra.caja.domain.CountryChoice.name(it.code, locale) }.forEach { c ->
                        CuadraChip(com.cuadra.caja.domain.CountryChoice.name(c.code, locale), c.code == country.code, { countryCode = c.code; picking = false })
                    }
                }
                Text(stringResource(R.string.onboarding_country_help), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                SectionLabel(stringResource(R.string.onboarding_currency))
                ChipFlow { com.cuadra.caja.domain.CountryChoice.currencies(country, currency).forEach { cur -> CuadraChip(cur, cur == currency, { currency = cur }) } }
                Text(stringResource(R.string.onboarding_currency_help), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                SectionLabel(stringResource(R.string.onboarding_timezone))
                ChipFlow {
                    com.cuadra.caja.domain.CountryChoice.timezones(country, phoneZone).forEach { z ->
                        CuadraChip(if (z == phoneZone && z != country.timezone) stringResource(R.string.onboarding_phone_zone, z) else z, z == zone, { zone = z })
                    }
                }
            }
        }
        CuadraButton(
            stringResource(R.string.onboarding_start), { onCreate(name, com.cuadra.caja.domain.BusinessOrigin(country.code, currency, zone)) }, Modifier.fillMaxWidth(),
            kind = ButtonKind.PRIMARY, enabled = name.isNotBlank() && !ui.busy,
        )
        ErrorText(ui.errorRes)

        val businesses = me?.businesses.orEmpty()
        if (businesses.isNotEmpty()) {
            Text(stringResource(R.string.onboarding_or), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.onboarding_your_businesses), style = MaterialTheme.typography.labelLarge)
            businesses.forEach { b ->
                CuadraCard {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(b.businessName, fontWeight = FontWeight.ExtraBold)
                        Text(stringResource(roleRes(b.role)), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (b.role == "OWNER" || b.role == "ADMIN") {
                            CuadraButton(stringResource(R.string.onboarding_use_here), { onUse(b.businessId) }, Modifier.fillMaxWidth(), enabled = !ui.busy)
                        } else {
                            Text(stringResource(R.string.onboarding_cannot_link), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
        CuadraButton(stringResource(R.string.more_sign_out), onSignOut, Modifier.fillMaxWidth())
    }
}

fun roleRes(role: String): Int = when (role) {
    "OWNER" -> R.string.role_OWNER
    "ADMIN" -> R.string.role_ADMIN
    else -> R.string.role_CASHIER
}

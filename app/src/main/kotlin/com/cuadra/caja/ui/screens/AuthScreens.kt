package com.cuadra.caja.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import com.cuadra.caja.ui.common.ButtonKind
import com.cuadra.caja.ui.common.CuadraButton
import com.cuadra.caja.ui.common.CuadraCard
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
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CuadraChip(stringResource(R.string.language_automatic), language == AppLanguage.AUTOMATIC, { onChange(AppLanguage.AUTOMATIC) })
            CuadraChip(stringResource(R.string.language_es), language == AppLanguage.SPANISH, { onChange(AppLanguage.SPANISH) })
            CuadraChip(stringResource(R.string.language_en), language == AppLanguage.ENGLISH, { onChange(AppLanguage.ENGLISH) })
        }
    }
}

/** Entrada: Google para el dueño; un teléfono de la caja se vincula con un código. */
@Composable
fun LoginScreen(
    language: AppLanguage, onLanguageChange: (AppLanguage) -> Unit, ui: AuthUi, onGoogle: () -> Unit, onLinkWithCode: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 28.dp, vertical = 48.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.displayLarge)
        Text(stringResource(R.string.app_tagline), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        CuadraButton(
            if (ui.busy) stringResource(R.string.auth_signing_in) else stringResource(R.string.auth_continue_with_google),
            onGoogle, Modifier.fillMaxWidth(), enabled = !ui.busy,
        )
        ErrorText(ui.errorRes)
        Text(stringResource(R.string.auth_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        CuadraButton(stringResource(R.string.auth_link_with_code), onLinkWithCode, Modifier.fillMaxWidth())
        LanguageChips(language, onLanguageChange)
    }
}

/** Primer negocio con un solo campo obligatorio (el nombre), o elegir uno de los que ya tiene. */
@Composable
fun OnboardingScreen(me: MeDto?, ui: AuthUi, onCreate: (String) -> Unit, onUse: (String) -> Unit, onLinkWithCode: () -> Unit, onSignOut: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    Column(
        Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.onboarding_title), style = MaterialTheme.typography.headlineMedium)
        VoiceTextField(
            name, { name = it.take(120) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(stringResource(R.string.onboarding_name_label)) },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
        )
        CuadraButton(stringResource(R.string.onboarding_start), { onCreate(name) }, Modifier.fillMaxWidth(), kind = ButtonKind.PRIMARY, enabled = name.isNotBlank() && !ui.busy)
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
        CuadraButton(stringResource(R.string.auth_link_with_code), onLinkWithCode, Modifier.fillMaxWidth())
        CuadraButton(stringResource(R.string.more_sign_out), onSignOut, Modifier.fillMaxWidth())
    }
}

fun roleRes(role: String): Int = when (role) {
    "OWNER" -> R.string.role_OWNER
    "ADMIN" -> R.string.role_ADMIN
    else -> R.string.role_CASHIER
}

/** El teléfono muestra un código; un dueño o admin lo escribe en Equipo → Teléfonos y este teléfono queda vinculado. */
@Composable
fun LinkCodeScreen(ui: AuthUi, onNewCode: () -> Unit, onBack: () -> Unit) {
    Column(
        Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(stringResource(R.string.link_title), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.link_instructions), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box(Modifier.fillMaxWidth(), contentAlignment = androidx.compose.ui.Alignment.Center) {
            Text(
                ui.link?.code?.chunked(3)?.joinToString(" ") ?: "···  ···", style = MaterialTheme.typography.displayLarge,
                color = if (ui.linkExpired) CuadraColors.Muted else CuadraColors.Ink,
            )
        }
        Text(
            stringResource(if (ui.linkExpired) R.string.link_expired else R.string.link_waiting),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ErrorText(ui.errorRes)
        if (ui.linkExpired || ui.errorRes != null) CuadraButton(stringResource(R.string.link_new_code), onNewCode, Modifier.fillMaxWidth(), kind = ButtonKind.PRIMARY)
        CuadraButton(stringResource(R.string.back), onBack, Modifier.fillMaxWidth())
    }
}

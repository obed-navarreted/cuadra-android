package com.cuadra.caja.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cuadra.caja.R
import com.cuadra.caja.domain.TeamRules
import com.cuadra.caja.ui.AccountActions
import com.cuadra.caja.ui.AccountUi
import com.cuadra.caja.ui.AccountViewModel
import com.cuadra.caja.ui.asString
import com.cuadra.caja.ui.common.ButtonKind
import com.cuadra.caja.ui.common.ButtonRow
import com.cuadra.caja.ui.common.CuadraButton
import com.cuadra.caja.ui.common.CuadraCard
import com.cuadra.caja.ui.common.LinkAction
import com.cuadra.caja.ui.common.Sheet
import com.cuadra.caja.ui.common.PinField
import com.cuadra.caja.ui.common.ScreenFrame
import com.cuadra.caja.ui.common.SectionLabel
import com.cuadra.caja.ui.common.Text
import com.cuadra.caja.ui.common.TitleBar
import com.cuadra.caja.ui.common.VoiceTextField
import com.cuadra.caja.ui.theme.CuadraColors

/**
 * «Mi cuenta»: cualquier persona cambia su propio nombre y PIN (cajeros incluidos). Al final, eliminar la cuenta (lo exige Google Play): el dueño con Google
 * la elimina (antes, sus negocios); una persona del equipo no tiene cuenta propia y se le explica que la da de baja el dueño o un admin.
 */
@Composable
fun AccountScreen(vm: AccountViewModel, onDeleteBusiness: (() -> Unit)? = null, onDeleted: () -> Unit = {}, infoUrl: String? = null, onInfo: (() -> Unit)? = null, onBack: () -> Unit) {
    LaunchedEffect(Unit) { vm.load() }
    val ui by vm.ui.collectAsState()
    LaunchedEffect(ui.accountDeleted) { if (ui.accountDeleted) onDeleted() }
    AccountContent(ui, vm, onBack, onDeleteBusiness, onInfo, infoUrl)
}

/** Mi cuenta sin ViewModel (estado + acciones): es lo que dibuja la guardia de diseño. */
@Composable
fun AccountContent(ui: AccountUi, actions: AccountActions, onBack: () -> Unit, onDeleteBusiness: (() -> Unit)? = null, onInfo: (() -> Unit)? = null, infoUrl: String? = null) {
    val d = ui.draft
    ScreenFrame(
        Modifier.fillMaxSize().imePadding().padding(horizontal = 16.dp),
        header = {
            TitleBar(Modifier.padding(top = 16.dp), end = { CuadraButton(stringResource(R.string.back), onBack, height = 48) }) {
                Text(stringResource(R.string.account_title), style = MaterialTheme.typography.headlineMedium)
            }
        },
        footer = {
            CuadraButton(stringResource(R.string.save), actions::save, Modifier.fillMaxWidth().padding(bottom = 8.dp), kind = ButtonKind.PRIMARY, height = 48, enabled = d.ready && !ui.saving && ui.loaded)
        },
    ) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionLabel(stringResource(R.string.account_name_section))
            VoiceTextField(d.name, { actions.update(d.copy(name = it.take(TeamRules.NAME_MAX))) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(stringResource(R.string.team_name_label)) }, enabled = ui.loaded)
            SectionLabel(stringResource(R.string.account_pin_section))
            Text(stringResource(if (d.hasPin) R.string.account_pin_hint else R.string.account_pin_hint_none), style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted)
            if (d.hasPin) PinField(d.current, { actions.update(d.copy(current = it)) }, stringResource(R.string.account_pin_current), hidden = true, enabled = ui.loaded)
            PinPair(d.pin, d.confirm, { actions.update(d.copy(pin = it)) }, { actions.update(d.copy(confirm = it)) }, hidden = true)
            ErrorText(ui.error)
            ui.lockedMillis?.let { Text(stringResource(R.string.pin_locked, ((it + 999) / 1000).toInt()), color = CuadraColors.Red, fontWeight = FontWeight.Bold) }
            if (ui.saved) Text(stringResource(R.string.account_saved), color = CuadraColors.Green, fontWeight = FontWeight.Bold)
            DeleteAccountSection(ui, actions, onDeleteBusiness, onInfo, infoUrl)
        }
    }
    if (ui.deleteOpen) DeleteAccountSheet(ui, actions)
}

@Composable
private fun DeleteAccountSection(ui: AccountUi, actions: AccountActions, onDeleteBusiness: (() -> Unit)?, onInfo: (() -> Unit)?, infoUrl: String?) {
    val owned = ui.ownedBusinesses.orEmpty()
    SectionLabel(stringResource(if (ui.hasGoogle) R.string.account_delete else R.string.account_team_title))
    CuadraCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when {
                !ui.hasGoogle -> Text(stringResource(R.string.account_team_body), style = MaterialTheme.typography.bodyMedium)
                owned.isNotEmpty() -> {
                    Text(stringResource(R.string.account_owns_title), fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.account_owns_body, owned.joinToString(", ")), style = MaterialTheme.typography.bodyMedium)
                    onDeleteBusiness?.let { CuadraButton(stringResource(R.string.account_go_delete_business), it, Modifier.fillMaxWidth(), height = 48) }
                }
                else -> {
                    Text(stringResource(R.string.account_delete_body), style = MaterialTheme.typography.bodyMedium)
                    CuadraButton(stringResource(R.string.account_delete), actions::openDeleteAccount, Modifier.fillMaxWidth(), kind = ButtonKind.DANGER, height = 48, enabled = ui.ownedBusinesses != null)
                }
            }
            if (onInfo != null && infoUrl != null) LinkAction(stringResource(R.string.account_delete_info), onInfo)
        }
    }
}

/** Confirmar la eliminación escribiendo la palabra (ELIMINAR / DELETE). */
@Composable
private fun DeleteAccountSheet(ui: AccountUi, actions: AccountActions) {
    val word = stringResource(R.string.account_delete_word)
    Sheet(actions::closeDeleteAccount, actions = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ErrorText(ui.deleteError)
            ButtonRow {
                CuadraButton(stringResource(R.string.cancel), actions::closeDeleteAccount, Modifier.share(1f))
                CuadraButton(stringResource(R.string.account_delete_confirm), actions::confirmDeleteAccount, Modifier.share(1.4f), kind = ButtonKind.DANGER,
                    enabled = !ui.deleting && ui.deleteTyped.trim().equals(word, ignoreCase = true))
            }
        }
    }) {
        Text(stringResource(R.string.account_delete_title), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.account_delete_body), style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted)
        com.cuadra.caja.ui.common.CodeField(ui.deleteTyped, actions::typeDeleteWord, stringResource(R.string.account_delete_type, word), Modifier.fillMaxWidth())
    }
}

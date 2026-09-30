package com.cuadra.caja.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cuadra.caja.R
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.style.TextAlign
import com.cuadra.caja.domain.AccessCode
import com.cuadra.caja.ui.TeamActions
import com.cuadra.caja.ui.common.ButtonKind
import com.cuadra.caja.ui.common.ButtonRow
import com.cuadra.caja.ui.common.CuadraButton
import com.cuadra.caja.ui.common.CuadraCard
import com.cuadra.caja.ui.common.rememberTextSharer
import com.cuadra.caja.ui.TeamNotice
import com.cuadra.caja.ui.asString
import com.cuadra.caja.ui.common.ChipGrid
import com.cuadra.caja.ui.common.CuadraChip
import com.cuadra.caja.ui.common.LinkAction
import com.cuadra.caja.ui.common.PinField
import com.cuadra.caja.ui.common.CheckRow
import com.cuadra.caja.ui.common.Text
import com.cuadra.caja.ui.theme.CuadraColors

/** Las dos pantallas de gestión del equipo: se pasa de una a otra con estas pestañas. */
enum class TeamTab { PEOPLE, DEVICES }

@Composable
fun TeamTabs(selected: TeamTab, onSelect: (TeamTab) -> Unit) {
    ChipGrid(Modifier.fillMaxWidth()) {
        CuadraChip(stringResource(R.string.team_tab_people), selected == TeamTab.PEOPLE, { onSelect(TeamTab.PEOPLE) })
        CuadraChip(stringResource(R.string.team_tab_devices), selected == TeamTab.DEVICES, { onSelect(TeamTab.DEVICES) })
    }
}

/** Resultado de una acción (lo que salió bien, en verde; un error, en rojo), con «Entendido» para quitarlo. */
@Composable
fun NoticeLine(notice: TeamNotice, onDismiss: () -> Unit) = NoticeLine(notice.text.asString(), notice.isError, onDismiss)

@Composable
fun NoticeLine(text: String, isError: Boolean, onDismiss: () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(0.dp)) {
        Text(text, color = if (isError) CuadraColors.Red else CuadraColors.Green, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
        LinkAction(stringResource(R.string.team_dismiss), onDismiss)
    }
}

/** El PIN nuevo escrito dos veces y, si aplica, «debe cambiarlo en su primer ingreso». `hidden`: se esconden los dígitos (el PIN propio). */
@Composable
fun PinPair(
    pin: String, confirm: String, onPin: (String) -> Unit, onConfirm: (String) -> Unit, hidden: Boolean = false,
    mustChange: Boolean? = null, onMustChange: (Boolean) -> Unit = {},
) {
    val mismatch = confirm.length >= pin.length && confirm.isNotEmpty() && pin != confirm
    PinField(pin, onPin, stringResource(R.string.team_pin_new), hidden = hidden, supporting = stringResource(R.string.team_pin_hint))
    PinField(confirm, onConfirm, stringResource(R.string.team_pin_repeat), hidden = hidden, isError = mismatch, supporting = if (mismatch) stringResource(R.string.team_pin_mismatch) else null)
    if (mustChange != null) {
        CheckRow(stringResource(R.string.team_must_change), mustChange, onMustChange)
        Text(stringResource(R.string.team_must_change_help), style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted)
    }
}

internal fun memberInitials(name: String) = name.trim().split(Regex("\\s+")).take(2).joinToString("") { it.take(1).uppercase() }

@Composable
internal fun teamRoleLabel(role: String): String = stringResource(roleRes(role))

/**
 * Botones «Copiar» y «Compartir» de un texto. «Copiar» avisa «Copiado» en el propio botón unos segundos. `enabled = false` si aún no hay texto.
 */
@Composable
fun CopyShareRow(text: String, copyLabel: String, shareTitle: String, enabled: Boolean = true) {
    val sharer = rememberTextSharer()
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) { if (copied) { kotlinx.coroutines.delay(2_500); copied = false } }
    ButtonRow {
        CuadraButton(
            stringResource(if (copied) R.string.share_copied else R.string.share_copy), { sharer.copy(copyLabel, text); copied = true }, Modifier.share(1f), enabled = enabled,
        )
        CuadraButton(stringResource(R.string.share_share), { sharer.share(shareTitle, text) }, Modifier.share(1f), kind = ButtonKind.PRIMARY, enabled = enabled)
    }
}

/**
 * «Código del negocio» al inicio de Equipo (dueño y admin): el código de 5 números grande y separado, «Copiar» / «Compartir» con el mensaje listo y,
 * solo el DUEÑO, «Renovar código» y «Elegir mi propio código». Sin código conocido (aún sin conexión) se dibuja con guiones y sin acciones.
 */
@Composable
fun AccessCodeCard(business: String, code: String?, isOwner: Boolean, actions: TeamActions) {
    val known = code != null && AccessCode.isComplete(code)
    CuadraCard {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.code_title), fontWeight = FontWeight.ExtraBold)
            Text(stringResource(R.string.code_hint), style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted)
            Text(
                if (known) AccessCode.spaced(code!!) else stringResource(R.string.code_unknown), Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.displaySmall, textAlign = TextAlign.Center, color = if (known) CuadraColors.Ink else CuadraColors.Muted,
            )
            if (!known) Text(stringResource(R.string.code_unknown_note), style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted)
            CopyShareRow(
                if (known) AccessCode.shareMessage(stringResource(R.string.code_share_message), business, code!!) else "", stringResource(R.string.code_title),
                stringResource(R.string.code_title), enabled = known,
            )
            if (isOwner) {
                LinkAction(stringResource(R.string.code_renew), actions::openRenewCode)
                LinkAction(stringResource(R.string.code_choose), actions::openChooseCode)
            }
        }
    }
}

/** Lo que se le da a una persona recién creada: código + PIN (el PIN no se vuelve a mostrar; el nombre, de referencia), con «Copiar» y «Compartir». */
@Composable
fun CredentialsCard(business: String, code: String, name: String, pin: String) {
    val labelStyle = MaterialTheme.typography.labelMedium
    CuadraCard {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.cred_title), fontWeight = FontWeight.ExtraBold)
            Text(stringResource(R.string.cred_business, business), style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted, maxLines = 3, ellipsize = true)
            CredentialLine(stringResource(R.string.cred_code), if (AccessCode.isComplete(code)) AccessCode.spaced(code) else "—", labelStyle)
            CredentialLine(stringResource(R.string.cred_pin), AccessCode.spaced(pin), labelStyle)
            CredentialLine(stringResource(R.string.cred_user), name, labelStyle)
            Text(stringResource(R.string.cred_pin_once), style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Orange, fontWeight = FontWeight.Bold)
            CopyShareRow(
                AccessCode.credentialsMessage(stringResource(R.string.cred_share_message), business, code, name, pin), stringResource(R.string.cred_title),
                stringResource(R.string.cred_title), enabled = AccessCode.isComplete(code),
            )
        }
    }
}

@Composable
private fun CredentialLine(label: String, value: String, labelStyle: androidx.compose.ui.text.TextStyle) {
    Column(Modifier.fillMaxWidth()) {
        Text(label, style = labelStyle, color = CuadraColors.Muted)
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
    }
}

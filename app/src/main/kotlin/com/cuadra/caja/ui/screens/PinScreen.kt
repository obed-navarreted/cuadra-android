package com.cuadra.caja.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cuadra.caja.R
import com.cuadra.caja.data.local.MemberEntity
import com.cuadra.caja.ui.PinUi
import com.cuadra.caja.ui.common.Avatar
import com.cuadra.caja.ui.common.ButtonKind
import com.cuadra.caja.ui.common.CuadraButton
import com.cuadra.caja.ui.common.CuadraCard
import com.cuadra.caja.ui.common.Keypad
import com.cuadra.caja.ui.theme.CuadraColors

private fun initials(name: String) = name.trim().split(Regex("\\s+")).take(2).joinToString("") { it.take(1).uppercase() }

/** "¿Quién atiende?": se elige a la persona y se valida su PIN en el teléfono, aunque no haya internet. */
@Composable
fun PinScreen(
    members: List<MemberEntity>, ui: PinUi, canCreatePin: Boolean, onSelect: (MemberEntity, Boolean) -> Unit, onBack: () -> Unit,
    onDigit: (Char) -> Unit, onBackspace: () -> Unit, onSubmit: () -> Unit, onSignOut: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        val selected = ui.selected
        if (selected == null) {
            Text(stringResource(R.string.pin_who), style = MaterialTheme.typography.headlineMedium)
            if (members.isEmpty()) Text(stringResource(R.string.pin_loading), color = MaterialTheme.colorScheme.onSurfaceVariant)
            members.forEach { m ->
                CuadraCard(onClick = { onSelect(m, canCreatePin) }) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Avatar(initials(m.displayName))
                        Column(Modifier.weight(1f)) {
                            Text(m.displayName, fontWeight = FontWeight.ExtraBold)
                            Text(stringResource(roleRes(m.role)), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            CuadraButton(stringResource(R.string.pin_sign_out), onSignOut, Modifier.fillMaxWidth())
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CuadraButton("‹", onBack, Modifier.size(56.dp))
                Avatar(initials(selected.displayName), selected = true)
                Text(
                    if (ui.creating) stringResource(R.string.pin_create_title) else stringResource(R.string.pin_enter, selected.displayName),
                    style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f),
                )
            }
            if (ui.creating) Text(stringResource(R.string.pin_create_hint), color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (ui.noPin) {
                Text(stringResource(R.string.pin_none), color = CuadraColors.Red, fontWeight = FontWeight.Bold)
            } else {
                PinDots(ui.pin.length)
                if (ui.wrong) Text(stringResource(R.string.pin_wrong), color = CuadraColors.Red, fontWeight = FontWeight.Bold)
                ui.lockedMillis?.let { Text(stringResource(R.string.pin_locked, ((it + 999) / 1000).toInt()), color = CuadraColors.Red, fontWeight = FontWeight.Bold) }
                ErrorText(ui.errorRes)
                Keypad(onDigit, onBackspace, Modifier.fillMaxWidth())
                CuadraButton(
                    stringResource(if (ui.creating) R.string.save else R.string.pin_submit), onSubmit, Modifier.fillMaxWidth(), kind = ButtonKind.DARK,
                    enabled = ui.pin.length >= 4 && !ui.busy,
                )
            }
        }
    }
}

@Composable
private fun PinDots(filled: Int) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        repeat(6) { i ->
            Box(Modifier.padding(horizontal = 6.dp).size(16.dp).background(if (i < filled) CuadraColors.Ink else CuadraColors.Line, CircleShape))
        }
    }
}

package com.cuadra.caja.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cuadra.caja.R
import com.cuadra.caja.ui.common.ButtonKind
import com.cuadra.caja.ui.common.ButtonRow
import com.cuadra.caja.ui.common.CuadraButton
import com.cuadra.caja.ui.common.CuadraCard
import com.cuadra.caja.ui.common.ScreenFrame
import com.cuadra.caja.ui.common.Sheet
import com.cuadra.caja.ui.common.Text
import com.cuadra.caja.ui.theme.CuadraColors

/**
 * «Tu acceso fue desactivado»: el dueño dio de baja a la persona de este teléfono PERSONAL y el servidor lo revocó. No se vende ni se ven datos; mientras
 * tanto el teléfono sigue enviando lo que esa persona hizo antes de la baja (el servidor lo acepta). Salir borra los datos de este negocio del teléfono.
 * `pending`: operaciones aún sin enviar; `failed`: las que el servidor no aceptó (hechas después de la baja).
 */
@Composable
fun AccessDisabledScreen(businessName: String?, pending: Int, failed: Int, onRetry: () -> Unit, onExit: () -> Unit, confirmOpen: Boolean = false) {
    var confirm by rememberSaveable { mutableStateOf(confirmOpen) }
    ScreenFrame(
        Modifier.fillMaxSize().systemBarsPadding().padding(16.dp),
        footer = {
            if (pending > 0) CuadraButton(stringResource(R.string.disabled_retry), onRetry, Modifier.fillMaxWidth(), kind = ButtonKind.DARK)
            CuadraButton(stringResource(R.string.disabled_exit), { if (pending > 0) confirm = true else onExit() }, Modifier.fillMaxWidth())
        },
    ) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(stringResource(R.string.disabled_title), style = MaterialTheme.typography.headlineMedium, color = CuadraColors.Red)
            Text(
                if (businessName.isNullOrBlank()) stringResource(R.string.disabled_body_plain) else stringResource(R.string.disabled_body, businessName),
                style = MaterialTheme.typography.bodyLarge,
            )
            CuadraCard {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (pending > 0) Text(pluralStringResource(R.plurals.disabled_pending, pending, pending), color = CuadraColors.Orange, fontWeight = FontWeight.Bold)
                    else Text(stringResource(R.string.disabled_all_sent), color = CuadraColors.Green, fontWeight = FontWeight.Bold)
                    if (failed > 0) Text(pluralStringResource(R.plurals.disabled_failed, failed, failed), color = CuadraColors.Red)
                }
            }
            Text(stringResource(R.string.disabled_help), style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted)
        }
    }
    if (confirm) {
        Sheet({ confirm = false }, actions = {
            ButtonRow {
                CuadraButton(stringResource(R.string.cancel), { confirm = false }, Modifier.share(1f))
                CuadraButton(stringResource(R.string.disabled_exit_anyway), { confirm = false; onExit() }, Modifier.share(1.3f), kind = ButtonKind.DANGER)
            }
        }) {
            Text(stringResource(R.string.disabled_exit_title), style = MaterialTheme.typography.headlineMedium)
            Text(pluralStringResource(R.plurals.disabled_exit_body, pending, pending), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

/** Teléfono compartido: la persona que atendía fue dada de baja. Se avisa una vez en «¿Quién atiende?»; lo que hizo antes se envía igual. */
@Composable
fun DisabledMemberNotice(name: String, onOk: () -> Unit) {
    Sheet(onOk, actions = { CuadraButton(stringResource(R.string.disabled_ok), onOk, Modifier.fillMaxWidth(), kind = ButtonKind.DARK) }) {
        Text(stringResource(R.string.disabled_title), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.disabled_member_notice, name), style = MaterialTheme.typography.bodyLarge)
    }
}

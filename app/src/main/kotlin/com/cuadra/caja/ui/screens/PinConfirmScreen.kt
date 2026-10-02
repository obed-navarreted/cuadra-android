package com.cuadra.caja.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cuadra.caja.R
import com.cuadra.caja.ui.PinConfirmActions
import com.cuadra.caja.ui.PinConfirmUi
import com.cuadra.caja.ui.PinConfirmViewModel
import com.cuadra.caja.ui.common.CuadraButton
import com.cuadra.caja.ui.common.Keypad
import com.cuadra.caja.ui.common.ScreenFrame
import com.cuadra.caja.ui.common.Text
import com.cuadra.caja.ui.common.TitleBar
import com.cuadra.caja.ui.theme.CuadraColors

/**
 * «Confirma tu PIN» (ADR 0012, 2026-10-01): ocupa el lugar de una pantalla de administración mientras la persona activa no confirmó su PIN con el servidor en
 * este teléfono. Al confirmarse, la sesión sube a su rol y la pantalla pedida aparece sola.
 */
@Composable
fun PinConfirmScreen(vm: PinConfirmViewModel, onBack: () -> Unit) {
    val ui by vm.ui.collectAsState()
    val context = LocalContext.current
    LaunchedEffect(Unit) { vm.setOffline(!hasInternet(context)) }
    PinConfirmContent(ui, vm, onBack)
}

/** Sin ViewModel: es lo que dibuja la guardia de diseño. `forName`: confirmar el PIN de OTRA persona (desde «Requiere atención»). */
@Composable
fun PinConfirmContent(ui: PinConfirmUi, actions: PinConfirmActions, onBack: () -> Unit, forName: String? = null) {
    ScreenFrame(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        header = {
            TitleBar(Modifier.padding(top = 16.dp), end = { CuadraButton(stringResource(R.string.back), onBack, height = 48) }) {
                Text(stringResource(R.string.pinconfirm_title), style = MaterialTheme.typography.headlineMedium)
            }
        },
    ) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            PinConfirmBody(ui, actions, forName)
        }
    }
}

/** El cuerpo, para la pantalla completa y para la hoja de «Requiere atención». */
@Composable
fun ColumnScope.PinConfirmBody(ui: PinConfirmUi, actions: PinConfirmActions, forName: String? = null) {
    Text(
        if (forName != null) stringResource(R.string.pinconfirm_body_for, forName) else stringResource(R.string.pinconfirm_body),
        style = MaterialTheme.typography.bodyLarge,
    )
    if (ui.offline) Text(stringResource(R.string.pinconfirm_offline), color = CuadraColors.Orange, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
    PinDots(ui.pin.length)
    when {
        ui.busy -> Text(stringResource(R.string.pinconfirm_checking), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        ui.wrong -> Text(stringResource(R.string.pin_wrong), color = CuadraColors.Red, fontWeight = FontWeight.Bold)
        ui.failed -> Text(stringResource(R.string.pinconfirm_failed), color = CuadraColors.Red, fontWeight = FontWeight.Bold)
    }
    ui.lockedMillis?.let { Text(stringResource(R.string.pin_locked, ((it + 999) / 1000).toInt()), color = CuadraColors.Red, fontWeight = FontWeight.Bold) }
    Keypad(actions::digit, actions::backspace, Modifier.fillMaxWidth())
}

internal fun hasInternet(context: Context): Boolean {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager ?: return true
    val caps = runCatching { cm.getNetworkCapabilities(cm.activeNetwork) }.getOrNull() ?: return false
    return caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
}

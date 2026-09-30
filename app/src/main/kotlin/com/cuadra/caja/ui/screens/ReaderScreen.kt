package com.cuadra.caja.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cuadra.caja.R
import com.cuadra.caja.data.scanner.ScanSource
import com.cuadra.caja.data.scanner.SetupState
import com.cuadra.caja.ui.ReaderActions
import com.cuadra.caja.ui.ReaderUi
import com.cuadra.caja.ui.ReaderViewModel
import com.cuadra.caja.ui.common.BarcodeScannerDialog
import com.cuadra.caja.ui.common.ButtonKind
import com.cuadra.caja.ui.common.ButtonRow
import com.cuadra.caja.ui.common.CuadraButton
import com.cuadra.caja.ui.common.CuadraCard
import com.cuadra.caja.ui.common.SplitRow
import com.cuadra.caja.ui.common.Text
import com.cuadra.caja.ui.common.TitleBar
import com.cuadra.caja.ui.theme.CuadraColors

/** Más › Lector de códigos: qué trae el equipo, encender/apagar cada lector, configurar Zebra y probar una lectura. */
@Composable
fun ReaderScreen(vm: ReaderViewModel, onBack: () -> Unit) {
    val ui by vm.ui.collectAsState()
    var testCamera by remember { mutableStateOf(false) }
    // Mientras esta pantalla está a la vista, el lector tipo teclado también se lee aquí (y solo se muestra).
    DisposableEffect(vm) { val claim = vm.claim(); onDispose { claim.close() } }
    ReaderContent(ui, vm, onBack, onTestCamera = { testCamera = true })
    if (testCamera) BarcodeScannerDialog(onDismiss = { testCamera = false }, onCode = {})
}

/** La pantalla sin ViewModel (estado + acciones): es lo que dibuja la guardia de diseño. */
@Composable
fun ReaderContent(ui: ReaderUi, actions: ReaderActions, onBack: () -> Unit, onTestCamera: () -> Unit) {
    // Column con desplazamiento (no LazyColumn): son pocas tarjetas y así todas se dibujan y las revisa la guardia de diseño.
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        TitleBar(end = { CuadraButton(stringResource(R.string.back), onBack, height = 48) }) {
            Text(stringResource(R.string.reader_title), style = MaterialTheme.typography.headlineMedium)
        }
        DeviceCard(ui)
        TogglesCard(ui, actions)
        SetupCard(ui, actions)
        TestCard(ui, actions, onTestCamera)
    }
}

@Composable
private fun FactRow(label: String, value: String) {
    SplitRow(end = { Text(value, fontWeight = FontWeight.ExtraBold) }, endMaxFraction = 0.6f) { Text(label) }
}

@Composable
private fun DeviceCard(ui: ReaderUi) {
    CuadraCard {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.reader_device_title), fontWeight = FontWeight.ExtraBold)
            FactRow(stringResource(R.string.reader_datawedge), stringResource(if (ui.env.dataWedge) R.string.reader_detected else R.string.reader_not_detected))
            FactRow(stringResource(R.string.reader_camera), stringResource(if (ui.env.hasCamera) R.string.reader_yes else R.string.reader_no))
            FactRow(stringResource(R.string.reader_keyboard), stringResource(if (ui.env.hwKeyboard || ui.settings.wedgeSeen) R.string.reader_yes else R.string.reader_no))
        }
    }
}

@Composable
private fun SwitchRow(label: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(label, Modifier.weight(1f))
        Switch(on, onChange, Modifier.semantics { contentDescription = label })
    }
}

@Composable
private fun TogglesCard(ui: ReaderUi, actions: ReaderActions) {
    CuadraCard {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SwitchRow(stringResource(R.string.reader_use_reader), ui.settings.useReader, actions::setUseReader)
            Text(stringResource(R.string.reader_use_reader_hint), style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted)
            SwitchRow(stringResource(R.string.reader_use_camera), ui.settings.useCamera, actions::setUseCamera)
            SwitchRow(stringResource(R.string.reader_beep), ui.settings.beep, actions::setBeep)
        }
    }
}

@Composable
private fun SetupCard(ui: ReaderUi, actions: ReaderActions) {
    CuadraCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CuadraButton(stringResource(R.string.reader_setup_button), actions::configureZebra, Modifier.fillMaxWidth(), kind = ButtonKind.DARK, enabled = ui.env.dataWedge && ui.setup.state != SetupState.RUNNING)
            Text(stringResource(if (ui.env.dataWedge) R.string.reader_setup_hint else R.string.reader_setup_absent), style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted)
            val (text, color) = when (ui.setup.state) {
                SetupState.IDLE, SetupState.ABSENT -> null to CuadraColors.Ink
                SetupState.RUNNING -> stringResource(R.string.reader_setup_running) to CuadraColors.Ink
                SetupState.OK -> stringResource(R.string.reader_setup_ok) to CuadraColors.Green
                SetupState.FAILED -> (ui.setup.detail?.let { stringResource(R.string.reader_setup_failed_detail, it) } ?: stringResource(R.string.reader_setup_failed)) to CuadraColors.Red
                SetupState.NO_ANSWER -> stringResource(R.string.reader_setup_no_answer) to CuadraColors.Orange
            }
            if (text != null) Text(text, color = color, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun TestCard(ui: ReaderUi, actions: ReaderActions, onTestCamera: () -> Unit) {
    CuadraCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.reader_test_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
            Text(stringResource(R.string.reader_test_hint), style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted)
            val last = ui.last
            if (last == null) {
                Text(stringResource(R.string.reader_test_empty), color = CuadraColors.Muted)
            } else {
                Text(last.code, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
                FactRow(stringResource(R.string.reader_test_source), stringResource(when (last.source) {
                    ScanSource.CAMERA -> R.string.reader_source_camera
                    ScanSource.WEDGE -> R.string.reader_source_wedge
                    ScanSource.DATAWEDGE -> R.string.reader_source_datawedge
                }))
                FactRow(stringResource(R.string.reader_test_format), last.format ?: stringResource(R.string.reader_format_unknown))
                FactRow(stringResource(R.string.reader_test_check), stringResource(when (last.checksumValid) {
                    true -> R.string.reader_check_valid
                    false -> R.string.reader_check_invalid
                    null -> R.string.reader_check_na
                }))
                Text(stringResource(R.string.reader_test_count, ui.count), fontWeight = FontWeight.ExtraBold)
            }
            val canTestCamera = ui.env.hasCamera && ui.settings.useCamera
            if (canTestCamera || last != null) ButtonRow {
                if (canTestCamera) CuadraButton(stringResource(R.string.reader_test_camera), onTestCamera, Modifier.share(1f), height = 48)
                if (last != null) CuadraButton(stringResource(R.string.reader_test_clear), actions::clearTest, Modifier.share(1f), height = 48)
            }
        }
    }
}

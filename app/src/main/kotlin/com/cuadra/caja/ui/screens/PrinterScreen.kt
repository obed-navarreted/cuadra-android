package com.cuadra.caja.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.cuadra.caja.R
import com.cuadra.caja.data.printer.PrinterDevice
import com.cuadra.caja.domain.printing.ConnIssue
import com.cuadra.caja.domain.printing.ConnState
import com.cuadra.caja.domain.printing.PrintCharset
import com.cuadra.caja.domain.printing.PrintNotice
import com.cuadra.caja.domain.printing.PrinterLink
import com.cuadra.caja.domain.printing.PrinterSettings
import com.cuadra.caja.ui.PrinterActions
import com.cuadra.caja.ui.PrinterUi
import com.cuadra.caja.ui.PrinterViewModel
import com.cuadra.caja.ui.common.ButtonKind
import com.cuadra.caja.ui.common.ButtonRow
import com.cuadra.caja.ui.common.ChipFlow
import com.cuadra.caja.ui.common.CuadraButton
import com.cuadra.caja.ui.common.CuadraCard
import com.cuadra.caja.ui.common.CuadraChip
import com.cuadra.caja.ui.common.LinkAction
import com.cuadra.caja.ui.common.MonoBlock
import com.cuadra.caja.ui.common.NumberField
import com.cuadra.caja.ui.common.SwitchRow
import com.cuadra.caja.ui.common.Tag
import com.cuadra.caja.ui.common.TagRow
import com.cuadra.caja.ui.common.Text
import com.cuadra.caja.ui.common.TitleBar
import com.cuadra.caja.ui.common.VoiceTextField
import com.cuadra.caja.ui.theme.CuadraColors

/** Más › Avanzado › Impresora: activar, elegir la impresora (Bluetooth o cable), papel, encabezado y pie, vista previa y prueba. */
@Composable
fun PrinterScreen(vm: PrinterViewModel, onBack: () -> Unit) {
    val ui by vm.ui.collectAsState()
    val context = LocalContext.current
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> vm.refresh(); if (granted) vm.reconnect() }
    // Al volver de los ajustes de Android (permiso, emparejar) se vuelven a leer los permisos y la lista.
    LifecycleResumeEffect(vm) { vm.refresh(); onPauseOrDispose {} }
    PrinterContent(
        ui, vm, onBack,
        onGrant = { if (Build.VERSION.SDK_INT >= 31) ask.launch(Manifest.permission.BLUETOOTH_CONNECT) },
        onOpenBluetooth = { runCatching { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) } },
        onOpenApp = { runCatching { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))) } },
    )
}

/** La pantalla sin ViewModel (estado + acciones): es lo que dibuja la guardia de diseño. */
@Composable
fun PrinterContent(ui: PrinterUi, actions: PrinterActions, onBack: () -> Unit, onGrant: () -> Unit, onOpenBluetooth: () -> Unit, onOpenApp: () -> Unit) {
    val s = ui.settings
    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        TitleBar(end = { CuadraButton(stringResource(R.string.back), onBack, height = 48) }) {
            Text(stringResource(R.string.printer_title), style = MaterialTheme.typography.headlineMedium)
        }
        CuadraCard {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SwitchRow(stringResource(R.string.printer_switch), s.enabled, actions::setEnabled, help = stringResource(R.string.printer_switch_help))
                TagRow { Tag(stringResource(R.string.more_optional), CuadraColors.Ink, CuadraColors.Soft) }
                Text(stringResource(R.string.printer_nofiscal), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            }
        }
        if (s.enabled) {
            StatusCard(ui, actions)
            ConnectionCard(ui, actions, onGrant, onOpenBluetooth, onOpenApp)
            PaperCard(s, actions)
            HeaderCard(s, actions)
            PreviewCard(ui)
            TestCard(ui, actions)
        }
    }
}

@Composable
private fun StatusCard(ui: PrinterUi, actions: PrinterActions) {
    val (label, color, icon) = when (ui.state) {
        ConnState.CONNECTED -> Triple(R.string.printer_status_connected, CuadraColors.Green, R.drawable.ic_printer)
        ConnState.CONNECTING -> Triple(R.string.printer_status_connecting, CuadraColors.Muted, R.drawable.ic_printer)
        ConnState.ERROR -> Triple(R.string.printer_status_error, CuadraColors.Orange, R.drawable.ic_printer_off)
        ConnState.DISCONNECTED -> Triple(R.string.printer_status_disconnected, CuadraColors.Orange, R.drawable.ic_printer_off)
    }
    CuadraCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.printer_status_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(painterResource(icon), contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
                Text(stringResource(label), Modifier.weight(1f), color = color, fontWeight = FontWeight.ExtraBold)
            }
            if (ui.state != ConnState.CONNECTED) issueText(ui.issue)?.let { Text(stringResource(it), style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted) }
            CuadraButton(stringResource(R.string.printer_reconnect), actions::reconnect, Modifier.fillMaxWidth(), height = 48, enabled = ui.state != ConnState.CONNECTING)
            Text(stringResource(R.string.printer_auto_hint), style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted)
        }
    }
}

private fun issueText(issue: ConnIssue): Int? = when (issue) {
    ConnIssue.NONE -> null
    ConnIssue.NO_DEVICE -> R.string.printer_issue_no_device
    ConnIssue.BLUETOOTH_OFF -> R.string.printer_issue_bt_off
    ConnIssue.NO_PERMISSION -> R.string.printer_issue_permission
    ConnIssue.USB_PERMISSION -> R.string.printer_issue_usb_permission
    ConnIssue.NOT_FOUND -> R.string.printer_issue_not_found
    ConnIssue.FAILED -> R.string.printer_issue_failed
}

@Composable
private fun ConnectionCard(ui: PrinterUi, actions: PrinterActions, onGrant: () -> Unit, onOpenBluetooth: () -> Unit, onOpenApp: () -> Unit) {
    val s = ui.settings
    CuadraCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.printer_conn_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
            ChipFlow {
                CuadraChip(stringResource(R.string.printer_link_bt), s.link == PrinterLink.BLUETOOTH, { actions.setLink(PrinterLink.BLUETOOTH) })
                CuadraChip(stringResource(R.string.printer_link_usb), s.link == PrinterLink.USB, { actions.setLink(PrinterLink.USB) })
            }
            if (s.link == PrinterLink.BLUETOOTH) {
                when {
                    !ui.hasBluetooth -> Text(stringResource(R.string.printer_bt_unsupported), color = CuadraColors.Orange, fontWeight = FontWeight.Bold)
                    !ui.btPermission -> {
                        Text(stringResource(R.string.printer_perm_title), fontWeight = FontWeight.ExtraBold)
                        Text(stringResource(R.string.printer_perm_why), style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted)
                        ButtonRow {
                            CuadraButton(stringResource(R.string.printer_perm_grant), onGrant, Modifier.share(1f), kind = ButtonKind.PRIMARY, height = 48)
                            CuadraButton(stringResource(R.string.printer_perm_settings), onOpenApp, Modifier.share(1f), height = 48)
                        }
                    }
                    ui.bonded.isEmpty() -> {
                        if (!ui.btOn) Text(stringResource(R.string.printer_issue_bt_off), color = CuadraColors.Orange, fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.printer_none_bt), style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted)
                        CuadraButton(stringResource(R.string.printer_open_bt_settings), onOpenBluetooth, Modifier.fillMaxWidth(), height = 48)
                    }
                    else -> {
                        if (!ui.btOn) Text(stringResource(R.string.printer_issue_bt_off), color = CuadraColors.Orange, fontWeight = FontWeight.Bold)
                        Devices(ui.bonded, s, actions)
                        LinkAction(stringResource(R.string.printer_open_bt_settings), onOpenBluetooth)
                    }
                }
            } else {
                when {
                    !ui.hasUsb -> Text(stringResource(R.string.printer_usb_unsupported), color = CuadraColors.Orange, fontWeight = FontWeight.Bold)
                    ui.usb.isEmpty() -> Text(stringResource(R.string.printer_none_usb), style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted)
                    else -> {
                        Devices(ui.usb, s, actions)
                        if (!s.hasDevice) Text(stringResource(R.string.printer_usb_auto), style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted)
                    }
                }
            }
            s.deviceName?.let { Text(stringResource(R.string.printer_chosen, it), fontWeight = FontWeight.Bold, maxLines = 3, ellipsize = true) }
        }
    }
}

@Composable
private fun Devices(devices: List<PrinterDevice>, s: PrinterSettings, actions: PrinterActions) {
    Text(stringResource(R.string.printer_pick), style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        devices.forEach { d -> DeviceRow(d, s.deviceKey == d.key) { actions.pick(d) } }
    }
}

/** Un equipo de la lista: el nombre (puede ser larguísimo) en hasta 3 líneas, marcado si es una impresora; el elegido se ve oscuro. */
@Composable
private fun DeviceRow(d: PrinterDevice, selected: Boolean, onClick: () -> Unit) {
    CuadraCard(Modifier.fillMaxWidth(), onClick = onClick, color = if (selected) CuadraColors.Ink else CuadraColors.Surface, padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(d.name, color = if (selected) CuadraColors.Bg else CuadraColors.Ink, fontWeight = FontWeight.Bold, maxLines = 3, ellipsize = true)
            if (d.printer) TagRow { Tag(stringResource(R.string.printer_device_printer), CuadraColors.Green, CuadraColors.GreenSoft) }
        }
    }
}

@Composable
private fun PaperCard(s: PrinterSettings, actions: PrinterActions) {
    CuadraCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.printer_paper_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
            Text(stringResource(R.string.printer_width), style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted)
            ChipFlow {
                CuadraChip(stringResource(R.string.printer_width_58), s.widthMm < 80, { actions.setWidth(58) })
                CuadraChip(stringResource(R.string.printer_width_80), s.widthMm >= 80, { actions.setWidth(80) })
            }
            SwitchRow(stringResource(R.string.printer_auto), s.autoPrint, actions::setAuto)
            Text(stringResource(R.string.printer_copies), style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted)
            ChipFlow {
                CuadraChip("1", s.copies == 1, { actions.setCopies(1) })
                CuadraChip("2", s.copies == 2, { actions.setCopies(2) })
            }
            Text(stringResource(R.string.printer_charset), style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted)
            ChipFlow {
                CuadraChip(stringResource(R.string.printer_charset_pc858), s.charset == PrintCharset.PC858, { actions.setCharset(PrintCharset.PC858) })
                CuadraChip(stringResource(R.string.printer_charset_ascii), s.charset == PrintCharset.ASCII, { actions.setCharset(PrintCharset.ASCII) })
            }
            Text(stringResource(R.string.printer_charset_hint), style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted)
        }
    }
}

@Composable
private fun HeaderCard(s: PrinterSettings, actions: PrinterActions) {
    // Lo escrito se guarda en cada tecla; el texto del campo vive aquí (no espera al disco) para que teclear no salte.
    var address by remember { mutableStateOf(s.address) }
    var phone by remember { mutableStateOf(s.phone) }
    var tax by remember { mutableStateOf(s.taxId) }
    var footer by remember { mutableStateOf(s.footer) }
    CuadraCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.printer_header_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
            VoiceTextField(
                address, { address = it.take(PrinterSettings.MAX_LINE); actions.setAddress(address) }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.printer_address)) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
            NumberField(phone, { phone = it.take(PrinterSettings.MAX_LINE); actions.setPhone(phone) }, label = { Text(stringResource(R.string.printer_phone), maxLines = 1) }, keyboardType = KeyboardType.Phone)
            VoiceTextField(tax, { tax = it.take(PrinterSettings.MAX_LINE); actions.setTaxId(tax) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(stringResource(R.string.printer_tax)) })
            VoiceTextField(
                footer, { footer = it.take(PrinterSettings.MAX_FOOTER); actions.setFooter(footer) }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.printer_footer)) },
                supportingText = { Text(stringResource(R.string.printer_footer_hint)) }, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
        }
    }
}

@Composable
private fun PreviewCard(ui: PrinterUi) {
    CuadraCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.printer_preview_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
            Text(stringResource(R.string.printer_preview_hint), style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted)
            CuadraCard(color = Color.White) { MonoBlock(ui.preview, ui.settings.columns, Modifier.fillMaxWidth()) }
        }
    }
}

@Composable
private fun TestCard(ui: PrinterUi, actions: PrinterActions) {
    CuadraCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CuadraButton(stringResource(R.string.printer_test), actions::printTest, Modifier.fillMaxWidth(), kind = ButtonKind.DARK, enabled = !ui.testing, height = 48)
            Text(stringResource(R.string.printer_test_hint), style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted)
            when (ui.result) {
                PrintNotice.PRINTED -> Text(stringResource(R.string.print_ok), color = CuadraColors.Green, fontWeight = FontWeight.Bold)
                PrintNotice.NO_PRINTER -> Text(stringResource(R.string.print_none), color = CuadraColors.Orange, fontWeight = FontWeight.Bold)
                PrintNotice.FAILED -> Text(stringResource(R.string.print_failed), color = CuadraColors.Red, fontWeight = FontWeight.Bold)
                null -> Unit
            }
        }
    }
}

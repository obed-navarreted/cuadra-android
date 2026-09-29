package com.cuadra.caja.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.cuadra.caja.R
import com.cuadra.caja.data.local.SaleEntity
import com.cuadra.caja.ui.CajaUi
import com.cuadra.caja.ui.CajaViewModel
import com.cuadra.caja.ui.Notice
import com.cuadra.caja.ui.Weighing
import com.cuadra.caja.ui.common.ButtonKind
import com.cuadra.caja.ui.common.CuadraButton
import com.cuadra.caja.ui.common.CuadraCard
import com.cuadra.caja.ui.common.CuadraChip
import com.cuadra.caja.ui.common.Keypad
import com.cuadra.caja.ui.common.LocalMoney
import com.cuadra.caja.ui.common.money
import com.cuadra.caja.ui.common.VoiceTextField
import com.cuadra.caja.ui.theme.CuadraColors

@Composable
internal fun Sheet(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(24.dp), color = CuadraColors.Bg) {
            Column(Modifier.padding(20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
        }
    }
}

@Composable
fun Overlays(ui: CajaUi, vm: CajaViewModel, parked: List<SaleEntity>) {
    if (ui.showParked) ParkedDialog(parked, vm)
    if (ui.parking) ParkDialog(ui.resumedLabel.orEmpty(), vm)
    ui.weighing?.let { WeighingDialog(it, vm) }
    ui.draft?.let { ProductDialog(it, vm) }
    ui.notice?.let { NoticeDialog(it, vm) }
}

@Composable
private fun ParkedDialog(parked: List<SaleEntity>, vm: CajaViewModel) {
    Sheet({ vm.toggleParked(false) }) {
        Text(stringResource(R.string.parked_title), style = MaterialTheme.typography.headlineMedium)
        if (parked.isEmpty()) Text(stringResource(R.string.parked_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyColumn(Modifier.heightIn(max = 380.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(parked, key = { it.id }) { s ->
                CuadraCard {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(s.label ?: "—", fontWeight = FontWeight.ExtraBold)
                                s.createdByName?.let { Text(stringResource(R.string.parked_by, it), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            }
                            Text(money(s.totalMinor), fontWeight = FontWeight.ExtraBold)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CuadraButton(stringResource(R.string.parked_resume), { vm.resume(s.id) }, Modifier.weight(1f), kind = ButtonKind.DARK)
                            CuadraButton(stringResource(R.string.parked_discard), { vm.discardParked(s.id) }, Modifier.weight(1f))
                        }
                    }
                }
            }
        }
        CuadraButton(stringResource(R.string.close), { vm.toggleParked(false) }, Modifier.fillMaxWidth())
    }
}

@Composable
private fun ParkDialog(initial: String, vm: CajaViewModel) {
    var label by remember { mutableStateOf(initial) }
    Sheet(vm::cancelPark) {
        Text(stringResource(R.string.parked_confirm_title), style = MaterialTheme.typography.headlineMedium)
        VoiceTextField(
            label, { label = it.take(60) }, Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text(stringResource(R.string.parked_label_hint)) },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CuadraButton(stringResource(R.string.cancel), vm::cancelPark, Modifier.weight(1f))
            CuadraButton(stringResource(R.string.register_park), { vm.park(label) }, Modifier.weight(1f), kind = ButtonKind.DARK)
        }
    }
}

/** Producto por peso: se escribe el peso o el monto y se muestra el otro. Ej.: C$ 70 de queso a C$ 90/lb = 0.78 lb. */
@Composable
private fun WeighingDialog(w: Weighing, vm: CajaViewModel) {
    val fmt = LocalMoney.current
    val qty = w.quantityMilli(fmt.decimals)
    val total = w.totalMinor(fmt.decimals)
    Sheet(vm::weighingCancel) {
        Text(listOfNotNull(w.product.name, w.product.variant).joinToString(" "), style = MaterialTheme.typography.headlineMedium)
        Text(fmt.format(w.product.priceMinor) + " " + stringResource(R.string.register_by_weight_unit), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CuadraChip(stringResource(R.string.weight_by_amount), w.byAmount, { vm.weighingMode(true) }, Modifier.weight(1f))
            CuadraChip(stringResource(R.string.weight_by_weight), !w.byAmount, { vm.weighingMode(false) }, Modifier.weight(1f))
        }
        Text(stringResource(R.string.weight_how_much), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            (if (w.byAmount) "" else "") + w.text.ifEmpty { "0" } + if (w.byAmount) "" else " " + stringResource(R.string.unit_lb),
            style = MaterialTheme.typography.displayLarge.copy(fontSize = androidx.compose.ui.unit.TextUnit(40f, androidx.compose.ui.unit.TextUnitType.Sp)),
        )
        Text(
            if (w.byAmount) stringResource(R.string.weight_weigh) + "  " + (qty?.let { qtyText(it) + " " + stringResource(R.string.unit_lb) } ?: "—")
            else stringResource(R.string.weight_total) + "  " + (total?.let { fmt.format(it) } ?: "—"),
            color = CuadraColors.Green, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.bodyLarge,
        )
        Keypad(vm::weighingKey, vm::weighingBackspace, Modifier.fillMaxWidth(), onDot = vm::weighingDot)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CuadraButton(stringResource(R.string.cancel), vm::weighingCancel, Modifier.weight(1f))
            CuadraButton(stringResource(R.string.weight_add), vm::weighingConfirm, Modifier.weight(1f), kind = ButtonKind.DARK, enabled = qty != null)
        }
    }
}

/** Producto nuevo desde la misma pantalla de venta: nombre, precio y si se vende por peso o va en Rápidos. */
@Composable
private fun ProductDialog(d: com.cuadra.caja.ui.ProductDraft, vm: CajaViewModel) {
    Sheet(vm::closeDraft) {
        Text(stringResource(R.string.product_new_title), style = MaterialTheme.typography.headlineMedium)
        VoiceTextField(
            d.name, { vm.updateDraft(d.copy(name = it.take(120))) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(stringResource(R.string.product_name)) },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        )
        OutlinedTextField(
            d.price, { vm.updateDraft(d.copy(price = it.take(14))) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(stringResource(R.string.product_price)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
        // sin voz: código de barras (se escanea o se teclea)
        OutlinedTextField(
            d.barcode, { vm.updateDraft(d.copy(barcode = it.take(64))) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(stringResource(R.string.product_barcode)) },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CuadraChip(stringResource(R.string.product_by_weight), d.byWeight, { vm.updateDraft(d.copy(byWeight = !d.byWeight)) })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CuadraChip(stringResource(R.string.product_quick), d.quick, { vm.updateDraft(d.copy(quick = !d.quick)) })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CuadraButton(stringResource(R.string.cancel), vm::closeDraft, Modifier.weight(1f))
            CuadraButton(stringResource(R.string.save), vm::saveDraft, Modifier.weight(1f), kind = ButtonKind.DARK)
        }
    }
}

@Composable
private fun NoticeDialog(n: Notice, vm: CajaViewModel) {
    val text = when (n) {
        Notice.CodeUnknown -> R.string.register_code_unknown
        Notice.CodeUnknownOffline -> R.string.register_code_unknown_offline
        Notice.TicketLocked -> R.string.parked_locked
        Notice.InvalidProduct -> R.string.product_invalid
    }
    Sheet(vm::dismissNotice) {
        Text(stringResource(text), style = MaterialTheme.typography.bodyLarge)
        CuadraButton(stringResource(R.string.close), vm::dismissNotice, Modifier.fillMaxWidth(), kind = ButtonKind.DARK)
    }
}

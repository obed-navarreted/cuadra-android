package com.cuadra.caja.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cuadra.caja.R
import com.cuadra.caja.data.local.SaleEntity
import com.cuadra.caja.domain.Pricing
import com.cuadra.caja.ui.common.ChipFlow
import com.cuadra.caja.ui.CajaActions
import com.cuadra.caja.ui.CajaUi
import com.cuadra.caja.ui.Notice
import com.cuadra.caja.ui.Weighing
import com.cuadra.caja.ui.common.BarcodeScannerDialog
import com.cuadra.caja.ui.common.ButtonKind
import com.cuadra.caja.ui.common.ButtonRow
import com.cuadra.caja.ui.common.CuadraButton
import com.cuadra.caja.ui.common.CuadraCard
import com.cuadra.caja.ui.common.CuadraChip
import com.cuadra.caja.ui.common.ChipGrid
import com.cuadra.caja.ui.common.Keypad
import com.cuadra.caja.ui.common.LocalMoney
import com.cuadra.caja.ui.common.MoneyText
import com.cuadra.caja.ui.common.NumberField
import com.cuadra.caja.ui.common.ScanFieldIcon
import com.cuadra.caja.ui.common.Sheet
import com.cuadra.caja.ui.common.SplitRow
import com.cuadra.caja.ui.common.Text
import com.cuadra.caja.ui.common.VoiceTextField
import com.cuadra.caja.ui.common.money
import com.cuadra.caja.ui.theme.CuadraColors

@Composable
fun Overlays(ui: CajaUi, actions: CajaActions, parked: List<SaleEntity>) {
    if (ui.receiptOpen) ReceiptSheet(ui, actions, parked.size)
    ui.editingLine?.let { QuantityDialog(it, actions) }
    ui.productMenu?.let { ProductMenuSheet(it, actions) }
    if (ui.showParked) ParkedDialog(parked, actions)
    if (ui.parking) ParkDialog(ui.resumedLabel.orEmpty(), actions)
    ui.weighing?.let { WeighingDialog(it, actions) }
    ui.openPrice?.let { OpenPriceDialog(it, actions) }
    ui.draft?.let { ProductDialog(it, actions) }
    ui.notice?.let { NoticeDialog(it, actions) }
    if (ui.scanning) {
        BarcodeScannerDialog(
            onDismiss = actions::closeScanner, onCode = actions::onScanned, continuousOption = true, addedCount = ui.scanAdded,
            paused = ui.notice != null || ui.draft != null || ui.weighing != null || ui.openPrice != null,
        )
    }
}

/** Cuentas apartadas. Descartar pide confirmación (cualquiera que venda puede descartar la de otro, y se borra en todos los teléfonos). */
@Composable
fun ParkedDialog(parked: List<SaleEntity>, actions: CajaActions, initialConfirm: String? = null) {
    var confirmId by rememberSaveable { mutableStateOf(initialConfirm) }
    val confirming = confirmId?.let { id -> parked.firstOrNull { it.id == id } }
    if (confirming != null) {
        Sheet({ confirmId = null }, actions = {
            ButtonRow {
                CuadraButton(stringResource(R.string.cancel), { confirmId = null }, Modifier.share(1f))
                CuadraButton(stringResource(R.string.parked_discard), { confirmId = null; actions.discardParked(confirming.id) }, Modifier.share(1.3f), kind = ButtonKind.DANGER)
            }
        }) {
            Text(stringResource(R.string.parked_discard_title), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(R.string.parked_discard_body, confirming.label ?: "—", money(confirming.totalMinor)), style = MaterialTheme.typography.bodyLarge)
        }
        return
    }
    Sheet({ actions.toggleParked(false) }, actions = { CuadraButton(stringResource(R.string.close), { actions.toggleParked(false) }, Modifier.fillMaxWidth()) }) {
        Text(stringResource(R.string.parked_title), style = MaterialTheme.typography.headlineMedium)
        if (parked.isEmpty()) Text(stringResource(R.string.parked_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
        parked.forEach { s ->
            CuadraCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SplitRow(end = { MoneyText(money(s.totalMinor), fontWeight = FontWeight.ExtraBold) }) {
                        Column {
                            Text(s.label ?: "—", fontWeight = FontWeight.ExtraBold, maxLines = 3, ellipsize = true)
                            s.createdByName?.let { Text(stringResource(R.string.parked_by, it), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, ellipsize = true) }
                        }
                    }
                    ButtonRow {
                        CuadraButton(stringResource(R.string.parked_resume), { actions.resume(s.id) }, Modifier.share(1f), kind = ButtonKind.DARK)
                        CuadraButton(stringResource(R.string.parked_discard), { confirmId = s.id }, Modifier.share(1f))
                    }
                }
            }
        }
    }
}

@Composable
fun ParkDialog(initial: String, actions: CajaActions) {
    var label by remember { mutableStateOf(initial) }
    Sheet(actions::cancelPark, actions = {
        ButtonRow {
            CuadraButton(stringResource(R.string.cancel), actions::cancelPark, Modifier.share(1f))
            CuadraButton(stringResource(R.string.register_park), { actions.park(label) }, Modifier.share(1f), kind = ButtonKind.DARK)
        }
    }) {
        Text(stringResource(R.string.parked_confirm_title), style = MaterialTheme.typography.headlineMedium)
        VoiceTextField(
            label, { label = it.take(60) }, Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text(stringResource(R.string.parked_label_hint)) },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        )
    }
}

/** Producto por peso: se escribe el peso o el monto y se muestra el otro. Ej.: C$ 70 de queso a C$ 90/lb = 0.78 lb. */
@Composable
fun WeighingDialog(w: Weighing, actions: CajaActions) {
    val fmt = LocalMoney.current
    val qty = w.quantityMilli(fmt.decimals)
    val total = w.totalMinor(fmt.decimals)
    Sheet(actions::weighingCancel, actions = {
        ButtonRow {
            CuadraButton(stringResource(R.string.cancel), actions::weighingCancel, Modifier.share(1f))
            CuadraButton(stringResource(R.string.weight_add), actions::weighingConfirm, Modifier.share(1f), kind = ButtonKind.DARK, enabled = qty != null)
        }
    }) {
        Text(listOfNotNull(w.product.name, w.product.variant).joinToString(" "), style = MaterialTheme.typography.headlineMedium, maxLines = 4, ellipsize = true)
        Text(fmt.format(w.product.priceMinor) + " " + stringResource(R.string.register_by_weight_unit), color = MaterialTheme.colorScheme.onSurfaceVariant)
        ChipGrid {
            CuadraChip(stringResource(R.string.weight_by_amount), w.byAmount, { actions.weighingMode(true) })
            CuadraChip(stringResource(R.string.weight_by_weight), !w.byAmount, { actions.weighingMode(false) })
        }
        Text(stringResource(R.string.weight_how_much), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        MoneyText(
            w.text.ifEmpty { "0" } + if (w.byAmount) "" else " " + stringResource(R.string.unit_lb), Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.displayMedium,
        )
        Text(
            if (w.byAmount) stringResource(R.string.weight_weigh) + "  " + (qty?.let { qtyText(it) + " " + stringResource(R.string.unit_lb) } ?: "—")
            else stringResource(R.string.weight_total) + "  " + (total?.let { fmt.format(it) } ?: "—"),
            color = CuadraColors.Green, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.bodyLarge,
        )
        Keypad(actions::weighingKey, actions::weighingBackspace, Modifier.fillMaxWidth(), onDot = actions::weighingDot)
    }
}

/** Precio de un producto de precio abierto: se escribe con el teclado numérico (parte del sugerido, si hay) y «Agregar» lo pone en la venta con cantidad 1. */
@Composable
fun OpenPriceDialog(o: com.cuadra.caja.ui.OpenPricing, actions: CajaActions) {
    val fmt = LocalMoney.current
    val price = o.entry.minor(fmt.decimals)
    Sheet(actions::openPriceCancel, actions = {
        ButtonRow {
            CuadraButton(stringResource(R.string.cancel), actions::openPriceCancel, Modifier.share(1f))
            CuadraButton(stringResource(R.string.open_price_add), actions::openPriceConfirm, Modifier.share(1f), kind = ButtonKind.DARK, enabled = price != null)
        }
    }) {
        Text(stringResource(R.string.open_price_title, listOfNotNull(o.product.name, o.product.variant).joinToString(" ")), style = MaterialTheme.typography.headlineMedium, maxLines = 4, ellipsize = true)
        Text(stringResource(R.string.open_price_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        MoneyText(
            o.entry.text.ifEmpty { "0" }, Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.displayMedium,
        )
        Text(price?.let { fmt.format(it) } ?: "—", color = CuadraColors.Green, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.bodyLarge)
        Keypad(actions::openPriceKey, actions::openPriceBackspace, Modifier.fillMaxWidth(), onDot = if (fmt.decimals > 0) actions::openPriceDot else null)
    }
}

/** Producto nuevo desde la misma pantalla de venta: nombre, cómo se cobra (fijo, por peso o abierto), precio y si es ★ Frecuente. */
@Composable
fun ProductDialog(d: com.cuadra.caja.ui.ProductDraft, actions: CajaActions) {
    var scanning by remember { mutableStateOf(false) }
    if (scanning) BarcodeScannerDialog(onDismiss = { scanning = false }, onCode = { actions.updateDraft(d.copy(barcode = it.take(64))) })
    Sheet(actions::closeDraft, actions = {
        ButtonRow {
            CuadraButton(stringResource(R.string.cancel), actions::closeDraft, Modifier.share(1f))
            CuadraButton(stringResource(R.string.save), actions::saveDraft, Modifier.share(1f), kind = ButtonKind.DARK)
        }
    }) {
        Text(stringResource(R.string.product_new_title), style = MaterialTheme.typography.headlineMedium)
        VoiceTextField(
            d.name, { actions.updateDraft(d.copy(name = it.take(120))) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(stringResource(R.string.product_name)) },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        )
        ChipFlow {
            CuadraChip(stringResource(R.string.pricing_FIXED), d.pricing == Pricing.FIXED, { actions.updateDraft(d.copy(pricing = Pricing.FIXED)) })
            CuadraChip(stringResource(R.string.pricing_BY_WEIGHT), d.pricing == Pricing.BY_WEIGHT, { actions.updateDraft(d.copy(pricing = Pricing.BY_WEIGHT)) })
            CuadraChip(stringResource(R.string.pricing_OPEN), d.pricing == Pricing.OPEN, { actions.updateDraft(d.copy(pricing = Pricing.OPEN)) })
        }
        NumberField(
            d.price, { actions.updateDraft(d.copy(price = it.take(14))) },
            label = { Text(stringResource(when (d.pricing) { Pricing.OPEN -> R.string.product_price_suggested; Pricing.BY_WEIGHT -> R.string.product_price_by_weight; else -> R.string.product_price }), maxLines = 1) },
            supportingText = if (d.pricing == Pricing.OPEN) ({ Text(stringResource(R.string.product_price_hint_optional)) }) else null,
        )
        com.cuadra.caja.ui.common.BarcodeField(d.barcode, { actions.updateDraft(d.copy(barcode = it.take(64))) }, { scanning = true })
        com.cuadra.caja.ui.common.ChipFlow {
            CuadraChip(stringResource(R.string.product_quick), d.quick, { actions.updateDraft(d.copy(quick = !d.quick)) })
        }
    }
}

@Composable
fun NoticeDialog(n: Notice, actions: CajaActions) {
    val text = when (n) {
        is Notice.CodeUnknown -> if (n.checksumOk) R.string.register_code_unknown else R.string.register_code_unknown_checksum
        Notice.CodeUnknownOffline -> R.string.register_code_unknown_offline
        Notice.TicketLocked -> R.string.parked_locked
        Notice.InvalidProduct -> R.string.product_invalid
        is Notice.BarcodeInUse -> R.string.product_barcode_taken
    }
    Sheet(actions::dismissNotice, actions = {
        if (n is Notice.CodeUnknown) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CuadraButton(stringResource(R.string.scan_create_product), { actions.createFromCode(n.code) }, Modifier.fillMaxWidth(), kind = ButtonKind.DARK)
                CuadraButton(stringResource(R.string.close), actions::dismissNotice, Modifier.fillMaxWidth())
            }
        } else {
            CuadraButton(stringResource(R.string.close), actions::dismissNotice, Modifier.fillMaxWidth(), kind = ButtonKind.DARK)
        }
    }) {
        Text(if (n is Notice.BarcodeInUse) stringResource(text, n.owner) else stringResource(text), style = MaterialTheme.typography.bodyLarge)
        if (n is Notice.CodeUnknown) Text(n.code, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.headlineMedium, maxLines = 4, minScale = 0.5f)
    }
}

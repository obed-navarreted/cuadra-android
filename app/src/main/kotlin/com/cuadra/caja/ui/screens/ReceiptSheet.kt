package com.cuadra.caja.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cuadra.caja.R
import com.cuadra.caja.domain.CartLine
import com.cuadra.caja.domain.QuantityEdit
import com.cuadra.caja.domain.QuantityInput
import com.cuadra.caja.domain.UndoEntry
import com.cuadra.caja.ui.CajaActions
import com.cuadra.caja.ui.CajaUi
import com.cuadra.caja.ui.common.AppBottomSheet
import com.cuadra.caja.ui.common.ButtonKind
import com.cuadra.caja.ui.common.ButtonRow
import com.cuadra.caja.ui.common.ChipFlow
import com.cuadra.caja.ui.common.CuadraButton
import com.cuadra.caja.ui.common.CuadraCard
import com.cuadra.caja.ui.common.FloatingNotice
import com.cuadra.caja.ui.common.LinkAction
import com.cuadra.caja.ui.common.MoneyText
import com.cuadra.caja.ui.common.ScreenFrame
import com.cuadra.caja.ui.common.Sheet
import com.cuadra.caja.ui.common.SplitRow
import com.cuadra.caja.ui.common.Text
import com.cuadra.caja.ui.common.TitleBar
import com.cuadra.caja.ui.common.VoiceTextField
import com.cuadra.caja.ui.common.hint
import com.cuadra.caja.ui.common.money
import com.cuadra.caja.ui.theme.CuadraColors

/** La hoja del recibo (sube desde abajo, ~75 % del alto, se arrastra para cerrarla). */
@Composable
fun ReceiptSheet(ui: CajaUi, actions: CajaActions, parkedCount: Int = 0, registerCheckout: Boolean = false, canEditProducts: Boolean = false) {
    AppBottomSheet(actions::closeReceipt) { ReceiptSheetContent(ui, actions, parkedCount, registerCheckout, canEditProducts) }
}

/**
 * Contenido de la hoja: arriba el título y «Cerrar» (fijos), en el centro las líneas (se desplazan) y abajo el total, «Apartar» y «Cobrar» (fijos).
 * Deslizar una línea a la izquierda la quita entera (con «Deshacer»); a la derecha abre el teclado para escribir la cantidad; tocarla, lo mismo.
 * Cada línea tiene además un menú (⋮) y acciones de accesibilidad con lo mismo, para no depender del gesto.
 */
@Composable
fun ReceiptSheetContent(ui: CajaUi, actions: CajaActions, parkedCount: Int = 0, registerCheckout: Boolean = false, canEditProducts: Boolean = false) {
    val cart = ui.cart
    // Con promociones: el total que se cobra y, debajo de la última línea que tocó cada una, «Promo 3 por C$ 100: −C$ 70».
    val priced = ui.priced
    Box(Modifier.fillMaxSize()) {
    ScreenFrame(
        Modifier.fillMaxSize().padding(horizontal = 16.dp), spacing = 8.dp,
        header = {
            TitleBar(
                Modifier.fillMaxWidth(),
                end = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (!cart.isEmpty) LinkAction(stringResource(R.string.register_clear), actions::clearCart, color = CuadraColors.Red)
                        CloseX(actions::closeReceipt)
                    }
                },
            ) {
                val clearLabel = stringResource(R.string.register_clear)
                Column(if (cart.isEmpty) Modifier else Modifier.semantics { customActions = listOf(CustomAccessibilityAction(clearLabel) { actions.clearCart(); true }) }) {
                    Text(stringResource(R.string.receipt_title), style = MaterialTheme.typography.headlineSmall)
                    Text(
                        androidx.compose.ui.res.pluralStringResource(R.plurals.register_lines, cart.lineCount, cart.lineCount),
                        style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Normal, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        footer = {
            SplitRow(end = { MoneyText(money(priced.totalMinor), fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.headlineSmall) }) {
                Text(stringResource(R.string.receipt_total), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold)
            }
            ButtonRow(Modifier.padding(bottom = 8.dp)) {
                // Como en la barra de la caja: «Apartar» o, con «Cobro en caja», «Enviar a caja» en el mismo lugar.
                if (registerCheckout) CuadraButton(stringResource(if (ui.resumedPending) R.string.send_update_confirm else R.string.send_bar), actions::askSend, Modifier.share(0.8f), enabled = !cart.isEmpty, height = 48)
                else CuadraButton(stringResource(R.string.register_park), actions::askPark, Modifier.share(0.6f), enabled = !cart.isEmpty, height = 48)
                CuadraButton(
                    stringResource(R.string.register_charge) + if (cart.isEmpty) "" else "  ·  " + money(priced.totalMinor),
                    actions::startCobro, Modifier.share(1f), kind = ButtonKind.PRIMARY, enabled = priced.totalMinor > 0, height = 48,
                )
            }
        },
    ) {
        if (cart.isEmpty) {
            Text(stringResource(R.string.receipt_empty), Modifier.fillMaxWidth().padding(vertical = 24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        } else {
            LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (ui.swipeHint) item(key = "hint") { SwipeHint(actions::dismissSwipeHint) }
                cart.lines.forEach { line ->
                    item(key = line.id) { ReceiptLine(line, actions, canEditProducts) }
                    priced.promo.applied.filter { it.lineIds.lastOrNull() == line.id }.forEach { a ->
                        item(key = "promo-" + a.promotionId) { PromoLineRow(a.quantity, a.priceMinor, a.discountMinor, Modifier.padding(horizontal = 12.dp)) }
                    }
                }
                // Las cuentas apartadas también se abren desde aquí: en un teléfono muy bajo con letra enorme el encabezado de la caja (con «Apartadas») puede no caber.
                if (parkedCount > 0) item(key = "parked") { LinkAction(stringResource(if (registerCheckout) R.string.register_queue_chip else R.string.register_parked) + " $parkedCount  ›", { actions.toggleParked(true) }) }
            }
        }
    }
    // Aviso flotante «Deshacer» (arriba de la hoja, encima del contenido: no ocupa lugar).
    if (ui.priceUpdated) FloatingNotice(stringResource(R.string.price_updated), null, {}, Modifier.align(Alignment.TopCenter).padding(top = 4.dp))
    else ui.undo?.let { UndoPopup(it, actions::undoLast, Modifier.align(Alignment.TopCenter).padding(top = 4.dp)) }
    }
}

@Composable
private fun CloseX(onClick: () -> Unit) {
    val description = stringResource(R.string.receipt_close)
    Box(
        Modifier.size(48.dp).clickable(role = Role.Button, onClick = onClick).semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { Text("✕", style = MaterialTheme.typography.headlineMedium, maxLines = 1, minScale = 0.6f) }
}

@Composable
private fun SwipeHint(onDismiss: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(CuadraColors.GreenSoft, RoundedCornerShape(16.dp)).padding(horizontal = 14.dp, vertical = 8.dp)) {
        Text("←  " + stringResource(R.string.receipt_swipe_delete) + "     " + stringResource(R.string.receipt_swipe_qty) + "  →", fontWeight = FontWeight.ExtraBold, color = CuadraColors.Green)
        Text(stringResource(R.string.receipt_hint), style = MaterialTheme.typography.bodyMedium)
        LinkAction(stringResource(R.string.receipt_hint_ok), onDismiss)
    }
}

/** Una línea del recibo con sus gestos. Izquierda = eliminar entera; derecha = escribir la cantidad (la fila vuelve a su sitio y abre el diálogo). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReceiptLine(line: CartLine, actions: CajaActions, canEdit: Boolean) {
    val current by rememberUpdatedState(line)
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { target ->
            when (target) {
                SwipeToDismissBoxValue.EndToStart -> { actions.deleteLine(current.id); true }
                SwipeToDismissBoxValue.StartToEnd -> { actions.editQuantity(current.id); false }
                SwipeToDismissBoxValue.Settled -> false
            }
        },
        positionalThreshold = { it * 0.35f },
    )
    SwipeToDismissBox(
        state,
        backgroundContent = {
            val dir = state.dismissDirection
            val deleting = dir == SwipeToDismissBoxValue.EndToStart
            val editing = dir == SwipeToDismissBoxValue.StartToEnd
            if (deleting || editing) {
                Box(
                    Modifier.fillMaxSize().background(if (deleting) CuadraColors.Red else CuadraColors.Green, RoundedCornerShape(16.dp)).padding(horizontal = 16.dp),
                    contentAlignment = if (deleting) Alignment.CenterEnd else Alignment.CenterStart,
                ) { Text(stringResource(if (deleting) R.string.receipt_swipe_delete else R.string.receipt_swipe_qty), color = androidx.compose.ui.graphics.Color.White, fontWeight = FontWeight.ExtraBold, maxLines = 1) }
            }
        },
    ) { LineCard(line, actions, canEdit) }
}

/**
 * Una línea del recibo: arriba el nombre (hasta 2 líneas) y el total a la derecha; abajo el detalle «C$ 25.00 × 2» en gris y los controles VISIBLES:
 * [−] cantidad [+] [lápiz] [papelera] (todos de 48 dp tocables). Si no caben junto al detalle (letra grande, teléfono angosto) bajan a su propia fila, sin recortarse.
 * La papelera quita la línea de una vez (con «Línea quitada · Deshacer», sin confirmación); el lápiz abre «Editar producto» en las del catálogo (quien puede editar
 * productos) y la hoja de descripción y cantidad en las demás. Los gestos de deslizar siguen funcionando.
 */
@Composable
private fun LineCard(line: CartLine, actions: CajaActions, canEdit: Boolean) {
    val step = if (line.quantityMilli % 1000L == 0L) 1000L else 250L
    val deleteLabel = stringResource(R.string.receipt_delete_line)
    val changeLabel = stringResource(if (line.descriptionEditable) R.string.register_edit_line else R.string.receipt_change_qty)
    val editProductLabel = stringResource(R.string.receipt_edit_product)
    val productId = line.productId.takeIf { canEdit }
    CuadraCard(
        onClick = { actions.editQuantity(line.id) },
        padding = PaddingValues(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 0.dp),
        modifier = Modifier.semantics {
            customActions = listOf(
                CustomAccessibilityAction(changeLabel) { actions.editQuantity(line.id); true },
                CustomAccessibilityAction(deleteLabel) { actions.deleteLine(line.id); true },
            ) + listOfNotNull(productId?.let { id -> CustomAccessibilityAction(editProductLabel) { actions.editProduct(id); true } })
        },
    ) {
        Column {
            SplitRow(Modifier.padding(end = 8.dp), endMaxFraction = 0.6f, end = { MoneyText(money(line.totalMinor), fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleSmall) }) {
                Text(line.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold, maxLines = 2, ellipsize = true)
            }
            SplitRow(
                endMaxFraction = 0.95f, spacing = 4.dp,
                end = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StepButton("−", stringResource(R.string.receipt_minus)) { actions.changeQuantity(line.id, -step) }
                        MoneyText(qtyText(line.quantityMilli), Modifier.weight(1f, fill = false).padding(horizontal = 2.dp), fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleSmall)
                        StepButton("+", stringResource(R.string.receipt_plus)) { actions.changeQuantity(line.id, step) }
                        LineIconButton(R.drawable.ic_edit, stringResource(R.string.receipt_edit_line_action), CuadraColors.Ink, Modifier.testTag(TAG_LINE_EDIT)) {
                            if (productId != null) actions.editProduct(productId) else actions.editQuantity(line.id)
                        }
                        LineIconButton(R.drawable.ic_trash, stringResource(R.string.receipt_remove_line), CuadraColors.Red, Modifier.testTag(TAG_LINE_REMOVE)) { actions.deleteLine(line.id) }
                    }
                },
            ) {
                Text("${money(line.unitPriceMinor)} × ${qtyText(line.quantityMilli)}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Normal, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Etiquetas de prueba de los controles visibles de cada línea del recibo (lápiz y papelera). */
const val TAG_LINE_EDIT = "ReceiptLineEdit"
const val TAG_LINE_REMOVE = "ReceiptLineRemove"

/** Botón con ícono de 48 x 48 dp tocables (el ícono mide 22 dp): lápiz «Editar» y papelera «Quitar línea». */
@Composable
internal fun LineIconButton(@androidx.annotation.DrawableRes icon: Int, description: String, tint: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(modifier.size(48.dp).clickable(role = Role.Button, onClick = onClick).semantics { contentDescription = description }, contentAlignment = Alignment.Center) {
        Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(22.dp), tint = tint)
    }
}

/** − o +: círculo de 32 dp dentro de un área tocable de 48 x 48 dp. */
@Composable
private fun StepButton(symbol: String, description: String, onClick: () -> Unit) {
    Box(Modifier.size(48.dp).clickable(role = Role.Button, onClick = onClick).semantics { contentDescription = description }, contentAlignment = Alignment.Center) {
        Box(Modifier.size(32.dp).background(CuadraColors.Soft, CircleShape), contentAlignment = Alignment.Center) {
            Text(symbol, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold, maxLines = 1, minScale = 0.6f)
        }
    }
}

/**
 * Aviso flotante «Cuajada ×1 · C$ 25.00 · Deshacer» (también «Recibo vaciado» y «Línea eliminada»): no ocupa lugar, se dibuja arriba encima del
 * contenido y se oculta solo (lo hace la pantalla). Ver `FloatingNotice`.
 */
@Composable
fun UndoPopup(entry: UndoEntry, onUndo: () -> Unit, modifier: Modifier = Modifier) {
    val text = when (entry) {
        is UndoEntry.Added -> stringResource(R.string.undo_added, entry.label, qtyText(entry.quantityMilli), money(entry.amountMinor))
        is UndoEntry.Deleted -> stringResource(R.string.undo_deleted)
        is UndoEntry.Cleared -> stringResource(R.string.undo_cleared)
    }
    FloatingNotice(text, stringResource(R.string.undo_action), onUndo, modifier)
}

/**
 * Editar una línea antes de cobrar (se abre al tocarla). Una línea agregada A MANO (teclado) cambia su descripción (dictable; vacía = «Varios») y su
 * cantidad; un producto del catálogo conserva su nombre y solo cambia la cantidad (15, 0.5 lb…). 0 = quitar la línea: el botón cambia a «Eliminar
 * línea» y ahí se confirma. La regla de qué se puede editar vive en `CartLine.descriptionEditable`.
 */
@Composable
fun QuantityDialog(line: CartLine, actions: CajaActions, initialText: String = QuantityInput.toText(line.quantityMilli), initialDescription: String = line.descriptionText) {
    val decimals = line.allowsDecimals
    val manual = line.descriptionEditable
    var field by remember(line.id) { mutableStateOf(TextFieldValue(initialText, TextRange(0, initialText.length))) }
    var description by remember(line.id) { mutableStateOf(initialDescription) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(line.id) { runCatching { focus.requestFocus() } }
    val edit = QuantityInput.parse(field.text, decimals)
    val save = {
        when (edit) {
            is QuantityEdit.Set -> actions.editLine(line.id, if (manual) description else null, edit.milli)
            QuantityEdit.Remove -> actions.deleteLine(line.id)
            QuantityEdit.Invalid -> Unit
        }
    }
    Sheet(actions::closeQuantityEdit, actions = {
        ButtonRow {
            CuadraButton(stringResource(R.string.cancel), actions::closeQuantityEdit, Modifier.share(1f))
            if (edit == QuantityEdit.Remove) CuadraButton(stringResource(R.string.qty_remove), { save() }, Modifier.share(1f), kind = ButtonKind.DANGER)
            else CuadraButton(stringResource(R.string.qty_save), { save() }, Modifier.share(1f), kind = ButtonKind.DARK, enabled = edit is QuantityEdit.Set)
        }
    }) {
        Text(stringResource(if (manual) R.string.register_edit_line else R.string.qty_title), style = MaterialTheme.typography.headlineMedium)
        if (manual) {
            VoiceTextField(
                description, { description = it.take(com.cuadra.caja.domain.Cart.MAX_DESCRIPTION) }, Modifier.fillMaxWidth(), singleLine = true,
                label = { Text(stringResource(R.string.line_description)) }, placeholder = { Text(stringResource(R.string.line_description_empty)) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
            )
        } else {
            Text(line.label, fontWeight = FontWeight.ExtraBold, maxLines = 3, ellipsize = true)
            Text(stringResource(R.string.line_catalog_name), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        OutlinedTextField(
            field, { v -> field = v.copy(text = QuantityInput.sanitize(v.text, decimals)) }, Modifier.fillMaxWidth().focusRequester(focus), singleLine = true,
            label = hint { Text(stringResource(R.string.qty_label)) },
            keyboardOptions = KeyboardOptions(keyboardType = if (decimals) KeyboardType.Decimal else KeyboardType.Number, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { save() }),
            isError = edit == QuantityEdit.Invalid,
        )
        when (edit) {
            QuantityEdit.Remove -> Text(stringResource(R.string.qty_zero_hint), color = CuadraColors.Red, fontWeight = FontWeight.Bold)
            QuantityEdit.Invalid -> Text(stringResource(if (!decimals && field.text.contains('.')) R.string.qty_no_decimals else R.string.qty_invalid), color = CuadraColors.Red)
            is QuantityEdit.Set -> Text(
                stringResource(R.string.qty_line_total, money(line.unitPriceMinor), qtyText(edit.milli), money(com.cuadra.caja.domain.SaleMath.lineTotal(line.unitPriceMinor, edit.milli, line.discountMinor))),
                color = CuadraColors.Green, fontWeight = FontWeight.ExtraBold,
            )
        }
    }
}

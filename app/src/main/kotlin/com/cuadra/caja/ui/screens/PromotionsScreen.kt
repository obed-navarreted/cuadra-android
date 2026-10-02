package com.cuadra.caja.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cuadra.caja.R
import com.cuadra.caja.data.local.ProductEntity
import com.cuadra.caja.domain.PromotionEditor
import com.cuadra.caja.domain.PromotionError
import com.cuadra.caja.domain.PromotionState
import com.cuadra.caja.ui.PromoNotice
import com.cuadra.caja.ui.PromotionDraft
import com.cuadra.caja.ui.PromotionRow
import com.cuadra.caja.ui.PromotionsActions
import com.cuadra.caja.ui.PromotionsUi
import com.cuadra.caja.ui.PromotionsViewModel
import com.cuadra.caja.ui.common.AppDialog
import com.cuadra.caja.ui.common.BarcodeScannerDialog
import com.cuadra.caja.ui.common.ButtonKind
import com.cuadra.caja.ui.common.ButtonRow
import com.cuadra.caja.ui.common.ChipFlow
import com.cuadra.caja.ui.common.CuadraButton
import com.cuadra.caja.ui.common.CuadraCard
import com.cuadra.caja.ui.common.CuadraChip
import com.cuadra.caja.ui.common.LinkAction
import com.cuadra.caja.ui.common.LocalMoney
import com.cuadra.caja.ui.common.MoneyText
import com.cuadra.caja.ui.common.NumberField
import com.cuadra.caja.ui.common.ScanTargetEffect
import com.cuadra.caja.ui.common.ScreenFrame
import com.cuadra.caja.ui.common.SectionLabel
import com.cuadra.caja.ui.common.Sheet
import com.cuadra.caja.ui.common.SplitRow
import com.cuadra.caja.ui.common.Tag
import com.cuadra.caja.ui.common.TagRow
import com.cuadra.caja.ui.common.Text
import com.cuadra.caja.ui.common.TitleBar
import com.cuadra.caja.ui.common.VoiceTextField
import com.cuadra.caja.ui.common.money
import com.cuadra.caja.ui.theme.CuadraColors
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.delay

/** Etiquetas de prueba: el editor y su lista de productos elegidos. */
const val TAG_PROMO_EDITOR = "PromoEditor"
const val TAG_PROMO_PRODUCT = "PromoProduct"

/** Productos › Promociones (dueño y admins). */
@Composable
fun PromotionsScreen(vm: PromotionsViewModel, onBack: () -> Unit) {
    val ui by vm.ui.collectAsState()
    val rows by vm.rows.collectAsState()
    androidx.activity.compose.BackHandler(enabled = ui.draft != null) { vm.close() }
    PromotionsContent(ui, rows, vm, onBack)
}

/** La pantalla sin ViewModel (estado + acciones): es lo que dibuja la guardia de diseño. */
@Composable
fun PromotionsContent(ui: PromotionsUi, rows: List<PromotionRow>, actions: PromotionsActions, onBack: () -> Unit, today: LocalDate = LocalDate.now()) {
    val draft = ui.draft
    if (draft != null) {
        PromotionEditorContent(draft, actions, today)
        return
    }
    ScreenFrame(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        footer = { CuadraButton(stringResource(R.string.promo_new), actions::newPromotion, Modifier.fillMaxWidth().padding(bottom = 8.dp), kind = ButtonKind.PRIMARY, height = 48) },
    ) {
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                TitleBar(Modifier.padding(top = 16.dp), end = { CuadraButton(stringResource(R.string.back), onBack, height = 48) }) {
                    Text(stringResource(R.string.promo_title), style = MaterialTheme.typography.headlineMedium)
                }
            }
            item { Text(stringResource(R.string.promo_entry_hint), style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted) }
            if (rows.isEmpty()) item {
                Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.promo_empty), color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                }
            } else items(rows, key = { it.rule.id }) { r -> PromotionCard(r, actions) }
        }
    }
}

@Composable
private fun stateLabel(s: PromotionState): String = when (s) {
    PromotionState.ACTIVE -> stringResource(R.string.promo_state_ACTIVE)
    PromotionState.PAUSED -> stringResource(R.string.promo_state_PAUSED)
    PromotionState.SCHEDULED -> stringResource(R.string.promo_state_SCHEDULED)
    PromotionState.ENDED -> stringResource(R.string.promo_state_ENDED)
}

/** Una promoción en la lista: nombre, estado, «3 por C$ 100 · 4 productos», los productos y el interruptor activa / pausada. */
@Composable
private fun PromotionCard(r: PromotionRow, actions: PromotionsActions) {
    val toggle = stringResource(R.string.promo_toggle, r.rule.name)
    CuadraCard(onClick = { actions.edit(r.rule.id) }) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(r.rule.name, Modifier.weight(1f), fontWeight = FontWeight.ExtraBold, maxLines = 2, ellipsize = true)
                Switch(r.rule.active, { actions.toggleActive(r.rule.id, it) }, Modifier.semantics { contentDescription = toggle })
            }
            TagRow {
                val (fg, bg) = when (r.state) {
                    PromotionState.ACTIVE -> CuadraColors.Green to CuadraColors.GreenSoft
                    PromotionState.SCHEDULED -> CuadraColors.Orange to CuadraColors.Soft
                    else -> CuadraColors.Muted to CuadraColors.Soft
                }
                Tag(stateLabel(r.state), fg, bg)
                Tag(stringResource(R.string.promo_rule, r.rule.quantity, money(r.rule.priceMinor)), CuadraColors.Ink, CuadraColors.Soft)
                Tag(pluralStringResource(R.plurals.promo_products_count, r.rule.productIds.size, r.rule.productIds.size), CuadraColors.Ink, CuadraColors.Soft)
            }
            if (r.productNames.isNotEmpty()) Text(r.productNames.joinToString(", "), style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted, maxLines = 2, ellipsize = true)
            dates(r.rule.startsOn, r.rule.endsOn)?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted) }
        }
    }
}

@Composable
private fun dates(from: LocalDate?, to: LocalDate?): String? {
    if (from == null && to == null) return null
    val f = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    return listOfNotNull(from?.let { stringResource(R.string.promo_from, f.format(it)) }, to?.let { stringResource(R.string.promo_until, f.format(it)) }).joinToString(" · ")
}

/**
 * El editor: nombre, «N por X», el ejemplo calculado con el motor de la caja, activa o pausada, fechas opcionales y los productos (buscar o escanear en modo
 * continuo; cada lectura agrega uno y se ve en la lista con su ✕). Guardar y Cancelar quedan fijos abajo.
 */
@Composable
fun PromotionEditorContent(d: PromotionDraft, actions: PromotionsActions, today: LocalDate = LocalDate.now()) {
    val decimals = LocalMoney.current.decimals
    // Lector del equipo (Zebra / teclado): con el editor abierto, cada lectura agrega un producto.
    ScanTargetEffect { actions.onScanned(it) }
    if (d.scanning) BarcodeScannerDialog(onDismiss = actions::closeScanner, onCode = actions::onScanned, continuousOption = true, addedCount = d.scanAdded, startContinuous = true)
    LaunchedEffect(d.noticeTick) { if (d.notice != null) { delay(2_500); actions.dismissNotice() } }
    var picking by remember { mutableStateOf<Boolean?>(null) }   // true = desde, false = hasta
    ScreenFrame(
        Modifier.fillMaxSize().imePadding().padding(horizontal = 16.dp).testTag(TAG_PROMO_EDITOR),
        footer = {
            ButtonRow(Modifier.padding(bottom = 8.dp)) {
                CuadraButton(stringResource(R.string.cancel), actions::close, Modifier.share(1f), height = 48)
                CuadraButton(stringResource(R.string.save), actions::save, Modifier.share(1.4f), kind = ButtonKind.PRIMARY, height = 48)
            }
        },
    ) {
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                TitleBar(Modifier.padding(top = 16.dp)) { Text(stringResource(if (d.id == null) R.string.promo_new else R.string.promo_edit), style = MaterialTheme.typography.headlineMedium) }
            }
            item {
                val nameError = PromotionError.NAME in d.errors
                VoiceTextField(d.form.name, actions::setName, Modifier.fillMaxWidth(), singleLine = true, label = { Text(stringResource(R.string.promo_name)) },
                    placeholder = { Text(stringResource(R.string.promo_name_hint)) }, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences), isError = nameError,
                    supportingText = if (nameError) ({ Text(stringResource(R.string.promo_error_name)) }) else null)
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val qtyError = PromotionError.QUANTITY in d.errors
                    val priceError = PromotionError.PRICE in d.errors
                    NumberField(d.form.quantity, actions::setQuantity, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.promo_quantity), maxLines = 1) }, keyboardType = KeyboardType.Number,
                        isError = qtyError, supportingText = if (qtyError) ({ Text(stringResource(R.string.promo_error_quantity)) }) else null)
                    NumberField(d.form.price, actions::setPrice, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.promo_price), maxLines = 1) },
                        isError = priceError, supportingText = if (priceError) ({ Text(stringResource(R.string.promo_error_price)) }) else null)
                }
            }
            PromotionEditor.example(d.form, decimals, d.products.map { it.priceMinor })?.let { ex ->
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(stringResource(R.string.promo_example, ex.units.toInt(), money(ex.unitPriceMinor), money(ex.totalMinor)), Modifier.fillMaxWidth(),
                            color = if (ex.noBenefit) CuadraColors.Orange else CuadraColors.Green, fontWeight = FontWeight.ExtraBold)
                        if (ex.noBenefit) Text(stringResource(R.string.promo_no_benefit), style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Orange)
                    }
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.promo_active), fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.promo_active_hint), style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted)
                    }
                    Switch(d.form.active, actions::setActive)
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.promo_dates), fontWeight = FontWeight.Bold)
                    ChipFlow {
                        CuadraChip(stringResource(R.string.promo_dates_always), !d.withDates, { actions.setWithDates(false) })
                        CuadraChip(stringResource(R.string.promo_dates_some), d.withDates, { actions.setWithDates(true) })
                    }
                    if (d.withDates) {
                        val f = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
                        SplitRow(end = { LinkAction(stringResource(R.string.promo_pick_date), { picking = true }) }) {
                            Text(stringResource(R.string.promo_from, d.form.startsOn?.let { f.format(it) } ?: f.format(today)))
                        }
                        SplitRow(end = {
                            Row {
                                if (d.form.endsOn != null) LinkAction(stringResource(R.string.promo_clear_date), { actions.setEndsOn(null) }, color = CuadraColors.Red)
                                LinkAction(stringResource(R.string.promo_pick_date), { picking = false })
                            }
                        }) {
                            Text(stringResource(R.string.promo_until, d.form.endsOn?.let { f.format(it) } ?: stringResource(R.string.promo_no_end)))
                        }
                        if (PromotionError.DATES in d.errors) Text(stringResource(R.string.promo_error_dates), color = CuadraColors.Red, fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.promo_dates_hint), style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted)
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SectionLabel(stringResource(R.string.promo_products) + " · " + d.products.size)
                    Text(stringResource(R.string.promo_products_hint), style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted)
                    if (PromotionError.PRODUCTS in d.errors) Text(stringResource(R.string.promo_products_none), color = CuadraColors.Red, fontWeight = FontWeight.Bold)
                    CuadraButton(stringResource(R.string.promo_scan), actions::openScanner, Modifier.fillMaxWidth(), kind = ButtonKind.DARK, height = 48)
                    d.notice?.let { n -> NoticeLine(n) }
                    VoiceTextField(d.query, actions::setQuery, Modifier.fillMaxWidth(), singleLine = true, label = { Text(stringResource(R.string.promo_search), maxLines = 1) })
                }
            }
            if (d.query.isNotBlank()) {
                if (d.results.isEmpty()) item { Text(stringResource(R.string.promo_no_results), color = CuadraColors.Muted) }
                else items(d.results, key = { "r-" + it.id }) { p -> ResultRow(p, p.id in d.form.productIds) { actions.addProduct(p) } }
            }
            items(d.products.asReversed(), key = { "p-" + it.id }) { p -> ChosenRow(p) { actions.removeProduct(p.id) } }
            if (d.id != null) item { CuadraButton(stringResource(R.string.promo_delete), actions::askDelete, Modifier.fillMaxWidth(), kind = ButtonKind.DANGER, height = 48) }
        }
    }
    picking?.let { start ->
        PromoDateDialog(if (start) d.form.startsOn ?: today else d.form.endsOn ?: d.form.startsOn ?: today, { date -> if (start) actions.setStartsOn(date) else actions.setEndsOn(date) }) { picking = null }
    }
    if (d.confirmDelete) {
        Sheet(actions::cancelDelete, actions = {
            ButtonRow {
                CuadraButton(stringResource(R.string.cancel), actions::cancelDelete, Modifier.share(1f))
                CuadraButton(stringResource(R.string.promo_delete), actions::delete, Modifier.share(1f), kind = ButtonKind.DANGER)
            }
        }) { Text(stringResource(R.string.promo_delete_confirm, d.form.name), fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun NoticeLine(n: PromoNotice) {
    val (text, color) = when (n) {
        is PromoNotice.Added -> stringResource(R.string.promo_added, n.name) to CuadraColors.Green
        is PromoNotice.Already -> stringResource(R.string.promo_already, n.name) to CuadraColors.Orange
        is PromoNotice.NotEligible -> stringResource(R.string.promo_not_eligible, n.name) to CuadraColors.Orange
        is PromoNotice.UnknownCode -> stringResource(R.string.promo_unknown_code, n.code) to CuadraColors.Red
    }
    Text(text, fontWeight = FontWeight.ExtraBold, color = color, maxLines = 2, ellipsize = true)
}

/** Un resultado de la búsqueda: tocarlo lo agrega (si ya está, se ve marcado). */
@Composable
private fun ResultRow(p: ProductEntity, chosen: Boolean, onAdd: () -> Unit) {
    CuadraCard(onClick = onAdd, color = if (chosen) CuadraColors.GreenSoft else CuadraColors.Surface) {
        SplitRow(end = { MoneyText(priceSummary(p), fontWeight = FontWeight.Bold) }) {
            Column {
                Text((if (chosen) "✓ " else "+ ") + p.name + (p.variant?.let { " · $it" } ?: ""), fontWeight = FontWeight.Bold, maxLines = 2, ellipsize = true)
                listOfNotNull(p.shortCode, p.barcode).joinToString(" · ").takeIf { it.isNotEmpty() }?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted, maxLines = 1, ellipsize = true) }
            }
        }
    }
}

/** Un producto elegido, con su ✕ para quitarlo. */
@Composable
private fun ChosenRow(p: ProductEntity, onRemove: () -> Unit) {
    val description = stringResource(R.string.promo_remove, p.name)
    CuadraCard(Modifier.testTag(TAG_PROMO_PRODUCT), padding = androidx.compose.foundation.layout.PaddingValues(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(p.name + (p.variant?.let { " · $it" } ?: ""), fontWeight = FontWeight.Bold, maxLines = 2, ellipsize = true)
                MoneyText(money(p.priceMinor), style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted)
            }
            Box(Modifier.size(48.dp).clickable(role = Role.Button, onClick = onRemove).semantics { contentDescription = description }, contentAlignment = Alignment.Center) {
                Text("✕", style = MaterialTheme.typography.titleMedium, color = CuadraColors.Red, maxLines = 1, minScale = 0.6f)
            }
        }
    }
}

/** Elegir un día (desde / hasta), en el calendario de Material dentro del diálogo de la app. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PromoDateDialog(initial: LocalDate, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val utc = java.time.ZoneOffset.UTC
    val state = androidx.compose.material3.rememberDatePickerState(initialSelectedDateMillis = initial.atStartOfDay(utc).toInstant().toEpochMilli())
    AppDialog(onDismissRequest = onDismiss, usePlatformDefaultWidth = false) {
        androidx.compose.material3.Surface(Modifier.padding(vertical = 8.dp).fillMaxWidth(), shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            val dens = androidx.compose.ui.platform.LocalDensity.current
            val shrink = (androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp / 392f).coerceAtMost(1f)
            Column {
                androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(dens.density * shrink, dens.fontScale.coerceAtMost(1.15f))) {
                    androidx.compose.material3.DatePicker(state, Modifier.weight(1f, fill = false), showModeToggle = false,
                        title = { Text(stringResource(R.string.promo_date_title), Modifier.padding(start = 24.dp, end = 12.dp, top = 16.dp), style = MaterialTheme.typography.titleMedium) })
                }
                ButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    CuadraButton(stringResource(R.string.cancel), onDismiss, Modifier.share(1f), height = 48)
                    CuadraButton(stringResource(R.string.save), {
                        state.selectedDateMillis?.let { onPick(java.time.Instant.ofEpochMilli(it).atZone(utc).toLocalDate()) }
                        onDismiss()
                    }, Modifier.share(1f), kind = ButtonKind.DARK, enabled = state.selectedDateMillis != null, height = 48)
                }
            }
        }
    }
}

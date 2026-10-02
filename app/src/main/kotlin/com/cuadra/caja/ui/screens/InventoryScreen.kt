package com.cuadra.caja.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.platform.testTag
import com.cuadra.caja.ui.common.TAG_STICKY
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.RadioButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.cuadra.caja.data.local.CategoryEntity
import com.cuadra.caja.domain.Pricing
import com.cuadra.caja.domain.ProductEditor
import com.cuadra.caja.domain.ProductError
import com.cuadra.caja.domain.ProductForm
import com.cuadra.caja.ui.common.BarcodeScannerDialog
import com.cuadra.caja.ui.common.BarcodeField
import com.cuadra.caja.ui.common.ScanFieldIcon
import com.cuadra.caja.ui.common.Text
import com.cuadra.caja.ui.common.MoneyText
import com.cuadra.caja.ui.common.NumberField
import com.cuadra.caja.ui.common.SplitRow
import com.cuadra.caja.ui.common.TitleBar
import com.cuadra.caja.ui.common.ChipFlow
import com.cuadra.caja.ui.common.ButtonRow
import com.cuadra.caja.ui.common.ScreenFrame
import com.cuadra.caja.ui.common.Sheet
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.cuadra.caja.R
import com.cuadra.caja.data.local.ProductStock
import com.cuadra.caja.data.local.StockMovementEntity
import com.cuadra.caja.data.repo.StockFilter
import com.cuadra.caja.data.remote.ProductHistoryEntryDto
import com.cuadra.caja.domain.HistoryStrings
import com.cuadra.caja.domain.ProductHistory
import com.cuadra.caja.ui.HistoryState
import com.cuadra.caja.ui.CountDraft2
import com.cuadra.caja.ui.InventoryActions
import com.cuadra.caja.ui.InventoryUi
import com.cuadra.caja.ui.InventoryViewModel
import com.cuadra.caja.ui.ProductDetail
import com.cuadra.caja.ui.ProductEditorDraft
import com.cuadra.caja.ui.StockAction
import com.cuadra.caja.ui.common.ButtonKind
import com.cuadra.caja.ui.common.CuadraButton
import com.cuadra.caja.ui.common.CuadraCard
import com.cuadra.caja.ui.common.CuadraChip
import com.cuadra.caja.ui.common.SectionLabel
import com.cuadra.caja.ui.common.Tag
import com.cuadra.caja.ui.common.money
import com.cuadra.caja.ui.common.VoiceTextField
import com.cuadra.caja.ui.theme.CuadraColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
internal fun unitLabel(unit: String): String = when (unit) {
    "LB" -> stringResource(R.string.unit_lb)
    "KG" -> stringResource(R.string.unit_kg)
    "L" -> stringResource(R.string.unit_l)
    "M" -> stringResource(R.string.unit_m)
    else -> stringResource(R.string.unit_unit)
}

/** El precio como se lee en listas: «C$ 50.00», o «Precio abierto» (con «Sugerido C$ 50.00» si tiene). Nunca «C$ 0.00» para un precio que no existe. */
@Composable
internal fun priceSummary(p: com.cuadra.caja.data.local.ProductEntity): String =
    if (p.pricing != Pricing.OPEN) money(p.priceMinor)
    else stringResource(R.string.pricing_OPEN) + if (p.priceMinor > 0) " · " + stringResource(R.string.open_price_suggested, money(p.priceMinor)) else ""

@Composable
private fun movementLabel(kind: String): String = when (kind) {
    "INITIAL" -> stringResource(R.string.mv_INITIAL)
    "PURCHASE" -> stringResource(R.string.mv_PURCHASE)
    "PURCHASE_REVERSAL" -> stringResource(R.string.mv_PURCHASE_REVERSAL)
    "SALE" -> stringResource(R.string.mv_SALE)
    "SALE_REVERSAL" -> stringResource(R.string.mv_SALE_REVERSAL)
    "ADJUSTMENT" -> stringResource(R.string.mv_ADJUSTMENT)
    "DAMAGE" -> stringResource(R.string.mv_DAMAGE)
    else -> stringResource(R.string.mv_RETURN)
}

/**
 * Existencias: qué hay, qué falta y qué está mal contado. Un producto sin control simplemente no lleva número.
 * Con `catalogOnly` (negocio sin módulo de inventario) es solo el catálogo: productos con precio, costo y ganancia, sin nada de existencias.
 */
@Composable
fun InventoryScreen(vm: InventoryViewModel, timezone: String, catalogOnly: Boolean = false, onPromotions: (() -> Unit)? = null, onBack: () -> Unit) {
    val ui by vm.ui.collectAsState()
    val items by vm.items.collectAsState()
    val review by vm.reviewCount.collectAsState()
    val detail by vm.detail.collectAsState()
    val canDelete by vm.canDelete.collectAsState()
    // Si el módulo de inventario se apagó, un filtro de existencias que hubiera quedado puesto no debe esconder productos.
    androidx.compose.runtime.LaunchedEffect(catalogOnly) { if (catalogOnly) vm.setFilter(StockFilter.ALL) }
    InventoryContent(ui, items, review, detail, canDelete, timezone, catalogOnly, vm, onBack, onPromotions)
}

/** El inventario sin ViewModel (estado + acciones): es lo que dibuja la guardia de diseño. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun InventoryContent(
    ui: InventoryUi, items: List<ProductStock>, review: Int, detail: ProductDetail?, canDelete: Boolean, timezone: String, catalogOnly: Boolean,
    actions: InventoryActions, onBack: () -> Unit,
    /** Productos › Promociones (dueño y admins); nulo = no se ofrece. */
    onPromotions: (() -> Unit)? = null,
) {
    ScreenFrame(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        footer = { CuadraButton(stringResource(R.string.inventory_new_product), actions::newProduct, Modifier.fillMaxWidth().padding(bottom = 8.dp), kind = ButtonKind.PRIMARY, height = 48) },
    ) {
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                TitleBar(Modifier.padding(top = 16.dp), end = { CuadraButton(stringResource(R.string.back), onBack, height = 48) }) {
                    Text(stringResource(if (catalogOnly) R.string.catalog_title else R.string.inventory_title), style = MaterialTheme.typography.headlineMedium)
                }
            }
            if (onPromotions != null) item {
                CuadraCard(onClick = onPromotions) {
                    SplitRow(end = { Text("›", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, maxLines = 1) }) {
                        Column {
                            Text(stringResource(R.string.promo_title), fontWeight = FontWeight.ExtraBold)
                            Text(stringResource(R.string.promo_entry_hint), style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted)
                        }
                    }
                }
            }
            stickyHeader {
                Box(Modifier.fillMaxWidth().testTag(TAG_STICKY).background(CuadraColors.Bg).padding(vertical = 2.dp)) {
                    VoiceTextField(ui.query, actions::setQuery, Modifier.fillMaxWidth(), singleLine = true, label = { Text(stringResource(R.string.inventory_search), maxLines = 1) })
                }
            }
            item {
                if (catalogOnly && canDelete) ChipFlow {
                    CuadraChip(stringResource(R.string.inventory_filter_all), ui.filter != StockFilter.INACTIVE, { actions.setFilter(StockFilter.ALL) })
                    CuadraChip(stringResource(R.string.inventory_filter_inactive), ui.filter == StockFilter.INACTIVE, { actions.setFilter(StockFilter.INACTIVE) })
                }
                if (!catalogOnly) ChipFlow {
                    CuadraChip(stringResource(R.string.inventory_filter_all), ui.filter == StockFilter.ALL, { actions.setFilter(StockFilter.ALL) })
                    CuadraChip(stringResource(R.string.inventory_filter_tracked), ui.filter == StockFilter.TRACKED, { actions.setFilter(StockFilter.TRACKED) })
                    CuadraChip(stringResource(R.string.inventory_filter_review) + if (review > 0) " ($review)" else "", ui.filter == StockFilter.REVIEW, { actions.setFilter(StockFilter.REVIEW) })
                    CuadraChip(stringResource(R.string.inventory_filter_untracked), ui.filter == StockFilter.UNTRACKED, { actions.setFilter(StockFilter.UNTRACKED) })
                    if (canDelete) CuadraChip(stringResource(R.string.inventory_filter_inactive), ui.filter == StockFilter.INACTIVE, { actions.setFilter(StockFilter.INACTIVE) })
                }
            }
            if (items.isEmpty()) item {
                Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) { Text(stringResource(R.string.inventory_empty), color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center) }
            } else items(items, key = { it.product.id }) { StockRow(it, catalogOnly) { actions.select(it.product.id) } }
        }
    }
    detail?.let { DetailSheet(it, ui.history, canDelete, timezone, catalogOnly, actions) }
    ui.confirmActive?.let { ConfirmActiveDialog(it, actions) }
    ui.editor?.let { EditorDialog(it, ui.categories, catalogOnly, actions) }
    if (!catalogOnly) ui.count?.let { CountDialog(it, actions) }
}

@Composable
private fun StockRow(s: ProductStock, catalogOnly: Boolean, onClick: () -> Unit) {
    val p = s.product
    val low = p.trackStock && p.minStockMilli != null && s.stockNowMilli <= p.minStockMilli
    val negative = p.trackStock && s.stockNowMilli < 0
    CuadraCard(Modifier.fillMaxWidth(), onClick = onClick) {
        SplitRow(end = {
            if (catalogOnly) Unit
            else if (p.trackStock) Column(horizontalAlignment = Alignment.End) {
                MoneyText(
                    qtyText(s.stockNowMilli) + " " + unitLabel(p.unit), fontWeight = FontWeight.ExtraBold,
                    color = if (negative) CuadraColors.Red else if (low) CuadraColors.Orange else CuadraColors.Ink,
                )
                if (negative) Tag(stringResource(R.string.inventory_negative), CuadraColors.Red, CuadraColors.RedSoft)
                else if (low) Tag(stringResource(R.string.inventory_low), CuadraColors.Orange, CuadraColors.OrangeSoft)
            } else Tag(stringResource(R.string.inventory_untracked_tag), CuadraColors.Ink, CuadraColors.Soft)
        }) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(p.name + (p.variant?.let { " · $it" } ?: ""), fontWeight = FontWeight.ExtraBold, maxLines = 2, ellipsize = true)
                Text(
                    listOfNotNull(priceSummary(p), p.costMinor?.let { stringResource(R.string.inventory_cost_short, money(it)) }).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted,
                )
            }
        }
    }
}

@Composable
fun DetailSheet(d: ProductDetail, history: HistoryState?, canDelete: Boolean, timezone: String, catalogOnly: Boolean, actions: InventoryActions) {
    val p = d.stock.product
    val zone = runCatching { ZoneId.of(timezone) }.getOrDefault(ZoneId.systemDefault())
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val time = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withLocale(locale).withZone(zone)
    Sheet({ actions.select(null) }, actions = { CuadraButton(stringResource(R.string.close), { actions.select(null) }, Modifier.fillMaxWidth(), kind = ButtonKind.DARK) }) {
        Text(p.name + (p.variant?.let { " · $it" } ?: ""), style = MaterialTheme.typography.headlineMedium)
        if (catalogOnly) Unit
        else if (p.trackStock) {
            val negative = d.stock.stockNowMilli < 0
            MoneyText(
                qtyText(d.stock.stockNowMilli) + " " + unitLabel(p.unit), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold,
                color = if (negative) CuadraColors.Red else CuadraColors.Ink,
            )
            if (negative) Text(stringResource(R.string.inventory_negative_hint), style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Red)
            p.minStockMilli?.let { Text(stringResource(R.string.inventory_min, qtyText(it) + " " + unitLabel(p.unit)), style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted) }
        } else Text(stringResource(R.string.inventory_untracked_hint), style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted)

        // Costo y ganancia: con el costo que tenía cada línea al venderse, no con el de hoy.
        CuadraCard {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (p.pricing == Pricing.OPEN) Line(stringResource(if (p.priceMinor > 0) R.string.inventory_price_suggested else R.string.inventory_price), if (p.priceMinor > 0) money(p.priceMinor) else stringResource(R.string.pricing_OPEN))
                else Line(stringResource(R.string.inventory_price), money(p.priceMinor))
                Line(stringResource(R.string.inventory_cost), p.costMinor?.let { money(it) } ?: "—")
                p.costMinor?.takeIf { p.priceMinor > 0 && p.pricing != Pricing.OPEN }?.let { Line(stringResource(R.string.inventory_margin), "${(p.priceMinor - it) * 100 / p.priceMinor}%") }
                Text(stringResource(R.string.inventory_profit_30), style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted, modifier = Modifier.padding(top = 6.dp))
                if (d.profit.quantityMilli > 0) {
                    Line(stringResource(R.string.inventory_sold), qtyText(d.profit.quantityMilli) + " " + unitLabel(p.unit))
                    Line(stringResource(R.string.inventory_revenue), money(d.profit.revenueMinor))
                    if (d.profit.costedRevenueMinor > 0) Line(stringResource(R.string.inventory_profit), money(d.profit.costedRevenueMinor - d.profit.costMinor), bold = true)
                    else Text(stringResource(R.string.inventory_no_cost_hint), style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted)
                } else Text(stringResource(R.string.inventory_no_sales), style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted)
            }
        }
        if (p.trackStock && !catalogOnly) {
            ButtonRow {
                CuadraButton(stringResource(R.string.inventory_count), { actions.openCount(p.id, StockAction.COUNT) }, Modifier.share(1f), kind = ButtonKind.PRIMARY, height = 48)
                CuadraButton(stringResource(R.string.inventory_damage), { actions.openCount(p.id, StockAction.DAMAGE) }, Modifier.share(1f), height = 48)
                CuadraButton(stringResource(R.string.inventory_return), { actions.openCount(p.id, StockAction.RETURN) }, Modifier.share(1f), height = 48)
            }
        }
        CuadraButton(stringResource(R.string.inventory_edit), { actions.edit(p) }, Modifier.fillMaxWidth(), height = 48)
        // Dar de baja y reactivar: solo dueño y administrador. El cajero ni lo ve.
        if (canDelete) {
            if (p.active) CuadraButton(stringResource(R.string.product_deactivate), { actions.askActive(false) }, Modifier.fillMaxWidth(), kind = ButtonKind.DANGER, height = 48)
            else CuadraButton(stringResource(R.string.product_reactivate), { actions.askActive(true) }, Modifier.fillMaxWidth(), kind = ButtonKind.PRIMARY, height = 48)
        }
        if (d.movements.isNotEmpty() && !catalogOnly) {
            SectionLabel(stringResource(R.string.inventory_history))
            d.movements.take(12).forEach { MovementRow(it, p.unit, time) }
        }
        androidx.compose.runtime.LaunchedEffect(p.id, p.rev) { actions.loadHistory(p.id) }
        HistorySection(history, zone, time, { actions.loadHistory(p.id) })
    }
}

@Composable
fun ConfirmActiveDialog(active: Boolean, actions: InventoryActions) {
    Sheet(actions::cancelActive, actions = {
        ButtonRow {
            CuadraButton(stringResource(R.string.cancel), actions::cancelActive, Modifier.share(1f))
            CuadraButton(stringResource(if (active) R.string.product_reactivate else R.string.product_deactivate), actions::confirmActive, Modifier.share(1.4f), kind = if (active) ButtonKind.PRIMARY else ButtonKind.DANGER)
        }
    }) {
        Text(stringResource(if (active) R.string.product_reactivate else R.string.product_deactivate), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(if (active) R.string.product_reactivate_confirm else R.string.product_deactivate_confirm))
    }
}

/** Quién cambió qué y cuándo. Necesita conexión: sin ella lo dice con claridad y no falla. */
@Composable
private fun HistorySection(state: HistoryState?, zone: ZoneId, time: DateTimeFormatter, onRetry: () -> Unit) {
    SectionLabel(stringResource(R.string.product_history_title))
    when (state) {
        null, HistoryState.Loading -> Text(stringResource(R.string.history_loading), style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted)
        HistoryState.Offline -> Text(stringResource(R.string.history_offline), style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted)
        HistoryState.Error -> {
            Text(stringResource(R.string.history_error), style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted)
            CuadraButton(stringResource(R.string.history_retry), onRetry, Modifier.fillMaxWidth(), height = 48)
        }
        is HistoryState.Loaded -> {
            if (state.entries.isEmpty()) Text(stringResource(R.string.history_none), style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted)
            val strings = rememberHistoryStrings()
            val fmt = com.cuadra.caja.ui.common.LocalMoney.current
            state.entries.forEach { HistoryEntryRow(it, strings, fmt::format, time) }
        }
    }
}

@Composable
private fun HistoryEntryRow(e: ProductHistoryEntryDto, strings: HistoryStrings, money: (Long) -> String, time: DateTimeFormatter) {
    val at = runCatching { Instant.parse(e.at) }.getOrNull()
    val role = e.actorRole?.let { stringResource(roleRes(it)) }
    val who = e.actorName?.let { n -> if (role != null) stringResource(R.string.history_who, n, role) else n } ?: "—"
    val whenText = at?.let {
        // El reloj del teléfono puede ir unos segundos atrás del servidor: nunca "dentro de 0 minutos".
        val now = System.currentTimeMillis()
        val rel = if (kotlin.math.abs(now - it.toEpochMilli()) < 60_000) stringResource(R.string.history_just_now)
        else android.text.format.DateUtils.getRelativeTimeSpanString(it.toEpochMilli(), maxOf(now, it.toEpochMilli()), android.text.format.DateUtils.MINUTE_IN_MILLIS).toString()
        stringResource(R.string.history_when, rel, time.format(it))
    } ?: e.at
    CuadraCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(who, fontWeight = FontWeight.ExtraBold)
            Text(whenText, style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted)
            ProductHistory.lines(e, strings, money).forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

@Composable
private fun rememberHistoryStrings(): HistoryStrings {
    val res = androidx.compose.ui.platform.LocalContext.current.resources
    val cfg = androidx.compose.ui.platform.LocalConfiguration.current
    return androidx.compose.runtime.remember(cfg) {
        object : HistoryStrings {
            override fun fieldName(field: String) = when (field) {
                "name" -> res.getString(R.string.hf_name); "variant" -> res.getString(R.string.hf_variant); "barcode" -> res.getString(R.string.hf_barcode)
                "shortCode" -> res.getString(R.string.hf_shortCode); "categoryId" -> res.getString(R.string.hf_categoryId); "unit" -> res.getString(R.string.hf_unit)
                "pricing" -> res.getString(R.string.hf_pricing); "priceMinor" -> res.getString(R.string.hf_priceMinor); "costMinor" -> res.getString(R.string.hf_costMinor)
                "isQuick" -> res.getString(R.string.hf_isQuick); "quickPosition" -> res.getString(R.string.hf_quickPosition); "color" -> res.getString(R.string.hf_color)
                "trackStock" -> res.getString(R.string.hf_trackStock); "minStockMilli" -> res.getString(R.string.hf_minStockMilli); else -> field
            }
            override fun unitName(code: String) = when (code) {
                "LB" -> res.getString(R.string.unit_lb); "KG" -> res.getString(R.string.unit_kg); "L" -> res.getString(R.string.unit_l)
                "M" -> res.getString(R.string.unit_m); else -> res.getString(R.string.unit_unit)
            }
            override fun pricingName(code: String) = when (code) { "FIXED" -> res.getString(R.string.pricing_FIXED); "BY_WEIGHT" -> res.getString(R.string.pricing_BY_WEIGHT); "OPEN" -> res.getString(R.string.pricing_OPEN); else -> code }
            override val deactivated = res.getString(R.string.history_deactivated)
            override val reactivated = res.getString(R.string.history_reactivated)
            override val yes = res.getString(R.string.history_yes)
            override val no = res.getString(R.string.history_no)
            override val empty = res.getString(R.string.history_blank)
            override fun created(who: String, price: String?) =
                if (price != null) res.getString(R.string.history_created, who, price) else res.getString(R.string.history_created_noprice, who)
            override fun changed(field: String, from: String, to: String) = res.getString(R.string.history_changed, field, from, to)
        }
    }
}

@Composable
private fun Line(label: String, value: String, bold: Boolean = false) {
    SplitRow(end = { MoneyText(value, fontWeight = if (bold) FontWeight.ExtraBold else FontWeight.Bold) }) {
        Text(label, fontWeight = if (bold) FontWeight.ExtraBold else FontWeight.Normal)
    }
}

@Composable
private fun MovementRow(m: StockMovementEntity, unit: String, time: DateTimeFormatter) {
    val sign = if (m.quantityMilli > 0) "+" else ""
    SplitRow(end = { MoneyText(sign + qtyText(m.quantityMilli) + " " + unitLabel(unit), fontWeight = FontWeight.ExtraBold, color = if (m.quantityMilli < 0) CuadraColors.Red else if (m.quantityMilli > 0) CuadraColors.Green else CuadraColors.Ink) }) {
        Column {
            Text(movementLabel(m.kind), fontWeight = FontWeight.Bold)
            Text(listOfNotNull(time.format(Instant.ofEpochMilli(m.occurredAt)), m.createdByName, m.note).joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted, maxLines = 3, ellipsize = true)
        }
    }
}

@Composable
fun CountDialog(d: CountDraft2, actions: InventoryActions) {
    val ok = com.cuadra.caja.domain.Money3.parse(d.amount, 3)?.let { it > 0 || d.kind == StockAction.COUNT } == true
    Sheet(actions::closeCount, actions = {
        ButtonRow {
            // Saltar el conteo inicial deja el producto con control pero en cero: se puede contar después.
            CuadraButton(stringResource(if (d.initial) R.string.inventory_later else R.string.cancel), actions::closeCount, Modifier.share(1f))
            CuadraButton(stringResource(R.string.save), actions::confirmCount, Modifier.share(1.4f), kind = ButtonKind.PRIMARY, enabled = ok)
        }
    }) {
        val title = when {
            d.initial -> R.string.inventory_initial_title
            d.kind == StockAction.COUNT -> R.string.inventory_count_title
            d.kind == StockAction.DAMAGE -> R.string.inventory_damage_title
            else -> R.string.inventory_return_title
        }
        Text(stringResource(title), style = MaterialTheme.typography.headlineMedium)
        Text(
            stringResource(if (d.initial) R.string.inventory_initial_hint else if (d.kind == StockAction.COUNT) R.string.inventory_count_hint else if (d.kind == StockAction.DAMAGE) R.string.inventory_damage_hint else R.string.inventory_return_hint),
            style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted,
        )
        NumberField(d.amount, { actions.updateCount(d.copy(amount = it.take(12))) }, label = { Text(stringResource(if (d.kind == StockAction.COUNT) R.string.inventory_counted else R.string.inventory_quantity), maxLines = 1) })
        if (!d.initial) VoiceTextField(d.note, { actions.updateCount(d.copy(note = it.take(200))) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(stringResource(R.string.inventory_note)) })
    }
}

/**
 * Editor de producto (nuevo o existente). Orden: nombre, código (teclear o escanear; avisa si otro producto ya lo tiene), código corto, variante,
 * categoría, cómo se cobra (fijo / por peso / abierto), precio, costo, «Rápidos» y, si el negocio lleva inventario, control de existencia.
 * Todos los campos tienen etiqueta visible; el contenido se desplaza y «Guardar» queda fijo abajo.
 */
@Composable
fun EditorDialog(d: ProductEditorDraft, categories: List<CategoryEntity>, catalogOnly: Boolean, actions: InventoryActions) {
    val f = d.form
    fun set(new: ProductForm) = actions.updateEditor(d.copy(form = new))
    var scanning by remember { mutableStateOf(false) }
    // Un lector físico (DataWedge) llena el campo del código aunque no tenga el foco.
    com.cuadra.caja.ui.common.ScanTargetEffect { code -> set(f.copy(barcode = code.trim().take(ProductEditor.MAX_BARCODE))) }
    if (scanning) BarcodeScannerDialog(onDismiss = { scanning = false }, onCode = { code -> set(f.copy(barcode = code.trim().take(ProductEditor.MAX_BARCODE))) ; scanning = false })
    Sheet(actions::closeEditor, actions = {
        ButtonRow {
            CuadraButton(stringResource(R.string.cancel), actions::closeEditor, Modifier.share(1f))
            CuadraButton(stringResource(R.string.save), actions::saveEditor, Modifier.share(1.4f), kind = ButtonKind.PRIMARY)
        }
    }) {
        Text(stringResource(if (d.id == null) R.string.inventory_new_product else R.string.inventory_edit), style = MaterialTheme.typography.headlineMedium)
        val nameError = ProductError.NAME_REQUIRED in d.errors
        VoiceTextField(f.name, { set(f.copy(name = it.take(ProductEditor.MAX_NAME))) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(stringResource(R.string.inventory_name)) },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences), isError = nameError,
            supportingText = if (nameError) ({ Text(stringResource(R.string.product_error_name)) }) else null)
        // Código de barras: teclado numérico y, con «ABC», de letras (Code 128/39); si otro producto ya lo tiene se dice antes de guardar.
        val taken = d.barcodeOwner != null && f.barcode.isNotBlank()
        BarcodeField(
            f.barcode, { set(f.copy(barcode = it.take(ProductEditor.MAX_BARCODE))) }, { scanning = true }, isError = taken,
            supportingText = if (taken) ({ Text(stringResource(R.string.product_barcode_taken, d.barcodeOwner.orEmpty()), color = CuadraColors.Red) }) else null,
        )
        NumberField(f.shortCode, { set(f.copy(shortCode = it.take(ProductEditor.MAX_SHORT_CODE))) }, label = { Text(stringResource(R.string.product_short_code), maxLines = 1) }, keyboardType = KeyboardType.Ascii)
        VoiceTextField(f.variant, { set(f.copy(variant = it.take(ProductEditor.MAX_VARIANT))) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(stringResource(R.string.product_variant)) },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))

        Text(stringResource(R.string.product_category), fontWeight = FontWeight.Bold)
        ChipFlow {
            CuadraChip(stringResource(R.string.product_category_none), f.categoryId == null, { set(f.copy(categoryId = null)) })
            categories.forEach { c -> CuadraChip(c.name, f.categoryId == c.id, { set(f.copy(categoryId = c.id)) }, userContent = true) }
            CuadraChip(stringResource(R.string.product_category_new), d.newCategory != null, { actions.updateEditor(d.copy(newCategory = if (d.newCategory == null) "" else null)) })
        }
        d.newCategory?.let { name ->
            VoiceTextField(name, { actions.updateEditor(d.copy(newCategory = it.take(60))) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(stringResource(R.string.product_category_name)) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
            CuadraButton(stringResource(R.string.product_category_add), actions::confirmNewCategory, Modifier.fillMaxWidth(), height = 48, enabled = name.isNotBlank())
        }

        Text(stringResource(R.string.product_pricing_title), fontWeight = FontWeight.Bold)
        PricingOption(R.string.pricing_FIXED, R.string.pricing_FIXED_hint, f.pricing == Pricing.FIXED) { set(f.copy(pricing = Pricing.FIXED)) }
        PricingOption(R.string.pricing_BY_WEIGHT, R.string.pricing_BY_WEIGHT_hint, f.pricing == Pricing.BY_WEIGHT) { set(f.copy(pricing = Pricing.BY_WEIGHT)) }
        PricingOption(R.string.pricing_OPEN_option, R.string.pricing_OPEN_hint, f.pricing == Pricing.OPEN) { set(f.copy(pricing = Pricing.OPEN)) }
        if (f.pricing == Pricing.BY_WEIGHT) {
            Text(stringResource(R.string.inventory_unit), fontWeight = FontWeight.Bold)
            ChipFlow { Pricing.WEIGHT_UNITS.forEach { u -> CuadraChip(unitLabel(u), f.unit == u, { set(f.copy(unit = u)) }) } }
        }
        val priceLabel = when (f.pricing) {
            Pricing.BY_WEIGHT -> stringResource(R.string.product_price_per, unitLabel(f.unit))
            Pricing.OPEN -> stringResource(R.string.product_price_suggested)
            else -> stringResource(R.string.inventory_price)
        }
        val priceError = ProductError.PRICE_REQUIRED in d.errors || ProductError.PRICE_INVALID in d.errors
        NumberField(
            f.price, { set(f.copy(price = it.take(14))) }, isError = priceError,
            label = { Text(priceLabel, maxLines = 1) },
            supportingText = if (priceError) ({ Text(stringResource(if (ProductError.PRICE_REQUIRED in d.errors) R.string.product_error_price else R.string.product_error_amount)) }) else null,
        )
        val costError = ProductError.COST_INVALID in d.errors
        NumberField(f.cost, { set(f.copy(cost = it.take(14))) }, label = { Text(stringResource(R.string.product_cost_optional), maxLines = 1) }, isError = costError,
            supportingText = if (costError) ({ Text(stringResource(R.string.product_error_amount)) }) else null)

        ToggleRow(R.string.product_quick, null, f.isQuick) { set(f.copy(isQuick = it)) }
        // En modo catálogo no se toca el control de existencia: se conserva el que ya tenía el producto.
        if (!catalogOnly) ToggleRow(R.string.inventory_track, R.string.inventory_track_hint, f.trackStock) { set(f.copy(trackStock = it)) }
        if (f.trackStock && !catalogOnly) {
            val minError = ProductError.MIN_STOCK_INVALID in d.errors
            NumberField(f.minStock, { set(f.copy(minStock = it.take(12))) }, label = { Text(stringResource(R.string.inventory_min_label), maxLines = 1) }, isError = minError,
                supportingText = if (minError) ({ Text(stringResource(R.string.product_error_amount)) }) else null)
        }
        if (d.errors.isNotEmpty()) Text(stringResource(R.string.inventory_editor_error), color = CuadraColors.Red, fontWeight = FontWeight.Bold)
    }
}

/** Una de las tres formas de cobrar: título y una línea que la explica; toda la tarjeta se toca. */
@Composable
private fun PricingOption(@androidx.annotation.StringRes title: Int, @androidx.annotation.StringRes helper: Int, selected: Boolean, onClick: () -> Unit) {
    CuadraCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RadioButton(selected, onClick = null)
            Column(Modifier.weight(1f)) {
                Text(stringResource(title), fontWeight = FontWeight.Bold)
                Text(stringResource(helper), style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted)
            }
        }
    }
}

@Composable
private fun ToggleRow(@androidx.annotation.StringRes title: Int, @androidx.annotation.StringRes hint: Int?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(title), fontWeight = FontWeight.Bold)
            if (hint != null) Text(stringResource(hint), style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted)
        }
        Switch(checked, onChange)
    }
}

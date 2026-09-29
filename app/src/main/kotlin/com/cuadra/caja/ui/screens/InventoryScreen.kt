package com.cuadra.caja.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.cuadra.caja.ui.CountDraft2
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
fun InventoryScreen(vm: InventoryViewModel, timezone: String, catalogOnly: Boolean = false, onBack: () -> Unit) {
    val ui by vm.ui.collectAsState()
    val items by vm.items.collectAsState()
    val review by vm.reviewCount.collectAsState()
    val detail by vm.detail.collectAsState()
    // Si el módulo de inventario se apagó, un filtro de existencias que hubiera quedado puesto no debe esconder productos.
    androidx.compose.runtime.LaunchedEffect(catalogOnly) { if (catalogOnly) vm.setFilter(StockFilter.ALL) }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(if (catalogOnly) R.string.catalog_title else R.string.inventory_title), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            CuadraButton(stringResource(R.string.back), onBack, height = 44)
        }
        VoiceTextField(ui.query, vm::setQuery, Modifier.fillMaxWidth(), singleLine = true, label = { Text(stringResource(R.string.inventory_search)) })
        if (!catalogOnly) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CuadraChip(stringResource(R.string.inventory_filter_all), ui.filter == StockFilter.ALL, { vm.setFilter(StockFilter.ALL) })
            CuadraChip(stringResource(R.string.inventory_filter_tracked), ui.filter == StockFilter.TRACKED, { vm.setFilter(StockFilter.TRACKED) })
            CuadraChip(stringResource(R.string.inventory_filter_review) + if (review > 0) " ($review)" else "", ui.filter == StockFilter.REVIEW, { vm.setFilter(StockFilter.REVIEW) })
            CuadraChip(stringResource(R.string.inventory_filter_untracked), ui.filter == StockFilter.UNTRACKED, { vm.setFilter(StockFilter.UNTRACKED) })
        }
        CuadraButton(stringResource(R.string.inventory_new_product), vm::newProduct, Modifier.fillMaxWidth(), kind = ButtonKind.PRIMARY, height = 48)
        if (items.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(stringResource(R.string.inventory_empty), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) { items(items, key = { it.product.id }) { StockRow(it, catalogOnly) { vm.select(it.product.id) } } }
    }
    detail?.let { DetailSheet(it, timezone, catalogOnly, vm) }
    ui.editor?.let { EditorDialog(it, catalogOnly, vm) }
    if (!catalogOnly) ui.count?.let { CountDialog(it, vm) }
}

@Composable
private fun StockRow(s: ProductStock, catalogOnly: Boolean, onClick: () -> Unit) {
    val p = s.product
    val low = p.trackStock && p.minStockMilli != null && s.stockNowMilli <= p.minStockMilli
    val negative = p.trackStock && s.stockNowMilli < 0
    CuadraCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(p.name + (p.variant?.let { " · $it" } ?: ""), fontWeight = FontWeight.ExtraBold, maxLines = 1)
                Text(
                    listOfNotNull(money(p.priceMinor), p.costMinor?.let { stringResource(R.string.inventory_cost_short, money(it)) }).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted,
                )
            }
            if (catalogOnly) Unit
            else if (p.trackStock) Column(horizontalAlignment = Alignment.End) {
                Text(
                    qtyText(s.stockNowMilli) + " " + unitLabel(p.unit), fontWeight = FontWeight.ExtraBold,
                    color = if (negative) CuadraColors.Red else if (low) CuadraColors.Orange else CuadraColors.Ink,
                )
                if (negative) Tag(stringResource(R.string.inventory_negative), CuadraColors.Red, CuadraColors.RedSoft)
                else if (low) Tag(stringResource(R.string.inventory_low), CuadraColors.Orange, CuadraColors.OrangeSoft)
            } else Tag(stringResource(R.string.inventory_untracked_tag), CuadraColors.Ink, CuadraColors.Soft)
        }
    }
}

@Composable
private fun DetailSheet(d: ProductDetail, timezone: String, catalogOnly: Boolean, vm: InventoryViewModel) {
    val p = d.stock.product
    val zone = runCatching { ZoneId.of(timezone) }.getOrDefault(ZoneId.systemDefault())
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val time = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withLocale(locale).withZone(zone)
    Sheet({ vm.select(null) }) {
        Text(p.name + (p.variant?.let { " · $it" } ?: ""), style = MaterialTheme.typography.headlineMedium)
        if (catalogOnly) Unit
        else if (p.trackStock) {
            val negative = d.stock.stockNowMilli < 0
            Text(
                qtyText(d.stock.stockNowMilli) + " " + unitLabel(p.unit), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold,
                color = if (negative) CuadraColors.Red else CuadraColors.Ink,
            )
            if (negative) Text(stringResource(R.string.inventory_negative_hint), style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Red)
            p.minStockMilli?.let { Text(stringResource(R.string.inventory_min, qtyText(it) + " " + unitLabel(p.unit)), style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted) }
        } else Text(stringResource(R.string.inventory_untracked_hint), style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted)

        // Costo y ganancia: con el costo que tenía cada línea al venderse, no con el de hoy.
        CuadraCard {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Line(stringResource(R.string.inventory_price), money(p.priceMinor))
                Line(stringResource(R.string.inventory_cost), p.costMinor?.let { money(it) } ?: "—")
                p.costMinor?.takeIf { p.priceMinor > 0 }?.let { Line(stringResource(R.string.inventory_margin), "${(p.priceMinor - it) * 100 / p.priceMinor}%") }
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CuadraButton(stringResource(R.string.inventory_count), { vm.openCount(p.id, StockAction.COUNT) }, Modifier.weight(1f), kind = ButtonKind.PRIMARY, height = 48)
                CuadraButton(stringResource(R.string.inventory_damage), { vm.openCount(p.id, StockAction.DAMAGE) }, Modifier.weight(1f), height = 48)
                CuadraButton(stringResource(R.string.inventory_return), { vm.openCount(p.id, StockAction.RETURN) }, Modifier.weight(1f), height = 48)
            }
        }
        CuadraButton(stringResource(R.string.inventory_edit), { vm.edit(p) }, Modifier.fillMaxWidth(), height = 48)
        if (d.movements.isNotEmpty() && !catalogOnly) {
            SectionLabel(stringResource(R.string.inventory_history))
            d.movements.take(12).forEach { MovementRow(it, p.unit, time) }
        }
        CuadraButton(stringResource(R.string.close), { vm.select(null) }, Modifier.fillMaxWidth(), kind = ButtonKind.DARK)
    }
}

@Composable
private fun Line(label: String, value: String, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(1f), fontWeight = if (bold) FontWeight.ExtraBold else FontWeight.Normal)
        Text(value, fontWeight = if (bold) FontWeight.ExtraBold else FontWeight.Bold)
    }
}

@Composable
private fun MovementRow(m: StockMovementEntity, unit: String, time: DateTimeFormatter) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(movementLabel(m.kind), fontWeight = FontWeight.Bold)
            Text(listOfNotNull(time.format(Instant.ofEpochMilli(m.occurredAt)), m.createdByName, m.note).joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted, maxLines = 2)
        }
        val sign = if (m.quantityMilli > 0) "+" else ""
        Text(sign + qtyText(m.quantityMilli) + " " + unitLabel(unit), fontWeight = FontWeight.ExtraBold, color = if (m.quantityMilli < 0) CuadraColors.Red else if (m.quantityMilli > 0) CuadraColors.Green else CuadraColors.Ink)
    }
}

@Composable
private fun CountDialog(d: CountDraft2, vm: InventoryViewModel) {
    Sheet(vm::closeCount) {
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
        OutlinedTextField(
            d.amount, { vm.updateCount(d.copy(amount = it.take(12))) }, Modifier.fillMaxWidth(), singleLine = true,
            label = { Text(stringResource(if (d.kind == StockAction.COUNT) R.string.inventory_counted else R.string.inventory_quantity)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
        if (!d.initial) VoiceTextField(d.note, { vm.updateCount(d.copy(note = it.take(200))) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(stringResource(R.string.inventory_note)) })
        val ok = com.cuadra.caja.domain.Money3.parse(d.amount, 3)?.let { it > 0 || d.kind == StockAction.COUNT } == true
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Saltar el conteo inicial deja el producto con control pero en cero: se puede contar después.
            CuadraButton(stringResource(if (d.initial) R.string.inventory_later else R.string.cancel), vm::closeCount, Modifier.weight(1f))
            CuadraButton(stringResource(R.string.save), vm::confirmCount, Modifier.weight(1.4f), kind = ButtonKind.PRIMARY, enabled = ok)
        }
    }
}

@Composable
private fun EditorDialog(d: ProductEditorDraft, catalogOnly: Boolean, vm: InventoryViewModel) {
    Sheet(vm::closeEditor) {
        Text(stringResource(if (d.id == null) R.string.inventory_new_product else R.string.inventory_edit), style = MaterialTheme.typography.headlineMedium)
        VoiceTextField(d.name, { vm.updateEditor(d.copy(name = it.take(200))) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(stringResource(R.string.inventory_name)) },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences), isError = d.error && d.name.isBlank())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(d.price, { vm.updateEditor(d.copy(price = it.take(14))) }, Modifier.weight(1f), singleLine = true, label = { Text(stringResource(R.string.inventory_price)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), isError = d.error && d.price.isBlank())
            OutlinedTextField(d.cost, { vm.updateEditor(d.copy(cost = it.take(14))) }, Modifier.weight(1f), singleLine = true, label = { Text(stringResource(R.string.inventory_cost)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
        }
        Text(stringResource(R.string.inventory_unit), fontWeight = FontWeight.Bold)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("UNIT", "LB", "KG", "L", "M").forEach { u -> CuadraChip(unitLabel(u), d.unit == u, { vm.updateEditor(d.copy(unit = u)) }) }
        }
        // En modo catálogo no se toca el control de existencia: se conserva el que ya tenía el producto.
        if (!catalogOnly) Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.inventory_track), fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.inventory_track_hint), style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted)
            }
            Switch(d.track, { vm.updateEditor(d.copy(track = it)) })
        }
        if (d.track && !catalogOnly) OutlinedTextField(d.minStock, { vm.updateEditor(d.copy(minStock = it.take(12))) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(stringResource(R.string.inventory_min_label)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
        if (d.error) Text(stringResource(R.string.inventory_editor_error), color = CuadraColors.Red, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CuadraButton(stringResource(R.string.cancel), vm::closeEditor, Modifier.weight(1f))
            CuadraButton(stringResource(R.string.save), vm::saveEditor, Modifier.weight(1.4f), kind = ButtonKind.PRIMARY)
        }
    }
}

package com.cuadra.caja.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.cuadra.caja.R
import com.cuadra.caja.data.local.ProductEntity
import com.cuadra.caja.domain.Cart
import com.cuadra.caja.domain.CartLine
import com.cuadra.caja.ui.CajaUi
import com.cuadra.caja.ui.CajaViewModel
import com.cuadra.caja.ui.PosTab
import com.cuadra.caja.ui.common.ButtonKind
import com.cuadra.caja.ui.common.CuadraButton
import com.cuadra.caja.ui.common.CuadraCard
import com.cuadra.caja.ui.common.CuadraChip
import com.cuadra.caja.ui.common.Keypad
import com.cuadra.caja.ui.common.LocalMoney
import com.cuadra.caja.ui.common.money
import com.cuadra.caja.ui.common.VoiceTextField
import com.cuadra.caja.ui.theme.CuadraColors
import java.math.BigDecimal

/** Cantidad legible: 2 → "2", 750 milésimas → "0.75". */
fun qtyText(milli: Long): String = BigDecimal.valueOf(milli, 3).stripTrailingZeros().toPlainString()

@Composable
fun CajaScreen(vm: CajaViewModel, container: com.cuadra.caja.AppContainer, businessName: String, memberName: String, onLock: () -> Unit) {
    val ui by vm.ui.collectAsState()
    val tabs by vm.tabs.collectAsState()
    val quick by vm.quick.collectAsState()
    val results by vm.results.collectAsState()
    val parked by vm.parked.collectAsState()
    val sold by vm.soldToday.collectAsState()
    val activeTab = if (ui.tab in tabs) ui.tab else tabs.first()

    val cobro = ui.cobro
    if (cobro != null) {
        CobroScreen(ui, vm)
        ui.share?.let { ShareDialog(container, it) { vm.shareDismiss() } }
        return
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Header(businessName, memberName, parked.size, sold, onParked = { vm.toggleParked(true) }, onLock = onLock)
        Hero(ui.cart, onClear = vm::clearCart)
        if (tabs.size > 1) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                tabs.forEach { t -> CuadraChip(stringResource(tabLabel(t)), t == activeTab, { vm.setTab(t) }, Modifier.weight(1f)) }
            }
        }
        Box(Modifier.weight(1f)) {
            when (activeTab) {
                PosTab.TYPE -> TypeTab(ui, vm)
                PosTab.QUICK -> QuickTab(ui, quick, vm)
                PosTab.LIST -> ListTab(ui, results, vm)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
            CuadraButton(stringResource(R.string.register_park), vm::askPark, Modifier.weight(0.42f), enabled = !ui.cart.isEmpty, height = 58)
            CuadraButton(
                stringResource(R.string.register_charge) + if (ui.cart.isEmpty) "" else "  ·  " + money(ui.cart.totalMinor),
                vm::startCobro, Modifier.weight(1f), kind = ButtonKind.PRIMARY, enabled = ui.cart.totalMinor > 0, height = 58,
            )
        }
    }
    Overlays(ui, vm, parked)
}

private fun tabLabel(t: PosTab) = when (t) {
    PosTab.TYPE -> R.string.register_tab_type
    PosTab.QUICK -> R.string.register_tab_quick
    PosTab.LIST -> R.string.register_tab_list
}

@Composable
private fun Header(business: String, member: String, parkedCount: Int, soldToday: Long, onParked: () -> Unit, onLock: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f)) {
            Text(business, style = MaterialTheme.typography.headlineMedium, maxLines = 1)
            Text(stringResource(R.string.more_signed_in_as, member), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            Text(stringResource(R.string.register_sold_today, money(soldToday)), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        CuadraChip(stringResource(R.string.register_parked) + if (parkedCount > 0) " $parkedCount" else "", parkedCount > 0, onParked)
        CuadraButton("⇄", onLock, Modifier.size(48.dp), height = 48)
    }
}

@Composable
private fun Hero(cart: Cart, onClear: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(CuadraColors.Ink, RoundedCornerShape(22.dp)).padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.register_total) + " · " + pluralStringResource(R.plurals.register_lines, cart.lines.size, cart.lines.size),
                color = CuadraColors.Line, style = MaterialTheme.typography.bodyMedium,
            )
            Text(money(cart.totalMinor), color = CuadraColors.Bg, style = MaterialTheme.typography.displayLarge.copy(fontSize = androidx.compose.ui.unit.TextUnit(38f, androidx.compose.ui.unit.TextUnitType.Sp)), maxLines = 1)
        }
        if (!cart.isEmpty) CuadraButton(stringResource(R.string.register_clear), onClear, kind = ButtonKind.DARK, height = 44)
    }
}

/** Líneas del recibo con − / +. Cada botón mide al menos 48 dp. */
@Composable
private fun CartLines(cart: Cart, vm: CajaViewModel, modifier: Modifier = Modifier) {
    if (cart.isEmpty) {
        Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.register_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items(cart.lines, key = { it.id }) { line -> LineRow(line, vm) }
    }
}

@Composable
private fun LineRow(line: CartLine, vm: CajaViewModel) {
    val step = if (line.quantityMilli % 1000L == 0L) 1000L else 250L
    CuadraCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                Text(listOfNotNull(line.name, line.variant).joinToString(" · "), fontWeight = FontWeight.ExtraBold, maxLines = 1)
                Text("${money(line.unitPriceMinor)} × ${qtyText(line.quantityMilli)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(money(line.totalMinor), fontWeight = FontWeight.ExtraBold)
            CuadraButton("−", { vm.changeQuantity(line.id, -step) }, Modifier.size(48.dp), height = 48)
            CuadraButton("+", { vm.changeQuantity(line.id, step) }, Modifier.size(48.dp), height = 48)
        }
    }
}

@Composable
private fun TypeTab(ui: CajaUi, vm: CajaViewModel) {
    val decimals = LocalMoney.current.decimals
    val total = ui.entry.totalMinor(decimals)
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        CartLines(ui.cart, vm, Modifier.weight(1f))
        VoiceTextField(
            ui.description, vm::setDescription, Modifier.fillMaxWidth(), singleLine = true, label = { Text(stringResource(R.string.register_description_hint)) },
        )
        Text(
            ui.entry.display, Modifier.fillMaxWidth(), style = MaterialTheme.typography.displayLarge.copy(fontSize = androidx.compose.ui.unit.TextUnit(40f, androidx.compose.ui.unit.TextUnitType.Sp)),
            textAlign = androidx.compose.ui.text.style.TextAlign.End, maxLines = 1,
        )
        Keypad(vm::key, vm::backspace, Modifier.fillMaxWidth(), onDot = if (decimals > 0 || !ui.entry.multiplying) vm::dot else null, onTimes = vm::times)
        CuadraButton(
            stringResource(R.string.register_add, total?.let { money(it) } ?: ""), vm::addEntry, Modifier.fillMaxWidth(), kind = ButtonKind.DARK, enabled = total != null,
        )
    }
}

@Composable
private fun QuickTab(ui: CajaUi, quick: List<ProductEntity>, vm: CajaViewModel) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        CartLines(ui.cart, vm, Modifier.weight(1f))
        LazyVerticalGrid(GridCells.Fixed(3), Modifier.height(230.dp), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(quick, key = { it.id }) { p -> ProductTile(p) { vm.tapProduct(p) } }
            item { NewTile(vm::openDraft) }
        }
        if (quick.isEmpty()) Text(stringResource(R.string.register_no_quick), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ProductTile(p: ProductEntity, onClick: () -> Unit) {
    CuadraCard(onClick = onClick, modifier = Modifier.height(88.dp)) {
        Column(verticalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxSize()) {
            Text(listOfNotNull(p.name, p.variant).joinToString(" "), fontWeight = FontWeight.ExtraBold, maxLines = 2, style = MaterialTheme.typography.bodyMedium)
            Text(
                money(p.priceMinor) + if (p.pricing == "BY_WEIGHT") " / " + stringResource(R.string.unit_lb) else "",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
            )
        }
    }
}

@Composable
private fun NewTile(onClick: () -> Unit) {
    CuadraCard(onClick = onClick, modifier = Modifier.height(88.dp)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("+  " + stringResource(R.string.register_new_product), fontWeight = FontWeight.ExtraBold) }
    }
}

@Composable
private fun ListTab(ui: CajaUi, results: List<ProductEntity>, vm: CajaViewModel) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        CartLines(ui.cart, vm, Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            VoiceTextField(
                ui.query, vm::setQuery, Modifier.weight(1f), singleLine = true, placeholder = { Text(stringResource(R.string.register_search_hint)) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { vm.submitCode(ui.query) }),
            )
            CuadraButton("+", vm::openDraft, Modifier.size(56.dp))
        }
        LazyColumn(Modifier.height(190.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(results, key = { it.id }) { p ->
                CuadraCard(onClick = { vm.tapProduct(p) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(listOfNotNull(p.name, p.variant).joinToString(" · "), Modifier.weight(1f), fontWeight = FontWeight.Bold, maxLines = 1)
                        Text(money(p.priceMinor), fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
            if (results.isEmpty()) item { Text(stringResource(R.string.register_no_results), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

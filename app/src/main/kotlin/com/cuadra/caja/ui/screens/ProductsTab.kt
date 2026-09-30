package com.cuadra.caja.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cuadra.caja.R
import com.cuadra.caja.data.local.ProductEntity
import com.cuadra.caja.domain.Pricing
import com.cuadra.caja.domain.ProductsPane
import com.cuadra.caja.ui.CajaActions
import com.cuadra.caja.ui.CajaUi
import com.cuadra.caja.ui.common.ButtonKind
import com.cuadra.caja.ui.common.ButtonRow
import com.cuadra.caja.ui.common.CuadraButton
import com.cuadra.caja.ui.common.CuadraCard
import com.cuadra.caja.ui.common.LinkAction
import com.cuadra.caja.ui.common.MoneyText
import com.cuadra.caja.ui.common.Sheet
import com.cuadra.caja.ui.common.SplitRow
import com.cuadra.caja.ui.common.TAG_STICKY
import com.cuadra.caja.ui.common.Text
import com.cuadra.caja.ui.common.VoiceTextField
import com.cuadra.caja.ui.common.money
import com.cuadra.caja.ui.theme.CuadraColors

/**
 * Pestaña «Productos» de la caja: arriba (fijo) el buscador por nombre, código corto o código de barras (todo en el teléfono, sin tildes ni mayúsculas).
 * Sin texto: los ★ Frecuentes (en su orden) y, si son pocos, los «Más vendidos» de los últimos 30 días, en mosaicos. Con texto: resultados en filas.
 * Tocar agrega (precio abierto y por peso abren su diálogo, como siempre). Quien puede editar productos marca o quita frecuentes con una pulsación
 * larga y los ordena con «Ordenar».
 */
@OptIn(ExperimentalFoundationApi::class)
internal fun LazyListScope.productsTab(ui: CajaUi, pane: ProductsPane, canManage: Boolean, actions: CajaActions) {
    stickyHeader(key = "search") { SearchBar(ui.query, actions) }
    when {
        ui.query.isNotBlank() -> results(ui.query, pane.results, canManage, actions)
        ui.reordering && canManage -> reorder(pane.marked, actions)
        else -> suggestions(pane, canManage, actions)
    }
}

@Composable
private fun SearchBar(query: String, actions: CajaActions) {
    Row(
        Modifier.fillMaxWidth().testTag(TAG_STICKY).background(CuadraColors.Bg).padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        VoiceTextField(
            query, actions::setQuery, Modifier.weight(1f), singleLine = true, placeholder = { Text(stringResource(R.string.products_search_hint)) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { actions.submitCode(query) }),
        )
        val newLabel = stringResource(R.string.register_new_product)
        CuadraButton("+", actions::openDraft, Modifier.size(56.dp).semantics { contentDescription = newLabel })
    }
}

private fun LazyListScope.suggestions(pane: ProductsPane, canManage: Boolean, actions: CajaActions) {
    val frequents = pane.frequents
    val best = pane.bestSellers
    item(key = "freq-head") {
        SplitRow(Modifier.fillMaxWidth(), endMaxFraction = 0.6f, end = { if (canManage && pane.marked.size >= 2) LinkAction(stringResource(R.string.products_reorder), actions::startReorder) }) {
            Text(stringResource(R.string.products_frequents), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold)
        }
    }
    if (frequents.isEmpty()) item(key = "freq-empty") {
        Text(
            stringResource(if (canManage) R.string.products_no_frequents_manager else R.string.products_no_frequents),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (frequents.isNotEmpty() || best.isEmpty()) item(key = "freq-grid") { TileGrid(frequents, frequent = true, trailingNew = best.isEmpty(), canManage, actions) }
    if (best.isNotEmpty()) {
        item(key = "best-head") {
            Text(stringResource(R.string.products_best_sellers), Modifier.padding(top = 4.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item(key = "best-grid") { TileGrid(best, frequent = false, trailingNew = true, canManage, actions) }
    }
}

/** Mosaicos compactos: columnas según el ancho y la letra (con letra grande hay menos, más anchas); el nombre nunca se parte por letras. */
@Composable
private fun TileGrid(products: List<ProductEntity>, frequent: Boolean, trailingNew: Boolean, canManage: Boolean, actions: CajaActions) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val cols = (maxWidth / (88.dp * fontFactor())).toInt().coerceIn(1, 3)
        val tiles: List<ProductEntity?> = if (trailingNew) products + null else products
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            tiles.chunked(cols).forEach { rowTiles ->
                Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rowTiles.forEach { p ->
                        val m = Modifier.weight(1f).fillMaxHeight()
                        if (p != null) ProductTile(p, frequent, m, canManage, actions) else NewTile(m, actions::openDraft)
                    }
                    repeat(cols - rowTiles.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun ProductTile(p: ProductEntity, frequent: Boolean, modifier: Modifier, canManage: Boolean, actions: CajaActions) {
    val longLabel = stringResource(if (p.isQuick) R.string.products_unmark_frequent else R.string.products_mark_frequent)
    // Los más vendidos (no marcados) van en gris claro: se distinguen de los ★ Frecuentes que eligió el negocio.
    CuadraCard(
        onClick = { actions.tapProduct(p) }, modifier = modifier.heightIn(min = 72.dp), color = if (frequent) CuadraColors.Surface else CuadraColors.Soft,
        padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 10.dp),
        onLongClick = if (canManage) ({ actions.openProductMenu(p) }) else null, onLongClickLabel = longLabel,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text((if (frequent) "★ " else "") + listOfNotNull(p.name, p.variant).joinToString(" "), fontWeight = FontWeight.ExtraBold, maxLines = 3, ellipsize = true, minScale = 0.75f, style = MaterialTheme.typography.bodyMedium)
            PriceLine(p)
        }
    }
}

/** Precio del mosaico: «Precio abierto» (y el sugerido, si tiene; nunca «C$ 0.00») o el precio (por libra si se pesa). */
@Composable
private fun PriceLine(p: ProductEntity) {
    if (p.pricing == Pricing.OPEN) {
        Text(stringResource(R.string.pricing_OPEN), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (p.priceMinor > 0) MoneyText("≈ " + money(p.priceMinor), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else MoneyText(
        money(p.priceMinor) + if (p.pricing == Pricing.BY_WEIGHT) " / " + stringResource(R.string.unit_lb) else "",
        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun NewTile(modifier: Modifier, onClick: () -> Unit) {
    CuadraCard(onClick = onClick, modifier = modifier.heightIn(min = 72.dp)) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("+  " + stringResource(R.string.register_new_product), fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
        }
    }
}

private fun LazyListScope.results(query: String, results: List<ProductEntity>, canManage: Boolean, actions: CajaActions) {
    items(results, key = { "r-" + it.id }) { p -> ResultRow(p, canManage, actions) }
    if (results.isEmpty()) item(key = "none") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.products_no_results, query.trim()), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, ellipsize = true)
            CuadraCard(onClick = { actions.createFromQuery(query) }) {
                Text("+  " + stringResource(R.string.products_create_named, query.trim()), fontWeight = FontWeight.ExtraBold, color = CuadraColors.Green, maxLines = 3, ellipsize = true)
            }
        }
    }
}

/** Fila de un resultado: nombre (★ si es frecuente), debajo el código corto y el de barras, y el precio a la derecha. */
@Composable
private fun ResultRow(p: ProductEntity, canManage: Boolean, actions: CajaActions) {
    val longLabel = stringResource(if (p.isQuick) R.string.products_unmark_frequent else R.string.products_mark_frequent)
    CuadraCard(onClick = { actions.tapProduct(p) }, onLongClick = if (canManage) ({ actions.openProductMenu(p) }) else null, onLongClickLabel = longLabel) {
        SplitRow(end = { if (p.pricing == Pricing.OPEN) OpenPriceTag(p) else MoneyText(money(p.priceMinor) + if (p.pricing == Pricing.BY_WEIGHT) " / " + stringResource(R.string.unit_lb) else "", fontWeight = FontWeight.ExtraBold) }) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text((if (p.isQuick) "★ " else "") + listOfNotNull(p.name, p.variant).joinToString(" · "), fontWeight = FontWeight.Bold, maxLines = 2, ellipsize = true)
                val codes = listOfNotNull(p.shortCode?.takeIf { it.isNotBlank() }?.let { stringResource(R.string.products_code, it) }, p.barcode?.takeIf { it.isNotBlank() })
                if (codes.isNotEmpty()) Text(codes.joinToString("  ·  "), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Normal, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** «Precio abierto» (y debajo el sugerido, si hay) en el lugar del monto de una fila. */
@Composable
private fun OpenPriceTag(p: ProductEntity) {
    Column(horizontalAlignment = Alignment.End) {
        Text(stringResource(R.string.pricing_OPEN), fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.End)
        if (p.priceMinor > 0) MoneyText("≈ " + money(p.priceMinor), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** «Ordenar frecuentes»: cada frecuente con subir/bajar (48 dp) y «Listo». */
private fun LazyListScope.reorder(marked: List<ProductEntity>, actions: CajaActions) {
    item(key = "reorder-head") {
        SplitRow(Modifier.fillMaxWidth(), endMaxFraction = 0.6f, end = { CuadraButton(stringResource(R.string.products_reorder_done), actions::finishReorder, kind = ButtonKind.DARK, height = 48) }) {
            Text(stringResource(R.string.products_reorder_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold)
        }
    }
    itemsIndexed(marked, key = { _, p -> "o-" + p.id }) { i, p ->
        CuadraCard(padding = androidx.compose.foundation.layout.PaddingValues(start = 12.dp, end = 4.dp, top = 2.dp, bottom = 2.dp)) {
            SplitRow(endMaxFraction = 0.5f, spacing = 4.dp, end = {
                Row {
                    ArrowButton("↑", stringResource(R.string.products_move_up), enabled = i > 0) { actions.moveFrequent(p.id, -1) }
                    ArrowButton("↓", stringResource(R.string.products_move_down), enabled = i < marked.lastIndex) { actions.moveFrequent(p.id, 1) }
                }
            }) {
                Text("${i + 1}.  " + listOfNotNull(p.name, p.variant).joinToString(" "), fontWeight = FontWeight.Bold, maxLines = 2, ellipsize = true)
            }
        }
    }
}

@Composable
private fun ArrowButton(symbol: String, description: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(48.dp).clickable(enabled = enabled, role = Role.Button, onClick = onClick).semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { Text(symbol, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = if (enabled) CuadraColors.Ink else CuadraColors.Line, maxLines = 1, minScale = 0.6f) }
}

/** Pulsación larga en un producto: marcarlo como frecuente o quitarlo (solo quien puede editar productos). */
@Composable
fun ProductMenuSheet(p: ProductEntity, actions: CajaActions) {
    Sheet(actions::closeProductMenu, actions = {
        ButtonRow {
            CuadraButton(stringResource(R.string.close), actions::closeProductMenu, Modifier.share(0.7f))
            CuadraButton(
                stringResource(if (p.isQuick) R.string.products_unmark_frequent else R.string.products_mark_frequent), { actions.toggleFrequent(p) }, Modifier.share(1f),
                kind = ButtonKind.DARK,
            )
        }
    }) {
        Text((if (p.isQuick) "★ " else "") + listOfNotNull(p.name, p.variant).joinToString(" · "), style = MaterialTheme.typography.headlineSmall, maxLines = 4, ellipsize = true)
        Column(Modifier.padding(top = 4.dp)) { PriceLine(p) }
        Text(stringResource(R.string.products_menu_hint), Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

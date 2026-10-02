package com.cuadra.caja.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.material3.Icon
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import com.cuadra.caja.ui.common.CappedFontScale
import com.cuadra.caja.ui.common.PosKeypad
import com.cuadra.caja.ui.common.TAG_BOTTOM_BAR
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.platform.testTag
import com.cuadra.caja.ui.common.TAG_STICKY
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cuadra.caja.R
import com.cuadra.caja.data.local.ProductEntity
import com.cuadra.caja.data.local.SaleEntity
import com.cuadra.caja.domain.Cart
import com.cuadra.caja.domain.CartLine
import com.cuadra.caja.domain.Pricing
import com.cuadra.caja.ui.CajaActions
import com.cuadra.caja.ui.CajaUi
import com.cuadra.caja.ui.CajaViewModel
import com.cuadra.caja.ui.PosTab
import com.cuadra.caja.domain.ProductsPane
import com.cuadra.caja.ui.ShareRequest
import com.cuadra.caja.ui.common.ButtonKind
import com.cuadra.caja.ui.common.TAG_RECEIPT_BADGE
import com.cuadra.caja.ui.common.ButtonRow
import com.cuadra.caja.ui.common.ChipGrid
import com.cuadra.caja.ui.common.CuadraButton
import com.cuadra.caja.ui.common.CuadraCard
import com.cuadra.caja.ui.common.CuadraChip
import com.cuadra.caja.ui.common.Keypad
import com.cuadra.caja.ui.common.LocalMoney
import com.cuadra.caja.ui.common.MoneyText
import com.cuadra.caja.ui.common.ScanButton
import com.cuadra.caja.ui.common.PrintNoticePopup
import com.cuadra.caja.ui.common.SaleNoticePopup
import com.cuadra.caja.domain.printing.PrintNotice
import com.cuadra.caja.domain.printing.PrinterBadge
import com.cuadra.caja.ui.common.DescriptionAmountRow
import com.cuadra.caja.ui.common.RegisterFrame
import com.cuadra.caja.ui.common.SplitRow
import com.cuadra.caja.ui.common.Text
import com.cuadra.caja.ui.common.ChipFlow
import com.cuadra.caja.ui.common.VoiceTextField
import com.cuadra.caja.ui.common.money
import com.cuadra.caja.ui.theme.CuadraColors
import java.math.BigDecimal

/** Cantidad legible: 2 → "2", 750 milésimas → "0.75". */
fun qtyText(milli: Long): String = BigDecimal.valueOf(milli, 3).stripTrailingZeros().toPlainString()

/** Con letra grande el alto/ancho «mínimo» de lo que lleva texto crece con ella (hasta 2×): las cajas nunca quedan más chicas que su contenido. */
@Composable
internal fun fontFactor(): Float = LocalDensity.current.fontScale.coerceIn(1f, 2f)

@Composable
fun CajaScreen(vm: CajaViewModel, container: com.cuadra.caja.AppContainer, businessName: String, memberName: String, onLock: () -> Unit, canManageFrequents: Boolean = false) {
    val ui by vm.ui.collectAsState()
    val tabs by vm.tabs.collectAsState()
    val products by vm.products.collectAsState()
    val parked by vm.parked.collectAsState()
    val sold by vm.soldToday.collectAsState()
    val askDescription by container.display.askDescription.collectAsState()
    val registerCheckout by vm.registerCheckout.collectAsState()
    val lineCounts by vm.parkedLineCounts.collectAsState()
    val business by vm.business.collectAsState()
    val zone = remember(business?.timezone) { runCatching { java.time.ZoneId.of(business?.timezone) }.getOrDefault(java.time.ZoneId.systemDefault()) }
    // Lector físico (teclado o DataWedge): mientras la caja está a la vista, sus lecturas llegan aquí.
    val hub = container.scanner
    val readerReady by hub.ready.collectAsState()
    androidx.compose.runtime.DisposableEffect(hub) {
        val claim = hub.claim { code, _ -> vm.onHardwareScan(code) }
        onDispose { claim.close() }
    }
    CajaContent(
        ui, tabs, products, parked, sold, businessName, memberName, vm, onLock, readerReady = readerReady,
        // El comprobante de una venta (sin cliente) pide el número: hoja chica con «Elegir de contactos» y «Abrir WhatsApp». El detalle de un fiado, la de siempre.
        shareDialog = { request, dismiss -> if (request is ShareRequest.Ticket) WhatsAppNumberDialog(container, request, dismiss) else ShareDialog(container, request, dismiss) },
        askDescription = askDescription, canManageFrequents = canManageFrequents, registerCheckout = registerCheckout, lineCounts = lineCounts, zone = zone,
    )
}

/**
 * La caja (pantalla principal), sin ViewModel: recibe el estado y las acciones. Es lo que dibuja la guardia de diseño.
 *
 * Disposición (docs/notas/caja-ux-v2.md): arriba, lo que puede desplazarse (encabezado, total, pestañas y el contenido de «Productos»); en «Manual»,
 * la calculadora va PEGADA abajo (`dock`: descripción —solo con «Pedir descripción al agregar»—, monto y teclas de 4 columnas con «Agregar» como tecla alta) y nunca se desplaza ni queda
 * detrás de nada; al pie, siempre fija, la barra `[Recibo · N] [Apartar] [Cobrar total] (sin «Apartar» con «Cobro en caja»)` (el aviso «Deshacer» es flotante: no ocupa lugar). El detalle del recibo
 * vive en una hoja (`ReceiptSheet`).
 */
@Composable
fun CajaContent(
    ui: CajaUi, tabs: List<PosTab>, products: ProductsPane, parked: List<SaleEntity>, soldToday: Long,
    businessName: String, memberName: String, actions: CajaActions, onLock: () -> Unit,
    shareDialog: @Composable (ShareRequest, () -> Unit) -> Unit = { _, _ -> },
    readerReady: Boolean = false,
    /** Preferencia de este teléfono «Pedir descripción al agregar» (apagada por omisión: la calculadora no muestra el campo). */
    askDescription: Boolean = false,
    /** Quien puede editar productos: marca/quita frecuentes (pulsación larga) y los ordena. */
    canManageFrequents: Boolean = true,
    /** Cobro en caja (ADR 0015): el botón del encabezado es «Por cobrar en caja» y reúne esas cuentas y las apartadas. */
    registerCheckout: Boolean = false,
    lineCounts: Map<String, Int> = emptyMap(),
    zone: java.time.ZoneId = java.time.ZoneId.systemDefault(),
) {
    val activeTab = if (ui.tab in tabs) ui.tab else tabs.first()
    val cobro = ui.cobro
    // El aviso de impresión se oculta solo: «Impreso» pronto; las advertencias se quedan el doble (traen «Reintentar»). Un aviso nuevo (printTick) reinicia la cuenta.
    // Con el aviso de la venta cobrada a la vista (vuelto / «Anular») el de impresión espera su turno: su cuenta empieza cuando nada más lo tapa.
    val saleNotice = ui.saleNotice
    val saleNoticeShown = cobro == null && saleNotice != null && !ui.receiptOpen
    LaunchedEffect(ui.printTick, ui.printNotice, saleNoticeShown, cobro != null) {
        val notice = ui.printNotice ?: return@LaunchedEffect
        if (cobro != null || saleNoticeShown) return@LaunchedEffect
        delay(if (notice == PrintNotice.PRINTED) UNDO_MILLIS else UNDO_MILLIS * 2)
        actions.dismissPrintNotice()
    }
    // El aviso de la venta cobrada se queda hasta el primer toque del teclado o de un producto (lo quita el ViewModel) o unos 8 s; con la hoja «Anular» abierta no corre.
    LaunchedEffect(saleNotice?.saleId, saleNotice?.undone, ui.saleUndo != null) {
        if (saleNotice == null || ui.saleUndo != null) return@LaunchedEffect
        delay(com.cuadra.caja.domain.SaleNotice.visibleMillis(saleNotice.undone || saleNotice.sent))
        actions.hideSaleNotice()
    }
    if (cobro != null) {
        CobroContent(ui, actions)
        ui.share?.let { shareDialog(it) { actions.shareDismiss() } }
        return
    }
    val decimals = LocalMoney.current.decimals
    val entryTotal = ui.entry.totalMinor(decimals)

    // El aviso «Deshacer» se oculta solo a los ~3.5 s; un aviso nuevo (undoTick) reinicia la cuenta.
    LaunchedEffect(ui.undoTick, ui.undoShown) {
        if (ui.undoShown) { delay(UNDO_MILLIS); actions.hideUndo() }
    }

    // El teclado del sistema abierto (escribiendo la descripción): no hay lugar para las teclas y la descripción no puede esconderse.
    @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
    val imeOpen = WindowInsets.isImeVisible
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Alto de las teclas según el alto de la pantalla (48–64 dp): en teléfonos bajos son las mínimas, en altos, más cómodas.
        val keyHeight = (maxHeight.value * 0.075f).coerceIn(48f, 64f).dp
        val typing = activeTab == PosTab.MANUAL
        // `RegisterFrame`: el total (`Hero`) tiene prioridad sobre las pestañas, la descripción y el encabezado; ver su documentación.
        RegisterFrame(
            Modifier.fillMaxSize().imePadding().padding(horizontal = 16.dp), spacing = 4.dp, bodyNatural = typing,
            header = { full -> Header(full, businessName, memberName, parked.size, soldToday, onParked = { actions.toggleParked(true) }, onLock = onLock, registerCheckout = registerCheckout) },
            hero = { Hero(ui.priced.cart, readerReady, ui.printer, onOpen = actions::openReceipt, onScan = actions::openScanner, onPrinter = actions::openPrinterSettings) },
            body = {
                if (typing) {
                    if (tabs.size > 1) Tabs(tabs, activeTab, actions)
                } else {
                    // La tira de la última línea de «Productos» va pegada bajo el buscador: completa (nombre y controles), compacta (solo controles) o, si la lista
                    // quedaría sin lugar (teléfono bajo y letra enorme), ninguna. Con el teclado abierto (buscando) o en «Ordenar frecuentes» tampoco.
                    BoxWithConstraints(Modifier.fillMaxSize()) {
                        val mode = if (imeOpen || ui.reordering || ui.receiptOpen) StripMode.NONE else StripMode.forProductsBody(maxHeight, fontFactor())
                        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (tabs.size > 1) item { Tabs(tabs, activeTab, actions) }
                            if (activeTab == PosTab.PRODUCTS) productsTab(
                                ui, products, canManageFrequents, actions,
                                strip = if (mode == StripMode.NONE) null else ({ LastLineStrip(ui.lastLine, compact = mode == StripMode.COMPACT, actions = actions) }),
                            )
                        }
                    }
                }
            },
            dock = if (typing) ({ TypeDock(ui, entryTotal, keyHeight, imeOpen, askDescription, actions) }) else null,
            // «Manual»: la última línea del recibo (cantidad editable) en el hueco entre las pestañas y la calculadora; sin teclado del sistema, sin la hoja del recibo abierta (la tapa) y con al menos una línea.
            reserveForStrip = if (!typing && !imeOpen && !ui.reordering && !ui.receiptOpen) StripMode.compactBodyMin(fontFactor()) else 0.dp,
            strip = ui.lastLine?.takeIf { typing && !imeOpen && !ui.receiptOpen }?.let { line -> { compact -> LastLineStrip(line, compact, actions) } },
            footer = {
                Column(Modifier.fillMaxWidth().testTag(TAG_BOTTOM_BAR).padding(bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PosBottomBar(ui.priced.cart, actions, com.cuadra.caja.domain.ParkRules.middle(registerCheckout), updating = ui.resumedPending)
                }
            },
            // Aviso flotante «Línea quitada · Deshacer» (agregar ya no abre aviso: la tira de la última línea lleva su − y su ✕): en el hueco libre entre las pestañas y la calculadora (o sobre el encabezado), sin ocupar lugar
            // ni tapar las pestañas, el total, las teclas o la barra (ver `RegisterFrame`).
            // Con la hoja del recibo abierta, el aviso se dibuja dentro de la hoja (esta capa queda detrás de su fondo oscuro).
            // Prioridad: el aviso de la venta cobrada (vuelto / «Anular», destacado), luego «Deshacer» de la calculadora y al final los avisos de impresión
            // (las advertencias de impresión esperan a que el aviso de la venta se vaya).
            overlay = saleNotice?.takeIf { !ui.receiptOpen }?.let { n ->
                {
                    val content = com.cuadra.caja.domain.SaleNotice.of(n.totalMinor, n.changeMinor, n.doneAtMillis, System.currentTimeMillis(), n.undone, n.sent, n.note)
                    SaleNoticePopup(content, printed = ui.printNotice == PrintNotice.PRINTED, onUndo = actions::askUndoSale)
                }
            }
                ?: ui.undo?.takeIf { !ui.receiptOpen }?.let { u -> { UndoPopup(u, actions::undoLast) } }
                ?: ui.printNotice?.takeIf { !ui.receiptOpen }?.let { n -> { PrintNoticePopup(n, actions::printAgain, actions::dismissPrintNotice) } },
            overlayLarge = saleNotice != null && !ui.receiptOpen,
        )
    }
    Overlays(ui, actions, parked, registerCheckout, lineCounts, zone)
}

/** Cuánto dura el aviso flotante «Deshacer» a la vista. */
internal const val UNDO_MILLIS = com.cuadra.caja.ui.common.NOTICE_MILLIS

/**
 * La calculadora pegada abajo: el monto (con «Pedir descripción al agregar», la descripción a su lado si caben en una fila) y las teclas.
 * Sin la preferencia (por omisión) no hay campo: la descripción de una línea manual se escribe después tocándola en el recibo.
 */
@Composable
private fun TypeDock(ui: CajaUi, entryTotal: Long?, keyHeight: androidx.compose.ui.unit.Dp, imeOpen: Boolean, askDescription: Boolean, actions: CajaActions) {
    val decimals = LocalMoney.current.decimals
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        CappedFontScale(1.3f) {
            if (askDescription) DescriptionAmountRow(
                description = { VoiceTextField(ui.description, actions::setDescription, Modifier.fillMaxWidth(), singleLine = true, compact = true, placeholder = { Text(stringResource(R.string.register_description_hint)) }) },
                amount = { MoneyText(ui.entry.display, style = MaterialTheme.typography.displayMedium, textAlign = TextAlign.End) },
            )
            else MoneyText(ui.entry.display, Modifier.fillMaxWidth(), style = MaterialTheme.typography.displayMedium, textAlign = TextAlign.End)
        }
        // Con el teclado del sistema abierto (escribiendo la descripción) no hay lugar para las teclas: queda solo un «Agregar» ancho sobre la barra.
        if (imeOpen) {
            CuadraButton(
                stringResource(R.string.register_add_key) + (entryTotal?.let { "  ·  " + money(it) } ?: ""), actions::addEntry, Modifier.fillMaxWidth(),
                kind = ButtonKind.DARK, enabled = entryTotal != null, height = 48,
            )
        } else PosKeypad(
            actions::key, actions::backspace, actions::addEntry, addEnabled = entryTotal != null, addLabel = stringResource(R.string.register_add_key), keyHeight = keyHeight,
            addTotal = if (ui.entry.multiplying) entryTotal?.let { money(it) } else null,
            onDot = if (decimals > 0 || !ui.entry.multiplying) actions::dot else null, onTimes = actions::times,
        )
    }
}

@Composable
private fun Tabs(tabs: List<PosTab>, activeTab: PosTab, actions: CajaActions) {
    // Las pestañas (dos palabras cortas: «Manual» y «Productos») se quedan en UNA fila aunque la letra sea enorme: su letra crece hasta 1.15× como máximo.
    CappedFontScale(1.15f) {
        ChipGrid(Modifier.fillMaxWidth().testTag(TAG_REGISTER_TABS)) { tabs.forEach { t -> CuadraChip(stringResource(tabLabel(t)), t == activeTab, { actions.setTab(t) }) } }
    }
}

/**
 * La barra fija de tres acciones (48 dp): `[Recibo · N]` con ícono, el botón del medio y `[Cobrar C$ total]` (la más ancha). El del medio es `[Apartar]` o,
 * con «Cobro en caja», `[Enviar a caja]` (mismo lugar y mismas reglas de ancho; ver `ParkRules.middle`). Si con la letra actual no caben
 * completas se van compactando en este orden: «Recibo» queda con ícono y número, «Cobrar» pierde el total (sigue visible arriba); al final la letra baja.
 */
@Composable
private fun PosBottomBar(
    cart: Cart, actions: CajaActions, middle: com.cuadra.caja.domain.ParkRules.Middle = com.cuadra.caja.domain.ParkRules.Middle.PARK,
    /** La cuenta en pantalla se retomó de «Por cobrar en caja» (agregar productos): el botón del medio dice «Actualizar en caja». */
    updating: Boolean = false,
) {
    val sending = middle == com.cuadra.caja.domain.ParkRules.Middle.SEND
    val n = cart.lineCount
    val receiptFull = if (n > 0) stringResource(R.string.register_receipt_n, n) else stringResource(R.string.register_receipt)
    val receiptDesc = receiptFull
    val receiptWord = stringResource(R.string.register_receipt)
    val park = stringResource(if (!sending) R.string.register_park else if (updating) R.string.send_update_confirm else R.string.send_bar)
    val chargeShort = stringResource(R.string.register_charge)
    val chargeFull = if (cart.isEmpty) chargeShort else stringResource(R.string.register_charge_total, money(cart.totalMinor))
    val measurer = rememberTextMeasurer()
    CappedFontScale(1.5f) {
        val density = LocalDensity.current
        val style = MaterialTheme.typography.labelLarge
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            fun w(text: String) = with(density) { measurer.measure(AnnotatedString(text), style, softWrap = false, maxLines = 1).size.width.toDp() } + 18.dp
            val icon = 30.dp
            val recFull = w(receiptFull) + icon + 6.dp
            val recShort = if (n > 0) w(n.toString()) + icon + 6.dp else 48.dp
            val gaps = 16.dp
            val parkW0 = w(park)
            // (Recibo completo, Cobrar completo) → (Recibo corto, Cobrar completo) → (Recibo corto, Cobrar corto) → lo mismo con la letra achicándose.
            val options = listOf(recFull to chargeFull, recShort to chargeFull, recShort to chargeShort)
            val pick = options.indexOfFirst { (r, c) -> r + parkW0 + w(c) + gaps <= maxWidth }.let { if (it < 0) 2 else it }
            val compactReceipt = pick >= 1
            val chargeText = if (pick >= 2) chargeShort else chargeFull
            // El NÚMERO va aparte (`badge`): nunca se achica ni se recorta; la palabra «Recibo» es lo que cede (y en la versión corta desaparece).
            val recWord = if (compactReceipt) "" else if (n <= 0) receiptFull else receiptWord
            val recBadge = if (n <= 0) null else if (compactReceipt) n.toString() else "· $n"
            val natural = listOf(if (compactReceipt) recShort else recFull, parkW0, w(chargeText)).map { it.value.coerceAtLeast(48f) }
            val avail = maxWidth.value - gaps.value
            // Reparto del ancho: si todo cabe, lo que sobra se reparte en proporción; si no, «Recibo» y «Apartar» conservan SU ancho (nunca por debajo del que necesita su
            // texto) y «Cobrar» toma el resto (su letra baja hasta 60 %).
            val widths = if (natural.sum() <= avail) natural.map { it * avail / natural.sum() }
            else {
                // «Recibo» (con su número, protegido) conserva SU ancho; «Apartar» y «Cobrar» se reparten el resto en proporción (sus letras bajan hasta 60 %).
                val rest = (avail - natural[0]).coerceAtLeast(96f)
                // «Cobrar» nunca queda por debajo de lo que su texto necesita con la letra al 60 %: si falta, cede el del medio (su texto, p. ej. «Enviar a caja»,
                // puede bajar a dos líneas sin partir palabras).
                val chargeNeed = (natural[2] - 18f) * 0.62f + 18f
                val parkW = (rest * natural[1] / (natural[1] + natural[2])).coerceIn(48f, natural[1]).let { p -> if (rest - p < chargeNeed) (rest - chargeNeed).coerceAtLeast(48f) else p }
                listOf(natural[0], parkW, (rest - parkW).coerceAtLeast(48f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BarButton(recWord, receiptDesc, actions::openReceipt, Modifier.width(widths[0].dp), icon = R.drawable.ic_receipt, badge = recBadge)
                BarButton(park, park, if (sending) actions::askSend else actions::askPark, Modifier.width(widths[1].dp).testTag(if (sending) TAG_BAR_SEND else TAG_BAR_PARK), enabled = !cart.isEmpty,
                    maxLines = if (sending) 2 else 1)
                BarButton(chargeText, chargeText, actions::startCobro, Modifier.width(widths[2].dp), kind = ButtonKind.PRIMARY, enabled = cart.totalMinor > 0)
            }
        }
    }
}

@Composable
private fun BarButton(label: String, description: String, onClick: () -> Unit, modifier: Modifier, kind: ButtonKind = ButtonKind.OUTLINE, enabled: Boolean = true, icon: Int? = null, badge: String? = null, maxLines: Int = 1) {
    val shape = RoundedCornerShape(16.dp)
    val bg = when (kind) { ButtonKind.PRIMARY -> CuadraColors.Green; else -> CuadraColors.Surface }
    val fg = if (!enabled) CuadraColors.Muted else if (kind == ButtonKind.PRIMARY) androidx.compose.ui.graphics.Color.White else CuadraColors.Ink
    Box(
        modifier.heightIn(min = 48.dp).defaultMinSize(minWidth = 48.dp).clip(shape).background(if (enabled) bg else CuadraColors.Soft, shape)
            .then(if (kind == ButtonKind.OUTLINE && enabled) Modifier.border(androidx.compose.foundation.BorderStroke(1.dp, CuadraColors.Line), shape) else Modifier)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick).semantics { contentDescription = description }.padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(24.dp), tint = fg)
            if (label.isNotEmpty()) Text(label, Modifier.weight(1f, fill = false), color = fg, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center, maxLines = maxLines, minScale = 0.6f)
            // El número del recibo: una sola línea, sin achicar ni recortar (minScale = 1); la fila le reserva su ancho completo antes que a la palabra.
            if (badge != null) Text(badge, Modifier.testTag(TAG_RECEIPT_BADGE), color = fg, style = MaterialTheme.typography.labelLarge, maxLines = 1, softWrap = false, minScale = 1f)
        }
    }
}

private fun tabLabel(t: PosTab) = when (t) {
    PosTab.MANUAL -> R.string.register_tab_manual
    PosTab.PRODUCTS -> R.string.register_tab_products
}

/**
 * Encabezado de la caja: la fila (nombre del negocio, «Apartadas», cambiar de cajero) con un margen chico arriba y abajo y, si hay alto de sobra (`full`),
 * «Atiende: …» en su línea y «Vendido hoy …» en la suya, las dos a la izquierda y en negrita. Es lo primero que se acorta si no hay alto para el total (ver `RegisterFrame`).
 */
@Composable
private fun Header(full: Boolean, business: String, member: String, parkedCount: Int, soldToday: Long, onParked: () -> Unit, onLock: () -> Unit, registerCheckout: Boolean = false) {
    Column(Modifier.fillMaxWidth().padding(vertical = com.cuadra.caja.ui.common.HEADER_GAP), verticalArrangement = Arrangement.spacedBy(com.cuadra.caja.ui.common.HEADER_LINE_GAP)) {
        // Los botones nunca se pisan con el nombre: si no caben lado a lado, quedan arriba a la derecha y el nombre baja (SplitRow).
        SplitRow(
            Modifier.fillMaxWidth(), endMaxFraction = 0.6f, spacing = 8.dp,
            end = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    // Con cobro en caja: «Por cobrar en caja N» (esas cuentas y las apartadas, en un solo lugar).
                    CuadraChip(stringResource(if (registerCheckout) R.string.register_queue_chip else R.string.register_parked) + if (parkedCount > 0) " $parkedCount" else "", parkedCount > 0, onParked,
                        Modifier.weight(1f, fill = false))
                    CuadraButton("⇄", onLock, Modifier.size(48.dp), height = 48)
                }
            },
        ) {
            // El nombre del negocio es lo único que puede terminar en «…» (deliberado, etiquetado): hasta 2 líneas y, antes, la letra baja hasta 70 %.
            Text(business, style = MaterialTheme.typography.titleLarge, fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold, maxLines = 2, ellipsize = true, minScale = 0.7f)
        }
        if (full) {
            Text(
                stringResource(R.string.more_signed_in_as, member), Modifier.fillMaxWidth(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Start, maxLines = 2, ellipsize = true,
            )
            Text(
                stringResource(R.string.register_sold_today, money(soldToday)), Modifier.fillMaxWidth(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Start,
            )
        }
    }
}

/** El botón del medio de la barra: «Apartar» o, con «Cobro en caja», «Enviar a caja». */
const val TAG_BAR_PARK = "bar_park"
const val TAG_BAR_SEND = "bar_send"

/** La fila de pestañas «Manual | Productos» (el aviso flotante nunca la tapa). */
const val TAG_REGISTER_TABS = "register_tabs"

/**
 * El total: la tarjeta oscura con el número entero. Nunca se recorta (`RegisterFrame` le da prioridad) y es compacta: el alto y la letra crecen con la letra del
 * teléfono solo hasta 1.3× (el monto baja de tamaño si es larguísimo). Tocar el texto o la cifra abre el recibo; el botón de escanear es aparte y va a la derecha.
 */
@Composable
private fun Hero(cart: Cart, readerReady: Boolean, printer: PrinterBadge, onOpen: () -> Unit, onScan: () -> Unit, onPrinter: () -> Unit) {
    val open = stringResource(R.string.register_receipt_open)
    CappedFontScale(1.3f) {
        Row(
            Modifier.fillMaxWidth().testTag(com.cuadra.caja.ui.common.TAG_TOTAL_CARD).background(CuadraColors.Ink, RoundedCornerShape(18.dp)).padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(Modifier.weight(1f).heightIn(min = 48.dp).clickable(role = Role.Button, onClickLabel = open, onClick = onOpen), verticalArrangement = Arrangement.Center) {
                // A qué se refiere el total y, si hay lector físico encendido (las lecturas llegan solas, sin abrir la cámara), su aviso: en la misma línea o debajo.
                ChipFlow(Modifier.fillMaxWidth(), spacing = 2.dp) {
                    Text(
                        stringResource(R.string.register_total) + " · " + pluralStringResource(R.plurals.register_lines, cart.lines.size, cart.lines.size) + "  ›",
                        Modifier.padding(end = 12.dp), color = CuadraColors.Line, style = MaterialTheme.typography.bodyMedium,
                    )
                    if (readerReady) ReaderReadyTag()
                }
                MoneyText(money(cart.totalMinor), Modifier.fillMaxWidth(), color = CuadraColors.Bg, style = MaterialTheme.typography.displayMedium)
            }
            if (printer != PrinterBadge.OFF) PrinterBadgeButton(printer, onPrinter)
            ScanButton(onScan, size = 48.dp)
        }
    }
}

/** El indicador de la impresora (solo con la opción activada): verde conectada, gris conectando, naranja tachada sin conexión. Se toca para abrir sus ajustes. */
@Composable
private fun PrinterBadgeButton(badge: PrinterBadge, onClick: () -> Unit) {
    val (icon, tint, description) = when (badge) {
        PrinterBadge.CONNECTED -> Triple(R.drawable.ic_printer, CuadraColors.OnInkGreen, R.string.printer_badge_connected)
        PrinterBadge.CONNECTING -> Triple(R.drawable.ic_printer, CuadraColors.OnInkGrey, R.string.printer_badge_connecting)
        else -> Triple(R.drawable.ic_printer_off, CuadraColors.OnInkOrange, R.string.printer_badge_disconnected)
    }
    val text = stringResource(description)
    Box(Modifier.size(48.dp).clickable(role = Role.Button, onClick = onClick).semantics { contentDescription = text }, contentAlignment = Alignment.Center) {
        Icon(painterResource(icon), contentDescription = null, tint = tint, modifier = Modifier.size(26.dp))
    }
}


/** «Lector listo»: icono y texto pequeños en la tarjeta del total (no se toca; solo informa). */
@Composable
private fun ReaderReadyTag() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        androidx.compose.material3.Icon(androidx.compose.ui.res.painterResource(R.drawable.ic_reader), contentDescription = null, tint = CuadraColors.GreenSoft, modifier = Modifier.size(18.dp))
        Text(stringResource(R.string.reader_ready), color = CuadraColors.GreenSoft, style = MaterialTheme.typography.bodyMedium)
    }
}

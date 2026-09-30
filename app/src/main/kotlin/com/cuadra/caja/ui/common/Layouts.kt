package com.cuadra.caja.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.layout.ParentDataModifier
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max

/** Etiqueta de prueba de las zonas de acciones que DEBEN quedar fijas y a la vista (pie de pantalla, botones de un diálogo). La guardia lo comprueba. */
const val TAG_PINNED_ACTION = "PinnedAction"

/** Etiqueta de prueba de los encabezados fijos de una lista (buscador): pasan por encima de las filas que se desplazan por debajo, a propósito. */
const val TAG_STICKY = "Sticky"

/** Etiqueta de prueba de cada tecla de la calculadora de la caja (0-9, ., ⌫, ×, Agregar): la guardia comprueba que ninguna quede oculta ni tapada. */
const val TAG_KEY = "Key"

/** Etiqueta de prueba de la tarjeta oscura del total de la caja: no debe moverse cuando aparece un aviso flotante. */
const val TAG_TOTAL_CARD = "TotalCard"

/** Etiqueta de prueba de la barra inferior fija de la caja (con la tira «Deshacer»): nada de la calculadora puede quedar debajo. */
const val TAG_BOTTOM_BAR = "BottomBar"

/** Etiqueta de prueba del número del botón «Recibo · N» de la barra inferior: la guardia exige que se dibuje ENTERO (sin recorte ni letra reducida). */
const val TAG_RECEIPT_BADGE = "ReceiptBadge"

/** Etiqueta de prueba del centro de una `ScreenFrame`. La guardia comprueba que conserve un alto útil. */
const val TAG_SCREEN_BODY = "ScreenBody"

/*
 * Piezas de disposición que aplican las reglas de docs/notas/reglas-de-interfaz-app.md: lo que no cabe baja de línea en vez de apretarse
 * o recortarse. La guardia de diseño (ui/guard) comprueba el resultado con letra 0.85x–2.0x y anchos de 320–411 dp.
 */

/**
 * Fila «contenido | valor» (un nombre y su monto, una etiqueta y su cifra). Si el valor ocupa más de la mitad del ancho, baja a otra línea,
 * alineado a la derecha: el nombre nunca queda reducido a una tira de letras y el monto nunca se aprieta.
 */
@Composable
fun SplitRow(
    modifier: Modifier = Modifier,
    spacing: Dp = 10.dp,
    endMaxFraction: Float = 0.5f,
    verticalAlignment: Alignment.Vertical = Alignment.CenterVertically,
    end: @Composable () -> Unit,
    start: @Composable () -> Unit,
) {
    Layout(content = { Box { start() }; Box { end() } }, modifier = modifier) { measurables, constraints ->
        val gap = spacing.roundToPx()
        val bounded = constraints.hasBoundedWidth
        val total = if (bounded) constraints.maxWidth else Constraints.Infinity
        val endNatural = measurables[1].maxIntrinsicWidth(Constraints.Infinity)
        // Lado a lado solo si el valor no es enorme Y al contenido le queda al menos su palabra más larga (nunca lo aprieta hasta partirla).
        val startMin = measurables[0].minIntrinsicWidth(Constraints.Infinity)
        val sideBySide = !bounded || (endNatural + gap <= (total * endMaxFraction).toInt() && endNatural + gap + startMin <= total)
        if (sideBySide) {
            val e = measurables[1].measure(Constraints(maxWidth = if (bounded) endNatural.coerceAtMost(total) else Constraints.Infinity))
            val s = measurables[0].measure(Constraints(maxWidth = if (bounded) (total - e.width - gap).coerceAtLeast(0) else Constraints.Infinity))
            val h = max(s.height, e.height)
            val w = if (bounded) total else s.width + gap + e.width
            layout(w, h) {
                s.placeRelative(0, verticalAlignment.align(s.height, h))
                e.placeRelative(w - e.width, verticalAlignment.align(e.height, h))
            }
        } else {
            val s = measurables[0].measure(Constraints(maxWidth = total))
            val e = measurables[1].measure(Constraints(maxWidth = total))
            val vGap = (gap / 3)
            layout(total, s.height + vGap + e.height) {
                s.placeRelative(0, 0)
                e.placeRelative(total - e.width, s.height + vGap)
            }
        }
    }
}

/**
 * Encabezado de pantalla: botón a la izquierda (atrás), botón a la derecha (volver, editar) y el título. Si el título cabe en una o dos líneas entre los
 * botones, van en la misma fila; si no (nombre largo, letra grande), los botones quedan arriba y el título baja a todo el ancho. El título nunca se
 * reduce a una columna angosta de letras.
 */
@Composable
fun TitleBar(modifier: Modifier = Modifier, start: (@Composable () -> Unit)? = null, end: (@Composable () -> Unit)? = null, spacing: Dp = 10.dp, title: @Composable () -> Unit) {
    Layout(content = { Box { start?.invoke() }; Box { end?.invoke() }; Box { title() } }, modifier = modifier) { measurables, constraints ->
        val gap = spacing.roundToPx()
        val total = if (constraints.hasBoundedWidth) constraints.maxWidth else Constraints.Infinity
        val loose = Constraints(maxWidth = total)
        val s = measurables[0].measure(loose)
        val e = measurables[1].measure(loose)
        val gaps = (if (s.width > 0) gap else 0) + (if (e.width > 0) gap else 0)
        val avail = (total - s.width - e.width - gaps).coerceAtLeast(0)
        val natural = measurables[2].maxIntrinsicWidth(Constraints.Infinity)
        if (total == Constraints.Infinity || natural <= avail * 1.5f) {
            val t = measurables[2].measure(Constraints(maxWidth = if (total == Constraints.Infinity) Constraints.Infinity else avail))
            val h = maxOf(s.height, e.height, t.height)
            val w = if (total == Constraints.Infinity) s.width + e.width + t.width + gaps else total
            layout(w, h) {
                s.placeRelative(0, (h - s.height) / 2)
                t.placeRelative(s.width + if (s.width > 0) gap else 0, (h - t.height) / 2)
                e.placeRelative(w - e.width, (h - e.height) / 2)
            }
        } else {
            val t = measurables[2].measure(Constraints(maxWidth = total))
            val row = maxOf(s.height, e.height)
            val vGap = if (row > 0) gap / 2 else 0
            layout(total, row + vGap + t.height) {
                s.placeRelative(0, (row - s.height) / 2)
                e.placeRelative(total - e.width, (row - e.height) / 2)
                t.placeRelative(0, row + vGap)
            }
        }
    }
}

/** Grupo de opciones (chips, etiquetas): baja a otra línea en vez de desplazarse de lado o recortarse. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChipFlow(modifier: Modifier = Modifier, spacing: Dp = 8.dp, alignEnd: Boolean = false, content: @Composable () -> Unit) {
    FlowRow(modifier, horizontalArrangement = if (alignEnd) Arrangement.spacedBy(spacing, Alignment.End) else Arrangement.spacedBy(spacing), verticalArrangement = Arrangement.spacedBy(spacing), itemVerticalAlignment = Alignment.CenterVertically) { content() }
}

/**
 * Opciones de igual ancho en una fila (pestañas, «Texto | Imagen», periodos). Si todas caben con su tamaño normal se reparten el ancho por igual;
 * si no, se acomodan en una cuadrícula de menos columnas (todas del mismo ancho, en varias filas). Nada se aprieta ni se corta.
 */
@Composable
fun ChipGrid(modifier: Modifier = Modifier, spacing: Dp = 8.dp, content: @Composable () -> Unit) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val gap = spacing.roundToPx()
        val total = if (constraints.hasBoundedWidth) constraints.maxWidth else Constraints.Infinity
        val n = measurables.size
        if (n == 0) return@Layout layout(0, 0) {}
        val natural = measurables.maxOf { it.maxIntrinsicWidth(Constraints.Infinity) }
        val cols = if (total == Constraints.Infinity) n else ((total + gap) / (natural + gap)).coerceIn(1, n)
        val cell = if (total == Constraints.Infinity) natural else (total - gap * (cols - 1)) / cols
        val placeables = measurables.map { it.measure(Constraints(minWidth = cell, maxWidth = cell)) }
        val rows = placeables.chunked(cols)
        val rowHeights = rows.map { r -> r.maxOf { it.height } }
        val h = rowHeights.sum() + gap * (rows.size - 1)
        val w = if (total == Constraints.Infinity) cell * cols + gap * (cols - 1) else total
        layout(w, h) {
            var y = 0
            rows.forEachIndexed { ri, r ->
                r.forEachIndexed { ci, p -> p.placeRelative(ci * (cell + gap), y + (rowHeights[ri] - p.height) / 2) }
                y += rowHeights[ri] + gap
            }
        }
    }
}

@Stable
interface ButtonRowScope {
    /** Parte del ancho que le toca a este botón (como `weight` de una fila). */
    fun Modifier.share(weight: Float): Modifier
}

private class ShareData(val weight: Float) : ParentDataModifier {
    override fun Density.modifyParentData(parentData: Any?): Any = this@ShareData
}

private object ButtonRowScopeImpl : ButtonRowScope {
    override fun Modifier.share(weight: Float): Modifier = this.then(ShareData(weight))
}

/**
 * Botones lado a lado que se reparten el ancho según `share(peso)`; si alguno no cabe COMPLETO con su tamaño normal, todos se apilan
 * (a todo el ancho, en el mismo orden). Así «Cancelar / Guardar · C$ 1,234.00» nunca se aprieta ni se corta con letra grande.
 */
@Composable
fun ButtonRow(modifier: Modifier = Modifier, spacing: Dp = 8.dp, content: @Composable ButtonRowScope.() -> Unit) {
    Layout(content = { ButtonRowScopeImpl.content() }, modifier = modifier) { measurables, constraints ->
        val gap = spacing.roundToPx()
        val total = if (constraints.hasBoundedWidth) constraints.maxWidth else Constraints.Infinity
        val weights = measurables.map { (it.parentData as? ShareData)?.weight ?: 1f }
        val sum = weights.sum()
        val usable = if (total == Constraints.Infinity) Int.MAX_VALUE / 4 else total - gap * (measurables.size - 1)
        val fits = measurables.indices.all { i -> measurables[i].maxIntrinsicWidth(Constraints.Infinity) <= (usable * weights[i] / sum).toInt() }
        if (measurables.size <= 1 || fits) {
            val widths = measurables.indices.map { i -> if (total == Constraints.Infinity) null else (usable * weights[i] / sum).toInt() }
            val placeables = measurables.mapIndexed { i, m -> m.measure(if (widths[i] != null) Constraints.fixedWidth(widths[i]!!) else Constraints()) }
            val h = placeables.maxOfOrNull { it.height } ?: 0
            val w = if (total == Constraints.Infinity) placeables.sumOf { it.width } + gap * (placeables.size - 1) else total
            layout(w, h) {
                var x = 0
                placeables.forEach { p -> p.placeRelative(x, (h - p.height) / 2); x += p.width + gap }
            }
        } else {
            val placeables = measurables.map { it.measure(Constraints(minWidth = if (total == Constraints.Infinity) 0 else total, maxWidth = total)) }
            val h = placeables.sumOf { it.height } + gap * (placeables.size - 1)
            layout(if (total == Constraints.Infinity) placeables.maxOf { it.width } else total, h) {
                var y = 0
                placeables.forEach { p -> p.placeRelative(0, y); y += p.height + gap }
            }
        }
    }
}

/**
 * Pantalla con encabezado y pie fijos y un centro que se desplaza. Con letra grande el encabezado y el pie no se comen la pantalla: cada uno
 * se limita a una fracción del alto y, si lo pasa, se desplaza por dentro; así el centro siempre conserva espacio y el pie (acciones principales)
 * siempre se ve. `dock` (opcional) va entre el centro y el pie y no se limita ni se desplaza: la caja lo usa para la calculadora.
 */
@Composable
fun ScreenFrame(
    modifier: Modifier = Modifier,
    spacing: Dp = 10.dp,
    headerMaxFraction: Float = 0.45f,
    footerMaxFraction: Float = 0.4f,
    header: (@Composable ColumnScope.() -> Unit)? = null,
    footer: (@Composable ColumnScope.() -> Unit)? = null,
    dock: (@Composable ColumnScope.() -> Unit)? = null,
    body: @Composable ColumnScope.() -> Unit,
) {
    BoxWithConstraints(modifier) {
        val h = maxHeight
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(spacing)) {
            if (header != null) Column(
                Modifier.fillMaxWidth().heightIn(max = h * headerMaxFraction).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(spacing), content = header,
            )
            Column(Modifier.fillMaxWidth().weight(1f, fill = true).testTag(TAG_SCREEN_BODY), content = body)
            // `dock`: lo que va pegado abajo y NUNCA se desplaza ni se recorta (la calculadora): el centro cede su espacio, no la calculadora.
            if (dock != null) Column(Modifier.fillMaxWidth().testTag(TAG_PINNED_ACTION), verticalArrangement = Arrangement.spacedBy(spacing), content = dock)
            if (footer != null) Column(
                Modifier.fillMaxWidth().heightIn(max = h * footerMaxFraction).testTag(TAG_PINNED_ACTION).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(spacing), content = footer,
            )
        }
    }
}

/**
 * Disposición de la caja con PRIORIDADES medidas (no adivinadas): de arriba abajo `header`, `hero` (la tarjeta oscura del total), `body` (pestañas y su contenido),
 * `dock` (descripción, monto y teclas) y `footer` (barra de tres acciones).
 *
 * Orden de prioridad (lo primero que se garantiza): footer > dock > hero > body (mínimo: una fila de pestañas) > encabezado (compacto, luego completo) > resto del body.
 * Es decir: el total NUNCA se recorta ni queda detrás de la calculadora; si no hay alto para todo, el encabezado se acorta (primero pierde sus líneas de estado, luego
 * se desplaza por dentro) y, en un extremo, no se dibuja. Lo que no cabe NO se compone (no queda una pieza invisible que un lector de pantalla pueda enfocar).
 * `overlay` (aviso «Deshacer», avisos de impresión) no ocupa lugar ni mueve nada, y nunca tapa las pestañas, el total, las teclas ni la barra: va centrado en el
 * hueco que queda libre entre las pestañas y la calculadora (en «Manual» con alto de sobra); en «Productos», al pie de la lista, justo arriba de la barra;
 * si no, encima de las líneas centradas del encabezado; y si no hay ningún lugar seguro (extremo: teclado del sistema abierto), no se compone.
 *
 * `bodyNatural`: el cuerpo es solo la fila de pestañas (mide lo que mide); si no, es una lista que toma el alto que sobre (mínimo [BODY_MIN]).
 * `header(full)`: `full = false` es la fila compacta (nombre, «Apartadas», cambiar de cajero); `true` añade las líneas de estado (quién atiende, vendido hoy).
 */
@Composable
fun RegisterFrame(
    modifier: Modifier = Modifier,
    spacing: Dp = 4.dp,
    bodyNatural: Boolean,
    header: @Composable (full: Boolean) -> Unit,
    hero: @Composable () -> Unit,
    body: @Composable () -> Unit,
    dock: (@Composable () -> Unit)? = null,
    footer: @Composable () -> Unit,
    overlay: (@Composable () -> Unit)? = null,
    /** El aviso flotante es el destacado (vuelto tras cobrar: letra más grande, hasta 2 líneas): se reserva su alto mayor. */
    overlayLarge: Boolean = false,
) {
    SubcomposeLayout(modifier) { constraints ->
        val w = constraints.maxWidth
        val bounded = constraints.hasBoundedHeight
        val h = if (bounded) constraints.maxHeight else Int.MAX_VALUE / 4
        val g = spacing.roundToPx()
        val open = Constraints(maxWidth = w)
        fun one(slot: Slot, c: Constraints, content: @Composable () -> Unit) = subcompose(slot) { Box(Modifier.let { m -> if (slot == Slot.DOCK || slot == Slot.FOOTER) m.testTag(TAG_PINNED_ACTION) else m }) { content() } }.map { it.measure(c) }
        val footerP = one(Slot.FOOTER, open, footer)
        val dockP = dock?.let { one(Slot.DOCK, open, it) }.orEmpty()
        val heroP = one(Slot.HERO, open, hero)
        val footerH = footerP.sumOf { it.height }
        val dockH = dockP.sumOf { it.height }
        val heroH = heroP.sumOf { it.height }
        val hasDock = dockH > 0
        // Alto que queda para todo lo demás (los espacios entre bloques ya descontados). Si ni una fila de pestañas cabe, el cuerpo no se compone (extremo).
        val room = h - (heroH + dockH + footerH) - (if (hasDock) 3 else 2) * g
        val bodyOk = room >= BODY_MIN.roundToPx()
        // El cuerpo de solo pestañas se mide ya (su alto natural, con desplazamiento por dentro si no cupiera); la lista se mide al final con el alto que sobre.
        val bodyFirst = if (bodyNatural && bodyOk) subcompose(Slot.BODY) { Box(Modifier.verticalScroll(rememberScrollState())) { body() } }.map { it.measure(Constraints(maxWidth = w, maxHeight = room)) } else emptyList()
        val bodyMin = if (!bodyOk) 0 else if (bodyNatural) bodyFirst.sumOf { it.height } else BODY_MIN.roundToPx()
        // Encabezado: completo si cabe con holgura; si no, la fila compacta (que se desplaza por dentro si apenas cabe); si casi no queda, no se compone.
        val headerRoom = room - bodyMin - g
        // Completo = la fila + «Atiende» + «Vendido hoy» (cada una en su línea, a la izquierda y en negrita) + los márgenes de arriba y abajo.
        val lineH = 20.sp.toPx()
        val fullEstimate = (HEADER_ROW.toPx() + 2 * lineH + 2 * HEADER_GAP.toPx() + 2 * HEADER_LINE_GAP.toPx()).toInt()
        val headerP = if (bodyOk && headerRoom >= HEADER_MIN.roundToPx()) {
            val full = headerRoom >= fullEstimate
            subcompose(Slot.HEADER) { Box(Modifier.verticalScroll(rememberScrollState())) { header(full) } }.map { it.measure(Constraints(maxWidth = w, maxHeight = headerRoom)) }
        } else emptyList()
        val headerH = headerP.sumOf { it.height }
        val top = if (headerH > 0) headerH + g else 0
        val bottomBlock = footerH + g + (if (hasDock) dockH + g else 0)
        val bodyP = when {
            !bodyOk -> emptyList()
            bodyNatural -> bodyFirst
            else -> {
                val left = (h - top - heroH - g - bottomBlock).coerceAtLeast(0)
                subcompose(Slot.BODY) { Box { body() } }.map { it.measure(Constraints(minWidth = w, maxWidth = w, minHeight = left, maxHeight = left)) }
            }
        }
        val bodyH = bodyP.sumOf { it.height }
        val total = if (bounded) constraints.maxHeight else top + heroH + g + bodyH + g + bottomBlock
        // Dónde va el aviso flotante (ver la documentación): se decide con su alto ESTIMADO antes de componerlo (lo que no tiene lugar no se compone)
        // y se comprueba con el medido.
        val bodyBottom = top + heroH + g + bodyH
        val dockTop = total - footerH - g - (if (hasDock) dockH + g else 0)
        val slack = if (bodyNatural && hasDock) dockTop - bodyBottom else 0
        val noticeEstimate = if (overlayLarge) maxOf(48.dp.toPx(), 2 * 22.sp.toPx() * minOf(fontScale, LARGE_NOTICE_FONT_CAP) / fontScale + 16.dp.toPx()).toInt() else maxOf(48.dp.toPx(), (if (fontScale > 1.3f) 2 else 1) * 16.sp.toPx() + 16.dp.toPx()).toInt()
        val tabsReserve = (TABS_RESERVE.toPx() + g).toInt()
        fun zone(oh: Int): Int? = when {
            slack >= oh + 2 * g -> bodyBottom + (slack - oh) / 2
            // «Productos»: al pie de la lista, justo arriba de la barra (como un aviso normal), lejos de las pestañas.
            !bodyNatural && bodyH >= oh + tabsReserve -> bodyBottom - oh
            headerH >= oh -> headerH - oh   // tapa las líneas centradas (lo menos necesario), no la fila del negocio
            else -> null
        }
        val overlayP = if (overlay != null && zone(noticeEstimate) != null) one(Slot.OVERLAY, open, overlay) else emptyList()
        val overlayY = overlayP.maxOfOrNull { it.height }?.let { zone(it) }
        layout(w, total) {
            // Abajo primero (footer y dock pegados al fondo); arriba después: si algo se traslapara en un extremo, el total queda por encima.
            var y = total - footerH
            footerP.forEach { it.placeRelative(0, y) }
            y -= g
            if (hasDock) { y -= dockH; dockP.forEach { it.placeRelative(0, y) } }
            headerP.forEach { it.placeRelative(0, 0) }
            bodyP.forEach { it.placeRelative(0, top + heroH + g) }
            heroP.forEach { it.placeRelative(0, top, zIndex = 1f) }
            if (overlayY != null) overlayP.forEach { it.placeRelative((w - it.width) / 2, overlayY, zIndex = 2f) }
        }
    }
}

private enum class Slot { HEADER, HERO, BODY, DOCK, FOOTER, OVERLAY }

/** Alto mínimo reservado al cuerpo de la caja (una fila de pestañas). */
private val BODY_MIN = 48.dp

/** Alto de la fila compacta del encabezado (los botones miden 48 dp). */
private val HEADER_ROW = 48.dp

/** Margen de arriba y de abajo de la fila del encabezado, y entre sus líneas centradas. */
internal val HEADER_GAP = 6.dp
internal val HEADER_LINE_GAP = 2.dp

/** Lo que se deja libre arriba de la lista de «Productos» (la fila de pestañas) al poner ahí el aviso flotante. */
private val TABS_RESERVE = 72.dp

/** Menos que esto no vale la pena dibujar el encabezado (se ve cortado): en un extremo simplemente no se dibuja. */
private val HEADER_MIN = 48.dp

/**
 * Descripción y monto tecleado en UNA fila si caben (la descripción toma lo que deja el monto, al menos [DESC_MIN]); si el monto es enorme, uno sobre otro. Ahorra una
 * fila entera de alto en teléfonos bajos, que es lo que permite ver el total, las pestañas y el encabezado a la vez.
 */
@Composable
fun DescriptionAmountRow(spacing: Dp = 8.dp, description: @Composable () -> Unit, amount: @Composable () -> Unit) {
    Layout(content = { Box { description() }; Box { amount() } }) { measurables, constraints ->
        val gap = spacing.roundToPx()
        val total = constraints.maxWidth
        val amountNatural = measurables[1].maxIntrinsicWidth(Int.MAX_VALUE)
        val descMin = DESC_MIN.roundToPx()
        val side = amountNatural + gap + descMin <= total
        if (side) {
            val a = measurables[1].measure(Constraints(maxWidth = amountNatural))
            val d = measurables[0].measure(Constraints(minWidth = total - a.width - gap, maxWidth = total - a.width - gap))
            val hh = maxOf(a.height, d.height)
            layout(total, hh) { d.placeRelative(0, (hh - d.height) / 2); a.placeRelative(total - a.width, (hh - a.height) / 2) }
        } else {
            val d = measurables[0].measure(Constraints(minWidth = total, maxWidth = total))
            val a = measurables[1].measure(Constraints(minWidth = total, maxWidth = total))
            layout(total, d.height + gap + a.height) { d.placeRelative(0, 0); a.placeRelative(0, d.height + gap) }
        }
    }
}

/** Ancho mínimo útil del campo de descripción al lado del monto. */
private val DESC_MIN = 150.dp

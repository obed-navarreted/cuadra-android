package com.cuadra.caja.ui.screens

import androidx.compose.animation.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.times
import com.cuadra.caja.R
import com.cuadra.caja.domain.CartLine
import com.cuadra.caja.domain.LastLine
import com.cuadra.caja.ui.CajaActions
import com.cuadra.caja.ui.common.CappedFontScale
import com.cuadra.caja.ui.common.CuadraCard
import com.cuadra.caja.ui.common.MoneyText
import com.cuadra.caja.ui.common.STRIP_FONT_CAP
import com.cuadra.caja.ui.common.STRIP_FULL_HEIGHT
import com.cuadra.caja.ui.common.STRIP_ROW_HEIGHT
import com.cuadra.caja.ui.common.Text
import com.cuadra.caja.ui.common.money
import com.cuadra.caja.ui.theme.CuadraColors

/** Etiqueta de prueba de la tira de la última línea (la guardia comprueba que nunca se traslape con las pestañas, el total, las teclas ni la barra). */
const val TAG_LAST_LINE = "LastLineStrip"

/** Etiqueta de prueba del número de la cantidad dentro de la tira (la guardia exige que se vea entero, en una línea y sin «…»). */
const val TAG_STRIP_QTY = "LastLineStripQty"

/** Etiqueta del texto del hueco reservado (recibo vacío, solo en «Productos»). */
const val TAG_STRIP_EMPTY = "LastLineStripEmpty"

/**
 * Cómo se dibuja la tira en «Productos» según el alto del cuerpo (lo que queda bajo el total): completa si la lista conserva un buen trozo, compacta (solo los
 * controles) si no, y nada en el extremo (teléfono bajo con letra enorme: la lista y el buscador no pueden quedarse sin lugar).
 */
enum class StripMode {
    FULL, COMPACT, NONE;

    companion object {
        /** Lo que ocupan las pestañas, el buscador y sus márgenes a letra 1.0× (crece con la letra, hasta 1.3×). */
        private val CHROME = 132.dp

        /** Lo que debe quedarle a la lista debajo de la tira: con la completa, algo más de una fila de mosaicos; con la compacta, lo mínimo útil. */
        private val LIST_FULL = 200.dp
        private val LIST_COMPACT = 96.dp

        /** El cuerpo mínimo con el que «Productos» puede dibujar la tira compacta (el marco hace ceder el encabezado antes de quitársela). */
        fun compactBodyMin(fontFactor: Float): androidx.compose.ui.unit.Dp = CHROME * fontFactor.coerceIn(1f, 1.3f) + STRIP_ROW_HEIGHT + LIST_COMPACT

        fun forProductsBody(bodyHeight: androidx.compose.ui.unit.Dp, fontFactor: Float): StripMode {
            val chrome = CHROME * fontFactor.coerceIn(1f, 1.3f)
            return when {
                bodyHeight >= chrome + STRIP_FULL_HEIGHT + LIST_FULL -> FULL
                bodyHeight >= chrome + STRIP_ROW_HEIGHT + LIST_COMPACT -> COMPACT
                else -> NONE
            }
        }
    }
}

/** Lo más ancha que puede ser la cantidad escrita (999, 12345.678): pasado esto la letra baja, nunca se corta. */
private val QTY_MAX_WIDTH = 96.dp

/** Margen interior (a los dos lados) de la cantidad y del subtotal dentro de su caja. */
private val QTY_PADDING = 12.dp
private val TOTAL_PADDING = 8.dp

private val StripShape = RoundedCornerShape(16.dp)

/**
 * La tira de la ÚLTIMA línea del recibo (pedido del dueño: «escaneo una vez y luego tengo que abrir el recibo para cambiar la cantidad»). Una sola línea, la
 * última agregada o tocada (`LastLine`), con su cantidad editable en pantalla: `–  [ 6 ]  +`, el subtotal y una ✕ para quitarla.
 *
 * - Arriba el nombre (una línea; es de lo poco que puede terminar en «…», deliberado) y a su lado el precio de cada uno; abajo los controles. Con `compact`
 *   (poco alto o letra enorme) queda solo la fila de controles: el nombre cede antes que el − / +, la cantidad o el subtotal.
 * - − y + usan `LastLine.step` (una unidad; un cuarto si la cantidad ya es fraccionaria); − en 1 quita la línea (con «Deshacer»). Tocar el número abre la hoja de
 *   cantidad (acepta decimales en los productos por peso). ✕ quita la línea y el aviso «Línea quitada · Deshacer» deja recuperarla.
 * - Un destello verde breve al agregar o cambiar la cantidad; no mueve nada.
 * - `line = null` dibuja el hueco reservado de la pestaña Productos (para que la lista no salte al agregar el primer producto).
 */
@Composable
fun LastLineStrip(line: CartLine?, compact: Boolean, actions: CajaActions, modifier: Modifier = Modifier) {
    CappedFontScale(STRIP_FONT_CAP) {
        if (line == null) EmptyStrip(compact, modifier) else FilledStrip(line, compact, actions, modifier)
    }
}

@Composable
private fun EmptyStrip(compact: Boolean, modifier: Modifier) {
    Box(
        modifier.fillMaxWidth().testTag(TAG_LAST_LINE).heightIn(min = if (compact) STRIP_ROW_HEIGHT else STRIP_FULL_HEIGHT)
            .background(CuadraColors.Soft, StripShape).border(BorderStroke(1.dp, CuadraColors.Line), StripShape).padding(horizontal = 12.dp, vertical = 4.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(stringResource(R.string.strip_empty), Modifier.testTag(TAG_STRIP_EMPTY), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, ellipsize = true)
    }
}

@Composable
private fun FilledStrip(line: CartLine, compact: Boolean, actions: CajaActions, modifier: Modifier) {
    // Destello al agregar o cambiar la cantidad (no al dibujarse por primera vez con una línea que ya estaba).
    val flash = remember { Animatable(CuadraColors.Surface) }
    val first = remember { booleanArrayOf(true) }
    LaunchedEffect(line.id, line.quantityMilli) {
        if (first[0]) { first[0] = false; return@LaunchedEffect }
        flash.snapTo(CuadraColors.GreenSoft)
        flash.animateTo(CuadraColors.Surface, tween(durationMillis = 700))
    }
    val height = if (compact) STRIP_ROW_HEIGHT else STRIP_FULL_HEIGHT
    CuadraCard(
        modifier.testTag(TAG_LAST_LINE).heightIn(min = height), color = flash.value, padding = PaddingValues(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
    ) {
        if (compact) {
            // Una sola fila: − cantidad + · subtotal · ✕. El nombre cede antes que cualquiera de los controles.
            CompactRow(line, actions, height - 10.dp)
        } else {
            Column(Modifier.fillMaxWidth().heightIn(min = height - 10.dp), verticalArrangement = Arrangement.Center) {
                // Arriba el nombre (con el precio de cada uno) y el subtotal; abajo los controles.
                Row(Modifier.fillMaxWidth().heightIn(min = 28.dp).padding(end = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(line.label + "  ·  " + money(line.unitPriceMinor), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, ellipsize = true)
                    Total(line, Modifier.widthIn(max = 160.dp))
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Stepper(line, actions)
                    Spacer(Modifier.weight(1f))
                    RemoveButton(line, actions)
                }
            }
        }
    }
}

/**
 * La fila compacta (sin nombre). La cantidad y el subtotal se reparten el ancho que dejan − + y ✕ EN PROPORCIÓN a lo que miden (medido, no adivinado): con una
 * cantidad de 8 cifras y un monto de 7 cifras en un teléfono angosto las dos letras bajan juntas (hasta 40 %), nunca una se come a la otra.
 */
@Composable
private fun CompactRow(line: CartLine, actions: CajaActions, minHeight: androidx.compose.ui.unit.Dp) {
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val density = androidx.compose.ui.platform.LocalDensity.current
    val style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold)
    BoxWithConstraints(Modifier.fillMaxWidth().heightIn(min = minHeight)) {
        fun w(text: String) = with(density) { measurer.measure(androidx.compose.ui.text.AnnotatedString(text), style, softWrap = false, maxLines = 1).size.width.toDp() }
        val qtyNatural = w(qtyText(line.quantityMilli)) + QTY_PADDING
        val totalNatural = w(money(line.totalMinor)) + TOTAL_PADDING
        // Lo que queda entre los tres botones de 48 dp (−, + y ✕).
        val room = maxWidth - 3 * 48.dp
        val f = if (qtyNatural + totalNatural <= room) 1f else (room / (qtyNatural + totalNatural)).coerceAtLeast(0.3f)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Stepper(line, actions, qtyWidth = maxOf(qtyNatural * f, 48.dp))
            Total(line, Modifier.weight(1f).padding(horizontal = TOTAL_PADDING / 2))
            RemoveButton(line, actions)
        }
    }
}

/** El subtotal de la línea con la promoción ya aplicada (en verde cuando una promoción le descuenta algo: «7 × 45» se ve «245»). */
@Composable
private fun Total(line: CartLine, modifier: Modifier) {
    MoneyText(money(line.totalMinor), modifier, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.End,
        color = if (line.discountMinor > 0) CuadraColors.Green else androidx.compose.ui.graphics.Color.Unspecified)
}

/** `–  [ 6 ]  +`: − y + de 48 dp tocables (círculo de 36 dp), y la cantidad en un campo que se toca para escribirla. */
@Composable
private fun Stepper(line: CartLine, actions: CajaActions, qtyWidth: androidx.compose.ui.unit.Dp? = null) {
    val minus = stringResource(R.string.receipt_minus)
    val plus = stringResource(R.string.receipt_plus)
    val qtyDescription = stringResource(R.string.strip_qty, qtyText(line.quantityMilli))
    Row(verticalAlignment = Alignment.CenterVertically) {
        StepButton("−", minus, enabled = true) {
            // − en la última unidad quita la línea (con «Deshacer»), igual que la ✕.
            val target = LastLine.minus(line)
            if (target <= 0L) actions.deleteLine(line.id) else actions.changeQuantity(line.id, target - line.quantityMilli)
        }
        Box(
            (if (qtyWidth != null) Modifier.width(qtyWidth) else Modifier.defaultMinSize(minWidth = 48.dp)).heightIn(min = 48.dp).clickable(role = Role.Button) { actions.editQuantity(line.id) }.semantics { contentDescription = qtyDescription },
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.defaultMinSize(minWidth = 44.dp).heightIn(min = 36.dp).background(CuadraColors.Bg, RoundedCornerShape(10.dp)).border(BorderStroke(1.dp, CuadraColors.Line), RoundedCornerShape(10.dp)).padding(horizontal = QTY_PADDING / 2), contentAlignment = Alignment.Center) {
                MoneyText(qtyText(line.quantityMilli), Modifier.widthIn(max = QTY_MAX_WIDTH).testTag(TAG_STRIP_QTY), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
            }
        }
        StepButton("+", plus, enabled = !LastLine.atMax(line)) { actions.changeQuantity(line.id, LastLine.plus(line) - line.quantityMilli) }
    }
}

@Composable
private fun StepButton(symbol: String, description: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(48.dp).alpha(if (enabled) 1f else 0.4f).clickable(enabled = enabled, role = Role.Button, onClick = onClick).semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(36.dp).background(CuadraColors.Soft, CircleShape), contentAlignment = Alignment.Center) {
            Text(symbol, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, maxLines = 1, minScale = 0.6f)
        }
    }
}

/** ✕ «Quitar esta línea»: la quita (con «Línea quitada · Deshacer»). */
@Composable
private fun RemoveButton(line: CartLine, actions: CajaActions) {
    val description = stringResource(R.string.strip_remove)
    Box(
        Modifier.size(48.dp).clickable(role = Role.Button) { actions.deleteLine(line.id) }.semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { Text("✕", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, minScale = 0.6f) }
}

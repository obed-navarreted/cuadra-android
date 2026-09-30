package com.cuadra.caja.ui.common

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LastBaseline
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.semantics.getTextLayoutResult
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Etiqueta de prueba de los textos que PUEDEN terminar en «…» a propósito (nombres muy largos en una lista, el nombre del negocio en el
 * encabezado). La guardia de diseño (`ui/guard`) falla ante cualquier otro texto recortado. Mantén esta lista corta y deliberada.
 */
const val TAG_ALLOW_ELLIPSIS = "AllowEllipsis"

/** Dentro de la etiqueta o el texto de ayuda de un campo (`label`, `placeholder`): una sola línea que, si no cabe, baja la letra y termina en «…» (como Material). */
val LocalFieldHint = androidx.compose.runtime.staticCompositionLocalOf { false }

/** Una palabra más larga que esto (una URL, un código, una referencia de 30 caracteres) puede partirse si no hay otra forma; las normales nunca. */
const val MAX_UNBREAKABLE_WORD = 20

/** `true` si alguna línea termina en medio de una palabra normal ("Eng/lish"): con letra grande una palabra que no cabe se parte y se ve mal. */
fun TextLayoutResult.splitsWord(): Boolean = splitWord() != null

/** La palabra (de hasta [MAX_UNBREAKABLE_WORD] letras) que se parte entre dos líneas, o `null`. */
fun TextLayoutResult.splitWord(): String? {
    val t = layoutInput.text.text
    for (i in 0 until lineCount - 1) {
        val end = getLineEnd(i, visibleEnd = false)
        if (end in 1 until t.length && t[end - 1].isLetterOrDigit() && t[end].isLetterOrDigit()) {
            var a = end - 1
            while (a > 0 && t[a - 1].isLetterOrDigit()) a--
            var b = end
            while (b < t.length && t[b].isLetterOrDigit()) b++
            if (b - a <= MAX_UNBREAKABLE_WORD) return t.substring(a, b)
        }
    }
    return null
}

/** Un texto está bien puesto si no se recorta y ninguna palabra se parte. */
internal fun TextLayoutResult.isClean(): Boolean = !hasVisualOverflow && !splitsWord()

/**
 * Texto de la app (reemplaza a `androidx.compose.material3.Text`; la guardia de código prohíbe importar el de Material en las pantallas).
 *
 * Regla: **nunca recorta en silencio**. Si el texto no cabe con el tamaño normal, primero baja la letra (hasta `minScale`, 50 % por omisión) y, si aun así
 * no cabe, sigue en más líneas si `maxLines` lo permite. Una palabra nunca se parte en dos líneas. Solo con `ellipsize = true` (deliberado y
 * etiquetado con [TAG_ALLOW_ELLIPSIS]) termina en «…».
 *
 * - `maxLines = 1` o `softWrap = false`: una sola línea, que se achica hasta caber (montos, títulos, botones).
 * - `ellipsize = true`: el texto se corta con «…» al llegar a `maxLines` (nombres largos en listas); la letra baja (hasta 80 %) solo para no partir una palabra.
 * - Sin `maxLines`: crece todo lo necesario; solo achica si una palabra sola no cabe en el ancho.
 */
@Composable
fun Text(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontWeight: FontWeight? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    maxLines: Int = Int.MAX_VALUE,
    softWrap: Boolean = true,
    style: TextStyle = LocalTextStyle.current,
    ellipsize: Boolean = false,
    minScale: Float = if (ellipsize) 0.8f else 0.5f,
) {
    val measurer = rememberTextMeasurer(cacheSize = 4)
    val inFieldHint = LocalFieldHint.current
    @Suppress("NAME_SHADOWING") val ellipsize = ellipsize || inFieldHint
    @Suppress("NAME_SHADOWING") val maxLines = if (inFieldHint && maxLines == Int.MAX_VALUE) 1 else maxLines
    val resolved = LocalTextStyle.current.merge(style).merge(
        TextStyle(color = color, fontSize = fontSize, fontWeight = fontWeight, textAlign = textAlign ?: TextAlign.Unspecified, lineHeight = lineHeight, textDecoration = textDecoration),
    ).let { it.copy(color = it.color.takeIf { c -> c != Color.Unspecified } ?: LocalContentColor.current) }
    val lines = if (!softWrap) 1 else maxLines
    var result by remember { mutableStateOf<TextLayoutResult?>(null) }
    val policy = remember(text, resolved, lines, ellipsize, minScale, measurer) {
        TextPolicy(text, resolved, lines, ellipsize, minScale, measurer) { result = it }
    }
    Layout(
        modifier = modifier
            .then(if (ellipsize) Modifier.testTag(TAG_ALLOW_ELLIPSIS) else Modifier)
            .semantics {
                this.text = AnnotatedString(text)
                getTextLayoutResult { list -> result?.let { list.add(it); true } ?: false }
            }
            .then(if (ellipsize || minScale < 1f) Modifier.clipToBounds() else Modifier)
            .drawBehind { result?.let { drawText(it) } },
        measurePolicy = policy,
    )
}

/** Un monto: siempre completo y en una línea; si no cabe se achica (hasta 40 %), nunca se recorta con «…». */
@Composable
fun MoneyText(
    text: String, modifier: Modifier = Modifier, color: Color = Color.Unspecified, fontWeight: FontWeight? = null, style: TextStyle = LocalTextStyle.current,
    textAlign: TextAlign? = null, textDecoration: TextDecoration? = null, fontSize: TextUnit = TextUnit.Unspecified, minScale: Float = 0.4f,
) = Text(text, modifier, color, fontSize, fontWeight, textAlign, textDecoration = textDecoration, maxLines = 1, style = style, minScale = minScale)

private class TextPolicy(
    val text: String, val style: TextStyle, val maxLines: Int, val ellipsize: Boolean, val minScale: Float, val measurer: TextMeasurer,
    val publish: (TextLayoutResult) -> Unit,
) : MeasurePolicy {

    private fun scaled(scale: Float, density: Density): TextStyle {
        if (scale >= 0.999f) return style
        val size = if (style.fontSize.isSpecified) style.fontSize else 14.sp
        return style.copy(
            fontSize = size * scale,
            lineHeight = if (style.lineHeight.isSpecified) style.lineHeight * scale else TextUnit.Unspecified,
        )
    }

    private fun measure(scale: Float, c: Constraints, density: Density, dir: LayoutDirection, overflow: TextOverflow = TextOverflow.Clip) =
        measurer.measure(
            AnnotatedString(text), scaled(scale, density), overflow, true, maxLines,
            constraints = c, layoutDirection = dir, density = density,
        )

    /** Un texto `ellipsize` puede terminar en «…» (eso es lo deliberado): solo baja la letra si una palabra se partiría. Los demás deben caber completos. */
    private fun ok(r: TextLayoutResult) = if (ellipsize) !r.splitsWord() else r.isClean()

    fun fit(c: Constraints, density: Density, dir: LayoutDirection): TextLayoutResult {
        val overflow = if (ellipsize && maxLines != Int.MAX_VALUE) TextOverflow.Ellipsis else TextOverflow.Clip
        var scale = 1f
        var r = measure(scale, c, density, dir, overflow)
        while (!ok(r) && scale > minScale + 0.001f) {
            scale = max(minScale, scale * 0.93f)
            r = measure(scale, c, density, dir, overflow)
        }
        // Ya no cabe ni con la letra más chica: se muestra lo que cabe con «…» (y la guardia de diseño lo reporta si el texto no es `ellipsize`).
        if (!ok(r) && maxLines != Int.MAX_VALUE && overflow != TextOverflow.Ellipsis) r = measure(scale, c, density, dir, TextOverflow.Ellipsis)
        return r
    }

    override fun MeasureScope.measure(measurables: List<Measurable>, constraints: Constraints): MeasureResult {
        val r = fit(constraints, this, layoutDirection)
        publish(r)
        val w = r.size.width.coerceIn(constraints.minWidth, max(constraints.minWidth, constraints.maxWidth))
        val h = r.size.height.coerceIn(constraints.minHeight, max(constraints.minHeight, constraints.maxHeight))
        return layout(w, h, mapOf(FirstBaseline to r.firstBaseline.roundToInt(), LastBaseline to r.lastBaseline.roundToInt())) {}
    }

    override fun IntrinsicMeasureScope.maxIntrinsicWidth(measurables: List<IntrinsicMeasurable>, height: Int): Int =
        measure(1f, Constraints(), this, layoutDirection).size.width

    /** El ancho mínimo de un texto es el de su palabra más larga (hasta [MAX_UNBREAKABLE_WORD] letras): con menos, tendría que partirla. */
    override fun IntrinsicMeasureScope.minIntrinsicWidth(measurables: List<IntrinsicMeasurable>, height: Int): Int {
        val natural = measure(1f, Constraints(), this, layoutDirection).size.width
        val longest = text.split(Regex("\\s+")).filter { it.isNotEmpty() }.maxByOrNull { minOf(it.length, MAX_UNBREAKABLE_WORD) } ?: return natural
        val word = if (longest.length > MAX_UNBREAKABLE_WORD) longest.take(MAX_UNBREAKABLE_WORD) else longest
        return measurer.measure(AnnotatedString(word), scaled(1f, this), TextOverflow.Clip, false, 1, constraints = Constraints(), layoutDirection = layoutDirection, density = this).size.width.coerceAtMost(natural)
    }

    override fun IntrinsicMeasureScope.minIntrinsicHeight(measurables: List<IntrinsicMeasurable>, width: Int): Int =
        fit(Constraints(maxWidth = width.coerceAtLeast(0)), this, layoutDirection).size.height

    override fun IntrinsicMeasureScope.maxIntrinsicHeight(measurables: List<IntrinsicMeasurable>, width: Int): Int =
        fit(Constraints(maxWidth = width.coerceAtLeast(0)), this, layoutDirection).size.height
}

/** Envuelve la etiqueta o el texto de ayuda de un campo para que se comporte como [LocalFieldHint]. */
internal fun hint(content: (@Composable () -> Unit)?): (@Composable () -> Unit)? =
    content?.let { c -> { androidx.compose.runtime.CompositionLocalProvider(LocalFieldHint provides true) { c() } } }

package com.cuadra.caja.ui.common

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp

/**
 * Texto monoespaciado de `columns` caracteres de ancho (el recibo tal como sale en el papel). Como las columnas no se pueden partir sin descuadrar el recibo, en vez de
 * bajar de línea la letra se ajusta UNA vez para que las `columns` quepan en el ancho disponible (mínimo 25 %): todas las líneas comparten el mismo tamaño y nunca se
 * recorta ni se desplaza de lado. Parte de `bodyMedium` de la escala.
 */
@Composable
fun MonoBlock(text: String, columns: Int, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer(cacheSize = 2)
    val density = LocalDensity.current
    val dir = LocalLayoutDirection.current
    val base = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
    BoxWithConstraints(modifier) {
        val available = constraints.maxWidth
        val ruler = AnnotatedString("0".repeat(columns))
        val size = if (base.fontSize.isSpecified) base.fontSize else 13.sp
        val scale = remember(available, columns, base, density) {
            var s = 1f
            while (s > 0.25f && measurer.measure(ruler, base.copy(fontSize = size * s), softWrap = false, maxLines = 1, constraints = Constraints(), layoutDirection = dir, density = density).size.width > available) s -= 0.04f
            s
        }
        val fitted: TextUnit = size * scale
        Text(text, style = base.copy(fontSize = fitted, lineHeight = TextUnit.Unspecified), minScale = 1f)
    }
}

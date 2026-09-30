package com.cuadra.caja.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cuadra.caja.ui.theme.CuadraColors

/** Limita cuánto crece la letra dentro de `content` (las teclas y la barra fija ya son grandes: con 2.0× no caben en un teléfono). El resto de la pantalla sí sigue la escala completa. */
@Composable
fun CappedFontScale(max: Float, content: @Composable () -> Unit) {
    val d = LocalDensity.current
    if (d.fontScale <= max) content() else CompositionLocalProvider(LocalDensity provides Density(d.density, max), content = content)
}

/** Tope de la letra de las teclas (28 sp × 1.2 ≈ 34 dp de letra: cabe en una tecla de 48 dp). */
const val KEY_FONT_CAP = 1.2f

private val KeyGap = 6.dp

/**
 * Calculadora de la caja, de 4 columnas: `7 8 9 ⌫ / 4 5 6 / 1 2 3 / . 0 ×`, y «Agregar» como UNA tecla alta a la derecha que ocupa las filas 2 a 4
 * (la alcanza el pulgar derecho sin mover la mano). Todas las teclas miden `keyHeight` (≥ 48 dp) y la letra tiene tope: la calculadora siempre
 * ocupa el mismo alto, con cualquier letra del teléfono, y cabe completa (incluido el 0) sobre la barra inferior.
 *
 * `onDot` / `onTimes` en `null`: esa tecla queda en blanco. `addTotal`: el total de lo escrito si es una multiplicación («3 × 25» → C$ 75.00).
 */
@Composable
fun PosKeypad(
    onDigit: (Char) -> Unit, onBackspace: () -> Unit, onAdd: () -> Unit, addEnabled: Boolean, addLabel: String, keyHeight: Dp, modifier: Modifier = Modifier,
    addTotal: String? = null, onDot: (() -> Unit)? = null, onTimes: (() -> Unit)? = null,
) {
    val backspace = "⌫"
    CappedFontScale(KEY_FONT_CAP) {
        Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(KeyGap)) {
            Column(Modifier.weight(3f), verticalArrangement = Arrangement.spacedBy(KeyGap)) {
                listOf("789", "456", "123").forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(KeyGap)) { row.forEach { c -> Key(c.toString(), Modifier.weight(1f), keyHeight) { onDigit(c) } } }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(KeyGap)) {
                    if (onDot != null) Key(".", Modifier.weight(1f), keyHeight, description = "dot") { onDot() } else Box(Modifier.weight(1f).height(keyHeight))
                    Key("0", Modifier.weight(1f), keyHeight) { onDigit('0') }
                    if (onTimes != null) Key("×", Modifier.weight(1f), keyHeight, description = "times") { onTimes() } else Box(Modifier.weight(1f).height(keyHeight))
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(KeyGap)) {
                Key(backspace, Modifier, keyHeight, description = "backspace") { onBackspace() }
                AddKey(addLabel, addTotal, addEnabled, keyHeight * 3 + KeyGap * 2, onAdd)
            }
        }
    }
}

@Composable
private fun Key(label: String, modifier: Modifier, height: Dp, description: String? = null, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = modifier.fillMaxWidth().height(height).background(CuadraColors.Surface, shape).testTag(TAG_KEY).clickable(role = Role.Button, onClick = onClick)
            .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier),
        contentAlignment = Alignment.Center,
    ) { Text(label, style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold), maxLines = 1, minScale = 0.5f) }
}

@Composable
private fun AddKey(label: String, total: String?, enabled: Boolean, height: Dp, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    val fg = if (enabled) CuadraColors.Bg else CuadraColors.Muted
    Box(
        Modifier.fillMaxWidth().height(height).background(if (enabled) CuadraColors.Ink else CuadraColors.Soft, shape).testTag(TAG_KEY)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(horizontal = 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("+", color = fg, style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold), maxLines = 1)
            Text(label, color = fg, style = MaterialTheme.typography.labelLarge, maxLines = 1, minScale = 0.5f)
            if (total != null) MoneyText(total, color = fg, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.ExtraBold, modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}

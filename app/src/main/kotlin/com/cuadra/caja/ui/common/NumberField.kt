package com.cuadra.caja.ui.common

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection

/**
 * Campo de monto, cantidad, teléfono o código: lo escrito SIEMPRE se ve completo. Si con la letra actual no cabe en el ancho del campo, la letra
 * del campo baja (hasta 50 %) en vez de dejar el número cortado ("99,999,9…"). Los campos de texto libre usan `VoiceTextField` (que además dicta).
 */
@Composable
fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: (@Composable () -> Unit)? = null,
    placeholder: (@Composable () -> Unit)? = null,
    supportingText: (@Composable () -> Unit)? = null,
    prefix: String? = null,
    textStyle: TextStyle = LocalTextStyle.current,
    keyboardType: KeyboardType = KeyboardType.Decimal,
    keyboardOptions: KeyboardOptions = KeyboardOptions(keyboardType = keyboardType),
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    isError: Boolean = false,
    enabled: Boolean = true,
    trailingIcon: (@Composable () -> Unit)? = null,
    /** Ancho que ocupa lo que va a la derecha dentro del campo (un icono = 48 dp; el código de barras lleva dos). */
    trailingWidth: androidx.compose.ui.unit.Dp = 48.dp,
) {
    val measurer = rememberTextMeasurer(cacheSize = 2)
    val density = LocalDensity.current
    val dir = LocalLayoutDirection.current
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val base = LocalTextStyle.current.merge(textStyle)
        // El campo deja 16 dp a cada lado, más el prefijo; el resto es para lo escrito.
        val shown = (prefix?.let { "$it " } ?: "") + value.ifEmpty { "0" }
        val available = (constraints.maxWidth - with(density) { (if (trailingIcon != null) 44.dp + trailingWidth else 44.dp).roundToPx() }).coerceAtLeast(1)
        val scale = remember(shown, base, available, density) {
            var s = 1f
            val size = if (base.fontSize.isSpecified) base.fontSize else 15.sp
            while (s > 0.5f && measurer.measure(AnnotatedString(shown), base.copy(fontSize = size * s), softWrap = false, maxLines = 1, constraints = Constraints(), layoutDirection = dir, density = density).size.width > available) s -= 0.05f
            s
        }
        val fitted = if (scale >= 0.999f) base else base.copy(fontSize = (if (base.fontSize.isSpecified) base.fontSize else 15.sp) * scale)
        // sin voz: monto, cantidad, teléfono o código (teclado numérico)
        OutlinedTextField(
            value, onValueChange, Modifier.fillMaxWidth(), enabled = enabled, singleLine = true, textStyle = fitted, label = hint(label), placeholder = hint(placeholder),
            supportingText = supportingText, prefix = prefix?.let { p -> { Text(p, style = fitted, maxLines = 1, minScale = 1f) } },
            keyboardOptions = keyboardOptions, keyboardActions = keyboardActions, isError = isError, trailingIcon = trailingIcon,
        )
    }
}

package com.cuadra.caja.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.cuadra.caja.R
import com.cuadra.caja.ui.theme.CuadraColors

/**
 * Campo del código de barras: teclado NUMÉRICO por omisión (lo más común, EAN/UPC) y, debajo, un control «Teclado: Números | Letras» con una línea de ayuda
 * («Letras solo para códigos que las traen»: Code 128 y Code 39). Escanear no cambia. El código tecleado es solo texto (no hay «tipo de código»);
 * si el que ya hay trae letras, arranca con el teclado de letras.
 */
@Composable
fun BarcodeField(
    value: String, onValueChange: (String) -> Unit, onScan: () -> Unit, modifier: Modifier = Modifier, isError: Boolean = false, supportingText: (@Composable () -> Unit)? = null,
) {
    var letters by remember { mutableStateOf(value.any { !it.isDigit() }) }
    val focus = LocalFocusManager.current
    // Un lector que teclea termina con Enter: aquí solo cierra el campo. Nunca envía el formulario completo por accidente.
    val enterEndsField = Modifier.onPreviewKeyEvent { e ->
        val enter = e.key == Key.Enter || e.key == Key.NumPadEnter
        if (enter && e.type == KeyEventType.KeyDown) focus.clearFocus()
        enter
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // sin voz: código de barras (números o letras de un código impreso; se escanea o se teclea, nunca se dicta)
        NumberField(
            value, onValueChange, Modifier.fillMaxWidth().then(enterEndsField), label = { Text(stringResource(R.string.product_barcode), maxLines = 1) },
            keyboardOptions = KeyboardOptions(keyboardType = if (letters) KeyboardType.Ascii else KeyboardType.Number, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
            isError = isError, supportingText = supportingText, trailingWidth = 52.dp,
            trailingIcon = { ScanFieldIcon(onScan) },
        )
        SegmentedChoice(
            stringResource(R.string.barcode_kb_label), listOf(stringResource(R.string.barcode_kb_numbers), stringResource(R.string.barcode_kb_letters)),
            if (letters) 1 else 0, { letters = it == 1 },
        )
        Text(stringResource(R.string.barcode_kb_help), style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted)
    }
}

/**
 * Control segmentado de pocas opciones (2 o 3): una etiqueta (opcional) y las opciones pegadas en una fila con borde común; la elegida va rellena. Cada opción mide al
 * menos 48 dp y reparte el ancho; el texto baja de tamaño y sigue en más líneas antes de recortarse.
 */
@Composable
fun SegmentedChoice(label: String?, options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(50)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (label != null) Text(label, style = MaterialTheme.typography.labelLarge, color = CuadraColors.Ink2)
        Row(Modifier.fillMaxWidth().clip(shape).border(BorderStroke(1.dp, CuadraColors.Line), shape).selectableGroup()) {
            options.forEachIndexed { i, text ->
                val on = i == selected
                Box(
                    Modifier.weight(1f).heightIn(min = 48.dp).background(if (on) CuadraColors.Ink else CuadraColors.Surface)
                        .selectable(on, role = Role.RadioButton, onClick = { onSelect(i) }).padding(horizontal = 10.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text, color = if (on) CuadraColors.Bg else CuadraColors.Ink, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center, maxLines = 3, minScale = 0.7f)
                }
            }
        }
    }
}

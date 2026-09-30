package com.cuadra.caja.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.cuadra.caja.domain.AccessCode
import com.cuadra.caja.domain.PinRules
import com.cuadra.caja.domain.TimeText
import com.cuadra.caja.ui.theme.CuadraColors

/**
 * Campo de PIN: solo números (exactamente 5), teclado numérico, nunca se dicta por voz. `hidden` esconde los dígitos (el PIN propio); al escribir el de otra
 * persona se ven, para poder dárselo. El texto de ayuda se envuelve; la etiqueta va en una línea.
 */
@Composable
fun PinField(value: String, onValueChange: (String) -> Unit, label: String, modifier: Modifier = Modifier, hidden: Boolean = false, isError: Boolean = false, supporting: String? = null, enabled: Boolean = true) {
    // sin voz: PIN (solo números; un PIN nunca se dicta)
    OutlinedTextField(
        value, { onValueChange(PinRules.sanitize(it)) }, modifier.fillMaxWidth(), enabled = enabled, singleLine = true, label = hint { Text(label) },
        supportingText = supporting?.let { s -> { Text(s) } }, isError = isError,
        visualTransformation = if (hidden) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
    )
}

/** Campo de correo (sin voz: se escribe con el teclado de correo). */
@Composable
fun EmailField(value: String, onValueChange: (String) -> Unit, label: String, modifier: Modifier = Modifier, isError: Boolean = false, supporting: String? = null) {
    // sin voz: correo
    OutlinedTextField(
        value, onValueChange, modifier.fillMaxWidth(), singleLine = true, label = hint { Text(label) }, supportingText = supporting?.let { s -> { Text(s) } }, isError = isError,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
    )
}

/** Campo de un código que se lee de otra pantalla (letras y números, en mayúsculas). No se dicta. */
@Composable
fun CodeField(value: String, onValueChange: (String) -> Unit, label: String, modifier: Modifier = Modifier, isError: Boolean = false, supporting: String? = null) {
    // sin voz: código de vinculación
    OutlinedTextField(
        value, onValueChange, modifier.fillMaxWidth(), singleLine = true, label = hint { Text(label) }, supportingText = supporting?.let { s -> { Text(s) } }, isError = isError,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Characters),
    )
}

/** Campo del código del negocio: solo 5 números, teclado numérico, nunca se dicta. */
@Composable
fun DigitsCodeField(value: String, onValueChange: (String) -> Unit, label: String, modifier: Modifier = Modifier, enabled: Boolean = true, isError: Boolean = false, supporting: String? = null) {
    // sin voz: código del negocio (solo números)
    OutlinedTextField(
        value, { onValueChange(AccessCode.sanitize(it)) }, modifier.fillMaxWidth(), enabled = enabled, singleLine = true, label = hint { Text(label) },
        supportingText = supporting?.let { s -> { Text(s) } }, isError = isError, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
    )
}

/** Interruptor con su texto (y una ayuda debajo): toda la fila se toca, mínimo 48 dp de alto. */
@Composable
fun SwitchRow(label: String, on: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier, help: String? = null, enabled: Boolean = true) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(label, Modifier.weight(1f), color = if (enabled) Color.Unspecified else CuadraColors.Muted)
            Switch(on, onChange, Modifier.semantics { contentDescription = label }, enabled = enabled)
        }
        if (help != null) Text(help, style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted)
    }
}

/** Casilla con su texto (varias líneas): toda la fila se toca, mínimo 48 dp de alto. Para confirmaciones como «Entiendo que…». */
@Composable
fun CheckRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(checked, enabled = enabled, role = Role.Checkbox, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Checkbox(checked, null, enabled = enabled)
        Text(label, Modifier.weight(1f), color = if (enabled) Color.Unspecified else CuadraColors.Muted)
    }
}

/**
 * Campo de una hora «HH:mm» (corte de la jornada, horas de silencio…): teclado numérico y los dos puntos se ponen solos («0200» → «02:00»).
 * Solo números: no se dicta.
 */
@Composable
fun TimeField(value: String, onValueChange: (String) -> Unit, label: String, modifier: Modifier = Modifier, isError: Boolean = false, supporting: String? = null, enabled: Boolean = true) {
    // sin voz: hora (solo números)
    OutlinedTextField(
        value, { onValueChange(TimeText.sanitize(it)) }, modifier.fillMaxWidth(), enabled = enabled, singleLine = true, label = hint { Text(label) }, placeholder = hint { Text("HH:mm") },
        supportingText = supporting?.let { s -> { Text(s) } }, isError = isError, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
}

/** Un color para elegir (avatar de una persona): el área tocable es de 48 dp; el elegido lleva un aro oscuro. */
@Composable
fun ColorSwatch(color: Color, selected: Boolean, description: String, onClick: () -> Unit) {
    Box(
        Modifier.size(48.dp).clickable(role = Role.RadioButton, onClick = onClick).semantics { contentDescription = description; this.selected = selected },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(34.dp).background(color, CircleShape).then(if (selected) Modifier.border(3.dp, CuadraColors.Ink, CircleShape) else Modifier.border(1.dp, CuadraColors.Line, CircleShape)))
    }
}

/** `#rrggbb` → color; nulo si no es válido. */
fun memberColor(hex: String?): Color? = hex?.let { runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull() }

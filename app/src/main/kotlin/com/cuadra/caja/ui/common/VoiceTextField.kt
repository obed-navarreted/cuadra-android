package com.cuadra.caja.ui.common

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.cuadra.caja.R
import com.cuadra.caja.domain.VoiceAdvice
import com.cuadra.caja.domain.VoiceAdvices
import com.cuadra.caja.domain.VoiceError
import com.cuadra.caja.domain.VoiceState
import com.cuadra.caja.domain.VoiceText
import com.cuadra.caja.ui.theme.CuadraColors

/**
 * Campo de texto libre con **dictado por voz**. Regla de producto: todo campo donde se escribe texto (nombres, descripciones, motivos, notas,
 * búsquedas) debe poder llenarse hablando; los montos, teléfonos, PIN y códigos se quedan con teclado. `VoiceInputGuardTest` lo vigila.
 *
 * El dictado es de la app (`SpeechRecognizer`, ver `VoiceDictation.kt`): al tocar el micrófono este late y lo entendido se ve EN VIVO en el campo;
 * se toca otra vez para terminar. Habla en el idioma de la app y, si el teléfono lo permite, sin conexión. Lo dictado se agrega al final de lo escrito.
 *
 * `compact`: una sola línea baja (48 dp, lo mínimo tocable por el micrófono), sin etiqueta flotante (la etiqueta pasa a ser el texto de ayuda)
 * y con la letra de `titleSmall`; para campos que van pegados a un teclado numérico (la descripción de la caja).
 *
 * `voiceState` solo lo usa la guardia de diseño para dibujar el campo «escuchando» sin micrófono real.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    singleLine: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    label: (@Composable () -> Unit)? = null,
    placeholder: (@Composable () -> Unit)? = null,
    supportingText: (@Composable () -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    isError: Boolean = false,
    enabled: Boolean = true,
    compact: Boolean = false,
    voiceState: VoiceState? = null,
    /** Campo de BÚSQUEDA (productos): un lector físico con el foco aquí no teclea en el campo, hace una lectura (ver `ScanHub.scanFieldFocused`). */
    scanFriendly: Boolean = false,
    /** Campo de búsqueda: con texto muestra una ✕ «Borrar búsqueda» (48 dp) que lo borra todo de un toque sin quitar el foco (el teclado sigue abierto). */
    clearable: Boolean = false,
) {
    var base by remember { mutableStateOf("") }
    val scanModifier = if (scanFriendly) modifier.scanSearchField(value, onValueChange) else modifier
    val current = androidx.compose.runtime.rememberUpdatedState(value)
    val change = androidx.compose.runtime.rememberUpdatedState(onValueChange)
    val dictation = rememberVoiceDictation(
        onStart = { base = current.value },
        onLive = { spoken -> change.value(VoiceText.merge(base, spoken)) },
        onCommit = { spoken -> change.value(VoiceText.merge(base, spoken)) },
        onCancel = { change.value(base) },
    )
    val state = voiceState ?: dictation.state
    val listening = state is VoiceState.Listening || state is VoiceState.Finishing
    // Con ✕ y micrófono a la vez el campo necesita ancho: si el campo mide menos de `BOTH_ICONS_MIN`, con texto la ✕ ocupa el lugar del micrófono (sin texto vuelve el micrófono;
    // escuchando, el micrófono siempre se ve porque es lo que detiene el dictado).
    var fieldWidth by remember { mutableStateOf(Int.MAX_VALUE) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val showClear = clearable && value.isNotEmpty() && enabled
    val showBoth = with(density) { fieldWidth.toDp() } >= BOTH_ICONS_MIN
    val showMic = !showClear || showBoth || listening
    val mic: @Composable () -> Unit = {
        androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            if (showClear) ClearButton { change.value("") }
            if (showMic) MicButton(listening, enabled) { dictation.onMicTap() }
        }
    }
    val sized = scanModifier.onSizeChanged { fieldWidth = it.width }

    if (compact) {
        val source = remember { MutableInteractionSource() }
        val hintText = placeholder ?: label
        BasicTextField(
            value, onValueChange, sized.fillMaxWidth().heightIn(min = 48.dp), enabled = enabled, singleLine = true,
            textStyle = MaterialTheme.typography.titleSmall.copy(color = CuadraColors.Ink), cursorBrush = SolidColor(CuadraColors.Ink),
            keyboardOptions = keyboardOptions, keyboardActions = keyboardActions, interactionSource = source,
            decorationBox = { inner ->
                OutlinedTextFieldDefaults.DecorationBox(
                    value = value, innerTextField = inner, enabled = enabled, singleLine = true, visualTransformation = VisualTransformation.None, interactionSource = source,
                    isError = isError,
                    placeholder = if (listening) ({ Text(stringResource(R.string.voice_listening), style = MaterialTheme.typography.titleSmall, maxLines = 1) })
                    else hintText?.let { h -> { androidx.compose.runtime.CompositionLocalProvider(LocalFieldHint provides true) { h() } } },
                    trailingIcon = mic,
                    contentPadding = OutlinedTextFieldDefaults.contentPadding(start = 14.dp, top = 4.dp, end = 0.dp, bottom = 4.dp),
                    container = { OutlinedTextFieldDefaults.Container(enabled = enabled, isError = isError, interactionSource = source, shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp)) },
                )
            },
        )
    } else {
        OutlinedTextField(
            value, onValueChange, sized, enabled = enabled, singleLine = singleLine, minLines = minLines, maxLines = maxLines,
            label = hint(label), placeholder = hint(if (listening && value.isEmpty()) ({ Text(stringResource(R.string.voice_listening)) }) else placeholder), supportingText = supportingText,
            keyboardOptions = keyboardOptions, keyboardActions = keyboardActions, isError = isError, trailingIcon = mic,
        )
    }
    VoiceDialogs(dictation, state)
}

/** Ancho del campo desde el que caben la ✕ y el micrófono juntos sin dejar el texto sin lugar. */
private val BOTH_ICONS_MIN = 220.dp

/** ✕ «Borrar búsqueda» (48 x 48 dp tocables). No pide el foco: el campo lo conserva y el teclado sigue abierto. */
@Composable
private fun ClearButton(onClick: () -> Unit) {
    val description = stringResource(R.string.search_clear)
    Box(Modifier.size(48.dp).clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onClick).semantics { contentDescription = description }.testTag(TAG_SEARCH_CLEAR), contentAlignment = androidx.compose.ui.Alignment.Center) {
        Icon(painterResource(R.drawable.ic_clear), contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** El micrófono (48 x 48 dp tocables): quieto en reposo; rojo y latiendo mientras escucha (tocar de nuevo termina). */
@Composable
private fun MicButton(listening: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val pulse = if (listening) {
        val t = rememberInfiniteTransition(label = "mic")
        t.animateFloat(1f, 1.3f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "micScale").value
    } else 1f
    val description = stringResource(if (listening) R.string.voice_stop else R.string.voice_dictate)
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(48.dp).semantics { contentDescription = description }) {
        Icon(
            painterResource(R.drawable.ic_mic), contentDescription = null, modifier = Modifier.scale(pulse),
            tint = if (listening) CuadraColors.Red else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Los avisos del dictado: motivo del permiso, permiso negado, «este teléfono no tiene dictado» y errores del reconocedor. */
@Composable
internal fun VoiceDialogs(dictation: VoiceDictation, state: VoiceState) {
    if (dictation.showRationale) {
        Sheet(dictation::dismiss, actions = {
            ButtonRow {
                CuadraButton(stringResource(R.string.voice_not_now), dictation::dismiss, Modifier.share(1f))
                CuadraButton(stringResource(R.string.voice_allow), dictation::confirmRationale, Modifier.share(1f), kind = ButtonKind.DARK)
            }
        }) {
            Text(stringResource(R.string.voice_rationale_title), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(R.string.voice_rationale))
        }
        return
    }
    val failed = state as? VoiceState.Failed ?: return
    when (failed.error) {
        VoiceError.NO_SERVICE -> Sheet(dictation::dismiss, actions = { CuadraButton(stringResource(R.string.voice_close), dictation::dismiss, Modifier.fillMaxWidth(), kind = ButtonKind.DARK) }) {
            Text(stringResource(R.string.voice_no_service_title), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(R.string.voice_no_service))
        }
        VoiceError.PERMISSION -> Sheet(dictation::dismiss, actions = {
            ButtonRow {
                CuadraButton(stringResource(R.string.voice_close), dictation::dismiss, Modifier.share(1f))
                if (failed.permanentlyDenied) CuadraButton(stringResource(R.string.voice_open_settings), { dictation.dismiss(); dictation.openSettings() }, Modifier.share(1f), kind = ButtonKind.DARK)
                else CuadraButton(stringResource(R.string.voice_allow), dictation::retry, Modifier.share(1f), kind = ButtonKind.DARK)
            }
        }) {
            Text(stringResource(R.string.voice_denied_title), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(if (failed.permanentlyDenied) R.string.voice_denied_settings else R.string.voice_denied_retry))
        }
        else -> Sheet(dictation::dismiss, actions = {
            ButtonRow {
                CuadraButton(stringResource(R.string.voice_close), dictation::dismiss, Modifier.share(1f))
                CuadraButton(stringResource(R.string.voice_retry), dictation::retry, Modifier.share(1f), kind = ButtonKind.DARK)
            }
        }) {
            Text(stringResource(R.string.voice_error_title), style = MaterialTheme.typography.headlineMedium)
            val advice = VoiceAdvices.of(failed.error)
            Text(adviceText(advice))
            // Qué idiomas se probaron: si nada funcionó, la persona (o quien la ayude) sabe qué descargar.
            if (dictation.triedLanguages.isNotEmpty() && (advice == VoiceAdvice.LANGUAGE || advice == VoiceAdvice.GENERIC)) {
                Text(stringResource(R.string.voice_tried, dictation.triedLanguages.joinToString(", ")), style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted)
            }
        }
    }
}

/**
 * Marca este campo como buscador que acepta lecturas: mientras tiene el foco, `ScanHub.scanFieldFocused` es verdadero y una lectura que ya escribió su primer
 * carácter aquí se limpia sola (`ScanLeak.strip`). Sin la aplicación (guardia de diseño) no hace nada.
 */
@Composable
internal fun Modifier.scanSearchField(value: String, onValueChange: (String) -> Unit): Modifier {
    val context = androidx.compose.ui.platform.LocalContext.current
    val hub = remember { (context.applicationContext as? com.cuadra.caja.CuadraApp)?.container?.scanner }
    val current = androidx.compose.runtime.rememberUpdatedState(value)
    val change = androidx.compose.runtime.rememberUpdatedState(onValueChange)
    val fixer: (String) -> Unit = remember { { code: String -> change.value(com.cuadra.caja.domain.ScanLeak.strip(current.value, code)) } }
    androidx.compose.runtime.DisposableEffect(hub) { onDispose { if (hub?.leakFixer === fixer) { hub.leakFixer = null; hub.scanFieldFocused = false } } }
    return this.onFocusChanged { f ->
        if (hub != null) {
            hub.scanFieldFocused = f.isFocused
            hub.leakFixer = if (f.isFocused) fixer else if (hub.leakFixer === fixer) null else hub.leakFixer
        }
    }
}

/** Etiqueta de prueba de la ✕ «Borrar búsqueda». */
const val TAG_SEARCH_CLEAR = "SearchClear"

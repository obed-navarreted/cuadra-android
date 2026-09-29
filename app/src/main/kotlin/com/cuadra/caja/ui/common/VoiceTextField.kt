package com.cuadra.caja.ui.common

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.cuadra.caja.R
import com.cuadra.caja.domain.VoiceText

/**
 * Campo de texto libre con **dictado por voz**. Regla de producto: todo campo donde se escribe texto (nombres, descripciones, motivos, notas,
 * búsquedas) debe poder llenarse hablando; los montos, teléfonos, PIN y códigos se quedan con teclado. `VoiceInputGuardTest` lo vigila.
 *
 * Usa el reconocedor de voz del sistema (no pide permiso de micrófono a la app): habla en el idioma de la app y, si el teléfono lo permite,
 * sin conexión. Lo dictado se agrega al final de lo que ya hay escrito.
 */
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
) {
    val context = LocalContext.current
    val language = LocalConfiguration.current.locales[0].toLanguageTag()
    val prompt = stringResource(R.string.voice_prompt)
    val unavailable = stringResource(R.string.voice_unavailable)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull().orEmpty()
            if (spoken.isNotBlank()) onValueChange(VoiceText.merge(value, spoken))
        }
    }
    OutlinedTextField(
        value, onValueChange, modifier, enabled = enabled, singleLine = singleLine, minLines = minLines, maxLines = maxLines, label = label, placeholder = placeholder, supportingText = supportingText,
        keyboardOptions = keyboardOptions, keyboardActions = keyboardActions, isError = isError,
        trailingIcon = {
            IconButton(
                onClick = {
                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        .putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
                        .putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)
                        .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                        .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                    // Sin servicio de voz en el teléfono se avisa qué hacer: nunca falla en silencio.
                    try {
                        launcher.launch(intent)
                    } catch (e: ActivityNotFoundException) {
                        Toast.makeText(context, unavailable, Toast.LENGTH_LONG).show()
                    }
                },
                enabled = enabled,
                modifier = Modifier.size(48.dp),
            ) { Icon(painterResource(R.drawable.ic_mic), contentDescription = stringResource(R.string.voice_dictate)) }
        },
    )
}

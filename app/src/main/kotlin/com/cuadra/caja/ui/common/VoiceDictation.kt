package com.cuadra.caja.ui.common

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import com.cuadra.caja.R
import com.cuadra.caja.domain.VoiceAdvice
import com.cuadra.caja.domain.VoiceAdvices
import com.cuadra.caja.domain.VoiceEffect
import com.cuadra.caja.domain.VoiceEngine
import com.cuadra.caja.domain.VoiceEnv
import com.cuadra.caja.domain.VoiceError
import com.cuadra.caja.domain.VoiceLanguages
import com.cuadra.caja.domain.VoiceAttempt
import com.cuadra.caja.domain.VoicePlan
import com.cuadra.caja.domain.VoiceEvent
import com.cuadra.caja.domain.VoiceMachine
import com.cuadra.caja.domain.VoiceState

/**
 * Dictado DENTRO de la app con `SpeechRecognizer` (sin la ventana de Google): mientras se habla, lo entendido se ve en vivo en el campo.
 * La lógica de estados vive en `domain/VoiceDictation.kt` (probada sin Android); esto solo conecta con el reconocedor, el permiso y los avisos.
 *
 * Camino de respaldo: si el teléfono no tiene reconocedor pero sí sabe resolver `RecognizerIntent`, se abre ese diálogo del sistema.
 * Si no hay nada, un diálogo explica qué hacer (nunca falla en silencio).
 */
@Stable
class VoiceDictation internal constructor(private val host: Host) {
    var state: VoiceState by mutableStateOf(VoiceState.Idle)
        internal set

    /** Etiquetas de idioma que se probaron en el último intento fallido (para decírselo a la persona). */
    var triedLanguages: List<String> by mutableStateOf(emptyList())
        internal set

    /** Pedir el permiso del sistema (tras mostrar el motivo). */
    var showRationale by mutableStateOf(false)
        internal set

    fun onMicTap() = host.tap(this)
    fun dismiss() = host.dismiss(this)
    fun confirmRationale() = host.confirmRationale(this)
    fun retry() = host.retry(this)
    fun openSettings() = host.openSettings()

    internal interface Host {
        fun tap(d: VoiceDictation)
        fun dismiss(d: VoiceDictation)
        fun confirmRationale(d: VoiceDictation)
        fun retry(d: VoiceDictation)
        fun openSettings()
    }
}

/** El texto del aviso (consejo) de cada error. */
@Composable
fun adviceText(advice: VoiceAdvice): String = stringResource(
    when (advice) {
        VoiceAdvice.OFFLINE_LANGUAGE -> R.string.voice_err_offline
        VoiceAdvice.TRY_AGAIN -> R.string.voice_err_try_again
        VoiceAdvice.BUSY -> R.string.voice_err_busy
        VoiceAdvice.SERVER -> R.string.voice_err_server
        VoiceAdvice.PERMISSION -> R.string.voice_denied_retry
        VoiceAdvice.LANGUAGE -> R.string.voice_err_language
        VoiceAdvice.MICROPHONE -> R.string.voice_err_mic
        VoiceAdvice.GENERIC -> R.string.voice_err_generic
        VoiceAdvice.NO_SERVICE -> R.string.voice_no_service
    },
)

private fun Context.activity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) { if (c is Activity) return c; c = c.baseContext }
    return null
}

/**
 * Crea el dictado de un campo. `onStart` se llama al empezar a escuchar (para recordar el texto que ya había), `onLive` con cada resultado parcial,
 * `onCommit` con el resultado final y `onCancel` si se termina sin resultado (para devolver el campo a como estaba).
 */
@Composable
fun rememberVoiceDictation(onStart: () -> Unit, onLive: (String) -> Unit, onCommit: (String) -> Unit, onCancel: () -> Unit): VoiceDictation {
    val context = LocalContext.current
    val language = LocalConfiguration.current.locales[0].toLanguageTag()
    val start = rememberUpdatedState(onStart)
    val live = rememberUpdatedState(onLive)
    val commit = rememberUpdatedState(onCommit)
    val cancel = rememberUpdatedState(onCancel)
    val languageNow = rememberUpdatedState(language)
    val prompt = stringResource(R.string.voice_prompt)
    val unavailable = stringResource(R.string.voice_unavailable)
    val promptNow = rememberUpdatedState(prompt)
    val unavailableNow = rememberUpdatedState(unavailable)

    // Lo que ya se pudo pedir al sistema; se rellena más abajo (el lanzador necesita el controlador y el controlador el lanzador).
    var controllerRef: Controller? = null
    val systemDialog = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull().orEmpty()
            if (spoken.isNotBlank()) { start.value(); commit.value(spoken) }
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val canAskAgain = granted || context.activity()?.let { androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.RECORD_AUDIO) } ?: true
        controllerRef?.event(VoiceEvent.PermissionResult(granted, canAskAgain))
    }
    val controller = remember(context) {
        Controller(
            context, { languageNow.value }, { start.value() }, { live.value(it) }, { commit.value(it) }, { cancel.value() },
            { permission.launch(Manifest.permission.RECORD_AUDIO) },
            { systemDialog.launchOrNull(context, languageNow.value, promptNow.value, unavailableNow.value) },
        ).also { controllerRef = it }
    }
    controllerRef = controller
    DisposableEffect(controller) { onDispose { controller.release() } }
    return controller.dictation
}

private fun androidx.activity.result.ActivityResultLauncher<Intent>.launchOrNull(context: Context, language: String, prompt: String, unavailable: String) {
    val intent = systemIntent(language, prompt)
    try { launch(intent) } catch (_: ActivityNotFoundException) { Toast.makeText(context, unavailable, Toast.LENGTH_LONG).show() }
}

private fun systemIntent(language: String, prompt: String) = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
    .putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
    .putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)
    .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)
    .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)

private class Controller(
    private val context: Context,
    private val language: () -> String,
    private val onStart: () -> Unit,
    private val onLive: (String) -> Unit,
    private val onCommit: (String) -> Unit,
    private val onCancel: () -> Unit,
    private val requestPermission: () -> Unit,
    private val launchSystemDialog: () -> Unit,
) : VoiceDictation.Host {
    val dictation = VoiceDictation(this)
    private var recognizer: SpeechRecognizer? = null
    /** Intento en curso y los ya probados (cadena de idiomas, ver `VoicePlan`). */
    private var plan: VoicePlan? = null

    private fun micGranted() = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    private fun standardAvailable() = SpeechRecognizer.isRecognitionAvailable(context)
    private fun onDeviceAvailable() = Build.VERSION.SDK_INT >= 33 && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
    private fun hasNetwork(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager ?: return true
        val caps = runCatching { cm.getNetworkCapabilities(cm.activeNetwork) }.getOrNull() ?: return false
        return caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
    private fun env() = VoiceEnv(hasNetwork(), standardAvailable(), onDeviceAvailable())
    private fun systemDialogResolvable() = systemIntent(language(), "").resolveActivity(context.packageManager) != null

    override fun tap(d: VoiceDictation) {
        val wasIdle = !d.state.active
        if (wasIdle) { plan = null; d.triedLanguages = emptyList() }
        event(VoiceEvent.Tap(serviceAvailable = standardAvailable() || onDeviceAvailable(), micGranted = micGranted(), fallbackAvailable = systemDialogResolvable()))
    }

    override fun dismiss(d: VoiceDictation) = event(VoiceEvent.Dismiss)

    override fun confirmRationale(d: VoiceDictation) {
        d.showRationale = false
        requestPermission()
    }

    override fun retry(d: VoiceDictation) {
        d.state = VoiceState.Idle
        tap(d)
    }

    override fun openSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    fun event(e: VoiceEvent) {
        val step = VoiceMachine.reduce(dictation.state, e)
        dictation.state = step.state
        when (val fx = step.effect) {
            VoiceEffect.RequestPermission -> dictation.showRationale = true
            VoiceEffect.StartListening -> { onStart(); startRecognizer() }
            VoiceEffect.StopListening -> recognizer?.stopListening()
            VoiceEffect.Cancel -> { destroy(); dictation.showRationale = false; onCancel() }
            is VoiceEffect.Commit -> { destroy(); onCommit(fx.spoken) }
            VoiceEffect.LaunchSystemDialog -> launchSystemDialog()
            null -> Unit
        }
        // Un error «pequeño» se avisa con un mensaje breve y vuelve a reposo; los demás abren un diálogo explicativo (lo dibuja `VoiceDialogs`).
        (dictation.state as? VoiceState.Failed)?.let { failed ->
            if (VoiceAdvices.isQuiet(failed.error)) {
                Toast.makeText(context, quietText(failed.error), Toast.LENGTH_SHORT).show()
                dictation.state = VoiceState.Idle
            }
        }
    }

    private fun quietText(error: VoiceError) = context.getString(if (error == VoiceError.BUSY) R.string.voice_err_busy else R.string.voice_err_try_again)

    private fun startRecognizer() {
        // Primer intento de este dictado: el reconocedor normal en el idioma del teléfono (el del teléfono, sin internet, solo si no hay red).
        val current = plan ?: VoicePlan.start(VoiceLanguages.chain(language()), env()).also { plan = it }
        launch(current.current)
    }

    private fun launch(attempt: VoiceAttempt) {
        destroy()
        dictation.triedLanguages = plan?.triedTags.orEmpty()
        val r = try {
            if (attempt.engine == VoiceEngine.ON_DEVICE && Build.VERSION.SDK_INT >= 33) SpeechRecognizer.createOnDeviceSpeechRecognizer(context) else SpeechRecognizer.createSpeechRecognizer(context)
        } catch (_: Exception) { null }
        if (r == null) { fail(5); return }
        recognizer = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
            override fun onPartialResults(partialResults: Bundle?) {
                val text = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                if (text.isNotBlank()) { event(VoiceEvent.Partial(text)); onLive(text) }
            }
            override fun onResults(results: Bundle?) {
                event(VoiceEvent.Final(results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()))
            }
            override fun onError(error: Int) = fail(error)
        })
        // NUNCA se pide «sin conexión» por omisión: el modelo del teléfono puede no estar descargado y da LANGUAGE_UNAVAILABLE.
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, attempt.tag)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, attempt.tag)
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            .putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        try { r.startListening(intent) } catch (_: Exception) { fail(5) }
    }

    /** Un error del reconocedor: si la cadena tiene otra cosa que probar (otra variante del idioma, o el reconocedor del teléfono sin red) se reintenta sola. */
    private fun fail(code: Int) {
        val p = plan
        val next = if (p != null && dictation.state.active) p.after(VoiceError.fromAndroid(code), env()) else null
        if (next != null) { plan = next; launch(next.current); return }
        dictation.triedLanguages = p?.triedTags.orEmpty()
        event(VoiceEvent.Error(code))
    }

    private fun destroy() {
        recognizer?.let { r -> runCatching { r.cancel() }; runCatching { r.destroy() } }
        recognizer = null
    }

    fun release() {
        val active = dictation.state.active
        destroy()
        if (active) { dictation.state = VoiceState.Idle; dictation.showRationale = false }
    }
}

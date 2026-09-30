package com.cuadra.caja.domain

/**
 * Lógica pura del dictado por voz dentro de la app (sin Android): qué error es, qué se le dice a la persona y cómo avanza el estado
 * (tocar el micrófono → permiso → escuchando → resultado parcial → final / error). El reconocedor real (`SpeechRecognizer`) solo traduce sus
 * eventos a `VoiceEvent` y ejecuta los `VoiceEffect` que este archivo decide.
 */

/** Los errores de `SpeechRecognizer` (constantes `ERROR_*`) en palabras nuestras. */
enum class VoiceError {
    NETWORK, AUDIO, SERVER, CLIENT, NO_MATCH, BUSY, PERMISSION, TOO_MANY_REQUESTS, LANGUAGE_NOT_SUPPORTED, LANGUAGE_UNAVAILABLE, NO_SERVICE, UNKNOWN;

    companion object {
        /** Códigos de `android.speech.SpeechRecognizer.ERROR_*` (se copian aquí para poder probar sin Android). */
        fun fromAndroid(code: Int): VoiceError = when (code) {
            1, 2, 11 -> NETWORK                      // NETWORK_TIMEOUT, NETWORK, SERVER_DISCONNECTED
            3 -> AUDIO
            4 -> SERVER
            5 -> CLIENT
            6, 7 -> NO_MATCH                          // SPEECH_TIMEOUT, NO_MATCH
            8 -> BUSY
            9 -> PERMISSION
            10 -> TOO_MANY_REQUESTS
            12 -> LANGUAGE_NOT_SUPPORTED
            13, 14, 15 -> LANGUAGE_UNAVAILABLE       // LANGUAGE_UNAVAILABLE, CANNOT_CHECK_SUPPORT, CANNOT_LISTEN_TO_DOWNLOAD_EVENTS
            else -> UNKNOWN
        }
    }
}

/** Qué se le dice a la persona: cada consejo tiene su texto (es/en) y siempre incluye el siguiente paso. */
enum class VoiceAdvice {
    /** «Sin internet: descarga el idioma sin conexión en Ajustes → Google → Voz». */
    OFFLINE_LANGUAGE,
    /** «No te escuché, intenta de nuevo». */
    TRY_AGAIN,
    /** «El dictado está ocupado, espera un momento e intenta otra vez». */
    BUSY,
    /** «El servicio de voz falló; intenta de nuevo o usa el micrófono del teclado». */
    SERVER,
    /** «Permite el micrófono para dictar». */
    PERMISSION,
    /** «Tu teléfono no dicta en este idioma: usa el micrófono del teclado (Gboard)». */
    LANGUAGE,
    /** «No se pudo usar el micrófono: revisa que otra app no lo esté usando». */
    MICROPHONE,
    /** «Algo falló con el dictado; intenta de nuevo o usa el micrófono del teclado (Gboard)». */
    GENERIC,
    /** «Este teléfono no tiene dictado por voz…» (diálogo explicativo). */
    NO_SERVICE,
}

object VoiceAdvices {
    fun of(error: VoiceError): VoiceAdvice = when (error) {
        VoiceError.NETWORK -> VoiceAdvice.OFFLINE_LANGUAGE
        VoiceError.NO_MATCH -> VoiceAdvice.TRY_AGAIN
        VoiceError.BUSY, VoiceError.TOO_MANY_REQUESTS -> VoiceAdvice.BUSY
        VoiceError.SERVER -> VoiceAdvice.SERVER
        VoiceError.PERMISSION -> VoiceAdvice.PERMISSION
        VoiceError.LANGUAGE_NOT_SUPPORTED, VoiceError.LANGUAGE_UNAVAILABLE -> VoiceAdvice.LANGUAGE
        VoiceError.AUDIO -> VoiceAdvice.MICROPHONE
        VoiceError.NO_SERVICE -> VoiceAdvice.NO_SERVICE
        VoiceError.CLIENT, VoiceError.UNKNOWN -> VoiceAdvice.GENERIC
    }

    /** Un error «pequeño» (no te escuché) se avisa con un mensaje breve; los demás, con un aviso que se queda hasta cerrarlo. */
    fun isQuiet(error: VoiceError) = error == VoiceError.NO_MATCH || error == VoiceError.BUSY
}

/** Estado del dictado de UN campo. */
sealed interface VoiceState {
    data object Idle : VoiceState

    /** Esperando la respuesta al permiso de micrófono. */
    data object AwaitingPermission : VoiceState

    /** Escuchando: `partial` es lo que se lleva entendido (se muestra en vivo en el campo). */
    data class Listening(val partial: String = "") : VoiceState

    /** La persona tocó para terminar: se espera el resultado final. */
    data class Finishing(val partial: String = "") : VoiceState
    data class Failed(val error: VoiceError, val permanentlyDenied: Boolean = false) : VoiceState

    val active: Boolean get() = this is Listening || this is Finishing || this is AwaitingPermission
}

sealed interface VoiceEvent {
    /** Se tocó el micrófono. `serviceAvailable`: hay reconocedor en el teléfono; `micGranted`: el permiso ya está dado. */
    data class Tap(val serviceAvailable: Boolean, val micGranted: Boolean, val fallbackAvailable: Boolean = false) : VoiceEvent
    data class PermissionResult(val granted: Boolean, val canAskAgain: Boolean = true) : VoiceEvent
    data class Partial(val text: String) : VoiceEvent
    data class Final(val text: String) : VoiceEvent
    data class Error(val code: Int) : VoiceEvent
    /** La persona cerró el aviso o el campo salió de pantalla. */
    data object Dismiss : VoiceEvent
}

sealed interface VoiceEffect {
    data object RequestPermission : VoiceEffect
    data object StartListening : VoiceEffect
    data object StopListening : VoiceEffect
    data object Cancel : VoiceEffect
    /** Escribir en el campo (el texto ya unido con lo que había). */
    data class Commit(val spoken: String) : VoiceEffect
    /** Sin reconocedor propio pero sí el diálogo del sistema (`RecognizerIntent`). */
    data object LaunchSystemDialog : VoiceEffect
}

data class VoiceStep(val state: VoiceState, val effect: VoiceEffect? = null)

/** La máquina de estados (pura): `reduce(estado, evento)` → nuevo estado y qué hacer. */
object VoiceMachine {
    fun reduce(state: VoiceState, event: VoiceEvent): VoiceStep = when (event) {
        is VoiceEvent.Tap -> when (state) {
            is VoiceState.Listening -> VoiceStep(VoiceState.Finishing(state.partial), VoiceEffect.StopListening)
            is VoiceState.Finishing, VoiceState.AwaitingPermission -> VoiceStep(state)
            else -> when {
                !event.serviceAvailable && event.fallbackAvailable -> VoiceStep(VoiceState.Idle, VoiceEffect.LaunchSystemDialog)
                !event.serviceAvailable -> VoiceStep(VoiceState.Failed(VoiceError.NO_SERVICE))
                !event.micGranted -> VoiceStep(VoiceState.AwaitingPermission, VoiceEffect.RequestPermission)
                else -> VoiceStep(VoiceState.Listening(), VoiceEffect.StartListening)
            }
        }
        is VoiceEvent.PermissionResult -> when {
            state != VoiceState.AwaitingPermission -> VoiceStep(state)
            event.granted -> VoiceStep(VoiceState.Listening(), VoiceEffect.StartListening)
            else -> VoiceStep(VoiceState.Failed(VoiceError.PERMISSION, permanentlyDenied = !event.canAskAgain))
        }
        is VoiceEvent.Partial -> when (state) {
            is VoiceState.Listening -> VoiceStep(VoiceState.Listening(event.text))
            is VoiceState.Finishing -> VoiceStep(VoiceState.Finishing(event.text))
            else -> VoiceStep(state)
        }
        is VoiceEvent.Final -> when {
            !state.active -> VoiceStep(state)
            event.text.isBlank() -> VoiceStep(VoiceState.Failed(VoiceError.NO_MATCH))
            else -> VoiceStep(VoiceState.Idle, VoiceEffect.Commit(event.text))
        }
        is VoiceEvent.Error -> {
            val error = VoiceError.fromAndroid(event.code)
            val partial = when (state) { is VoiceState.Listening -> state.partial; is VoiceState.Finishing -> state.partial; else -> "" }
            when {
                !state.active -> VoiceStep(state)
                // Lo que ya se entendió no se pierde por un «no te escuché» al final.
                error == VoiceError.NO_MATCH && partial.isNotBlank() -> VoiceStep(VoiceState.Idle, VoiceEffect.Commit(partial))
                else -> VoiceStep(VoiceState.Failed(error), VoiceEffect.Cancel)
            }
        }
        VoiceEvent.Dismiss -> VoiceStep(VoiceState.Idle, if (state.active) VoiceEffect.Cancel else null)
    }
}

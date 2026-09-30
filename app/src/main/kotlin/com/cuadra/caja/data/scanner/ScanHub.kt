package com.cuadra.caja.data.scanner

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import com.cuadra.caja.domain.DeviceProfile
import com.cuadra.caja.domain.ScanCode
import com.cuadra.caja.domain.ScanDebounce
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Preferencias del lector (por teléfono, no se sincronizan). La cámara empieza encendida. El lector del equipo empieza APAGADO, salvo en un Zebra (TC77…), donde se
 * enciende solo la primera vez (`DeviceProfile.isZebra`); ver `ScanHub.firstRun`.
 */
data class ScannerSettings(
    val useReader: Boolean = false,
    val useCamera: Boolean = true,
    val beep: Boolean = true,
    /** Ya llegó al menos una ráfaga de un lector tipo teclado en este teléfono. */
    val wedgeSeen: Boolean = false,
    /** El perfil de DataWedge ya se configuró bien alguna vez (no se repite solo). */
    val dataWedgeSetUp: Boolean = false,
)

/** Lo que el equipo trae: DataWedge instalado, cámara, teclado físico. */
data class ReaderEnv(val dataWedge: Boolean = false, val hasCamera: Boolean = true, val hwKeyboard: Boolean = false)

enum class SetupState { IDLE, RUNNING, OK, FAILED, NO_ANSWER, ABSENT }
data class SetupUi(val state: SetupState = SetupState.IDLE, val detail: String? = null)

/** Quien recibe las lecturas del lector físico en cada momento (la última pantalla en pedirlo manda). */
fun interface ScanTarget { fun onScan(code: String, source: ScanSource) }

private val Context.scannerDataStore by preferencesDataStore("scanner")

private object K {
    val useReader = booleanPreferencesKey("use_reader")
    val useCamera = booleanPreferencesKey("use_camera")
    val beep = booleanPreferencesKey("beep")
    val wedgeSeen = booleanPreferencesKey("wedge_seen")
    val dwSetUp = booleanPreferencesKey("datawedge_set_up")
    val zebraChecked = booleanPreferencesKey("zebra_checked")
}

/**
 * Punto único de entrada de los lectores: cámara, teclado (wedge) y Zebra DataWedge. Filtra (apagado en Ajustes), quita repetidos (mismo código dentro
 * de `ScanTiming.DEBOUNCE_MS`), avisa (vibración y pitido), guarda la última lectura para la prueba de Ajustes y la entrega a quien la pidió.
 * Los objetos de pantalla (claim/submit) se usan desde el hilo principal.
 */
class ScanHub(private val context: Context, private val scope: CoroutineScope) {
    private val store = context.applicationContext.scannerDataStore

    val settings: StateFlow<ScannerSettings> = store.data.catch { if (it is IOException) emit(emptyPreferences()) else throw it }.map { p ->
        ScannerSettings(p[K.useReader] ?: false, p[K.useCamera] ?: true, p[K.beep] ?: true, p[K.wedgeSeen] ?: false, p[K.dwSetUp] ?: false)
    }.stateIn(scope, SharingStarted.Eagerly, ScannerSettings())

    private val _env = MutableStateFlow(ReaderEnv())
    val env: StateFlow<ReaderEnv> = _env.asStateFlow()

    private val _last = MutableStateFlow<ScanRecord?>(null)
    val last: StateFlow<ScanRecord?> = _last.asStateFlow()
    private val _count = MutableStateFlow(0)
    val count: StateFlow<Int> = _count.asStateFlow()
    private val _setup = MutableStateFlow(SetupUi())
    val setup: StateFlow<SetupUi> = _setup.asStateFlow()

    /** Aviso de una sola vez: «Zebra detectada: lector del equipo activado». */
    private val _zebraNotice = MutableStateFlow(false)
    val zebraNotice: StateFlow<Boolean> = _zebraNotice.asStateFlow()
    fun dismissZebraNotice() { _zebraNotice.value = false }

    /** Solo para compilaciones de depuración: hace de cuenta que el equipo es un Zebra (probar el arranque automático en un emulador). */
    @Volatile var debugZebra = false

    /** «Lector listo»: el lector del equipo está encendido y hay señales de que existe uno (DataWedge, teclado físico o una ráfaga ya vista). */
    val ready: StateFlow<Boolean> = combine(settings, env) { s, e -> s.useReader && (e.dataWedge || e.hwKeyboard || s.wedgeSeen) }
        .stateIn(scope, SharingStarted.Eagerly, false)

    private val targets = ArrayList<ScanTarget>()
    private val debounce = ScanDebounce()
    private val feedback = ScanFeedback(context)
    private val dataWedge = DataWedgeClient(context, this, scope)
    val keys = WedgeKeys(this)

    val hasTarget: Boolean get() = targets.isNotEmpty()

    /** Pide recibir las lecturas del lector físico mientras la pantalla está a la vista; cerrar el resultado deja de recibirlas. */
    fun claim(target: ScanTarget): AutoCloseable {
        targets += target
        return AutoCloseable { targets.remove(target) }
    }

    /** Lectura del lector físico (DataWedge o teclado): pasa por el filtro y llega a quien la pidió. */
    fun submit(raw: String, source: ScanSource, label: String? = null): Boolean {
        val code = ScanCode.normalize(raw)
        if (code.isEmpty() || !settings.value.useReader) return false
        if (!debounce.accept(code, SystemClock.elapsedRealtime())) return false
        if (source == ScanSource.WEDGE && !settings.value.wedgeSeen) scope.launch { store.edit { it[K.wedgeSeen] = true } }
        emit(code, source, label)
        targets.lastOrNull()?.onScan(code, source)
        return true
    }

    /** Lectura de la cámara (ya confirmada y sin repetidos): solo se avisa y se guarda para la prueba. */
    fun record(raw: String, label: String? = null) {
        val code = ScanCode.normalize(raw)
        if (code.isNotEmpty()) emit(code, ScanSource.CAMERA, label)
    }

    private fun emit(code: String, source: ScanSource, label: String?) {
        _last.value = scanRecord(code, source, label, System.currentTimeMillis())
        _count.update { it + 1 }
        feedback.play(settings.value.beep)
    }

    fun clearTest() { _last.value = null; _count.value = 0 }

    fun setUseReader(on: Boolean) { scope.launch { store.edit { it[K.useReader] = on } } }
    fun setUseCamera(on: Boolean) { scope.launch { store.edit { it[K.useCamera] = on } } }
    fun setBeep(on: Boolean) { scope.launch { store.edit { it[K.beep] = on } } }

    // ---------- ciclo de vida (MainActivity) ----------
    fun attach() {
        refreshEnv()
        dataWedge.attach()
        // Primera vez con DataWedge: se configura solo. Si falla, queda el botón de Ajustes.
        if (_env.value.dataWedge && !settings.value.dataWedgeSetUp && settings.value.useReader) configureDataWedge()
    }

    fun detach() { dataWedge.detach(); keys.reset() }

    /**
     * Primer arranque de esta instalación: en un Zebra (fabricante Zebra/Symbol o DataWedge instalado) enciende «Usar el lector del equipo», crea el perfil de DataWedge
     * y avisa una vez; en cualquier otro teléfono lo deja APAGADO (la cámara sigue siendo lo básico). Respeta lo que la persona ya haya elegido a mano.
     */
    fun firstRun() {
        scope.launch {
            val prefs = runCatching { store.data.first() }.getOrNull() ?: return@launch
            if (prefs[K.zebraChecked] == true && !debugZebra) return@launch
            val zebra = debugZebra || DeviceProfile.isZebra(Build.MANUFACTURER, Build.MODEL, dataWedge.isPresent())
            var enabled = false
            store.edit {
                it[K.zebraChecked] = true
                if (zebra && (debugZebra || it[K.useReader] == null)) it[K.useReader] = true
                enabled = zebra && it[K.useReader] == true
            }
            if (enabled) {
                refreshEnv()
                configureDataWedge()
                _zebraNotice.value = true
            }
            debugZebra = false
        }
    }

    fun refreshEnv() {
        val pm = context.packageManager
        _env.value = ReaderEnv(
            dataWedge = dataWedge.isPresent(),
            hasCamera = pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY),
            hwKeyboard = context.resources.configuration.keyboard != Configuration.KEYBOARD_NOKEYS,
        )
    }

    fun configureDataWedge() = dataWedge.configure()

    internal fun setSetup(state: SetupState, detail: String? = null) {
        _setup.value = SetupUi(state, detail)
        if (state == SetupState.OK) scope.launch { store.edit { it[K.dwSetUp] = true } }
    }
}

/** Vibración corta y pitido del sistema (respeta el modo silencio: si el timbre no está en normal, no suena). */
class ScanFeedback(private val context: Context) {
    private val handler = Handler(Looper.getMainLooper())

    fun play(beep: Boolean) {
        runCatching {
            val vibrator = if (Build.VERSION.SDK_INT >= 31) context.getSystemService(VibratorManager::class.java)?.defaultVibrator
            else @Suppress("DEPRECATION") context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            if (vibrator?.hasVibrator() == true) vibrator.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE))
        }
        if (!beep) return
        runCatching {
            val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            if (audio.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
            val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 70)
            tone.startTone(ToneGenerator.TONE_PROP_BEEP, 90)
            handler.postDelayed({ runCatching { tone.release() } }, 300)
        }
    }
}

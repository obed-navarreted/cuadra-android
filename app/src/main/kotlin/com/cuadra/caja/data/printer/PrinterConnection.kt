package com.cuadra.caja.data.printer

import com.cuadra.caja.domain.printing.Backoff
import com.cuadra.caja.domain.printing.ConnIssue
import com.cuadra.caja.domain.printing.ConnState
import com.cuadra.caja.domain.printing.PrintOutcome
import com.cuadra.caja.domain.printing.PrinterSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.coroutineContext

/**
 * El administrador de la conexión con la impresora: mantiene el estado (DESCONECTADA / CONECTANDO / CONECTADA / ERROR) y hace que el enlace se recupere solo.
 *
 * - Solo trabaja con la opción ACTIVADA y la app a la vista (`setForeground`); en segundo plano cierra el enlace y no intenta nada (no hay servicio).
 * - Reintenta con espera creciente 2 s → 4 → 8 → 16 → 30 s (`Backoff`); al volver a primer plano, al pulsar «Reconectar» o cuando el sistema avisa (Bluetooth
 *   encendido, cable puesto) reintenta de inmediato.
 * - Con el enlace vivo lo comprueba cada `healthEveryMs` (~15 s) con `PrinterTransport.isAlive`; si murió (impresora dormida o apagada) pasa a DESCONECTADA y reconecta.
 * - ANTES de imprimir, si no está CONECTADA, hace una conexión nueva (máx. `connectTimeoutMs`); si la escritura falla reconecta una vez y reintenta.
 * - Un trabajo que no se pudo imprimir queda guardado (`hasPending`) para el «Reintentar».
 *
 * No conoce Android: recibe el `transportFactory` (devuelve nulo si no hay impresora elegida). Las pruebas lo mueven con tiempo virtual.
 */
class PrinterConnection(
    private val scope: CoroutineScope,
    val settings: StateFlow<PrinterSettings>,
    private val transportFactory: (PrinterSettings) -> PrinterTransport?,
    private val connectTimeoutMs: Long = 5_000,
    private val healthEveryMs: Long = 15_000,
) {
    private val _state = MutableStateFlow(ConnState.DISCONNECTED)
    val state: StateFlow<ConnState> = _state.asStateFlow()
    private val _issue = MutableStateFlow(ConnIssue.NONE)
    val issue: StateFlow<ConnIssue> = _issue.asStateFlow()
    private val _hasPending = MutableStateFlow(false)
    val hasPending: StateFlow<Boolean> = _hasPending.asStateFlow()

    private val lock = Mutex()
    private var transport: PrinterTransport? = null
    private val kick = Channel<Unit>(Channel.CONFLATED)
    private var loop: Job? = null
    private var pending: ByteArray? = null
    @Volatile private var foreground = false
    @Volatile private var failures = 0

    init {
        // Cambiar la opción, el tipo de conexión o la impresora elegida: se suelta el enlace y se evalúa de nuevo.
        scope.launch {
            settings.map { Triple(it.enabled, it.link, it.deviceKey) }.distinctUntilChanged().collect {
                failures = 0
                lock.withLock { closeLocked() }
                if (!settings.value.enabled) { _issue.value = ConnIssue.NONE; pending = null; _hasPending.value = false }
                reevaluate()
                kick.trySend(Unit) // si ya había un bucle esperando, que conecte la impresora nueva sin esperar
            }
        }
    }

    // ---------- lo que le avisa el resto de la app ----------

    /** La app se puso a la vista (`true`) o pasó a segundo plano (`false`). */
    fun setForeground(value: Boolean) {
        foreground = value
        failures = 0
        reevaluate() // al ponerse a la vista el bucle arranca y lo primero que hace es conectar: no hace falta avisarle
    }

    /** «Reconectar»: suelta el enlace actual y reconecta ya, sin esperar. */
    fun reconnectNow() {
        scope.launch {
            failures = 0
            lock.withLock { closeLocked() }
            reevaluate()
            kick.trySend(Unit)
        }
    }

    /** El sistema avisó que el enlace se cortó (ACL desconectado, cable retirado): se suelta y se reintenta de inmediato. */
    fun onLinkLost() {
        scope.launch {
            failures = 0
            lock.withLock { closeLocked() }
            kick.trySend(Unit)
        }
    }

    /** El Bluetooth se encendió: se reintenta ya. Si se apagó, es como perder el enlace. */
    fun onBluetoothState(on: Boolean) {
        if (on) { failures = 0; kick.trySend(Unit) } else onLinkLost()
    }

    /** Se puso un cable USB: se reintenta ya. */
    fun onUsbAttached() { failures = 0; kick.trySend(Unit) }

    // ---------- imprimir ----------

    /**
     * Manda un trabajo. Si no hay enlace, primero intenta una conexión nueva. Devuelve `PRINTED`, `DISABLED` (opción apagada), `NOT_CONNECTED` (no se pudo conectar)
     * o `FAILED` (se conectó pero falló al escribir, incluso reconectando una vez). Un trabajo no impreso queda para `retryPending`.
     */
    suspend fun print(bytes: ByteArray): PrintOutcome {
        if (!settings.value.enabled) return PrintOutcome.DISABLED
        val result = lock.withLock {
            when {
                _state.value != ConnState.CONNECTED && !connectLocked() -> PrintOutcome.NOT_CONNECTED
                writeLocked(bytes) -> PrintOutcome.PRINTED
                connectLocked() && writeLocked(bytes) -> PrintOutcome.PRINTED
                else -> PrintOutcome.FAILED
            }
        }
        if (result == PrintOutcome.PRINTED) { pending = null; _hasPending.value = false }
        else { pending = bytes; _hasPending.value = true }
        if (result != PrintOutcome.PRINTED) kick.trySend(Unit)
        return result
    }

    /** «Reintentar»: vuelve a mandar el último trabajo que falló. */
    suspend fun retryPending(): PrintOutcome = pending?.let { print(it) } ?: PrintOutcome.FAILED

    // ---------- interno ----------

    @Synchronized private fun reevaluate() {
        val run = settings.value.enabled && foreground
        if (run && loop?.isActive != true) {
            loop = scope.launch { runLoop() }
        } else if (!run) {
            loop?.cancel(); loop = null
            scope.launch { lock.withLock { closeLocked() } }
        }
    }

    private suspend fun runLoop() {
        while (coroutineContext.isActive) {
            if (!settings.value.enabled) return
            if (_state.value != ConnState.CONNECTED) {
                if (lock.withLock { connectLocked() }) { failures = 0; continue }
                failures++
                kick.tryReceive() // un aviso que llegó durante este intento ya no sirve: el intento acaba de fallar
                // Sin impresora elegida no hay nada que reintentar hasta que cambie un ajuste; lo demás (apagada, dormida, fuera de alcance) sí.
                if (_issue.value == ConnIssue.NO_DEVICE) withTimeoutOrNull(Long.MAX_VALUE / 4) { kick.receive() }
                else withTimeoutOrNull(Backoff.delayMs(failures)) { kick.receive() }
            } else {
                withTimeoutOrNull(healthEveryMs) { kick.receive() }
                if (_state.value == ConnState.CONNECTED && !lock.withLock { transport?.isAlive() == true }) lock.withLock { closeLocked() }
            }
        }
    }

    /** Conecta (con el candado tomado). `true` si quedó CONECTADA. */
    private suspend fun connectLocked(): Boolean {
        if (_state.value == ConnState.CONNECTED && transport != null) return true
        val t = transportFactory(settings.value)
        if (t == null) { _issue.value = ConnIssue.NO_DEVICE; _state.value = ConnState.DISCONNECTED; return false }
        _state.value = ConnState.CONNECTING
        try {
            val done = withTimeoutOrNull(connectTimeoutMs + 1_500) { t.connect(connectTimeoutMs); true } ?: false
            if (!done) throw TransportException(ConnIssue.FAILED, "tiempo agotado")
            transport = t; _issue.value = ConnIssue.NONE; _state.value = ConnState.CONNECTED
            return true
        } catch (e: CancellationException) {
            runCatching { t.close() }; _state.value = ConnState.DISCONNECTED
            throw e
        } catch (e: Exception) {
            runCatching { t.close() }
            transport = null
            _issue.value = (e as? TransportException)?.issue ?: ConnIssue.FAILED
            _state.value = ConnState.ERROR
            return false
        }
    }

    private suspend fun writeLocked(bytes: ByteArray): Boolean = try {
        transport?.write(bytes) ?: throw java.io.IOException("sin enlace")
        true
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        closeLocked()
        false
    }

    private fun closeLocked() {
        runCatching { transport?.close() }
        transport = null
        _state.value = ConnState.DISCONNECTED
    }
}

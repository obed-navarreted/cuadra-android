package com.cuadra.caja.data.printer

import android.util.Log
import com.cuadra.caja.domain.printing.ConnIssue
import com.cuadra.caja.domain.printing.EscPosDump
import com.cuadra.caja.domain.printing.PrintCharset
import java.io.IOException
import kotlinx.coroutines.delay

/** Falla de conexión con su motivo (`ConnIssue`), para explicarla en Ajustes. */
class TransportException(val issue: ConnIssue, message: String? = null) : IOException(message ?: issue.name)

/**
 * Un canal hacia la impresora: Bluetooth clásico (SPP), cable USB, o uno simulado (pruebas y vista previa). Nada aquí depende de la red.
 * `connect` y `write` lanzan `IOException` si algo falla; `isAlive` es una comprobación liviana del enlace (sin imprimir nada).
 */
interface PrinterTransport {
    suspend fun connect(timeoutMs: Long)
    suspend fun write(bytes: ByteArray)
    suspend fun isAlive(): Boolean
    fun close()
}

/**
 * Impresora simulada en memoria: guarda cada trabajo en `written`, puede fallar al conectar `failConnect` veces, tardar `connectDelayMs` y «morir» (`alive = false`).
 * Es la de las pruebas y la que usa el gancho de depuración (`debug_printer`) para probar en un emulador sin impresora.
 */
class FakePrinterTransport(
    var failConnect: Int = 0, var connectDelayMs: Long = 0, @Volatile var alive: Boolean = true, private val logTag: String? = null,
) : PrinterTransport {
    val written = mutableListOf<ByteArray>()
    @Volatile var connected = false
    @Volatile var connectCalls = 0
    @Volatile var closeCalls = 0

    override suspend fun connect(timeoutMs: Long) {
        connectCalls++
        if (connectDelayMs > 0) delay(connectDelayMs)
        if (failConnect > 0) { failConnect--; throw TransportException(ConnIssue.NOT_FOUND, "simulada: no responde") }
        connected = true
    }

    override suspend fun write(bytes: ByteArray) {
        if (!connected || !alive) throw IOException("simulada: enlace caído")
        written += bytes
        if (logTag != null && !bytes.contentEquals(com.cuadra.caja.domain.printing.EscPos.STATUS_QUERY)) {
            Log.i(logTag, "BYTES ${bytes.size}: " + bytes.joinToString(" ") { "%02X".format(it) })
            EscPosDump.toText(bytes, PrintCharset.PC858).lines().forEach { Log.i(logTag, it) }
        }
    }

    override suspend fun isAlive(): Boolean = connected && alive
    override fun close() { connected = false; closeCalls++ }
}

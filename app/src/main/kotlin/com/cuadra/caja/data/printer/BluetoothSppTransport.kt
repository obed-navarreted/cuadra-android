package com.cuadra.caja.data.printer

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.cuadra.caja.domain.printing.ConnIssue
import com.cuadra.caja.domain.printing.EscPos
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Permiso de Bluetooth de esta versión de Android: `BLUETOOTH_CONNECT` desde Android 12; antes, `BLUETOOTH` (normal, sin pedirlo). No se usa ubicación. */
object BluetoothAccess {
    fun granted(context: Context): Boolean =
        Build.VERSION.SDK_INT < 31 || ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    fun hasBluetooth(context: Context): Boolean = context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)

    fun enabled(context: Context): Boolean = runCatching { context.getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled == true }.getOrDefault(false)
}

/**
 * Bluetooth clásico (SPP / RFCOMM, UUID 00001101-0000-1000-8000-00805F9B34FB) con una impresora YA emparejada en los ajustes de Android.
 * Cancela la búsqueda antes de conectar (con búsqueda activa la conexión se vuelve lenta e inestable), intenta primero el socket seguro y, si falla, el inseguro
 * (muchas impresoras baratas no negocian bien el emparejamiento seguro). Un vigilante cierra el socket si `connect()` (bloqueante) pasa del tiempo.
 */
@SuppressLint("MissingPermission")
class BluetoothSppTransport(private val context: Context, private val address: String) : PrinterTransport {
    @Volatile private var socket: BluetoothSocket? = null

    override suspend fun connect(timeoutMs: Long) = withContext(Dispatchers.IO) {
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: throw TransportException(ConnIssue.FAILED, "sin Bluetooth")
        if (!BluetoothAccess.granted(context)) throw TransportException(ConnIssue.NO_PERMISSION)
        if (!adapter.isEnabled) throw TransportException(ConnIssue.BLUETOOTH_OFF)
        val device = try { adapter.getRemoteDevice(address) } catch (_: IllegalArgumentException) { throw TransportException(ConnIssue.NOT_FOUND, "dirección no válida") }
        try { adapter.cancelDiscovery() } catch (_: SecurityException) {}
        close()
        val started = SystemClock.elapsedRealtime()
        var last: Exception? = null
        for (secure in listOf(true, false)) {
            val left = timeoutMs - (SystemClock.elapsedRealtime() - started)
            if (left < 800) break
            val s = try {
                if (secure) device.createRfcommSocketToServiceRecord(SPP) else device.createInsecureRfcommSocketToServiceRecord(SPP)
            } catch (e: SecurityException) { throw TransportException(ConnIssue.NO_PERMISSION) } catch (e: IOException) { last = e; continue }
            socket = s
            val watchdog = launch { delay(left); runCatching { s.close() } }
            try {
                s.connect()
                watchdog.cancel()
                return@withContext
            } catch (e: SecurityException) {
                watchdog.cancel(); runCatching { s.close() }
                throw TransportException(ConnIssue.NO_PERMISSION)
            } catch (e: IOException) {
                watchdog.cancel(); runCatching { s.close() }
                last = e
            }
        }
        socket = null
        throw TransportException(ConnIssue.FAILED, last?.message)
    }

    override suspend fun write(bytes: ByteArray) = withContext(Dispatchers.IO) {
        val out = socket?.takeIf { it.isConnected }?.outputStream ?: throw IOException("sin enlace")
        var off = 0
        while (off < bytes.size) {
            val n = minOf(CHUNK, bytes.size - off)
            out.write(bytes, off, n)
            off += n
            if (off < bytes.size) delay(CHUNK_PAUSE_MS) // las impresoras baratas tienen un búfer chico: se les da tiempo de imprimir
        }
        out.flush()
    }

    /** `DLE EOT 1` (estado en tiempo real): no imprime; si el enlace está roto, escribir falla. Lo que conteste se descarta. */
    override suspend fun isAlive(): Boolean = withContext(Dispatchers.IO) {
        val s = socket ?: return@withContext false
        if (!s.isConnected) return@withContext false
        try {
            s.outputStream.write(EscPos.STATUS_QUERY)
            s.outputStream.flush()
            val input = s.inputStream
            val n = input.available()
            if (n > 0) input.skip(n.toLong())
            true
        } catch (_: IOException) { false }
    }

    override fun close() {
        val s = socket
        socket = null
        runCatching { s?.close() }
    }

    companion object {
        val SPP: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        private const val CHUNK = 512
        private const val CHUNK_PAUSE_MS = 15L
    }
}

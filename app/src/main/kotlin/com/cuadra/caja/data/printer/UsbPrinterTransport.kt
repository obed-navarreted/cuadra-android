package com.cuadra.caja.data.printer

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.cuadra.caja.domain.printing.ConnIssue
import java.io.IOException
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Una impresora (o dispositivo) USB conectado. `key` = «vendorId:productId» en hexadecimal. `isPrinter` = alguna interfaz de clase 7 (impresora). */
data class UsbInfo(val key: String, val name: String, val isPrinter: Boolean)

object UsbPrinters {
    fun key(vendorId: Int, productId: Int): String = "%04x:%04x".format(vendorId, productId)

    fun key(d: UsbDevice): String = key(d.vendorId, d.productId)

    private fun UsbDevice.hasPrinterInterface(): Boolean = (0 until interfaceCount).any { getInterface(it).interfaceClass == UsbConstants.USB_CLASS_PRINTER }

    private fun UsbDevice.label(): String {
        val product = runCatching { productName }.getOrNull()?.takeIf { it.isNotBlank() }
        val maker = runCatching { manufacturerName }.getOrNull()?.takeIf { it.isNotBlank() }
        return listOfNotNull(maker, product).joinToString(" ").ifBlank { "USB " + key(this) }
    }

    /** Dispositivos USB conectados: primero los de clase impresora. */
    fun list(context: Context): List<UsbInfo> {
        val usb = context.getSystemService(UsbManager::class.java) ?: return emptyList()
        return runCatching { usb.deviceList.values.map { UsbInfo(key(it), it.label(), it.hasPrinterInterface()) }.sortedByDescending { it.isPrinter } }.getOrDefault(emptyList())
    }

    /** El dispositivo elegido (`key`) o, si no hay elección, el primero de clase impresora. */
    fun find(usb: UsbManager, key: String?): UsbDevice? {
        val devices = usb.deviceList.values
        if (!key.isNullOrBlank()) return devices.firstOrNull { key(it) == key }
        return devices.firstOrNull { it.hasPrinterInterface() }
    }
}

/**
 * Impresora por cable USB (host/OTG): pide el permiso de Android de forma explícita (`UsbManager.requestPermission` con un `PendingIntent`), reclama la interfaz de
 * impresora y escribe por el endpoint de salida «bulk» en trozos. Si ya se pidió el permiso y se negó, no vuelve a preguntar hasta que se reconecte a mano.
 */
class UsbPrinterTransport(private val context: Context, private val key: String?) : PrinterTransport {
    private var device: UsbDevice? = null
    private var connection: UsbDeviceConnection? = null
    private var iface: UsbInterface? = null
    private var out: UsbEndpoint? = null

    override suspend fun connect(timeoutMs: Long) {
        val usb = context.getSystemService(UsbManager::class.java) ?: throw TransportException(ConnIssue.FAILED, "sin USB host")
        val dev = UsbPrinters.find(usb, key) ?: throw TransportException(ConnIssue.NOT_FOUND, "no hay impresora USB")
        if (!usb.hasPermission(dev)) {
            val k = UsbPrinters.key(dev)
            if (!asked.add(k) || !requestPermission(context, usb, dev)) throw TransportException(ConnIssue.USB_PERMISSION)
        }
        withContext(Dispatchers.IO) {
            close()
            // Interfaz: la de clase impresora si la hay; si no, la primera con un endpoint de salida «bulk».
            val candidates = (0 until dev.interfaceCount).map { dev.getInterface(it) }.sortedByDescending { it.interfaceClass == UsbConstants.USB_CLASS_PRINTER }
            val chosen = candidates.firstNotNullOfOrNull { i ->
                (0 until i.endpointCount).map { i.getEndpoint(it) }.firstOrNull { it.type == UsbConstants.USB_ENDPOINT_XFER_BULK && it.direction == UsbConstants.USB_DIR_OUT }?.let { i to it }
            } ?: throw TransportException(ConnIssue.FAILED, "sin endpoint de salida")
            val c = usb.openDevice(dev) ?: throw TransportException(ConnIssue.USB_PERMISSION)
            if (!c.claimInterface(chosen.first, true)) { c.close(); throw TransportException(ConnIssue.FAILED, "no se pudo reclamar la interfaz") }
            device = dev; connection = c; iface = chosen.first; out = chosen.second
        }
    }

    override suspend fun write(bytes: ByteArray) = withContext(Dispatchers.IO) {
        val c = connection ?: throw IOException("sin enlace")
        val ep = out ?: throw IOException("sin enlace")
        var off = 0
        while (off < bytes.size) {
            val n = minOf(CHUNK, bytes.size - off)
            val sent = c.bulkTransfer(ep, bytes, off, n, WRITE_TIMEOUT_MS)
            if (sent < 0) throw IOException("falló la escritura USB")
            off += sent
        }
    }

    override suspend fun isAlive(): Boolean {
        val d = device ?: return false
        val usb = context.getSystemService(UsbManager::class.java) ?: return false
        return connection != null && runCatching { usb.deviceList.values.any { it.deviceId == d.deviceId } }.getOrDefault(false)
    }

    override fun close() {
        val c = connection
        val i = iface
        connection = null; iface = null; out = null; device = null
        runCatching { if (c != null && i != null) c.releaseInterface(i) }
        runCatching { c?.close() }
    }

    companion object {
        private const val CHUNK = 4096
        private const val WRITE_TIMEOUT_MS = 5_000
        private const val ACTION = "com.cuadra.caja.USB_PERMISSION"

        /** Dispositivos a los que ya se les pidió permiso en esta sesión (no se repite el diálogo en cada reintento). */
        private val asked = java.util.Collections.synchronizedSet(mutableSetOf<String>())

        fun forgetAsked() = asked.clear()

        /** Diálogo del sistema «¿Permitir que Cuentiva acceda al dispositivo USB?». Devuelve si se concedió (o `false` si se negó o pasaron 60 s). */
        private suspend fun requestPermission(context: Context, usb: UsbManager, dev: UsbDevice): Boolean = withTimeoutOrNull(60_000) {
            suspendCancellableCoroutine { cont ->
                val receiver = object : BroadcastReceiver() {
                    override fun onReceive(c: Context, intent: Intent) {
                        if (intent.action != ACTION) return
                        runCatching { context.unregisterReceiver(this) }
                        if (cont.isActive) cont.resume(intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false))
                    }
                }
                ContextCompat.registerReceiver(context, receiver, IntentFilter(ACTION), ContextCompat.RECEIVER_NOT_EXPORTED)
                cont.invokeOnCancellation { runCatching { context.unregisterReceiver(receiver) } }
                // Android exige un PendingIntent MUTABLE (el sistema le agrega el resultado) y, con uno mutable, un intent explícito (nuestro paquete).
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
                usb.requestPermission(dev, PendingIntent.getBroadcast(context, 0, Intent(ACTION).setPackage(context.packageName), flags))
            }
        } ?: false
    }
}

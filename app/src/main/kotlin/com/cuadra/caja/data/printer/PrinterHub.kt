package com.cuadra.caja.data.printer

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import com.cuadra.caja.core.i18n.AppLocale
import com.cuadra.caja.core.model.Currency
import com.cuadra.caja.data.local.Db
import com.cuadra.caja.data.sync.calendar
import com.cuadra.caja.domain.SaleView
import com.cuadra.caja.domain.printing.ConnIssue
import com.cuadra.caja.domain.printing.ConnState
import com.cuadra.caja.domain.printing.PrintOutcome
import com.cuadra.caja.domain.printing.PrinterBadge
import com.cuadra.caja.domain.printing.PrinterLink
import com.cuadra.caja.domain.printing.PrinterSamples
import com.cuadra.caja.domain.printing.PrinterSettings
import com.cuadra.caja.domain.printing.Receipt
import com.cuadra.caja.domain.printing.SaleReceipts
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** Una impresora que se puede elegir: dirección Bluetooth o «vendorId:productId» USB. `printer` = el equipo se anuncia como impresora. */
data class PrinterDevice(val key: String, val name: String, val printer: Boolean = false)

/** Modos del gancho de depuración (`debug_printer`): una impresora simulada que sí conecta, o una que nunca conecta. Solo existe en compilaciones de depuración. */
enum class DebugPrinter { CONNECTED, DISCONNECTED }

/**
 * La impresora térmica de la app: ajustes, conexión que se recupera sola (`PrinterConnection`), avisos del sistema (Bluetooth y USB) e impresión de ventas,
 * de la prueba y de la vista previa. Sin servicio en segundo plano: `attach`/`detach` los llama `MainActivity` al ponerse a la vista y al salir.
 * Nada aquí necesita red.
 */
class PrinterHub(private val context: Context, private val scope: CoroutineScope, private val db: Db) {
    val prefs = PrinterPrefs(context, scope)
    val settings: StateFlow<PrinterSettings> get() = prefs.settings

    @Volatile var debugMode: DebugPrinter? = null
        set(value) { field = value; connection.reconnectNow() }
    private val debugFake = FakePrinterTransport(logTag = "CuentivaPrint")

    val connection = PrinterConnection(scope, prefs.settings, ::transportFor)
    val badge: StateFlow<PrinterBadge> = combine(prefs.settings, connection.state) { s, c -> PrinterBadge.of(s.enabled, c) }.stateIn(scope, SharingStarted.Eagerly, PrinterBadge.OFF)

    /** Sube cuando se conecta o desconecta un cable USB o cambia el Bluetooth: Ajustes vuelve a leer las listas. */
    val devicesChanged = MutableStateFlow(0)

    private fun transportFor(s: PrinterSettings): PrinterTransport? {
        debugMode?.let { return if (it == DebugPrinter.CONNECTED) debugFake else FakePrinterTransport(failConnect = Int.MAX_VALUE) }
        return when (s.link) {
            PrinterLink.BLUETOOTH -> s.deviceKey?.takeIf { it.isNotBlank() }?.let { BluetoothSppTransport(context, it) }
            PrinterLink.USB -> UsbPrinterTransport(context, s.deviceKey)
        }
    }

    // ---------- ciclo de vida (MainActivity) ----------
    private var registered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) {
            runCatching {
                val s = settings.value
                fun address() = runCatching { IntentCompat.getParcelableExtra(intent, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)?.address }.getOrNull()
                when (intent.action) {
                    BluetoothDevice.ACTION_ACL_DISCONNECTED -> if (s.enabled && s.link == PrinterLink.BLUETOOTH && address() == s.deviceKey) connection.onLinkLost()
                    BluetoothDevice.ACTION_ACL_CONNECTED -> if (s.enabled && s.link == PrinterLink.BLUETOOTH && address() == s.deviceKey) connection.onBluetoothState(true)
                    BluetoothAdapter.ACTION_STATE_CHANGED -> when (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)) {
                        BluetoothAdapter.STATE_ON -> connection.onBluetoothState(true)
                        BluetoothAdapter.STATE_OFF -> connection.onBluetoothState(false)
                    }
                    UsbManager.ACTION_USB_DEVICE_ATTACHED -> { UsbPrinterTransport.forgetAsked(); connection.onUsbAttached() }
                    UsbManager.ACTION_USB_DEVICE_DETACHED -> if (s.enabled && s.link == PrinterLink.USB) connection.onLinkLost()
                }
                devicesChanged.value++
            }
        }
    }

    /** La app se puso a la vista: se escuchan los avisos del sistema y se reconecta de inmediato (solo si la opción está activada). */
    fun attach() {
        if (!registered) runCatching {
            val f = IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED); addAction(BluetoothDevice.ACTION_ACL_CONNECTED); addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
                addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED); addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            }
            ContextCompat.registerReceiver(context, receiver, f, ContextCompat.RECEIVER_NOT_EXPORTED)
            registered = true
        }
        UsbPrinterTransport.forgetAsked()
        connection.setForeground(true)
    }

    /** La app salió de la vista: se dejan de escuchar los avisos y se suelta el enlace (sin servicio en segundo plano no se intenta nada). */
    fun detach() {
        connection.setForeground(false)
        if (registered) { registered = false; runCatching { context.unregisterReceiver(receiver) } }
    }

    fun reconnect() { UsbPrinterTransport.forgetAsked(); connection.reconnectNow() }

    // ---------- listas y permisos ----------
    fun bluetoothPermission(): Boolean = debugMode != null || BluetoothAccess.granted(context)
    fun bluetoothOn(): Boolean = debugMode != null || BluetoothAccess.enabled(context)
    fun hasBluetooth(): Boolean = debugMode != null || BluetoothAccess.hasBluetooth(context)
    fun hasUsbHost(): Boolean = context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_USB_HOST)

    /** Los equipos Bluetooth YA emparejados en Android (no se busca nada: sin ubicación). Las impresoras primero. */
    @SuppressLint("MissingPermission")
    fun bondedDevices(): List<PrinterDevice> {
        if (debugMode != null) return listOf(PrinterDevice("00:11:22:33:44:55", "MTP-II", true), PrinterDevice("AA:BB:CC:DD:EE:FF", "Impresora térmica de la barra del mostrador Sucursal Centro 58mm", true), PrinterDevice("11:22:33:44:55:66", "Audífonos de Kevin"))
        if (!BluetoothAccess.granted(context)) return emptyList()
        return runCatching {
            context.getSystemService(BluetoothManager::class.java)?.adapter?.bondedDevices.orEmpty()
                .map { PrinterDevice(it.address, it.name?.takeIf { n -> n.isNotBlank() } ?: it.address, it.bluetoothClass?.majorDeviceClass == BluetoothClass.Device.Major.IMAGING) }
                .sortedWith(compareByDescending<PrinterDevice> { it.printer }.thenBy { it.name.lowercase() })
        }.getOrDefault(emptyList())
    }

    fun usbDevices(): List<PrinterDevice> = UsbPrinters.list(context).map { PrinterDevice(it.key, it.name, it.isPrinter) }

    // ---------- imprimir ----------
    private fun language(): String = AppLocale.current().tag ?: Locale.getDefault().language

    private suspend fun render(sample: (name: String, zone: java.time.ZoneId, money: (Long) -> String) -> Receipt): Receipt? {
        val b = db.directory().businessNow() ?: return null
        val s = settings.value
        val money = SaleReceipts.money(Currency.of(b.currency), Locale.Builder().setLanguage("es").setRegion(b.country).build(), s.charset)
        return sample(b.name, b.calendar().zone, money)
    }

    /** El recibo de una venta con los ajustes de ahora (también las anuladas: llevan el aviso «ANULADA»). */
    suspend fun receiptFor(sale: SaleView): Receipt? = render { name, zone, money -> SaleReceipts.receipt(sale, name, zone, settings.value, language(), money) }

    /** Imprime el recibo de una venta (con las copias elegidas). Con la opción apagada no hace nada. */
    suspend fun printSale(sale: SaleView): PrintOutcome {
        val s = settings.value
        if (!s.enabled) return PrintOutcome.DISABLED
        val receipt = receiptFor(sale) ?: return PrintOutcome.FAILED
        return connection.print(receipt.toBytes(s.copies, s.endSpacing, s.hasCutter))
    }

    /** Imprime el comprobante de una devolución (una copia). Con la opción apagada no hace nada. */
    suspend fun printReturn(sale: SaleView, ret: com.cuadra.caja.domain.SaleReturnView): PrintOutcome {
        val s = settings.value
        if (!s.enabled) return PrintOutcome.DISABLED
        val receipt = render { name, zone, money -> SaleReceipts.returnReceipt(sale, ret, name, zone, s, language(), money) } ?: return PrintOutcome.FAILED
        return connection.print(receipt.toBytes(1, s.endSpacing, s.hasCutter))
    }

    suspend fun printTest(): PrintOutcome {
        val s = settings.value
        if (!s.enabled) return PrintOutcome.DISABLED
        val r = render { name, zone, money -> PrinterSamples.test(name, s, language(), money, zone, System.currentTimeMillis()) } ?: return PrintOutcome.FAILED
        return connection.print(r.toBytes(1, s.endSpacing, s.hasCutter))
    }

    /** El texto exacto de un recibo de muestra con los ajustes actuales (la vista previa). */
    suspend fun previewText(): String {
        val s = settings.value
        return render { name, zone, money -> PrinterSamples.preview(name, s, language(), money, zone, System.currentTimeMillis()) }?.previewText().orEmpty()
    }

    suspend fun retry(): PrintOutcome = connection.retryPending()

    fun issue(): ConnIssue = connection.issue.value
    fun state(): ConnState = connection.state.value
}

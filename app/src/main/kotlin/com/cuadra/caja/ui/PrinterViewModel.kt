package com.cuadra.caja.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cuadra.caja.AppContainer
import com.cuadra.caja.data.printer.PrinterDevice
import com.cuadra.caja.domain.printing.ConnIssue
import com.cuadra.caja.domain.printing.ConnState
import com.cuadra.caja.domain.printing.PrintCharset
import com.cuadra.caja.domain.printing.PrintNotice
import com.cuadra.caja.domain.printing.PrinterLink
import com.cuadra.caja.domain.printing.PrinterSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Lo que muestra Más › Avanzado › Impresora. */
data class PrinterUi(
    val settings: PrinterSettings = PrinterSettings(),
    val state: ConnState = ConnState.DISCONNECTED,
    val issue: ConnIssue = ConnIssue.NONE,
    val hasBluetooth: Boolean = true,
    val hasUsb: Boolean = true,
    val btPermission: Boolean = true,
    val btOn: Boolean = true,
    val bonded: List<PrinterDevice> = emptyList(),
    val usb: List<PrinterDevice> = emptyList(),
    /** El texto de un recibo de muestra con los ajustes actuales (monoespaciado, tal como sale en el papel). */
    val preview: String = "",
    val testing: Boolean = false,
    /** Resultado de «Imprimir prueba». */
    val result: PrintNotice? = null,
)

interface PrinterActions {
    fun setEnabled(on: Boolean) {}
    fun setLink(link: PrinterLink) {}
    fun pick(device: PrinterDevice) {}
    fun setWidth(mm: Int) {}
    fun setAuto(on: Boolean) {}
    fun setCopies(n: Int) {}
    fun setCharset(charset: PrintCharset) {}
    fun setEndSpacing(spacing: com.cuadra.caja.domain.printing.EndSpacing) {}
    fun setHasCutter(on: Boolean) {}
    fun setAddress(text: String) {}
    fun setPhone(text: String) {}
    fun setTaxId(text: String) {}
    fun setFooter(text: String) {}
    fun reconnect() {}
    fun printTest() {}
    fun refresh() {}
}

class PrinterViewModel(private val c: AppContainer) : ViewModel(), PrinterActions {
    private val hub = c.printer
    private val _ui = MutableStateFlow(PrinterUi())
    val ui: StateFlow<PrinterUi> = _ui.asStateFlow()
    private val tick = MutableStateFlow(0)

    init {
        viewModelScope.launch {
            combine(hub.settings, hub.connection.state, hub.connection.issue, hub.devicesChanged, tick) { s, st, issue, _, _ -> Triple(s, st, issue) }.collect { (s, st, issue) ->
                // Nada de permisos ni búsquedas con la opción apagada: solo se leen las listas cuando está activada.
                val lists = if (s.enabled) withContext(Dispatchers.Default) {
                    Pair(if (s.link == PrinterLink.BLUETOOTH) hub.bondedDevices() else emptyList(), if (s.link == PrinterLink.USB) hub.usbDevices() else emptyList())
                } else Pair(emptyList(), emptyList())
                val preview = runCatching { hub.previewText() }.getOrDefault("")
                _ui.update {
                    it.copy(
                        settings = s, state = st, issue = issue, hasBluetooth = hub.hasBluetooth(), hasUsb = hub.hasUsbHost(),
                        btPermission = !s.enabled || hub.bluetoothPermission(), btOn = hub.bluetoothOn(), bonded = lists.first, usb = lists.second, preview = preview,
                    )
                }
            }
        }
    }

    private fun change(f: (PrinterSettings) -> PrinterSettings) = hub.prefs.update(f)

    override fun setEnabled(on: Boolean) = change { it.copy(enabled = on) }
    override fun setLink(link: PrinterLink) = change { if (it.link == link) it else it.copy(link = link, deviceKey = null, deviceName = null) }
    override fun pick(device: PrinterDevice) = change { it.copy(deviceKey = device.key, deviceName = device.name) }
    override fun setWidth(mm: Int) = change { it.copy(widthMm = if (mm >= 80) 80 else 58) }
    override fun setAuto(on: Boolean) = change { it.copy(autoPrint = on) }
    override fun setCopies(n: Int) = change { it.copy(copies = n.coerceIn(1, 2)) }
    override fun setCharset(charset: PrintCharset) = change { it.copy(charset = charset) }
    override fun setEndSpacing(spacing: com.cuadra.caja.domain.printing.EndSpacing) = change { it.copy(endSpacing = spacing) }
    override fun setHasCutter(on: Boolean) = change { it.copy(hasCutter = on) }
    override fun setAddress(text: String) = change { it.copy(address = text.take(PrinterSettings.MAX_LINE)) }
    override fun setPhone(text: String) = change { it.copy(phone = text.take(PrinterSettings.MAX_LINE)) }
    override fun setTaxId(text: String) = change { it.copy(taxId = text.take(PrinterSettings.MAX_LINE)) }
    override fun setFooter(text: String) = change { it.copy(footer = text.take(PrinterSettings.MAX_FOOTER)) }
    override fun reconnect() = hub.reconnect()
    override fun refresh() { tick.value++ }

    override fun printTest() {
        if (_ui.value.testing) return
        _ui.update { it.copy(testing = true, result = null) }
        viewModelScope.launch {
            val outcome = hub.printTest()
            _ui.update { it.copy(testing = false, result = PrintNotice.of(outcome)) }
        }
    }
}

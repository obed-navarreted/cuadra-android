package com.cuadra.caja.data.scanner

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import com.cuadra.caja.domain.ScanBurstDetector
import com.cuadra.caja.domain.ScanTiming

/**
 * Puente entre las teclas del Activity y `ScanBurstDetector`. Solo actúa si el lector está encendido, alguna pantalla pidió lecturas y NINGÚN campo
 * de texto tiene el foco (si lo tiene, el código se teclea en el campo, sin cambios), salvo un campo de BÚSQUEDA (`ScanHub.scanFieldFocused`): ahí la ráfaga es una lectura. Lo que no es una ráfaga rápida pasa intacto.
 */
class WedgeKeys(private val hub: ScanHub) {
    private val detector = ScanBurstDetector()
    private val handler = Handler(Looper.getMainLooper())
    private val consumedDown = HashSet<Int>()

    private val flush = Runnable { tick() }

    /** La ráfaga en curso empezó con un campo de búsqueda enfocado: su primera tecla ya se escribió en el campo y hay que quitarla al entregar la lectura. */
    private var burstInField = false

    private fun tick() {
        detector.flushIfQuiet(SystemClock.uptimeMillis())?.let { hub.submit(it, ScanSource.WEDGE, leakedIntoField = burstInField) }
        if (detector.pending) handler.postDelayed(flush, ScanTiming.WEDGE_QUIET_MS / 2)
    }

    fun reset() { handler.removeCallbacks(flush); detector.reset(); consumedDown.clear(); burstInField = false }

    /** `true` si la tecla es parte de una lectura y ya se atendió. */
    fun dispatch(event: KeyEvent, acceptingText: Boolean): Boolean {
        if (event.action == KeyEvent.ACTION_UP) return consumedDown.remove(event.keyCode)
        if (event.action != KeyEvent.ACTION_DOWN) return false
        // Un campo de texto con el foco se queda con las teclas, salvo el buscador de la caja (y el de productos): ahí una ráfaga rápida es una lectura.
        val inSearchField = acceptingText && hub.scanFieldFocused
        if ((acceptingText && !inSearchField) || !hub.hasTarget || !hub.settings.value.useReader) { if (detector.pending) reset(); return false }
        if (event.repeatCount > 0 && event.keyCode in consumedDown) return true
        if (KeyEvent.isModifierKey(event.keyCode)) return false
        val now = event.eventTime
        val ch = event.unicodeChar
        val terminator = event.keyCode == KeyEvent.KEYCODE_ENTER || event.keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER || event.keyCode == KeyEvent.KEYCODE_TAB ||
            ch == 3 || ch == '\r'.code || ch == '\n'.code || ch == '\t'.code
        if (!detector.pending) burstInField = inSearchField
        val outcome = when {
            terminator -> detector.onTerminator(now)
            ch <= 0 -> return false // sin carácter (teclas de función, acentos sueltos)
            else -> detector.onChar(ch.toChar(), now)
        }
        handler.removeCallbacks(flush)
        if (detector.pending) handler.postDelayed(flush, ScanTiming.WEDGE_QUIET_MS + 10)
        outcome.scan?.let { hub.submit(it, ScanSource.WEDGE, leakedIntoField = burstInField) }
        if (outcome.consume) consumedDown += event.keyCode
        return outcome.consume
    }

}

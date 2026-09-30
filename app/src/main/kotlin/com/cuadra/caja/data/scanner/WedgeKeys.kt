package com.cuadra.caja.data.scanner

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import com.cuadra.caja.domain.ScanBurstDetector
import com.cuadra.caja.domain.ScanTiming

/**
 * Puente entre las teclas del Activity y `ScanBurstDetector`. Solo actúa si el lector está encendido, alguna pantalla pidió lecturas y NINGÚN campo
 * de texto tiene el foco (si lo tiene, el código se teclea en el campo, sin cambios). Lo que no es una ráfaga rápida pasa intacto.
 */
class WedgeKeys(private val hub: ScanHub) {
    private val detector = ScanBurstDetector()
    private val handler = Handler(Looper.getMainLooper())
    private val consumedDown = HashSet<Int>()

    private val flush = Runnable { tick() }

    private fun tick() {
        detector.flushIfQuiet(SystemClock.uptimeMillis())?.let { hub.submit(it, ScanSource.WEDGE) }
        if (detector.pending) handler.postDelayed(flush, ScanTiming.WEDGE_QUIET_MS / 2)
    }

    fun reset() { handler.removeCallbacks(flush); detector.reset(); consumedDown.clear() }

    /** `true` si la tecla es parte de una lectura y ya se atendió. */
    fun dispatch(event: KeyEvent, acceptingText: Boolean): Boolean {
        if (event.action == KeyEvent.ACTION_UP) return consumedDown.remove(event.keyCode)
        if (event.action != KeyEvent.ACTION_DOWN) return false
        if (acceptingText || !hub.hasTarget || !hub.settings.value.useReader) { if (detector.pending) reset(); return false }
        if (event.repeatCount > 0 && event.keyCode in consumedDown) return true
        if (KeyEvent.isModifierKey(event.keyCode)) return false
        val now = event.eventTime
        val ch = event.unicodeChar
        val terminator = event.keyCode == KeyEvent.KEYCODE_ENTER || event.keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER || event.keyCode == KeyEvent.KEYCODE_TAB ||
            ch == 3 || ch == '\r'.code || ch == '\n'.code || ch == '\t'.code
        val outcome = when {
            terminator -> detector.onTerminator(now)
            ch <= 0 -> return false // sin carácter (teclas de función, acentos sueltos)
            else -> detector.onChar(ch.toChar(), now)
        }
        handler.removeCallbacks(flush)
        if (detector.pending) handler.postDelayed(flush, ScanTiming.WEDGE_QUIET_MS + 10)
        outcome.scan?.let { hub.submit(it, ScanSource.WEDGE) }
        if (outcome.consume) consumedDown += event.keyCode
        return outcome.consume
    }

}

package com.cuadra.caja.domain

/**
 * Detecta lecturas de un lector físico que «teclea» el código (modo teclado / «keyboard wedge»: Zebra en modo teclado, Bluetooth, USB).
 * Un lector escribe todo el código en unos pocos milisegundos y termina con Enter; una persona teclea con pausas de más de 100 ms.
 *
 * Máquina de estados pura (sin Android, el tiempo lo da quien llama):
 *  - Una ráfaga = al menos `minChars` caracteres con pausas menores a `maxGapMs` entre uno y otro.
 *  - Termina con un terminador (Enter, CR, LF, Tab, ETX) o, si el lector no manda ninguno, cuando pasa `quietMs` sin teclas (`flushIfQuiet`).
 *  - Lo que no es una lectura (teclear lento, ráfaga corta) nunca se consume: `consume = false` y las teclas siguen su camino.
 *  - STX (inicio de texto) y demás caracteres de control que algunos lectores agregan de prefijo/sufijo se ignoran.
 */
class ScanBurstDetector(
    private val minChars: Int = ScanTiming.WEDGE_MIN_CHARS,
    private val maxGapMs: Long = ScanTiming.WEDGE_MAX_GAP_MS,
    private val quietMs: Long = ScanTiming.WEDGE_QUIET_MS,
    private val maxChars: Int = ScanTiming.WEDGE_MAX_CHARS,
) {
    /** `consume`: la tecla es parte de una lectura y no debe llegar a nadie más. `scan`: código completo, si con esta tecla (o la anterior) terminó una lectura. */
    data class Outcome(val consume: Boolean = false, val scan: String? = null)

    private val buffer = StringBuilder()
    private var lastAt = 0L

    /** Hay caracteres esperando el final de una ráfaga (quien llama debe programar `flushIfQuiet`). */
    val pending: Boolean get() = buffer.isNotEmpty()

    /** Un carácter imprimible. Los de control (STX y otros) se ignoran sin tocar el estado. */
    fun onChar(ch: Char, nowMs: Long): Outcome {
        if (ch.isISOControl()) return Outcome()
        var previous: String? = null
        if (buffer.isNotEmpty() && nowMs - lastAt >= maxGapMs) {
            // Pausa larga: la ráfaga anterior terminó. Sin terminador solo cuenta como lectura si ya pasó el tiempo de silencio.
            if (buffer.length >= minChars && nowMs - lastAt >= quietMs) previous = buffer.toString()
            buffer.clear()
        }
        if (buffer.length >= maxChars) buffer.clear()
        buffer.append(ch)
        lastAt = nowMs
        // La primera tecla pasa (podría ser una persona); desde la segunda, tan rápida, ya no es una persona.
        return Outcome(consume = buffer.length >= 2, scan = previous)
    }

    /** Enter, CR, LF, Tab o ETX. Cierra la ráfaga si tiene el largo suficiente; si no, la tecla pasa tal cual. */
    fun onTerminator(nowMs: Long): Outcome {
        val fast = buffer.length >= minChars && nowMs - lastAt < quietMs
        val late = buffer.length >= minChars && !fast
        val code = if (buffer.length >= minChars) buffer.toString() else null
        buffer.clear()
        return when {
            fast -> Outcome(consume = true, scan = code)
            late -> Outcome(consume = false, scan = code) // el silencio ya la había cerrado
            else -> Outcome()
        }
    }

    /** Para el temporizador: si pasó `quietMs` desde la última tecla, entrega la ráfaga (si era larga) y vacía. */
    fun flushIfQuiet(nowMs: Long): String? {
        if (buffer.isEmpty() || nowMs - lastAt < quietMs) return null
        val code = if (buffer.length >= minChars) buffer.toString() else null
        buffer.clear()
        return code
    }

    fun reset() = buffer.clear()
}

/** Tiempos y umbrales del lector (físico y de cámara) en un solo lugar. */
object ScanTiming {
    const val WEDGE_MIN_CHARS = 4
    const val WEDGE_MAX_GAP_MS = 60L
    const val WEDGE_QUIET_MS = 120L
    const val WEDGE_MAX_CHARS = 64

    /** El mismo código no se agrega dos veces dentro de este tiempo (cámara, teclado y DataWedge). */
    const val DEBOUNCE_MS = 1500L

    /** Cuadros seguidos con el mismo valor para confirmar una lectura de cámara. */
    const val CONFIRM_FRAMES = 2
    const val BAD_CHECKSUM_FRAMES = 3
    const val OTHER_CANDIDATE_MS = 300L
    const val STREAK_GAP_MS = 700L

    const val HINT_AFTER_MS = 4000L
}

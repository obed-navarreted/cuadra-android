package com.cuadra.caja.domain

/**
 * Confirma una lectura de la cámara solo cuando es fiable. La cámara decodifica cuadro por cuadro y a veces se equivoca (código curvo, borroso,
 * dos códigos a la vez): un valor se acepta cuando aparece en cuadros seguidos.
 *
 * Regla:
 *  - EAN-13 / UPC-A / EAN-8 con dígito de control válido: 1 cuadro, si en los últimos 300 ms no se vio OTRO valor; si se vio, 2 cuadros.
 *  - Con dígito de control MALO: 3 cuadros seguidos (una lectura mala casi nunca se repite tres veces igual).
 *  - Cualquier otro código (Code 128, Code 39, QR, largos raros): 2 cuadros.
 *  - «Parpadeo» (valores distintos alternando) reinicia la cuenta cada vez: nunca confirma.
 *  - Los cuadros de una misma serie pueden tener huecos de hasta 700 ms (cuadros donde no se leyó nada).
 */
class ScanConfirmer(
    private val confirmFrames: Int = ScanTiming.CONFIRM_FRAMES,
    private val badChecksumFrames: Int = ScanTiming.BAD_CHECKSUM_FRAMES,
    private val otherWindowMs: Long = ScanTiming.OTHER_CANDIDATE_MS,
    private val streakGapMs: Long = ScanTiming.STREAK_GAP_MS,
) {
    private var streakValue: String? = null
    private var streak = 0
    private var streakAt = 0L
    private val recent = ArrayDeque<Pair<String, Long>>()

    /** Cuántos cuadros seguidos se piden para este valor, dado si hubo otro candidato reciente. */
    fun required(code: String, otherRecently: Boolean): Int = when (ScanCode.checksumValid(code)) {
        true -> if (otherRecently) confirmFrames else 1
        false -> badChecksumFrames
        null -> confirmFrames
    }

    /** Un valor decodificado en un cuadro. Devuelve el código si con este cuadro queda confirmado (y la serie empieza de nuevo). */
    fun offer(raw: String, nowMs: Long): String? {
        val code = ScanCode.normalize(raw)
        if (code.isEmpty()) return null
        while (recent.isNotEmpty() && nowMs - recent.first().second > otherWindowMs) recent.removeFirst()
        val other = recent.any { it.first != code }
        recent.addLast(code to nowMs)
        streak = if (code == streakValue && nowMs - streakAt <= streakGapMs) streak + 1 else 1
        streakValue = code
        streakAt = nowMs
        if (streak >= required(code, other)) {
            streak = 0
            streakValue = null
            return code
        }
        return null
    }

    fun reset() { streak = 0; streakValue = null; recent.clear() }
}

/** Brillo medio de un cuadro (plano Y de la cámara) y la decisión «poca luz», con histéresis para que el aviso no parpadee. */
object Luma {
    /**
     * Promedio 0..255 de una muestra del plano Y: se leen como mucho ~`samples` x `samples` puntos repartidos por la imagen (rápido en equipos viejos).
     * `get(index)` devuelve el byte (sin signo) en la posición de la memoria.
     */
    fun average(width: Int, height: Int, rowStride: Int, pixelStride: Int, samples: Int = 24, get: (Int) -> Int): Double {
        if (width <= 0 || height <= 0) return 0.0
        val stepX = maxOf(1, width / samples)
        val stepY = maxOf(1, height / samples)
        var sum = 0L
        var n = 0
        var y = 0
        while (y < height) {
            var x = 0
            while (x < width) { sum += get(y * rowStride + x * pixelStride) and 0xFF; n++; x += stepX }
            y += stepY
        }
        return if (n == 0) 0.0 else sum.toDouble() / n
    }
}

/** Promedio móvil del brillo: se enciende con poca luz (< `lowBelow`) y se apaga cuando hay luz de sobra (> `okAbove`). */
class LowLightMonitor(private val lowBelow: Double = 45.0, private val okAbove: Double = 65.0, private val alpha: Double = 0.25) {
    private var ema: Double? = null
    var low = false
        private set

    fun update(luma: Double): Boolean {
        val e = ema?.let { it + alpha * (luma - it) } ?: luma
        ema = e
        if (!low && e < lowBelow) low = true else if (low && e > okAbove) low = false
        return low
    }
}

/** Qué pista mostrar debajo de la cámara. */
enum class ScanHint { NONE, LOW_LIGHT, FOCUS }

object ScanHints {
    /**
     * Poca luz y linterna apagada: sugerir la linterna (lo primero). Si no, tras `HINT_AFTER_MS` sin ninguna lectura desde que se abrió el lector
     * (o desde la última): «Acerca o aleja hasta enfocar».
     */
    fun pick(nowMs: Long, sinceMs: Long, lowLight: Boolean, torchOn: Boolean, afterMs: Long = ScanTiming.HINT_AFTER_MS): ScanHint = when {
        lowLight && !torchOn -> ScanHint.LOW_LIGHT
        nowMs - sinceMs >= afterMs -> ScanHint.FOCUS
        else -> ScanHint.NONE
    }
}

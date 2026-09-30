package com.cuadra.caja.domain

import java.nio.ByteBuffer

/**
 * Franja de lectura de la cámara: la zona central que se dibuja (recuadro) y la única que se analiza. Leer solo ahí evita códigos vecinos y reduce el
 * trabajo del equipo. Las fracciones son de la imagen VISIBLE (ya derecha, como se ve en la pantalla).
 */
object ScanRoi {
    const val X0 = 0.06f
    const val X1 = 0.94f
    const val Y0 = 0.30f
    const val Y1 = 0.70f

    /** Rectángulo (izquierda, arriba, ancho, alto) en píxeles del cuadro TAL COMO LLEGA de la cámara (sin girar), dentro del área visible `cropW x cropH`. */
    data class Rect(val left: Int, val top: Int, val width: Int, val height: Int)

    /**
     * Pasa la franja (fracciones de la imagen derecha) a coordenadas del cuadro sin girar. `rotation` = grados que hay que girar el cuadro para verlo derecho
     * (0, 90, 180, 270). `cropLeft/cropTop` = origen del área visible dentro del cuadro. El resultado tiene ancho y alto pares y cabe en el área visible.
     */
    fun toFrame(cropLeft: Int, cropTop: Int, cropW: Int, cropH: Int, rotation: Int, x0: Float = X0, y0: Float = Y0, x1: Float = X1, y1: Float = Y1): Rect {
        val upW = if (rotation % 180 == 0) cropW else cropH
        val upH = if (rotation % 180 == 0) cropH else cropW
        val ux0 = x0 * upW; val ux1 = x1 * upW; val uy0 = y0 * upH; val uy1 = y1 * upH
        // Esquinas de la franja derecha → cuadro sin girar (relativas al área visible).
        fun map(ux: Float, uy: Float): Pair<Float, Float> = when (((rotation % 360) + 360) % 360) {
            90 -> uy to (cropH - ux)
            180 -> (cropW - ux) to (cropH - uy)
            270 -> (cropW - uy) to ux
            else -> ux to uy
        }
        val a = map(ux0, uy0); val b = map(ux1, uy1)
        val l = minOf(a.first, b.first).toInt().coerceIn(0, cropW)
        val t = minOf(a.second, b.second).toInt().coerceIn(0, cropH)
        val r = maxOf(a.first, b.first).toInt().coerceIn(0, cropW)
        val bt = maxOf(a.second, b.second).toInt().coerceIn(0, cropH)
        val w = (r - l) and 1.inv()
        val h = (bt - t) and 1.inv()
        return Rect(cropLeft + l, cropTop + t, w, h)
    }

    /** Cuánto reducir un recorte demasiado grande (equipos viejos): 1 = tal cual, 2 = la mitad… hasta que el lado largo baje de `maxSide`. */
    fun decimation(width: Int, height: Int, maxSide: Int = 1400): Int {
        var d = 1
        while (maxOf(width, height) / d > maxSide) d++
        return d
    }
}

/** Recorte y reducción del plano de brillo (Y) de un cuadro de la cámara a un NV21 (los planos de color van en gris: para códigos solo importa el brillo). */
object FrameCrop {
    /** Tamaño del NV21 de `w x h` (Y + color a la mitad). */
    fun nv21Size(w: Int, h: Int) = w * h * 3 / 2

    /**
     * Copia el recorte `rect` del plano Y (`rowStride`/`pixelStride` del cuadro), tomando 1 de cada `decimation` puntos, a `out` como NV21.
     * Devuelve (ancho, alto) del resultado, pares. `out` debe tener al menos `nv21Size` bytes para el resultado.
     */
    fun cropToNv21(y: ByteBuffer, rowStride: Int, pixelStride: Int, rect: ScanRoi.Rect, decimation: Int, out: ByteArray): Pair<Int, Int> {
        val w = (rect.width / decimation) and 1.inv()
        val h = (rect.height / decimation) and 1.inv()
        for (row in 0 until h) {
            val base = (rect.top + row * decimation) * rowStride + rect.left * pixelStride
            val dst = row * w
            if (decimation == 1 && pixelStride == 1) {
                val dup = y.duplicate()
                dup.position(base)
                dup.get(out, dst, w)
            } else {
                for (col in 0 until w) out[dst + col] = y.get(base + col * decimation * pixelStride)
            }
        }
        java.util.Arrays.fill(out, w * h, nv21Size(w, h), 128.toByte())
        return w to h
    }
}

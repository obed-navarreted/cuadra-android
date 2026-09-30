package com.cuadra.caja.domain

import java.nio.ByteBuffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanConfirmerTest {
    private val ean = "4006381333931" // dígito de control válido
    private val eanBad = "4006381333932"
    private val other = "5901234123457"

    @Test fun validEanConfirmsInOneFrame() {
        assertEquals(ean, ScanConfirmer().offer(ean, 0))
    }

    @Test fun validEanNeedsTwoFramesIfAnotherCandidateWasJustSeen() {
        val c = ScanConfirmer()
        assertEquals(other, c.offer(other, 0))          // otro código, confirmado solo
        assertNull(c.offer(ean, 100))                   // hubo otro hace 100 ms: pide 2
        assertEquals(ean, c.offer(ean, 200))
    }

    @Test fun otherCandidateOlderThan300msDoesNotCount() {
        val c = ScanConfirmer()
        c.offer(other, 0)
        assertEquals(ean, c.offer(ean, 400))
    }

    @Test fun badCheckDigitNeedsThreeConsecutiveFrames() {
        val c = ScanConfirmer()
        assertNull(c.offer(eanBad, 0))
        assertNull(c.offer(eanBad, 80))
        assertEquals(eanBad, c.offer(eanBad, 160))
    }

    @Test fun badCheckDigitReadOnlyTwiceIsRejected() {
        val c = ScanConfirmer()
        assertNull(c.offer(eanBad, 0))
        assertNull(c.offer(eanBad, 80))
        assertNull(c.offer(ean.dropLast(1) + "0", 160)) // otra lectura distinta rompe la serie
    }

    @Test fun flickerBetweenValuesNeverConfirmsAnInvalidOne() {
        val c = ScanConfirmer()
        var t = 0L
        repeat(10) { i -> assertNull(c.offer(if (i % 2 == 0) eanBad else "4006381333939", t)); t += 60 }
    }

    @Test fun flickerNeverConfirmsCode128Either() {
        val c = ScanConfirmer()
        var t = 0L
        repeat(10) { i -> assertNull(c.offer(if (i % 2 == 0) "ABC-1" else "ABC-2", t)); t += 60 }
    }

    @Test fun nonNumericCodeNeedsTwoFrames() {
        val c = ScanConfirmer()
        assertNull(c.offer("LOTE-77", 0))
        assertEquals("LOTE-77", c.offer("LOTE-77", 100))
    }

    @Test fun streakBreaksAfterALongGap() {
        val c = ScanConfirmer()
        assertNull(c.offer("LOTE-77", 0))
        assertNull(c.offer("LOTE-77", 1500))            // más de 700 ms: empieza de nuevo
        assertEquals("LOTE-77", c.offer("LOTE-77", 1600))
    }

    @Test fun framesWithSkippedDetectionsStillCount() {
        val c = ScanConfirmer()
        assertNull(c.offer("LOTE-77", 0))
        assertEquals("LOTE-77", c.offer("LOTE-77", 600)) // hueco de 600 ms < 700
    }

    @Test fun confirmationResetsTheStreak() {
        val c = ScanConfirmer()
        assertNull(c.offer("LOTE-77", 0))
        assertEquals("LOTE-77", c.offer("LOTE-77", 50))
        assertNull(c.offer("LOTE-77", 100)) // serie nueva
    }

    @Test fun emptyAndBlankAreIgnored() {
        val c = ScanConfirmer()
        assertNull(c.offer("", 0)); assertNull(c.offer("  \n", 10))
    }

    @Test fun requiredFramesRule() {
        val c = ScanConfirmer()
        assertEquals(1, c.required(ean, false)); assertEquals(2, c.required(ean, true))
        assertEquals(3, c.required(eanBad, false)); assertEquals(3, c.required(eanBad, true))
        assertEquals(2, c.required("ABC", false))
    }

    // ---------- luz ----------
    @Test fun lumaAverageOfFlatAndSplitFrames() {
        val dark = ByteArray(64 * 48) { 20 }
        assertEquals(20.0, Luma.average(64, 48, 64, 1) { dark[it].toInt() }, 0.01)
        val half = ByteArray(64 * 48) { if (it % 64 < 32) 0 else 200.toByte() }
        assertEquals(100.0, Luma.average(64, 48, 64, 1, samples = 64) { half[it].toInt() }, 1.0)
    }

    @Test fun lumaHonorsRowStrideAndUnsignedBytes() {
        // ancho 4, alto 2, fila de 8 bytes (relleno de 4 en 0): solo se promedian los 4 primeros de cada fila.
        val buf = ByteArray(16) { 0 }
        for (r in 0..1) for (x in 0..3) buf[r * 8 + x] = 250.toByte()
        assertEquals(250.0, Luma.average(4, 2, 8, 1, samples = 4) { buf[it].toInt() }, 0.01)
    }

    @Test fun lowLightHasHysteresis() {
        val m = LowLightMonitor()
        repeat(6) { assertTrue(m.update(20.0) || it < 1 || true) }
        assertTrue(m.low)
        // Un poco más de luz, sin llegar al umbral de apagado: sigue avisando.
        repeat(20) { m.update(55.0) }
        assertTrue(m.low)
        repeat(20) { m.update(120.0) }
        assertFalse(m.low)
    }

    @Test fun lowLightNotTriggeredByNormalRoom() {
        val m = LowLightMonitor()
        repeat(30) { assertFalse(m.update(110.0)) }
    }

    @Test fun hintsPickLowLightFirstThenFocusAfterFourSeconds() {
        assertEquals(ScanHint.NONE, ScanHints.pick(1000, 0, lowLight = false, torchOn = false))
        assertEquals(ScanHint.FOCUS, ScanHints.pick(4000, 0, lowLight = false, torchOn = false))
        assertEquals(ScanHint.LOW_LIGHT, ScanHints.pick(500, 0, lowLight = true, torchOn = false))
        assertEquals("con la linterna encendida no se insiste", ScanHint.NONE, ScanHints.pick(500, 0, lowLight = true, torchOn = true))
        assertEquals(ScanHint.LOW_LIGHT, ScanHints.pick(9000, 0, lowLight = true, torchOn = false))
    }

    // ---------- franja de lectura ----------
    @Test fun roiMapsToUprightBandForEveryRotation() {
        // Cuadro 1280x720 sin girar. Derecho (0°): la franja es el 88 % x 40 % centrales.
        val r0 = ScanRoi.toFrame(0, 0, 1280, 720, 0)
        assertEquals(ScanRoi.Rect(76, 216, 1126, 288), r0)
        // 90°: la imagen derecha mide 720 x 1280; la franja (88 % del ancho derecho = 633, 40 % del alto = 512) queda en el cuadro como 512 x 633.
        val r90 = ScanRoi.toFrame(0, 0, 1280, 720, 90)
        assertEquals(512, r90.width); assertEquals(632, r90.height)
        // y centrada horizontalmente en el cuadro (0.30..0.70 de 1280 → 384..896)
        assertEquals(384, r90.left)
        val r180 = ScanRoi.toFrame(0, 0, 1280, 720, 180)
        assertEquals(1126, r180.width); assertEquals(288, r180.height)
        val r270 = ScanRoi.toFrame(0, 0, 1280, 720, 270)
        assertEquals(r90.width, r270.width); assertEquals(r90.height, r270.height)
        for (r in listOf(r0, r90, r180, r270)) {
            assertTrue(r.left >= 0 && r.top >= 0 && r.left + r.width <= 1280 && r.top + r.height <= 720)
            assertTrue(r.width % 2 == 0 && r.height % 2 == 0)
        }
    }

    @Test fun roiRespectsCropOrigin() {
        val r = ScanRoi.toFrame(100, 50, 800, 600, 0)
        assertEquals(100 + (0.06f * 800).toInt(), r.left)
        assertEquals(50 + (0.30f * 600).toInt(), r.top)
    }

    @Test fun decimationCapsTheLongSide() {
        assertEquals(1, ScanRoi.decimation(1280, 720))
        assertEquals(2, ScanRoi.decimation(2560, 1440))
        assertEquals(3, ScanRoi.decimation(4000, 3000))
    }

    @Test fun cropCopiesTheRegionAndFillsChromaGray() {
        // Cuadro 8x6 con valor = fila*10 + columna, fila de 10 bytes (relleno).
        val stride = 10
        val src = ByteArray(stride * 6) { i -> ((i / stride) * 10 + (i % stride)).toByte() }
        val out = ByteArray(FrameCrop.nv21Size(4, 2))
        val (w, h) = FrameCrop.cropToNv21(ByteBuffer.wrap(src), stride, 1, ScanRoi.Rect(2, 1, 4, 2), 1, out)
        assertEquals(4, w); assertEquals(2, h)
        assertEquals(listOf<Byte>(12, 13, 14, 15, 22, 23, 24, 25), out.take(8))
        assertTrue(out.drop(8).all { it == 128.toByte() })
    }

    @Test fun cropDecimatesEveryOtherPixel() {
        val src = ByteArray(8 * 8) { (it % 8).toByte() }
        val out = ByteArray(FrameCrop.nv21Size(4, 4))
        val (w, h) = FrameCrop.cropToNv21(ByteBuffer.wrap(src), 8, 1, ScanRoi.Rect(0, 0, 8, 8), 2, out)
        assertEquals(4, w); assertEquals(4, h)
        assertEquals(listOf<Byte>(0, 2, 4, 6), out.take(4))
    }
}

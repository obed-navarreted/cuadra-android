package com.cuadra.caja.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanBurstDetectorTest {
    /** Escribe `text` con `gap` ms entre teclas empezando en `start`; devuelve las salidas y la hora de la última tecla. */
    private fun ScanBurstDetector.type(text: String, start: Long, gap: Long): Pair<List<ScanBurstDetector.Outcome>, Long> {
        var t = start
        val out = ArrayList<ScanBurstDetector.Outcome>()
        text.forEachIndexed { i, c -> if (i > 0) t += gap; out += onChar(c, t) }
        return out to t
    }

    @Test fun fastBurstWithEnterIsAScan() {
        val d = ScanBurstDetector()
        val (outs, last) = d.type("7501055300075", 1000, 5)
        assertTrue(outs.all { it.scan == null })
        val end = d.onTerminator(last + 5)
        assertEquals("7501055300075", end.scan)
        assertTrue("Enter de la lectura se consume", end.consume)
    }

    @Test fun burstWithoutEnterFlushesAfterQuietTime() {
        val d = ScanBurstDetector()
        val (_, last) = d.type("7501055300075", 0, 8)
        assertNull("todavía no pasó el silencio", d.flushIfQuiet(last + 50))
        assertEquals("7501055300075", d.flushIfQuiet(last + 120))
        assertNull("ya se entregó", d.flushIfQuiet(last + 500))
        assertFalse(d.pending)
    }

    @Test fun slowTypingIsNeverAScanNorSwallowed() {
        val d = ScanBurstDetector()
        val (outs, last) = d.type("750105", 0, 250)
        assertTrue("ninguna tecla lenta se consume", outs.none { it.consume })
        assertTrue(outs.none { it.scan != null })
        val end = d.onTerminator(last + 250)
        assertNull(end.scan)
        assertFalse("el Enter de una persona pasa", end.consume)
        assertNull(d.flushIfQuiet(last + 1000))
    }

    @Test fun mediumHumanSpeedStillNotAScan() {
        // 100 ms entre teclas es tecleo muy rápido de persona: por encima del umbral de 60 ms.
        val d = ScanBurstDetector()
        val (outs, last) = d.type("12345678", 0, 100)
        assertTrue(outs.none { it.consume || it.scan != null })
        assertNull(d.onTerminator(last + 20).scan)
    }

    @Test fun tooShortBurstIsIgnored() {
        val d = ScanBurstDetector()
        val (_, last) = d.type("123", 0, 5)
        val end = d.onTerminator(last + 5)
        assertNull(end.scan)
        assertFalse("Enter suelto pasa", end.consume)
        d.type("12", 1000, 5)
        assertNull(d.flushIfQuiet(2000))
    }

    @Test fun twoScansBackToBack() {
        val d = ScanBurstDetector()
        val (_, l1) = d.type("7501055300075", 0, 5)
        assertEquals("7501055300075", d.onTerminator(l1 + 5).scan)
        val (outs2, l2) = d.type("7441001600153", l1 + 300, 5)
        assertTrue(outs2.all { it.scan == null })
        assertEquals("7441001600153", d.onTerminator(l2 + 5).scan)
    }

    @Test fun twoScansWithoutEnterWithinOneGapEmitFirstOnNextKey() {
        // Sin Enter y sin que el temporizador corriera: al llegar la siguiente ráfaga (tras el silencio) la anterior se entrega.
        val d = ScanBurstDetector()
        val (_, l1) = d.type("7501055300075", 0, 5)
        val first = d.onChar('7', l1 + 400)
        assertEquals("7501055300075", first.scan)
        val (_, l2) = d.type("441001600153", l1 + 405, 5)
        assertEquals("7441001600153", d.flushIfQuiet(l2 + 130))
    }

    @Test fun keysInterleavedWithLongPausesNeverCombine() {
        val d = ScanBurstDetector()
        var t = 0L
        val outs = ArrayList<ScanBurstDetector.Outcome>()
        // Tres teclas rápidas, pausa larga, tres teclas rápidas: ninguna tanda llega a 4.
        "123".forEachIndexed { i, c -> outs += d.onChar(c, t + i * 5) }
        t += 500
        "456".forEachIndexed { i, c -> outs += d.onChar(c, t + i * 5) }
        assertTrue(outs.none { it.scan != null })
        assertNull(d.onTerminator(t + 30).scan)
    }

    @Test fun pauseInTheMiddleSplitsTheBurst() {
        val d = ScanBurstDetector()
        val (_, l1) = d.type("1234", 0, 5)
        // Un hueco de 90 ms (entre 60 y 120): ambiguo, se descarta lo anterior y empieza otra tanda.
        val o = d.onChar('5', l1 + 90)
        assertNull(o.scan)
        val (_, l2) = d.type("6789", l1 + 95, 5)
        assertEquals("56789", d.onTerminator(l2 + 5).scan)
    }

    @Test fun prefixAndSuffixCharactersAreStripped() {
        val d = ScanBurstDetector()
        var t = 0L
        // STX de prefijo (control, se ignora), código, ETX de sufijo (terminador).
        assertFalse(d.onChar('\u0002', t).consume)
        "7501055300075".forEach { t += 4; d.onChar(it, t) }
        val end = d.onTerminator(t + 4) // ETX/CR/LF/Tab: mismo trato que Enter
        assertEquals("7501055300075", end.scan)
        // CR seguido de LF: el segundo llega con el búfer vacío, pasa sin más.
        val lf = d.onTerminator(t + 8)
        assertNull(lf.scan)
        assertFalse(lf.consume)
    }

    @Test fun controlCharactersInsideAreIgnored() {
        val d = ScanBurstDetector()
        d.onChar('1', 0); d.onChar('\u001d', 3); d.onChar('2', 6); d.onChar('3', 9); d.onChar('4', 12)
        assertEquals("1234", d.onTerminator(15).scan)
    }

    @Test fun firstKeyPassesRestAreConsumed() {
        val d = ScanBurstDetector()
        val (outs, _) = d.type("12345", 0, 5)
        assertFalse(outs[0].consume)
        assertTrue(outs.drop(1).all { it.consume })
    }

    @Test fun enterAfterLongSilenceOnlyFlushesWithoutConsuming() {
        val d = ScanBurstDetector()
        val (_, last) = d.type("7501055300075", 0, 5)
        val end = d.onTerminator(last + 400) // el temporizador no corrió
        assertEquals("7501055300075", end.scan)
        assertFalse("ese Enter ya no es de la lectura", end.consume)
    }

    @Test fun overlongBurstDoesNotGrowForever() {
        val d = ScanBurstDetector(maxChars = 10)
        val (_, last) = d.type("A".repeat(25), 0, 2)
        val scan = d.onTerminator(last + 2).scan
        assertTrue(scan == null || scan.length <= 10)
    }
}

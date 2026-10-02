package com.cuadra.caja.domain.printing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** El final del recibo no gasta papel: sin líneas de relleno y con el avance justo al corte (o a la barra de rasgar). */
class ReceiptEndingTest {
    private val r = Scenarios.all.getValue("simple")
    private fun tail(bytes: ByteArray, n: Int) = bytes.takeLast(n).joinToString(" ") { "%02X".format(it) }

    @Test fun withACutterTheJobEndsRightAfterTheLastLineWithFeedAndCut() {
        val b = r.toBytes()
        // Restablece estilos (no gasta papel) y `GS V 66 0`: avanza solo hasta el corte y corta. Nada de `ESC d n` ni líneas vacías.
        assertEquals("1B 61 00 1B 45 00 1D 21 00 1D 56 42 00", tail(b, 13))
        assertFalse(EscPosDump.toText(b, r.charset).contains("[feed"))
    }

    @Test fun moreSpaceAddsTwoLinesBeforeTheCut() {
        assertTrue(EscPosDump.toText(r.toBytes(spacing = EndSpacing.MORE), r.charset).endsWith("[feed 2][cut]"))
    }

    @Test fun withoutACutterItFeedsJustPastTheTearBarAndNeverCuts() {
        val min = EscPosDump.toText(r.toBytes(hasCutter = false), r.charset)
        assertTrue(min.endsWith("[feed ${ReceiptFormatter.TEAR_MIN}]"))
        assertFalse(min.contains("[cut]"))
        assertTrue(EscPosDump.toText(r.toBytes(hasCutter = false, spacing = EndSpacing.MORE), r.charset).endsWith("[feed ${ReceiptFormatter.TEAR_MORE}]"))
    }

    @Test fun noReceiptHasBlankLinesInTheTextOrADoubleBlank() {
        for ((name, rc) in Scenarios.all) {
            assertTrue("$name: línea vacía", rc.lines.none { it.text.isBlank() && it.text.isEmpty() && rc.lines.indexOf(it) > 0 })
            assertFalse("$name termina en blanco", rc.lines.last().text.isBlank())
        }
    }

    @Test fun defaultsAreMinimumWithCutter() {
        val s = PrinterSettings()
        assertEquals(EndSpacing.MINIMUM, s.endSpacing)
        assertTrue(s.hasCutter)
        assertEquals(EndSpacing.MORE, EndSpacing.parse("more"))
        assertEquals(EndSpacing.MINIMUM, EndSpacing.parse(null))
    }
}

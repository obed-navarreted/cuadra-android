package com.cuadra.caja.domain.printing

import java.nio.charset.Charset
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EscPosTest {
    private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

    @Test fun initSelectsFontAAndPc858() {
        assertArrayEquals(bytes(0x1B, 0x40, 0x1B, 0x4D, 0x00, 0x1B, 0x74, 19), EscPos(PrintCharset.PC858).init().toBytes())
        // Sin acentos: no se cambia de página de códigos.
        assertArrayEquals(bytes(0x1B, 0x40, 0x1B, 0x4D, 0x00), EscPos(PrintCharset.ASCII).init().toBytes())
    }

    @Test fun commandsAreTheStandardEscPosBytes() {
        val p = EscPos(PrintCharset.PC858).align(Align.CENTER).bold(true).size(TextSize.BIG).text("A").lf().bold(false).size(TextSize.NORMAL).align(Align.RIGHT).feed(3).cut()
        assertArrayEquals(bytes(0x1B, 0x61, 1, 0x1B, 0x45, 1, 0x1D, 0x21, 0x11, 0x41, 0x0A, 0x1B, 0x45, 0, 0x1D, 0x21, 0, 0x1B, 0x61, 2, 0x1B, 0x64, 3, 0x1D, 0x56, 0x42, 0x00), p.toBytes())
        assertArrayEquals(bytes(0x10, 0x04, 0x01), EscPos.STATUS_QUERY)
    }

    @Test fun spanishLettersMapToPc858Bytes() {
        val expected = mapOf('á' to 0xA0, 'é' to 0x82, 'í' to 0xA1, 'ó' to 0xA2, 'ú' to 0xA3, 'ñ' to 0xA4, 'Ñ' to 0xA5, 'ü' to 0x81, 'Ü' to 0x9A, '¿' to 0xA8, '¡' to 0xAD, '€' to 0xD5,
            'Á' to 0xB5, 'É' to 0x90, 'Í' to 0xD6, 'Ó' to 0xE0, 'Ú' to 0xE9, '°' to 0xF8, 'ª' to 0xA6, 'º' to 0xA7, '«' to 0xAE, '»' to 0xAF)
        for ((c, b) in expected) assertArrayEquals("$c", bytes(b), PrintText.encode(c.toString(), PrintCharset.PC858))
    }

    /** La tabla propia coincide con la página 850 de Java en todos los bytes altos (858 solo cambia 0xD5: «ı» → «€»). */
    @Test fun tableMatchesJavaCp850ExceptTheEuro() {
        val cp850 = Charset.forName("IBM850")
        for (v in 0x80..0xFF) {
            if (v == 0xD5 || v == 0xFF || v == 0xF0) continue // 0xF0 = guion suave, 0xFF = espacio irrompible: no imprimibles como tales
            val javaChar = String(byteArrayOf(v.toByte()), cp850)[0]
            assertEquals("byte 0x%02X".format(v), javaChar, PrintText.printable(javaChar.toString(), PrintCharset.PC858)[0])
            assertArrayEquals("byte 0x%02X".format(v), byteArrayOf(v.toByte()), PrintText.encode(javaChar.toString(), PrintCharset.PC858))
        }
    }

    @Test fun asciiFallbackRemovesAccents() {
        assertEquals("Manana en el cafe: Nino, pinata, u, !Que? Ano", String(PrintText.encode("Mañana en el café: Niño, piñata, ü, ¡Qué¿ Año", PrintCharset.ASCII)))
        assertEquals("cancion 100% EUR 5", String(PrintText.encode("canción 100% € 5", PrintCharset.ASCII)))
        assertEquals("a-b \"x\"", String(PrintText.encode("a–b «x»", PrintCharset.ASCII)))
        assertEquals("Strasse", String(PrintText.encode("Straße", PrintCharset.ASCII)))
    }

    @Test fun unknownCharactersBecomeQuestionMarksAndOddSpacesBecomeSpaces() {
        assertEquals("a b c d", String(PrintText.encode("a b c\td", PrintCharset.PC858)))
        assertEquals("ab", String(PrintText.encode("a\u0007b", PrintCharset.PC858)))
        assertEquals("?", String(PrintText.encode("日", PrintCharset.PC858)))
        assertTrue(PrintText.canEncode("C$ ñ €", PrintCharset.PC858))
        assertFalse(PrintText.canEncode("₡", PrintCharset.PC858))
        assertFalse(PrintText.canEncode("ñ", PrintCharset.ASCII))
    }

    @Test fun dumpReadsBackWhatWasBuilt() {
        val b = EscPos(PrintCharset.PC858).init().align(Align.CENTER).bold(true).text("Piña").lf().feed(2).cut().toBytes()
        assertEquals("[init][font 0][codepage 19][align center][bold]Piña\n[feed 2][cut]", EscPosDump.toText(b, PrintCharset.PC858))
    }
}

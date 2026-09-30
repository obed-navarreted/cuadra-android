package com.cuadra.caja.domain.printing

import com.cuadra.caja.core.model.Currency
import com.cuadra.caja.core.model.Money
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Instantáneas del recibo: el texto EXACTO (`resources/receipts/<caso>.txt`) y los bytes ESC/POS (`.hex`, y su lectura en `.dump`). Cambiar el recibo obliga a revisar estos archivos. */
class ReceiptSnapshotTest {
    private fun resource(name: String) = javaClass.getResource("/receipts/$name")!!.readText()

    @Test fun textLayoutMatchesTheSnapshots() {
        for ((name, receipt) in Scenarios.all) assertEquals("caso $name", resource("$name.txt"), receipt.previewText() + "\n")
    }

    @Test fun bytesMatchTheSnapshots() {
        for (name in listOf("simple", "accents", "ascii", "cancelled")) {
            val r = Scenarios.all.getValue(name)
            val bytes = r.toBytes()
            assertEquals("hex de $name", resource("$name.hex"), bytes.joinToString(" ") { "%02X".format(it) }.chunked(96).joinToString("\n") + "\n")
            assertEquals("lectura de $name", resource("$name.dump"), EscPosDump.toText(bytes, r.charset) + "\n")
        }
    }

    @Test fun noLineIsWiderThanThePaper() {
        for ((name, r) in Scenarios.all) for (line in r.previewText().lines()) assertTrue("$name: «$line» mide ${line.length} y caben ${r.columns}", line.length <= r.columns)
        // En los bytes: cada línea (sin comandos) cabe en las columnas, contando doble las de doble ancho.
        for ((name, r) in Scenarios.all) for (l in r.lines) {
            val cap = r.columns / l.size.widthFactor
            assertTrue("$name: «${l.text}» mide ${l.text.length} y caben $cap", l.text.length <= cap)
        }
    }

    @Test fun wrappingNeverSplitsAWordThatFits() {
        for ((name, r) in Scenarios.all) {
            val original = r.lines.map { it.text }
            val longestWord = original.flatMap { it.split(' ') }.maxOf { it.length }
            assertTrue("$name: hay una palabra de $longestWord", longestWord <= r.columns)
        }
        // Una palabra más larga que el ancho sí se parte, y nada se pierde.
        val words = ReceiptFormatter.wrap("Supercalifragilisticoespialidosoextraordinariamentelargo y fin", 32)
        assertEquals(listOf("Supercalifragilisticoespialidoso", "extraordinariamentelargo y fin"), words)
        assertEquals(listOf("uno dos", "tres"), ReceiptFormatter.wrap("uno   dos tres", 8))
        assertEquals(emptyList<String>(), ReceiptFormatter.wrap("   ", 8))
    }

    @Test fun leftRightAlignsOrDropsTheAmountToItsOwnLine() {
        assertEquals(listOf("Total          C$ 5.00"), ReceiptFormatter.leftRight("Total", "C$ 5.00", 22))
        val l = ReceiptFormatter.leftRight("  1 x C$ 99,999,999.00", "C$ 99,999,999.00", 32)
        assertEquals(2, l.size)
        assertEquals("  1 x C$ 99,999,999.00", l[0])
        assertEquals("C$ 99,999,999.00".padStart(32), l[1])
    }

    @Test fun referenceAndBusinessTimeZone() {
        assertEquals("#A1B2C3", ReceiptFormatter.reference("a1b2c3d4-0000-4000-8000-000000000001"))
        assertEquals("#AB", ReceiptFormatter.reference("ab"))
        // 20:32 UTC del 29 sep es 14:32 en Managua, pero ya el 30 a las 05:32 en Tokio: el recibo usa la zona del NEGOCIO.
        assertEquals("29 sep 2026  14:32", ReceiptFormatter.dateTime(ReceiptFixtures.AT, ZoneId.of("America/Managua"), ReceiptLabels.ES))
        assertEquals("30 sep 2026  05:32", ReceiptFormatter.dateTime(ReceiptFixtures.AT, ZoneId.of("Asia/Tokyo"), ReceiptLabels.ES))
        assertEquals("29 Sep 2026  14:32", ReceiptFormatter.dateTime(ReceiptFixtures.AT, ZoneId.of("America/Managua"), ReceiptLabels.EN))
    }

    @Test fun quantityHasNoUselessZeros() {
        assertEquals("2", ReceiptFormatter.quantity(2000))
        assertEquals("0.75", ReceiptFormatter.quantity(750))
        assertEquals("2.345", ReceiptFormatter.quantity(2345))
        assertEquals("99999", ReceiptFormatter.quantity(99_999_000))
    }

    @Test fun cancelledReprintCarriesTheBannerAndNormalOnesDoNot() {
        val cancelled = Scenarios.all.getValue("cancelled")
        assertTrue(cancelled.lines.any { it.text == "ANULADA" && it.bold && it.size == TextSize.BIG && it.align == Align.CENTER })
        assertFalse(Scenarios.all.getValue("simple").lines.any { it.text == "ANULADA" })
    }

    @Test fun neverPrintsCardDataOrReferences() {
        val d = ReceiptFixtures.data(
            listOf(ReceiptFixtures.item("Pan", 1000, 5000)), listOf(ReceiptPayment("CARD", null, 5000, null, null, null)),
        )
        val text = ReceiptFormatter.build(d).previewText()
        assertTrue(text.contains("Tarjeta"))
        assertFalse(text.contains("4111"))
    }

    @Test fun moneyUsesTheBusinessCurrencyDecimalsAndFallsBackToTheCodeWhenTheSymbolIsNotPrintable() {
        assertEquals("C$ 1,234.50", ReceiptFixtures.NIO(123_450))
        // ₡ no existe en PC858: sale «CRC», sin decimales y sin espacios raros.
        assertEquals("CRC 1 500", ReceiptFixtures.CRC(1500))
        assertTrue(PrintText.canEncode(ReceiptFixtures.CRC(1234567), PrintCharset.PC858))
        val eur = SaleReceipts.money(Currency.of("EUR"), Locale.Builder().setLanguage("es").setRegion("ES").build(), PrintCharset.PC858)
        assertTrue(eur(123_450).startsWith("€"))
        // Con «sin acentos» el euro tampoco se puede dibujar: sale «EUR».
        val eurAscii = SaleReceipts.money(Currency.of("EUR"), Locale.Builder().setLanguage("es").setRegion("ES").build(), PrintCharset.ASCII)
        assertTrue(eurAscii(123_450).startsWith("EUR"))
        assertEquals(Money(5).format(Currency.of("NIO"), Locale.US), "C$ 0.05")
    }

    @Test fun copiesRepeatTheJobWithACutEach() {
        val r = Scenarios.all.getValue("cancelled")
        val one = r.toBytes(1)
        val two = r.toBytes(2)
        assertEquals(one.size * 2, two.size)
        assertEquals(2, EscPosDump.toText(two, r.charset).split("[cut]").size - 1)
        assertEquals(r.toBytes(5).size, r.toBytes(50).size) // más de 5 copias se recorta
        assertEquals(one.size, r.toBytes(0).size)
    }
}

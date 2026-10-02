package com.cuadra.caja.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WhatsAppReceiptTest {
    @Test fun theFieldStartsWithTheBusinessCountryCode() {
        assertEquals("+505 ", WhatsAppReceipt.prefill("NI"))
        assertEquals("+52 ", WhatsAppReceipt.prefill("mx"))
        assertEquals("", WhatsAppReceipt.prefill("ZZ"))
        assertEquals("", WhatsAppReceipt.prefill(null))
    }

    @Test fun aNationalNumberGetsTheCountryCode() {
        assertEquals("50588881234", WhatsAppReceipt.number("8888 1234", "NI"))
        assertEquals("50588881234", WhatsAppReceipt.number("8888-1234", "NI"))
        assertEquals("50588881234", WhatsAppReceipt.number("(505) 8888-1234", "NI"))
    }

    @Test fun thePrefilledCodeAndSpacesAreAccepted() {
        assertEquals("50588881234", WhatsAppReceipt.number("+505 8888 1234", "NI"))
        assertEquals("50588881234", WhatsAppReceipt.number("  +505  8888  1234 ", "NI"))
        assertEquals("50588881234", WhatsAppReceipt.number("00505 8888 1234", "NI"))
        // Otro país escrito a propósito se respeta.
        assertEquals("50688881234", WhatsAppReceipt.number("+506 8888 1234", "NI"))
    }

    @Test fun theLeadingTrunkZeroIsDropped() {
        assertEquals("50588881234", WhatsAppReceipt.number("0 8888 1234", "NI"))
        assertEquals("50588881234", WhatsAppReceipt.number("+505 0 8888 1234", "NI"))
        assertEquals("525512345678", WhatsAppReceipt.number("0 55 1234 5678", "MX"))
    }

    @Test fun onlyTheCodeOrGarbageIsNotANumber() {
        assertNull(WhatsAppReceipt.number("+505 ", "NI"))
        assertNull(WhatsAppReceipt.number("", "NI"))
        assertNull(WhatsAppReceipt.number("abc", "NI"))
        assertNull(WhatsAppReceipt.number("12", "NI"))
        assertNull(WhatsAppReceipt.number("+1234567890123456789", "NI"))
    }

    @Test fun theReceiptTextHasEveryLineAndTheTotal() {
        val template = MessageTemplates.default(MessageKind.TICKET, "es")
        val text = WhatsAppReceipt.text(template, "Pulpería San Benito", "1 oct", listOf("Cerveza ×2" to "C$ 120.00", "Nacatamal" to "C$ 80.00"), "C$ 200.00")
        assertTrue(text, text.startsWith("Pulpería San Benito"))
        assertTrue(text, text.contains("1 oct"))
        val lines = text.lines()
        assertTrue(text, lines.any { it.startsWith("· Cerveza ×2") && it.endsWith("C$ 120.00") })
        assertTrue(text, lines.any { it.startsWith("· Nacatamal") && it.endsWith("C$ 80.00") })
        assertTrue(text, text.contains("Total: C$ 200.00"))
    }

    @Test fun theLinkOpensThatChatWithTheTextEncoded() {
        assertEquals("https://wa.me/50588881234?text=Total%3A%20C%24%20200.00", WhatsAppReceipt.link("50588881234", "Total: C$ 200.00"))
    }
}

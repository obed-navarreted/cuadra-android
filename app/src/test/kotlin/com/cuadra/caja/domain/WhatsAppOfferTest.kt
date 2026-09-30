package com.cuadra.caja.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WhatsAppOfferTest {
    @Test fun offOffersNothingAndOpensNothing() {
        assertNull(WhatsAppOffer.doneShare(false, "credito", "ticket"))
        assertNull(WhatsAppOffer.doneShare(false, null, "ticket"))
        // Ni siquiera con fiado y la casilla marcada.
        assertNull(WhatsAppOffer.autoShare(false, true, "credito"))
    }

    @Test fun onKeepsTheOldBehaviour() {
        assertEquals("ticket", WhatsAppOffer.doneShare(true, null, "ticket"))
        assertEquals("credito", WhatsAppOffer.doneShare(true, "credito", "ticket"))
        assertEquals("credito", WhatsAppOffer.autoShare(true, true, "credito"))
        assertNull("casilla desmarcada", WhatsAppOffer.autoShare(true, false, "credito"))
        assertNull("una venta normal nunca abre nada sola", WhatsAppOffer.autoShare(true, true, null))
    }
}

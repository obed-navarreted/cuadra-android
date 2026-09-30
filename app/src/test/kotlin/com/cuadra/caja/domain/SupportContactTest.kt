package com.cuadra.caja.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SupportContactTest {
    @Test fun whatsappNumberKeepsDigitsAndFallsBack() {
        assertEquals("50582724138", SupportContact.whatsappNumber("+505 8272-4138"))
        assertEquals("50582724138", SupportContact.whatsappNumber(null))
        assertEquals("50582724138", SupportContact.whatsappNumber("123"))
        assertEquals("50612345678", SupportContact.whatsappNumber("50612345678"))
    }

    @Test fun whatsappUrlCarriesTheEncodedMessage() {
        assertEquals("https://wa.me/50582724138?text=Hola%2C%20quiero%20apoyar%20Cuentiva", SupportContact.whatsappUrl(null, "Hola, quiero apoyar Cuentiva"))
    }

    @Test fun emailFallsBackWhenInvalid() {
        assertEquals("ndiazobed@gmail.com", SupportContact.email(null))
        assertEquals("ndiazobed@gmail.com", SupportContact.email("no es correo"))
        assertEquals("a@b.co", SupportContact.email(" a@b.co "))
    }

    @Test fun playDistributionIsDetected() {
        assertTrue(SupportContact.isPlay("play")); assertTrue(SupportContact.isPlay(" Play "))
        assertFalse(SupportContact.isPlay("direct"))
    }
}

package com.cuadra.caja.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class PhoneNumbersTest {
    private fun n(raw: String?, country: String) = PhoneNumbers.normalize(raw, country)

    // Mismos casos que el servidor (PhonesTest.java): las dos plataformas deben producir el mismo número.
    @Test fun aNationalNumberGetsTheCountryCode() {
        assertEquals(PhoneResult.Valid("50588551234"), n("8855 1234", "NI"))
        assertEquals(PhoneResult.Valid("50588551234"), n("(505) 8855-1234", "NI"))
        assertEquals(PhoneResult.Valid("525512345678"), n("5512345678", "MX"))
        assertEquals(PhoneResult.Valid("13055550100"), n("305 555 0100", "US"))
    }

    @Test fun anInternationalNumberIsKept() {
        assertEquals(PhoneResult.Valid("50588551234"), n("+505 8855 1234", "NI"))
        assertEquals(PhoneResult.Valid("13055550100"), n("+1 305 555 0100", "NI"))
        assertEquals(PhoneResult.Valid("50588551234"), n("00505 8855 1234", "HN"))
        assertEquals(PhoneResult.Valid("50588551234"), n("50588551234", "NI"))
        assertEquals(PhoneResult.Valid("13055550100"), n("13055550100", "US"))
    }

    @Test fun blankIsNoPhoneAndGarbageIsInvalid() {
        assertEquals(PhoneResult.None, n(null, "NI"))
        assertEquals(PhoneResult.None, n("   ", "NI"))
        assertEquals(PhoneResult.Invalid, n("abc", "NI"))
        assertEquals(PhoneResult.Invalid, n("1".repeat(20), "NI"))
        assertEquals(PhoneResult.Invalid, n("123", "XX"))
    }
}

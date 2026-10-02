package com.cuadra.caja.domain

import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

class ParkRulesTest {
    @Test fun parksOnlyWithItemsAndWithoutRegisterCheckout() {
        assertTrue(ParkRules.canPark(registerCheckout = false, cartEmpty = false))
        assertFalse(ParkRules.canPark(registerCheckout = false, cartEmpty = true))
        assertFalse(ParkRules.canPark(registerCheckout = true, cartEmpty = false))
        assertFalse(ParkRules.canPark(registerCheckout = true, cartEmpty = true))
    }

    @Test fun theMiddleButtonIsParkOrSendToRegister() {
        assertEquals(ParkRules.Middle.PARK, ParkRules.middle(registerCheckout = false))
        assertEquals(ParkRules.Middle.SEND, ParkRules.middle(registerCheckout = true))
    }

    @Test fun sendsOnlyWithTheSettingOnAndSomethingOnTheTicket() {
        assertTrue(ParkRules.canSend(registerCheckout = true, cartEmpty = false))
        assertFalse(ParkRules.canSend(registerCheckout = true, cartEmpty = true))
        assertFalse(ParkRules.canSend(registerCheckout = false, cartEmpty = false))
        assertFalse(ParkRules.canSend(registerCheckout = false, cartEmpty = true))
    }
}

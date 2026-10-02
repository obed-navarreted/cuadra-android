package com.cuadra.caja.ui

import com.cuadra.caja.domain.PaymentPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** El ajuste «Cobro en caja» manda sobre un cobro ya abierto (ADR 0015): se apaga o se enciende con la pantalla abierta. */
class CobroRegisterSettingTest {
    private fun cobro(on: Boolean = true, toRegister: Boolean = false) = CobroUi(PaymentPlan.cash(5000), registerCheckout = on, toRegister = toRegister)

    @Test fun turningOffHidesTheOptionAndFallsBackToChargeNow() {
        val c = cobro(toRegister = true).withRegisterSetting(false)
        assertFalse(c.registerCheckout)
        assertFalse(c.toRegister)
        assertTrue(c.registerOffNotice)
    }

    @Test fun turningOffWithChargeNowSelectedKeepsItAndWarns() {
        val c = cobro(toRegister = false).withRegisterSetting(false)
        assertFalse(c.registerCheckout)
        assertTrue(c.registerOffNotice)
    }

    @Test fun turningOnShowsTheOptionAgainAndClearsTheWarning() {
        val c = cobro(on = false).copy(registerOffNotice = true).withRegisterSetting(true)
        assertTrue(c.registerCheckout)
        assertFalse(c.registerOffNotice)
        assertFalse(c.toRegister)
    }

    @Test fun nothingChangesWhenTheSettingDoesNotChange() {
        val c = cobro()
        assertSame(c, c.withRegisterSetting(true))
    }

    @Test fun aTicketAlreadySentOrChargedIsLeftAlone() {
        val sent = cobro(toRegister = true).copy(sent = true)
        assertSame(sent, sent.withRegisterSetting(false))
        val done = cobro().copy(doneChangeMinor = 0)
        assertEquals(true, done.withRegisterSetting(false).registerCheckout)
    }
}

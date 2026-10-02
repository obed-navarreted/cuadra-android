package com.cuadra.caja.ui

import com.cuadra.caja.domain.Cart
import com.cuadra.caja.domain.CartLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * «Enviar a caja» desde la barra (ADR 0015): cómo abre la hoja y qué pasa si el ajuste «Cobro en caja» cambia con ella abierta. «Cobrar» ya no tiene modo
 * «Enviar a caja»: es solo para cobrar.
 */
class CobroRegisterSettingTest {
    private val cart = Cart(listOf(CartLine("l1", null, null, "Café", null, 5000, null, 1000)))

    @Test fun aNewTicketOpensWithAnEmptyNoteAndTheSendButton() {
        val s = SendUi.open(resumedLabel = null, resumedPending = false)
        assertEquals("", s.note)
        assertFalse(s.update)
        assertFalse(s.saving)
    }

    @Test fun aTicketResumedFromTheQueueKeepsItsNoteAndReadsUpdate() {
        val s = SendUi.open(resumedLabel = "Mesa 4", resumedPending = true)
        assertEquals("Mesa 4", s.note)
        assertTrue(s.update)
    }

    @Test fun aResumedPlainParkedTicketKeepsItsLabelButIsANewSend() {
        val s = SendUi.open(resumedLabel = "Señora de rojo", resumedPending = false)
        assertEquals("Señora de rojo", s.note)
        assertFalse(s.update)
    }

    @Test fun thePrefilledNoteIsCappedLikeTheField() {
        assertEquals(com.cuadra.caja.domain.RegisterQueue.NOTE_MAX, SendUi.open("x".repeat(500), true).note.length)
    }

    @Test fun turningTheSettingOffClosesTheOpenSheetWithTheOrangeNoticeAndKeepsTheTicket() {
        val ui = CajaUi(cart = cart, sending = SendUi("Mesa 4"), resumedId = "s1", resumedLabel = "Mesa 4")
        val off = ui.withRegisterSetting(false)
        assertNull(off.sending)
        assertEquals(Notice.RegisterCheckoutOff, off.notice)
        assertEquals(cart, off.cart)
        assertEquals("s1", off.resumedId)
    }

    @Test fun nothingChangesWithoutAnOpenSheetOrWhenTheSettingStaysOn() {
        val closed = CajaUi(cart = cart)
        assertSame(closed, closed.withRegisterSetting(false))
        val open = CajaUi(cart = cart, sending = SendUi())
        assertSame(open, open.withRegisterSetting(true))
    }

    @Test fun theHardwareScannerIsIgnoredWhileTheSheetIsOpen() {
        assertEquals(HardwareScanRoute.IGNORE, CajaUi(cart = cart, sending = SendUi()).hardwareScanRoute())
        assertEquals(HardwareScanRoute.ADD, CajaUi(cart = cart).hardwareScanRoute())
    }
}

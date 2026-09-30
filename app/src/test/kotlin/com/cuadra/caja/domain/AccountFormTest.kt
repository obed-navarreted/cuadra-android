package com.cuadra.caja.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountFormTest {
    private val base = AccountDraft(originalName = "Kevin")

    @Test fun nothingChangedCannotSave() {
        assertFalse(base.ready); assertFalse(base.nameChanged); assertFalse(base.wantsPin)
    }

    @Test fun renamingAloneIsEnough() {
        assertTrue(base.copy(name = " Kevin R. ").ready)
        assertFalse(base.copy(name = "  ").ready)
        assertFalse(base.copy(name = " Kevin ").ready)   // solo espacios de diferencia
    }

    @Test fun changingPinNeedsCurrentNewAndMatchingRepeat() {
        val ok = base.copy(current = "12345", pin = "56789", confirm = "56789")
        assertTrue(ok.ready); assertNull(ok.pinProblem)
        assertFalse(ok.copy(current = "").ready)
        assertFalse(ok.copy(current = "1234").ready)
        assertFalse(ok.copy(confirm = "56790").ready)
        assertEquals(PinProblem.MISMATCH, ok.copy(confirm = "56790").pinProblem)
        assertFalse(ok.copy(pin = "5678", confirm = "5678").ready)  // 4 números no bastan
        assertFalse(ok.copy(pin = "567890", confirm = "567890").ready)  // 6 tampoco
        assertEquals(PinProblem.TOO_SHORT, ok.copy(pin = "5678", confirm = "5678").pinProblem)
    }

    @Test fun withoutAPinYetTheCurrentOneIsNotAsked() {
        val owner = AccountDraft("Dueño", hasPin = false, pin = "43210", confirm = "43210")
        assertTrue(owner.ready)
    }

    @Test fun aPinChangeWithABlankNameIsHeldBack() {
        assertFalse(base.copy(name = "", current = "12345", pin = "56789", confirm = "56789").ready)
    }

    @Test fun mismatchIsShownOnlyOnceTheRepeatIsAsLongAsThePin() {
        assertFalse(base.copy(pin = "56789", confirm = "56").showMismatch)
        assertTrue(base.copy(pin = "56789", confirm = "56790").showMismatch)
        assertFalse(base.copy(pin = "56789", confirm = "56789").showMismatch)
        assertFalse(base.copy(pin = "56789", confirm = "").showMismatch)
    }
}

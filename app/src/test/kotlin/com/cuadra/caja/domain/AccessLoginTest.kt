package com.cuadra.caja.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessLoginTest {
    // ---- código del negocio ----
    @Test fun codeKeepsOnlyFiveDigits() {
        assertEquals("13085", AccessCode.sanitize("13085"))
        assertEquals("13085", AccessCode.sanitize("1 30-85"))
        assertEquals("13085", AccessCode.sanitize("1308599"))
        assertEquals("", AccessCode.sanitize("abc"))
        assertEquals("12", AccessCode.sanitize("a1b2"))
    }

    @Test fun codeIsCompleteOnlyWithFiveDigits() {
        assertTrue(AccessCode.isComplete("13085")); assertTrue(AccessCode.isComplete("00001"))
        assertFalse(AccessCode.isComplete("1308")); assertFalse(AccessCode.isComplete("130856")); assertFalse(AccessCode.isComplete("13O85")); assertFalse(AccessCode.isComplete(""))
    }

    @Test fun aChosenCodeHasFiveDigitsAndNoLeadingZero() {
        assertTrue(AccessCode.isChosenValid("13085")); assertTrue(AccessCode.isChosenValid("99999")); assertTrue(AccessCode.isChosenValid("10000"))
        assertFalse(AccessCode.isChosenValid("01234")); assertFalse(AccessCode.isChosenValid("1234")); assertFalse(AccessCode.isChosenValid("123456")); assertFalse(AccessCode.isChosenValid("12a45")); assertFalse(AccessCode.isChosenValid(""))
    }

    @Test fun codeIsShownBigAndSpaced() {
        assertEquals("1 3 0 8 5", AccessCode.spaced("13085"))
        assertEquals("", AccessCode.spaced(""))
    }

    @Test fun shareMessageNamesTheBusinessAndTheCode() {
        val es = "Para entrar a %1\$s en la app Cuentiva: código %2\$s y tu PIN."
        assertEquals("Para entrar a Panadería Sol en la app Cuentiva: código 13085 y tu PIN.", AccessCode.shareMessage(es, "  Panadería Sol ", "13085"))
        val en = "To get into %1\$s in the Cuentiva app: code %2\$s and your PIN."
        assertEquals("To get into Sol Bakery in the Cuentiva app: code 13085 and your PIN.", AccessCode.shareMessage(en, "Sol Bakery", "13085"))
    }

    @Test fun credentialsMessageCarriesCodeAndPinWithTheNameForReference() {
        val t = "%3\$s: para entrar a %1\$s en la app Cuentiva usa el código %2\$s y tu PIN %4\$s."
        assertEquals("Prueba app: para entrar a Sol en la app Cuentiva usa el código 13085 y tu PIN 12345.", AccessCode.credentialsMessage(t, "Sol", "13085", " Prueba app ", "12345"))
    }

    // ---- formulario de entrada (código + PIN, sin usuario) ----
    @Test fun pinTakesAtMostFiveDigitsAndIgnoresOtherKeys() {
        var f = MemberLoginForm()
        "123456".forEach { f = f.digit(it) }
        assertEquals("12345", f.pin)
        f = f.digit('x')
        assertEquals("12345", f.pin)
        f = f.backspace().backspace()
        assertEquals("123", f.pin)
        assertEquals("", MemberLoginForm().backspace().pin)
    }

    @Test fun formIsReadyWithCodeAndFiveDigitPin() {
        val ok = MemberLoginForm("13085", "12345")
        assertTrue(ok.ready)
        assertFalse(ok.copy(code = "1308").ready)
        assertFalse(ok.copy(pin = "1234").ready)
        assertFalse(MemberLoginForm().ready)
    }

    @Test fun editingCleansTheCode() {
        assertEquals("13085", MemberLoginForm().withCode(" 13-085 9").code)
    }

    @Test fun theFifthDigitSubmitsOnlyWhenTheCodeIsThere() {
        val before = MemberLoginForm("13085", "1234")
        assertTrue(before.autoSubmits(before.digit('5')))
        val noCode = MemberLoginForm("130", "1234")
        assertFalse(noCode.autoSubmits(noCode.digit('5')))
        // Los primeros cuatro números no envían nada.
        val early = MemberLoginForm("13085", "123")
        assertFalse(early.autoSubmits(early.digit('4')))
    }

    // ---- PIN nuevo dos veces ----
    private fun type(start: NewPinEntry, digits: String) = digits.fold(start) { e, d -> e.digit(d) }

    @Test fun newPinAsksTwiceAndFinishesWhenBothMatch() {
        var e = NewPinEntry()
        assertEquals(NewPinEntry.Stage.FIRST, e.stage)
        e = type(e, "1234")
        assertEquals(NewPinEntry.Stage.FIRST, e.stage)
        e = e.digit('5')
        assertEquals(NewPinEntry.Stage.REPEAT, e.stage)
        assertEquals("", e.shown)
        assertFalse(e.done)
        e = type(e, "1234")
        assertEquals("1234", e.shown)
        assertFalse(e.done)
        e = e.digit('5')
        assertTrue(e.done)
        assertEquals("12345", e.first)
        // Ya terminado, no acepta más.
        assertEquals(e, e.digit('9'))
    }

    @Test fun aWrongRepeatStartsOverWithAWarning() {
        val e = type(NewPinEntry(), "1234512346")
        assertEquals(NewPinEntry(mismatch = true), e)
        assertEquals(NewPinEntry.Stage.FIRST, e.stage)
        // Al volver a escribir, el aviso desaparece.
        assertFalse(e.digit('1').mismatch)
    }

    @Test fun backspaceWalksBackThroughBothSteps() {
        var e = type(NewPinEntry(), "12345")
        e = e.digit('1').backspace()
        assertEquals("", e.second)
        assertEquals(NewPinEntry.Stage.REPEAT, e.stage)
        e = e.backspace()
        assertEquals("1234", e.first)
        assertEquals(NewPinEntry.Stage.FIRST, e.stage)
        assertNotEquals(NewPinEntry.Stage.REPEAT, NewPinEntry().stage)
        assertEquals(NewPinEntry(), NewPinEntry().backspace())
    }

    @Test fun nonDigitsAreIgnoredInTheNewPin() {
        assertEquals(NewPinEntry(), NewPinEntry().digit('a'))
    }
}

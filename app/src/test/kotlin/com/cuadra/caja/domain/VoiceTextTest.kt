package com.cuadra.caja.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceTextTest {
    @Test fun spokenTextInAnEmptyFieldStartsWithACapital() {
        assertEquals("Queso seco", VoiceText.merge("", "queso seco"))
        assertEquals("Queso seco", VoiceText.merge("   ", "queso seco"))
    }

    @Test fun spokenTextIsAppendedToWhatIsAlreadyThere() {
        assertEquals("Queso seco de vaca", VoiceText.merge("Queso seco", "de vaca"))
        assertEquals("Queso seco de vaca", VoiceText.merge("Queso seco ", "de vaca"))
    }

    @Test fun extraSpacesAreCleanedAndSilenceChangesNothing() {
        assertEquals("Crema de leche", VoiceText.merge("", "  crema   de  leche "))
        assertEquals("Crema", VoiceText.merge("Crema", ""))
        assertEquals("Crema", VoiceText.merge("Crema", "   "))
    }

    @Test fun accentedFirstLettersGetTheirCapital() {
        assertEquals("Árbol", VoiceText.merge("", "árbol"))
    }
}

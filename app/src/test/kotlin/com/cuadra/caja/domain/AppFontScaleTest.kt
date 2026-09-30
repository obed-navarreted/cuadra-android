package com.cuadra.caja.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/** La política de letra de la app (Más › Tamaño de letra): todas las opciones, los extremos del sistema y los datos rotos. */
class AppFontScaleTest {
    private fun eff(system: Float, c: FontSizeChoice) = AppFontScale.effective(system, c)

    @Test fun automaticFollowsTheSystemButNeverPast115() {
        assertEquals(0.85f, eff(0.85f, FontSizeChoice.AUTO), 0f)
        assertEquals(1.0f, eff(1.0f, FontSizeChoice.AUTO), 0f)
        assertEquals(1.15f, eff(1.15f, FontSizeChoice.AUTO), 0f)
        assertEquals(1.15f, eff(1.3f, FontSizeChoice.AUTO), 0f)
        assertEquals(1.15f, eff(1.5f, FontSizeChoice.AUTO), 0f)
        assertEquals(1.15f, eff(2.0f, FontSizeChoice.AUTO), 0f)
    }

    @Test fun smallNormalAndLargeIgnoreTheSystem() {
        for (sys in listOf(0.85f, 1.0f, 1.3f, 2.0f)) {
            assertEquals(0.9f, eff(sys, FontSizeChoice.SMALL), 0f)
            assertEquals(1.0f, eff(sys, FontSizeChoice.NORMAL), 0f)
            assertEquals(1.15f, eff(sys, FontSizeChoice.LARGE), 0f)
        }
    }

    @Test fun likeTheSystemUsesTheWholeScale() {
        assertEquals(0.85f, eff(0.85f, FontSizeChoice.SYSTEM), 0f)
        assertEquals(1.0f, eff(1.0f, FontSizeChoice.SYSTEM), 0f)
        assertEquals(1.5f, eff(1.5f, FontSizeChoice.SYSTEM), 0f)
        assertEquals(2.0f, eff(2.0f, FontSizeChoice.SYSTEM), 0f)
    }

    @Test fun brokenSystemValuesFallBackToOne() {
        for (bad in listOf(Float.NaN, 0f, -1f, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            for (c in FontSizeChoice.entries) {
                val expected = when (c) { FontSizeChoice.SMALL -> 0.9f; FontSizeChoice.LARGE -> 1.15f; else -> 1.0f }
                assertEquals("sistema=$bad, opción=$c", expected, eff(bad, c), 0f)
            }
        }
    }

    @Test fun absurdSystemValuesAreClamped() {
        assertEquals(0.5f, eff(0.1f, FontSizeChoice.SYSTEM), 0f)
        assertEquals(3.0f, eff(9f, FontSizeChoice.SYSTEM), 0f)
        assertEquals(0.5f, eff(0.1f, FontSizeChoice.AUTO), 0f)
        assertEquals(1.15f, eff(9f, FontSizeChoice.AUTO), 0f)
    }

    @Test fun choiceParsingDefaultsToAutomatic() {
        assertEquals(FontSizeChoice.AUTO, FontSizeChoice.parse(null))
        assertEquals(FontSizeChoice.AUTO, FontSizeChoice.parse("nonsense"))
        for (c in FontSizeChoice.entries) assertEquals(c, FontSizeChoice.parse(c.name))
    }
}

package com.cuadra.caja.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateTest {
    @Test fun equalOlderNewer() {
        assertEquals(0, AppVersion.compare("1.2.3", "1.2.3"))
        assertTrue(AppVersion.compare("1.2.3", "1.2.4")!! < 0)
        assertTrue(AppVersion.compare("1.10.0", "1.9.9")!! > 0)   // numérico, no de texto
        assertTrue(AppVersion.compare("2.0.0", "1.99.99")!! > 0)
    }

    @Test fun differentLengthsArePaddedWithZeros() {
        assertEquals(0, AppVersion.compare("1.2", "1.2.0"))
        assertEquals(0, AppVersion.compare("1", "1.0.0.0"))
        assertTrue(AppVersion.compare("1.2", "1.2.1")!! < 0)
        assertTrue(AppVersion.compare("1.2.0.1", "1.2")!! > 0)
    }

    @Test fun suffixesAreIgnored() {
        assertEquals(0, AppVersion.compare("0.1.0-debug", "0.1.0"))
        assertEquals(0, AppVersion.compare("v1.2.3+45", "1.2.3"))
        assertTrue(AppVersion.compare("1.0.0-rc1", "1.0.1")!! < 0)
        assertEquals(listOf(3, 4), AppVersion.parse(" 3.4-beta "))
    }

    @Test fun unreadableIsNull() {
        assertNull(AppVersion.parse(null))
        assertNull(AppVersion.parse(""))
        assertNull(AppVersion.parse("  "))
        assertNull(AppVersion.parse("abc"))
        assertNull(AppVersion.compare("1.0", null))
    }

    @Test fun blankOrMissingRequirementNeverBlocks() {
        assertEquals(UpdateNeed.None, AppUpdate.need("0.1.0", null, null))
        assertEquals(UpdateNeed.None, AppUpdate.need("0.1.0", "", " "))
        assertEquals(UpdateNeed.None, AppUpdate.need("0.1.0", "garbage", "garbage"))
    }

    @Test fun unreadableInstalledVersionNeverBlocks() {
        assertEquals(UpdateNeed.None, AppUpdate.need("", "9.0.0", "9.0.0"))
    }

    @Test fun belowMinIsRequiredEvenIfRecommendedDismissed() {
        assertEquals(UpdateNeed.Required("1.0.0"), AppUpdate.need("0.9.9-debug", "1.0.0", "1.2.0", dismissedRecommended = "1.2.0"))
        assertEquals(UpdateNeed.Required("1.0.0"), AppUpdate.need("0.9", " 1.0.0 ", null))
    }

    @Test fun atOrAboveMinIsNotBlocked() {
        assertEquals(UpdateNeed.None, AppUpdate.need("1.0.0", "1.0.0", null))
        assertEquals(UpdateNeed.None, AppUpdate.need("1.0.1", "1.0.0", "1.0.0"))
    }

    @Test fun recommendedIsDismissiblePerVersion() {
        assertEquals(UpdateNeed.Recommended("1.2.0"), AppUpdate.need("1.0.0", "0.5.0", "1.2.0"))
        assertEquals(UpdateNeed.None, AppUpdate.need("1.0.0", "0.5.0", "1.2.0", dismissedRecommended = "1.2.0"))
        // una versión recomendada NUEVA vuelve a avisar
        assertEquals(UpdateNeed.Recommended("1.3.0"), AppUpdate.need("1.0.0", "0.5.0", "1.3.0", dismissedRecommended = "1.2.0"))
    }

    @Test fun refreshGating() {
        val h = 3_600_000L
        assertTrue(AppUpdate.shouldRefresh(null, 1000, 4 * h))                 // nunca se obtuvo
        assertFalse(AppUpdate.shouldRefresh(1000, 1000 + 3 * h, 4 * h))        // reciente
        assertTrue(AppUpdate.shouldRefresh(1000, 1000 + 4 * h, 4 * h))         // venció
        assertTrue(AppUpdate.shouldRefresh(10 * h, 5 * h, 4 * h))              // el reloj retrocedió
    }

    @Test fun announcementVisibility() {
        assertTrue(AppUpdate.announcementVisible("a1", null))
        assertTrue(AppUpdate.announcementVisible("a2", "a1"))
        assertFalse(AppUpdate.announcementVisible("a1", "a1"))
        assertFalse(AppUpdate.announcementVisible(null, null))
        assertFalse(AppUpdate.announcementVisible(" ", null))
    }

    @Test fun onlyCuadraLinksOpen() {
        assertTrue(AppUpdate.isAppLink("cuadra://inventario"))
        assertFalse(AppUpdate.isAppLink("https://example.com"))
        assertFalse(AppUpdate.isAppLink(null))
    }
}

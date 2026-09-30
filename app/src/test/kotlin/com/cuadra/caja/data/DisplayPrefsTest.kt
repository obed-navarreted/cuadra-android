package com.cuadra.caja.data

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.cuadra.caja.data.prefs.DisplayPrefs
import com.cuadra.caja.domain.FontSizeChoice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Preferencias de este teléfono: el ofrecimiento de WhatsApp al terminar la venta nace APAGADO y se recuerda; no toca el tamaño de letra. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class DisplayPrefsTest {
    private suspend fun waitFor(what: String, cond: () -> Boolean) {
        repeat(200) { if (cond()) return; delay(25) }
        throw AssertionError("no se cumplió: $what")
    }

    @Test fun whatsAppOfferIsOffByDefaultAndRemembered() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val prefs = DisplayPrefs(ApplicationProvider.getApplicationContext(), scope)
        assertFalse(prefs.offerWhatsApp.value)
        prefs.setOfferWhatsApp(true)
        waitFor("encendido") { prefs.offerWhatsApp.value }
        assertTrue(prefs.offerWhatsApp.value)
        // Otra instancia (como al reabrir la app) lo lee ya encendido.
        assertTrue(DisplayPrefs(ApplicationProvider.getApplicationContext(), scope).offerWhatsApp.value)
        prefs.setOfferWhatsApp(false)
        waitFor("apagado") { !prefs.offerWhatsApp.value }
        assertFalse(prefs.offerWhatsApp.value)
    }

    @Test fun fontChoiceIsIndependent() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val prefs = DisplayPrefs(ApplicationProvider.getApplicationContext(), scope)
        prefs.setFontSize(FontSizeChoice.LARGE)
        waitFor("letra") { prefs.fontSize.value == FontSizeChoice.LARGE }
        assertEquals(FontSizeChoice.LARGE, prefs.fontSize.value)
        assertFalse(prefs.offerWhatsApp.value)
    }

    @Test fun askDescriptionIsOffByDefaultAndRemembered() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val prefs = DisplayPrefs(ApplicationProvider.getApplicationContext(), scope)
        assertFalse(prefs.askDescription.value)
        prefs.setAskDescription(true)
        waitFor("encendido") { prefs.askDescription.value }
        // Otra instancia (como al reabrir la app) la lee ya encendida, y no toca las demás preferencias.
        val again = DisplayPrefs(ApplicationProvider.getApplicationContext(), scope)
        assertTrue(again.askDescription.value)
        assertFalse(again.offerWhatsApp.value)
        prefs.setAskDescription(false)
        waitFor("apagado") { !prefs.askDescription.value }
    }
}

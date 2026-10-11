package com.cuadra.caja.data.scanner

import android.view.KeyEvent
import com.cuadra.caja.domain.ScanLeak
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Lector tipo teclado (wedge) con un campo de texto enfocado. Antes un campo enfocado se quedaba con TODAS las teclas: en el buscador de la caja el código
 * se tecleaba ahí (basura en el buscador, sin pitido ni la ruta de la caja). Ahora, en un campo de BÚSQUEDA, una ráfaga rápida es una lectura (la primera tecla
 * ya pasó al campo y se limpia); en cualquier otro campo (nombre, nota, código del editor) el código se sigue tecleando.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class WedgeSearchFieldTest {
    private lateinit var hub: ScanHub
    private val received = mutableListOf<String>()
    private val cleaned = mutableListOf<String>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    @Before fun open() {
        hub = ScanHub(org.robolectric.RuntimeEnvironment.getApplication(), scope)
        hub.setUseReader(true)
        val until = System.currentTimeMillis() + 5_000
        while (!hub.settings.value.useReader && System.currentTimeMillis() < until) Thread.sleep(20)
        assertTrue(hub.settings.value.useReader)
        hub.claim { code, _ -> received += code }
        hub.leakFixer = { code -> cleaned += code }
    }

    @After fun close() { scope.coroutineContext[kotlinx.coroutines.Job]?.cancel() }

    private fun key(code: Int, at: Long) = KeyEvent(at, at, KeyEvent.ACTION_DOWN, code, 0)

    /** Una ráfaga de dígitos (5 ms entre teclas) y su Enter; devuelve lo que `dispatch` contestó a cada tecla. */
    private fun burst(digits: String, acceptingText: Boolean): List<Boolean> {
        var at = 10_000L
        val out = digits.map { d -> key(KeyEvent.KEYCODE_0 + (d - '0'), at.also { at += 5 }).let { hub.keys.dispatch(it, acceptingText) } }
        return out + hub.keys.dispatch(key(KeyEvent.KEYCODE_ENTER, at), acceptingText)
    }

    @Test fun inASearchFieldAFastBurstIsAScanAndTheLeakedFirstKeyIsCleaned() {
        hub.scanFieldFocused = true
        val answers = burst("7501000000017", acceptingText = true)
        assertFalse("la primera tecla pasa al campo (podría ser una persona)", answers.first())
        assertTrue("el resto de la ráfaga y el Enter se consumen", answers.drop(1).all { it })
        assertEquals(listOf("7501000000017"), received)
        assertEquals("el campo quita la primera tecla que ya había escrito", listOf("7501000000017"), cleaned)
    }

    @Test fun inAnyOtherTextFieldTheCodeIsStillTyped() {
        hub.scanFieldFocused = false
        val answers = burst("7501000000017", acceptingText = true)
        assertTrue(answers.none { it })
        assertTrue(received.isEmpty())
        assertTrue(cleaned.isEmpty())
    }

    @Test fun withNoFieldFocusedNothingLeaksIntoAnyField() {
        hub.scanFieldFocused = false
        val answers = burst("5901234123457", acceptingText = false)
        assertFalse(answers.first())
        assertEquals(listOf("5901234123457"), received)
        // El campo no se enteró de nada: no hay nada que limpiar.
        assertTrue(cleaned.isEmpty())
    }

    @Test fun slowTypingInTheSearchFieldIsNeverAScan() {
        hub.scanFieldFocused = true
        var at = 10_000L
        val answers = "cerveza".map { c -> hub.keys.dispatch(key(KeyEvent.KEYCODE_A + (c - 'a'), at.also { at += 250 }), true) }
        assertTrue(answers.none { it })
        assertTrue(received.isEmpty())
        assertNull(cleaned.firstOrNull())
    }

    @Test fun leakedFirstCharacterIsStrippedFromTheEndOfTheField() {
        assertEquals("", ScanLeak.strip("7", "7501000000017"))
        assertEquals("cer", ScanLeak.strip("cer7", "7501000000017"))
        // Si el campo no termina en esa tecla (nada se escribió), no se toca.
        assertEquals("cer", ScanLeak.strip("cer", "7501000000017"))
        assertEquals("77", ScanLeak.strip("777", "7501000000017"))
        assertEquals("x", ScanLeak.strip("x", ""))
    }

    // Más › Productos («Comprobar producto»): la pantalla reclama el lector; con el buscador enfocado (y el teclado abierto) la lectura llega a ella y no queda escrita en el buscador.
    @Test fun theProductManagementScreenClaimReceivesTheScanWhileItsSearchIsFocused() {
        val listScans = mutableListOf<String>()
        val claim = hub.claim { code, _ -> listScans += code }
        hub.scanFieldFocused = true
        val answers = burst("7501000000017", acceptingText = true)
        assertTrue(answers.drop(1).all { it })
        assertEquals(listOf("7501000000017"), listScans)
        assertEquals(listOf("7501000000017"), cleaned)
        // La caja (reclamo anterior) no recibe nada mientras la pantalla de productos manda; al cerrarla, vuelve a recibir.
        assertTrue(received.isEmpty())
        claim.close()
        Thread.sleep(400)
        burst("5901234123457", acceptingText = true)
        assertEquals(listOf("5901234123457"), received)
    }
}

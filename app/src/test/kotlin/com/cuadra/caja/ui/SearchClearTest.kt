package com.cuadra.caja.ui

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.cuadra.caja.ui.common.TAG_SEARCH_CLEAR
import com.cuadra.caja.ui.common.VoiceTextField
import com.cuadra.caja.ui.theme.CuadraTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** La ✕ «Borrar búsqueda» de los buscadores: con texto aparece, borra todo de un toque y el campo conserva el foco (el teclado sigue abierto). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h900dp-xhdpi", application = android.app.Application::class)
class SearchClearTest {
    @get:Rule val rule = createEmptyComposeRule()

    private fun text() = rule.onNode(hasSetTextAction()).fetchSemanticsNode().config.getOrNull(SemanticsProperties.EditableText)?.text.orEmpty()

    @Test fun textThenClearThenEmptyAndTheFieldKeepsTheFocus() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app.packageName, ComponentActivity::class.java.name))
        var typed = "start"
        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        scenario.onActivity {
            it.setContent {
                CuadraTheme {
                    var q by remember { mutableStateOf("") }
                    typed = q
                    VoiceTextField(q, { q = it }, singleLine = true, clearable = true, placeholder = { androidx.compose.material3.Text("Buscar") })
                }
            }
        }
        rule.waitForIdle()
        // Sin texto no hay ✕.
        assertEquals(0, rule.onAllNodes(hasTestTag(TAG_SEARCH_CLEAR)).fetchSemanticsNodes().size)
        rule.onNode(hasSetTextAction()).performClick()
        rule.onNode(hasSetTextAction()).performTextInput("cerveza victoria")
        rule.waitForIdle()
        assertEquals("cerveza victoria", text())
        rule.onNodeWithTag(TAG_SEARCH_CLEAR).assertExists()
        // Un toque: todo el texto fuera y el foco sigue en el campo.
        rule.onNodeWithTag(TAG_SEARCH_CLEAR).performClick()
        rule.waitForIdle()
        assertEquals("", text())
        rule.onNode(hasSetTextAction()).assertIsFocused()
        assertEquals(0, rule.onAllNodes(hasTestTag(TAG_SEARCH_CLEAR)).fetchSemanticsNodes().size)
        // Y se puede escribir otra búsqueda enseguida.
        rule.onNode(hasSetTextAction()).performTextInput("pan")
        rule.waitForIdle()
        assertEquals("pan", text())
        scenario.close()
    }
}

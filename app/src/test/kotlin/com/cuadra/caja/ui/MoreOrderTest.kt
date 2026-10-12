package com.cuadra.caja.ui

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.cuadra.caja.core.i18n.AppLanguage
import com.cuadra.caja.data.sync.SyncStatus
import com.cuadra.caja.ui.screens.MoreActions
import com.cuadra.caja.ui.screens.MoreScreen
import com.cuadra.caja.ui.theme.CuadraTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** «Más»: Productos es la PRIMERA opción de la lista (después de las tarjetas del negocio y de la sincronización), para dueño, admin y cajero que edita productos. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "es-w411dp-h2400dp-xhdpi", application = android.app.Application::class)
class MoreOrderTest {
    @get:Rule val rule = createEmptyComposeRule()

    private fun top(text: String) = rule.onNode(hasText(text, substring = false)).fetchSemanticsNode().boundsInRoot.top

    private fun show(actions: MoreActions) {
        val app = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app.packageName, ComponentActivity::class.java.name))
        ActivityScenario.launch(ComponentActivity::class.java).onActivity {
            it.setContent { CuadraTheme { MoreScreen("Negocio", "Persona", SyncStatus.IDLE, 0, 0, AppLanguage.SPANISH, 0, actions) } }
        }
        rule.waitForIdle()
    }

    private val go: () -> Unit = {}

    @Test fun ownerAndAdminSeeProductsBeforeEverythingElse() {
        show(MoreActions(onSettings = go, onTeam = go, onActivity = go, onTemplates = go, onProducts = go, onInventory = go, onPurchases = go, onSummary = go, onDailyClose = go, onNotifications = go, onHelp = go, onMyAccount = go))
        val products = top("Productos")
        for (other in listOf("Ajustes del negocio", "Equipo", "Resumen del negocio", "Notificaciones", "Ayuda y contacto")) {
            assertTrue("«Productos» va antes que «$other»", products < top(other))
        }
        // Inventario y Compras directamente debajo de Productos, antes que Ajustes.
        assertTrue(products < top("Inventario") && top("Inventario") < top("Ajustes del negocio"))
        rule.onNode(hasText("Productos y existencias", substring = false, ignoreCase = true)).assertExists()
    }

    @Test fun aCashierWhoEditsProductsAlsoSeesProductsFirst() {
        show(MoreActions(onProducts = go, onNotifications = go, onHelp = go, onMyAccount = go))
        assertTrue(top("Productos") < top("Notificaciones"))
        assertTrue(top("Productos") < top("Ayuda y contacto"))
    }

    @Test fun switchPersonButtonSitsInTheTopCardAndLocks() {
        var locks = 0
        show(MoreActions(onLock = { locks++ }, onProducts = go, onNotifications = go, onHelp = go, onMyAccount = go))
        rule.onNodeWithText("Cambiar de persona").assertIsDisplayed()
        assertTrue("el botón va dentro de la tarjeta de arriba, antes que Productos", top("Cambiar de persona") < top("Productos"))
        rule.onNodeWithText("Cambiar de persona").performClick()
        assertTrue("tocarlo bloquea a quien atiende (abre «¿Quién atiende?»)", locks == 1)
    }
}

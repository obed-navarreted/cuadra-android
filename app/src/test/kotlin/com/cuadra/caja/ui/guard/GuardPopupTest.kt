package com.cuadra.caja.ui.guard

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.cuadra.caja.data.local.ProductEntity
import com.cuadra.caja.domain.UndoEntry
import com.cuadra.caja.ui.CajaActions
import com.cuadra.caja.ui.CajaUi
import com.cuadra.caja.ui.PosTab
import com.cuadra.caja.ui.common.TAG_BOTTOM_BAR
import com.cuadra.caja.ui.common.TAG_KEY
import com.cuadra.caja.ui.common.TAG_OVERLAY
import com.cuadra.caja.ui.common.TAG_TOTAL_CARD
import com.cuadra.caja.ui.screens.CajaContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * El aviso «Deshacer» es FLOTANTE: al aparecer o cambiar no mueve NADA (ni la tarjeta del total, ni las 14 teclas, ni la barra inferior) en toda la matriz
 * letra × ancho × idioma, con los tres avisos (agregado, línea eliminada, recibo vaciado) y con el teclado del sistema.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h900dp-xhdpi", application = Application::class)
class GuardPopupTest {
    @get:Rule val rule = createEmptyComposeRule()

    private var cfg by mutableStateOf(GuardMatrix.FULL.first())
    private var shown by mutableStateOf<((CajaUi) -> CajaUi)?>(null)

    private fun boxes(root: SemanticsNode): Map<String, List<List<Int>>> {
        val out = mutableMapOf<String, MutableList<List<Int>>>()
        fun walk(n: SemanticsNode) {
            val tag = n.config.getOrNull(SemanticsProperties.TestTag)
            if (tag == TAG_TOTAL_CARD || tag == TAG_KEY || tag == TAG_BOTTOM_BAR) out.getOrPut(tag) { mutableListOf() } += listOf(n.positionInRoot.x.toInt(), n.positionInRoot.y.toInt(), n.size.width, n.size.height)
            n.children.forEach(::walk)
        }
        walk(root)
        return out
    }

    private fun overlay(root: SemanticsNode): SemanticsNode? {
        var found: SemanticsNode? = null
        fun walk(n: SemanticsNode) { if (n.config.getOrNull(SemanticsProperties.TestTag) == TAG_OVERLAY) found = n; n.children.forEach(::walk) }
        walk(root)
        return found
    }

    @Test fun theFloatingUndoNeverMovesNorCoversTheTotalCardTheKeysOrTheBottomBar() {
        val entries = listOf(Fixtures.undoAdded, Fixtures.undoDeleted, UndoEntry.Cleared(Fixtures.cart15)).map { e -> e::class.simpleName!! to { u: CajaUi -> u.copy(undoStack = listOf(e), undoShown = true) } }
        check(entries, "Avisos flotantes")
    }

    /** Los avisos de impresión («Impreso», «Sin impresora conectada», «No se pudo imprimir») son flotantes igual: no mueven el total, las teclas ni la barra, y quedan justo debajo del total. */
    @Test fun thePrintNoticesNeverMoveNorCoverTheTotalCardTheKeysOrTheBottomBar() {
        val entries = com.cuadra.caja.domain.printing.PrintNotice.entries.map { n ->
            "aviso de impresión $n" to { u: CajaUi -> u.copy(printer = com.cuadra.caja.domain.printing.PrinterBadge.CONNECTED, printNotice = n) }
        }
        check(entries, "Avisos de impresión", withPrinterBadge = true)
    }

    private fun check(entries: List<Pair<String, (CajaUi) -> CajaUi>>, title: String, withPrinterBadge: Boolean = false) {
        val app = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app.packageName, ComponentActivity::class.java.name))
        val density = app.resources.displayMetrics.density
        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        val ui = CajaUi(cart = Fixtures.cart15, entry = Fixtures.entry('1', '2', '5'), description = "", tab = PosTab.MANUAL)
        scenario.onActivity { a ->
            a.setContent {
                GuardEnvironment(cfg) {
                    CajaContent(
                        shown?.invoke(ui) ?: ui, listOf(PosTab.MANUAL, PosTab.PRODUCTS), Fixtures.pane, Fixtures.parked, Fixtures.HUGE,
                        Fixtures.BUSINESS_NAME, "Kevin", object : CajaActions {}, {},
                    )
                }
            }
        }
        val problems = mutableListOf<String>()
        val configs = GuardMatrix.FULL + GuardMatrix.KEYBOARD
        for (c in configs) {
            cfg = c
            shown = if (withPrinterBadge) ({ u -> u.copy(printer = com.cuadra.caja.domain.printing.PrinterBadge.CONNECTED) }) else null
            rule.waitForIdle()
            val before = boxes(rule.onRoot(useUnmergedTree = true).fetchSemanticsNode())
            for ((label, change) in entries) {
                shown = change
                rule.waitForIdle()
                val root = rule.onRoot(useUnmergedTree = true).fetchSemanticsNode()
                val after = boxes(root)
                if (before != after) problems += "[$c] $label: cambió el diseño (antes=$before, después=$after)"
                val pill = overlay(root)
                if (pill == null) problems += "[$c] $label: no se dibuja el aviso"
                else {
                    val widthDp = pill.size.width / density
                    if (widthDp > c.widthDp * 0.9f + 1f) problems += "[$c] aviso de ${"%.0f".format(widthDp)} dp (> 90 % de ${c.widthDp})"
                    // Con letra normal cabe en una sola línea baja: 48 dp de área tocable, 36 dp de píldora visible.
                    if (!withPrinterBadge && c.fontScale <= 1.0f && pill.size.height / density > 48.5f) problems += "[$c] aviso de ${"%.0f".format(pill.size.height / density)} dp de alto"
                    // El aviso va justo DEBAJO de la tarjeta del total (nunca la tapa): su borde superior está entre el borde inferior del total y 16 dp más abajo.
                    val card = after[TAG_TOTAL_CARD]?.singleOrNull()
                    if (card == null) problems += "[$c] no hay tarjeta del total"
                    else {
                        val cardBottom = (card[1] + card[3]) / density
                        val y = pill.positionInRoot.y / density
                        if (y < cardBottom - 0.5f || y > cardBottom + 16f) problems += "[$c] el aviso no está justo debajo del total (y=$y dp, el total termina en $cardBottom dp)"
                    }
                }
                shown = if (withPrinterBadge) ({ u -> u.copy(printer = com.cuadra.caja.domain.printing.PrinterBadge.CONNECTED) }) else null
                rule.waitForIdle()
            }
        }
        assertTrue("$title:\n" + problems.take(60).joinToString("\n"), problems.isEmpty())
        assertEquals(0, problems.size)
    }
}

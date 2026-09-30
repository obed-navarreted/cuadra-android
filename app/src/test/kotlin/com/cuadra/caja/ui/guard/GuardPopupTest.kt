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
import com.cuadra.caja.ui.SaleNoticeUi
import com.cuadra.caja.ui.screens.CajaContent
import com.cuadra.caja.ui.screens.TAG_REGISTER_TABS
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
 * El aviso «Deshacer» es FLOTANTE: al aparecer o cambiar no mueve NADA (ni la tarjeta del total, ni las 14 teclas, ni la barra inferior, ni las pestañas) en toda
 * la matriz letra × ancho × idioma, en «Manual» y en «Productos», con los tres avisos (agregado, línea eliminada, recibo vaciado), con el teclado del sistema y en
 * el teléfono bajo. Y NUNCA tapa la fila de pestañas, la tarjeta del total, una tecla ni la barra inferior (va en el hueco libre entre las pestañas y la
 * calculadora o, si no hay, sobre el encabezado).
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
            if (tag == TAG_TOTAL_CARD || tag == TAG_KEY || tag == TAG_BOTTOM_BAR || tag == TAG_REGISTER_TABS) out.getOrPut(tag) { mutableListOf() } += listOf(n.positionInRoot.x.toInt(), n.positionInRoot.y.toInt(), n.size.width, n.size.height)
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

    /** El aviso de la venta cobrada (vuelto / «Anular», destacado) también es flotante: no mueve ni tapa nada. La altura puede crecer (letra grande, montos enormes). */
    @Test fun theSaleNoticeNeverMovesNorCoversTheTotalCardTheKeysOrTheBottomBar() {
        val now = System.currentTimeMillis()
        val entries = listOf(
            "vuelto" to { u: CajaUi -> u.copy(saleNotice = SaleNoticeUi("s1", Fixtures.BIG, 2_750, now)) },
            "vuelto enorme" to { u: CajaUi -> u.copy(saleNotice = SaleNoticeUi("s1", Fixtures.BIG, Fixtures.HUGE, now)) },
            "sin vuelto" to { u: CajaUi -> u.copy(saleNotice = SaleNoticeUi("s1", Fixtures.HUGE, 0, now)) },
            "anulada" to { u: CajaUi -> u.copy(saleNotice = SaleNoticeUi("s1", Fixtures.BIG, 0, now, undone = true)) },
        )
        check(entries, "Aviso de venta cobrada", tallNotice = true)
    }

    /** Los avisos de impresión («Impreso», «Sin impresora conectada», «No se pudo imprimir») son flotantes igual: no mueven ni tapan el total, las pestañas, las teclas ni la barra. */
    @Test fun thePrintNoticesNeverMoveNorCoverTheTotalCardTheKeysOrTheBottomBar() {
        val entries = com.cuadra.caja.domain.printing.PrintNotice.entries.map { n ->
            "aviso de impresión $n" to { u: CajaUi -> u.copy(printer = com.cuadra.caja.domain.printing.PrinterBadge.CONNECTED, printNotice = n) }
        }
        check(entries, "Avisos de impresión", withPrinterBadge = true)
    }

    private var tab by mutableStateOf(PosTab.MANUAL)

    private fun check(entries: List<Pair<String, (CajaUi) -> CajaUi>>, title: String, withPrinterBadge: Boolean = false, tallNotice: Boolean = false) {
        val app = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app.packageName, ComponentActivity::class.java.name))
        val density = app.resources.displayMetrics.density
        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        val ui = CajaUi(cart = Fixtures.cart15, entry = Fixtures.entry('1', '2', '5'), description = "", tab = PosTab.MANUAL)
        scenario.onActivity { a ->
            a.setContent {
                GuardEnvironment(cfg) {
                    CajaContent(
                        (shown?.invoke(ui) ?: ui).copy(tab = tab), listOf(PosTab.MANUAL, PosTab.PRODUCTS), Fixtures.pane, Fixtures.parked, Fixtures.HUGE,
                        Fixtures.BUSINESS_NAME, "Kevin", object : CajaActions {}, {},
                    )
                }
            }
        }
        val problems = mutableListOf<String>()
        val configs = GuardMatrix.FULL + GuardMatrix.KEYBOARD + GuardMatrix.SMALL_PHONE
        var missing = 0
        val missingCfgs = sortedSetOf<String>()
        for (t in PosTab.entries) for (c in configs) {
            tab = t
            cfg = c
            shown = if (withPrinterBadge) ({ u -> u.copy(printer = com.cuadra.caja.domain.printing.PrinterBadge.CONNECTED) }) else null
            rule.waitForIdle()
            val before = boxes(rule.onRoot(useUnmergedTree = true).fetchSemanticsNode())
            for ((label, change) in entries) {
                shown = change
                rule.waitForIdle()
                val root = rule.onRoot(useUnmergedTree = true).fetchSemanticsNode()
                val after = boxes(root)
                if (before != after) problems += "[$c · $t] $label: cambió el diseño (antes=$before, después=$after)"
                val pill = overlay(root)
                // Sin ningún lugar seguro (un extremo: sin encabezado ni hueco) el aviso no se compone; con letra hasta 1.3× y sin teclado siempre tiene lugar.
                if (pill == null) { missing++; missingCfgs += "$c · $t"; if (c.effectiveScale <= 1.3f && c.imeDp == 0) problems += "[$c · $t] $label: no se dibuja el aviso" }
                else {
                    val widthDp = pill.size.width / density
                    if (widthDp > c.widthDp * 0.9f + 1f) problems += "[$c] aviso de ${"%.0f".format(widthDp)} dp (> 90 % de ${c.widthDp})"
                    // Con letra normal cabe en una sola línea baja: 48 dp de área tocable, 36 dp de píldora visible.
                    if (!withPrinterBadge && !tallNotice && c.fontScale <= 1.0f && pill.size.height / density > 48.5f) problems += "[$c] aviso de ${"%.0f".format(pill.size.height / density)} dp de alto"
                    val px = pill.positionInRoot.x; val py = pill.positionInRoot.y
                    val p = listOf(px, py, px + pill.size.width, py + pill.size.height)
                    if (p[1] < -0.5f || p[3] > c.heightDp * density + 0.5f) problems += "[$c · $t] $label: el aviso se sale de la ventana"
                    // Nunca encima de la fila de pestañas, el total, una tecla o la barra inferior.
                    for ((tag, rects) in after) for (r in rects) {
                        val ox = minOf(p[2], (r[0] + r[2]).toFloat()) - maxOf(p[0], r[0].toFloat())
                        val oy = minOf(p[3], (r[1] + r[3]).toFloat()) - maxOf(p[1], r[1].toFloat())
                        if (ox > 0.5f && oy > 0.5f) { problems += "[$c · $t] $label: el aviso tapa «$tag» (aviso y=${p[1] / density}..${p[3] / density} dp, $tag y=${r[1] / density}..${(r[1] + r[3]) / density} dp)"; break }
                    }
                }
                shown = if (withPrinterBadge) ({ u -> u.copy(printer = com.cuadra.caja.domain.printing.PrinterBadge.CONNECTED) }) else null
                rule.waitForIdle()
            }
        }
        println("$title: sin lugar seguro para el aviso en $missing de ${2 * configs.size * entries.size} casos (extremos): ${missingCfgs.joinToString(" | ")}")
        assertTrue("$title:\n" + problems.take(60).joinToString("\n"), problems.isEmpty())
        assertEquals(0, problems.size)
    }
}

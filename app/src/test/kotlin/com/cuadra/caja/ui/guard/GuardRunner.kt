package com.cuadra.caja.ui.guard

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import org.robolectric.Shadows.shadowOf
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.semantics.getOrNull
import java.io.File

/** Una pantalla (o diálogo) con datos de ejemplo y la matriz de ajustes con la que se revisa. */
class GuardCase(
    val name: String, val matrix: List<GuardCfg> = GuardMatrix.REDUCED, val expectKeys: Int? = null,
    /** La tarjeta del total de la caja debe verse entera y sin nada encima (regla J). Por omisión, en todos los casos con calculadora. */
    val expectTotal: Boolean = expectKeys != null,
    /** El número del botón «Recibo · N» debe verse entero (regla K); N = líneas del carrito. */
    val expectBadge: Int? = null,
    /** La tira de la última línea debe dibujarse (regla L). Aunque sea false, si se dibuja se revisa igual. */
    val expectStrip: Boolean = false,
    /** Con qué ajustes debe dibujarse sí o sí (en los extremos de poco alto o de letra enorme la tira cede su lugar a propósito). */
    val stripWhen: (GuardCfg) -> Boolean = STRIP_NORMAL,
    /** Elementos (por `testTag`) que deben dibujarse una vez y enteros dentro de la ventana (p. ej. «Enviar a caja» en la barra). */
    val expectTags: List<String> = emptyList(),
    val content: @Composable () -> Unit,
)

/** Cuándo la tira de la última línea debe verse sí o sí: letra efectiva hasta 1.3×, sin teclado y en una ventana de 700 dp o más (más allá puede ceder su lugar). */
val STRIP_NORMAL: (GuardCfg) -> Boolean = { it.effectiveScale <= 1.3f && it.imeDp == 0 && it.windowDp >= 700 }

/** Teclas de la calculadora de la caja: 0-9, «.», «×», «⌫» y «Agregar». */
const val CAJA_KEYS = 14

/**
 * Dibuja cada caso con cada combinación de ajustes (letra × ancho × idioma) en UNA sola composición (cambia el estado, no recrea la actividad)
 * y junta todos los problemas de `LayoutGuard`. El reporte completo queda en build/reports/layout-guard/<grupo>.txt.
 */
class GuardRunner(private val rule: ComposeTestRule, private val group: String) {
    private var current by mutableStateOf<GuardCase?>(null)
    private var cfg by mutableStateOf(GuardMatrix.FULL.first())
    private var started = false
    private var scenario: ActivityScenario<ComponentActivity>? = null

    val findings = mutableListOf<Finding>()
    val timings = mutableListOf<String>()

    fun run(cases: List<GuardCase>) {
        if (!started) {
            started = true
            // ui-test-manifest solo se puede declarar en debugImplementation (entraría al APK), así que la actividad de prueba se registra aquí.
            val app = ApplicationProvider.getApplicationContext<Application>()
            shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app.packageName, ComponentActivity::class.java.name))
            scenario = ActivityScenario.launch(ComponentActivity::class.java)
            scenario!!.onActivity { it.setContent { current?.let { c -> key(c.name) { GuardEnvironment(cfg) { c.content() } } } } }
        }
        val density = androidx.test.core.app.ApplicationProvider.getApplicationContext<Application>().resources.displayMetrics.density
        for (case in cases) {
            val t0 = System.nanoTime()
            current = case
            for (c in case.matrix) {
                cfg = c
                rule.waitForIdle()
                val root = rule.onRoot(useUnmergedTree = true).fetchSemanticsNode()
                findings += LayoutGuard.inspect(root, case.name, c, density)
                if (case.expectTotal) findings += LayoutGuard.inspectTotal(root, case.name, c, density)
                findings += LayoutGuard.inspectStrip(root, case.name, c, density, case.expectStrip && case.stripWhen(c))
                case.expectBadge?.let { findings += LayoutGuard.inspectReceiptBadge(root, case.name, c, density, it) }
                for (tag in case.expectTags) {
                    val hits = mutableListOf<androidx.compose.ui.semantics.SemanticsNode>()
                    fun find(n: androidx.compose.ui.semantics.SemanticsNode) { if (n.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.TestTag) == tag) hits += n; n.children.forEach(::find) }
                    find(root)
                    if (hits.size != 1) { findings += Finding(case.name, c, GuardRule.B_OUTSIDE_SCREEN, tag, "se dibuja ${hits.size} veces y se esperaba 1"); continue }
                    val b = hits.single().boundsInWindow
                    val w = c.widthDp * density + 1.5f
                    val h = c.heightDp * density + 1.5f
                    if (b.left < -1.5f || b.top < -1.5f || b.right > w || b.bottom > h || b.width <= 0f || b.height <= 0f) findings += Finding(case.name, c, GuardRule.B_OUTSIDE_SCREEN, tag, "no queda entero dentro de la ventana ($b)")
                }
                // Una tecla que ni se dibuja no la ve ninguna regla de posición: se cuentan.
                case.expectKeys?.let { expected ->
                    var keys = 0
                    fun walk(n: androidx.compose.ui.semantics.SemanticsNode) { if (n.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.TestTag) == com.cuadra.caja.ui.common.TAG_KEY) keys++; n.children.forEach(::walk) }
                    walk(root)
                    if (keys != expected) findings += Finding(case.name, c, GuardRule.I_KEY_HIDDEN, "calculadora", "se dibujan $keys teclas y se esperaban $expected")
                }
            }
            timings += "${case.name}: ${case.matrix.size} combinaciones, ${(System.nanoTime() - t0) / 1_000_000} ms"
        }
    }

    /** Escribe el reporte y falla con la lista completa si hubo problemas. */
    fun assertClean() {
        val dir = File("build/reports/layout-guard").apply { mkdirs() }
        val byRule = findings.groupBy { it.rule }.entries.joinToString("\n") { (r, l) -> "  ${r.code} (${r.meaning}): ${l.size}" }
        val text = buildString {
            appendLine("Guardia de diseño, grupo $group: ${findings.size} problema(s)")
            if (findings.isNotEmpty()) appendLine(byRule)
            appendLine()
            findings.groupBy { it.screen }.forEach { (screen, list) ->
                appendLine("== $screen (${list.size})")
                list.forEach { appendLine("  " + it.toString().removePrefix("[$screen | ")) }
            }
            appendLine()
            appendLine("Tiempos:")
            timings.forEach { appendLine("  $it") }
        }
        File(dir, "$group.txt").writeText(text)
        check(findings.isEmpty()) { text.lines().take(400).joinToString("\n") }
    }
}

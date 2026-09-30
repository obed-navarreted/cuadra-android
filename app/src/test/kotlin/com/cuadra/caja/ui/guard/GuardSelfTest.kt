package com.cuadra.caja.ui.guard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cuadra.caja.ui.common.TAG_BOTTOM_BAR
import com.cuadra.caja.ui.common.TAG_KEY
import com.cuadra.caja.ui.common.TAG_TOTAL_CARD
import com.cuadra.caja.ui.common.TAG_PINNED_ACTION
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * La guardia de la guardia: dibuja a propósito pantallas MAL hechas y comprueba que cada regla las detecta.
 * Si alguien rompe `LayoutGuard`, esta prueba falla en vez de dejar pasar recortes en silencio.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h900dp-xhdpi", application = android.app.Application::class)
class GuardSelfTest {
    @get:Rule val rule = createEmptyComposeRule()

    private val one = listOf(GuardCfg(1.0f, 320, "es"))

    @Test fun everyRuleCatchesItsBug() {
        val runner = GuardRunner(rule, "self")
        runner.run(
            listOf(
                GuardCase("A recorte", one) { Box(Modifier.width(60.dp)) { Text("Una frase larga que no cabe", maxLines = 1, overflow = TextOverflow.Clip) } },
                GuardCase("A elipsis", one) { Box(Modifier.width(60.dp)) { Text("Una frase larga que no cabe", maxLines = 1, overflow = TextOverflow.Ellipsis) } },
                GuardCase("D palabra partida", one) { Box(Modifier.width(60.dp)) { Text("Refrigeradoras") } },
                GuardCase("B fuera de pantalla", one) { Row { Box(Modifier.requiredSize(200.dp).background(Color.Red)); Box(Modifier.requiredSize(200.dp).clickable {}) { Text("Botón afuera") } } },
                GuardCase("C tocable chico", one) { Box(Modifier.size(30.dp).clickable {}) { Text("x") } },
                GuardCase("E traslape", one) { Box { Box(Modifier.size(80.dp).clickable {}) { Text("uno") }; Box(Modifier.offset(40.dp, 10.dp).size(80.dp).clickable {}) { Text("dos") } } },
                GuardCase("F recortado por contenedor", one) { Box(Modifier.height(10.dp).width(100.dp).clipToBounds()) { Text("Texto alto", Modifier.requiredHeight(40.dp), maxLines = 1) } },
                GuardCase("I tecla tapada por la barra", one) { Box { Box(Modifier.offset(0.dp, 300.dp).size(60.dp).testTag(TAG_KEY).clickable {}) { Text("7") }; Box(Modifier.offset(0.dp, 280.dp).size(200.dp, 60.dp).testTag(TAG_BOTTOM_BAR)) { Text("Barra") } } },
                GuardCase("I tecla fuera de la ventana", one) { Box { Box(Modifier.offset(0.dp, 700.dp).size(60.dp).testTag(TAG_KEY).clickable {}) { Text("0") } } },
                GuardCase("I tecla recortada por una zona con scroll", one) { Column(Modifier.height(50.dp).verticalScroll(rememberScrollState())) { Box(Modifier.size(60.dp).offset(0.dp, 30.dp).testTag(TAG_KEY).clickable {}) { Text("8") }; Box(Modifier.size(60.dp).testTag(TAG_KEY).clickable {}) { Text("9") } } },
                GuardCase("I faltan teclas", one, expectKeys = CAJA_KEYS) { Box { Box(Modifier.size(60.dp).testTag(TAG_KEY).clickable {}) { Text("1") } } },
                GuardCase("J total tapado por una tecla", one, expectTotal = true) { Box { Box(Modifier.size(200.dp, 60.dp).testTag(TAG_TOTAL_CARD)) { Text("C$ 1.00") }; Box(Modifier.offset(0.dp, 20.dp).size(60.dp).testTag(TAG_KEY).clickable {}) { Text("7") } } },
                GuardCase("J total tapado por la barra", one, expectTotal = true) { Box { Box(Modifier.size(200.dp, 60.dp).testTag(TAG_TOTAL_CARD)) { Text("C$ 1.00") }; Box(Modifier.offset(0.dp, 40.dp).size(200.dp, 60.dp).testTag(TAG_BOTTOM_BAR)) { Text("Barra") } } },
                GuardCase("J total fuera de la ventana", one, expectTotal = true) { Box { Box(Modifier.offset(0.dp, 700.dp).size(200.dp, 60.dp).testTag(TAG_TOTAL_CARD)) { Text("C$ 1.00") } } },
                GuardCase("J total recortado por una zona con scroll", one, expectTotal = true) { Column(Modifier.height(50.dp).verticalScroll(rememberScrollState())) { Box(Modifier.size(200.dp, 60.dp).offset(0.dp, 30.dp).testTag(TAG_TOTAL_CARD)) { Text("C$ 1.00") }; Box(Modifier.size(60.dp)) } },
                GuardCase("J no hay tarjeta del total", one, expectTotal = true) { Box { Text("sin tarjeta") } },
                GuardCase("J total bien puesto (no debe fallar)", one, expectTotal = true) { Column { Box(Modifier.size(200.dp, 60.dp).testTag(TAG_TOTAL_CARD)) { Text("C$ 1.00") }; Box(Modifier.size(60.dp).testTag(TAG_KEY).clickable {}) { Text("7") } } },
                GuardCase("K número del recibo cortado por su botón", one, expectBadge = 999) { Box(Modifier.width(30.dp).height(48.dp).clipToBounds().clickable {}) { Text("999", Modifier.requiredWidth(60.dp).testTag(com.cuadra.caja.ui.common.TAG_RECEIPT_BADGE), maxLines = 1) } },
                GuardCase("K número del recibo que falta", one, expectBadge = 3) { Box { Text("Recibo") } },
                GuardCase("K número del recibo bien puesto (no debe fallar)", one, expectBadge = 15) { Box(Modifier.width(120.dp).height(48.dp).clickable {}) { com.cuadra.caja.ui.common.Text("15", Modifier.testTag(com.cuadra.caja.ui.common.TAG_RECEIPT_BADGE), maxLines = 1, softWrap = false, minScale = 1f) } },
                GuardCase("G acción fija dentro de scroll", one) { Column(Modifier.verticalScroll(rememberScrollState())) { Box(Modifier.testTag(TAG_PINNED_ACTION).height(60.dp).width(100.dp).clickable {}) { Text("Guardar") } } },
            ),
        )
        fun found(screen: String, rule: GuardRule) = runner.findings.any { it.screen == screen && it.rule == rule }
        assertTrue("A recorte:\n" + runner.findings.joinToString("\n"), found("A recorte", GuardRule.A_TEXT_CLIPPED))
        assertTrue("A elipsis", found("A elipsis", GuardRule.A_TEXT_CLIPPED))
        assertTrue("D", found("D palabra partida", GuardRule.D_WORD_SPLIT))
        assertTrue("B", found("B fuera de pantalla", GuardRule.B_OUTSIDE_SCREEN))
        assertTrue("C", found("C tocable chico", GuardRule.C_TOUCH_TARGET))
        assertTrue("E", found("E traslape", GuardRule.E_OVERLAP))
        assertTrue("F", found("F recortado por contenedor", GuardRule.F_CLIPPED_BY_PARENT))
        assertTrue("I tapada", found("I tecla tapada por la barra", GuardRule.I_KEY_HIDDEN))
        assertTrue("I fuera", found("I tecla fuera de la ventana", GuardRule.I_KEY_HIDDEN))
        assertTrue("I recortada", found("I tecla recortada por una zona con scroll", GuardRule.I_KEY_HIDDEN))
        assertTrue("I faltan", found("I faltan teclas", GuardRule.I_KEY_HIDDEN))
        assertTrue("J tecla", found("J total tapado por una tecla", GuardRule.J_TOTAL_HIDDEN))
        assertTrue("J barra", found("J total tapado por la barra", GuardRule.J_TOTAL_HIDDEN))
        assertTrue("J fuera", found("J total fuera de la ventana", GuardRule.J_TOTAL_HIDDEN))
        assertTrue("J recortado", found("J total recortado por una zona con scroll", GuardRule.J_TOTAL_HIDDEN))
        assertTrue("J falta", found("J no hay tarjeta del total", GuardRule.J_TOTAL_HIDDEN))
        assertTrue("J bien puesto no debe fallar: " + runner.findings.filter { it.screen.startsWith("J total bien") }, runner.findings.none { it.screen.startsWith("J total bien") && it.rule == GuardRule.J_TOTAL_HIDDEN })
        assertTrue("K cortado", found("K número del recibo cortado por su botón", GuardRule.K_RECEIPT_BADGE))
        assertTrue("K falta", found("K número del recibo que falta", GuardRule.K_RECEIPT_BADGE))
        assertTrue("K bien puesto no debe fallar: " + runner.findings.filter { it.screen.startsWith("K número del recibo bien") }, runner.findings.none { it.screen.startsWith("K número del recibo bien") && it.rule == GuardRule.K_RECEIPT_BADGE })
        assertTrue("G", found("G acción fija dentro de scroll", GuardRule.G_PINNED_ACTION))
    }
}

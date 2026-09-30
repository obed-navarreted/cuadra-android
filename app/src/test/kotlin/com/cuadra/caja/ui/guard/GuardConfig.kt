package com.cuadra.caja.ui.guard

import com.cuadra.caja.domain.AppFontScale
import com.cuadra.caja.domain.FontSizeChoice
import java.util.Locale

/**
 * Una combinación de ajustes del teléfono con la que se dibuja cada pantalla. `fontScale` es la letra del SISTEMA; `choice` la política de la app (Más › Tamaño de
 * letra): con `SYSTEM` («Como el sistema») la app usa toda la letra del teléfono; con `AUTO` (el valor por omisión real) se limita a 1.15×.
 */
data class GuardCfg(val fontScale: Float, val widthDp: Int, val language: String, val imeDp: Int = 0, val choice: FontSizeChoice = FontSizeChoice.SYSTEM, val windowDp: Int = GuardMatrix.HEIGHT_DP) {
    /** La escala que la app aplica de verdad con esta combinación. */
    val effectiveScale: Float get() = AppFontScale.effective(fontScale, choice)

    val locale: Locale get() = Locale.forLanguageTag(language)

    /** Alto que queda para la app: con el teclado abierto (`imeDp`) la ventana se achica igual que en el teléfono (imePadding / adjustResize). */
    val heightDp: Int get() = windowDp - imeDp
    override fun toString() = "letra ${fontScale}x" + (if (choice == FontSizeChoice.SYSTEM) " (como el sistema)" else " (${choice.name}, efectiva ${effectiveScale}x)") + ", ${widthDp}dp, $language" + (if (windowDp != GuardMatrix.HEIGHT_DP) ", alto ${windowDp}dp" else "") + if (imeDp > 0) ", teclado ${imeDp}dp" else ""
}

object GuardMatrix {
    /** Alto de la ventana simulada: un teléfono chico (320×640 es el peor caso real; se usa algo más alto para no penalizar dos veces). */
    const val HEIGHT_DP = 720

    val FONTS = listOf(0.85f, 1.0f, 1.3f, 1.5f, 2.0f)
    val WIDTHS = listOf(320, 360, 411)
    val LANGUAGES = listOf("es", "en")

    /** Alto del teclado del sistema con el que se prueban las pantallas que tienen campos. */
    const val IME_DP = 300

    /**
     * Teléfono BAJO (el contenido de la app, sin barra de secciones ni barras del sistema, mide 560 dp: un 360×640 real): la caja debe seguir mostrando el total entero
     * y la calculadora completa. Letra del sistema 1.0 / 1.5 / 2.0 (Como el sistema) y el extremo con la política por omisión.
     */
    const val SMALL_DP = 560
    val SMALL_PHONE: List<GuardCfg> = listOf(1.0f, 1.5f, 2.0f).flatMap { f -> listOf(320, 360).flatMap { w -> LANGUAGES.map { l -> GuardCfg(f, w, l, windowDp = SMALL_DP) } } } +
        listOf(320, 360).flatMap { w -> LANGUAGES.map { l -> GuardCfg(2.0f, w, l, choice = FontSizeChoice.AUTO, windowDp = SMALL_DP) } }

    /** Con el teclado abierto: los extremos de letra y ancho (los campos y el botón de guardar/cobrar deben seguir a la vista). */
    val KEYBOARD: List<GuardCfg> = listOf(1.0f, 1.5f, 2.0f).flatMap { f -> listOf(320, 411).flatMap { w -> listOf("es", "en").map { l -> GuardCfg(f, w, l, IME_DP) } } } +
        listOf(320, 411).flatMap { w -> listOf("es", "en").map { l -> GuardCfg(2.0f, w, l, IME_DP, FontSizeChoice.AUTO) } }

    /**
     * La política por omisión de la app (Automático: sigue al teléfono hasta 1.15×) con el teléfono en su extremo (2.0×): la app se ve a 1.15×. Los demás valores del sistema
     * dan la misma escala efectiva que ya cubre «Como el sistema» (0.85, 1.0), así que basta el extremo.
     */
    val AUTO_EXTREME: List<GuardCfg> = WIDTHS.flatMap { w -> LANGUAGES.map { l -> GuardCfg(2.0f, w, l, choice = FontSizeChoice.AUTO) } }

    /** Matriz completa (30 combinaciones): las pantallas más complejas. */
    val FULL: List<GuardCfg> = FONTS.flatMap { f -> WIDTHS.flatMap { w -> LANGUAGES.map { l -> GuardCfg(f, w, l) } } } + AUTO_EXTREME

    /**
     * Matriz reducida (12 combinaciones): los extremos de cada eje (letra 0.85/1.0/1.5/2.0 × ancho 320/411 × es/en, sin repetir combinaciones
     * que ya cubre la completa). Suficiente para pantallas simples: los recortes aparecen en los extremos.
     */
    val REDUCED: List<GuardCfg> = listOf(
        GuardCfg(1.0f, 411, "es"), GuardCfg(1.0f, 320, "en"), GuardCfg(1.5f, 360, "es"), GuardCfg(1.5f, 320, "en"),
        GuardCfg(2.0f, 411, "en"), GuardCfg(2.0f, 320, "es"), GuardCfg(2.0f, 320, "en"), GuardCfg(0.85f, 320, "es"),
        GuardCfg(1.3f, 360, "en"), GuardCfg(2.0f, 360, "es"),
        GuardCfg(2.0f, 320, "en", choice = FontSizeChoice.AUTO), GuardCfg(2.0f, 411, "es", choice = FontSizeChoice.AUTO),
    )
}

package com.cuadra.caja.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.cuadra.caja.domain.AppFontScale
import com.cuadra.caja.domain.FontSizeChoice

/**
 * Aplica en TODA la app la escala de letra efectiva (`AppFontScale.effective`): función de la escala del teléfono y de la elección de la persona.
 * Se pone una sola vez en la raíz (`MainActivity`, y `GuardEnvironment` en las pruebas); los diálogos, hojas y popups heredan el mismo `LocalDensity`.
 * El punto de partida es la escala del SISTEMA (`LocalConfiguration`), no el `LocalDensity` actual, así reaplicar no acumula recortes.
 */
@Composable
fun ProvideAppFontScale(choice: FontSizeChoice, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    val system = LocalConfiguration.current.fontScale
    val scale = AppFontScale.effective(system, choice)
    CompositionLocalProvider(LocalDensity provides Density(density.density, scale), content = content)
}

/** La escala de letra del teléfono (sin la política de la app). */
@Composable
fun systemFontScale(): Float = AppFontScale.sanitize(LocalConfiguration.current.fontScale)

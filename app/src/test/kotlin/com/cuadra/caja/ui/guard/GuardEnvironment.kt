package com.cuadra.caja.ui.guard

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.cuadra.caja.ui.common.LocalInlineDialogs
import com.cuadra.caja.ui.common.MoneyFormat
import com.cuadra.caja.ui.common.ProvideAppFontScale
import com.cuadra.caja.ui.common.ProvideMoney
import com.cuadra.caja.ui.theme.CuadraTheme

/**
 * Dibuja `content` como lo vería un teléfono con la letra, el ancho y el idioma de `cfg`: mismo tema, dinero de un negocio de Nicaragua (C$),
 * diálogos en línea con márgenes de ventana. La ventana real de la prueba es mayor; el contenido se limita a `cfg.widthDp × HEIGHT_DP`.
 */
@Composable
fun GuardEnvironment(cfg: GuardCfg, content: @Composable () -> Unit) {
    val base = LocalContext.current
    val baseConf = androidx.compose.ui.platform.LocalConfiguration.current
    val ctx = remember(base, baseConf, cfg) {
        val conf = Configuration(baseConf).apply {
            setLocale(cfg.locale)
            fontScale = cfg.fontScale
            screenWidthDp = cfg.widthDp
            screenHeightDp = cfg.heightDp
        }
        val localized = base.createConfigurationContext(conf)
        // ContextWrapper: sigue siendo "la actividad" para quien la busque (resultados de actividad, etc.) pero con textos del idioma pedido.
        object : android.content.ContextWrapper(base) {
            override fun getResources() = localized.resources
        }
    }
    // Letra del SISTEMA; `ProvideAppFontScale` (la misma pieza que `MainActivity`) aplica la política de la app encima, para pantallas, diálogos y hojas por igual.
    val density = Density(LocalDensity.current.density, cfg.fontScale)
    CompositionLocalProvider(
        LocalContext provides ctx,
        LocalConfiguration provides ctx.resources.configuration,
        LocalResources provides ctx.resources,
        LocalDensity provides density,
        LocalInlineDialogs provides true,
    ) {
        ProvideAppFontScale(cfg.choice) {
            CuadraTheme {
                ProvideMoney(MoneyFormat.of("NIO", "NI")) {
                    Box(Modifier.fillMaxSize()) { Box(Modifier.size(cfg.widthDp.dp, cfg.heightDp.dp)) { content() } }
                }
            }
        }
    }
}

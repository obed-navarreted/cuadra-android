package com.cuadra.caja.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.cuadra.caja.R

/** Tokens del diseño (PLAN.md 11.5). Mismos valores que diseno/pantallas/styles.css y web/src/styles/tokens.css. */
object CuadraColors {
    val Bg = Color(0xFFF4F1EA)
    val Surface = Color(0xFFFFFFFF)
    val Ink = Color(0xFF1A1916)
    val Ink2 = Color(0xFF4A463E)
    val Muted = Color(0xFF5E594F)
    val Line = Color(0xFFE6E1D6)
    val Soft = Color(0xFFEFEBE2)
    val Green = Color(0xFF1D6A4C)
    val GreenSoft = Color(0xFFE3EFE8)
    val Orange = Color(0xFFA8460F)
    val OrangeSoft = Color(0xFFF8E7DA)
    val Red = Color(0xFF9C2F22)
    val RedSoft = Color(0xFFF6E1DD)
    val WhatsApp = Color(0xFF1B7F4B)
}

// Fase 0: una sola familia (Manrope variable). Bricolage Grotesque para títulos se añade con las fuentes descargables.
// Manrope es una fuente variable: cada peso se pide como una variación del mismo archivo (API 26+, el mínimo de la app).
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun manrope(weight: FontWeight) = Font(R.font.manrope, weight, variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)))

private val Manrope = FontFamily(
    manrope(FontWeight.Normal), manrope(FontWeight.Medium), manrope(FontWeight.SemiBold), manrope(FontWeight.Bold), manrope(FontWeight.ExtraBold),
)

private val CuadraTypography = Typography(
    displayLarge = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 52.sp, letterSpacing = (-1.5).sp),
    headlineMedium = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, letterSpacing = (-0.5).sp),
    bodyLarge = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Normal, fontSize = 18.sp, lineHeight = 26.sp),
    bodyMedium = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Normal, fontSize = 15.sp),
    labelLarge = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 15.sp),
)

private val LightScheme = lightColorScheme(
    primary = CuadraColors.Green, onPrimary = Color.White,
    background = CuadraColors.Bg, onBackground = CuadraColors.Ink,
    surface = CuadraColors.Surface, onSurface = CuadraColors.Ink,
    surfaceVariant = CuadraColors.Soft, onSurfaceVariant = CuadraColors.Muted,
    outline = CuadraColors.Line, error = CuadraColors.Red,
)

@Composable
fun CuadraTheme(content: @Composable () -> Unit) {
    // Modo oscuro: fase posterior (PLAN.md 11.5); por ahora la app siempre usa la paleta clara.
    @Suppress("UNUSED_VARIABLE") val dark = isSystemInDarkTheme()
    MaterialTheme(colorScheme = LightScheme, typography = CuadraTypography, content = content)
}

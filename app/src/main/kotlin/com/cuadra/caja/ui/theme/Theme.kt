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

    /** Estados sobre fondos OSCUROS (la tarjeta del total, el aviso flotante): los colores de texto de arriba son muy oscuros ahí. */
    val OnInkGreen = Color(0xFF6FD4A0)
    val OnInkOrange = Color(0xFFF3A66B)
    val OnInkGrey = Color(0xFF9A958A)
}

// Fase 0: una sola familia (Manrope variable). Bricolage Grotesque para títulos se añade con las fuentes descargables.
// Manrope es una fuente variable: cada peso se pide como una variación del mismo archivo (API 26+, el mínimo de la app).
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun manrope(weight: FontWeight) = Font(R.font.manrope, weight, variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)))

private val Manrope = FontFamily(
    manrope(FontWeight.Normal), manrope(FontWeight.Medium), manrope(FontWeight.SemiBold), manrope(FontWeight.Bold), manrope(FontWeight.ExtraBold),
)

/**
 * Escala tipográfica (docs/notas/reglas-de-interfaz-app.md, «Escala tipográfica»). Está definida COMPLETA: ningún estilo cae en el valor por omisión de Material
 * (que es más grande). Con letra 1.0 el cuerpo mide 15/13.5 sp; los montos son lo más grande (30 sp) pero nunca gigantes. Interlineado ≈ 1.3×.
 * Todo tamaño de letra sale de aquí: nada de `fontSize = NN.sp` suelto en las pantallas.
 */
private fun style(weight: FontWeight, size: Float, line: Float, spacing: Float = 0f) =
    TextStyle(fontFamily = Manrope, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp, letterSpacing = spacing.sp)

internal val CuadraTypography = Typography(
    displayLarge = style(FontWeight.ExtraBold, 34f, 40f, -0.5f),   // marca (pantalla de entrada)
    displayMedium = style(FontWeight.ExtraBold, 30f, 34f, -0.5f),  // montos destacados: total de la caja, monto tecleado, deuda, vuelto
    displaySmall = style(FontWeight.ExtraBold, 28f, 34f, -0.3f),
    headlineLarge = style(FontWeight.ExtraBold, 26f, 32f, -0.3f),
    headlineMedium = style(FontWeight.ExtraBold, 22f, 28f, -0.3f), // títulos de pantalla, cifras de las teclas
    headlineSmall = style(FontWeight.ExtraBold, 18f, 24f, -0.2f),  // títulos de pantallas densas
    titleLarge = style(FontWeight.SemiBold, 20f, 26f),
    titleMedium = style(FontWeight.SemiBold, 16f, 22f),
    titleSmall = style(FontWeight.SemiBold, 14f, 19f),
    bodyLarge = style(FontWeight.Normal, 15f, 20f),                // texto por omisión (LocalTextStyle)
    bodyMedium = style(FontWeight.Normal, 13.5f, 18f),             // ayudas, detalles, texto secundario
    bodySmall = style(FontWeight.Normal, 12f, 16f),
    labelLarge = style(FontWeight.Bold, 14f, 18f),                 // botones, chips
    labelMedium = style(FontWeight.Bold, 12f, 16f),
    labelSmall = style(FontWeight.Bold, 11f, 14f),
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

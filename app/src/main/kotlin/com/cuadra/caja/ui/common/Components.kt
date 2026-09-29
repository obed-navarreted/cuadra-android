package com.cuadra.caja.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cuadra.caja.core.model.Currency
import com.cuadra.caja.core.model.Money
import com.cuadra.caja.ui.theme.CuadraColors
import java.util.Locale

/** Moneda y país del negocio: el formato del dinero sigue al país donde se vende, no al idioma de la persona (PLAN.md 7.2). */
data class MoneyFormat(val currency: Currency, val locale: Locale) {
    fun format(minor: Long): String = Money(minor).format(currency, locale)
    val decimals: Int get() = currency.decimals

    companion object {
        val Default = MoneyFormat(Currency.of("USD"), Locale.US)
        fun of(currencyCode: String, country: String) = MoneyFormat(Currency.of(currencyCode), Locale.Builder().setLanguage("es").setRegion(country).build())
    }
}

val LocalMoney = compositionLocalOf { MoneyFormat.Default }

@Composable
fun ProvideMoney(format: MoneyFormat, content: @Composable () -> Unit) = CompositionLocalProvider(LocalMoney provides format, content = content)

@Composable
fun money(minor: Long): String = LocalMoney.current.format(minor)

enum class ButtonKind { PRIMARY, DARK, OUTLINE, WHATSAPP, DANGER }

/** Botón de 48 dp como mínimo (56 por defecto): se usa con guantes y en pantallas de 4.7 pulgadas. */
@Composable
fun CuadraButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, kind: ButtonKind = ButtonKind.OUTLINE, enabled: Boolean = true, height: Int = 56) {
    val bg = when (kind) {
        ButtonKind.PRIMARY -> CuadraColors.Green
        ButtonKind.DARK -> CuadraColors.Ink
        ButtonKind.WHATSAPP -> CuadraColors.WhatsApp
        ButtonKind.DANGER -> CuadraColors.Red
        ButtonKind.OUTLINE -> CuadraColors.Surface
    }
    val fg = if (kind == ButtonKind.OUTLINE) CuadraColors.Ink else Color.White
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = modifier.heightIn(min = height.dp).clip(shape).background(if (enabled) bg else CuadraColors.Soft, shape)
            .then(if (kind == ButtonKind.OUTLINE) Modifier.border(BorderStroke(1.dp, CuadraColors.Line), shape) else Modifier)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (enabled) fg else CuadraColors.Muted, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
    }
}

@Composable
fun CuadraChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier = modifier.heightIn(min = 44.dp).background(if (selected) CuadraColors.Ink else CuadraColors.Surface, shape)
            .border(BorderStroke(1.dp, if (selected) CuadraColors.Ink else CuadraColors.Line), shape)
            .clickable(role = Role.Tab, onClick = onClick).padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (selected) CuadraColors.Bg else CuadraColors.Ink, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun CuadraCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Surface(
        modifier = modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier),
        shape = shape, color = CuadraColors.Surface, border = BorderStroke(1.dp, CuadraColors.Line),
    ) { Box(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) { content() } }
}

@Composable
fun Tag(text: String, color: Color, background: Color) {
    Box(Modifier.background(background, RoundedCornerShape(50)).padding(horizontal = 9.dp, vertical = 3.dp)) {
        Text(text, color = color, style = MaterialTheme.typography.labelLarge.copy(fontSize = androidx.compose.ui.unit.TextUnit(11f, androidx.compose.ui.unit.TextUnitType.Sp)))
    }
}

@Composable
fun Avatar(initials: String, selected: Boolean = false, size: Int = 44) {
    Box(
        Modifier.size(size.dp).background(if (selected) CuadraColors.Green else CuadraColors.Soft, CircleShape),
        contentAlignment = Alignment.Center,
    ) { Text(initials, color = if (selected) Color.White else CuadraColors.Ink, fontWeight = FontWeight.ExtraBold) }
}

/** Teclado numérico propio (sin teclado del sistema): teclas grandes y sin distraer con sugerencias. */
@Composable
fun Keypad(
    onDigit: (Char) -> Unit, onBackspace: () -> Unit, modifier: Modifier = Modifier,
    onDot: (() -> Unit)? = null, onTimes: (() -> Unit)? = null,
) {
    val backspace = "⌫"
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("789", "456", "123").forEachIndexed { i, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { c -> Key(c.toString(), Modifier.weight(1f)) { onDigit(c) } }
                when (i) {
                    0 -> Key(backspace, Modifier.weight(1f), description = "backspace") { onBackspace() }
                    1 -> if (onTimes != null) Key("×", Modifier.weight(1f), description = "times") { onTimes() } else Box(Modifier.weight(1f))
                    else -> if (onDot != null) Key(".", Modifier.weight(1f), description = "dot") { onDot() } else Box(Modifier.weight(1f))
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Key("0", Modifier.weight(3f)) { onDigit('0') }
            Box(Modifier.weight(1f))
        }
    }
}

@Composable
private fun Key(label: String, modifier: Modifier, description: String? = null, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = modifier.heightIn(min = 56.dp).background(CuadraColors.Surface, shape).clickable(role = Role.Button, onClick = onClick)
            .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier),
        contentAlignment = Alignment.Center,
    ) { Text(label, style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)) }
}

@Composable
fun SectionLabel(text: String) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelLarge.copy(fontSize = androidx.compose.ui.unit.TextUnit(12f, androidx.compose.ui.unit.TextUnitType.Sp)), color = CuadraColors.Muted,
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
}

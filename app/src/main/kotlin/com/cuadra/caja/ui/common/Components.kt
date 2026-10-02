package com.cuadra.caja.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
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

    /** Con el signo delante del símbolo en los negativos («-C$ 70.00»): las líneas de promoción del comprobante. */
    fun signed(minor: Long): String = if (minor < 0) "-" + format(-minor) else format(minor)
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

/**
 * Botón de 48 dp como mínimo (56 por defecto): se usa con guantes y en pantallas de 4.7 pulgadas. Crece en alto con la letra: el texto sigue
 * en hasta 2 líneas (por palabras) y, solo si una palabra no cabe, baja la letra hasta 70 %; nunca se recorta.
 */
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
        modifier = modifier.heightIn(min = height.coerceAtLeast(48).dp).defaultMinSize(minWidth = 48.dp).clip(shape).background(if (enabled) bg else CuadraColors.Soft, shape)
            .then(if (kind == ButtonKind.OUTLINE) Modifier.border(BorderStroke(1.dp, CuadraColors.Line), shape) else Modifier)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(horizontal = if (text.length <= 2) 4.dp else 12.dp, vertical = if (text.length <= 2) 0.dp else 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (enabled) fg else CuadraColors.Muted, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center, maxLines = 2, minScale = 0.7f)
    }
}

@Composable
fun CuadraChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, userContent: Boolean = false) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier = modifier.heightIn(min = 48.dp).defaultMinSize(minWidth = 48.dp).background(if (selected) CuadraColors.Ink else CuadraColors.Surface, shape)
            .border(BorderStroke(1.dp, if (selected) CuadraColors.Ink else CuadraColors.Line), shape)
            .clickable(role = Role.Tab, onClick = onClick).padding(horizontal = 14.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        // Una palabra nunca se parte en dos líneas ("Eng/lish" con letra grande): el chip baja de línea (ChipFlow), sigue en 2 líneas por palabras
        // y, solo si una palabra sola no cabe, baja la letra.
        // `userContent`: un nombre escrito por la persona (proveedor, cliente…) puede ser larguísimo: es de los pocos textos que terminan en «…» a propósito.
        Text(text, color = if (selected) CuadraColors.Bg else CuadraColors.Ink, style = MaterialTheme.typography.labelLarge, maxLines = if (userContent) 2 else 4, minScale = if (userContent) 1f else 0.7f, ellipsize = userContent, textAlign = TextAlign.Center)
    }
}

/** Acción de texto dentro de una fila o tarjeta («Editar», «Anular»): con al menos 48 x 48 dp de área tocable. */
@Composable
fun LinkAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = CuadraColors.Green, style: androidx.compose.ui.text.TextStyle = androidx.compose.material3.LocalTextStyle.current) {
    Box(modifier.heightIn(min = 48.dp).defaultMinSize(minWidth = 48.dp).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 6.dp), contentAlignment = Alignment.CenterStart) {
        Text(text, color = color, fontWeight = FontWeight.ExtraBold, maxLines = 2, minScale = 0.8f, style = style)
    }
}

@Composable
fun CuadraCard(
    modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, color: Color = CuadraColors.Surface, borderColor: Color = CuadraColors.Line,
    /** Margen interior: por omisión 14 x 12 dp; las listas densas (líneas del recibo) usan uno más chico. */
    padding: androidx.compose.foundation.layout.PaddingValues = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 12.dp),
    /** Pulsación larga (opcional; p. ej. «Marcar como frecuente»), con su nombre para accesibilidad. */
    onLongClick: (() -> Unit)? = null, onLongClickLabel: String? = null,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    @OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
    val click = when {
        onClick != null && onLongClick != null -> Modifier.heightIn(min = 48.dp).combinedClickable(role = Role.Button, onLongClickLabel = onLongClickLabel, onLongClick = onLongClick, onClick = onClick)
        onClick != null -> Modifier.heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onClick)
        else -> Modifier
    }
    Surface(
        modifier = modifier.fillMaxWidth().then(click),
        shape = shape, color = color, border = BorderStroke(1.dp, borderColor),
    ) { Box(Modifier.padding(padding)) { content() } }
}

@Composable
fun Tag(text: String, color: Color, background: Color) {
    Box(Modifier.background(background, RoundedCornerShape(50)).padding(horizontal = 9.dp, vertical = 3.dp)) {
        Text(text, color = color, style = MaterialTheme.typography.labelSmall, maxLines = 2, minScale = 0.8f)
    }
}

/** Etiquetas una junto a otra: si no caben, bajan de línea. */
@Composable
fun TagRow(modifier: Modifier = Modifier, content: @Composable () -> Unit) = ChipFlow(modifier, spacing = 6.dp, content = content)

@Composable
fun Avatar(initials: String, selected: Boolean = false, size: Int = 44, color: Color? = null) {
    Box(
        Modifier.size(size.dp).background(color ?: if (selected) CuadraColors.Green else CuadraColors.Soft, CircleShape),
        contentAlignment = Alignment.Center,
    ) { Text(initials, color = if (selected || color != null) Color.White else CuadraColors.Ink, fontWeight = FontWeight.ExtraBold, maxLines = 1, minScale = 0.4f, modifier = Modifier.padding(horizontal = 2.dp)) }
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
        modifier = modifier.heightIn(min = 52.dp).background(CuadraColors.Surface, shape).clickable(role = Role.Button, onClick = onClick)
            .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier),
        contentAlignment = Alignment.Center,
    ) { Text(label, style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold), maxLines = 1, minScale = 0.5f) }
}

@Composable
fun SectionLabel(text: String) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelMedium, color = CuadraColors.Muted,
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
}

/** Una entrada de «Más»: ícono + etiqueta (+ detalle debajo y una etiqueta a la derecha, p. ej. «3 nuevos»). */
data class MenuItem(@androidx.annotation.DrawableRes val icon: Int, val label: String, val onClick: () -> Unit, val detail: String? = null, val badge: String? = null, val danger: Boolean = false)

/**
 * Grupo de entradas con título: una tarjeta con filas de al menos 56 dp (ícono, etiqueta que baja de línea, flecha), separadas por una línea fina.
 * Toda la fila se toca; nunca se recorta la etiqueta.
 */
@Composable
fun MenuGroup(title: String?, items: List<MenuItem>, modifier: Modifier = Modifier) {
    if (items.isEmpty()) return
    MenuCard(title, modifier) {
        items.forEachIndexed { i, item ->
            if (i > 0) MenuDivider()
            MenuRow(item)
        }
    }
}

/** La tarjeta de un grupo de «Más» con su título; el contenido son filas ([MenuRow], [MenuDivider] o las propias). */
@Composable
fun MenuCard(title: String?, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (title != null) SectionLabel(title)
        Surface(Modifier.fillMaxWidth(), shape = shape, color = CuadraColors.Surface, border = BorderStroke(1.dp, CuadraColors.Line)) {
            Column(content = content)
        }
    }
}

@Composable
fun MenuDivider() = Box(Modifier.fillMaxWidth().height(1.dp).background(CuadraColors.Line))

@Composable
fun MenuRow(item: MenuItem) {
    val tint = if (item.danger) CuadraColors.Red else CuadraColors.Ink
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button, onClick = item.onClick).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        androidx.compose.material3.Icon(androidx.compose.ui.res.painterResource(item.icon), contentDescription = null, modifier = Modifier.size(24.dp), tint = tint)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(item.label, color = tint, fontWeight = FontWeight.Bold)
            item.badge?.let { TagRow { Tag(it, CuadraColors.Green, CuadraColors.GreenSoft) } }
            item.detail?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted) }
        }
        androidx.compose.material3.Icon(androidx.compose.ui.res.painterResource(com.cuadra.caja.R.drawable.ic_chevron_right), contentDescription = null, modifier = Modifier.size(20.dp), tint = CuadraColors.Muted)
    }
}

/** Barra de avance (uso de un plan): `percent` de 0 a 100, con su descripción para lectores de pantalla. Sin texto propio: la cifra va en una línea aparte. */
@Composable
fun MeterBar(percent: Int, color: Color, description: String, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(50)
    val p = percent.coerceIn(0, 100)
    Box(
        modifier.fillMaxWidth().height(10.dp).background(CuadraColors.Soft, shape).clip(shape)
            .semantics { contentDescription = description; progressBarRangeInfo = androidx.compose.ui.semantics.ProgressBarRangeInfo(p / 100f, 0f..1f) },
    ) {
        if (p > 0) Box(Modifier.fillMaxWidth(p / 100f).height(10.dp).background(color, shape))
    }
}

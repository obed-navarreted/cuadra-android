package com.cuadra.caja.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.cuadra.caja.R
import com.cuadra.caja.domain.AppFontScale
import com.cuadra.caja.domain.FontSizeChoice
import com.cuadra.caja.ui.common.CuadraButton
import com.cuadra.caja.ui.common.CuadraCard
import com.cuadra.caja.ui.common.MoneyText
import com.cuadra.caja.ui.common.ScreenFrame
import com.cuadra.caja.ui.common.SplitRow
import com.cuadra.caja.ui.common.Text
import com.cuadra.caja.ui.common.TitleBar
import com.cuadra.caja.ui.common.systemFontScale
import com.cuadra.caja.ui.theme.CuadraColors

/**
 * Más › Tamaño de letra: cinco opciones y una vista previa en vivo que se queda a la vista mientras se recorren las opciones (ScreenFrame: la vista previa es el
 * encabezado fijo; las opciones se desplazan). Preferencia de este teléfono.
 */
@Composable
fun TextSizeScreen(choice: FontSizeChoice, onChoose: (FontSizeChoice) -> Unit, onBack: () -> Unit) {
    val system = systemFontScale()
    ScreenFrame(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 16.dp), spacing = 8.dp, headerMaxFraction = 0.55f, header = {
        TitleBar(end = { CuadraButton(stringResource(R.string.back), onBack, height = 48) }) {
            Text(stringResource(R.string.textsize_title), style = MaterialTheme.typography.headlineMedium)
        }
        Text(stringResource(R.string.textsize_intro), style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted)
        SamplePreview(AppFontScale.effective(system, choice), system)
    }) {
        CuadraCard(Modifier.verticalScroll(rememberScrollState()), padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 2.dp)) {
            Column {
                FontSizeChoice.entries.forEach { c -> ChoiceRow(c, c == choice) { onChoose(c) } }
            }
        }
    }
}

/** La muestra se dibuja con la escala que tendría la app si se eligiera esa opción (no con la de ahora): el cambio se ve al instante. */
@Composable
private fun SamplePreview(scale: Float, system: Float) {
    val d = LocalDensity.current
    CuadraCard {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            CompositionLocalProvider(LocalDensity provides Density(d.density, scale)) {
                SplitRow(end = { MoneyText("C$ 1,234.50", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold) }, endMaxFraction = 0.55f) {
                    Column {
                        Text(stringResource(R.string.textsize_sample_title), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.textsize_sample_line), style = MaterialTheme.typography.bodyLarge)
                    }
                }
                Text(stringResource(R.string.textsize_sample_hint), style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted)
            }
            // Fuera de la muestra: con la escala real de la pantalla, así esta nota se lee siempre igual.
            Text(stringResource(R.string.textsize_now, fmt(scale), fmt(system)), style = MaterialTheme.typography.labelMedium, color = CuadraColors.Green)
        }
    }
}

private fun fmt(v: Float) = java.util.Locale.US.let { String.format(it, "%.2f", v).trimEnd('0').trimEnd('.') } + "×"

@Composable
private fun ChoiceRow(choice: FontSizeChoice, selected: Boolean, onClick: () -> Unit) {
    val (label, detail) = when (choice) {
        FontSizeChoice.AUTO -> R.string.textsize_auto to R.string.textsize_auto_hint
        FontSizeChoice.SMALL -> R.string.textsize_small to R.string.textsize_small_hint
        FontSizeChoice.NORMAL -> R.string.textsize_normal to R.string.textsize_normal_hint
        FontSizeChoice.LARGE -> R.string.textsize_large to R.string.textsize_large_hint
        FontSizeChoice.SYSTEM -> R.string.textsize_system to R.string.textsize_system_hint
    }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).selectable(selected = selected, role = Role.RadioButton, onClick = onClick).padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RadioButton(selected, onClick = null)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(label), fontWeight = FontWeight.Bold)
            Text(stringResource(detail), style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted)
        }
    }
}

package com.cuadra.caja.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cuadra.caja.R
import com.cuadra.caja.domain.BusinessCalendar
import com.cuadra.caja.domain.BusinessRange
import com.cuadra.caja.domain.RangeChoice
import com.cuadra.caja.domain.RangePreset
import com.cuadra.caja.domain.RangePresets
import com.cuadra.caja.domain.RangeResult
import com.cuadra.caja.domain.RangeWindowText
import com.cuadra.caja.domain.ResolvedRange
import com.cuadra.caja.ui.theme.CuadraColors
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
private fun presetLabel(p: RangePreset): String = stringResource(
    when (p) {
        RangePreset.TODAY -> R.string.range_today
        RangePreset.YESTERDAY -> R.string.range_yesterday
        RangePreset.LAST_7 -> R.string.range_last_7
        RangePreset.LAST_30 -> R.string.range_last_30
        RangePreset.THIS_MONTH -> R.string.range_this_month
        RangePreset.LAST_MONTH -> R.string.range_last_month
        RangePreset.CUSTOM -> R.string.range_custom
    },
)

/** La ventana exacta de una elección: «28 sep 2:00 a. m. → 29 sep 2:00 a. m.» (o «→ en curso» si el último día es hoy). Zona y corte del negocio. */
@Composable
fun rangeWindowText(range: ResolvedRange, calendar: BusinessCalendar, nowMillis: Long): String {
    val locale = LocalConfiguration.current.locales[0]
    val (start, end) = RangeWindowText.parts(range, calendar, nowMillis, locale)
    return stringResource(R.string.range_window, start, end ?: stringResource(R.string.range_in_progress))
}

/**
 * Selector de rango COMPARTIDO (Resumen, Ventas, Gastos, Cierre del día): Hoy, Ayer, 7 días, 30 días, Este mes, Mes pasado y «Personalizado» (calendario).
 * Debajo se escribe SIEMPRE la ventana exacta de lo elegido, para que nadie tenga que adivinar qué horas cubre («Ayer» = de la hora de corte de ayer a la de hoy).
 * Las jornadas son del NEGOCIO (`calendar`), nunca del teléfono. Sin negocio conocido todavía, solo se dibujan los atajos.
 */
@Composable
fun RangePicker(choice: RangeChoice, calendar: BusinessCalendar?, onChoose: (RangeChoice) -> Unit, modifier: Modifier = Modifier, nowMillis: Long = System.currentTimeMillis()) {
    var picking by remember { mutableStateOf(false) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ChipFlow {
            RangePresets.ALL.forEach { p -> CuadraChip(presetLabel(p), choice.preset == p, { onChoose(RangeChoice(p)) }) }
            val custom = choice.custom
            CuadraChip(
                if (choice.preset == RangePreset.CUSTOM && custom != null) rangeLabel(custom.first, custom.second) else presetLabel(RangePreset.CUSTOM),
                choice.preset == RangePreset.CUSTOM, { picking = true },
            )
        }
        if (calendar != null) {
            val resolved = RangePresets.resolve(choice, calendar, nowMillis)
            Text(rangeWindowText(resolved, calendar, nowMillis), style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted)
        }
        if (picking && calendar != null) {
            RangePickerDialog(calendar, nowMillis, choice.custom, onApply = { f, t -> onChoose(RangeChoice(RangePreset.CUSTOM, f to t)) }, onDismiss = { picking = false })
        }
    }
}

@Composable
private fun rangeLabel(from: LocalDate, to: LocalDate): String {
    val locale: Locale = LocalConfiguration.current.locales[0]
    val pattern = if (from.year != to.year || from.year != LocalDate.now().year) "d MMM yyyy" else "d MMM"
    val fmt = DateTimeFormatter.ofPattern(pattern, locale)
    return stringResource(R.string.summary_range_label, from.format(fmt).replace(".", ""), to.format(fmt).replace(".", ""))
}

private fun LocalDate.toPickerMillis() = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
private fun Long.toPickerDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

/**
 * Calendario de rango: fechas de NEGOCIO (no pasa de hoy); el dominio valida y aquí solo se avisa. Un solo toque cuenta como un solo día.
 * Muestra la ventana exacta de lo que se va tocando, antes de aplicar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RangePickerDialog(calendar: BusinessCalendar, nowMillis: Long, last: Pair<LocalDate, LocalDate>?, onApply: (LocalDate, LocalDate) -> Unit, onDismiss: () -> Unit) {
    val today = RangePresets.today(calendar, nowMillis)
    val state = rememberDateRangePickerState(
        initialSelectedStartDateMillis = last?.first?.toPickerMillis(),
        initialSelectedEndDateMillis = last?.second?.toPickerMillis(),
        initialDisplayedMonthMillis = (last?.second ?: today).toPickerMillis(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = !utcTimeMillis.toPickerDate().isAfter(today)
        },
    )
    val from = state.selectedStartDateMillis?.toPickerDate()
    val to = state.selectedEndDateMillis?.toPickerDate() ?: from
    val result = if (from != null && to != null) BusinessRange.resolve(from, to, calendar, today) else null
    val error = when (result) {
        RangeResult.Reversed -> stringResource(R.string.summary_range_reversed)
        RangeResult.InFuture -> stringResource(R.string.summary_range_future)
        is RangeResult.TooLong -> stringResource(R.string.summary_range_too_long, result.days, BusinessRange.MAX_DAYS)
        else -> null
    }
    AppDialog(onDismissRequest = onDismiss, usePlatformDefaultWidth = false) {
        androidx.compose.material3.Surface(Modifier.padding(vertical = 8.dp).fillMaxWidth(), shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            // El selector de Material tiene celdas fijas de 48 dp (7 columnas): necesita ~390 dp de ancho y una letra que no crezca sin límite.
            // Se reduce la escala del selector solo lo justo para que quepa la semana completa y su letra se limita a 1.15x (el resto de la pantalla sigue con la letra del teléfono).
            val dens = androidx.compose.ui.platform.LocalDensity.current
            val shrink = (LocalConfiguration.current.screenWidthDp / 392f).coerceAtMost(1f)
            Column {
                androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(dens.density * shrink, dens.fontScale.coerceAtMost(1.15f))) {
                    DateRangePicker(
                        state, Modifier.weight(1f, fill = false).heightIn(max = 540.dp),
                        title = { Text(stringResource(R.string.summary_range_title), Modifier.padding(start = 24.dp, end = 12.dp, top = 16.dp), style = MaterialTheme.typography.titleMedium) },
                        headline = { Text(stringResource(R.string.summary_range_hint), Modifier.padding(start = 24.dp, end = 12.dp), style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted) },
                        showModeToggle = false,
                    )
                }
                if (result is RangeResult.Ok) {
                    val resolved = ResolvedRange(RangePreset.CUSTOM, result.from, result.to, result.startMillis, result.endMillis, inProgress = result.to == today)
                    Text(rangeWindowText(resolved, calendar, nowMillis), Modifier.padding(horizontal = 24.dp, vertical = 6.dp), style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted)
                }
                if (error != null) Text(error, Modifier.padding(horizontal = 24.dp, vertical = 8.dp), color = CuadraColors.Orange, fontWeight = FontWeight.Bold)
                ButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    CuadraButton(stringResource(R.string.cancel), onDismiss, Modifier.share(1f), height = 48)
                    CuadraButton(stringResource(R.string.summary_range_apply), { if (from != null && to != null && result is RangeResult.Ok) { onApply(result.from, result.to); onDismiss() } }, Modifier.share(1f), kind = ButtonKind.DARK, enabled = result is RangeResult.Ok, height = 48)
                }
            }
        }
    }
}

package com.cuadra.caja.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.cuadra.caja.R
import com.cuadra.caja.data.local.SaleEntity
import com.cuadra.caja.ui.HistoryViewModel
import com.cuadra.caja.ui.common.CuadraCard
import com.cuadra.caja.ui.common.Tag
import com.cuadra.caja.ui.common.money
import com.cuadra.caja.ui.theme.CuadraColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Ventas de este teléfono, las más recientes primero. Lo cobrado y lo eliminado se distingue a simple vista. */
@Composable
fun HistoryScreen(vm: HistoryViewModel, timezone: String) {
    val sales by vm.sales.collectAsState()
    val zone = runCatching { ZoneId.of(timezone) }.getOrDefault(ZoneId.systemDefault())
    // El idioma se lee de la configuración de Compose: al cambiarlo desde la app, las fechas se vuelven a dar formato.
    val locale: Locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val time = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withLocale(locale).withZone(zone)
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.history_title), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 16.dp))
        if (sales.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(stringResource(R.string.history_empty), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) { items(sales, key = { it.id }) { SaleRow(it, time) } }
        }
    }
}

@Composable
private fun SaleRow(s: SaleEntity, time: DateTimeFormatter) {
    val cancelled = s.status == "CANCELLED"
    CuadraCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(time.format(Instant.ofEpochMilli(s.completedAt ?: s.createdAt)), fontWeight = FontWeight.ExtraBold)
                (s.completedByName ?: s.createdByName)?.let { Text(stringResource(R.string.history_by, it), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (cancelled) Tag(stringResource(R.string.history_cancelled), CuadraColors.Red, CuadraColors.RedSoft)
                    else Tag(stringResource(R.string.history_completed), CuadraColors.Green, CuadraColors.GreenSoft)
                    if (s.rev == 0L) Tag(stringResource(R.string.history_unsynced), CuadraColors.Ink, CuadraColors.Soft)
                }
            }
            Text(money(s.totalMinor), fontWeight = FontWeight.ExtraBold, textDecoration = if (cancelled) TextDecoration.LineThrough else null)
        }
    }
}

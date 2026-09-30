package com.cuadra.caja.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cuadra.caja.R
import com.cuadra.caja.domain.ModuleVisibility
import com.cuadra.caja.domain.BusinessCalendar
import com.cuadra.caja.domain.DayCloseCard
import com.cuadra.caja.domain.RangePreset
import com.cuadra.caja.domain.RangeWindowText
import com.cuadra.caja.ui.DailyCloseActions
import com.cuadra.caja.ui.DailyCloseUi
import com.cuadra.caja.ui.DailyCloseViewModel
import com.cuadra.caja.ui.asString
import com.cuadra.caja.ui.common.CuadraButton
import com.cuadra.caja.ui.common.CuadraCard
import com.cuadra.caja.ui.common.LinkAction
import com.cuadra.caja.ui.common.MoneyText
import com.cuadra.caja.ui.common.RangePicker
import com.cuadra.caja.ui.common.SectionLabel
import com.cuadra.caja.ui.common.SplitRow
import com.cuadra.caja.ui.common.TitleBar
import com.cuadra.caja.ui.common.Text
import com.cuadra.caja.ui.common.money
import com.cuadra.caja.ui.theme.CuadraColors
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun DailyCloseScreen(vm: DailyCloseViewModel, onBack: () -> Unit) {
    val ui by vm.ui.collectAsState()
    val calendar by vm.calendar.collectAsState()
    DailyCloseContent(ui, calendar, vm, onBack)
}

/** Cierre del día sin ViewModel (estado + acciones): es lo que dibuja la guardia de diseño. */
@Composable
fun DailyCloseContent(ui: DailyCloseUi, calendar: BusinessCalendar?, actions: DailyCloseActions, onBack: () -> Unit, nowMillis: Long = System.currentTimeMillis()) {
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            TitleBar(Modifier.padding(top = 16.dp), end = { CuadraButton(stringResource(R.string.back), onBack, height = 48) }) {
                Text(stringResource(R.string.close_title), style = MaterialTheme.typography.headlineMedium)
            }
        }
        item { Text(stringResource(R.string.close_intro), style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted) }
        item { RangePicker(ui.range, calendar, actions::setRange, nowMillis = nowMillis) }
        if (ui.needsConnection || ui.error != null) item {
            CuadraCard {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(if (ui.needsConnection) R.string.close_needs_connection else R.string.close_error), fontWeight = FontWeight.Bold, color = CuadraColors.Orange)
                    ui.error?.let { Text(it.asString(), style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted) }
                    if (ui.stale && ui.cards != null) Text(stringResource(R.string.close_stale_note), style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted)
                    LinkAction(stringResource(R.string.close_retry), actions::retry)
                }
            }
        }
        // El cierre puede estar incompleto mientras un teléfono no termine de enviar (este u otro del negocio).
        if (ui.localPending > 0 || ui.syncWarnings.isNotEmpty()) item {
            val locale: Locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
            val fmt = java.time.format.DateTimeFormatter.ofLocalizedDateTime(java.time.format.FormatStyle.SHORT).withLocale(locale).withZone(calendar?.zone ?: java.time.ZoneId.systemDefault())
            CuadraCard(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (ui.localPending > 0) Text(pluralStringResource(R.plurals.close_sync_pending_here, ui.localPending, ui.localPending), color = CuadraColors.Orange, fontWeight = FontWeight.Bold)
                    ui.syncWarnings.forEach { w ->
                        val text = if (w.pendingOps > 0) pluralStringResource(R.plurals.close_sync_pending_device, w.pendingOps, w.pendingOps, w.name)
                        else stringResource(R.string.close_sync_stale_device, w.name, w.lastSyncAt?.let { runCatching { fmt.format(java.time.Instant.parse(it)) }.getOrNull() } ?: "—")
                        Text(text, color = CuadraColors.Orange, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        if (ui.loading) item { Text(stringResource(R.string.close_loading), color = CuadraColors.Muted) }
        ui.cards?.let { cards -> items(cards, key = { it.date.toString() }) { DayCard(it, calendar, nowMillis) } }
        item { Column(Modifier.padding(bottom = 8.dp)) {} }
    }
}

@Composable
private fun DayCard(d: DayCloseCard, calendar: BusinessCalendar?, nowMillis: Long) {
    val locale: Locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val zone = calendar?.zone
    val inProgress = nowMillis in d.startMillis until d.endMillis
    val heading = DateTimeFormatter.ofPattern("EEEE d MMM", locale).format(d.date).replace(".", "").replaceFirstChar { it.titlecase(locale) }
    val withYear = zone != null && java.time.Instant.ofEpochMilli(d.startMillis).atZone(zone).year != java.time.Instant.ofEpochMilli(nowMillis).atZone(zone).year
    val window = zone?.let {
        val start = RangeWindowText.point(d.startMillis, it, locale, withYear)
        val end = if (inProgress) stringResource(R.string.range_in_progress) else RangeWindowText.point(d.endMillis, it, locale, withYear)
        stringResource(R.string.range_window, start, end)
    }
    CuadraCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(heading, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
            window?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted) }
            SplitRow(end = { MoneyText(money(d.salesMinor), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold) }) {
                Column {
                    Text(stringResource(R.string.close_total_sales), fontWeight = FontWeight.Bold)
                    Text(pluralStringResource(R.plurals.close_sales_count, d.salesCount.toInt(), d.salesCount.toInt()), style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted)
                }
            }
            if (!d.hasAdjustments && d.salesCount == 0L && d.cancelledCount == 0L && d.collected.totalMinor == 0L && d.drawerExpensesMinor == 0L && d.otherExpensesMinor == 0L && d.withdrawalsMinor == 0L && d.depositsMinor == 0L) {
                Text(stringResource(R.string.close_quiet_day), style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted)
            } else {
                SectionLabel(stringResource(R.string.close_by_method))
                Line(stringResource(R.string.method_CASH), d.sales.cashMinor)
                Line(stringResource(R.string.method_CARD), d.sales.cardMinor)
                Line(stringResource(R.string.method_TRANSFER), d.sales.transferMinor)
                val modules = com.cuadra.caja.ui.common.LocalModules.current
                if (ModuleVisibility.credit(modules) || d.sales.creditMinor != 0L) Line(stringResource(R.string.close_credit_sales), d.sales.creditMinor)
                Line(stringResource(R.string.method_OTHER), d.sales.otherMinor)
                if (d.collected.totalMinor > 0) {
                    SectionLabel(stringResource(R.string.close_collected))
                    Line(stringResource(R.string.method_CASH), d.collected.cashMinor)
                    Line(stringResource(R.string.method_CARD), d.collected.cardMinor)
                    Line(stringResource(R.string.method_TRANSFER), d.collected.transferMinor)
                    Line(stringResource(R.string.method_OTHER), d.collected.otherMinor + d.collected.creditMinor)
                }
                if (ModuleVisibility.expenses(modules) || d.drawerExpensesMinor != 0L || d.otherExpensesMinor != 0L) {
                    SectionLabel(stringResource(R.string.close_expenses))
                    Line(stringResource(R.string.close_drawer_expenses), d.drawerExpensesMinor, minus = true)
                    Line(stringResource(R.string.close_other_expenses), d.otherExpensesMinor, minus = true)
                }
                SectionLabel(stringResource(R.string.close_cash_moves))
                Line(stringResource(R.string.close_withdrawals), d.withdrawalsMinor, minus = true)
                Line(stringResource(R.string.close_deposits), d.depositsMinor)
                // Los días cerrados no cambian: lo devuelto hoy y lo anulado hoy de días anteriores se corrige AQUÍ.
                if (d.hasAdjustments) {
                    SectionLabel(stringResource(R.string.close_adjustments))
                    if (d.returnsCount > 0) Line(stringResource(R.string.close_returns, d.returnsCount.toInt()), d.returnsMinor, minus = true)
                    if (d.cashRefundsMinor > 0) Line(stringResource(R.string.close_cash_refunds), d.cashRefundsMinor, minus = true)
                    if (d.priorCancelledCount > 0) Line(stringResource(R.string.close_prior_cancelled, d.priorCancelledCount.toInt()), d.priorCancelledMinor, minus = true)
                    d.laterVoids.forEach { v -> SignedLine(laterVoidLabel(v.kind, v.count.toInt()), if (v.cashEffectMinor == 0L) v.amountMinor else v.cashEffectMinor) }
                    if (d.returnsCount > 0 || d.priorCancelledCount > 0) {
                        SplitRow(end = { MoneyText(money(d.netSalesMinor), fontWeight = FontWeight.ExtraBold) }) { Text(stringResource(R.string.close_net), fontWeight = FontWeight.ExtraBold) }
                    }
                    Text(stringResource(R.string.close_adjust_note), style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted)
                }
            }
            SplitRow(end = { MoneyText(money(d.expectedCashMinor), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, color = CuadraColors.Green) }) {
                Text(stringResource(R.string.close_expected), fontWeight = FontWeight.ExtraBold)
            }
            Text(stringResource(R.string.close_expected_how), style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted)
            if (d.cancelledCount > 0) Text(pluralStringResource(R.plurals.close_cancelled, d.cancelledCount.toInt(), d.cancelledCount.toInt(), money(d.cancelledMinor)), style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Red, fontWeight = FontWeight.Bold)
        }
    }
}

/** Una línea «concepto | monto»; lo que resta del cajón lleva «−». Las líneas en cero se muestran igual: ver el 0 también informa. */
@Composable
private fun Line(label: String, minor: Long, minus: Boolean = false) {
    SplitRow(end = { MoneyText((if (minus && minor > 0) "−" else "") + money(minor), fontWeight = FontWeight.Bold) }) { Text(label) }
}

@Composable
private fun laterVoidLabel(kind: String, count: Int): String = when (kind) {
    "EXPENSE_DRAWER" -> stringResource(R.string.close_later_EXPENSE_DRAWER, count)
    "EXPENSE_OTHER" -> stringResource(R.string.close_later_EXPENSE_OTHER, count)
    "CREDIT_PAYMENT" -> stringResource(R.string.close_later_CREDIT_PAYMENT, count)
    "WITHDRAWAL" -> stringResource(R.string.close_later_WITHDRAWAL, count)
    else -> stringResource(R.string.close_later_DEPOSIT, count)
}

/** Una línea con signo (+ vuelve al cajón, − sale). */
@Composable
private fun SignedLine(label: String, minor: Long) {
    SplitRow(end = { MoneyText((if (minor < 0) "−" else "+") + money(kotlin.math.abs(minor)), fontWeight = FontWeight.Bold) }) { Text(label) }
}

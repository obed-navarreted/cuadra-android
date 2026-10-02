package com.cuadra.caja.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import com.cuadra.caja.domain.BusinessCalendar
import com.cuadra.caja.domain.RangeChoice
import com.cuadra.caja.ui.common.RangePicker
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import com.cuadra.caja.ui.common.Text
import com.cuadra.caja.ui.common.AppDialog
import com.cuadra.caja.ui.common.MoneyText
import com.cuadra.caja.ui.common.ButtonKind
import com.cuadra.caja.ui.common.SplitRow
import com.cuadra.caja.ui.common.TitleBar
import com.cuadra.caja.ui.common.ChipFlow
import com.cuadra.caja.ui.common.ButtonRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cuadra.caja.R
import com.cuadra.caja.domain.ModuleVisibility
import com.cuadra.caja.ui.SummaryActions
import com.cuadra.caja.ui.SummaryData
import com.cuadra.caja.ui.SummaryViewModel
import com.cuadra.caja.ui.common.CuadraButton
import com.cuadra.caja.ui.common.CuadraCard
import com.cuadra.caja.ui.common.CuadraChip
import com.cuadra.caja.ui.common.SectionLabel
import com.cuadra.caja.ui.common.money
import com.cuadra.caja.ui.theme.CuadraColors

@Composable
private fun methodLabel(method: String): String = when (method) {
    "CASH" -> stringResource(R.string.method_CASH)
    "TRANSFER" -> stringResource(R.string.method_TRANSFER)
    "CARD" -> stringResource(R.string.method_CARD)
    "CREDIT" -> stringResource(R.string.method_CREDIT)
    else -> stringResource(R.string.method_OTHER)
}

/** Resumen para quien administra: cuánto se vendió, cuánto se gastó, la ganancia estimada y lo que falta por cobrar. */
@Composable
fun SummaryScreen(vm: SummaryViewModel, onBack: () -> Unit, onYesterdayClose: (() -> Unit)? = null) {
    val data by vm.data.collectAsState()
    val choice by vm.choice.collectAsState()
    val calendar by vm.calendar.collectAsState()
    com.cuadra.caja.ui.common.Refreshing(vm.refresher) { SummaryContent(data, choice, calendar, vm, onBack, onYesterdayClose) }
}

/** El resumen sin ViewModel (estado + acciones): es lo que dibuja la guardia de diseño. */
@Composable
fun SummaryContent(
    data: SummaryData?, choice: RangeChoice, calendar: BusinessCalendar?, actions: SummaryActions, onBack: () -> Unit, onYesterdayClose: (() -> Unit)? = null,
    nowMillis: Long = System.currentTimeMillis(),
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TitleBar(end = { CuadraButton(stringResource(R.string.back), onBack, height = 48) }) {
            Text(stringResource(R.string.summary_title), style = MaterialTheme.typography.headlineMedium)
        }
        RangePicker(choice, calendar, actions::choose, nowMillis = nowMillis)
        // Atajo al cierre automático de la jornada anterior (solo quien administra: la pantalla lo entrega).
        onYesterdayClose?.let { CuadraButton(stringResource(R.string.summary_yesterday_close), it, Modifier.fillMaxWidth()) }
        val d = data
        if (d == null) {
            Text(stringResource(R.string.summary_loading), color = CuadraColors.Muted)
            return@Column
        }
        val ticket = if (d.sales.count == 0L) 0 else Math.round(d.sales.totalMinor.toDouble() / d.sales.count)
        Kpi(stringResource(R.string.summary_sales), money(d.sales.totalMinor), pluralStringResource(R.plurals.summary_sales_hint, d.sales.count.toInt(), d.sales.count.toInt(), money(ticket).replace(' ', '\u00A0')))
        // Lo que descontaron las promociones por cantidad (ya está restado de las ventas).
        if (d.promotionDiscountMinor > 0) SplitRow(end = { MoneyText("−" + money(d.promotionDiscountMinor), fontWeight = FontWeight.Bold, color = CuadraColors.Green) }) { Text(stringResource(R.string.promo_discounts)) }
        val modules = com.cuadra.caja.ui.common.LocalModules.current
        if (ModuleVisibility.expenses(modules) || d.profit.operatingExpensesMinor != 0L) Kpi(stringResource(R.string.summary_expenses), money(d.profit.operatingExpensesMinor), stringResource(R.string.summary_expenses_hint, money(d.profit.purchasesExcludedMinor)))
        Kpi(
            stringResource(R.string.summary_profit), money(d.profit.estimatedProfitMinor), stringResource(R.string.summary_profit_formula),
            valueColor = if (d.profit.estimatedProfitMinor < 0) CuadraColors.Red else CuadraColors.Green,
        )
        if (d.profit.costCoveragePercent < 100 && d.sales.count > 0) Text(stringResource(R.string.summary_coverage_warning, d.profit.costCoveragePercent), color = CuadraColors.Orange, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
        if (ModuleVisibility.credit(modules) || d.receivableMinor != 0L) Kpi(stringResource(R.string.summary_receivable), money(d.receivableMinor), null, valueColor = if (d.receivableMinor > 0) CuadraColors.Orange else CuadraColors.Ink)
        if (d.lowStock > 0) Text(pluralStringResource(R.plurals.summary_low_stock, d.lowStock, d.lowStock), color = CuadraColors.Orange, fontWeight = FontWeight.Bold)
        if (d.methods.isNotEmpty()) {
            SectionLabel(stringResource(R.string.summary_methods))
            CuadraCard {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    d.methods.forEach { m ->
                        SplitRow(end = { MoneyText(money(m.amountMinor), fontWeight = FontWeight.Bold) }) { Text(methodLabel(m.method)) }
                    }
                }
            }
        }
        if (d.top.isNotEmpty()) {
            SectionLabel(stringResource(R.string.summary_top))
            CuadraCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    d.top.forEach { p ->
                        SplitRow(end = {
                            Column(horizontalAlignment = Alignment.End) {
                                MoneyText(money(p.revenueMinor), fontWeight = FontWeight.ExtraBold)
                                if (p.fullyCosted) MoneyText(money(p.profitMinor), style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted)
                                else Text(stringResource(R.string.summary_no_cost), style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted, textAlign = androidx.compose.ui.text.style.TextAlign.End)
                            }
                        }) {
                            Column {
                                Text(p.name, fontWeight = FontWeight.Bold, maxLines = 2, ellipsize = true)
                                MoneyText(qtyText(p.quantityMilli), style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted)
                            }
                        }
                    }
                }
            }
        }
        if (d.sales.count == 0L) Text(stringResource(R.string.summary_empty), color = CuadraColors.Muted)
        Text(stringResource(R.string.summary_scope_note), style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted)
    }
}

@Composable
private fun Kpi(label: String, value: String, hint: String?, valueColor: androidx.compose.ui.graphics.Color = CuadraColors.Ink) {
    CuadraCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted, fontWeight = FontWeight.Bold)
            MoneyText(value, Modifier.fillMaxWidth(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, color = valueColor)
            hint?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted) }
        }
    }
}

package com.cuadra.caja.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.cuadra.caja.ui.SummaryPeriod
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
fun SummaryScreen(vm: SummaryViewModel, onBack: () -> Unit) {
    val data by vm.data.collectAsState()
    val period by vm.period.collectAsState()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.summary_title), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            CuadraButton(stringResource(R.string.back), onBack, height = 44)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CuadraChip(stringResource(R.string.summary_today), period == SummaryPeriod.TODAY, { vm.select(SummaryPeriod.TODAY) })
            CuadraChip(stringResource(R.string.summary_week), period == SummaryPeriod.WEEK, { vm.select(SummaryPeriod.WEEK) })
            CuadraChip(stringResource(R.string.summary_month), period == SummaryPeriod.MONTH, { vm.select(SummaryPeriod.MONTH) })
        }
        val d = data
        if (d == null) {
            Text(stringResource(R.string.summary_loading), color = CuadraColors.Muted)
            return@Column
        }
        val ticket = if (d.sales.count == 0L) 0 else Math.round(d.sales.totalMinor.toDouble() / d.sales.count)
        Kpi(stringResource(R.string.summary_sales), money(d.sales.totalMinor), pluralStringResource(R.plurals.summary_sales_hint, d.sales.count.toInt(), d.sales.count.toInt(), money(ticket)))
        Kpi(stringResource(R.string.summary_expenses), money(d.profit.operatingExpensesMinor), stringResource(R.string.summary_expenses_hint, money(d.profit.purchasesExcludedMinor)))
        Kpi(
            stringResource(R.string.summary_profit), money(d.profit.estimatedProfitMinor), stringResource(R.string.summary_profit_formula),
            valueColor = if (d.profit.estimatedProfitMinor < 0) CuadraColors.Red else CuadraColors.Green,
        )
        if (d.profit.costCoveragePercent < 100 && d.sales.count > 0) Text(stringResource(R.string.summary_coverage_warning, d.profit.costCoveragePercent), color = CuadraColors.Orange, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
        Kpi(stringResource(R.string.summary_receivable), money(d.receivableMinor), null, valueColor = if (d.receivableMinor > 0) CuadraColors.Orange else CuadraColors.Ink)
        if (d.lowStock > 0) Text(pluralStringResource(R.plurals.summary_low_stock, d.lowStock, d.lowStock), color = CuadraColors.Orange, fontWeight = FontWeight.Bold)
        if (d.methods.isNotEmpty()) {
            SectionLabel(stringResource(R.string.summary_methods))
            CuadraCard {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    d.methods.forEach { m ->
                        Row(Modifier.fillMaxWidth()) { Text(methodLabel(m.method), Modifier.weight(1f)); Text(money(m.amountMinor), fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
        if (d.top.isNotEmpty()) {
            SectionLabel(stringResource(R.string.summary_top))
            CuadraCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    d.top.forEach { p ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(p.name, fontWeight = FontWeight.Bold, maxLines = 1)
                                Text(qtyText(p.quantityMilli), style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(money(p.revenueMinor), fontWeight = FontWeight.ExtraBold)
                                Text(if (p.fullyCosted) money(p.profitMinor) else stringResource(R.string.summary_no_cost), style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted)
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
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, color = valueColor)
            hint?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted) }
        }
    }
}

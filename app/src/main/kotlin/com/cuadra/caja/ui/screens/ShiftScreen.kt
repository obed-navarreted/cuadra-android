package com.cuadra.caja.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.cuadra.caja.R
import com.cuadra.caja.core.model.Currency
import com.cuadra.caja.core.model.Money
import com.cuadra.caja.data.local.ShiftEntity
import com.cuadra.caja.domain.CashClosing
import com.cuadra.caja.domain.ClosingBreakdown
import com.cuadra.caja.domain.ClosingResult
import com.cuadra.caja.domain.Denominations
import com.cuadra.caja.ui.CashViewModel
import com.cuadra.caja.ui.CountDraft
import com.cuadra.caja.ui.common.ButtonKind
import com.cuadra.caja.ui.common.CuadraButton
import com.cuadra.caja.ui.common.CuadraCard
import com.cuadra.caja.ui.common.LocalMoney
import com.cuadra.caja.ui.common.SectionLabel
import com.cuadra.caja.ui.common.money
import com.cuadra.caja.ui.common.VoiceTextField
import com.cuadra.caja.ui.theme.CuadraColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Turno y cierre de caja: abrir con el fondo, ver lo que se movió, contar el efectivo y cerrar con la diferencia a la vista. */
@Composable
fun ShiftScreen(vm: CashViewModel, timezone: String, required: Boolean, onBack: () -> Unit) {
    val ui by vm.ui.collectAsState()
    val shift by vm.currentShift.collectAsState()
    val breakdown by vm.breakdown.collectAsState()
    val recent by vm.recentShifts.collectAsState()
    val business by vm.business.collectAsState()
    val suggested by vm.suggestedFloat.collectAsState()
    val zone = runCatching { ZoneId.of(timezone) }.getOrDefault(ZoneId.systemDefault())
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val time = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withLocale(locale).withZone(zone)

    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.shift_title), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            if (!required || shift != null) CuadraButton(stringResource(R.string.back), onBack, height = 44)
        }
        val closed = ui.closedShift
        when {
            closed != null -> ClosedSummary(closed, time, vm, onBack)
            shift == null -> OpenForm(ui.openFloat, suggested, vm, required)
            else -> OpenShiftBody(shift!!, breakdown, ui.count, ui.noteRequired, business?.currency ?: "USD", time, vm)
        }
        if (closed == null && recent.any { it.status == "CLOSED" }) {
            SectionLabel(stringResource(R.string.shift_recent))
            recent.filter { it.status == "CLOSED" }.take(5).forEach { s ->
                CuadraCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(time.format(Instant.ofEpochMilli(s.closedAt ?: s.openedAt)), fontWeight = FontWeight.Bold)
                            Text(s.closedByName ?: s.openedByName.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted)
                        }
                        // Un cajero no ve resultados de otros turnos: el servidor los deja en blanco y aquí no se muestran.
                        s.differenceMinor?.let { DifferenceTag(it) }
                    }
                }
            }
        }
    }
    ui.messageRes?.let { res ->
        Sheet(vm::dismissMessage) {
            Text(stringResource(res), style = MaterialTheme.typography.bodyLarge)
            CuadraButton(stringResource(R.string.close), vm::dismissMessage, Modifier.fillMaxWidth(), kind = ButtonKind.DARK)
        }
    }
    if (ui.count.showCounter) Counter(ui.count, business?.currency ?: "USD", vm)
}

@Composable
private fun DifferenceTag(diff: Long) {
    val (text, fg, bg) = when (CashClosing.result(diff)) {
        ClosingResult.BALANCED -> Triple(stringResource(R.string.result_balanced), CuadraColors.Green, CuadraColors.GreenSoft)
        ClosingResult.SHORT -> Triple(stringResource(R.string.result_short, money(-diff)), CuadraColors.Red, CuadraColors.RedSoft)
        ClosingResult.OVER -> Triple(stringResource(R.string.result_over, money(diff)), CuadraColors.Orange, CuadraColors.OrangeSoft)
    }
    com.cuadra.caja.ui.common.Tag(text, fg, bg)
}

@Composable
private fun OpenForm(text: String?, suggested: Long, vm: CashViewModel, required: Boolean) {
    val fmt = LocalMoney.current
    val value = text ?: ""
    CuadraCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.shift_open_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
            Text(stringResource(if (required) R.string.shift_required_hint else R.string.shift_open_hint), style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted)
            OutlinedTextField(value, { vm.updateOpenFloat(it.take(14)) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(stringResource(R.string.shift_float)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            if (suggested > 0) CuadraButton(stringResource(R.string.shift_float_suggest, fmt.format(suggested)), { vm.updateOpenFloat(java.math.BigDecimal.valueOf(suggested, fmt.decimals).toPlainString()) }, Modifier.fillMaxWidth(), height = 48)
            val ok = value.isBlank() || Money.parse(value, fmt.decimals) != null
            CuadraButton(stringResource(R.string.shift_open_confirm), { if (text == null) vm.updateOpenFloat("") ; vm.openShift() }, Modifier.fillMaxWidth(), kind = ButtonKind.PRIMARY, enabled = ok)
        }
    }
}

@Composable
private fun Line(label: String, minor: Long, sign: String = "", bold: Boolean = false, color: androidx.compose.ui.graphics.Color = CuadraColors.Ink) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(1f), fontWeight = if (bold) FontWeight.ExtraBold else FontWeight.Normal)
        Text(sign + money(minor), color = color, fontWeight = if (bold) FontWeight.ExtraBold else FontWeight.Bold)
    }
}

@Composable
private fun OpenShiftBody(shift: ShiftEntity, b: ClosingBreakdown, count: CountDraft, noteRequired: Boolean, currency: String, time: DateTimeFormatter, vm: CashViewModel) {
    val fmt = LocalMoney.current
    val expected = CashClosing.expected(shift.openingFloatMinor, b)
    Text(stringResource(R.string.shift_since, shift.openedByName.orEmpty(), time.format(Instant.ofEpochMilli(shift.openedAt))), style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted)
    CuadraCard {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Line(stringResource(R.string.shift_opening_float), shift.openingFloatMinor)
            Line(stringResource(R.string.shift_cash_sales, b.cashSalesCount), b.cashSalesMinor, "+")
            Line(stringResource(R.string.shift_credit_payments), b.creditPaymentsCashMinor, "+")
            Line(stringResource(R.string.shift_deposits), b.depositsMinor, "+")
            Line(stringResource(R.string.shift_expenses), b.expensesCashMinor, "−", color = CuadraColors.Red)
            Line(stringResource(R.string.shift_withdrawals), b.withdrawalsMinor, "−", color = CuadraColors.Red)
            Line(stringResource(R.string.shift_expected), expected, bold = true)
        }
    }
    val others = b.transferMinor + b.cardMinor + b.otherMinor
    if (others > 0 || b.creditNewMinor > 0) Text(
        stringResource(R.string.shift_other_methods, fmt.format(others), fmt.format(b.creditNewMinor)), style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted,
    )
    SectionLabel(stringResource(R.string.shift_counted_label))
    val counted = vm.countedMinor()
    OutlinedTextField(
        count.counted, { vm.updateCount(count.copy(counted = it.take(14), counts = emptyMap())) }, Modifier.fillMaxWidth(), singleLine = true,
        label = { Text(stringResource(R.string.shift_counted_input)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        placeholder = if (count.counts.isNotEmpty()) ({ Text(fmt.format(Denominations.total(count.counts))) }) else null,
    )
    CuadraButton(stringResource(R.string.shift_count_bills), { vm.updateCount(count.copy(showCounter = true)) }, Modifier.fillMaxWidth(), height = 48)
    if (counted != null) {
        val diff = CashClosing.difference(counted, expected)
        CuadraCard { Row(verticalAlignment = Alignment.CenterVertically) { Text(stringResource(R.string.shift_difference), Modifier.weight(1f), fontWeight = FontWeight.ExtraBold); DifferenceTag(diff) } }
    }
    VoiceTextField(
        count.note, { vm.updateCount(count.copy(note = it.take(300))) }, Modifier.fillMaxWidth(), minLines = 2,
        label = { Text(stringResource(if (noteRequired) R.string.shift_note_required else R.string.shift_note)) }, isError = noteRequired,
    )
    CuadraButton(stringResource(R.string.shift_close), vm::closeShift, Modifier.fillMaxWidth(), kind = ButtonKind.DARK, enabled = counted != null)
}

@Composable
private fun ClosedSummary(s: ShiftEntity, time: DateTimeFormatter, vm: CashViewModel, onBack: () -> Unit) {
    CuadraCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.shift_closed_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
            s.closedAt?.let { Text(time.format(Instant.ofEpochMilli(it)), color = CuadraColors.Muted) }
            Line(stringResource(R.string.shift_expected), s.expectedAtCloseMinor ?: 0)
            Line(stringResource(R.string.shift_counted_label), s.countedMinor ?: 0)
            s.differenceMinor?.let { DifferenceTag(it) }
            Text(stringResource(R.string.shift_closed_sync), style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted)
        }
    }
    CuadraButton(stringResource(R.string.close), { vm.showShift(false); onBack() }, Modifier.fillMaxWidth(), kind = ButtonKind.DARK)
}

/** Contador de billetes y monedas: cada toque suma uno; la suma sustituye al monto escrito. */
@Composable
private fun Counter(count: CountDraft, currency: String, vm: CashViewModel) {
    val fmt = LocalMoney.current
    val values = Denominations.forCurrency(currency, Currency.of(currency).decimals)
    val total = Denominations.total(count.counts)
    Sheet({ vm.updateCount(count.copy(showCounter = false)) }) {
        Text(stringResource(R.string.shift_counter_title), style = MaterialTheme.typography.headlineMedium)
        values.forEach { v ->
            val n = count.counts[v] ?: 0
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(fmt.format(v), Modifier.weight(1f), fontWeight = FontWeight.ExtraBold)
                CuadraButton("−", { vm.updateCount(count.copy(counts = count.counts + (v to (n - 1).coerceAtLeast(0)))) }, height = 44)
                Text(n.toString(), Modifier.padding(horizontal = 6.dp), fontWeight = FontWeight.ExtraBold)
                CuadraButton("+", { vm.updateCount(count.copy(counts = count.counts + (v to n + 1))) }, height = 44, kind = ButtonKind.PRIMARY)
            }
        }
        Text(stringResource(R.string.shift_counter_total, fmt.format(total)), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
        CuadraButton(stringResource(R.string.shift_counter_use), { vm.updateCount(count.copy(counted = "", showCounter = false)) }, Modifier.fillMaxWidth(), kind = ButtonKind.PRIMARY, enabled = total > 0)
    }
}

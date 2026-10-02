package com.cuadra.caja.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cuadra.caja.R
import com.cuadra.caja.core.model.Currency
import com.cuadra.caja.core.model.Money
import com.cuadra.caja.data.local.ShiftEntity
import com.cuadra.caja.domain.CashClosing
import com.cuadra.caja.domain.ClosingBreakdown
import com.cuadra.caja.domain.ClosingResult
import com.cuadra.caja.domain.Denominations
import com.cuadra.caja.ui.CashActions
import com.cuadra.caja.ui.CashUi
import com.cuadra.caja.ui.CashViewModel
import com.cuadra.caja.ui.CountDraft
import com.cuadra.caja.ui.countedMinor
import com.cuadra.caja.ui.common.ButtonKind
import com.cuadra.caja.ui.common.ButtonRow
import com.cuadra.caja.ui.common.CuadraButton
import com.cuadra.caja.ui.common.CuadraCard
import com.cuadra.caja.ui.common.LocalMoney
import com.cuadra.caja.ui.common.MoneyText
import com.cuadra.caja.ui.common.NumberField
import com.cuadra.caja.ui.common.ScreenFrame
import com.cuadra.caja.ui.common.SectionLabel
import com.cuadra.caja.ui.common.Sheet
import com.cuadra.caja.ui.common.SplitRow
import com.cuadra.caja.ui.common.Text
import com.cuadra.caja.ui.common.TitleBar
import com.cuadra.caja.ui.common.VoiceTextField
import com.cuadra.caja.ui.common.money
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
    ShiftContent(ui, shift, breakdown, recent, business?.currency ?: "USD", suggested, timezone, required, vm, onBack)
}

/** El turno sin ViewModel (estado + acciones): es lo que dibuja la guardia de diseño. */
@Composable
fun ShiftContent(
    ui: CashUi, shift: ShiftEntity?, breakdown: ClosingBreakdown, recent: List<ShiftEntity>, currency: String, suggested: Long, timezone: String, required: Boolean,
    actions: CashActions, onBack: () -> Unit,
) {
    val fmt = LocalMoney.current
    val zone = runCatching { ZoneId.of(timezone) }.getOrDefault(ZoneId.systemDefault())
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val time = com.cuadra.caja.domain.ClockFormat.dateTime(locale, zone)
    val closed = ui.closedShift
    val counted = ui.count.countedMinor(fmt.decimals)
    val expected = shift?.let { CashClosing.expected(it.openingFloatMinor, breakdown) }

    ScreenFrame(
        Modifier.fillMaxSize().imePadding().padding(16.dp), spacing = 12.dp,
        header = {
            TitleBar(end = if (!required || shift != null) ({ CuadraButton(stringResource(R.string.back), onBack, height = 48) }) else null) {
                Text(stringResource(R.string.shift_title), style = MaterialTheme.typography.headlineMedium)
            }
        },
        footer = {
            when {
                closed != null -> CuadraButton(stringResource(R.string.close), { actions.showShift(false); onBack() }, Modifier.fillMaxWidth(), kind = ButtonKind.DARK)
                shift == null -> {
                    val value = ui.openFloat ?: ""
                    val ok = value.isBlank() || Money.parse(value, fmt.decimals) != null
                    CuadraButton(stringResource(R.string.shift_open_confirm), { if (ui.openFloat == null) actions.updateOpenFloat(""); actions.openShift() }, Modifier.fillMaxWidth(), kind = ButtonKind.PRIMARY, enabled = ok)
                }
                else -> CuadraButton(stringResource(R.string.shift_close), actions::closeShift, Modifier.fillMaxWidth(), kind = ButtonKind.DARK, enabled = counted != null)
            }
        },
    ) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when {
                closed != null -> ClosedSummary(closed, time)
                shift == null -> OpenForm(ui.openFloat, suggested, actions, required)
                else -> OpenShiftBody(shift, breakdown, expected ?: 0, ui.count, counted, ui.noteRequired, time, actions)
            }
            if (closed == null && recent.any { it.status == "CLOSED" }) {
                SectionLabel(stringResource(R.string.shift_recent))
                recent.filter { it.status == "CLOSED" }.take(5).forEach { s ->
                    CuadraCard {
                        SplitRow(end = { s.differenceMinor?.let { DifferenceTag(it) } }) {
                            Column {
                                Text(time.format(Instant.ofEpochMilli(s.closedAt ?: s.openedAt)), fontWeight = FontWeight.Bold)
                                // Un cajero no ve resultados de otros turnos: el servidor los deja en blanco y aquí no se muestran.
                                Text(s.closedByName ?: s.openedByName.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted, maxLines = 2, ellipsize = true)
                            }
                        }
                    }
                }
            }
        }
    }
    ui.messageRes?.let { res ->
        Sheet(actions::dismissMessage, actions = { CuadraButton(stringResource(R.string.close), actions::dismissMessage, Modifier.fillMaxWidth(), kind = ButtonKind.DARK) }) {
            Text(stringResource(res), style = MaterialTheme.typography.bodyLarge)
        }
    }
    if (ui.count.showCounter) Counter(ui.count, currency, actions)
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
private fun OpenForm(text: String?, suggested: Long, actions: CashActions, required: Boolean) {
    val fmt = LocalMoney.current
    val value = text ?: ""
    CuadraCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.shift_open_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
            Text(stringResource(if (required) R.string.shift_required_hint else R.string.shift_open_hint), style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted)
            NumberField(value, { actions.updateOpenFloat(it.take(14)) }, label = { Text(stringResource(R.string.shift_float), maxLines = 1) })
            if (suggested > 0) CuadraButton(stringResource(R.string.shift_float_suggest, fmt.format(suggested)), { actions.updateOpenFloat(java.math.BigDecimal.valueOf(suggested, fmt.decimals).toPlainString()) }, Modifier.fillMaxWidth(), height = 48)
        }
    }
}

@Composable
private fun Line(label: String, minor: Long, sign: String = "", bold: Boolean = false, color: androidx.compose.ui.graphics.Color = CuadraColors.Ink) {
    SplitRow(end = { MoneyText(sign + money(minor), color = color, fontWeight = if (bold) FontWeight.ExtraBold else FontWeight.Bold) }) {
        Text(label, fontWeight = if (bold) FontWeight.ExtraBold else FontWeight.Normal)
    }
}

@Composable
private fun OpenShiftBody(shift: ShiftEntity, b: ClosingBreakdown, expected: Long, count: CountDraft, counted: Long?, noteRequired: Boolean, time: DateTimeFormatter, actions: CashActions) {
    val fmt = LocalMoney.current
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
    NumberField(
        count.counted, { actions.updateCount(count.copy(counted = it.take(14), counts = emptyMap())) },
        label = { Text(stringResource(R.string.shift_counted_input), maxLines = 1) },
        placeholder = if (count.counts.isNotEmpty()) ({ Text(fmt.format(Denominations.total(count.counts)), maxLines = 1) }) else null,
    )
    CuadraButton(stringResource(R.string.shift_count_bills), { actions.updateCount(count.copy(showCounter = true)) }, Modifier.fillMaxWidth(), height = 48)
    if (counted != null) {
        val diff = CashClosing.difference(counted, expected)
        CuadraCard { SplitRow(end = { DifferenceTag(diff) }) { Text(stringResource(R.string.shift_difference), fontWeight = FontWeight.ExtraBold) } }
    }
    VoiceTextField(
        count.note, { actions.updateCount(count.copy(note = it.take(300))) }, Modifier.fillMaxWidth(), minLines = 2,
        label = { Text(stringResource(if (noteRequired) R.string.shift_note_required else R.string.shift_note)) }, isError = noteRequired,
    )
}

@Composable
private fun ClosedSummary(s: ShiftEntity, time: DateTimeFormatter) {
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
}

/** Contador de billetes y monedas: cada toque suma uno; la suma sustituye al monto escrito. */
@Composable
fun Counter(count: CountDraft, currency: String, actions: CashActions) {
    val fmt = LocalMoney.current
    val values = Denominations.forCurrency(currency, Currency.of(currency).decimals)
    val total = Denominations.total(count.counts)
    Sheet({ actions.updateCount(count.copy(showCounter = false)) }, actions = {
        CuadraButton(stringResource(R.string.shift_counter_use), { actions.updateCount(count.copy(counted = "", showCounter = false)) }, Modifier.fillMaxWidth(), kind = ButtonKind.PRIMARY, enabled = total > 0)
    }) {
        Text(stringResource(R.string.shift_counter_title), style = MaterialTheme.typography.headlineMedium)
        values.forEach { v ->
            val n = count.counts[v] ?: 0
            SplitRow(end = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CuadraButton("−", { actions.updateCount(count.copy(counts = count.counts + (v to (n - 1).coerceAtLeast(0)))) }, Modifier.size(48.dp), height = 48)
                    Text(n.toString(), Modifier.padding(horizontal = 6.dp), fontWeight = FontWeight.ExtraBold, maxLines = 1, minScale = 0.5f)
                    CuadraButton("+", { actions.updateCount(count.copy(counts = count.counts + (v to n + 1))) }, Modifier.size(48.dp), height = 48, kind = ButtonKind.PRIMARY)
                }
            }, endMaxFraction = 0.7f) {
                MoneyText(fmt.format(v), fontWeight = FontWeight.ExtraBold)
            }
        }
        Text(stringResource(R.string.shift_counter_total, fmt.format(total)), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, maxLines = 2, minScale = 0.5f)
    }
}

package com.cuadra.caja.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.cuadra.caja.R
import com.cuadra.caja.core.model.Money
import com.cuadra.caja.data.local.ExpenseCategoryEntity
import com.cuadra.caja.ui.CashRow
import com.cuadra.caja.ui.CashViewModel
import com.cuadra.caja.ui.ExpenseDraft
import com.cuadra.caja.ui.ExpensePeriod
import com.cuadra.caja.ui.MovementDraft
import com.cuadra.caja.ui.VoidTarget
import com.cuadra.caja.ui.common.ButtonKind
import com.cuadra.caja.ui.common.CuadraButton
import com.cuadra.caja.ui.common.CuadraCard
import com.cuadra.caja.ui.common.CuadraChip
import com.cuadra.caja.ui.common.LocalMoney
import com.cuadra.caja.ui.common.Tag
import com.cuadra.caja.ui.common.money
import com.cuadra.caja.ui.common.VoiceTextField
import com.cuadra.caja.ui.theme.CuadraColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

private val SOURCES = listOf("CASH_DRAWER" to R.string.source_CASH_DRAWER, "BANK" to R.string.source_BANK, "CARD" to R.string.source_CARD, "OWNER" to R.string.source_OWNER, "OTHER" to R.string.source_OTHER)

/** Nombre de una categoría: las de fábrica se traducen por su clave; las creadas por el negocio traen su propio nombre. */
@Composable
internal fun categoryLabel(c: ExpenseCategoryEntity): String = categoryLabel(c.key ?: c.name.orEmpty())

@Composable
internal fun categoryLabel(keyOrName: String): String {
    val res = when (keyOrName) {
        "goods" -> R.string.cat_goods; "utilities" -> R.string.cat_utilities; "payroll" -> R.string.cat_payroll; "rent" -> R.string.cat_rent
        "transport" -> R.string.cat_transport; "supplies" -> R.string.cat_supplies; "maintenance" -> R.string.cat_maintenance; "other" -> R.string.cat_other
        else -> null
    }
    return res?.let { stringResource(it) } ?: keyOrName
}

/** Gastos y movimientos de caja del periodo. Lo que sale DEL CAJÓN se separa de lo pagado por otros medios: solo lo primero cambia el cierre. */
@Composable
fun ExpensesScreen(vm: CashViewModel, timezone: String, hasShifts: Boolean, onShift: () -> Unit) {
    val ui by vm.ui.collectAsState()
    val totals by vm.totals.collectAsState()
    val rows by vm.rows.collectAsState()
    val canManage by vm.canManage.collectAsState()
    val shift by vm.currentShift.collectAsState()
    val zone = runCatching { ZoneId.of(timezone) }.getOrDefault(ZoneId.systemDefault())
    val locale: Locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val time = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withLocale(locale).withZone(zone)

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.expenses_title), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 16.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(ExpensePeriod.TODAY to R.string.period_today, ExpensePeriod.WEEK to R.string.period_week, ExpensePeriod.MONTH to R.string.period_month).forEach { (p, label) ->
                CuadraChip(stringResource(label), ui.period == p, { vm.setPeriod(p) })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Kpi(stringResource(R.string.expenses_out_cash), money(totals.cashDrawer), CuadraColors.Red, Modifier.weight(1f))
            Kpi(stringResource(R.string.expenses_out_other), money(totals.other), CuadraColors.Ink, Modifier.weight(1f))
        }
        if (hasShifts) CuadraCard(onClick = onShift) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.shift_title), fontWeight = FontWeight.ExtraBold)
                    Text(stringResource(if (shift != null) R.string.shift_chip_open else R.string.shift_chip_none), style = MaterialTheme.typography.bodyMedium, color = if (shift != null) CuadraColors.Green else CuadraColors.Orange)
                }
                Text(stringResource(if (shift != null) R.string.shift_close_short else R.string.shift_open_short), color = CuadraColors.Green, fontWeight = FontWeight.ExtraBold)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CuadraButton(stringResource(R.string.expenses_new), vm::openExpense, Modifier.weight(1.4f), kind = ButtonKind.PRIMARY)
            CuadraButton(stringResource(R.string.expenses_deposit), { vm.openMovement("DEPOSIT") }, Modifier.weight(1f))
            if (canManage) CuadraButton(stringResource(R.string.expenses_withdraw), { vm.openMovement("WITHDRAWAL") }, Modifier.weight(1f))
        }
        if (rows.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(stringResource(R.string.expenses_empty), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(rows, key = { (if (it is CashRow.Expense) "e-" + it.e.id else "m-" + (it as CashRow.Movement).m.id) }) { CashRowItem(it, time, canManage, vm) }
        }
    }
    ExpenseDialogs(vm)
}

@Composable
private fun Kpi(label: String, value: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    CuadraCard(modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted)
            Text(value, style = MaterialTheme.typography.headlineMedium, color = color, fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
private fun CashRowItem(row: CashRow, time: DateTimeFormatter, canManage: Boolean, vm: CashViewModel) {
    val voided = when (row) { is CashRow.Expense -> row.e.voided; is CashRow.Movement -> row.m.voided }
    CuadraCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                when (row) {
                    is CashRow.Expense -> {
                        val cat = row.category?.let { categoryLabel(it) }
                        Text(row.e.description ?: cat ?: stringResource(R.string.cat_other), fontWeight = FontWeight.ExtraBold, maxLines = 1)
                        Text(listOfNotNull(cat.takeIf { row.e.description != null }, time.format(Instant.ofEpochMilli(row.e.occurredAt)), row.e.createdByName).joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            SOURCES.firstOrNull { it.first == row.e.source }?.let { Tag(stringResource(it.second), CuadraColors.Ink, CuadraColors.Soft) }
                            if (voided) Tag(stringResource(R.string.voided_tag), CuadraColors.Red, CuadraColors.RedSoft)
                            if (row.e.rev == 0L) Tag(stringResource(R.string.history_unsynced), CuadraColors.Ink, CuadraColors.Soft)
                        }
                    }
                    is CashRow.Movement -> {
                        val deposit = row.m.kind == "DEPOSIT"
                        Text(row.m.reason ?: stringResource(if (deposit) R.string.movement_deposit else R.string.movement_withdrawal), fontWeight = FontWeight.ExtraBold, maxLines = 1)
                        Text(listOfNotNull(time.format(Instant.ofEpochMilli(row.m.occurredAt)), row.m.createdByName).joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Tag(stringResource(if (deposit) R.string.movement_deposit else R.string.movement_withdrawal), if (deposit) CuadraColors.Green else CuadraColors.Orange, if (deposit) CuadraColors.GreenSoft else CuadraColors.OrangeSoft)
                            if (voided) Tag(stringResource(R.string.voided_tag), CuadraColors.Red, CuadraColors.RedSoft)
                        }
                    }
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                val (amount, sign, color) = when (row) {
                    is CashRow.Expense -> Triple(row.e.amountMinor, "−", CuadraColors.Red)
                    is CashRow.Movement -> if (row.m.kind == "DEPOSIT") Triple(row.m.amountMinor, "+", CuadraColors.Green) else Triple(row.m.amountMinor, "−", CuadraColors.Orange)
                }
                Text(sign + money(amount), fontWeight = FontWeight.ExtraBold, color = color, textDecoration = if (voided) TextDecoration.LineThrough else null)
                if (canManage && !voided) Text(
                    stringResource(R.string.void_action), color = CuadraColors.Red, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 6.dp).clickableNoRipple { vm.askVoid(VoidTarget(if (row is CashRow.Expense) row.e.id else (row as CashRow.Movement).m.id, row is CashRow.Movement)) },
                )
            }
        }
    }
}

@Composable
private fun ExpenseDialogs(vm: CashViewModel) {
    val ui by vm.ui.collectAsState()
    val categories by vm.categories.collectAsState()
    val canManage by vm.canManage.collectAsState()
    ui.expenseDraft?.let { ExpenseDialog(it, categories, canManage, vm) }
    ui.movementDraft?.let { MovementDialog(it, vm) }
    ui.voidTarget?.let { t ->
        Sheet(vm::closeVoid) {
            Text(stringResource(R.string.void_title), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(R.string.void_hint), style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted)
            VoiceTextField(t.reason, { vm.updateVoid(it.take(200)) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(stringResource(R.string.void_reason)) })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CuadraButton(stringResource(R.string.cancel), vm::closeVoid, Modifier.weight(1f))
                CuadraButton(stringResource(R.string.void_confirm), vm::confirmVoid, Modifier.weight(1.4f), kind = ButtonKind.DANGER)
            }
        }
    }
}

@Composable
private fun ExpenseDialog(d: ExpenseDraft, categories: List<ExpenseCategoryEntity>, canManage: Boolean, vm: CashViewModel) {
    val fmt = LocalMoney.current
    val entered = Money.parse(d.amount, fmt.decimals)?.minor
    Sheet(vm::closeExpense) {
        Text(stringResource(R.string.expense_new_title), style = MaterialTheme.typography.headlineMedium)
        OutlinedTextField(d.amount, { vm.updateExpense(d.copy(amount = it.take(14))) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(stringResource(R.string.expense_amount)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
        VoiceTextField(d.description, { vm.updateExpense(d.copy(description = it.take(120))) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(stringResource(R.string.expense_description)) }, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
        Text(stringResource(R.string.expense_category), fontWeight = FontWeight.Bold)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            categories.forEach { c -> CuadraChip(categoryLabel(c), d.categoryId == c.id, { vm.updateExpense(d.copy(categoryId = if (d.categoryId == c.id) null else c.id)) }) }
        }
        // Un cajero solo saca dinero del cajón; el resto de orígenes lo registra quien administra.
        if (canManage) {
            Text(stringResource(R.string.expense_source), fontWeight = FontWeight.Bold)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SOURCES.forEach { (s, label) -> CuadraChip(stringResource(label), d.source == s, { vm.updateExpense(d.copy(source = s)) }) }
            }
        }
        if (d.source == "CASH_DRAWER" || !canManage) Text(stringResource(R.string.expense_cash_hint), style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted)
        else Text(stringResource(R.string.expense_other_hint), style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CuadraButton(stringResource(R.string.cancel), vm::closeExpense, Modifier.weight(1f))
            CuadraButton(stringResource(R.string.save) + (entered?.takeIf { it > 0 }?.let { " · " + fmt.format(it) } ?: ""), vm::saveExpense, Modifier.weight(1.6f), kind = ButtonKind.PRIMARY, enabled = entered != null && entered > 0)
        }
    }
}

@Composable
private fun MovementDialog(d: MovementDraft, vm: CashViewModel) {
    val fmt = LocalMoney.current
    val entered = Money.parse(d.amount, fmt.decimals)?.minor
    val deposit = d.kind == "DEPOSIT"
    Sheet(vm::closeMovement) {
        Text(stringResource(if (deposit) R.string.movement_deposit_title else R.string.movement_withdraw_title), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(if (deposit) R.string.movement_deposit_hint else R.string.movement_withdraw_hint), style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted)
        OutlinedTextField(d.amount, { vm.updateMovement(d.copy(amount = it.take(14))) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(stringResource(R.string.expense_amount)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
        VoiceTextField(d.reason, { vm.updateMovement(d.copy(reason = it.take(120))) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(stringResource(R.string.movement_reason)) }, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CuadraButton(stringResource(R.string.cancel), vm::closeMovement, Modifier.weight(1f))
            CuadraButton(stringResource(R.string.save), vm::saveMovement, Modifier.weight(1.6f), kind = ButtonKind.PRIMARY, enabled = entered != null && entered > 0)
        }
    }
}

private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier = this.then(Modifier.clickable(onClick = onClick))

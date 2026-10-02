package com.cuadra.caja.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.cuadra.caja.R
import com.cuadra.caja.core.model.Money
import com.cuadra.caja.data.local.ExpenseCategoryEntity
import com.cuadra.caja.data.local.ExpenseTotals
import com.cuadra.caja.data.local.ShiftEntity
import com.cuadra.caja.ui.CashActions
import com.cuadra.caja.ui.CashRow
import com.cuadra.caja.ui.CashUi
import com.cuadra.caja.ui.CashViewModel
import com.cuadra.caja.ui.ExpenseDraft
import com.cuadra.caja.domain.BusinessCalendar
import com.cuadra.caja.ui.common.RangePicker
import com.cuadra.caja.ui.MovementDraft
import com.cuadra.caja.ui.VoidTarget
import com.cuadra.caja.ui.common.ButtonKind
import com.cuadra.caja.ui.common.ButtonRow
import com.cuadra.caja.ui.common.ChipFlow
import com.cuadra.caja.ui.common.ChipGrid
import com.cuadra.caja.ui.common.CuadraButton
import com.cuadra.caja.ui.common.CuadraCard
import com.cuadra.caja.ui.common.CuadraChip
import com.cuadra.caja.ui.common.LinkAction
import com.cuadra.caja.ui.CategoryManagerUi
import com.cuadra.caja.ui.asString
import com.cuadra.caja.ui.common.LocalMoney
import com.cuadra.caja.ui.common.MoneyText
import com.cuadra.caja.ui.common.NumberField
import com.cuadra.caja.ui.common.ScreenFrame
import com.cuadra.caja.ui.common.Sheet
import com.cuadra.caja.ui.common.SplitRow
import com.cuadra.caja.ui.common.Tag
import com.cuadra.caja.ui.common.TagRow
import com.cuadra.caja.ui.common.Text
import com.cuadra.caja.ui.common.VoiceTextField
import com.cuadra.caja.ui.common.money
import com.cuadra.caja.ui.theme.CuadraColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

private val SOURCES = listOf("CASH_DRAWER" to R.string.source_CASH_DRAWER, "BANK" to R.string.source_BANK, "CARD" to R.string.source_CARD, "OWNER" to R.string.source_OWNER, "OTHER" to R.string.source_OTHER)

/** Nombre de una categoría: las de fábrica se traducen por su clave; las creadas por el negocio traen su propio nombre. */
@Composable
internal fun categoryLabel(c: ExpenseCategoryEntity): String = categoryLabel(c.name?.takeIf { it.isNotBlank() } ?: c.key.orEmpty())

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
fun ExpensesScreen(vm: CashViewModel, hasShifts: Boolean, onShift: () -> Unit) {
    val ui by vm.ui.collectAsState()
    val totals by vm.totals.collectAsState()
    val rows by vm.rows.collectAsState()
    val canManage by vm.canManage.collectAsState()
    val shift by vm.currentShift.collectAsState()
    val categories by vm.categories.collectAsState()
    val calendar by vm.calendar.collectAsState()
    com.cuadra.caja.ui.common.Refreshing(vm.refresher) { ExpensesContent(ui, totals, rows, canManage, shift, categories, calendar, hasShifts, vm, onShift) }
}

/** Gastos sin ViewModel (estado + acciones): es lo que dibuja la guardia de diseño. */
@Composable
fun ExpensesContent(
    ui: CashUi, totals: ExpenseTotals, rows: List<CashRow>, canManage: Boolean, shift: ShiftEntity?, categories: List<ExpenseCategoryEntity>,
    calendar: BusinessCalendar?, hasShifts: Boolean, actions: CashActions, onShift: () -> Unit, nowMillis: Long = System.currentTimeMillis(),
) {
    val zone = calendar?.zone ?: ZoneId.of("UTC")
    val locale: Locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val time = com.cuadra.caja.domain.ClockFormat.dateTime(locale, zone)

    ScreenFrame(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        footer = {
            // Las acciones principales quedan fijas abajo; con letra grande se apilan en vez de apretarse.
            ButtonRow(Modifier.padding(bottom = 8.dp)) {
                CuadraButton(stringResource(R.string.expenses_new), actions::openExpense, Modifier.share(1.4f), kind = ButtonKind.PRIMARY)
                CuadraButton(stringResource(R.string.expenses_deposit), { actions.openMovement("DEPOSIT") }, Modifier.share(1f))
                if (canManage) CuadraButton(stringResource(R.string.expenses_withdraw), { actions.openMovement("WITHDRAWAL") }, Modifier.share(1f))
            }
        },
    ) {
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { Text(stringResource(R.string.expenses_title), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 16.dp)) }
            item { RangePicker(ui.range, calendar, actions::setRange, nowMillis = nowMillis) }
            if (canManage) item { LinkAction(stringResource(R.string.expcat_manage), actions::openCategories) }
            item {
                ButtonRow(spacing = 10.dp) {
                    Kpi(stringResource(R.string.expenses_out_cash), money(totals.cashDrawer), CuadraColors.Red, Modifier.share(1f))
                    Kpi(stringResource(R.string.expenses_out_other), money(totals.other), CuadraColors.Ink, Modifier.share(1f))
                }
            }
            if (hasShifts) item {
                CuadraCard(onClick = onShift) {
                    SplitRow(end = { Text(stringResource(if (shift != null) R.string.shift_close_short else R.string.shift_open_short), color = CuadraColors.Green, fontWeight = FontWeight.ExtraBold, maxLines = 2) }) {
                        Column {
                            Text(stringResource(R.string.shift_title), fontWeight = FontWeight.ExtraBold)
                            Text(stringResource(if (shift != null) R.string.shift_chip_open else R.string.shift_chip_none), style = MaterialTheme.typography.bodyMedium, color = if (shift != null) CuadraColors.Green else CuadraColors.Orange)
                        }
                    }
                }
            }
            if (rows.isEmpty()) item {
                Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.expenses_empty), color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                }
            } else items(rows, key = { (if (it is CashRow.Expense) "e-" + it.e.id else "m-" + (it as CashRow.Movement).m.id) }) { CashRowItem(it, time, canManage, actions) }
        }
    }
    ExpenseDialogs(ui, categories, canManage, actions)
}

@Composable
private fun Kpi(label: String, value: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    CuadraCard(modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted)
            MoneyText(value, style = MaterialTheme.typography.headlineMedium, color = color, fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
private fun CashRowItem(row: CashRow, time: DateTimeFormatter, canManage: Boolean, actions: CashActions) {
    val voided = when (row) { is CashRow.Expense -> row.e.voided; is CashRow.Movement -> row.m.voided }
    val (amount, sign, color) = when (row) {
        is CashRow.Expense -> Triple(row.e.amountMinor, "−", CuadraColors.Red)
        is CashRow.Movement -> if (row.m.kind == "DEPOSIT") Triple(row.m.amountMinor, "+", CuadraColors.Green) else Triple(row.m.amountMinor, "−", CuadraColors.Orange)
    }
    CuadraCard(Modifier.fillMaxWidth()) {
        SplitRow(end = {
            Column(horizontalAlignment = Alignment.End) {
                MoneyText(sign + money(amount), fontWeight = FontWeight.ExtraBold, color = color, textDecoration = if (voided) TextDecoration.LineThrough else null)
                if (canManage && !voided) LinkAction(stringResource(R.string.void_action), { actions.askVoid(VoidTarget(if (row is CashRow.Expense) row.e.id else (row as CashRow.Movement).m.id, row is CashRow.Movement)) }, color = CuadraColors.Red)
            }
        }) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                when (row) {
                    is CashRow.Expense -> {
                        val cat = row.category?.let { categoryLabel(it) }
                        Text(row.e.description ?: cat ?: stringResource(R.string.cat_other), fontWeight = FontWeight.ExtraBold, maxLines = 2, ellipsize = true)
                        Text(listOfNotNull(cat.takeIf { row.e.description != null }, time.format(Instant.ofEpochMilli(row.e.occurredAt)), row.e.createdByName).joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted, maxLines = 3, ellipsize = true)
                        TagRow {
                            SOURCES.firstOrNull { it.first == row.e.source }?.let { Tag(stringResource(it.second), CuadraColors.Ink, CuadraColors.Soft) }
                            if (voided) Tag(stringResource(R.string.voided_tag), CuadraColors.Red, CuadraColors.RedSoft)
                            if (row.e.rev == 0L) Tag(stringResource(R.string.history_unsynced), CuadraColors.Ink, CuadraColors.Soft)
                        }
                    }
                    is CashRow.Movement -> {
                        val deposit = row.m.kind == "DEPOSIT"
                        Text(row.m.reason ?: stringResource(if (deposit) R.string.movement_deposit else R.string.movement_withdrawal), fontWeight = FontWeight.ExtraBold, maxLines = 2, ellipsize = true)
                        Text(listOfNotNull(time.format(Instant.ofEpochMilli(row.m.occurredAt)), row.m.createdByName).joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted, maxLines = 3, ellipsize = true)
                        TagRow {
                            Tag(stringResource(if (deposit) R.string.movement_deposit else R.string.movement_withdrawal), if (deposit) CuadraColors.Green else CuadraColors.Orange, if (deposit) CuadraColors.GreenSoft else CuadraColors.OrangeSoft)
                            if (voided) Tag(stringResource(R.string.voided_tag), CuadraColors.Red, CuadraColors.RedSoft)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExpenseDialogs(ui: CashUi, categories: List<ExpenseCategoryEntity>, canManage: Boolean, actions: CashActions) {
    ui.expenseDraft?.let { ExpenseDialog(it, categories, canManage, actions) }
    ui.movementDraft?.let { MovementDialog(it, actions) }
    ui.categoryManager?.let { if (canManage) CategoryManagerDialog(it, categories, actions) }
    ui.voidTarget?.let { t ->
        Sheet(actions::closeVoid, actions = {
            ButtonRow {
                CuadraButton(stringResource(R.string.cancel), actions::closeVoid, Modifier.share(1f))
                CuadraButton(stringResource(R.string.void_confirm), actions::confirmVoid, Modifier.share(1.4f), kind = ButtonKind.DANGER)
            }
        }) {
            Text(stringResource(R.string.void_title), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(R.string.void_hint), style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted)
            VoiceTextField(t.reason, { actions.updateVoid(it.take(200)) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(stringResource(R.string.void_reason)) })
        }
    }
}

@Composable
fun ExpenseDialog(d: ExpenseDraft, categories: List<ExpenseCategoryEntity>, canManage: Boolean, actions: CashActions) {
    val fmt = LocalMoney.current
    val entered = Money.parse(d.amount, fmt.decimals)?.minor
    Sheet(actions::closeExpense, actions = {
        ButtonRow {
            CuadraButton(stringResource(R.string.cancel), actions::closeExpense, Modifier.share(1f))
            CuadraButton(stringResource(R.string.save) + (entered?.takeIf { it > 0 }?.let { " · " + fmt.format(it) } ?: ""), actions::saveExpense, Modifier.share(1.6f), kind = ButtonKind.PRIMARY, enabled = entered != null && entered > 0)
        }
    }) {
        Text(stringResource(R.string.expense_new_title), style = MaterialTheme.typography.headlineMedium)
        NumberField(d.amount, { actions.updateExpense(d.copy(amount = it.take(14))) }, label = { Text(stringResource(R.string.expense_amount), maxLines = 1) })
        VoiceTextField(d.description, { actions.updateExpense(d.copy(description = it.take(120))) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(stringResource(R.string.expense_description)) }, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
        Text(stringResource(R.string.expense_category), fontWeight = FontWeight.Bold)
        ChipFlow {
            categories.forEach { c -> CuadraChip(categoryLabel(c), d.categoryId == c.id, { actions.updateExpense(d.copy(categoryId = if (d.categoryId == c.id) null else c.id)) }) }
        }
        // Un cajero solo saca dinero del cajón; el resto de orígenes lo registra quien administra.
        if (canManage) {
            Text(stringResource(R.string.expense_source), fontWeight = FontWeight.Bold)
            ChipFlow { SOURCES.forEach { (s, label) -> CuadraChip(stringResource(label), d.source == s, { actions.updateExpense(d.copy(source = s)) }) } }
        }
        if (d.source == "CASH_DRAWER" || !canManage) Text(stringResource(R.string.expense_cash_hint), style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted)
        else Text(stringResource(R.string.expense_other_hint), style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted)
    }
}

/** Categorías de gastos: renombrar, archivar y crear (dueño y admin, con conexión). Lo archivado deja de ofrecerse; los gastos ya anotados lo conservan. */
@Composable
fun CategoryManagerDialog(m: CategoryManagerUi, categories: List<ExpenseCategoryEntity>, actions: CashActions) {
    Sheet(actions::closeCategories, actions = {
        if (m.editing) ButtonRow {
            CuadraButton(stringResource(R.string.cancel), actions::cancelCategoryEdit, Modifier.share(1f), enabled = !m.saving)
            CuadraButton(stringResource(R.string.save), actions::saveCategory, Modifier.share(1.3f), kind = ButtonKind.PRIMARY, enabled = m.name.isNotBlank() && !m.saving)
        } else ButtonRow {
            CuadraButton(stringResource(R.string.close), actions::closeCategories, Modifier.share(1f))
            CuadraButton(stringResource(R.string.expcat_new), { actions.startCategoryEdit(null) }, Modifier.share(1.3f), kind = ButtonKind.DARK)
        }
    }) {
        Text(stringResource(R.string.expcat_title), style = MaterialTheme.typography.headlineMedium)
        if (m.editing) {
            VoiceTextField(m.name, actions::updateCategoryName, Modifier.fillMaxWidth(), singleLine = true, label = { Text(stringResource(R.string.expcat_name)) }, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
        } else {
            Text(stringResource(R.string.expcat_hint), style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted)
            categories.forEach { c ->
                CuadraCard {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(categoryLabel(c), fontWeight = FontWeight.Bold)
                        ChipFlow(spacing = 0.dp) {
                            LinkAction(stringResource(R.string.expcat_rename), { actions.startCategoryEdit(c.id) })
                            LinkAction(stringResource(R.string.expcat_archive), { actions.archiveCategory(c.id) }, color = CuadraColors.Red)
                        }
                    }
                }
            }
        }
        m.error?.let { Text(it.asString(), color = CuadraColors.Red, fontWeight = FontWeight.Bold) }
    }
}

@Composable
fun MovementDialog(d: MovementDraft, actions: CashActions) {
    val fmt = LocalMoney.current
    val entered = Money.parse(d.amount, fmt.decimals)?.minor
    val deposit = d.kind == "DEPOSIT"
    Sheet(actions::closeMovement, actions = {
        ButtonRow {
            CuadraButton(stringResource(R.string.cancel), actions::closeMovement, Modifier.share(1f))
            CuadraButton(stringResource(R.string.save), actions::saveMovement, Modifier.share(1.6f), kind = ButtonKind.PRIMARY, enabled = entered != null && entered > 0)
        }
    }) {
        Text(stringResource(if (deposit) R.string.movement_deposit_title else R.string.movement_withdraw_title), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(if (deposit) R.string.movement_deposit_hint else R.string.movement_withdraw_hint), style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted)
        NumberField(d.amount, { actions.updateMovement(d.copy(amount = it.take(14))) }, label = { Text(stringResource(R.string.expense_amount), maxLines = 1) })
        VoiceTextField(d.reason, { actions.updateMovement(d.copy(reason = it.take(120))) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(stringResource(R.string.movement_reason)) }, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
    }
}

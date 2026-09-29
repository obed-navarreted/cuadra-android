package com.cuadra.caja.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.cuadra.caja.AppContainer
import com.cuadra.caja.R
import com.cuadra.caja.data.local.CreditEntity
import com.cuadra.caja.data.local.CustomerEntity
import com.cuadra.caja.ui.CreditFilter
import com.cuadra.caja.ui.CreditsViewModel
import com.cuadra.caja.ui.CustomerDraft
import com.cuadra.caja.ui.LedgerMode
import com.cuadra.caja.ui.LedgerRow
import com.cuadra.caja.ui.MovementUi
import com.cuadra.caja.ui.PayTarget
import com.cuadra.caja.ui.ShareRequest
import com.cuadra.caja.ui.common.Avatar
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
import java.util.Locale

private const val DAY = 24L * 60 * 60 * 1000
private fun initials(name: String) = name.trim().split(Regex("\\s+")).take(2).joinToString("") { it.take(1).uppercase() }

@Composable
private fun daysAgo(at: Long): String {
    val d = ((System.currentTimeMillis() - at) / DAY).toInt().coerceAtLeast(0)
    return if (d == 0) stringResource(R.string.credits_today) else stringResource(R.string.credits_days_ago, d)
}

/** La libreta: quién debe, cuánto y desde cuándo. Un cliente es opcional: basta el nombre escrito al fiar. */
@Composable
fun CreditsScreen(vm: CreditsViewModel, container: AppContainer) {
    val ui by vm.ui.collectAsState()
    val rows by vm.rows.collectAsState()
    val customers by vm.customers.collectAsState()
    val totals by vm.totals.collectAsState()
    val detail by vm.detail.collectAsState()
    val canManage by vm.canManage.collectAsState()

    val d = detail
    if (ui.openCustomerId != null && d != null) {
        CustomerDetail(d, canManage, vm)
    } else {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.credits_title), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 16.dp))
            Row(
                Modifier.fillMaxWidth().background(CuadraColors.Ink, RoundedCornerShape(22.dp)).padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.credits_owed) + " · " + pluralStringResource(R.plurals.credits_pending, totals.openCount, totals.openCount), color = CuadraColors.Line, style = MaterialTheme.typography.bodyMedium)
                    Text(money(totals.openTotal), color = Color(0xFFF2B58A), style = MaterialTheme.typography.displayLarge.copy(fontSize = androidx.compose.ui.unit.TextUnit(34f, androidx.compose.ui.unit.TextUnitType.Sp)), maxLines = 1)
                    if (totals.overdueCount > 0) Text(stringResource(R.string.credits_overdue_summary, money(totals.overdueTotal), CreditsViewModel.OVERDUE_DAYS), color = CuadraColors.Line, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CuadraChip(stringResource(R.string.credits_tab_credits), ui.mode == LedgerMode.CREDITS, { vm.setMode(LedgerMode.CREDITS) }, Modifier.weight(1f))
                CuadraChip(stringResource(R.string.credits_tab_customers), ui.mode == LedgerMode.CUSTOMERS, { vm.setMode(LedgerMode.CUSTOMERS) }, Modifier.weight(1f))
            }
            VoiceTextField(ui.query, vm::setQuery, Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text(stringResource(R.string.credits_search_hint)) })
            if (ui.mode == LedgerMode.CREDITS) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        CreditFilter.OPEN to R.string.credits_filter_open, CreditFilter.WITH to R.string.credits_filter_customer, CreditFilter.WITHOUT to R.string.credits_filter_note,
                        CreditFilter.OLD to R.string.credits_filter_old, CreditFilter.PAID to R.string.credits_filter_paid,
                    ).forEach { (f, label) -> CuadraChip(stringResource(label), ui.filter == f, { vm.setFilter(f) }) }
                }
            }
            Box(Modifier.weight(1f)) {
                if (ui.mode == LedgerMode.CREDITS) {
                    if (rows.isEmpty()) Empty(R.string.credits_empty) else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(rows, key = { r -> if (r is LedgerRow.Customer) "c-" + r.customer.id else "n-" + (r as LedgerRow.Note).item.credit.id }) { r ->
                            when (r) {
                                is LedgerRow.Customer -> CustomerRow(r, vm)
                                is LedgerRow.Note -> NoteRow(r.item.credit, vm)
                            }
                        }
                    }
                } else {
                    if (customers.isEmpty()) Empty(R.string.credits_customers_empty) else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(customers, key = { it.id }) { CustomerListItem(it, vm) }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (ui.mode == LedgerMode.CUSTOMERS) CuadraButton(stringResource(R.string.customer_new), { vm.openCustomerEditor() }, Modifier.weight(1f))
                CuadraButton(stringResource(R.string.credits_add_manual), vm::openManual, Modifier.weight(1f), kind = ButtonKind.DARK)
            }
        }
    }
    CreditDialogs(vm, container, customers, canManage)
}

@Composable
private fun Empty(res: Int) = Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(stringResource(res), color = MaterialTheme.colorScheme.onSurfaceVariant) }

@Composable
private fun CustomerRow(r: LedgerRow.Customer, vm: CreditsViewModel) {
    CuadraCard(onClick = { vm.openCustomer(r.customer.id) }) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Avatar(initials(r.customer.name))
                Column(Modifier.weight(1f)) {
                    Text(r.customer.name, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                    Text(pluralStringResource(R.plurals.credits_pending, r.credits.size, r.credits.size) + " · " + daysAgo(r.sortKey), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(money(r.balanceMinor), fontWeight = FontWeight.ExtraBold, color = CuadraColors.Orange)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CuadraButton(stringResource(R.string.credits_pay), { vm.askPay(PayTarget(null, r.customer.id, r.customer.name, r.balanceMinor, r.customer.phone)) }, Modifier.weight(1f), height = 48)
                CuadraButton(stringResource(R.string.credits_remind), { vm.share(ShareRequest.Reminder(null, r.customer.id)) }, Modifier.weight(1f), kind = ButtonKind.WHATSAPP, height = 48)
            }
        }
    }
}

@Composable
private fun NoteRow(c: CreditEntity, vm: CreditsViewModel) {
    val paid = c.status == "PAID"
    CuadraCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(c.debtorLabel, fontWeight = FontWeight.ExtraBold, maxLines = 2)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Tag(stringResource(R.string.credits_note_tag), CuadraColors.Ink, CuadraColors.Soft)
                        Text(daysAgo(c.createdAt), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (paid) Tag(stringResource(R.string.credits_paid_tag), CuadraColors.Green, CuadraColors.GreenSoft)
                    }
                    c.lastReminderAt?.let { Text(stringResource(R.string.credits_reminded, ((System.currentTimeMillis() - it) / DAY).toInt()), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                Text(money(if (paid) c.amountMinor else c.balanceMinor), fontWeight = FontWeight.ExtraBold, color = if (paid) CuadraColors.Muted else CuadraColors.Orange, textDecoration = if (paid) TextDecoration.LineThrough else null)
            }
            if (!paid) {
                val target = PayTarget(c.id, null, c.debtorLabel, c.balanceMinor, c.debtorPhone)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CuadraButton(stringResource(R.string.credits_pay), { vm.askPay(target) }, Modifier.weight(1f), height = 48)
                    CuadraButton(stringResource(R.string.credits_settle), { vm.askPay(target.copy(settleAll = true)) }, Modifier.weight(1f), height = 48)
                    CuadraButton(stringResource(R.string.credits_remind), { vm.share(ShareRequest.Reminder(c.id, null)) }, Modifier.weight(1.1f), kind = ButtonKind.WHATSAPP, height = 48)
                }
                CuadraButton(stringResource(R.string.customer_link), { vm.askLink(c.id) }, Modifier.fillMaxWidth(), height = 44)
            }
        }
    }
}

@Composable
private fun CustomerListItem(c: CustomerEntity, vm: CreditsViewModel) {
    CuadraCard(onClick = { vm.openCustomer(c.id) }) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Avatar(initials(c.name))
            Column(Modifier.weight(1f)) {
                Text(c.name, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                Text(c.phone?.let { "+$it" } ?: "—", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (c.balanceMinor > 0) Text(money(c.balanceMinor), fontWeight = FontWeight.ExtraBold, color = CuadraColors.Orange) else Text(stringResource(R.string.customer_none_owed), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** La cuenta de un cliente: cuánto debe, su límite, y todo lo que se le fió y abonó, del más reciente al más viejo. */
@Composable
private fun CustomerDetail(d: com.cuadra.caja.ui.CustomerDetailUi, canManage: Boolean, vm: CreditsViewModel) {
    val c = d.customer
    val zone = ZoneId.systemDefault()
    val locale: Locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val date = DateTimeFormatter.ofPattern("d MMM", locale).withZone(zone)
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CuadraButton("‹", { vm.openCustomer(null) }, Modifier.size(56.dp))
            Column(Modifier.weight(1f)) {
                Text(c.name, style = MaterialTheme.typography.headlineMedium, maxLines = 1)
                Text(c.phone?.let { "+$it" } ?: "—", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            CuadraButton("✎", { vm.openCustomerEditor(CustomerDraft(c.id, c.name, c.phone.orEmpty(), c.notes.orEmpty(), c.creditLimitMinor?.let { com.cuadra.caja.core.model.Money(it).minor.toString() }.orEmpty())) }, Modifier.size(56.dp))
        }
        Column(Modifier.fillMaxWidth().background(CuadraColors.Ink, RoundedCornerShape(22.dp)).padding(horizontal = 20.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.credits_owes), color = CuadraColors.Line, style = MaterialTheme.typography.bodyMedium)
            Text(money(c.balanceMinor), color = Color(0xFFF2B58A), style = MaterialTheme.typography.displayLarge.copy(fontSize = androidx.compose.ui.unit.TextUnit(36f, androidx.compose.ui.unit.TextUnitType.Sp)), maxLines = 1)
            c.creditLimitMinor?.let { lim ->
                Text(stringResource(R.string.credits_limit, money(lim)), color = CuadraColors.Line, style = MaterialTheme.typography.bodyMedium)
                Box(Modifier.fillMaxWidth().height(6.dp).background(Color(0xFF3A3832), RoundedCornerShape(3.dp))) {
                    Box(Modifier.fillMaxWidth((c.balanceMinor.toFloat() / lim.coerceAtLeast(1)).coerceIn(0f, 1f)).height(6.dp).background(Color(0xFFF2B58A), RoundedCornerShape(3.dp)))
                }
            }
            Text(c.lastReminderAt?.let { stringResource(R.string.credits_reminded, ((System.currentTimeMillis() - it) / DAY).toInt()) } ?: stringResource(R.string.credits_never_reminded), color = CuadraColors.Line, style = MaterialTheme.typography.bodyMedium)
        }
        CuadraButton(stringResource(R.string.credit_pay_title), { vm.askPay(PayTarget(null, c.id, c.name, c.balanceMinor, c.phone)) }, Modifier.fillMaxWidth(), kind = ButtonKind.PRIMARY, enabled = c.balanceMinor > 0)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CuadraButton(stringResource(R.string.credits_remind), { vm.share(ShareRequest.Reminder(null, c.id)) }, Modifier.weight(1f), kind = ButtonKind.WHATSAPP, enabled = c.balanceMinor > 0, height = 48)
            CuadraButton(stringResource(R.string.customer_statement), { vm.share(ShareRequest.Statement(c.id)) }, Modifier.weight(1f), height = 48)
        }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (d.openCredits.isNotEmpty()) {
                item { Text(stringResource(R.string.credits_open_credits).uppercase(), style = MaterialTheme.typography.labelLarge, color = CuadraColors.Muted) }
                items(d.openCredits, key = { "open-" + it.id }) { cr ->
                    CuadraCard {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text(cr.note ?: cr.debtorLabel, fontWeight = FontWeight.Bold, maxLines = 1)
                                Text(date.format(Instant.ofEpochMilli(cr.createdAt)), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(money(cr.balanceMinor), fontWeight = FontWeight.ExtraBold, color = CuadraColors.Orange)
                            CuadraButton(stringResource(R.string.credits_pay), { vm.askPay(PayTarget(cr.id, null, cr.debtorLabel, cr.balanceMinor, cr.debtorPhone ?: c.phone)) }, height = 44)
                            if (canManage) CuadraButton(stringResource(R.string.writeoff_confirm), { vm.askWriteOff(cr.id) }, height = 44)
                        }
                    }
                }
            }
            item { Text(stringResource(R.string.customer_movements).uppercase(), style = MaterialTheme.typography.labelLarge, color = CuadraColors.Muted, modifier = Modifier.padding(top = 6.dp)) }
            items(d.movements, key = { (if (it.isPayment) "p-" else "c-") + (it.paymentId ?: it.creditId) }) { m -> MovementRow(m, date, canManage, vm) }
        }
    }
}

@Composable
private fun MovementRow(m: MovementUi, date: DateTimeFormatter, canManage: Boolean, vm: CreditsViewModel) {
    CuadraCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (m.isPayment) Tag(stringResource(R.string.customer_movement_payment), CuadraColors.Green, CuadraColors.GreenSoft) else Tag(stringResource(R.string.customer_movement_credit), CuadraColors.Orange, CuadraColors.OrangeSoft)
            Column(Modifier.weight(1f)) {
                Text(date.format(Instant.ofEpochMilli(m.at)), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                Text(listOfNotNull(if (m.isPayment) null else m.label, m.member).joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            if (m.voided) Tag(stringResource(R.string.customer_movement_voided), CuadraColors.Red, CuadraColors.RedSoft)
            Text(
                (if (m.isPayment) "− " else "+ ") + money(m.amountMinor), fontWeight = FontWeight.ExtraBold,
                color = if (m.isPayment) CuadraColors.Green else CuadraColors.Orange, textDecoration = if (m.voided) TextDecoration.LineThrough else null,
            )
        }
        if (m.isPayment && !m.voided && canManage && m.paymentId != null) {
            Box(Modifier.padding(top = 6.dp)) { CuadraButton(stringResource(R.string.void_payment), { vm.voidPayment(m.groupId ?: m.paymentId) }, Modifier.fillMaxWidth(), height = 44) }
        }
    }
}

@Composable
private fun CreditDialogs(vm: CreditsViewModel, container: AppContainer, customers: List<CustomerEntity>, canManage: Boolean) {
    val ui by vm.ui.collectAsState()
    ui.pay?.let { PayDialog(it, vm) }
    ui.manual?.let { ManualDialog(it, vm) }
    ui.customerEditor?.let { CustomerEditorDialog(it, vm) }
    ui.linkingCreditId?.let { LinkDialog(customers, vm) }
    ui.writeOffCreditId?.let { if (canManage) WriteOffDialog(vm) }
    ui.share?.let { ShareDialog(container, it) { vm.share(null) } }
    ui.messageRes?.let { res ->
        Sheet(vm::dismissMessage) {
            Text(stringResource(res), style = MaterialTheme.typography.bodyLarge)
            CuadraButton(stringResource(R.string.close), vm::dismissMessage, Modifier.fillMaxWidth(), kind = ButtonKind.DARK)
        }
    }
}

@Composable
private fun PayDialog(target: PayTarget, vm: CreditsViewModel) {
    val fmt = LocalMoney.current
    // "Saldar" arranca con todo lo que debe; "Abonar" con el monto vacío para escribir cuánto pagó.
    var amount by remember(target) { mutableStateOf(if (target.settleAll) java.math.BigDecimal.valueOf(target.balanceMinor, fmt.decimals).toPlainString() else "") }
    var method by remember(target) { mutableStateOf("CASH") }
    var reference by remember(target) { mutableStateOf("") }
    var receipt by remember(target) { mutableStateOf(true) }
    val entered = com.cuadra.caja.core.model.Money.parse(amount, fmt.decimals)?.minor
    val left = entered?.let { (target.balanceMinor - it).coerceAtLeast(0) }
    Sheet(vm::closePay) {
        Text(stringResource(R.string.credit_pay_title), style = MaterialTheme.typography.headlineMedium)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Avatar(initials(target.title), size = 40)
            Column(Modifier.weight(1f)) {
                Text(target.title, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                Text(stringResource(R.string.credits_owes) + " " + fmt.format(target.balanceMinor), style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Orange)
            }
        }
        OutlinedTextField(
            amount, { amount = it.take(14) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(stringResource(R.string.credit_pay_amount)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
        if (entered != null && entered > target.balanceMinor) Text(stringResource(R.string.credit_pay_too_much), color = CuadraColors.Orange, fontWeight = FontWeight.Bold)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("CASH" to R.string.method_CASH, "TRANSFER" to R.string.method_TRANSFER, "CARD" to R.string.method_CARD, "OTHER" to R.string.method_OTHER).forEach { (m, label) ->
                CuadraChip(stringResource(label), method == m, { method = m })
            }
        }
        if (method != "CASH") VoiceTextField(reference, { reference = it.take(60) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(stringResource(R.string.pay_reference_hint)) })
        left?.let { Text(stringResource(R.string.credit_pay_left, fmt.format(it)), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        CuadraChip(stringResource(R.string.credit_pay_send_receipt), receipt, { receipt = !receipt })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CuadraButton(stringResource(R.string.cancel), vm::closePay, Modifier.weight(1f))
            CuadraButton(stringResource(R.string.credit_pay_confirm, entered?.let { fmt.format(it) } ?: ""), { vm.confirmPay(amount, method, reference, receipt) }, Modifier.weight(1.6f), kind = ButtonKind.PRIMARY, enabled = entered != null && entered > 0)
        }
    }
}

@Composable
private fun ManualDialog(d: com.cuadra.caja.ui.ManualDraft, vm: CreditsViewModel) {
    Sheet(vm::closeManual) {
        Text(stringResource(R.string.manual_title), style = MaterialTheme.typography.headlineMedium)
        VoiceTextField(d.debtor, { vm.updateManual(d.copy(debtor = it.take(120))) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(stringResource(R.string.manual_debtor)) }, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
        OutlinedTextField(d.amount, { vm.updateManual(d.copy(amount = it.take(14))) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(stringResource(R.string.manual_amount)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
        OutlinedTextField(d.phone, { vm.updateManual(d.copy(phone = it.take(20))) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(stringResource(R.string.manual_phone)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
        VoiceTextField(d.note, { vm.updateManual(d.copy(note = it.take(200))) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(stringResource(R.string.manual_note)) })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CuadraButton(stringResource(R.string.cancel), vm::closeManual, Modifier.weight(1f))
            CuadraButton(stringResource(R.string.save), vm::saveManual, Modifier.weight(1f), kind = ButtonKind.DARK)
        }
    }
}

@Composable
private fun CustomerEditorDialog(d: CustomerDraft, vm: CreditsViewModel) {
    Sheet(vm::closeCustomerEditor) {
        Text(stringResource(if (d.id == null) R.string.customer_new else R.string.customer_edit), style = MaterialTheme.typography.headlineMedium)
        VoiceTextField(d.name, { vm.updateCustomerEditor(d.copy(name = it.take(120))) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(stringResource(R.string.customer_name)) }, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
        OutlinedTextField(d.phone, { vm.updateCustomerEditor(d.copy(phone = it.take(20))) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(stringResource(R.string.customer_phone)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
        OutlinedTextField(d.limit, { vm.updateCustomerEditor(d.copy(limit = it.take(14))) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(stringResource(R.string.customer_limit)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
        VoiceTextField(d.notes, { vm.updateCustomerEditor(d.copy(notes = it.take(300))) }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.customer_notes)) })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CuadraButton(stringResource(R.string.cancel), vm::closeCustomerEditor, Modifier.weight(1f))
            CuadraButton(stringResource(R.string.save), vm::saveCustomer, Modifier.weight(1f), kind = ButtonKind.DARK)
        }
    }
}

@Composable
private fun LinkDialog(customers: List<CustomerEntity>, vm: CreditsViewModel) {
    Sheet({ vm.askLink(null) }) {
        Text(stringResource(R.string.customer_link_choose), style = MaterialTheme.typography.headlineMedium)
        if (customers.isEmpty()) Text(stringResource(R.string.credits_customers_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
        customers.take(30).forEach { c -> CuadraCard(onClick = { vm.link(c.id) }) { Text(c.name, fontWeight = FontWeight.Bold) } }
        CuadraButton(stringResource(R.string.cancel), { vm.askLink(null) }, Modifier.fillMaxWidth())
    }
}

@Composable
private fun WriteOffDialog(vm: CreditsViewModel) {
    var reason by remember { mutableStateOf("") }
    Sheet({ vm.askWriteOff(null) }) {
        Text(stringResource(R.string.writeoff_title), style = MaterialTheme.typography.headlineMedium)
        VoiceTextField(reason, { reason = it.take(200) }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.writeoff_reason)) })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CuadraButton(stringResource(R.string.cancel), { vm.askWriteOff(null) }, Modifier.weight(1f))
            CuadraButton(stringResource(R.string.writeoff_confirm), { vm.writeOff(reason) }, Modifier.weight(1f), kind = ButtonKind.DANGER, enabled = reason.isNotBlank())
        }
    }
}

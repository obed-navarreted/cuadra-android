package com.cuadra.caja.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.platform.testTag
import com.cuadra.caja.ui.common.TAG_STICKY
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.cuadra.caja.AppContainer
import com.cuadra.caja.R
import com.cuadra.caja.data.local.CreditEntity
import com.cuadra.caja.data.local.CreditTotals
import com.cuadra.caja.data.local.CustomerEntity
import com.cuadra.caja.ui.CreditFilter
import com.cuadra.caja.ui.CreditsActions
import com.cuadra.caja.ui.CreditsUi
import com.cuadra.caja.ui.CreditsViewModel
import com.cuadra.caja.ui.CustomerDetailUi
import com.cuadra.caja.ui.CustomerDraft
import com.cuadra.caja.ui.LedgerMode
import com.cuadra.caja.ui.LedgerRow
import com.cuadra.caja.ui.MovementUi
import com.cuadra.caja.ui.PayTarget
import com.cuadra.caja.ui.ShareRequest
import com.cuadra.caja.ui.common.Avatar
import com.cuadra.caja.ui.common.ButtonKind
import com.cuadra.caja.ui.common.ButtonRow
import com.cuadra.caja.ui.common.ChipFlow
import com.cuadra.caja.ui.common.ChipGrid
import com.cuadra.caja.ui.common.CuadraButton
import com.cuadra.caja.ui.common.CuadraCard
import com.cuadra.caja.ui.common.CuadraChip
import com.cuadra.caja.ui.common.LocalMoney
import com.cuadra.caja.ui.common.MoneyText
import com.cuadra.caja.ui.common.NumberField
import com.cuadra.caja.ui.common.ScreenFrame
import com.cuadra.caja.ui.common.Sheet
import com.cuadra.caja.ui.common.SplitRow
import com.cuadra.caja.ui.common.Tag
import com.cuadra.caja.ui.common.TagRow
import com.cuadra.caja.ui.common.Text
import com.cuadra.caja.ui.common.TitleBar
import com.cuadra.caja.ui.common.VoiceTextField
import com.cuadra.caja.ui.common.money
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
    com.cuadra.caja.ui.common.Refreshing(vm.refresher) { CreditsContent(ui, rows, customers, totals, detail, canManage, vm, shareDialog = { request, dismiss -> ShareDialog(container, request, dismiss) }) }
}

/** La libreta sin ViewModel (estado + acciones): es lo que dibuja la guardia de diseño. */
@Composable
fun CreditsContent(
    ui: CreditsUi, rows: List<LedgerRow>, customers: List<CustomerEntity>, totals: CreditTotals, detail: CustomerDetailUi?, canManage: Boolean, actions: CreditsActions,
    shareDialog: @Composable (ShareRequest, () -> Unit) -> Unit = { _, _ -> },
) {
    if (ui.openCustomerId != null && detail != null) {
        CustomerDetail(detail, canManage, actions)
    } else {
        CreditsList(ui, rows, customers, totals, actions)
    }
    CreditDialogs(ui, customers, canManage, actions, shareDialog)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CreditsList(ui: CreditsUi, rows: List<LedgerRow>, customers: List<CustomerEntity>, totals: CreditTotals, actions: CreditsActions) {
    ScreenFrame(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        footer = {
            ButtonRow(Modifier.padding(bottom = 8.dp)) {
                if (ui.mode == LedgerMode.CUSTOMERS) CuadraButton(stringResource(R.string.customer_new), { actions.openCustomerEditor() }, Modifier.share(1f))
                CuadraButton(stringResource(R.string.credits_add_manual), actions::openManual, Modifier.share(1f), kind = ButtonKind.DARK)
            }
        },
    ) {
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { Text(stringResource(R.string.credits_title), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 16.dp)) }
            item {
                Column(Modifier.fillMaxWidth().background(CuadraColors.Ink, RoundedCornerShape(22.dp)).padding(horizontal = 20.dp, vertical = 14.dp)) {
                    Text(stringResource(R.string.credits_owed) + " · " + pluralStringResource(R.plurals.credits_pending, totals.openCount, totals.openCount), color = CuadraColors.Line, style = MaterialTheme.typography.bodyMedium)
                    MoneyText(money(totals.openTotal), Modifier.fillMaxWidth(), color = Color(0xFFF2B58A), style = MaterialTheme.typography.displaySmall)
                    if (totals.overdueCount > 0) Text(stringResource(R.string.credits_overdue_summary, money(totals.overdueTotal), ui.overdueDays), color = CuadraColors.Line, style = MaterialTheme.typography.bodyMedium)
                }
            }
            item {
                ChipGrid(Modifier.fillMaxWidth()) {
                    CuadraChip(stringResource(R.string.credits_tab_credits), ui.mode == LedgerMode.CREDITS, { actions.setMode(LedgerMode.CREDITS) })
                    CuadraChip(stringResource(R.string.credits_tab_customers), ui.mode == LedgerMode.CUSTOMERS, { actions.setMode(LedgerMode.CUSTOMERS) })
                }
            }
            stickyHeader {
                Box(Modifier.fillMaxWidth().testTag(TAG_STICKY).background(CuadraColors.Bg).padding(vertical = 2.dp)) {
                    VoiceTextField(ui.query, actions::setQuery, Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text(stringResource(R.string.credits_search_hint), maxLines = 1) })
                }
            }
            if (ui.mode == LedgerMode.CREDITS) {
                item {
                    ChipFlow {
                        listOf(
                            CreditFilter.OPEN to R.string.credits_filter_open, CreditFilter.WITH to R.string.credits_filter_customer, CreditFilter.WITHOUT to R.string.credits_filter_note,
                            CreditFilter.OLD to R.string.credits_filter_old, CreditFilter.PAID to R.string.credits_filter_paid,
                        ).forEach { (f, label) -> CuadraChip(if (f == CreditFilter.OLD) stringResource(label, ui.overdueDays) else stringResource(label), ui.filter == f, { actions.setFilter(f) }) }
                    }
                }
                if (rows.isEmpty()) item { Empty(R.string.credits_empty) } else items(rows, key = { r -> if (r is LedgerRow.Customer) "c-" + r.customer.id else "n-" + (r as LedgerRow.Note).item.credit.id }) { r ->
                    when (r) {
                        is LedgerRow.Customer -> CustomerRow(r, actions)
                        is LedgerRow.Note -> NoteRow(r.item.credit, actions)
                    }
                }
            } else {
                if (customers.isEmpty()) item { Empty(R.string.credits_customers_empty) } else items(customers, key = { it.id }) { CustomerListItem(it, actions) }
            }
        }
    }
}

@Composable
private fun Empty(res: Int) = Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
    Text(stringResource(res), color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
}

@Composable
private fun CustomerRow(r: LedgerRow.Customer, actions: CreditsActions) {
    CuadraCard(onClick = { actions.openCustomer(r.customer.id) }) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SplitRow(end = { MoneyText(money(r.balanceMinor), fontWeight = FontWeight.ExtraBold, color = CuadraColors.Orange) }) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Avatar(initials(r.customer.name))
                    Column(Modifier.weight(1f)) {
                        Text(r.customer.name, fontWeight = FontWeight.ExtraBold, maxLines = 2, ellipsize = true)
                        Text(pluralStringResource(R.plurals.credits_pending, r.credits.size, r.credits.size) + " · " + daysAgo(r.sortKey), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            ButtonRow {
                CuadraButton(stringResource(R.string.credits_pay), { actions.askPay(PayTarget(null, r.customer.id, r.customer.name, r.balanceMinor, r.customer.phone)) }, Modifier.share(1f), height = 48)
                CuadraButton(stringResource(R.string.credits_remind), { actions.share(ShareRequest.Reminder(null, r.customer.id)) }, Modifier.share(1f), kind = ButtonKind.WHATSAPP, height = 48)
            }
        }
    }
}

@Composable
private fun NoteRow(c: CreditEntity, actions: CreditsActions) {
    val paid = c.status == "PAID"
    CuadraCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SplitRow(end = { MoneyText(money(if (paid) c.amountMinor else c.balanceMinor), fontWeight = FontWeight.ExtraBold, color = if (paid) CuadraColors.Muted else CuadraColors.Orange, textDecoration = if (paid) TextDecoration.LineThrough else null) }) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(c.debtorLabel, fontWeight = FontWeight.ExtraBold, maxLines = 3, ellipsize = true)
                    TagRow {
                        Tag(stringResource(R.string.credits_note_tag), CuadraColors.Ink, CuadraColors.Soft)
                        Text(daysAgo(c.createdAt), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (paid) Tag(stringResource(R.string.credits_paid_tag), CuadraColors.Green, CuadraColors.GreenSoft)
                    }
                    c.lastReminderAt?.let { Text(stringResource(R.string.credits_reminded, ((System.currentTimeMillis() - it) / DAY).toInt()), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
            if (!paid) {
                val target = PayTarget(c.id, null, c.debtorLabel, c.balanceMinor, c.debtorPhone)
                ButtonRow {
                    CuadraButton(stringResource(R.string.credits_pay), { actions.askPay(target) }, Modifier.share(1f), height = 48)
                    CuadraButton(stringResource(R.string.credits_settle), { actions.askPay(target.copy(settleAll = true)) }, Modifier.share(1f), height = 48)
                    CuadraButton(stringResource(R.string.credits_remind), { actions.share(ShareRequest.Reminder(c.id, null)) }, Modifier.share(1.1f), kind = ButtonKind.WHATSAPP, height = 48)
                }
                CuadraButton(stringResource(R.string.customer_link), { actions.askLink(c.id) }, Modifier.fillMaxWidth(), height = 48)
            }
        }
    }
}

@Composable
private fun CustomerListItem(c: CustomerEntity, actions: CreditsActions) {
    CuadraCard(onClick = { actions.openCustomer(c.id) }) {
        SplitRow(end = {
            if (c.balanceMinor > 0) MoneyText(money(c.balanceMinor), fontWeight = FontWeight.ExtraBold, color = CuadraColors.Orange)
            else Text(stringResource(R.string.customer_none_owed), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Avatar(initials(c.name))
                Column(Modifier.weight(1f)) {
                    Text(c.name, fontWeight = FontWeight.ExtraBold, maxLines = 2, ellipsize = true)
                    Text(c.phone?.let { "+$it" } ?: "—", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** La cuenta de un cliente: cuánto debe, su límite, y todo lo que se le fió y abonó, del más reciente al más viejo. */
@Composable
private fun CustomerDetail(d: CustomerDetailUi, canManage: Boolean, actions: CreditsActions) {
    val c = d.customer
    val zone = ZoneId.systemDefault()
    val locale: Locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val date = DateTimeFormatter.ofPattern("d MMM", locale).withZone(zone)
    val decimals = com.cuadra.caja.ui.common.LocalMoney.current.decimals
    ScreenFrame(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        header = {
            TitleBar(
                Modifier.padding(top = 12.dp),
                start = { CuadraButton("‹", { actions.openCustomer(null) }, Modifier.size(56.dp)) },
                end = { CuadraButton("✎", { actions.openCustomerEditor(CustomerDraft(c.id, c.name, c.phone.orEmpty(), c.notes.orEmpty(), com.cuadra.caja.domain.CreditLimitField.toText(c.creditLimitMinor, decimals), c.balanceMinor)) }, Modifier.size(56.dp)) },
            ) {
                Column {
                    Text(c.name, style = MaterialTheme.typography.headlineMedium)
                    Text(c.phone?.let { "+$it" } ?: "—", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Column(Modifier.fillMaxWidth().background(CuadraColors.Ink, RoundedCornerShape(22.dp)).padding(horizontal = 20.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.credits_owes), color = CuadraColors.Line, style = MaterialTheme.typography.bodyMedium)
                MoneyText(money(c.balanceMinor), Modifier.fillMaxWidth(), color = Color(0xFFF2B58A), style = MaterialTheme.typography.displaySmall)
                c.creditLimitMinor?.let { lim ->
                    Text(stringResource(R.string.credits_limit, money(lim)), color = CuadraColors.Line, style = MaterialTheme.typography.bodyMedium)
                    Box(Modifier.fillMaxWidth().height(6.dp).background(Color(0xFF3A3832), RoundedCornerShape(3.dp))) {
                        Box(Modifier.fillMaxWidth((c.balanceMinor.toFloat() / lim.coerceAtLeast(1)).coerceIn(0f, 1f)).height(6.dp).background(Color(0xFFF2B58A), RoundedCornerShape(3.dp)))
                    }
                }
                Text(c.lastReminderAt?.let { stringResource(R.string.credits_reminded, ((System.currentTimeMillis() - it) / DAY).toInt()) } ?: stringResource(R.string.credits_never_reminded), color = CuadraColors.Line, style = MaterialTheme.typography.bodyMedium)
            }
        },
        footer = {
            CuadraButton(stringResource(R.string.credit_pay_title), { actions.askPay(PayTarget(null, c.id, c.name, c.balanceMinor, c.phone)) }, Modifier.fillMaxWidth(), kind = ButtonKind.PRIMARY, enabled = c.balanceMinor > 0)
            ButtonRow(Modifier.padding(bottom = 8.dp)) {
                CuadraButton(stringResource(R.string.credits_remind), { actions.share(ShareRequest.Reminder(null, c.id)) }, Modifier.share(1f), kind = ButtonKind.WHATSAPP, enabled = c.balanceMinor > 0, height = 48)
                CuadraButton(stringResource(R.string.customer_statement), { actions.share(ShareRequest.Statement(c.id)) }, Modifier.share(1f), height = 48)
            }
        },
    ) {
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (d.openCredits.isNotEmpty()) {
                item { Text(stringResource(R.string.credits_open_credits).uppercase(), style = MaterialTheme.typography.labelLarge, color = CuadraColors.Muted) }
                items(d.openCredits, key = { "open-" + it.id }) { cr ->
                    CuadraCard {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            SplitRow(end = { MoneyText(money(cr.balanceMinor), fontWeight = FontWeight.ExtraBold, color = CuadraColors.Orange) }) {
                                Column {
                                    Text(cr.note ?: cr.debtorLabel, fontWeight = FontWeight.Bold, maxLines = 3, ellipsize = true)
                                    Text(date.format(Instant.ofEpochMilli(cr.createdAt)), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            ButtonRow {
                                CuadraButton(stringResource(R.string.credits_pay), { actions.askPay(PayTarget(cr.id, null, cr.debtorLabel, cr.balanceMinor, cr.debtorPhone ?: c.phone)) }, Modifier.share(1f), height = 48)
                                if (canManage) CuadraButton(stringResource(R.string.writeoff_confirm), { actions.askWriteOff(cr.id) }, Modifier.share(1f), height = 48)
                            }
                        }
                    }
                }
            }
            item { Text(stringResource(R.string.customer_movements).uppercase(), style = MaterialTheme.typography.labelLarge, color = CuadraColors.Muted, modifier = Modifier.padding(top = 6.dp)) }
            items(d.movements, key = { (if (it.isPayment) "p-" else "c-") + (it.paymentId ?: it.creditId) }) { m -> MovementRow(m, date, canManage, actions) }
        }
    }
}

@Composable
private fun MovementRow(m: MovementUi, date: DateTimeFormatter, canManage: Boolean, actions: CreditsActions) {
    CuadraCard {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SplitRow(end = {
                MoneyText(
                    (if (m.isPayment) "− " else "+ ") + money(m.amountMinor), fontWeight = FontWeight.ExtraBold,
                    color = if (m.isPayment) CuadraColors.Green else CuadraColors.Orange, textDecoration = if (m.voided) TextDecoration.LineThrough else null,
                )
            }) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    TagRow {
                        if (m.isPayment) Tag(stringResource(R.string.customer_movement_payment), CuadraColors.Green, CuadraColors.GreenSoft) else Tag(stringResource(R.string.customer_movement_credit), CuadraColors.Orange, CuadraColors.OrangeSoft)
                        if (m.voided) Tag(stringResource(R.string.customer_movement_voided), CuadraColors.Red, CuadraColors.RedSoft)
                    }
                    Text(date.format(Instant.ofEpochMilli(m.at)), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                    Text(listOfNotNull(if (m.isPayment) null else m.label, m.member).joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, ellipsize = true)
                }
            }
            if (m.isPayment && !m.voided && canManage && m.paymentId != null) {
                CuadraButton(stringResource(R.string.void_payment), { actions.askVoidPayment(m.groupId ?: m.paymentId) }, Modifier.fillMaxWidth(), height = 48)
            }
        }
    }
}

@Composable
private fun CreditDialogs(ui: CreditsUi, customers: List<CustomerEntity>, canManage: Boolean, actions: CreditsActions, shareDialog: @Composable (ShareRequest, () -> Unit) -> Unit) {
    ui.pay?.let { PayDialog(it, actions) }
    ui.manual?.let { ManualDialog(it, actions) }
    ui.customerEditor?.let { CustomerEditorDialog(it, actions) }
    ui.linkingCreditId?.let { LinkDialog(customers, actions) }
    ui.writeOffCreditId?.let { if (canManage) WriteOffDialog(actions) }
    ui.voidPaymentId?.let { if (canManage) VoidPaymentDialog(actions) }
    ui.archiveCustomerId?.let { ArchiveCustomerDialog(actions) }
    ui.share?.let { shareDialog(it) { actions.share(null) } }
    ui.messageRes?.let { res ->
        Sheet(actions::dismissMessage, actions = { CuadraButton(stringResource(R.string.close), actions::dismissMessage, Modifier.fillMaxWidth(), kind = ButtonKind.DARK) }) {
            Text(stringResource(res), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
fun PayDialog(target: PayTarget, actions: CreditsActions) {
    val fmt = LocalMoney.current
    // "Saldar" arranca con todo lo que debe; "Abonar" con el monto vacío para escribir cuánto pagó.
    var amount by remember(target) { mutableStateOf(if (target.settleAll) java.math.BigDecimal.valueOf(target.balanceMinor, fmt.decimals).toPlainString() else "") }
    var method by remember(target) { mutableStateOf("CASH") }
    var reference by remember(target) { mutableStateOf("") }
    var receipt by remember(target) { mutableStateOf(true) }
    val entered = com.cuadra.caja.core.model.Money.parse(amount, fmt.decimals)?.minor
    val left = entered?.let { (target.balanceMinor - it).coerceAtLeast(0) }
    Sheet(actions::closePay, actions = {
        ButtonRow {
            CuadraButton(stringResource(R.string.cancel), actions::closePay, Modifier.share(1f))
            CuadraButton(stringResource(R.string.credit_pay_confirm, entered?.let { fmt.format(it) } ?: ""), { actions.confirmPay(amount, method, reference, receipt) }, Modifier.share(1.6f), kind = ButtonKind.PRIMARY, enabled = entered != null && entered > 0)
        }
    }) {
        Text(stringResource(R.string.credit_pay_title), style = MaterialTheme.typography.headlineMedium)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Avatar(initials(target.title), size = 40)
            Column(Modifier.weight(1f)) {
                Text(target.title, fontWeight = FontWeight.ExtraBold, maxLines = 3, ellipsize = true)
                Text(stringResource(R.string.credits_owes) + " " + fmt.format(target.balanceMinor), style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Orange)
            }
        }
        NumberField(amount, { amount = it.take(14) }, label = { Text(stringResource(R.string.credit_pay_amount), maxLines = 1) })
        if (entered != null && entered > target.balanceMinor) Text(stringResource(R.string.credit_pay_too_much), color = CuadraColors.Orange, fontWeight = FontWeight.Bold)
        ChipFlow {
            listOf("CASH" to R.string.method_CASH, "TRANSFER" to R.string.method_TRANSFER, "CARD" to R.string.method_CARD, "OTHER" to R.string.method_OTHER).forEach { (m, label) ->
                CuadraChip(stringResource(label), method == m, { method = m })
            }
        }
        if (method != "CASH") VoiceTextField(reference, { reference = it.take(60) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(stringResource(R.string.pay_reference_hint)) })
        left?.let { Text(stringResource(R.string.credit_pay_left, fmt.format(it)), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        ChipFlow { CuadraChip(stringResource(R.string.credit_pay_send_receipt), receipt, { receipt = !receipt }) }
    }
}

@Composable
fun ManualDialog(d: com.cuadra.caja.ui.ManualDraft, actions: CreditsActions) {
    Sheet(actions::closeManual, actions = {
        ButtonRow {
            CuadraButton(stringResource(R.string.cancel), actions::closeManual, Modifier.share(1f))
            CuadraButton(stringResource(R.string.save), actions::saveManual, Modifier.share(1f), kind = ButtonKind.DARK)
        }
    }) {
        Text(stringResource(R.string.manual_title), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.manual_help), style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted)
        VoiceTextField(d.debtor, { actions.updateManual(d.copy(debtor = it.take(120))) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(stringResource(R.string.manual_debtor)) }, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
        NumberField(d.amount, { actions.updateManual(d.copy(amount = it.take(14))) }, label = { Text(stringResource(R.string.manual_amount), maxLines = 1) })
        NumberField(d.phone, { actions.updateManual(d.copy(phone = it.take(20))) }, label = { Text(stringResource(R.string.manual_phone), maxLines = 1) }, keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone)
        VoiceTextField(d.note, { actions.updateManual(d.copy(note = it.take(200))) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(stringResource(R.string.manual_note)) })
    }
}

@Composable
fun CustomerEditorDialog(d: CustomerDraft, actions: CreditsActions) {
    Sheet(actions::closeCustomerEditor, actions = {
        ButtonRow {
            CuadraButton(stringResource(R.string.cancel), actions::closeCustomerEditor, Modifier.share(1f))
            CuadraButton(stringResource(R.string.save), actions::saveCustomer, Modifier.share(1f), kind = ButtonKind.DARK)
        }
    }) {
        Text(stringResource(if (d.id == null) R.string.customer_new else R.string.customer_edit), style = MaterialTheme.typography.headlineMedium)
        VoiceTextField(d.name, { actions.updateCustomerEditor(d.copy(name = it.take(120))) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(stringResource(R.string.customer_name)) }, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
        NumberField(d.phone, { actions.updateCustomerEditor(d.copy(phone = it.take(20))) }, label = { Text(stringResource(R.string.customer_phone), maxLines = 1) }, keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone)
        NumberField(d.limit, { actions.updateCustomerEditor(d.copy(limit = it.take(14))) }, label = { Text(stringResource(R.string.customer_limit), maxLines = 1) })
        VoiceTextField(d.notes, { actions.updateCustomerEditor(d.copy(notes = it.take(300))) }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.customer_notes)) })
        // Archivar: solo un cliente que ya existe y no debe nada (con saldo se explica por qué no).
        if (d.id != null) {
            if (d.balanceMinor > 0) Text(stringResource(R.string.customer_archive_needs_zero), style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted)
            else CuadraButton(stringResource(R.string.customer_archive), { actions.askArchiveCustomer(d.id) }, Modifier.fillMaxWidth(), height = 48)
        }
    }
}

@Composable
fun LinkDialog(customers: List<CustomerEntity>, actions: CreditsActions) {
    Sheet({ actions.askLink(null) }, actions = { CuadraButton(stringResource(R.string.cancel), { actions.askLink(null) }, Modifier.fillMaxWidth()) }) {
        Text(stringResource(R.string.customer_link_choose), style = MaterialTheme.typography.headlineMedium)
        if (customers.isEmpty()) Text(stringResource(R.string.credits_customers_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
        customers.take(30).forEach { c -> CuadraCard(onClick = { actions.link(c.id) }) { Text(c.name, fontWeight = FontWeight.Bold, maxLines = 3, ellipsize = true) } }
    }
}

/** Anular un abono: el motivo es opcional pero queda registrado (quién lo anuló y por qué). */
@Composable
fun VoidPaymentDialog(actions: CreditsActions) {
    var reason by remember { mutableStateOf("") }
    Sheet({ actions.askVoidPayment(null) }, actions = {
        ButtonRow {
            CuadraButton(stringResource(R.string.cancel), { actions.askVoidPayment(null) }, Modifier.share(1f))
            CuadraButton(stringResource(R.string.void_payment), { actions.voidPayment(reason) }, Modifier.share(1f), kind = ButtonKind.DANGER)
        }
    }) {
        Text(stringResource(R.string.void_payment_title), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.void_payment_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        VoiceTextField(reason, { reason = it.take(200) }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.void_payment_reason)) })
    }
}

@Composable
fun ArchiveCustomerDialog(actions: CreditsActions) {
    Sheet({ actions.askArchiveCustomer(null) }, actions = {
        ButtonRow {
            CuadraButton(stringResource(R.string.cancel), { actions.askArchiveCustomer(null) }, Modifier.share(1f))
            CuadraButton(stringResource(R.string.customer_archive_confirm), actions::archiveCustomer, Modifier.share(1f), kind = ButtonKind.DARK)
        }
    }) {
        Text(stringResource(R.string.customer_archive_title), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.customer_archive_body), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun WriteOffDialog(actions: CreditsActions) {
    var reason by remember { mutableStateOf("") }
    Sheet({ actions.askWriteOff(null) }, actions = {
        ButtonRow {
            CuadraButton(stringResource(R.string.cancel), { actions.askWriteOff(null) }, Modifier.share(1f))
            CuadraButton(stringResource(R.string.writeoff_confirm), { actions.writeOff(reason) }, Modifier.share(1f), kind = ButtonKind.DANGER, enabled = reason.isNotBlank())
        }
    }) {
        Text(stringResource(R.string.writeoff_title), style = MaterialTheme.typography.headlineMedium)
        VoiceTextField(reason, { reason = it.take(200) }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.writeoff_reason)) })
    }
}

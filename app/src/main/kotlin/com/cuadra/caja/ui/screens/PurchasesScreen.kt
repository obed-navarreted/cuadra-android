package com.cuadra.caja.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import com.cuadra.caja.data.local.ProductEntity
import com.cuadra.caja.data.local.PurchaseItemEntity
import com.cuadra.caja.data.local.PurchaseRow
import com.cuadra.caja.data.local.SupplierEntity
import com.cuadra.caja.data.local.SupplierPaymentEntity
import com.cuadra.caja.ui.LineDraft
import com.cuadra.caja.ui.PayDraft
import com.cuadra.caja.ui.PurchaseDraft
import com.cuadra.caja.ui.PurchasesActions
import com.cuadra.caja.ui.PurchasesTab
import com.cuadra.caja.ui.PurchasesUi
import com.cuadra.caja.ui.PurchasesViewModel
import com.cuadra.caja.ui.SupplierDraft
import com.cuadra.caja.ui.common.ButtonKind
import com.cuadra.caja.ui.common.ButtonRow
import com.cuadra.caja.ui.common.ChipFlow
import com.cuadra.caja.ui.common.CuadraButton
import com.cuadra.caja.ui.common.CuadraCard
import com.cuadra.caja.ui.common.CuadraChip
import com.cuadra.caja.ui.common.LinkAction
import com.cuadra.caja.ui.common.LocalMoney
import com.cuadra.caja.ui.common.MoneyText
import com.cuadra.caja.ui.common.NumberField
import com.cuadra.caja.ui.common.ScreenFrame
import com.cuadra.caja.ui.common.SectionLabel
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
import java.time.format.FormatStyle

private val SOURCES = listOf("CASH_DRAWER" to R.string.source_CASH_DRAWER, "BANK" to R.string.source_BANK, "CARD" to R.string.source_CARD, "OWNER" to R.string.source_OWNER, "OTHER" to R.string.source_OTHER)

/** Compras, proveedores y lo que se debe. Dos pestañas: lo comprado (con su saldo) y a quién se le debe. */
@Composable
fun PurchasesScreen(vm: PurchasesViewModel, timezone: String, onBack: () -> Unit) {
    val ui by vm.ui.collectAsState()
    val purchases by vm.purchases.collectAsState()
    val suppliers by vm.suppliers.collectAsState()
    val balances by vm.balances.collectAsState()
    val owed by vm.totalOwed.collectAsState()
    val items by vm.detailItems.collectAsState()
    val payments by vm.detailPayments.collectAsState()
    val pick by vm.pickResults.collectAsState()
    PurchasesContent(ui, purchases, suppliers, balances, owed, items, payments, pick, timezone, vm, onBack)
}

/** Compras sin ViewModel (estado + acciones): es lo que dibuja la guardia de diseño. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PurchasesContent(
    ui: PurchasesUi, purchases: List<PurchaseRow>, suppliers: List<SupplierEntity>, balances: Map<String, Long>, owed: Long,
    detailItems: List<PurchaseItemEntity>, detailPayments: List<SupplierPaymentEntity>, pickResults: List<ProductEntity>, timezone: String,
    actions: PurchasesActions, onBack: () -> Unit,
) {
    val draft = ui.draft
    if (draft != null) {
        NewPurchase(draft, suppliers, actions)
        PurchaseDialogs(ui, purchases, suppliers, detailItems, detailPayments, pickResults, draft, timezone, actions)
        return
    }
    val zone = runCatching { ZoneId.of(timezone) }.getOrDefault(ZoneId.systemDefault())
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val date = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).withZone(zone)

    ScreenFrame(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        footer = {
            if (ui.tab == PurchasesTab.PURCHASES) CuadraButton(stringResource(R.string.purchases_new), { actions.newPurchase(ui.supplierFilter) }, Modifier.fillMaxWidth().padding(bottom = 8.dp), kind = ButtonKind.PRIMARY, height = 48)
            else CuadraButton(stringResource(R.string.suppliers_new), actions::newSupplier, Modifier.fillMaxWidth().padding(bottom = 8.dp), height = 48)
        },
    ) {
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                TitleBar(Modifier.padding(top = 16.dp), end = { CuadraButton(stringResource(R.string.back), onBack, height = 48) }) {
                    Text(stringResource(R.string.purchases_title), style = MaterialTheme.typography.headlineMedium)
                }
            }
            item {
                CuadraCard {
                    Column {
                        Text(stringResource(R.string.purchases_owed_total), style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted)
                        MoneyText(money(owed), Modifier.fillMaxWidth(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, color = if (owed > 0) CuadraColors.Orange else CuadraColors.Green)
                    }
                }
            }
            item {
                ChipFlow {
                    CuadraChip(stringResource(R.string.purchases_tab_purchases), ui.tab == PurchasesTab.PURCHASES, { actions.setTab(PurchasesTab.PURCHASES) })
                    CuadraChip(stringResource(R.string.purchases_tab_suppliers), ui.tab == PurchasesTab.SUPPLIERS, { actions.setTab(PurchasesTab.SUPPLIERS) })
                    if (ui.tab == PurchasesTab.PURCHASES) CuadraChip(stringResource(R.string.purchases_only_owed), ui.onlyOwed, { actions.setOnlyOwed(!ui.onlyOwed) })
                }
            }
            if (ui.tab == PurchasesTab.PURCHASES) {
                ui.supplierFilter?.let { id -> suppliers.firstOrNull { it.id == id }?.let { s -> item { ChipFlow { CuadraChip(s.name + "  ✕", true, { actions.filterSupplier(null) }, userContent = true) } } } }
                if (purchases.isEmpty()) item { EmptyText(R.string.purchases_empty) }
                else items(purchases, key = { it.purchase.id }) { PurchaseCard(it, date) { actions.openDetail(it.purchase.id) } }
            } else {
                if (suppliers.isEmpty()) item { EmptyText(R.string.suppliers_empty) }
                else items(suppliers, key = { it.id }) { s -> SupplierCard(s, balances[s.id] ?: 0, actions) }
            }
        }
    }
    PurchaseDialogs(ui, purchases, suppliers, detailItems, detailPayments, pickResults, null, timezone, actions)
}

@Composable
private fun EmptyText(res: Int) = Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
    Text(stringResource(res), color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
}

@Composable
private fun PurchaseCard(row: PurchaseRow, date: DateTimeFormatter, onClick: () -> Unit) {
    val p = row.purchase
    val owed = if (p.voided) 0 else (p.totalMinor - row.paidMinor).coerceAtLeast(0)
    CuadraCard(Modifier.fillMaxWidth(), onClick = onClick) {
        SplitRow(end = { MoneyText(money(p.totalMinor), fontWeight = FontWeight.ExtraBold, textDecoration = if (p.voided) TextDecoration.LineThrough else null) }) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(p.supplierName ?: stringResource(R.string.purchases_no_supplier), fontWeight = FontWeight.ExtraBold, maxLines = 2, ellipsize = true)
                Text(listOfNotNull(date.format(Instant.ofEpochMilli(p.occurredAt)), p.createdByName).joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted, maxLines = 3, ellipsize = true)
                TagRow {
                    if (p.voided) Tag(stringResource(R.string.voided_tag), CuadraColors.Red, CuadraColors.RedSoft)
                    else if (owed > 0) Tag(stringResource(R.string.purchases_owes, money(owed)), CuadraColors.Orange, CuadraColors.OrangeSoft)
                    else Tag(stringResource(R.string.purchases_paid), CuadraColors.Green, CuadraColors.GreenSoft)
                    if (p.rev == 0L) Tag(stringResource(R.string.history_unsynced), CuadraColors.Ink, CuadraColors.Soft)
                }
            }
        }
    }
}

@Composable
private fun SupplierCard(s: SupplierEntity, owed: Long, actions: PurchasesActions) {
    CuadraCard(Modifier.fillMaxWidth(), onClick = { actions.filterSupplier(s.id) }) {
        SplitRow(end = {
            Column(horizontalAlignment = Alignment.End) {
                if (owed > 0) {
                    MoneyText(money(owed), fontWeight = FontWeight.ExtraBold, color = CuadraColors.Orange)
                    CuadraButton(stringResource(R.string.suppliers_pay), { actions.askPaySupplier(s, owed) }, kind = ButtonKind.PRIMARY, height = 48)
                } else Text(stringResource(R.string.suppliers_no_debt), color = CuadraColors.Green, fontWeight = FontWeight.Bold)
            }
        }) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(s.name, fontWeight = FontWeight.ExtraBold, maxLines = 2, ellipsize = true)
                s.phone?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted) }
                LinkAction(stringResource(R.string.suppliers_edit), { actions.editSupplier(s) })
            }
        }
    }
}

@Composable
private fun PurchaseDialogs(
    ui: PurchasesUi, rows: List<PurchaseRow>, suppliers: List<SupplierEntity>, items: List<PurchaseItemEntity>, payments: List<SupplierPaymentEntity>,
    pick: List<ProductEntity>, draft: PurchaseDraft?, timezone: String, actions: PurchasesActions,
) {
    val detailId = ui.detailId
    if (detailId != null) {
        val row = rows.firstOrNull { it.purchase.id == detailId }
        if (row != null) DetailSheet(row, items, payments, timezone, actions) else androidx.compose.runtime.LaunchedEffect(detailId) { actions.openDetail(null) }
    }
    ui.pay?.let { PayDialog(it, actions) }
    ui.void?.let { v ->
        Sheet(actions::closeVoid, actions = {
            ButtonRow {
                CuadraButton(stringResource(R.string.cancel), actions::closeVoid, Modifier.share(1f))
                CuadraButton(stringResource(R.string.void_confirm), actions::confirmVoid, Modifier.share(1.4f), kind = ButtonKind.DANGER)
            }
        }) {
            Text(stringResource(if (v.purchaseId != null) R.string.purchases_void_title else R.string.purchases_void_payment_title), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(if (v.purchaseId != null) R.string.purchases_void_hint else R.string.purchases_void_payment_hint), style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted)
            VoiceTextField(v.reason, { actions.updateVoid(it.take(200)) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(stringResource(R.string.void_reason)) })
        }
    }
    ui.supplierEditor?.let { SupplierDialog(it, actions) }
    if (draft != null) {
        if (draft.picking) PickDialog(draft, pick, actions)
        draft.lineEditor?.let { LineDialog(it, draft.error, actions) }
    }
}

@Composable
fun DetailSheet(row: PurchaseRow, items: List<PurchaseItemEntity>, payments: List<SupplierPaymentEntity>, timezone: String, actions: PurchasesActions) {
    val zone = runCatching { ZoneId.of(timezone) }.getOrDefault(ZoneId.systemDefault())
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val time = com.cuadra.caja.domain.ClockFormat.dateTime(locale, zone)
    val p = row.purchase
    val owed = if (p.voided) 0 else (p.totalMinor - row.paidMinor).coerceAtLeast(0)
    Sheet({ actions.openDetail(null) }, actions = { CuadraButton(stringResource(R.string.close), { actions.openDetail(null) }, Modifier.fillMaxWidth(), kind = ButtonKind.DARK) }) {
        Text(p.supplierName ?: stringResource(R.string.purchases_no_supplier), style = MaterialTheme.typography.headlineMedium)
        Text(time.format(Instant.ofEpochMilli(p.occurredAt)) + (p.createdByName?.let { " · $it" } ?: ""), style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted)
        if (p.voided) TagRow { Tag(stringResource(R.string.voided_tag), CuadraColors.Red, CuadraColors.RedSoft) }
        CuadraCard {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items.forEach { l ->
                    SplitRow(end = { MoneyText(money(l.lineTotalMinor), fontWeight = FontWeight.Bold) }) { Text("${qtyText(l.quantityMilli)} × ${l.name}") }
                }
                SplitRow(Modifier.padding(top = 6.dp), end = { MoneyText(money(p.totalMinor), fontWeight = FontWeight.ExtraBold) }) { Text(stringResource(R.string.purchases_total), fontWeight = FontWeight.ExtraBold) }
                SplitRow(end = { MoneyText(money(row.paidMinor)) }) { Text(stringResource(R.string.purchases_paid_label)) }
                if (owed > 0) SplitRow(end = { MoneyText(money(owed), color = CuadraColors.Orange, fontWeight = FontWeight.ExtraBold) }) { Text(stringResource(R.string.purchases_balance), color = CuadraColors.Orange, fontWeight = FontWeight.ExtraBold) }
            }
        }
        p.note?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted) }
        if (payments.isNotEmpty()) {
            SectionLabel(stringResource(R.string.purchases_payments))
            payments.forEach { pay ->
                SplitRow(end = {
                    Column(horizontalAlignment = Alignment.End) {
                        MoneyText(money(pay.amountMinor), fontWeight = FontWeight.ExtraBold, textDecoration = if (pay.voided) TextDecoration.LineThrough else null)
                        if (!pay.voided && !p.voided) LinkAction(stringResource(R.string.void_action), { actions.askVoidPayment(pay.id) }, color = CuadraColors.Red)
                    }
                }) {
                    Column {
                        Text(time.format(Instant.ofEpochMilli(pay.occurredAt)), fontWeight = FontWeight.Bold)
                        Text(SOURCES.firstOrNull { it.first == pay.source }?.let { stringResource(it.second) }.orEmpty() + (if (pay.voided) " · " + stringResource(R.string.voided_tag) else ""), style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted)
                    }
                }
            }
        }
        if (owed > 0) CuadraButton(stringResource(R.string.suppliers_pay), { actions.askPayPurchase(row) }, Modifier.fillMaxWidth(), kind = ButtonKind.PRIMARY)
        if (!p.voided) CuadraButton(stringResource(R.string.purchases_void), { actions.askVoidPurchase(p.id) }, Modifier.fillMaxWidth(), height = 48)
    }
}

@Composable
fun PayDialog(d: PayDraft, actions: PurchasesActions) {
    val fmt = LocalMoney.current
    val entered = Money.parse(d.amount, fmt.decimals)?.minor
    Sheet(actions::closePay, actions = {
        ButtonRow {
            CuadraButton(stringResource(R.string.cancel), actions::closePay, Modifier.share(1f))
            CuadraButton(stringResource(R.string.save) + (entered?.takeIf { it > 0 }?.let { " · " + fmt.format(it) } ?: ""), actions::confirmPay, Modifier.share(1.6f), kind = ButtonKind.PRIMARY, enabled = entered != null && entered > 0)
        }
    }) {
        Text(stringResource(R.string.suppliers_pay), style = MaterialTheme.typography.headlineMedium)
        Text(d.title.ifBlank { stringResource(R.string.purchases_no_supplier) } + " · " + stringResource(R.string.purchases_owes, fmt.format(d.owedMinor)), style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Orange)
        NumberField(d.amount, { actions.updatePay(d.copy(amount = it.take(14))) }, label = { Text(stringResource(R.string.expense_amount), maxLines = 1) })
        if (entered != null && entered > d.owedMinor) Text(stringResource(R.string.purchases_pay_too_much), color = CuadraColors.Orange, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.expense_source), fontWeight = FontWeight.Bold)
        ChipFlow { SOURCES.forEach { (s, label) -> CuadraChip(stringResource(label), d.source == s, { actions.updatePay(d.copy(source = s)) }) } }
        Text(stringResource(if (d.source == "CASH_DRAWER") R.string.expense_cash_hint else R.string.expense_other_hint), style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted)
    }
}

@Composable
fun SupplierDialog(d: SupplierDraft, actions: PurchasesActions) {
    Sheet(actions::closeSupplier, actions = {
        ButtonRow {
            CuadraButton(stringResource(R.string.cancel), actions::closeSupplier, Modifier.share(1f))
            CuadraButton(stringResource(R.string.save), actions::saveSupplier, Modifier.share(1.4f), kind = ButtonKind.PRIMARY)
        }
    }) {
        Text(stringResource(if (d.id == null) R.string.suppliers_new else R.string.suppliers_edit), style = MaterialTheme.typography.headlineMedium)
        VoiceTextField(d.name, { actions.updateSupplier(d.copy(name = it.take(120))) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(stringResource(R.string.inventory_name)) },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words), isError = d.error && d.name.isBlank())
        NumberField(d.phone, { actions.updateSupplier(d.copy(phone = it.take(20))) }, label = { Text(stringResource(R.string.manual_phone), maxLines = 1) }, keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone)
        VoiceTextField(d.notes, { actions.updateSupplier(d.copy(notes = it.take(300))) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(stringResource(R.string.manual_note)) })
        if (d.error) Text(stringResource(R.string.suppliers_error), color = CuadraColors.Red, fontWeight = FontWeight.Bold)
    }
}

// ---------- nueva compra ----------

@Composable
private fun NewPurchase(d: PurchaseDraft, suppliers: List<SupplierEntity>, actions: PurchasesActions) {
    val fmt = LocalMoney.current
    val total = d.lines.sumOf { it.totalMinor }
    val paidText = d.paid ?: java.math.BigDecimal.valueOf(total, fmt.decimals).toPlainString()
    val paid = Money.parse(paidText.ifBlank { "0" }, fmt.decimals)?.minor
    ScreenFrame(
        Modifier.fillMaxSize().imePadding().padding(16.dp), spacing = 12.dp,
        header = {
            TitleBar(end = { CuadraButton(stringResource(R.string.cancel), actions::closeDraft, height = 48) }) {
                Text(stringResource(R.string.purchases_new), style = MaterialTheme.typography.headlineMedium)
            }
        },
        footer = {
            CuadraButton(stringResource(R.string.purchases_save, fmt.format(total)), actions::savePurchase, Modifier.fillMaxWidth(), kind = ButtonKind.PRIMARY, enabled = d.lines.isNotEmpty() && paid != null && paid in 0..total)
        },
    ) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionLabel(stringResource(R.string.purchases_supplier))
            ChipFlow {
                suppliers.forEach { s -> CuadraChip(s.name, d.supplierId == s.id, { actions.updateDraft(d.copy(supplierId = if (d.supplierId == s.id) null else s.id)) }, userContent = true) }
                CuadraChip("+ " + stringResource(R.string.suppliers_new), false, actions::newSupplier)
            }
            if (d.supplierId == null) VoiceTextField(d.supplierName, { actions.updateDraft(d.copy(supplierName = it.take(120))) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(stringResource(R.string.purchases_supplier_name_hint)) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
            SectionLabel(stringResource(R.string.purchases_lines))
            d.lines.forEach { l ->
                CuadraCard(onClick = { actions.editLine(l) }) {
                    SplitRow(endMaxFraction = 0.7f, end = {
                        ChipFlow(alignEnd = true) {
                            MoneyText(money(l.totalMinor), fontWeight = FontWeight.ExtraBold)
                            LinkAction("✕", { actions.removeLine(l.id) }, color = CuadraColors.Red)
                        }
                    }) {
                        Column {
                            Text(l.name, fontWeight = FontWeight.ExtraBold, maxLines = 2, ellipsize = true)
                            Text("${qtyText(l.quantityMilli)} × ${money(l.unitCostMinor)}", style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted)
                        }
                    }
                }
            }
            CuadraButton(stringResource(R.string.purchases_add_line), { actions.updateDraft(d.copy(picking = true)) }, Modifier.fillMaxWidth(), height = 48)
            SplitRow(end = { MoneyText(money(total), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold) }) {
                Text(stringResource(R.string.purchases_total), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
            }
            SectionLabel(stringResource(R.string.purchases_paid_now))
            NumberField(paidText, { actions.updateDraft(d.copy(paid = it.take(14))) }, label = { Text(stringResource(R.string.expense_amount), maxLines = 1) })
            ChipFlow { SOURCES.forEach { (s, label) -> CuadraChip(stringResource(label), d.paidSource == s, { actions.updateDraft(d.copy(paidSource = s)) }) } }
            if (paid != null && paid in 0..total) {
                if (paid < total) Text(stringResource(R.string.purchases_remaining, fmt.format(total - paid)), color = CuadraColors.Orange, fontWeight = FontWeight.Bold)
                if (paid > 0) Text(stringResource(if (d.paidSource == "CASH_DRAWER") R.string.expense_cash_hint else R.string.expense_other_hint), style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted)
            }
            VoiceTextField(d.note, { actions.updateDraft(d.copy(note = it.take(300))) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(stringResource(R.string.manual_note)) })
            if (d.error) Text(stringResource(R.string.purchases_error), color = CuadraColors.Red, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun PickDialog(d: PurchaseDraft, results: List<ProductEntity>, actions: PurchasesActions) {
    Sheet({ actions.updateDraft(d.copy(picking = false)) }, actions = { CuadraButton(stringResource(R.string.purchases_free_line), actions::pickFree, Modifier.fillMaxWidth(), height = 48) }) {
        Text(stringResource(R.string.purchases_add_line), style = MaterialTheme.typography.headlineMedium)
        VoiceTextField(d.pickQuery, { actions.updateDraft(d.copy(pickQuery = it)) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(stringResource(R.string.inventory_search), maxLines = 1) })
        results.take(8).forEach { p ->
            CuadraCard(onClick = { actions.pickProduct(p) }) {
                SplitRow(end = { p.costMinor?.let { MoneyText(money(it), color = CuadraColors.Muted) } }) {
                    Text(p.name + (p.variant?.let { " · $it" } ?: ""), fontWeight = FontWeight.Bold, maxLines = 2, ellipsize = true)
                }
            }
        }
    }
}

@Composable
fun LineDialog(l: LineDraft, error: Boolean, actions: PurchasesActions) {
    Sheet(actions::closeLine, actions = {
        ButtonRow {
            CuadraButton(stringResource(R.string.cancel), actions::closeLine, Modifier.share(1f))
            CuadraButton(stringResource(R.string.save), actions::saveLine, Modifier.share(1.4f), kind = ButtonKind.PRIMARY)
        }
    }) {
        Text(stringResource(R.string.purchases_line_title), style = MaterialTheme.typography.headlineMedium)
        if (l.productId == null) VoiceTextField(l.name, { actions.updateLine(l.copy(name = it.take(200))) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(stringResource(R.string.inventory_name)) },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences), isError = error && l.name.isBlank())
        else Text(l.name, fontWeight = FontWeight.ExtraBold)
        NumberField(l.quantity, { actions.updateLine(l.copy(quantity = it.take(12))) }, label = { Text(stringResource(R.string.inventory_quantity), maxLines = 1) }, isError = error && l.quantity.isBlank())
        NumberField(l.cost, { actions.updateLine(l.copy(cost = it.take(14))) }, label = { Text(stringResource(R.string.purchases_unit_cost), maxLines = 1) })
    }
}

package com.cuadra.caja.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import com.cuadra.caja.data.local.PurchaseRow
import com.cuadra.caja.data.local.SupplierEntity
import com.cuadra.caja.ui.LineDraft
import com.cuadra.caja.ui.PayDraft
import com.cuadra.caja.ui.PurchaseDraft
import com.cuadra.caja.ui.PurchasesTab
import com.cuadra.caja.ui.PurchasesViewModel
import com.cuadra.caja.ui.SupplierDraft
import com.cuadra.caja.ui.common.ButtonKind
import com.cuadra.caja.ui.common.CuadraButton
import com.cuadra.caja.ui.common.CuadraCard
import com.cuadra.caja.ui.common.CuadraChip
import com.cuadra.caja.ui.common.LocalMoney
import com.cuadra.caja.ui.common.SectionLabel
import com.cuadra.caja.ui.common.Tag
import com.cuadra.caja.ui.common.money
import com.cuadra.caja.ui.common.VoiceTextField
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
    val draft = ui.draft
    if (draft != null) {
        NewPurchase(draft, vm)
        PurchaseDialogs(vm, timezone)
        return
    }
    val purchases by vm.purchases.collectAsState()
    val suppliers by vm.suppliers.collectAsState()
    val balances by vm.balances.collectAsState()
    val owed by vm.totalOwed.collectAsState()
    val zone = runCatching { ZoneId.of(timezone) }.getOrDefault(ZoneId.systemDefault())
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val date = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).withZone(zone)

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.purchases_title), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            CuadraButton(stringResource(R.string.back), onBack, height = 44)
        }
        CuadraCard {
            Column {
                Text(stringResource(R.string.purchases_owed_total), style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted)
                Text(money(owed), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, color = if (owed > 0) CuadraColors.Orange else CuadraColors.Green)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CuadraChip(stringResource(R.string.purchases_tab_purchases), ui.tab == PurchasesTab.PURCHASES, { vm.setTab(PurchasesTab.PURCHASES) })
            CuadraChip(stringResource(R.string.purchases_tab_suppliers), ui.tab == PurchasesTab.SUPPLIERS, { vm.setTab(PurchasesTab.SUPPLIERS) })
            if (ui.tab == PurchasesTab.PURCHASES) CuadraChip(stringResource(R.string.purchases_only_owed), ui.onlyOwed, { vm.setOnlyOwed(!ui.onlyOwed) })
        }
        if (ui.tab == PurchasesTab.PURCHASES) {
            ui.supplierFilter?.let { id -> suppliers.firstOrNull { it.id == id }?.let { CuadraChip(it.name + "  ✕", true, { vm.filterSupplier(null) }) } }
            CuadraButton(stringResource(R.string.purchases_new), { vm.newPurchase(ui.supplierFilter) }, Modifier.fillMaxWidth(), kind = ButtonKind.PRIMARY, height = 48)
            if (purchases.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(stringResource(R.string.purchases_empty), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) { items(purchases, key = { it.purchase.id }) { PurchaseCard(it, date) { vm.openDetail(it.purchase.id) } } }
        } else {
            CuadraButton(stringResource(R.string.suppliers_new), vm::newSupplier, Modifier.fillMaxWidth(), height = 48)
            if (suppliers.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(stringResource(R.string.suppliers_empty), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(suppliers, key = { it.id }) { s -> SupplierCard(s, balances[s.id] ?: 0, vm) }
            }
        }
    }
    PurchaseDialogs(vm, timezone)
}

@Composable
private fun PurchaseCard(row: PurchaseRow, date: DateTimeFormatter, onClick: () -> Unit) {
    val p = row.purchase
    val owed = if (p.voided) 0 else (p.totalMinor - row.paidMinor).coerceAtLeast(0)
    CuadraCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(p.supplierName ?: stringResource(R.string.purchases_no_supplier), fontWeight = FontWeight.ExtraBold, maxLines = 1)
                Text(listOfNotNull(date.format(Instant.ofEpochMilli(p.occurredAt)), p.createdByName).joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (p.voided) Tag(stringResource(R.string.voided_tag), CuadraColors.Red, CuadraColors.RedSoft)
                    else if (owed > 0) Tag(stringResource(R.string.purchases_owes, money(owed)), CuadraColors.Orange, CuadraColors.OrangeSoft)
                    else Tag(stringResource(R.string.purchases_paid), CuadraColors.Green, CuadraColors.GreenSoft)
                    if (p.rev == 0L) Tag(stringResource(R.string.history_unsynced), CuadraColors.Ink, CuadraColors.Soft)
                }
            }
            Text(money(p.totalMinor), fontWeight = FontWeight.ExtraBold, textDecoration = if (p.voided) TextDecoration.LineThrough else null)
        }
    }
}

@Composable
private fun SupplierCard(s: SupplierEntity, owed: Long, vm: PurchasesViewModel) {
    CuadraCard(Modifier.fillMaxWidth(), onClick = { vm.filterSupplier(s.id) }) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(s.name, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                s.phone?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.suppliers_edit), color = CuadraColors.Green, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { vm.editSupplier(s) })
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                if (owed > 0) {
                    Text(money(owed), fontWeight = FontWeight.ExtraBold, color = CuadraColors.Orange)
                    CuadraButton(stringResource(R.string.suppliers_pay), { vm.askPaySupplier(s, owed) }, height = 40, kind = ButtonKind.PRIMARY)
                } else Text(stringResource(R.string.suppliers_no_debt), color = CuadraColors.Green, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun PurchaseDialogs(vm: PurchasesViewModel, timezone: String) {
    val ui by vm.ui.collectAsState()
    val detailId = ui.detailId
    if (detailId != null) DetailSheet(detailId, timezone, vm)
    ui.pay?.let { PayDialog(it, vm) }
    ui.void?.let { v ->
        Sheet(vm::closeVoid) {
            Text(stringResource(if (v.purchaseId != null) R.string.purchases_void_title else R.string.purchases_void_payment_title), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(if (v.purchaseId != null) R.string.purchases_void_hint else R.string.purchases_void_payment_hint), style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted)
            VoiceTextField(v.reason, { vm.updateVoid(it.take(200)) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(stringResource(R.string.void_reason)) })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CuadraButton(stringResource(R.string.cancel), vm::closeVoid, Modifier.weight(1f))
                CuadraButton(stringResource(R.string.void_confirm), vm::confirmVoid, Modifier.weight(1.4f), kind = ButtonKind.DANGER)
            }
        }
    }
    ui.supplierEditor?.let { SupplierDialog(it, vm) }
}

@Composable
private fun DetailSheet(id: String, timezone: String, vm: PurchasesViewModel) {
    val rows by vm.purchases.collectAsState()
    val items by vm.detailItems.collectAsState()
    val payments by vm.detailPayments.collectAsState()
    val row = rows.firstOrNull { it.purchase.id == id }
    val zone = runCatching { ZoneId.of(timezone) }.getOrDefault(ZoneId.systemDefault())
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val time = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withLocale(locale).withZone(zone)
    if (row == null) { vm.openDetail(null); return }
    val p = row.purchase
    val owed = if (p.voided) 0 else (p.totalMinor - row.paidMinor).coerceAtLeast(0)
    Sheet({ vm.openDetail(null) }) {
        Text(p.supplierName ?: stringResource(R.string.purchases_no_supplier), style = MaterialTheme.typography.headlineMedium)
        Text(time.format(Instant.ofEpochMilli(p.occurredAt)) + (p.createdByName?.let { " · $it" } ?: ""), style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted)
        if (p.voided) Tag(stringResource(R.string.voided_tag), CuadraColors.Red, CuadraColors.RedSoft)
        CuadraCard {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items.forEach { l ->
                    Row(Modifier.fillMaxWidth()) {
                        Text("${qtyText(l.quantityMilli)} × ${l.name}", Modifier.weight(1f))
                        Text(money(l.lineTotalMinor), fontWeight = FontWeight.Bold)
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 6.dp)) { Text(stringResource(R.string.purchases_total), Modifier.weight(1f), fontWeight = FontWeight.ExtraBold); Text(money(p.totalMinor), fontWeight = FontWeight.ExtraBold) }
                Row(Modifier.fillMaxWidth()) { Text(stringResource(R.string.purchases_paid_label), Modifier.weight(1f)); Text(money(row.paidMinor)) }
                if (owed > 0) Row(Modifier.fillMaxWidth()) { Text(stringResource(R.string.purchases_balance), Modifier.weight(1f), color = CuadraColors.Orange, fontWeight = FontWeight.ExtraBold); Text(money(owed), color = CuadraColors.Orange, fontWeight = FontWeight.ExtraBold) }
            }
        }
        p.note?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted) }
        if (payments.isNotEmpty()) {
            SectionLabel(stringResource(R.string.purchases_payments))
            payments.forEach { pay ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(time.format(Instant.ofEpochMilli(pay.occurredAt)), fontWeight = FontWeight.Bold)
                        Text(SOURCES.firstOrNull { it.first == pay.source }?.let { stringResource(it.second) }.orEmpty() + (if (pay.voided) " · " + stringResource(R.string.voided_tag) else ""), style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted)
                    }
                    Text(money(pay.amountMinor), fontWeight = FontWeight.ExtraBold, textDecoration = if (pay.voided) TextDecoration.LineThrough else null)
                    if (!pay.voided && !p.voided) Text(stringResource(R.string.void_action), color = CuadraColors.Red, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 12.dp).clickable { vm.askVoidPayment(pay.id) })
                }
            }
        }
        if (owed > 0) CuadraButton(stringResource(R.string.suppliers_pay), { vm.askPayPurchase(row) }, Modifier.fillMaxWidth(), kind = ButtonKind.PRIMARY)
        if (!p.voided) CuadraButton(stringResource(R.string.purchases_void), { vm.askVoidPurchase(p.id) }, Modifier.fillMaxWidth(), height = 48)
        CuadraButton(stringResource(R.string.close), { vm.openDetail(null) }, Modifier.fillMaxWidth(), kind = ButtonKind.DARK)
    }
}

@Composable
private fun PayDialog(d: PayDraft, vm: PurchasesViewModel) {
    val fmt = LocalMoney.current
    val entered = Money.parse(d.amount, fmt.decimals)?.minor
    Sheet(vm::closePay) {
        Text(stringResource(R.string.suppliers_pay), style = MaterialTheme.typography.headlineMedium)
        Text(d.title.ifBlank { stringResource(R.string.purchases_no_supplier) } + " · " + stringResource(R.string.purchases_owes, fmt.format(d.owedMinor)), style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Orange)
        OutlinedTextField(d.amount, { vm.updatePay(d.copy(amount = it.take(14))) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(stringResource(R.string.expense_amount)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
        if (entered != null && entered > d.owedMinor) Text(stringResource(R.string.purchases_pay_too_much), color = CuadraColors.Orange, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.expense_source), fontWeight = FontWeight.Bold)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SOURCES.forEach { (s, label) -> CuadraChip(stringResource(label), d.source == s, { vm.updatePay(d.copy(source = s)) }) }
        }
        Text(stringResource(if (d.source == "CASH_DRAWER") R.string.expense_cash_hint else R.string.expense_other_hint), style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CuadraButton(stringResource(R.string.cancel), vm::closePay, Modifier.weight(1f))
            CuadraButton(stringResource(R.string.save) + (entered?.takeIf { it > 0 }?.let { " · " + fmt.format(it) } ?: ""), vm::confirmPay, Modifier.weight(1.6f), kind = ButtonKind.PRIMARY, enabled = entered != null && entered > 0)
        }
    }
}

@Composable
private fun SupplierDialog(d: SupplierDraft, vm: PurchasesViewModel) {
    Sheet(vm::closeSupplier) {
        Text(stringResource(if (d.id == null) R.string.suppliers_new else R.string.suppliers_edit), style = MaterialTheme.typography.headlineMedium)
        VoiceTextField(d.name, { vm.updateSupplier(d.copy(name = it.take(120))) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(stringResource(R.string.inventory_name)) },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words), isError = d.error && d.name.isBlank())
        OutlinedTextField(d.phone, { vm.updateSupplier(d.copy(phone = it.take(20))) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(stringResource(R.string.manual_phone)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
        VoiceTextField(d.notes, { vm.updateSupplier(d.copy(notes = it.take(300))) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(stringResource(R.string.manual_note)) })
        if (d.error) Text(stringResource(R.string.suppliers_error), color = CuadraColors.Red, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CuadraButton(stringResource(R.string.cancel), vm::closeSupplier, Modifier.weight(1f))
            CuadraButton(stringResource(R.string.save), vm::saveSupplier, Modifier.weight(1.4f), kind = ButtonKind.PRIMARY)
        }
    }
}

// ---------- nueva compra ----------

@Composable
private fun NewPurchase(d: PurchaseDraft, vm: PurchasesViewModel) {
    val fmt = LocalMoney.current
    val suppliers by vm.suppliers.collectAsState()
    val total = vm.totalOf(d)
    val paidText = d.paid ?: java.math.BigDecimal.valueOf(total, fmt.decimals).toPlainString()
    val paid = Money.parse(paidText.ifBlank { "0" }, fmt.decimals)?.minor
    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.purchases_new), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            CuadraButton(stringResource(R.string.cancel), vm::closeDraft, height = 44)
        }
        SectionLabel(stringResource(R.string.purchases_supplier))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            suppliers.forEach { s -> CuadraChip(s.name, d.supplierId == s.id, { vm.updateDraft(d.copy(supplierId = if (d.supplierId == s.id) null else s.id)) }) }
            CuadraChip("+ " + stringResource(R.string.suppliers_new), false, vm::newSupplier)
        }
        if (d.supplierId == null) VoiceTextField(d.supplierName, { vm.updateDraft(d.copy(supplierName = it.take(120))) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(stringResource(R.string.purchases_supplier_name_hint)) },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
        SectionLabel(stringResource(R.string.purchases_lines))
        d.lines.forEach { l ->
            CuadraCard(onClick = { vm.editLine(l) }) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(l.name, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                        Text("${qtyText(l.quantityMilli)} × ${money(l.unitCostMinor)}", style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted)
                    }
                    Text(money(l.totalMinor), fontWeight = FontWeight.ExtraBold)
                    Text("✕", color = CuadraColors.Red, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(start = 8.dp).clickable { vm.removeLine(l.id) })
                }
            }
        }
        CuadraButton(stringResource(R.string.purchases_add_line), { vm.updateDraft(d.copy(picking = true)) }, Modifier.fillMaxWidth(), height = 48)
        Row(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.purchases_total), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
            Text(money(total), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
        }
        SectionLabel(stringResource(R.string.purchases_paid_now))
        OutlinedTextField(paidText, { vm.updateDraft(d.copy(paid = it.take(14))) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(stringResource(R.string.expense_amount)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SOURCES.forEach { (s, label) -> CuadraChip(stringResource(label), d.paidSource == s, { vm.updateDraft(d.copy(paidSource = s)) }) }
        }
        if (paid != null && paid in 0..total) {
            if (paid < total) Text(stringResource(R.string.purchases_remaining, fmt.format(total - paid)), color = CuadraColors.Orange, fontWeight = FontWeight.Bold)
            if (paid > 0) Text(stringResource(if (d.paidSource == "CASH_DRAWER") R.string.expense_cash_hint else R.string.expense_other_hint), style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted)
        }
        VoiceTextField(d.note, { vm.updateDraft(d.copy(note = it.take(300))) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(stringResource(R.string.manual_note)) })
        if (d.error) Text(stringResource(R.string.purchases_error), color = CuadraColors.Red, fontWeight = FontWeight.Bold)
        CuadraButton(stringResource(R.string.purchases_save, fmt.format(total)), vm::savePurchase, Modifier.fillMaxWidth(), kind = ButtonKind.PRIMARY, enabled = d.lines.isNotEmpty() && paid != null && paid in 0..total)
    }
    if (d.picking) PickDialog(d, vm)
    d.lineEditor?.let { LineDialog(it, d.error, vm) }
}

@Composable
private fun PickDialog(d: PurchaseDraft, vm: PurchasesViewModel) {
    val results by vm.pickResults.collectAsState()
    Sheet({ vm.updateDraft(d.copy(picking = false)) }) {
        Text(stringResource(R.string.purchases_add_line), style = MaterialTheme.typography.headlineMedium)
        VoiceTextField(d.pickQuery, { vm.updateDraft(d.copy(pickQuery = it)) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(stringResource(R.string.inventory_search)) })
        results.take(8).forEach { p ->
            CuadraCard(onClick = { vm.pickProduct(p) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(p.name + (p.variant?.let { " · $it" } ?: ""), Modifier.weight(1f), fontWeight = FontWeight.Bold, maxLines = 1)
                    p.costMinor?.let { Text(money(it), color = CuadraColors.Muted) }
                }
            }
        }
        CuadraButton(stringResource(R.string.purchases_free_line), vm::pickFree, Modifier.fillMaxWidth(), height = 48)
    }
}

@Composable
private fun LineDialog(l: LineDraft, error: Boolean, vm: PurchasesViewModel) {
    Sheet(vm::closeLine) {
        Text(stringResource(R.string.purchases_line_title), style = MaterialTheme.typography.headlineMedium)
        if (l.productId == null) VoiceTextField(l.name, { vm.updateLine(l.copy(name = it.take(200))) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(stringResource(R.string.inventory_name)) },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences), isError = error && l.name.isBlank())
        else Text(l.name, fontWeight = FontWeight.ExtraBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(l.quantity, { vm.updateLine(l.copy(quantity = it.take(12))) }, Modifier.weight(1f), singleLine = true, label = { Text(stringResource(R.string.inventory_quantity)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), isError = error && l.quantity.isBlank())
            OutlinedTextField(l.cost, { vm.updateLine(l.copy(cost = it.take(14))) }, Modifier.weight(1f), singleLine = true, label = { Text(stringResource(R.string.purchases_unit_cost)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CuadraButton(stringResource(R.string.cancel), vm::closeLine, Modifier.weight(1f))
            CuadraButton(stringResource(R.string.save), vm::saveLine, Modifier.weight(1.4f), kind = ButtonKind.PRIMARY)
        }
    }
}

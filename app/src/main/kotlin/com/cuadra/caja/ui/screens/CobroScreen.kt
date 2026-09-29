package com.cuadra.caja.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cuadra.caja.R
import com.cuadra.caja.core.model.Money
import com.cuadra.caja.domain.CashSuggestions
import com.cuadra.caja.domain.PayMethod
import com.cuadra.caja.domain.PaymentEntry
import com.cuadra.caja.domain.PaymentIssue
import com.cuadra.caja.ui.CajaUi
import com.cuadra.caja.ui.CajaViewModel
import com.cuadra.caja.ui.CobroUi
import com.cuadra.caja.ui.common.ButtonKind
import com.cuadra.caja.ui.common.CuadraButton
import com.cuadra.caja.ui.common.CuadraCard
import com.cuadra.caja.ui.common.CuadraChip
import com.cuadra.caja.ui.common.LocalMoney
import com.cuadra.caja.ui.common.SectionLabel
import com.cuadra.caja.ui.common.money
import com.cuadra.caja.ui.common.VoiceTextField
import com.cuadra.caja.ui.theme.CuadraColors

fun methodRes(m: PayMethod) = when (m) {
    PayMethod.CASH -> R.string.method_CASH
    PayMethod.TRANSFER -> R.string.method_TRANSFER
    PayMethod.CARD -> R.string.method_CARD
    PayMethod.CREDIT -> R.string.method_CREDIT
    PayMethod.OTHER -> R.string.method_OTHER
}

/** Cobro con pago mixto: cualquier combinación de métodos; el efectivo calcula el vuelto; lo fiado se anota con el nombre. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CobroScreen(ui: CajaUi, vm: CajaViewModel) {
    val cobro = ui.cobro ?: return
    val plan = cobro.plan
    val money = LocalMoney.current

    // El cobro ocupa toda la pantalla: la raíz ya pone el margen superior y aquí solo falta el inferior (la barra de navegación se oculta).
    Column(Modifier.fillMaxSize().navigationBarsPadding().imePadding().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (cobro.doneChangeMinor == null) CuadraButton("‹", vm::cancelCobro, Modifier.size(56.dp))
            Text(stringResource(R.string.pay_title), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            Text(money.format(plan.totalMinor), style = MaterialTheme.typography.headlineMedium)
        }

        if (cobro.doneChangeMinor != null) {
            Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text("✓", style = MaterialTheme.typography.displayLarge, color = CuadraColors.Green)
                Text(stringResource(R.string.pay_done), style = MaterialTheme.typography.headlineMedium)
                if (cobro.doneChangeMinor > 0) {
                    Text(stringResource(R.string.pay_done_change, money.format(cobro.doneChangeMinor)), style = MaterialTheme.typography.displayLarge, color = CuadraColors.Green, textAlign = TextAlign.Center)
                }
            }
            cobro.doneShare?.let { CuadraButton(stringResource(R.string.pay_done_send), { vm.shareDone(it) }, Modifier.fillMaxWidth(), kind = ButtonKind.WHATSAPP) }
            CuadraButton(stringResource(R.string.pay_new_sale), vm::finishCobro, Modifier.fillMaxWidth().padding(bottom = 12.dp), kind = ButtonKind.PRIMARY, height = 60)
            return
        }

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionLabel(stringResource(R.string.pay_how))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PayMethod.entries.forEach { m -> CuadraChip(stringResource(methodRes(m)), plan.entries.any { it.method == m }, { vm.toggleMethod(m) }) }
            }
            Text(stringResource(R.string.pay_combine_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

            plan.entries.forEachIndexed { index, entry -> EntryCard(entry, index, plan.entries.size, cobro, vm) }

            plan.issues().forEach { issue ->
                val text = when (issue) {
                    is PaymentIssue.Missing -> stringResource(R.string.pay_missing, money.format(issue.missingMinor))
                    is PaymentIssue.Excess -> stringResource(R.string.pay_excess, money.format(issue.excessMinor))
                    PaymentIssue.DebtorRequired -> stringResource(R.string.pay_debtor_required)
                    else -> null
                }
                if (text != null) Text(text, color = CuadraColors.Orange, fontWeight = FontWeight.ExtraBold)
            }
        }

        val change = plan.changeMinor
        CuadraButton(
            if (change > 0) stringResource(R.string.pay_confirm_change, money.format(change)) else stringResource(R.string.pay_confirm),
            vm::confirmCobro, Modifier.fillMaxWidth().padding(bottom = 12.dp), kind = ButtonKind.PRIMARY, enabled = plan.isValid && !cobro.saving, height = 60,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EntryCard(entry: PaymentEntry, index: Int, count: Int, cobro: CobroUi, vm: CajaViewModel) {
    val otherLabel = cobro.otherLabel
    val fmt = LocalMoney.current
    CuadraCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(methodRes(entry.method)), fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                // Con varios métodos, el primero se edita y el último toma "el resto" (así siempre suman el total).
                if (count > 1 && index == 0) {
                    var text by remember(entry.method) { mutableStateOf(Money(entry.amountMinor).let { fmt.format(it.minor).substringAfter(' ').replace(",", "") }) }
                    OutlinedTextField(
                        text, { text = it; vm.setAmount(entry.method, it) }, Modifier.size(width = 150.dp, height = 56.dp), singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), label = { Text(stringResource(R.string.pay_amount)) },
                    )
                } else {
                    Text(fmt.format(entry.amountMinor), fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.headlineMedium)
                }
            }
            when (entry.method) {
                PayMethod.CASH -> {
                    Text(stringResource(R.string.pay_pays_with), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        CuadraChip(stringResource(R.string.pay_exact), entry.tenderedMinor == null || entry.tenderedMinor == entry.amountMinor, { vm.setTendered(null) })
                        CashSuggestions.forAmount(entry.amountMinor, fmt.currency.code, fmt.decimals).forEach { s ->
                            CuadraChip(fmt.format(s), entry.tenderedMinor == s, { vm.setTendered(s) })
                        }
                    }
                    if (entry.changeMinor > 0) {
                        Box(Modifier.fillMaxWidth().padding(top = 2.dp), contentAlignment = Alignment.CenterStart) {
                            Text(stringResource(R.string.pay_change) + "  " + fmt.format(entry.changeMinor), color = CuadraColors.Green, style = MaterialTheme.typography.headlineMedium)
                        }
                    }
                }
                PayMethod.CREDIT -> CreditFields(entry, cobro, vm)
                PayMethod.OTHER -> VoiceTextField(otherLabel, vm::setOtherLabel, Modifier.fillMaxWidth(), singleLine = true, label = { Text(stringResource(R.string.pay_other_label)) })
                PayMethod.TRANSFER, PayMethod.CARD -> VoiceTextField(
                    entry.reference.orEmpty(), { vm.setReference(entry.method, it) }, Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text(stringResource(R.string.pay_reference_hint)) },
                )
            }
        }
    }
}

/** A quién se le fía: un nombre basta. El cliente y el teléfono son opcionales; con teléfono se puede mandar el detalle por WhatsApp. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CreditFields(entry: PaymentEntry, cobro: CobroUi, vm: CajaViewModel) {
    val fmt = LocalMoney.current
    VoiceTextField(
        cobro.debtor, vm::setDebtor, Modifier.fillMaxWidth(), singleLine = true, label = { Text(stringResource(R.string.pay_debtor)) },
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
    )
    if (cobro.customerMatches.isNotEmpty() || cobro.nameSuggestions.isNotEmpty()) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            cobro.customerMatches.forEach { cu -> CuadraChip(cu.name + if (cu.balanceMinor > 0) " · " + fmt.format(cu.balanceMinor) else "", false, { vm.pickCustomer(cu) }) }
            cobro.nameSuggestions.forEach { n -> CuadraChip(n, false, { vm.pickName(n) }) }
        }
    }
    OutlinedTextField(
        cobro.debtorPhone, vm::setDebtorPhone, Modifier.fillMaxWidth(), singleLine = true, label = { Text(stringResource(R.string.pay_debtor_phone)) },
        isError = cobro.phoneInvalid, supportingText = { if (cobro.phoneInvalid) Text(stringResource(R.string.pay_debtor_invalid_phone)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
    )
    cobro.customer?.let { cu ->
        val limit = cu.creditLimitMinor
        if (limit != null && cu.balanceMinor + entry.amountMinor > limit) Text(stringResource(R.string.pay_over_limit, fmt.format(limit)), color = CuadraColors.Orange, fontWeight = FontWeight.ExtraBold)
    }
    Text(stringResource(R.string.pay_debtor_optional), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (cobro.customer == null) CuadraChip(stringResource(R.string.pay_debtor_save_customer), cobro.saveAsCustomer, vm::toggleSaveCustomer)
        CuadraChip(stringResource(R.string.pay_debtor_send_wa), cobro.sendWhatsApp, vm::toggleSendWhatsApp)
    }
}

package com.cuadra.caja.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cuadra.caja.R
import com.cuadra.caja.domain.Attention
import com.cuadra.caja.ui.AttentionActions
import com.cuadra.caja.ui.AttentionUi
import com.cuadra.caja.ui.AttentionViewModel
import com.cuadra.caja.ui.common.ButtonKind
import com.cuadra.caja.ui.common.ButtonRow
import com.cuadra.caja.ui.common.CuadraButton
import com.cuadra.caja.ui.common.CuadraCard
import com.cuadra.caja.ui.common.ScreenFrame
import com.cuadra.caja.ui.common.Sheet
import com.cuadra.caja.ui.common.Tag
import com.cuadra.caja.ui.common.TagRow
import com.cuadra.caja.ui.common.Text
import com.cuadra.caja.ui.common.TitleBar
import com.cuadra.caja.ui.common.money
import com.cuadra.caja.ui.theme.CuadraColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Más › «Requiere atención»: lo que el servidor rechazó o aplicó con algo que revisar. Nada se borra solo: la persona decide. */
@Composable
fun AttentionScreen(vm: AttentionViewModel, onBack: () -> Unit) {
    val ui by vm.ui.collectAsState()
    AttentionContent(ui, vm, onBack)
}

/** Sin ViewModel (estado + acciones): es lo que dibuja la guardia de diseño. `zone` fija la hora en las pruebas. */
@Composable
fun AttentionContent(ui: AttentionUi, actions: AttentionActions, onBack: () -> Unit, zone: ZoneId = ZoneId.systemDefault()) {
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val time = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withLocale(locale).withZone(zone)
    ScreenFrame(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        header = {
            TitleBar(Modifier.padding(top = 16.dp), end = { CuadraButton(stringResource(R.string.back), onBack, height = 48) }) {
                Text(stringResource(R.string.att_title), style = MaterialTheme.typography.headlineMedium)
            }
        },
    ) {
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { Text(stringResource(R.string.att_hint), style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted) }
            if (!ui.loading && ui.items.isEmpty()) item {
                Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.att_empty), color = CuadraColors.Muted, textAlign = TextAlign.Center)
                }
            }
            items(ui.items, key = { it.seq }) { AttentionCard(it, time, actions) }
        }
    }
    ui.discarding?.let { item ->
        Sheet(actions::cancelDiscard, actions = {
            ButtonRow {
                CuadraButton(stringResource(R.string.cancel), actions::cancelDiscard, Modifier.share(1f))
                CuadraButton(stringResource(R.string.att_discard), actions::confirmDiscard, Modifier.share(1.3f), kind = ButtonKind.DANGER)
            }
        }) {
            Text(stringResource(R.string.att_discard_title), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(if (item.review) R.string.att_discard_review_body else R.string.att_discard_body, whatText(item)), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun AttentionCard(item: Attention.Item, time: DateTimeFormatter, actions: AttentionActions) {
    CuadraCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(whatText(item), fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium)
            val at = time.format(Instant.ofEpochMilli(item.at))
            Text(item.byName?.let { stringResource(R.string.att_meta, at, it) } ?: at, style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Ink2)
            TagRow {
                if (item.review) Tag(stringResource(R.string.att_tag_review), CuadraColors.Orange, CuadraColors.OrangeSoft)
                else Tag(stringResource(R.string.att_tag_rejected), CuadraColors.Red, CuadraColors.RedSoft)
            }
            Text(reasonText(item), color = if (item.review) CuadraColors.Ink else CuadraColors.Red, style = MaterialTheme.typography.bodyMedium)
            ButtonRow {
                if (item.canRetry) CuadraButton(stringResource(R.string.att_retry), { actions.retry(item) }, Modifier.share(1f), kind = ButtonKind.DARK)
                CuadraButton(stringResource(if (item.review) R.string.att_dismiss_review else R.string.att_discard), { actions.askDiscard(item) }, Modifier.share(1f))
            }
        }
    }
}

@Composable
private fun whatText(item: Attention.Item): String {
    val amount = item.amountMinor?.let { money(it) }
    return when (item.what) {
        Attention.What.SALE -> amount?.let { stringResource(R.string.att_what_sale, it) } ?: stringResource(R.string.att_what_sale_plain)
        Attention.What.SALE_CANCEL -> stringResource(R.string.att_what_sale_cancel)
        Attention.What.SALE_RETURN -> stringResource(R.string.att_what_sale_return)
        Attention.What.CREDIT_PAYMENT -> amount?.let { stringResource(R.string.att_what_credit_payment, it) } ?: stringResource(R.string.att_what_other)
        Attention.What.CREDIT -> amount?.let { stringResource(R.string.att_what_credit, it) } ?: stringResource(R.string.att_what_other)
        Attention.What.EXPENSE -> amount?.let { stringResource(R.string.att_what_expense, it) } ?: stringResource(R.string.att_what_other)
        Attention.What.WITHDRAWAL -> amount?.let { stringResource(R.string.att_what_withdrawal, it) } ?: stringResource(R.string.att_what_other)
        Attention.What.DEPOSIT -> amount?.let { stringResource(R.string.att_what_deposit, it) } ?: stringResource(R.string.att_what_other)
        Attention.What.PRODUCT -> item.name?.let { stringResource(R.string.att_what_product, it) } ?: stringResource(R.string.att_what_product_plain)
        Attention.What.CUSTOMER -> item.name?.let { stringResource(R.string.att_what_customer, it) } ?: stringResource(R.string.att_what_other)
        Attention.What.OTHER -> stringResource(R.string.att_what_other)
    }
}

@Composable
private fun reasonText(item: Attention.Item): String = when (item.reason) {
    Attention.Reason.CONFLICT_COPY -> stringResource(R.string.att_reason_CONFLICT_COPY)
    Attention.Reason.ALREADY_CLOSED_ELSEWHERE -> stringResource(R.string.att_reason_ALREADY_CLOSED)
    Attention.Reason.CREDIT_LIMIT -> if (item.limitMinor != null && item.balanceMinor != null) {
        stringResource(R.string.att_reason_CREDIT_LIMIT, item.customerName ?: "—", money(item.limitMinor), money(item.balanceMinor))
    } else stringResource(R.string.att_reason_CREDIT_LIMIT_plain)
    Attention.Reason.CREDIT_CLOSED -> stringResource(R.string.att_reason_CREDIT_CLOSED)
    Attention.Reason.NO_OPEN_CREDITS -> stringResource(R.string.att_reason_NO_OPEN_CREDITS)
    Attention.Reason.MEMBER_DISABLED -> stringResource(R.string.att_reason_MEMBER_DISABLED)
    Attention.Reason.DEVICE_NOT_TRUSTED -> stringResource(R.string.att_reason_DEVICE_NOT_TRUSTED)
    Attention.Reason.FORBIDDEN -> stringResource(R.string.att_reason_FORBIDDEN)
    Attention.Reason.PAYMENT_MISMATCH -> stringResource(R.string.att_reason_PAYMENT_MISMATCH)
    Attention.Reason.CUSTOMER_REQUIRED -> stringResource(R.string.att_reason_CUSTOMER_REQUIRED)
    Attention.Reason.CREDIT_HAS_PAYMENTS -> stringResource(R.string.att_reason_CREDIT_HAS_PAYMENTS)
    Attention.Reason.NOT_FOUND -> stringResource(R.string.att_reason_NOT_FOUND)
    Attention.Reason.CODE_IN_USE -> stringResource(R.string.att_reason_CODE_IN_USE)
    Attention.Reason.UNDO_NOT_ALLOWED -> stringResource(R.string.att_reason_UNDO_NOT_ALLOWED)
    Attention.Reason.RETURN_REJECTED -> stringResource(R.string.att_reason_RETURN_REJECTED)
    Attention.Reason.LATE_OP_REJECTED -> stringResource(R.string.att_reason_LATE_OP_REJECTED)
    Attention.Reason.OTHER -> stringResource(R.string.att_reason_OTHER, item.code)
}

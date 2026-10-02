package com.cuadra.caja.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.cuadra.caja.R
import com.cuadra.caja.data.local.SaleEntity
import com.cuadra.caja.domain.ReasonCheck
import com.cuadra.caja.domain.RegisterQueue
import com.cuadra.caja.domain.SaleDeletion
import com.cuadra.caja.ui.CajaActions
import com.cuadra.caja.ui.CobroUi
import com.cuadra.caja.ui.QueueCancelUi
import com.cuadra.caja.ui.QueueDetailUi
import com.cuadra.caja.ui.common.ButtonKind
import com.cuadra.caja.ui.common.ButtonRow
import com.cuadra.caja.ui.common.CuadraButton
import com.cuadra.caja.ui.common.CuadraCard
import com.cuadra.caja.ui.common.MoneyText
import com.cuadra.caja.ui.common.SectionLabel
import com.cuadra.caja.ui.common.SegmentedChoice
import com.cuadra.caja.ui.common.Sheet
import com.cuadra.caja.ui.common.SplitRow
import com.cuadra.caja.ui.common.Text
import com.cuadra.caja.ui.common.VoiceTextField
import com.cuadra.caja.ui.common.money
import com.cuadra.caja.ui.theme.CuadraColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

// Cobro en caja (docs/adr/0015): quien atiende envía la cuenta a caja; cualquiera la cobra desde «Por cobrar en caja».

@Composable
private fun timeText(millis: Long?, zone: ZoneId): String {
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    return millis?.let { DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale).withZone(zone).format(Instant.ofEpochMilli(it)) } ?: "-"
}

/**
 * La lista de la caja con el ajuste encendido: un solo lugar con las cuentas «Por cobrar en caja» (la que más espera primero; tocar una abre su detalle) y,
 * debajo, las apartadas comunes (retomar o descartar, como siempre).
 */
@Composable
fun RegisterQueueSheet(
    split: RegisterQueue.Split, actions: CajaActions, zone: ZoneId = ZoneId.systemDefault(), counts: Map<String, Int> = emptyMap(), initialConfirm: String? = null,
    refreshing: Boolean = false,
) {
    var confirmId by rememberSaveable { mutableStateOf(initialConfirm) }
    val confirming = confirmId?.let { id -> split.parked.firstOrNull { it.id == id } }
    if (confirming != null) {
        Sheet({ confirmId = null }, actions = {
            ButtonRow {
                CuadraButton(stringResource(R.string.cancel), { confirmId = null }, Modifier.share(1f))
                CuadraButton(stringResource(R.string.parked_discard), { confirmId = null; actions.discardParked(confirming.id) }, Modifier.share(1.3f), kind = ButtonKind.DANGER)
            }
        }) {
            Text(stringResource(R.string.parked_discard_title), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(R.string.parked_discard_body, confirming.label ?: "-", money(confirming.totalMinor)), style = MaterialTheme.typography.bodyLarge)
        }
        return
    }
    Sheet({ actions.toggleParked(false) }, actions = { CuadraButton(stringResource(R.string.close), { actions.toggleParked(false) }, Modifier.fillMaxWidth()) }) {
        Text(stringResource(R.string.queue_title), style = MaterialTheme.typography.headlineMedium)
        com.cuadra.caja.ui.common.RefreshRow(refreshing) { actions.refreshQueue(com.cuadra.caja.domain.RefreshTrigger.PULLED) }
        if (split.pending.isEmpty()) Text(stringResource(R.string.queue_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
        else SplitRow(end = { MoneyText(money(split.pendingTotalMinor), fontWeight = FontWeight.ExtraBold) }) {
            Text(pluralStringResource(R.plurals.queue_count, split.pending.size, split.pending.size), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        }
        split.pending.forEach { s -> QueueRow(s, counts[s.id] ?: 0, zone) { actions.openQueueTicket(s.id) } }
        if (split.parked.isNotEmpty()) {
            SectionLabel(stringResource(R.string.parked_title))
            split.parked.forEach { s -> ParkedCard(s, actions) { confirmId = s.id } }
        }
    }
}

/** Una cuenta por cobrar en caja: nota, quién la tomó y a qué hora la envió, cuántos productos y el total. */
@Composable
private fun QueueRow(s: SaleEntity, count: Int, zone: ZoneId, onOpen: () -> Unit) {
    CuadraCard(Modifier.fillMaxWidth(), onClick = onOpen) {
        SplitRow(end = { MoneyText(money(s.totalMinor), fontWeight = FontWeight.ExtraBold) }) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(s.label ?: stringResource(R.string.queue_no_note), fontWeight = FontWeight.ExtraBold, maxLines = 3, ellipsize = true)
                Text(stringResource(R.string.queue_row_by, s.sentByName ?: s.createdByName ?: "-", timeText(s.sentToRegisterAt, zone)), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, ellipsize = true)
                if (count > 0) Text(pluralStringResource(R.plurals.queue_items, count, count), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ParkedCard(s: SaleEntity, actions: CajaActions, onDiscard: () -> Unit) {
    CuadraCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SplitRow(end = { MoneyText(money(s.totalMinor), fontWeight = FontWeight.ExtraBold) }) {
                Column {
                    Text(s.label ?: "-", fontWeight = FontWeight.ExtraBold, maxLines = 3, ellipsize = true)
                    s.createdByName?.let { Text(stringResource(R.string.parked_by, it), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, ellipsize = true) }
                }
            }
            ButtonRow {
                CuadraButton(stringResource(R.string.parked_resume), { actions.resume(s.id) }, Modifier.share(1f), kind = ButtonKind.DARK)
                CuadraButton(stringResource(R.string.parked_discard), onDiscard, Modifier.share(1f))
            }
        }
    }
}

/**
 * El detalle de una cuenta por cobrar en caja: nota, quién la tomó y quién la envió (y cuándo), cada línea con su cantidad y el total. Acciones fijas abajo:
 * «Cobrar» (la carga en la caja con la reserva y abre «Cobrar»), «Agregar productos» y «Anular» (con motivo).
 */
@Composable
fun QueueTicketSheet(d: QueueDetailUi, actions: CajaActions, zone: ZoneId = ZoneId.systemDefault()) {
    val s = d.sale
    Sheet(actions::closeQueueTicket, actions = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CuadraButton(stringResource(R.string.queue_charge, money(s.totalMinor)), { actions.chargeQueued(s.id) }, Modifier.fillMaxWidth(), kind = ButtonKind.PRIMARY)
            ButtonRow {
                CuadraButton(stringResource(R.string.queue_add), { actions.addToQueued(s.id) }, Modifier.share(1.3f))
                CuadraButton(stringResource(R.string.queue_cancel), { actions.askCancelQueued(s.id) }, Modifier.share(1f), kind = ButtonKind.DANGER)
            }
            CuadraButton(stringResource(R.string.close), actions::closeQueueTicket, Modifier.fillMaxWidth())
        }
    }) {
        Text(stringResource(R.string.queue_detail_title), style = MaterialTheme.typography.headlineMedium)
        Text(s.label ?: stringResource(R.string.queue_no_note), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, maxLines = 4, ellipsize = true)
        s.createdByName?.let { Text(stringResource(R.string.queue_taken_by, it), style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted) }
        Text(stringResource(R.string.queue_sent_by, s.sentByName ?: s.createdByName ?: "-", timeText(s.sentToRegisterAt, zone)), style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted)
        SectionLabel(stringResource(R.string.sale_lines))
        d.items.forEach { l ->
            SplitRow(end = { MoneyText(money(if (d.promotions.isEmpty()) lineTotal(l) else lineTotal(l) + l.discountMinor), fontWeight = FontWeight.Bold) }) {
                Column {
                    Text(listOfNotNull(l.name, l.variant?.takeIf { it.isNotBlank() }).joinToString(" · "), fontWeight = FontWeight.Bold, maxLines = 3, ellipsize = true)
                    MoneyText(stringResource(R.string.sale_line_qty, qtyText(l.quantityMilli), money(l.unitPriceMinor)), style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted)
                }
            }
        }
        d.promotions.forEach { p -> PromoLineRow(p.quantity, p.priceMinor, p.discountMinor) }
        if (s.discountMinor > 0) SplitRow(end = { MoneyText("−" + money(s.discountMinor), color = CuadraColors.Green) }) { Text(stringResource(R.string.sale_discount)) }
        SplitRow(end = { MoneyText(money(s.totalMinor), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold) }) { Text(stringResource(R.string.sale_total), fontWeight = FontWeight.ExtraBold) }
        Text(stringResource(R.string.queue_not_a_sale), style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted)
    }
}

private fun lineTotal(i: com.cuadra.caja.data.local.SaleItemEntity): Long = Math.floorDiv(i.unitPriceMinor * i.quantityMilli + 500, 1000L) - i.discountMinor

/** «Anular esta cuenta»: no es una venta, pero alguien la tomó: el motivo es obligatorio (5 letras o más) y queda en la actividad. */
@Composable
fun QueueCancelSheet(q: QueueCancelUi, actions: CajaActions) {
    val valid = SaleDeletion.check(q.reason) == ReasonCheck.OK
    Sheet(actions::closeQueueCancel, actions = {
        ButtonRow {
            CuadraButton(stringResource(R.string.cancel), actions::closeQueueCancel, Modifier.share(1f))
            CuadraButton(stringResource(R.string.queue_cancel_confirm), actions::confirmCancelQueued, Modifier.share(1.4f), kind = ButtonKind.DANGER, enabled = valid)
        }
    }) {
        Text(stringResource(R.string.queue_cancel_title), style = MaterialTheme.typography.headlineMedium)
        SplitRow(end = { MoneyText(money(q.totalMinor), fontWeight = FontWeight.ExtraBold) }) { Text(q.label ?: stringResource(R.string.queue_no_note), fontWeight = FontWeight.Bold, maxLines = 3, ellipsize = true) }
        Text(stringResource(R.string.queue_cancel_help), style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted)
        VoiceTextField(
            q.reason, actions::setQueueCancelReason, Modifier.fillMaxWidth(), minLines = 2, label = { Text(stringResource(R.string.sale_delete_reason)) },
            supportingText = if (!valid && q.reason.isNotEmpty()) ({ Text(stringResource(R.string.sale_delete_reason_short)) }) else null, isError = !valid && q.reason.isNotEmpty(),
        )
    }
}

/** Arriba de «Cobrar», con el ajuste: «Cobrar ahora» (lo de siempre) o «Enviar a caja». */
@Composable
fun CobroModeSwitch(cobro: CobroUi, actions: CajaActions) {
    SegmentedChoice(
        stringResource(R.string.pay_mode_label), listOf(stringResource(R.string.pay_mode_now), stringResource(R.string.pay_mode_register)),
        if (cobro.toRegister) 1 else 0, { actions.setCobroMode(it == 1) },
    )
}

/** «Enviar a caja»: sin método de pago ni vuelto; solo una nota opcional (por voz también), p. ej. «Mesa 4, Juan». */
@Composable
fun RegisterSendBody(cobro: CobroUi, actions: CajaActions) {
    Text(stringResource(R.string.pay_register_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    VoiceTextField(
        cobro.registerNote, actions::setRegisterNote, Modifier.fillMaxWidth(), singleLine = true, label = { Text(stringResource(R.string.pay_register_note)) },
        placeholder = { Text(stringResource(R.string.pay_register_note_hint)) }, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
    )
}

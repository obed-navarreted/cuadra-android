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
import com.cuadra.caja.ui.SendUi
import com.cuadra.caja.ui.QueueCancelUi
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.cuadra.caja.ui.QueueDetailUi
import com.cuadra.caja.ui.common.ButtonKind
import com.cuadra.caja.ui.common.ButtonRow
import com.cuadra.caja.ui.common.CuadraButton
import com.cuadra.caja.ui.common.CuadraCard
import com.cuadra.caja.ui.common.MoneyText
import com.cuadra.caja.ui.common.SectionLabel
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
    return millis?.let { com.cuadra.caja.domain.ClockFormat.time(locale, zone).format(Instant.ofEpochMilli(it)) } ?: "-"
}

/**
 * La lista de la caja con el ajuste encendido: un solo lugar con las cuentas «Por cobrar en caja» (la que más espera primero; tocar una abre su detalle) y,
 * debajo, las apartadas comunes (retomar o descartar, como siempre).
 */
@Composable
fun RegisterQueueSheet(
    split: RegisterQueue.Split, actions: CajaActions, zone: ZoneId = ZoneId.systemDefault(), counts: Map<String, Int> = emptyMap(), initialConfirm: String? = null,
    refreshing: Boolean = false,
    /** Buscador (aparece desde [RegisterQueue.SEARCH_FROM] cuentas esperando). */
    query: String = "",
    /** Quién tiene abierta cada cuenta en otro teléfono («La está cobrando Ana»). */
    locks: Map<String, String> = emptyMap(),
    nowMillis: Long? = null,
) {
    // «hace 12 min» se actualiza solo mientras la lista está a la vista (cada 30 s).
    var ticking by androidx.compose.runtime.remember { mutableStateOf(System.currentTimeMillis()) }
    if (nowMillis == null) androidx.compose.runtime.LaunchedEffect(Unit) {
        while (true) { kotlinx.coroutines.delay(30_000); ticking = System.currentTimeMillis() }
    }
    val now = nowMillis ?: ticking
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
        if (split.pending.isEmpty()) {
            Column(Modifier.testTag(TAG_QUEUE_EMPTY), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.queue_empty), fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.queue_empty_help), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else SplitRow(end = { MoneyText(money(split.pendingTotalMinor), fontWeight = FontWeight.ExtraBold) }) {
            Text(pluralStringResource(R.plurals.queue_count, split.pending.size, split.pending.size), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        }
        // Con muchas cuentas: buscar por la nota («mesa 4») o por quién atendió. Con pocas se ven todas de un vistazo.
        if (split.pending.size >= RegisterQueue.SEARCH_FROM || query.isNotEmpty()) {
            VoiceTextField(
                query, actions::setQueueQuery, Modifier.fillMaxWidth().testTag(TAG_QUEUE_SEARCH), singleLine = true, compact = true,
                placeholder = { Text(stringResource(R.string.queue_search)) },
            )
        }
        val shown = RegisterQueue.filter(split.pending, query)
        if (shown.isEmpty() && split.pending.isNotEmpty()) Text(stringResource(R.string.queue_no_match, query.trim()), color = MaterialTheme.colorScheme.onSurfaceVariant)
        shown.forEach { s -> QueueRow(s, counts[s.id] ?: 0, now, locks[s.id]) { actions.openQueueTicket(s.id) } }
        if (split.parked.isNotEmpty()) {
            SectionLabel(stringResource(R.string.parked_title))
            split.parked.forEach { s -> ParkedCard(s, actions) { confirmId = s.id } }
        }
    }
}

/** La espera de una cuenta en palabras: «recién llegada», «hace 12 min», «hace 1 h 5 min». */
@Composable
fun ageText(sentAt: Long?, nowMillis: Long): String = when (val a = RegisterQueue.age(sentAt, nowMillis)) {
    null -> "-"
    RegisterQueue.Age.Now -> stringResource(R.string.queue_age_now)
    is RegisterQueue.Age.Minutes -> pluralStringResource(R.plurals.queue_age_min, a.minutes.toInt(), a.minutes.toInt())
    is RegisterQueue.Age.Hours -> stringResource(R.string.queue_age_hours, a.hours.toInt(), a.minutes.toInt())
}

/**
 * Una cuenta por cobrar en caja: nota, quién la tomó y cuánto lleva esperando (en naranja desde [RegisterQueue.WAITING_LONG_MIN] min), cuántos productos, el
 * total y, si otro teléfono la tiene abierta, «La está cobrando Ana». Toda la tarjeta es el botón (48 dp o más) y se lee de una vez con el lector de pantalla.
 */
@Composable
private fun QueueRow(s: SaleEntity, count: Int, nowMillis: Long, lockedBy: String?, onOpen: () -> Unit) {
    val note = s.label ?: stringResource(R.string.queue_no_note)
    val age = ageText(s.sentToRegisterAt, nowMillis)
    val long = RegisterQueue.waitingLong(s.sentToRegisterAt, nowMillis)
    // «Atendió»: quien tomó la cuenta (si otra persona la reenvió, eso se ve en el detalle). El nombre puede terminar en «…»; la espera va en su propia línea y
    // nunca se corta (es lo que quien cobra necesita ver con letra grande).
    val by = stringResource(R.string.queue_row_by_name, s.createdByName ?: s.sentByName ?: "-")
    val items = if (count > 0) pluralStringResource(R.plurals.queue_items, count, count) else null
    val waitLine = listOfNotNull(age, items).joinToString(" · ")
    val description = stringResource(R.string.queue_row_desc, note, "$by, $waitLine", money(s.totalMinor)) + (lockedBy?.let { " " + stringResource(R.string.queue_locked_tag, it) } ?: "")
    CuadraCard(Modifier.fillMaxWidth().testTag(TAG_QUEUE_ROW).semantics(mergeDescendants = true) { contentDescription = description }, onClick = onOpen) {
        SplitRow(end = { MoneyText(money(s.totalMinor), fontWeight = FontWeight.ExtraBold) }) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(note, fontWeight = FontWeight.ExtraBold, maxLines = 3, ellipsize = true)
                Text(by, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, ellipsize = true)
                Text(waitLine, style = MaterialTheme.typography.bodyMedium, fontWeight = if (long) FontWeight.Bold else null,
                    color = if (long) CuadraColors.Orange else MaterialTheme.colorScheme.onSurfaceVariant)
                lockedBy?.let { LockedTag(it) }
            }
        }
    }
}

/** «La está cobrando Ana» (naranja): la tiene abierta otro teléfono. El nombre de la persona es lo único que puede terminar en «…» (hasta 2 líneas). */
@Composable
private fun LockedTag(name: String) {
    androidx.compose.foundation.layout.Box(
        Modifier.testTag(TAG_QUEUE_LOCKED).background(CuadraColors.OrangeSoft, androidx.compose.foundation.shape.RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 3.dp),
    ) {
        Text(stringResource(R.string.queue_locked_tag, name), color = CuadraColors.Orange, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, maxLines = 2, ellipsize = true)
    }
}

const val TAG_QUEUE_LOCKED = "queue_locked"
const val TAG_QUEUE_ROW = "queue_row"
const val TAG_QUEUE_SEARCH = "queue_search"
const val TAG_QUEUE_EMPTY = "queue_empty"
const val TAG_SEND_NOTE = "send_note"

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
fun QueueTicketSheet(d: QueueDetailUi, actions: CajaActions, zone: ZoneId = ZoneId.systemDefault(), lockedBy: String? = null, nowMillis: Long = System.currentTimeMillis()) {
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
        Text(stringResource(R.string.queue_sent_by, s.sentByName ?: s.createdByName ?: "-", timeText(s.sentToRegisterAt, zone)) + " · " + ageText(s.sentToRegisterAt, nowMillis),
            style = MaterialTheme.typography.bodyMedium, color = if (RegisterQueue.waitingLong(s.sentToRegisterAt, nowMillis)) CuadraColors.Orange else CuadraColors.Muted)
        lockedBy?.let { LockedTag(it) }
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

/**
 * «Enviar a caja» desde la barra (ADR 0015): hoja compacta con el resumen (productos y total), la nota opcional (por voz también; «Mesa, nombre o nota») y un
 * solo botón: un toque más y se envía. Si la cuenta se retomó de la lista para agregar productos, la nota viene escrita y el botón dice «Actualizar en caja».
 * El botón va fijo abajo (visible con el teclado abierto) y «Enviar» del teclado también la envía.
 */
@Composable
fun SendSheet(send: SendUi, lineCount: Int, totalMinor: Long, actions: CajaActions) {
    val confirm = stringResource(if (send.update) R.string.send_update_confirm else R.string.send_confirm)
    Sheet(actions::cancelSend, actions = {
        ButtonRow {
            CuadraButton(stringResource(R.string.cancel), actions::cancelSend, Modifier.share(1f), enabled = !send.saving)
            CuadraButton(confirm, actions::confirmSend, Modifier.share(1.6f), kind = ButtonKind.DARK, enabled = !send.saving && lineCount > 0)
        }
    }) {
        Text(stringResource(if (send.update) R.string.send_update_title else R.string.send_title), style = MaterialTheme.typography.headlineMedium)
        SplitRow(end = { MoneyText(money(totalMinor), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold) }) {
            Text(pluralStringResource(R.plurals.queue_items, lineCount, lineCount), fontWeight = FontWeight.Bold)
        }
        VoiceTextField(
            send.note, actions::setSendNote, Modifier.fillMaxWidth().testTag(TAG_SEND_NOTE), singleLine = true,
            placeholder = { Text(stringResource(R.string.send_note_hint)) },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = androidx.compose.ui.text.input.ImeAction.Send),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSend = { actions.confirmSend() }),
            enabled = !send.saving,
        )
        Text(stringResource(if (send.update) R.string.send_update_help else R.string.send_help), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

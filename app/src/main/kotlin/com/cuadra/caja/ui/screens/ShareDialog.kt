package com.cuadra.caja.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import com.cuadra.caja.ui.common.Text
import com.cuadra.caja.ui.common.money
import com.cuadra.caja.ui.common.ChipGrid
import com.cuadra.caja.ui.common.Sheet
import com.cuadra.caja.ui.common.ButtonRow
import com.cuadra.caja.ui.common.ChipFlow
import com.cuadra.caja.ui.common.SplitRow
import com.cuadra.caja.ui.common.MoneyText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.cuadra.caja.AppContainer
import com.cuadra.caja.R
import com.cuadra.caja.data.share.ShareCardRenderer
import com.cuadra.caja.data.share.WhatsAppSender
import com.cuadra.caja.domain.CardLabels
import com.cuadra.caja.domain.MessageKind
import com.cuadra.caja.domain.MessageTemplates
import com.cuadra.caja.domain.ShareBuilders
import com.cuadra.caja.domain.ShareContent
import com.cuadra.caja.domain.StatementLine
import com.cuadra.caja.ui.ShareRequest
import com.cuadra.caja.ui.common.ButtonKind
import com.cuadra.caja.ui.common.CuadraButton
import com.cuadra.caja.ui.common.CuadraChip
import com.cuadra.caja.ui.common.LocalMoney
import com.cuadra.caja.ui.common.MoneyFormat
import com.cuadra.caja.ui.common.VoiceTextField
import com.cuadra.caja.ui.theme.CuadraColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Lo ya resuelto de una petición: el contenido, a quién va y qué se registra al abrir WhatsApp. */
private data class Resolved(val content: ShareContent, val phone: String?, val creditId: String?, val customerId: String?, val recipient: String, val eventKind: String)

private fun labels(ctx: Context) = CardLabels(
    ctx.getString(R.string.card_reminder), ctx.getString(R.string.card_statement), ctx.getString(R.string.card_credit), ctx.getString(R.string.card_payment),
    ctx.getString(R.string.card_paid_off), ctx.getString(R.string.card_balance), ctx.getString(R.string.card_total), ctx.getString(R.string.card_open_for),
    ctx.getString(R.string.card_ticket),
)

private suspend fun resolve(c: AppContainer, ctx: Context, request: ShareRequest, money: MoneyFormat, locale: Locale): Resolved? {
    val business = c.db.directory().businessNow() ?: return null
    val zone = runCatching { ZoneId.of(business.timezone) }.getOrDefault(ZoneId.systemDefault())
    val day = DateTimeFormatter.ofPattern("d MMM", locale).withZone(zone)
    val now = System.currentTimeMillis()
    val l = labels(ctx)
    fun date(at: Long) = day.format(Instant.ofEpochMilli(at))

    return when (request) {
        is ShareRequest.Reminder -> {
            val customer = request.customerId?.let { c.customers.get(it) }
            val credit = request.creditId?.let { c.credits.get(it) }
            val name = customer?.name ?: credit?.debtorLabel ?: return null
            val balance = customer?.balanceMinor ?: credit?.balanceMinor ?: 0
            val since = customer?.oldestOpenAt ?: credit?.createdAt ?: now
            Resolved(ShareBuilders.reminder(business.name, name, money.format(balance), ((now - since) / DAY).coerceAtLeast(0), l), customer?.phone ?: credit?.debtorPhone,
                request.creditId, request.customerId ?: credit?.customerId, name, "REMINDER_OPENED")
        }
        is ShareRequest.Statement -> {
            val customer = c.customers.get(request.customerId) ?: return null
            val credits = c.db.credits().forCustomer(request.customerId).let { it.first() }
            val payments = c.credits.paymentsForCustomer(request.customerId).first().filter { !it.voided }
            val lines = (credits.filter { it.credit.status != "CANCELLED" }.map { Triple(it.credit.createdAt, ctx.getString(R.string.customer_movement_credit), it.credit.amountMinor to false) } +
                payments.map { Triple(it.occurredAt, ctx.getString(R.string.customer_movement_payment), it.amountMinor to true) })
                .sortedBy { it.first }.map { StatementLine(date(it.first), it.second, money.format(it.third.first), it.third.second) }
            Resolved(ShareBuilders.statement(business.name, customer.name, lines, money.format(customer.balanceMinor), l), customer.phone, null, customer.id, customer.name, "STATEMENT_OPENED")
        }
        is ShareRequest.CreditNew -> Resolved(
            ShareBuilders.creditNew(business.name, request.debtor, date(now), money.format(request.creditedMinor), request.items.map { it.first to money.format(it.second) },
                request.paidNowMinor?.let { money.format(it) }, l, ctx.getString(R.string.card_paid_now)),
            request.phone, request.creditId, request.customerId, request.debtor, "CREDIT_OPENED",
        )
        is ShareRequest.Ticket -> Resolved(
            ShareBuilders.ticket(business.name, date(now), request.items.map { it.first to money.format(it.second) }, money.format(request.totalMinor), l),
            null, null, null, "", "TICKET_OPENED",
        )
        is ShareRequest.Payment -> {
            val r = request.receipt
            Resolved(ShareBuilders.payment(business.name, r.debtorLabel, date(now), money.format(r.amountMinor), money.format(r.customerBalanceMinor ?: r.creditBalanceMinor), r.paidOff, l),
                r.phone, r.creditId, r.customerId, r.debtorLabel, "RECEIPT_OPENED")
        }
    }
}

private const val DAY = 24L * 60 * 60 * 1000

/**
 * "Enviar por WhatsApp": texto editable o imagen, hacia el chat del cliente. La persona revisa y toca enviar; la app solo abre WhatsApp
 * (no puede confirmar que se envió, así que registra "se abrió WhatsApp").
 */
@Composable
fun ShareDialog(container: AppContainer, request: ShareRequest, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val money = LocalMoney.current
    val locale: Locale = LocalConfiguration.current.locales[0]
    val scope = rememberCoroutineScope()
    var resolved by remember(request) { mutableStateOf<Resolved?>(null) }
    var text by remember(request) { mutableStateOf("") }
    var imageTab by remember(request) { mutableStateOf(false) }
    var failed by remember(request) { mutableStateOf(false) }
    var pkg by remember(request) { mutableStateOf(WhatsAppSender.preferred(context)) }
    val installed = remember { WhatsAppSender.installed(context) }
    val needsChoice = installed.size > 1 && pkg == null

    LaunchedEffect(request) {
        val r = resolve(container, context, request, money, locale)
        resolved = r
        if (r != null) {
            val lang = if (locale.language == "en") "en" else "es"
            val template = container.db.templates().get(r.content.kind.name, lang)?.body ?: MessageTemplates.default(r.content.kind, lang)
            text = MessageTemplates.render(template, r.content.vars)
        }
    }

    val r = resolved
    ShareSheet(
        ShareUi(
            loading = r == null,
            titleRes = when (r?.content?.kind) {
                MessageKind.REMINDER -> R.string.share_title_reminder
                MessageKind.STATEMENT -> R.string.share_title_statement
                MessageKind.CREDIT_NEW -> R.string.share_title_credit
                MessageKind.TICKET -> R.string.share_title_ticket
                else -> R.string.share_title_payment
            },
            recipient = r?.recipient.orEmpty(), phone = r?.phone, imageTab = imageTab, text = text, card = r?.content?.card,
            needsChoice = needsChoice, choices = installed, failed = failed,
        ),
        onDismiss = onDismiss, onImageTab = { imageTab = it }, onText = { text = it },
        onPick = { p -> WhatsAppSender.rememberChoice(context, p); pkg = p },
        onOpen = {
            val res = r ?: return@ShareSheet
            scope.launch {
                val sent = if (imageTab) {
                    val file = ShareCardRenderer.render(context, res.content.card)
                    WhatsAppSender.sendImage(context, res.phone, null, file, pkg)
                } else {
                    WhatsAppSender.sendText(context, res.phone, text, pkg)
                }
                if (sent) {
                    container.credits.recordEvent(res.creditId, res.customerId, res.eventKind, if (imageTab) "IMAGE" else "TEXT")
                    onDismiss()
                } else {
                    failed = true
                }
            }
        },
    )
}

/** Lo que muestra la hoja de WhatsApp. */
data class ShareUi(
    val loading: Boolean, val titleRes: Int, val recipient: String, val phone: String?, val imageTab: Boolean, val text: String,
    val card: com.cuadra.caja.domain.ShareCard?, val needsChoice: Boolean, val choices: List<String>, val failed: Boolean,
)

/** La hoja de «Enviar por WhatsApp» sin lógica de envío (recibe todo por parámetros): es lo que dibuja la guardia de diseño. */
@Composable
fun ShareSheet(
    ui: ShareUi, onDismiss: () -> Unit, onImageTab: (Boolean) -> Unit, onText: (String) -> Unit, onPick: (String) -> Unit, onOpen: () -> Unit,
) {
    Sheet(onDismiss, actions = {
        if (!ui.loading) Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ButtonRow {
                CuadraButton(stringResource(R.string.share_open), onOpen, Modifier.share(1.4f), kind = ButtonKind.WHATSAPP, enabled = !ui.needsChoice)
                CuadraButton(stringResource(R.string.share_not_now), onDismiss, Modifier.share(1f))
            }
            // Una línea, bajo el botón: WhatsApp nunca envía solo; se abre con el mensaje listo y la persona toca «Enviar».
            Text(stringResource(R.string.share_hint), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Normal, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else CuadraButton(stringResource(R.string.share_not_now), onDismiss, Modifier.fillMaxWidth())
    }) {
        if (ui.loading || ui.card == null) {
            Text(stringResource(R.string.pin_loading), color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@Sheet
        }
        Text(stringResource(ui.titleRes), style = MaterialTheme.typography.headlineMedium)
        Text(
            if (ui.phone != null) stringResource(R.string.share_to, ui.recipient, "+" + ui.phone) else stringResource(R.string.share_no_phone),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ChipGrid {
            CuadraChip(stringResource(R.string.share_tab_text), !ui.imageTab, { onImageTab(false) })
            CuadraChip(stringResource(R.string.share_tab_image), ui.imageTab, { onImageTab(true) })
        }
        if (!ui.imageTab) {
            VoiceTextField(ui.text, onText, Modifier.fillMaxWidth(), minLines = 5, maxLines = 10)
            Text(stringResource(R.string.share_edit_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            CardPreview(ui.card)
        }
        if (ui.needsChoice) {
            Text(stringResource(R.string.share_which), style = MaterialTheme.typography.labelLarge)
            ChipFlow {
                ui.choices.forEach { p -> CuadraChip(stringResource(if (p == WhatsAppSender.BUSINESS) R.string.share_wa_business else R.string.share_wa_personal), false, { onPick(p) }) }
            }
        }
        if (ui.failed) Text(stringResource(R.string.share_failed), color = CuadraColors.Red, fontWeight = FontWeight.Bold)
    }
}

/** Vista previa de la tarjeta que se enviará como imagen (misma información que el PNG). */
@Composable
private fun CardPreview(card: com.cuadra.caja.domain.ShareCard) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Surface(shape = RoundedCornerShape(18.dp), color = CuadraColors.Ink) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text(card.business, color = CuadraColors.Line, style = MaterialTheme.typography.bodyMedium)
                Text(card.title, color = CuadraColors.Bg, style = MaterialTheme.typography.headlineMedium)
            }
        }
        card.subtitle?.let { Text(it, fontWeight = FontWeight.ExtraBold) }
        card.lines.forEach { l ->
            SplitRow(end = { MoneyText(l.right, fontWeight = if (l.emphasis) FontWeight.ExtraBold else FontWeight.Bold) }) {
                Text(l.left, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        SplitRow(end = { MoneyText(card.totalValue, style = MaterialTheme.typography.headlineMedium, color = CuadraColors.Orange) }) {
            Text(card.totalLabel, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

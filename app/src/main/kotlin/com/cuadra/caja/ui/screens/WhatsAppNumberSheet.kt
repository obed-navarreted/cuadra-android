package com.cuadra.caja.ui.screens

import android.content.Intent
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.cuadra.caja.AppContainer
import com.cuadra.caja.R
import com.cuadra.caja.data.share.WhatsAppSender
import com.cuadra.caja.domain.MessageKind
import com.cuadra.caja.domain.MessageTemplates
import com.cuadra.caja.domain.WhatsAppReceipt
import com.cuadra.caja.ui.ShareRequest
import com.cuadra.caja.ui.common.ButtonKind
import com.cuadra.caja.ui.common.ButtonRow
import com.cuadra.caja.ui.common.ChipFlow
import com.cuadra.caja.ui.common.CuadraButton
import com.cuadra.caja.ui.common.CuadraChip
import com.cuadra.caja.ui.common.LinkAction
import com.cuadra.caja.ui.common.LocalMoney
import com.cuadra.caja.ui.common.NumberField
import com.cuadra.caja.ui.common.Sheet
import com.cuadra.caja.ui.common.Text
import com.cuadra.caja.ui.theme.CuadraColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

/** Lo que muestra la hoja «Enviar comprobante por WhatsApp» (con número). */
data class WhatsAppNumberUi(
    val number: String, val invalid: Boolean = false, val failed: Boolean = false,
    /** Con WhatsApp y WhatsApp Business instaladas y sin elegir: se pregunta cuál. */
    val choices: List<String> = emptyList(), val needsChoice: Boolean = false,
)

/**
 * La hoja chica del comprobante por WhatsApp: el número (con el código del país del negocio ya escrito; no se guarda), «Elegir de contactos» y «Abrir
 * WhatsApp». Sin lógica de envío: es lo que dibuja la guardia de diseño.
 */
@Composable
fun WhatsAppNumberSheet(
    ui: WhatsAppNumberUi, onNumber: (String) -> Unit, onPickContact: () -> Unit, onPick: (String) -> Unit, onOpen: () -> Unit, onDismiss: () -> Unit,
) {
    Sheet(onDismiss, actions = {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ButtonRow {
                CuadraButton(stringResource(R.string.wa_open), onOpen, Modifier.share(1.4f), kind = ButtonKind.WHATSAPP, enabled = !ui.needsChoice)
                CuadraButton(stringResource(R.string.cancel), onDismiss, Modifier.share(1f))
            }
            // WhatsApp nunca envía solo: se abre con el mensaje listo y la persona toca «Enviar».
            Text(stringResource(R.string.share_hint), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Normal, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }) {
        Text(stringResource(R.string.wa_title), style = MaterialTheme.typography.headlineMedium)
        NumberField(
            ui.number, onNumber, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.wa_number), maxLines = 1) }, keyboardType = KeyboardType.Phone,
            isError = ui.invalid, supportingText = { Text(stringResource(if (ui.invalid) R.string.wa_number_invalid else R.string.wa_number_hint)) },
        )
        LinkAction(stringResource(R.string.wa_pick_contact), onPickContact)
        if (ui.needsChoice) {
            Text(stringResource(R.string.share_which), style = MaterialTheme.typography.labelLarge)
            ChipFlow {
                ui.choices.forEach { p -> CuadraChip(stringResource(if (p == WhatsAppSender.BUSINESS) R.string.share_wa_business else R.string.share_wa_personal), false, { onPick(p) }) }
            }
        }
        if (ui.failed) Text(stringResource(R.string.share_failed), color = CuadraColors.Red, fontWeight = FontWeight.Bold)
    }
}

/**
 * «Enviar por WhatsApp» con número para el comprobante de CUALQUIER venta (al terminar de cobrar, con la preferencia encendida, y siempre desde el detalle de
 * una venta en Ventas). El contacto se elige con el selector del sistema (ACTION_PICK sobre teléfonos): no hace falta el permiso de contactos.
 */
@Composable
fun WhatsAppNumberDialog(container: AppContainer, ticket: ShareRequest.Ticket, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val money = LocalMoney.current
    val locale = LocalConfiguration.current.locales[0]
    val scope = rememberCoroutineScope()
    var country by remember { mutableStateOf<String?>(null) }
    var business by remember { mutableStateOf("") }
    var zone by remember { mutableStateOf(ZoneId.systemDefault()) }
    var number by remember { mutableStateOf("") }
    var invalid by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var pkg by remember { mutableStateOf(WhatsAppSender.preferred(context)) }
    val installed = remember { WhatsAppSender.installed(context) }
    LaunchedEffect(Unit) {
        container.db.directory().businessNow()?.let { b ->
            country = b.country
            business = b.name
            zone = runCatching { ZoneId.of(b.timezone) }.getOrDefault(ZoneId.systemDefault())
            if (number.isEmpty()) number = WhatsAppReceipt.prefill(b.country)
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data ?: return@rememberLauncherForActivityResult
        // El selector concede leer SOLO ese número: sin permiso de contactos.
        val picked = runCatching {
            context.contentResolver.query(uri, arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        }.getOrNull()
        if (!picked.isNullOrBlank()) { number = picked; invalid = false }
    }
    WhatsAppNumberSheet(
        WhatsAppNumberUi(number, invalid, failed, installed, installed.size > 1 && pkg == null),
        onNumber = { number = it.filter { c -> c.isDigit() || c in "+ -()" }.take(24); invalid = false; failed = false },
        onPickContact = {
            runCatching { picker.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)) }.onFailure { failed = true }
        },
        onPick = { p -> WhatsAppSender.rememberChoice(context, p); pkg = p },
        onOpen = {
            val digits = WhatsAppReceipt.number(number, country)
            if (digits == null) { invalid = true; return@WhatsAppNumberSheet }
            scope.launch {
                val lang = if (locale.language == "en") "en" else "es"
                val template = container.db.templates().get(MessageKind.TICKET.name, lang)?.body ?: MessageTemplates.default(MessageKind.TICKET, lang)
                val date = DateTimeFormatter.ofPattern("d MMM", locale).withZone(zone).format(Instant.now())
                val text = WhatsAppReceipt.text(template, business, date, ticket.items.map { it.first to money.signed(it.second) }, money.format(ticket.totalMinor))
                if (WhatsAppSender.openChat(context, digits, text, pkg)) onDismiss() else failed = true
            }
        },
        onDismiss = onDismiss,
    )
}

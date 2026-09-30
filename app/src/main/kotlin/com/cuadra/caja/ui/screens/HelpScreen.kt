package com.cuadra.caja.ui.screens

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.cuadra.caja.R
import com.cuadra.caja.domain.SupportRules
import com.cuadra.caja.domain.TicketCategory
import com.cuadra.caja.domain.TicketDraft
import com.cuadra.caja.ui.HelpActions
import com.cuadra.caja.ui.HelpTab
import com.cuadra.caja.ui.HelpUi
import com.cuadra.caja.ui.HelpViewModel
import com.cuadra.caja.ui.asString
import com.cuadra.caja.ui.common.ButtonKind
import com.cuadra.caja.ui.common.CheckRow
import com.cuadra.caja.ui.common.ChipFlow
import com.cuadra.caja.ui.common.ChipGrid
import com.cuadra.caja.ui.common.CuadraButton
import com.cuadra.caja.ui.common.CuadraCard
import com.cuadra.caja.ui.common.CuadraChip
import com.cuadra.caja.ui.common.EmailField
import com.cuadra.caja.ui.common.LinkAction
import com.cuadra.caja.ui.common.NumberField
import com.cuadra.caja.ui.common.ScreenFrame
import com.cuadra.caja.ui.common.SectionLabel
import com.cuadra.caja.ui.common.SplitRow
import com.cuadra.caja.ui.common.Text
import com.cuadra.caja.ui.common.TitleBar
import com.cuadra.caja.ui.common.VoiceTextField
import com.cuadra.caja.ui.message
import com.cuadra.caja.ui.theme.CuadraColors

private val FAQ = listOf(
    R.string.help_faq_link_q to R.string.help_faq_link_a,
    R.string.help_faq_offline_q to R.string.help_faq_offline_a,
    R.string.help_faq_closing_q to R.string.help_faq_closing_a,
    R.string.help_faq_import_q to R.string.help_faq_import_a,
)

/** «Ayuda y contacto»: preguntas frecuentes y el formulario para escribir a soporte. */
@Composable
fun HelpScreen(vm: HelpViewModel, onBack: () -> Unit) {
    LaunchedEffect(Unit) { vm.enter() }
    val ui by vm.ui.collectAsState()
    val email by vm.supportEmail.collectAsState()
    HelpContent(ui, email, vm, onBack)
}

/** Ayuda sin ViewModel (estado + acciones): es lo que dibuja la guardia de diseño. */
@Composable
fun HelpContent(ui: HelpUi, supportEmail: String?, actions: HelpActions, onBack: () -> Unit) {
    val writing = ui.tab == HelpTab.WRITE && ui.reference == null
    val d = ui.draft
    ScreenFrame(
        Modifier.fillMaxSize().imePadding().padding(horizontal = 16.dp),
        header = {
            TitleBar(Modifier.padding(top = 16.dp), end = { CuadraButton(stringResource(R.string.back), onBack, height = 48) }) {
                Text(stringResource(R.string.help_title), style = MaterialTheme.typography.headlineMedium)
            }
            ChipGrid(Modifier.fillMaxWidth()) {
                CuadraChip(stringResource(R.string.help_tab_faq), ui.tab == HelpTab.FAQ, { actions.tab(HelpTab.FAQ) })
                CuadraChip(stringResource(R.string.help_tab_write), ui.tab == HelpTab.WRITE, { actions.tab(HelpTab.WRITE) })
            }
        },
        footer = {
            if (writing) CuadraButton(stringResource(R.string.help_send), actions::send, Modifier.fillMaxWidth().padding(bottom = 8.dp), kind = ButtonKind.PRIMARY, height = 48, enabled = !ui.sending)
        },
    ) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (ui.tab == HelpTab.FAQ) Faq(ui, supportEmail, actions) else if (ui.reference != null) Sent(ui.reference, actions) else Write(ui, d, actions)
        }
    }
}

@Composable
private fun Faq(ui: HelpUi, supportEmail: String?, actions: HelpActions) {
    Text(stringResource(R.string.help_subtitle), style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted)
    FAQ.forEachIndexed { i, (q, a) ->
        val open = i in ui.expanded
        CuadraCard(onClick = { actions.toggle(i) }) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(q), fontWeight = FontWeight.ExtraBold)
                if (open) Text(stringResource(a), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
    CuadraButton(stringResource(R.string.help_still), { actions.tab(HelpTab.WRITE) }, Modifier.fillMaxWidth(), height = 48)
    ContactEmail(supportEmail)
}

@Composable
private fun ContactEmail(email: String?) {
    if (email.isNullOrBlank()) return
    val context = LocalContext.current
    CuadraCard {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(R.string.help_email_or), style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted)
            Text(email, fontWeight = FontWeight.Bold)
            LinkAction(stringResource(R.string.help_email_open), {
                try { context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$email"))) } catch (e: ActivityNotFoundException) { /* sin app de correo: queda el texto para copiarlo */ }
            })
        }
    }
}

@Composable
private fun Sent(reference: String, actions: HelpActions) {
    CuadraCard(color = CuadraColors.GreenSoft, borderColor = CuadraColors.Green) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.help_sent), fontWeight = FontWeight.ExtraBold, color = CuadraColors.Green)
            Text(stringResource(R.string.help_reference, reference), fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.help_sent_note), style = MaterialTheme.typography.bodyMedium)
        }
    }
    CuadraButton(stringResource(R.string.help_another), actions::another, Modifier.fillMaxWidth(), height = 48)
}

@Composable
private fun Write(ui: HelpUi, d: TicketDraft, actions: HelpActions) {
    SectionLabel(stringResource(R.string.help_category))
    ChipFlow {
        TicketCategory.entries.filter { it != TicketCategory.BILLING }.forEach { c -> CuadraChip(stringResource(categoryRes(c)), d.category == c, { actions.update(d.copy(category = c)) }) }
    }
    VoiceTextField(
        d.message, { actions.update(d.copy(message = it.take(SupportRules.MESSAGE_MAX))) }, Modifier.fillMaxWidth(), minLines = 4,
        label = { Text(stringResource(R.string.help_message)) }, supportingText = { Text(stringResource(R.string.help_message_hint, d.message.trim().length, SupportRules.MESSAGE_MAX)) },
    )
    EmailField(d.email, { actions.update(d.copy(email = it.take(SupportRules.EMAIL_MAX))) }, stringResource(R.string.help_email), supporting = stringResource(R.string.help_email_hint))
    NumberField(d.phone, { actions.update(d.copy(phone = it.filter { c -> c.isDigit() || c in "+ -()" }.take(SupportRules.PHONE_MAX))) }, keyboardType = KeyboardType.Phone, label = { Text(stringResource(R.string.help_phone), maxLines = 1) })
    CheckRow(stringResource(R.string.help_diag), d.includeDiagnostics, { actions.update(d.copy(includeDiagnostics = it)) })
    if (d.includeDiagnostics) {
        ui.diagnostics?.let { diag ->
            CuadraCard(color = CuadraColors.Soft) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.help_diag_what), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                    diag.rows().forEach { (k, v) -> SplitRow(end = { Text(v, style = MaterialTheme.typography.bodyMedium, textAlign = androidx.compose.ui.text.style.TextAlign.End) }) { Text(k, style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted) } }
                }
            }
        }
    }
    Text(stringResource(R.string.help_privacy), style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted)
    ui.validation?.let { Text(it.message().asString(), color = CuadraColors.Red, fontWeight = FontWeight.Bold) }
    ui.error?.let { Text(it.asString(), color = CuadraColors.Red, fontWeight = FontWeight.Bold) }
}

private fun categoryRes(c: TicketCategory) = when (c) {
    TicketCategory.QUESTION -> R.string.help_cat_QUESTION
    TicketCategory.PROBLEM -> R.string.help_cat_PROBLEM
    TicketCategory.SUGGESTION -> R.string.help_cat_SUGGESTION
    TicketCategory.BILLING -> R.string.help_cat_BILLING
}

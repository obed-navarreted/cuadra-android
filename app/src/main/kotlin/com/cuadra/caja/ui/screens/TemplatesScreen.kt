package com.cuadra.caja.ui.screens

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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cuadra.caja.R
import com.cuadra.caja.domain.MessageKind
import com.cuadra.caja.domain.TemplateEditor
import com.cuadra.caja.ui.TemplatesActions
import com.cuadra.caja.ui.TemplatesUi
import com.cuadra.caja.ui.TemplatesViewModel
import com.cuadra.caja.ui.common.ButtonKind
import com.cuadra.caja.ui.common.ButtonRow
import com.cuadra.caja.ui.common.ChipFlow
import com.cuadra.caja.ui.common.ChipGrid
import com.cuadra.caja.ui.common.CuadraButton
import com.cuadra.caja.ui.common.CuadraCard
import com.cuadra.caja.ui.common.CuadraChip
import com.cuadra.caja.ui.common.LinkAction
import com.cuadra.caja.ui.common.LocalMoney
import com.cuadra.caja.ui.common.ScreenFrame
import com.cuadra.caja.ui.common.SectionLabel
import com.cuadra.caja.ui.common.Sheet
import com.cuadra.caja.ui.common.Text
import com.cuadra.caja.ui.common.TitleBar
import com.cuadra.caja.ui.common.VoiceTextField
import com.cuadra.caja.ui.theme.CuadraColors
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** «Mensajes de WhatsApp» (dueño y admin): el texto de cada mensaje que se comparte con los clientes, con vista previa y variables. */
@Composable
fun TemplatesScreen(vm: TemplatesViewModel, onBack: () -> Unit) {
    LaunchedEffect(Unit) { vm.enter() }
    val ui by vm.ui.collectAsState()
    val stored by vm.stored.collectAsState()
    val business by vm.business.collectAsState()
    val saved = stored.firstOrNull { it.kind == ui.kind.name && it.locale == ui.locale }?.body
    TemplatesContent(ui, vm.textOf(ui), saved, business?.name.orEmpty(), vm, onBack)
}

/** Plantillas sin ViewModel (estado + acciones): es lo que dibuja la guardia de diseño. `saved` = el texto propio guardado (nulo = se usa el de fábrica). */
@Composable
fun TemplatesContent(ui: TemplatesUi, text: String, saved: String?, businessName: String, actions: TemplatesActions, onBack: () -> Unit) {
    val fmt = LocalMoney.current
    val locale = LocalConfiguration.current.locales[0]
    val today = LocalDate.now().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))
    val unknown = TemplateEditor.unknownVariables(text)
    val canSave = TemplateEditor.canSave(text, saved, ui.kind, ui.locale)
    // Sin el módulo Fiado solo se editan los comprobantes de venta; si estaba elegida una plantilla de fiado, pasa a la primera que queda.
    val kinds = com.cuadra.caja.domain.ModuleVisibility.messageKinds(com.cuadra.caja.ui.common.LocalModules.current)
    androidx.compose.runtime.LaunchedEffect(kinds, ui.kind) { if (ui.kind !in kinds) actions.kind(kinds.first()) }
    ScreenFrame(
        Modifier.fillMaxSize().imePadding().padding(horizontal = 16.dp),
        header = {
            TitleBar(Modifier.padding(top = 16.dp), end = { CuadraButton(stringResource(R.string.back), onBack, height = 48) }) {
                Text(stringResource(R.string.tpl_title), style = MaterialTheme.typography.headlineMedium)
            }
        },
        footer = {
            CuadraButton(stringResource(R.string.save), actions::save, Modifier.fillMaxWidth().padding(bottom = 8.dp), kind = ButtonKind.PRIMARY, height = 48, enabled = canSave && !ui.saving)
        },
    ) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.tpl_hint), style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted)
            SectionLabel(stringResource(R.string.tpl_kind))
            ChipFlow { kinds.forEach { k -> CuadraChip(stringResource(kindRes(k)), ui.kind == k, { actions.kind(k) }) } }
            SectionLabel(stringResource(R.string.tpl_language))
            ChipGrid(Modifier.fillMaxWidth()) {
                CuadraChip(stringResource(R.string.language_es), ui.locale == "es", { actions.locale("es") })
                CuadraChip(stringResource(R.string.language_en), ui.locale == "en", { actions.locale("en") })
            }
            if (ui.offline) Text(stringResource(R.string.tpl_offline), color = CuadraColors.Orange, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
            ui.notice?.let { NoticeLine(it, actions::dismissNotice) }
            VoiceTextField(
                text, actions::edit, Modifier.fillMaxWidth(), minLines = 5, label = { Text(stringResource(R.string.tpl_body)) },
                supportingText = { Text(stringResource(if (saved != null) R.string.tpl_custom else R.string.tpl_factory)) },
            )
            if (saved != null) LinkAction(stringResource(R.string.tpl_reset), actions::askReset)
            SectionLabel(stringResource(R.string.tpl_variables))
            Text(stringResource(R.string.tpl_variables_hint), style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted)
            ChipFlow { TemplateEditor.VARIABLES.forEach { v -> CuadraChip("{$v}", false, { actions.insert(v) }) } }
            if (unknown.isNotEmpty()) Text(stringResource(R.string.tpl_unknown, unknown.joinToString(" ") { "{$it}" }), color = CuadraColors.Orange, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
            if (text.isNotBlank()) {
                SectionLabel(stringResource(R.string.tpl_preview))
                CuadraCard(color = CuadraColors.GreenSoft, borderColor = CuadraColors.Line) {
                    Text(TemplateEditor.preview(text, TemplateEditor.sample(businessName, today, fmt::format)))
                }
            }
        }
    }
    if (ui.confirmReset) {
        Sheet(actions::cancelReset, actions = {
            ButtonRow {
                CuadraButton(stringResource(R.string.cancel), actions::cancelReset, Modifier.share(1f))
                CuadraButton(stringResource(R.string.tpl_reset_confirm), actions::reset, Modifier.share(1.3f), kind = ButtonKind.DARK)
            }
        }) {
            Text(stringResource(R.string.tpl_reset), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(R.string.tpl_reset_body), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

private fun kindRes(k: MessageKind) = when (k) {
    MessageKind.CREDIT_NEW -> R.string.tpl_kind_CREDIT_NEW
    MessageKind.PAYMENT -> R.string.tpl_kind_PAYMENT
    MessageKind.PAID_OFF -> R.string.tpl_kind_PAID_OFF
    MessageKind.REMINDER -> R.string.tpl_kind_REMINDER
    MessageKind.STATEMENT -> R.string.tpl_kind_STATEMENT
    MessageKind.TICKET -> R.string.tpl_kind_TICKET
}

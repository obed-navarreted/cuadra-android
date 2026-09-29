package com.cuadra.caja.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.unit.dp
import com.cuadra.caja.R
import com.cuadra.caja.data.local.MemberEntity
import com.cuadra.caja.data.remote.ScheduleAudienceDto
import com.cuadra.caja.data.remote.ScheduleDto
import com.cuadra.caja.domain.DraftError
import com.cuadra.caja.domain.RuleSummary
import com.cuadra.caja.domain.ScheduleDraft
import com.cuadra.caja.domain.ScheduleDrafts
import com.cuadra.caja.domain.ScheduleRepeat
import com.cuadra.caja.domain.ScheduleWhen
import com.cuadra.caja.ui.SCHEDULE_TEMPLATES
import com.cuadra.caja.ui.SchedulesViewModel
import com.cuadra.caja.ui.ErrorMessage
import com.cuadra.caja.ui.asString
import com.cuadra.caja.ui.common.ButtonKind
import com.cuadra.caja.ui.common.CuadraButton
import com.cuadra.caja.ui.common.CuadraCard
import com.cuadra.caja.ui.common.CuadraChip
import com.cuadra.caja.ui.common.SectionLabel
import com.cuadra.caja.ui.common.Tag
import com.cuadra.caja.ui.common.VoiceTextField
import com.cuadra.caja.ui.theme.CuadraColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val LINKS = listOf(
    null to R.string.sched_action_none,
    "cuadra://caja" to R.string.sched_action_caja,
    "cuadra://cierre" to R.string.sched_action_cierre,
    "cuadra://fiados" to R.string.sched_action_fiados,
    "cuadra://inventario" to R.string.sched_action_inventario,
    "cuadra://gastos" to R.string.sched_action_gastos,
)

/** Programar avisos para el equipo: lista de programaciones y editor. Solo con conexión. */
@Composable
fun SchedulesScreen(vm: SchedulesViewModel, onBack: () -> Unit) {
    // Al entrar se vuelve a pedir la lista: el ViewModel vive mientras la app, y un aviso enviado desde otro lado no estaría.
    androidx.compose.runtime.LaunchedEffect(Unit) { vm.load() }
    val ui by vm.ui.collectAsState()
    val business by vm.business.collectAsState()
    val members by vm.members.collectAsState()
    val zone = runCatching { ZoneId.of(business?.timezone.orEmpty()) }.getOrDefault(ZoneId.systemDefault())
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val time = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withLocale(locale).withZone(zone)

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.sched_title), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            CuadraButton(stringResource(R.string.back), onBack, height = 44)
        }
        when {
            ui.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(stringResource(R.string.sched_loading), color = CuadraColors.Muted) }
            ui.loadError != null -> Column(Modifier.fillMaxWidth().padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text((ui.loadError ?: ErrorMessage(R.string.sched_err_generic)).asString(), color = CuadraColors.Orange, fontWeight = FontWeight.Bold)
                CuadraButton(stringResource(R.string.sched_retry), vm::load, Modifier.fillMaxWidth(), kind = ButtonKind.PRIMARY)
            }
            else -> {
                CuadraButton(stringResource(R.string.sched_new), vm::openNew, Modifier.fillMaxWidth(), kind = ButtonKind.PRIMARY, height = 48)
                if (ui.schedules.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(stringResource(R.string.sched_empty), color = CuadraColors.Muted) }
                else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) { items(ui.schedules, key = { it.id }) { ScheduleCard(it, time, members, vm) } }
            }
        }
    }
    ui.editor?.let { Editor(it, ui.editorError, ui.saveError, ui.saving, members, vm) }
    ui.deleteId?.let {
        Sheet(vm::cancelDelete) {
            Text(stringResource(R.string.sched_delete_title), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(R.string.sched_delete_hint), style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CuadraButton(stringResource(R.string.cancel), vm::cancelDelete, Modifier.weight(1f))
                CuadraButton(stringResource(R.string.sched_delete), vm::confirmDelete, Modifier.weight(1.4f), kind = ButtonKind.DANGER)
            }
        }
    }
    ui.historyFor?.let { s ->
        Sheet(vm::closeHistory) {
            Text(stringResource(R.string.sched_history_title), style = MaterialTheme.typography.headlineMedium)
            Text(s.title, fontWeight = FontWeight.Bold)
            val runs = ui.history
            when {
                runs == null -> Text(stringResource(R.string.sched_loading), color = CuadraColors.Muted)
                runs.isEmpty() -> Text(stringResource(R.string.sched_history_empty), color = CuadraColors.Muted)
                else -> runs.forEach { r ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(instantText(r.runAt, time), fontWeight = FontWeight.Bold)
                            Text(stringResource(R.string.sched_run_recipients, r.recipients), style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted)
                        }
                        if (r.status == "SENT") Tag(stringResource(R.string.sched_run_SENT), CuadraColors.Green, CuadraColors.GreenSoft)
                        else Tag(stringResource(R.string.sched_run_SKIPPED_LATE), CuadraColors.Orange, CuadraColors.OrangeSoft)
                    }
                }
            }
            CuadraButton(stringResource(R.string.close), vm::closeHistory, Modifier.fillMaxWidth(), kind = ButtonKind.DARK)
        }
    }
    ui.message?.let { msg ->
        Sheet(vm::dismissMessage) {
            Text(msg.asString(), style = MaterialTheme.typography.bodyLarge)
            CuadraButton(stringResource(R.string.close), vm::dismissMessage, Modifier.fillMaxWidth(), kind = ButtonKind.DARK)
        }
    }
}

private fun instantText(iso: String, fmt: DateTimeFormatter): String = runCatching { fmt.format(Instant.parse(iso)) }.getOrDefault(iso)

@Composable
private fun ruleText(s: RuleSummary): String = when (s) {
    is RuleSummary.Once -> stringResource(R.string.sched_rule_once, s.date, s.time)
    is RuleSummary.Daily -> stringResource(R.string.sched_rule_daily, s.time)
    is RuleSummary.Weekly -> stringResource(R.string.sched_rule_weekly, s.days.map { dayLetter(it) }.joinToString(", "), s.time)
    is RuleSummary.Monthly -> stringResource(R.string.sched_rule_monthly, s.day, s.time)
    is RuleSummary.EveryN -> stringResource(R.string.sched_rule_every, s.n, s.time)
}

@Composable
private fun dayLetter(day: Int): String = when (day) {
    1 -> stringResource(R.string.sched_day_1); 2 -> stringResource(R.string.sched_day_2); 3 -> stringResource(R.string.sched_day_3); 4 -> stringResource(R.string.sched_day_4)
    5 -> stringResource(R.string.sched_day_5); 6 -> stringResource(R.string.sched_day_6); else -> stringResource(R.string.sched_day_7)
}

@Composable
private fun roleLabel(role: String): String = when (role) {
    "ADMIN" -> stringResource(R.string.sched_role_ADMIN)
    "CASHIER" -> stringResource(R.string.sched_role_CASHIER)
    else -> stringResource(R.string.sched_role_OWNER)
}

@Composable
private fun audienceText(a: ScheduleAudienceDto): String {
    val parts = mutableListOf<String>()
    if (a.all == true) parts += stringResource(R.string.sched_aud_all)
    else if (a.roles.isNotEmpty()) parts += stringResource(R.string.sched_aud_roles, a.roles.map { roleLabel(it) }.joinToString(", "))
    if (a.memberIds.isNotEmpty()) parts += stringResource(R.string.sched_aud_people, a.memberIds.size)
    return parts.joinToString(" · ")
}

@Composable
private fun ScheduleCard(s: ScheduleDto, time: DateTimeFormatter, members: List<MemberEntity>, vm: SchedulesViewModel) {
    val suffix = " " + stringResource(R.string.sched_copy_suffix)
    CuadraCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(s.title, Modifier.weight(1f), fontWeight = FontWeight.ExtraBold, maxLines = 2)
                when {
                    s.nextRunAt != null -> Unit
                    // Un aviso de una sola vez que ya salió (incluye "enviar ahora") no está pausado: está enviado.
                    s.rule.type == "ONCE" && !s.active && s.lastRunAt != null -> Tag(stringResource(R.string.sched_sent_tag), CuadraColors.Green, CuadraColors.GreenSoft)
                    s.active -> Tag(stringResource(R.string.sched_ended), CuadraColors.Ink, CuadraColors.Soft)
                    else -> Tag(stringResource(R.string.sched_paused), CuadraColors.Orange, CuadraColors.OrangeSoft)
                }
            }
            Text(s.body, style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Ink2, maxLines = 3)
            Text(ruleText(ScheduleDrafts.summary(s.rule)), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            Text(audienceText(s.audience), style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted)
            s.nextRunAt?.let { Text(stringResource(R.string.sched_next, instantText(it, time)), style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Green) }
            Text(stringResource(R.string.sched_counts, s.sent, s.read), style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                val once = s.rule.type == "ONCE"
                // Una programación de una sola vez ya enviada no se reanuda: se duplica o se elimina.
                if (!(once && !s.active && s.nextRunAt == null)) Action(stringResource(if (s.active) R.string.sched_pause else R.string.sched_resume)) { vm.toggle(s) }
                Action(stringResource(R.string.sched_edit)) { vm.edit(s) }
                Action(stringResource(R.string.sched_duplicate)) { vm.duplicate(s, suffix) }
                Action(stringResource(R.string.sched_history)) { vm.openHistory(s) }
                Action(stringResource(R.string.sched_delete), CuadraColors.Red) { vm.askDelete(s.id) }
            }
        }
    }
}

@Composable
private fun Action(text: String, color: androidx.compose.ui.graphics.Color = CuadraColors.Green, onClick: () -> Unit) {
    Text(text, color = color, fontWeight = FontWeight.ExtraBold, modifier = Modifier.clickable(onClick = onClick).padding(vertical = 10.dp))
}

@Composable
private fun errorText(e: DraftError): String = stringResource(
    when (e) {
        DraftError.TITLE -> R.string.sched_invalid_TITLE
        DraftError.BODY -> R.string.sched_invalid_BODY
        DraftError.AUDIENCE -> R.string.sched_invalid_AUDIENCE
        DraftError.DATE -> R.string.sched_invalid_DATE
        DraftError.TIME -> R.string.sched_invalid_TIME
        DraftError.DAYS -> R.string.sched_invalid_DAYS
        DraftError.DAY_OF_MONTH -> R.string.sched_invalid_DAY_OF_MONTH
        DraftError.EVERY_DAYS -> R.string.sched_invalid_EVERY_DAYS
        DraftError.END_DATE -> R.string.sched_invalid_END_DATE
    },
)

@Composable
private fun Editor(d: ScheduleDraft, error: DraftError?, saveError: ErrorMessage?, saving: Boolean, members: List<MemberEntity>, vm: SchedulesViewModel) {
    Sheet(vm::closeEditor) {
        Text(stringResource(R.string.sched_editor_title), style = MaterialTheme.typography.headlineMedium)

        SectionLabel(stringResource(R.string.sched_templates))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SCHEDULE_TEMPLATES.forEach { (titleRes, bodyRes) ->
                val title = stringResource(titleRes)
                val body = stringResource(bodyRes)
                CuadraChip(title, d.title == title && d.body == body, { vm.applyTemplate(title, body) })
            }
        }
        VoiceTextField(
            d.title, { vm.updateEditor(d.copy(title = it.take(ScheduleDrafts.TITLE_MAX))) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(stringResource(R.string.sched_field_title)) },
            supportingText = { Text(stringResource(R.string.sched_counter, d.title.length, ScheduleDrafts.TITLE_RECOMMENDED)) }, isError = error == DraftError.TITLE,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        )
        VoiceTextField(
            d.body, { vm.updateEditor(d.copy(body = it.take(ScheduleDrafts.BODY_MAX))) }, Modifier.fillMaxWidth(), minLines = 2, label = { Text(stringResource(R.string.sched_field_body)) },
            supportingText = { Text(stringResource(R.string.sched_counter, d.body.length, ScheduleDrafts.BODY_RECOMMENDED)) }, isError = error == DraftError.BODY,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        )

        SectionLabel(stringResource(R.string.sched_audience))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CuadraChip(stringResource(R.string.sched_aud_all), d.all, { vm.updateEditor(d.copy(all = !d.all)) })
            CuadraChip(stringResource(R.string.sched_role_ADMIN), "ADMIN" in d.roles, { vm.updateEditor(d.copy(roles = d.roles.toggle("ADMIN"))) })
            CuadraChip(stringResource(R.string.sched_role_CASHIER), "CASHIER" in d.roles, { vm.updateEditor(d.copy(roles = d.roles.toggle("CASHIER"))) })
        }
        if (members.isNotEmpty()) {
            Text(stringResource(R.string.sched_people), style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                members.forEach { m -> CuadraChip(m.displayName, m.id in d.memberIds, { vm.updateEditor(d.copy(memberIds = d.memberIds.toggle(m.id))) }) }
            }
        }
        if (error == DraftError.AUDIENCE) Problem(error)

        SectionLabel(stringResource(R.string.sched_action))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LINKS.forEach { (link, label) -> CuadraChip(stringResource(label), d.link == link, { vm.updateEditor(d.copy(link = link)) }) }
        }

        SectionLabel(stringResource(R.string.sched_when))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CuadraChip(stringResource(R.string.sched_when_now), d.whenMode == ScheduleWhen.NOW, { vm.updateEditor(d.copy(whenMode = ScheduleWhen.NOW)) })
            CuadraChip(stringResource(R.string.sched_when_once), d.whenMode == ScheduleWhen.ONCE, { vm.updateEditor(d.copy(whenMode = ScheduleWhen.ONCE)) })
            CuadraChip(stringResource(R.string.sched_when_repeat), d.whenMode == ScheduleWhen.REPEAT, { vm.updateEditor(d.copy(whenMode = ScheduleWhen.REPEAT)) })
        }
        if (d.whenMode != ScheduleWhen.NOW) Text(stringResource(R.string.sched_zone_hint), style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted)
        if (d.whenMode == ScheduleWhen.ONCE) {
            TextField(d.date, R.string.sched_date, error == DraftError.DATE, KeyboardType.Number) { vm.updateEditor(d.copy(date = it.take(10))) }
            TextField(d.time, R.string.sched_time, error == DraftError.TIME, KeyboardType.Number) { vm.updateEditor(d.copy(time = it.take(5))) }
        }
        if (d.whenMode == ScheduleWhen.REPEAT) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CuadraChip(stringResource(R.string.sched_repeat_daily), d.repeat == ScheduleRepeat.DAILY, { vm.updateEditor(d.copy(repeat = ScheduleRepeat.DAILY)) })
                CuadraChip(stringResource(R.string.sched_repeat_weekly), d.repeat == ScheduleRepeat.WEEKLY, { vm.updateEditor(d.copy(repeat = ScheduleRepeat.WEEKLY)) })
                CuadraChip(stringResource(R.string.sched_repeat_monthly), d.repeat == ScheduleRepeat.MONTHLY, { vm.updateEditor(d.copy(repeat = ScheduleRepeat.MONTHLY)) })
                CuadraChip(stringResource(R.string.sched_repeat_every), d.repeat == ScheduleRepeat.EVERY_N, { vm.updateEditor(d.copy(repeat = ScheduleRepeat.EVERY_N)) })
            }
            if (d.repeat == ScheduleRepeat.WEEKLY) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (1..7).forEach { day -> CuadraChip(dayLetter(day), day in d.days, { vm.updateEditor(d.copy(days = d.days.toggle(day))) }) }
                }
                if (error == DraftError.DAYS) Problem(error)
            }
            if (d.repeat == ScheduleRepeat.MONTHLY) TextField(d.dayOfMonth, R.string.sched_day_of_month, error == DraftError.DAY_OF_MONTH, KeyboardType.Number) { vm.updateEditor(d.copy(dayOfMonth = it.take(2))) }
            if (d.repeat == ScheduleRepeat.EVERY_N) TextField(d.everyDays, R.string.sched_every_days, error == DraftError.EVERY_DAYS, KeyboardType.Number) { vm.updateEditor(d.copy(everyDays = it.take(3))) }
            TextField(d.time, R.string.sched_time, error == DraftError.TIME, KeyboardType.Number) { vm.updateEditor(d.copy(time = it.take(5))) }
            TextField(d.endDate, R.string.sched_end_date, error == DraftError.END_DATE, KeyboardType.Number) { vm.updateEditor(d.copy(endDate = it.take(10))) }
        }

        SectionLabel(stringResource(R.string.sched_preview))
        CuadraCard {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(d.title.ifBlank { stringResource(R.string.sched_field_title) }, fontWeight = FontWeight.ExtraBold, color = if (d.title.isBlank()) CuadraColors.Muted else CuadraColors.Ink)
                Text(d.body.ifBlank { stringResource(R.string.sched_field_body) }, style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted)
            }
        }

        // Audiencia y días ya muestran su aviso junto al campo; el resto se dice aquí, junto al botón.
        if (error != null && error != DraftError.AUDIENCE && error != DraftError.DAYS) Problem(error)
        saveError?.let { Text(it.asString(), color = CuadraColors.Red, fontWeight = FontWeight.Bold) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CuadraButton(stringResource(R.string.cancel), vm::closeEditor, Modifier.weight(1f))
            val now = d.whenMode == ScheduleWhen.NOW
            CuadraButton(
                stringResource(if (now) R.string.sched_send_now else if (d.id != null) R.string.sched_save_changes else R.string.sched_save), vm::save, Modifier.weight(1.6f),
                kind = ButtonKind.PRIMARY, enabled = !saving,
            )
        }
    }
}

@Composable
private fun Problem(e: DraftError) = Text(errorText(e), color = CuadraColors.Red, fontWeight = FontWeight.Bold)

@Composable
private fun TextField(value: String, label: Int, error: Boolean, type: KeyboardType, onChange: (String) -> Unit) {
    VoiceTextField(value, onChange, Modifier.fillMaxWidth(), singleLine = true, label = { Text(stringResource(label)) }, isError = error, keyboardOptions = KeyboardOptions(keyboardType = type))
}

private fun <T> Set<T>.toggle(item: T): Set<T> = if (item in this) this - item else this + item

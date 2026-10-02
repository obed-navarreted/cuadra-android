package com.cuadra.caja.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.cuadra.caja.R
import com.cuadra.caja.data.local.BusinessEntity
import com.cuadra.caja.data.sync.calendar
import com.cuadra.caja.data.sync.modules
import com.cuadra.caja.domain.BusinessCalendar
import com.cuadra.caja.domain.BusinessSettingsRules
import com.cuadra.caja.domain.DayRuleAdvice
import com.cuadra.caja.domain.DeleteBusinessRules
import com.cuadra.caja.domain.NotifyDraft
import com.cuadra.caja.domain.PosViews
import com.cuadra.caja.domain.SettingsDraft
import com.cuadra.caja.domain.SettingsError
import com.cuadra.caja.domain.SettingsModules
import com.cuadra.caja.domain.TimeText
import com.cuadra.caja.domain.TimeZones
import com.cuadra.caja.ui.DeleteUi
import com.cuadra.caja.ui.SettingsActions
import com.cuadra.caja.ui.SettingsUi
import com.cuadra.caja.ui.SettingsViewModel
import com.cuadra.caja.ui.asString
import com.cuadra.caja.ui.message
import com.cuadra.caja.ui.common.ButtonKind
import com.cuadra.caja.ui.common.ButtonRow
import com.cuadra.caja.ui.common.CheckRow
import com.cuadra.caja.ui.common.ChipFlow
import com.cuadra.caja.ui.common.CuadraButton
import com.cuadra.caja.ui.common.CuadraCard
import com.cuadra.caja.ui.common.CuadraChip
import com.cuadra.caja.ui.common.LinkAction
import com.cuadra.caja.ui.common.NumberField
import com.cuadra.caja.ui.common.ScreenFrame
import com.cuadra.caja.ui.common.SectionLabel
import com.cuadra.caja.ui.common.Sheet
import com.cuadra.caja.ui.common.SplitRow
import com.cuadra.caja.ui.common.SwitchRow
import com.cuadra.caja.ui.common.Text
import com.cuadra.caja.ui.common.money
import androidx.compose.ui.res.pluralStringResource
import com.cuadra.caja.ui.DiscardQueue
import com.cuadra.caja.ui.common.TimeField
import com.cuadra.caja.ui.common.TitleBar
import com.cuadra.caja.ui.common.VoiceTextField
import com.cuadra.caja.ui.theme.CuadraColors
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** «Ajustes del negocio». El dueño edita; un administrador ve los mismos datos sin poder cambiarlos. */
@Composable
fun BusinessSettingsScreen(vm: SettingsViewModel, onBack: () -> Unit) {
    LaunchedEffect(Unit) { vm.enter() }
    val ui by vm.ui.collectAsState()
    val business by vm.business.collectAsState()
    SettingsContent(ui, business, vm, onBack)
}

/** Ajustes sin ViewModel (estado + acciones): es lo que dibuja la guardia de diseño. */
@Composable
fun SettingsContent(ui: SettingsUi, business: BusinessEntity?, actions: SettingsActions, onBack: () -> Unit) {
    val canEdit = ui.canEdit
    val draft = ui.draft
    ScreenFrame(
        Modifier.fillMaxSize().imePadding().padding(horizontal = 16.dp),
        header = {
            TitleBar(Modifier.padding(top = 16.dp), end = { CuadraButton(stringResource(R.string.back), onBack, height = 48) }) {
                Text(stringResource(R.string.set_title), style = MaterialTheme.typography.headlineMedium)
            }
        },
        footer = {
            if (canEdit && ui.dirty) {
                Column(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.set_unsaved), fontWeight = FontWeight.ExtraBold)
                    ui.validation?.let { Text(it.message().asString(), color = CuadraColors.Red, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium) }
                    ButtonRow {
                        CuadraButton(stringResource(R.string.set_discard), actions::discard, Modifier.share(1f), height = 48, enabled = !ui.saving)
                        CuadraButton(stringResource(R.string.set_save), actions::save, Modifier.share(1.3f), kind = ButtonKind.PRIMARY, height = 48, enabled = !ui.saving)
                    }
                }
            }
        },
    ) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!canEdit && ui.role != null) Text(stringResource(R.string.set_owner_only), color = CuadraColors.Muted, style = MaterialTheme.typography.bodyMedium)
            if (ui.offline) Text(stringResource(R.string.set_offline_note), color = CuadraColors.Orange, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
            ui.loadError?.let { e ->
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(e.asString(), color = CuadraColors.Red, fontWeight = FontWeight.Bold)
                    CuadraButton(stringResource(R.string.sched_retry), actions::retry, Modifier.fillMaxWidth())
                }
            }
            ui.notice?.let { NoticeLine(it, actions::dismissNotice) }
            if (draft == null || business == null) {
                if (ui.loading) Text(stringResource(R.string.set_loading), color = CuadraColors.Muted)
            } else {
                DataCard(draft, business, canEdit, ui.validation, actions)
                DayCard(draft, ui.base ?: draft, business, canEdit, ui.validation, actions)
                if (com.cuadra.caja.domain.ModuleVisibility.credit(com.cuadra.caja.ui.common.LocalModules.current)) CreditCard(draft, canEdit, ui.validation, actions)
                RegisterCard(draft, canEdit, actions)
                ModulesCard(business, canEdit, ui.moduleSaving, actions)
                NotifyCard(ui, canEdit, actions)
                if (ui.isOwner) DangerCard(actions)
            }
        }
    }
    if (ui.confirmDayRule) DayRuleConfirm(actions)
    ui.discardQueue?.let { DiscardQueueConfirm(it, actions) }
    ui.zoneQuery?.let { ZonePicker(it, draft?.timezone.orEmpty(), business?.country.orEmpty(), actions) }
    ui.delete?.let { DeleteSheet(it, ui.base?.name ?: business?.name.orEmpty(), actions) }
}

@Composable
private fun Card(title: String, content: @Composable () -> Unit) {
    CuadraCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun Hint(text: String) = Text(text, style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted)

@Composable
private fun DataCard(d: SettingsDraft, b: BusinessEntity, canEdit: Boolean, validation: SettingsError?, actions: SettingsActions) {
    Card(stringResource(R.string.set_sec_business)) {
        VoiceTextField(
            d.name, { actions.update(d.copy(name = it.take(BusinessSettingsRules.NAME_MAX))) }, Modifier.fillMaxWidth(), singleLine = true, enabled = canEdit,
            label = { Text(stringResource(R.string.set_name)) }, isError = validation == SettingsError.NAME,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
        )
        VoiceTextField(
            d.type, { actions.update(d.copy(type = it.take(BusinessSettingsRules.TYPE_MAX))) }, Modifier.fillMaxWidth(), singleLine = true, enabled = canEdit,
            label = { Text(stringResource(R.string.set_type)) }, supportingText = { Text(stringResource(R.string.set_type_hint)) },
        )
        val locale = LocalConfiguration.current.locales[0]
        if (!canEdit || b.currencyLocked) {
            SplitRow(end = { Text(com.cuadra.caja.domain.CountryChoice.name(b.country, locale), fontWeight = FontWeight.Bold) }) { Text(stringResource(R.string.set_country), color = CuadraColors.Muted) }
            SplitRow(end = { Text(b.currency, fontWeight = FontWeight.Bold) }) { Text(stringResource(R.string.set_currency), color = CuadraColors.Muted) }
            Hint(if (b.currencyLocked) stringResource(R.string.set_currency_locked, b.currency) else stringResource(R.string.set_fixed_hint))
        } else {
            // Antes de la primera venta el país y la moneda todavía se pueden corregir (una tienda de Honduras no debe quedar en córdobas).
            var picking by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
            val options = com.cuadra.caja.domain.CountryChoice.FALLBACK
            val country = options.firstOrNull { it.code == d.country } ?: com.cuadra.caja.domain.CountryOption(d.country, d.currency, d.timezone)
            SplitRow(end = { LinkAction(stringResource(R.string.onboarding_country_change), { picking = !picking }) }) {
                Column {
                    Text(stringResource(R.string.set_country), color = CuadraColors.Muted, style = MaterialTheme.typography.bodyMedium)
                    Text(com.cuadra.caja.domain.CountryChoice.name(d.country, locale), fontWeight = FontWeight.Bold)
                }
            }
            if (picking) com.cuadra.caja.ui.common.ChipFlow {
                options.sortedBy { com.cuadra.caja.domain.CountryChoice.name(it.code, locale) }.forEach { c ->
                    com.cuadra.caja.ui.common.CuadraChip(com.cuadra.caja.domain.CountryChoice.name(c.code, locale), c.code == d.country, {
                        picking = false
                        actions.update(d.copy(country = c.code, currency = c.currency))
                    })
                }
            }
            SectionLabel(stringResource(R.string.set_currency))
            com.cuadra.caja.ui.common.ChipFlow {
                com.cuadra.caja.domain.CountryChoice.currencies(country, d.currency).forEach { cur -> com.cuadra.caja.ui.common.CuadraChip(cur, cur == d.currency, { actions.update(d.copy(currency = cur)) }) }
            }
            Hint(stringResource(R.string.set_currency_open))
        }
    }
}

@Composable
private fun localDate(iso: String): String {
    val locale = LocalConfiguration.current.locales[0]
    return runCatching { LocalDate.parse(iso).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)) }.getOrDefault(iso)
}

@Composable
private fun DayCard(d: SettingsDraft, base: SettingsDraft, b: BusinessEntity, canEdit: Boolean, validation: SettingsError?, actions: SettingsActions) {
    val rules = b.calendar().rules.sortedByDescending { it.from }
    val pending = b.dayRuleEffectiveFrom?.let { from -> rules.firstOrNull { it.from.toString() == from } }
    Card(stringResource(R.string.set_sec_day)) {
        SplitRow(end = { if (canEdit) LinkAction(stringResource(R.string.set_change), actions::openZonePicker) }) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.set_timezone), color = CuadraColors.Muted, style = MaterialTheme.typography.bodyMedium)
                Text(d.timezone, fontWeight = FontWeight.Bold)
                Text(TimeZones.offsetLabel(d.timezone), color = CuadraColors.Muted, style = MaterialTheme.typography.bodyMedium)
            }
        }
        TimeField(
            d.dayCutoff, { actions.update(d.copy(dayCutoff = it)) }, stringResource(R.string.set_cutoff), enabled = canEdit, isError = validation == SettingsError.CUTOFF,
            supporting = stringResource(R.string.set_cutoff_hint),
        )
        // El aviso de la ADR 0011, a la vista desde que se cambia la zona o el corte (y otra vez antes de guardar).
        if (canEdit && BusinessSettingsRules.changesDayRule(d, base)) Text(stringResource(R.string.set_day_rule_warning), color = CuadraColors.Orange, fontWeight = FontWeight.Bold)
        if (canEdit && DayRuleAdvice.dstCutoffTip(d.timezone, d.dayCutoff)) {
            Text(stringResource(R.string.set_dst_tip), color = CuadraColors.Orange, style = MaterialTheme.typography.bodyMedium)
            LinkAction(stringResource(R.string.set_dst_use), { actions.update(d.copy(dayCutoff = DayRuleAdvice.RECOMMENDED_DST.toString())) })
        }
        pending?.let { r ->
            Text(stringResource(R.string.set_pending, localDate(r.from.toString())), color = CuadraColors.Green, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.set_pending_detail, r.zone.id, com.cuadra.caja.domain.ClockFormat.hm(r.cutoff.toString())), color = CuadraColors.Muted, style = MaterialTheme.typography.bodyMedium)
        }
        if (rules.size > 1) {
            SectionLabel(stringResource(R.string.set_history_title))
            Hint(stringResource(R.string.set_history_hint))
            rules.forEach { r ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(if (r.from == BusinessCalendar.SINCE_FOREVER) stringResource(R.string.set_history_always) else stringResource(R.string.set_history_from, localDate(r.from.toString())), fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.set_history_rule, r.zone.id, com.cuadra.caja.domain.ClockFormat.hm(r.cutoff.toString())), color = CuadraColors.Muted, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun CreditCard(d: SettingsDraft, canEdit: Boolean, validation: SettingsError?, actions: SettingsActions) {
    Card(stringResource(R.string.set_sec_credit)) {
        SwitchRow(stringResource(R.string.set_credit_requires), d.creditRequiresCustomer, { actions.update(d.copy(creditRequiresCustomer = it)) }, help = stringResource(R.string.set_credit_requires_help), enabled = canEdit)
        SwitchRow(stringResource(R.string.set_credit_limit), d.creditLimitEnforced, { actions.update(d.copy(creditLimitEnforced = it)) }, help = stringResource(R.string.set_credit_limit_help), enabled = canEdit)
        NumberField(
            d.creditDefaultDueDays, { actions.update(d.copy(creditDefaultDueDays = it.filter { c -> c in '0'..'9' }.take(3))) }, enabled = canEdit, keyboardType = KeyboardType.Number,
            label = { Text(stringResource(R.string.set_credit_due), maxLines = 1) }, isError = validation == SettingsError.DUE_DAYS, supportingText = { Text(stringResource(R.string.set_credit_due_help)) },
        )
        NumberField(
            d.creditOverdueDays, { actions.update(d.copy(creditOverdueDays = it.filter { c -> c in '0'..'9' }.take(3))) }, enabled = canEdit, keyboardType = KeyboardType.Number,
            label = { Text(stringResource(R.string.set_credit_overdue), maxLines = 1) }, isError = validation == SettingsError.OVERDUE_DAYS, supportingText = { Text(stringResource(R.string.set_credit_overdue_help)) },
        )
    }
}

@Composable
private fun RegisterCard(d: SettingsDraft, canEdit: Boolean, actions: SettingsActions) {
    Card(stringResource(R.string.set_sec_register)) {
        Hint(stringResource(R.string.set_pos_hint))
        // Dos pestañas: «Manual» (TYPE) y «Productos» (las vistas viejas QUICK y LIST, que se encienden y apagan juntas).
        ChipFlow {
            val manual = "TYPE" in d.posViews
            val products = d.posViews.any { it in PosViews.PRODUCTS }
            CuadraChip(stringResource(R.string.set_pos_TYPE), manual, { if (canEdit) actions.update(d.copy(posViews = if (manual) d.posViews - "TYPE" else d.posViews + "TYPE")) })
            CuadraChip(stringResource(R.string.set_pos_PRODUCTS), products, { if (canEdit) actions.update(d.copy(posViews = PosViews.withProducts(d.posViews, !products))) })
        }
        if (d.posViews.isEmpty()) Text(stringResource(R.string.set_val_pos_views), color = CuadraColors.Red, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
        // Cobro en caja (ADR 0015): quien atiende arma la cuenta y la envía a caja; cualquiera la cobra después.
        SwitchRow(stringResource(R.string.set_register_checkout), d.registerCheckout, { actions.update(d.copy(registerCheckout = it)) }, help = stringResource(R.string.set_register_checkout_help), enabled = canEdit)
    }
}


@Composable
private fun ModulesCard(b: BusinessEntity, canEdit: Boolean, saving: Boolean, actions: SettingsActions) {
    val modules = b.modules()
    Card(stringResource(R.string.set_sec_modules)) {
        Hint(stringResource(R.string.set_modules_hint))
        SettingsModules.KEYS.forEach { key ->
            val (label, help) = when (key) {
                "credit" -> R.string.module_credit to R.string.set_module_credit_help
                "expenses" -> R.string.module_expenses to R.string.set_module_expenses_help
                "inventory" -> R.string.module_inventory to R.string.set_module_inventory_help
                "catalog" -> R.string.module_catalog to R.string.set_module_catalog_help
                else -> R.string.module_team to R.string.set_module_team_help
            }
            SwitchRow(stringResource(label), SettingsModules.isOn(modules, key), { actions.setModule(key, it) }, help = stringResource(help), enabled = canEdit && !saving)
        }
    }
}

@Composable
private fun NotifyCard(ui: SettingsUi, canEdit: Boolean, actions: SettingsActions) {
    val n = ui.notify
    Card(stringResource(R.string.set_sec_notify)) {
        Hint(stringResource(R.string.set_notify_hint))
        if (n == null) {
            Text(stringResource(if (ui.notifyOffline) R.string.set_notify_offline else R.string.set_loading), color = if (ui.notifyOffline) CuadraColors.Orange else CuadraColors.Muted, style = MaterialTheme.typography.bodyMedium)
            return@Card
        }
        TimeField(n.quietStart, { actions.updateNotify(n.copy(quietStart = it)) }, stringResource(R.string.set_quiet_start), enabled = canEdit)
        TimeField(n.quietEnd, { actions.updateNotify(n.copy(quietEnd = it)) }, stringResource(R.string.set_quiet_end), enabled = canEdit, supporting = stringResource(R.string.set_quiet_help))
        SwitchRow(stringResource(R.string.set_summary), n.summaryEnabled, { actions.updateNotify(n.copy(summaryEnabled = it)) }, help = stringResource(R.string.set_summary_help), enabled = canEdit)
        TimeField(n.summaryTime, { actions.updateNotify(n.copy(summaryTime = it)) }, stringResource(R.string.set_summary_time), enabled = canEdit && n.summaryEnabled)
        NumberField(
            n.staleHours, { actions.updateNotify(n.copy(staleHours = it.filter { c -> c in '0'..'9' }.take(3))) }, enabled = canEdit, keyboardType = KeyboardType.Number,
            label = { Text(stringResource(R.string.set_stale), maxLines = 1) }, supportingText = { Text(stringResource(R.string.set_stale_help)) },
        )
        ui.notifyNotice?.let { NoticeLine(it, actions::dismissNotice) }
        if (canEdit && ui.notifyDirty) CuadraButton(stringResource(R.string.set_notify_save), actions::saveNotify, Modifier.fillMaxWidth(), kind = ButtonKind.PRIMARY, height = 48, enabled = !ui.notifySaving)
    }
}

@Composable
private fun DangerCard(actions: SettingsActions) {
    CuadraCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.set_sec_danger), color = CuadraColors.Red, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.set_delete_title), fontWeight = FontWeight.Bold)
            Hint(stringResource(R.string.set_delete_body))
            CuadraButton(stringResource(R.string.set_delete_button), actions::openDelete, Modifier.fillMaxWidth(), kind = ButtonKind.OUTLINE, height = 48)
        }
    }
}

// ---------- hojas ----------

/** El aviso de la ADR 0011 antes de guardar un cambio de zona o de corte. */
@Composable
private fun DayRuleConfirm(actions: SettingsActions) {
    Sheet(actions::cancelConfirm, actions = {
        ButtonRow {
            CuadraButton(stringResource(R.string.cancel), actions::cancelConfirm, Modifier.share(1f))
            CuadraButton(stringResource(R.string.set_confirm_save), actions::confirmSave, Modifier.share(1.3f), kind = ButtonKind.PRIMARY)
        }
    }) {
        Text(stringResource(R.string.set_confirm_title), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.set_day_rule_warning), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.set_confirm_body), style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted)
    }
}

/** Apagar «Cobro en caja» con cuentas por cobrar: se avisa cuántas son y que se anularán (ADR 0015). */
@Composable
private fun DiscardQueueConfirm(q: DiscardQueue, actions: SettingsActions) {
    Sheet(actions::cancelDiscard, actions = {
        ButtonRow {
            CuadraButton(stringResource(R.string.cancel), actions::cancelDiscard, Modifier.share(1f))
            CuadraButton(stringResource(R.string.set_discard_queue_confirm), actions::confirmDiscard, Modifier.share(1.3f), kind = ButtonKind.DANGER)
        }
    }) {
        Text(stringResource(R.string.set_discard_queue_title), style = MaterialTheme.typography.headlineMedium)
        Text(pluralStringResource(R.plurals.set_discard_queue_body, q.count, q.count, money(q.totalMinor)), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ZonePicker(query: String, current: String, country: String, actions: SettingsActions) {
    val options = TimeZones.suggest(query, current, country)
    Sheet(actions::closeZonePicker, actions = { CuadraButton(stringResource(R.string.cancel), actions::closeZonePicker, Modifier.fillMaxWidth()) }) {
        Text(stringResource(R.string.set_zone_title), style = MaterialTheme.typography.headlineMedium)
        VoiceTextField(query, actions::zoneQuery, Modifier.fillMaxWidth(), singleLine = true, label = { Text(stringResource(R.string.set_zone_search)) })
        Hint(stringResource(R.string.set_zone_hint))
        if (options.isEmpty()) Text(stringResource(R.string.set_zone_none), color = CuadraColors.Muted)
        options.forEach { id ->
            CuadraCard(onClick = { actions.pickZone(id) }, color = if (id == current) CuadraColors.GreenSoft else CuadraColors.Surface) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(id, fontWeight = FontWeight.Bold)
                    Text(TimeZones.offsetLabel(id), style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted)
                }
            }
        }
    }
}

/** Eliminar el negocio: qué pasa exactamente, el nombre escrito, la casilla «Entiendo que…» y el botón final (solo con las tres cosas). */
@Composable
private fun DeleteSheet(d: DeleteUi, businessName: String, actions: SettingsActions) {
    val ready = DeleteBusinessRules.canConfirm(d.typed, businessName, d.understood, d.busy)
    Sheet(actions::closeDelete, actions = {
        ButtonRow {
            CuadraButton(stringResource(R.string.cancel), actions::closeDelete, Modifier.share(1f), enabled = !d.busy)
            CuadraButton(stringResource(R.string.set_delete_confirm), actions::confirmDelete, Modifier.share(1.3f), kind = ButtonKind.DANGER, enabled = ready)
        }
    }) {
        Text(stringResource(R.string.set_delete_sheet_title), style = MaterialTheme.typography.headlineMedium, color = CuadraColors.Red)
        Text(stringResource(R.string.set_delete_what), fontWeight = FontWeight.ExtraBold)
        listOf(R.string.set_delete_p1, R.string.set_delete_p2, R.string.set_delete_p3, R.string.set_delete_p4).forEach { Text("• " + stringResource(it), style = MaterialTheme.typography.bodyMedium) }
        Text(stringResource(R.string.set_delete_type_intro), fontWeight = FontWeight.Bold)
        Text(businessName, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium)
        VoiceTextField(
            d.typed, { actions.updateDelete(d.copy(typed = it)) }, Modifier.fillMaxWidth(), singleLine = true, enabled = !d.busy,
            label = { Text(stringResource(R.string.set_delete_type_label)) }, supportingText = { Text(stringResource(R.string.set_delete_type_help)) },
        )
        CheckRow(stringResource(R.string.set_delete_check), d.understood, { actions.updateDelete(d.copy(understood = it)) }, enabled = !d.busy)
        d.error?.let { Text(it.asString(), color = CuadraColors.Red, fontWeight = FontWeight.Bold) }
    }
}

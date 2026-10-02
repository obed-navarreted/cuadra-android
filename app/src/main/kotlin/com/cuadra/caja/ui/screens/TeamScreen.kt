package com.cuadra.caja.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cuadra.caja.R
import com.cuadra.caja.data.local.MemberEntity
import com.cuadra.caja.domain.AccessCode
import com.cuadra.caja.domain.PinRules
import com.cuadra.caja.ui.common.DigitsCodeField
import com.cuadra.caja.domain.TeamRules
import com.cuadra.caja.ui.ConfirmKind
import com.cuadra.caja.ui.TeamActions
import com.cuadra.caja.ui.TeamDialog
import com.cuadra.caja.ui.TeamUi
import com.cuadra.caja.ui.TeamViewModel
import com.cuadra.caja.ui.asString
import com.cuadra.caja.ui.common.Avatar
import com.cuadra.caja.ui.common.ButtonKind
import com.cuadra.caja.ui.common.ButtonRow
import com.cuadra.caja.ui.common.ChipFlow
import com.cuadra.caja.ui.common.ColorSwatch
import com.cuadra.caja.ui.common.CuadraButton
import com.cuadra.caja.ui.common.CuadraCard
import com.cuadra.caja.ui.common.CuadraChip
import com.cuadra.caja.ui.common.ScreenFrame
import com.cuadra.caja.ui.common.SectionLabel
import com.cuadra.caja.ui.common.Sheet
import com.cuadra.caja.ui.common.Tag
import com.cuadra.caja.ui.common.TagRow
import com.cuadra.caja.ui.common.Text
import com.cuadra.caja.ui.common.TitleBar
import com.cuadra.caja.ui.common.VoiceTextField
import com.cuadra.caja.ui.common.memberColor
import com.cuadra.caja.ui.theme.CuadraColors

/** Equipo → Personas (dueño y admin). Al abrir se vuelve a pedir el directorio; sin conexión queda lo último guardado. */
@Composable
fun TeamScreen(vm: TeamViewModel, onTab: (TeamTab) -> Unit, onMyAccount: () -> Unit, onBack: () -> Unit) {
    LaunchedEffect(Unit) { vm.enter() }
    val ui by vm.ui.collectAsState()
    val members by vm.members.collectAsState()
    com.cuadra.caja.ui.common.Refreshing(vm.refresher, busy = ui.loading, showOffline = false) { TeamContent(ui, members, vm, onTab, onMyAccount, onBack) }
}

/** Personas sin ViewModel (estado + acciones): es lo que dibuja la guardia de diseño. */
@Composable
fun TeamContent(ui: TeamUi, members: List<MemberEntity>, actions: TeamActions, onTab: (TeamTab) -> Unit, onMyAccount: () -> Unit, onBack: () -> Unit) {
    val canAdd = TeamRules.assignableRoles(ui.viewerRole).isNotEmpty()
    ScreenFrame(
        Modifier.fillMaxSize().imePadding().padding(horizontal = 16.dp),
        header = {
            TitleBar(Modifier.padding(top = 16.dp), end = { CuadraButton(stringResource(R.string.back), onBack, height = 48) }) {
                Text(stringResource(R.string.team_title), style = MaterialTheme.typography.headlineMedium)
            }
            TeamTabs(TeamTab.PEOPLE, onTab)
        },
        footer = {
            if (canAdd) CuadraButton(stringResource(R.string.team_add), actions::openNew, Modifier.fillMaxWidth().padding(bottom = 8.dp), kind = ButtonKind.PRIMARY, height = 48)
        },
    ) {
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { AccessCodeCard(ui.businessName, ui.accessCode, ui.viewerRole == TeamRules.OWNER, actions) }
            item { Text(stringResource(R.string.team_hint), style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted) }
            if (ui.offline) item { Text(stringResource(R.string.team_offline_note), color = CuadraColors.Orange, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium) }
            ui.loadError?.let { e ->
                item {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(e.asString(), color = CuadraColors.Red, fontWeight = FontWeight.Bold)
                        CuadraButton(stringResource(R.string.sched_retry), actions::refresh, Modifier.fillMaxWidth())
                    }
                }
            }
            ui.notice?.let { n -> item { NoticeLine(n, actions::dismissNotice) } }
            if (members.isEmpty()) item {
                Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                    Text(stringResource(if (ui.loading) R.string.pin_loading else R.string.team_empty), color = CuadraColors.Muted, textAlign = TextAlign.Center)
                }
            }
            items(members, key = { it.id }) { m -> MemberRow(m, m.id == ui.viewerId) { actions.open(m) } }
        }
    }
    ui.selected?.let { MemberSheet(it, ui, actions, onMyAccount) }
    ui.dialog?.let { TeamDialogs(it, ui.saving, TeamRules.assignableRoles(ui.viewerRole), actions) }
}

@Composable
private fun MemberTags(m: MemberEntity, self: Boolean) {
    TagRow {
        if (self) Tag(stringResource(R.string.team_you), CuadraColors.Ink, CuadraColors.Soft)
        when (m.role) {
            TeamRules.OWNER -> Tag(teamRoleLabel(m.role), CuadraColors.Green, CuadraColors.GreenSoft)
            TeamRules.ADMIN -> Tag(teamRoleLabel(m.role), CuadraColors.Orange, CuadraColors.OrangeSoft)
            else -> Tag(teamRoleLabel(m.role), CuadraColors.Ink, CuadraColors.Soft)
        }
        if (m.status != "ACTIVE") Tag(stringResource(R.string.team_status_disabled), CuadraColors.Red, CuadraColors.RedSoft)
        if (m.pinSet) Tag(stringResource(R.string.team_pin_set), CuadraColors.Ink, CuadraColors.Soft) else Tag(stringResource(R.string.team_no_pin), CuadraColors.Orange, CuadraColors.OrangeSoft)
        if (m.hasGoogle) Tag(stringResource(R.string.team_google), CuadraColors.Green, CuadraColors.GreenSoft)
    }
}

@Composable
private fun MemberRow(m: MemberEntity, self: Boolean, onClick: () -> Unit) {
    CuadraCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Avatar(memberInitials(m.displayName), color = memberColor(m.color))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(m.displayName, fontWeight = FontWeight.ExtraBold, maxLines = 2, ellipsize = true)
                MemberTags(m, self)
            }
        }
    }
}

/** Solo las acciones que `canManage` permite sobre esa persona; sin ninguna, se explica por qué. */
@Composable
private fun MemberSheet(m: MemberEntity, ui: TeamUi, actions: TeamActions, onMyAccount: () -> Unit) {
    val self = m.id == ui.viewerId
    val perms = TeamRules.canManage(ui.viewerRole, self, m.role)
    Sheet(actions::closeSheet, actions = { CuadraButton(stringResource(R.string.close), actions::closeSheet, Modifier.fillMaxWidth(), kind = ButtonKind.DARK) }) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Avatar(memberInitials(m.displayName), color = memberColor(m.color))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(m.displayName, style = MaterialTheme.typography.headlineMedium, maxLines = 3, ellipsize = true)
                MemberTags(m, self)
            }
        }
        if (!perms.any) {
            Text(stringResource(if (m.role == TeamRules.OWNER) R.string.team_owner_locked else R.string.team_no_actions), color = CuadraColors.Muted, fontWeight = FontWeight.Bold)
        }
        if (perms.edit) CuadraButton(stringResource(R.string.team_edit), { actions.openEdit(m) }, Modifier.fillMaxWidth())
        if (perms.changeRole && m.role != TeamRules.OWNER) {
            if (m.role == TeamRules.ADMIN) CuadraButton(stringResource(R.string.team_make_cashier), { actions.openConfirm(m, ConfirmKind.MAKE_CASHIER) }, Modifier.fillMaxWidth())
            else CuadraButton(stringResource(R.string.team_make_admin), { actions.openConfirm(m, ConfirmKind.MAKE_ADMIN) }, Modifier.fillMaxWidth())
        }
        if (perms.disable) {
            if (m.status == "ACTIVE") CuadraButton(stringResource(R.string.team_disable), { actions.openConfirm(m, ConfirmKind.DISABLE) }, Modifier.fillMaxWidth())
            else CuadraButton(stringResource(R.string.team_enable), { actions.openConfirm(m, ConfirmKind.ENABLE) }, Modifier.fillMaxWidth())
        }
        if (perms.resetPin) {
            // El PIN propio se cambia en «Mi cuenta», donde se pide primero el actual.
            if (self) CuadraButton(stringResource(R.string.team_change_my_pin), { actions.closeSheet(); onMyAccount() }, Modifier.fillMaxWidth())
            else CuadraButton(stringResource(R.string.team_reset_pin), { actions.openPin(m) }, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun TeamDialogs(d: TeamDialog, saving: Boolean, roles: List<String>, actions: TeamActions) {
    when (d) {
        is TeamDialog.Edit -> {
            Sheet(actions::closeDialog, actions = { CancelSave(saving = saving, enabled = TeamRules.cleanName(d.name).isNotEmpty(), actions = actions) }) {
                Text(stringResource(R.string.team_edit_title), style = MaterialTheme.typography.headlineMedium)
                VoiceTextField(d.name, { actions.updateDialog(d.copy(name = it.take(TeamRules.NAME_MAX))) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(stringResource(R.string.team_name_label)) }, supportingText = { Text(stringResource(R.string.team_name_help)) })
                SectionLabel(stringResource(R.string.team_color))
                ChipFlow(spacing = 0.dp) {
                    TeamRules.COLORS.forEach { hex -> ColorSwatch(memberColor(hex) ?: CuadraColors.Soft, d.color.equals(hex, ignoreCase = true), hex) { actions.updateDialog(d.copy(color = hex)) } }
                }
                ErrorText(d.error)
            }
        }
        is TeamDialog.Confirm -> {
            val (title, body, action) = when (d.kind) {
                ConfirmKind.MAKE_ADMIN -> Triple(R.string.team_make_admin, R.string.team_confirm_make_admin, R.string.team_confirm_do)
                ConfirmKind.MAKE_CASHIER -> Triple(R.string.team_make_cashier, R.string.team_confirm_make_cashier, R.string.team_confirm_do)
                ConfirmKind.DISABLE -> Triple(R.string.team_disable, R.string.team_confirm_disable, R.string.team_disable)
                ConfirmKind.ENABLE -> Triple(R.string.team_enable, R.string.team_confirm_enable, R.string.team_enable)
            }
            Sheet(actions::closeDialog, actions = {
                ButtonRow {
                    CuadraButton(stringResource(R.string.cancel), actions::closeDialog, Modifier.share(1f))
                    CuadraButton(stringResource(action), actions::confirm, Modifier.share(1.3f), kind = if (d.kind == ConfirmKind.DISABLE) ButtonKind.DANGER else ButtonKind.DARK, enabled = !saving)
                }
            }) {
                Text(stringResource(title), style = MaterialTheme.typography.headlineMedium)
                Text(stringResource(body, d.member.displayName), style = MaterialTheme.typography.bodyLarge)
                ErrorText(d.error)
            }
        }
        is TeamDialog.Pin -> {
            Sheet(actions::closeDialog, actions = { CancelSave(saving = saving, enabled = PinRules.canSave(d.pin, d.confirm), actions = actions, label = R.string.team_pin_save) }) {
                Text(stringResource(R.string.team_pin_title, d.member.displayName), style = MaterialTheme.typography.headlineMedium, maxLines = 4, ellipsize = true)
                PinPair(d.pin, d.confirm, { actions.updateDialog(d.copy(pin = it)) }, { actions.updateDialog(d.copy(confirm = it)) }, mustChange = d.mustChange, onMustChange = { actions.updateDialog(d.copy(mustChange = it)) })
                ErrorText(d.error)
            }
        }
        is TeamDialog.RenewCode -> {
            Sheet(actions::closeDialog, actions = {
                ButtonRow {
                    CuadraButton(stringResource(R.string.cancel), actions::closeDialog, Modifier.share(1f))
                    CuadraButton(stringResource(R.string.code_renew_do), actions::confirm, Modifier.share(1.3f), kind = ButtonKind.DARK, enabled = !saving)
                }
            }) {
                Text(stringResource(R.string.code_renew), style = MaterialTheme.typography.headlineMedium)
                Text(stringResource(R.string.code_renew_body), style = MaterialTheme.typography.bodyLarge)
                ErrorText(d.error)
            }
        }
        is TeamDialog.ChooseCode -> {
            Sheet(actions::closeDialog, actions = { CancelSave(saving = saving, enabled = AccessCode.isChosenValid(d.code), actions = actions) }) {
                Text(stringResource(R.string.code_choose), style = MaterialTheme.typography.headlineMedium)
                Text(stringResource(R.string.code_choose_body), style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted)
                DigitsCodeField(
                    d.code, { actions.updateDialog(d.copy(code = it)) }, stringResource(R.string.code_choose_label),
                    isError = d.code.isNotEmpty() && d.code.length == AccessCode.LENGTH && !AccessCode.isChosenValid(d.code), supporting = stringResource(R.string.code_choose_help),
                )
                ErrorText(d.error)
            }
        }
        is TeamDialog.Credentials -> {
            Sheet(actions::closeDialog, actions = { CuadraButton(stringResource(R.string.cred_done), actions::closeDialog, Modifier.fillMaxWidth(), kind = ButtonKind.DARK) }) {
                Text(stringResource(R.string.team_created_title), style = MaterialTheme.typography.headlineMedium)
                CredentialsCard(d.business, d.code, d.username, d.pin)
            }
        }
        is TeamDialog.New -> {
            val ready = TeamRules.cleanName(d.name).isNotEmpty() && PinRules.canSave(d.pin, d.confirm)
            Sheet(actions::closeDialog, actions = { CancelSave(saving = saving, enabled = ready, actions = actions, label = R.string.team_create) }) {
                Text(stringResource(R.string.team_add), style = MaterialTheme.typography.headlineMedium)
                Text(stringResource(R.string.team_new_hint), style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted)
                VoiceTextField(d.name, { actions.updateDialog(d.copy(name = it.take(TeamRules.NAME_MAX))) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(stringResource(R.string.team_name_label)) }, supportingText = { Text(stringResource(R.string.team_name_help)) })
                SectionLabel(stringResource(R.string.team_role_label))
                ChipFlow {
                    // Solo lo que el rol de quien mira puede asignar (nunca dueño).
                    roles.forEach { r -> CuadraChip(teamRoleLabel(r), d.role == r, { actions.updateDialog(d.copy(role = r)) }) }
                }
                PinPair(d.pin, d.confirm, { actions.updateDialog(d.copy(pin = it)) }, { actions.updateDialog(d.copy(confirm = it)) }, mustChange = d.mustChange, onMustChange = { actions.updateDialog(d.copy(mustChange = it)) })
                ErrorText(d.error)
            }
        }
    }
}

@Composable
private fun CancelSave(saving: Boolean, enabled: Boolean, actions: TeamActions, label: Int = R.string.save) {
    ButtonRow {
        CuadraButton(stringResource(R.string.cancel), actions::closeDialog, Modifier.share(1f))
        CuadraButton(stringResource(label), actions::save, Modifier.share(1.3f), kind = ButtonKind.PRIMARY, enabled = enabled && !saving)
    }
}

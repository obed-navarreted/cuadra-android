package com.cuadra.caja.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cuadra.caja.R
import com.cuadra.caja.data.remote.DeviceDto
import com.cuadra.caja.ui.DevicesActions
import com.cuadra.caja.ui.DevicesUi
import com.cuadra.caja.ui.DevicesViewModel
import com.cuadra.caja.ui.asString
import com.cuadra.caja.ui.common.ButtonKind
import com.cuadra.caja.ui.common.ButtonRow
import com.cuadra.caja.ui.common.CuadraButton
import com.cuadra.caja.ui.common.CuadraCard
import com.cuadra.caja.ui.common.LinkAction
import com.cuadra.caja.ui.common.ScreenFrame
import com.cuadra.caja.ui.common.Sheet
import com.cuadra.caja.ui.common.SplitRow
import com.cuadra.caja.ui.common.Tag
import com.cuadra.caja.ui.common.TagRow
import com.cuadra.caja.ui.common.Text
import com.cuadra.caja.ui.common.TitleBar
import com.cuadra.caja.ui.theme.CuadraColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Equipo → Teléfonos (dueño y admin, en línea): lista y revocar. Cada persona que entra con el código del negocio aparece aquí. */
@Composable
fun DevicesScreen(vm: DevicesViewModel, onTab: (TeamTab) -> Unit, onBack: () -> Unit) {
    LaunchedEffect(Unit) { vm.enter() }
    val ui by vm.ui.collectAsState()
    val business by vm.business.collectAsState()
    DevicesContent(ui, business?.timezone.orEmpty(), vm, onTab, onBack)
}

/** Teléfonos sin ViewModel (estado + acciones): es lo que dibuja la guardia de diseño. */
@Composable
fun DevicesContent(ui: DevicesUi, timezone: String, actions: DevicesActions, onTab: (TeamTab) -> Unit, onBack: () -> Unit) {
    val zone = runCatching { ZoneId.of(timezone) }.getOrDefault(ZoneId.systemDefault())
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val time = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withLocale(locale).withZone(zone)
    ScreenFrame(
        Modifier.fillMaxSize().imePadding().padding(horizontal = 16.dp),
        header = {
            TitleBar(Modifier.padding(top = 16.dp), end = { CuadraButton(stringResource(R.string.back), onBack, height = 48) }) {
                Text(stringResource(R.string.team_title), style = MaterialTheme.typography.headlineMedium)
            }
            TeamTabs(TeamTab.DEVICES, onTab)
        },
    ) {
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { Text(stringResource(R.string.dev_hint), style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted) }
            ui.notice?.let { n -> item { NoticeLine(n.asString(), false, actions::dismissNotice) } }
            when {
                ui.loading && ui.devices.isEmpty() -> item { Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) { Text(stringResource(R.string.sched_loading), color = CuadraColors.Muted) } }
                ui.loadError != null -> item {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(ui.loadError.asString(), color = CuadraColors.Orange, fontWeight = FontWeight.Bold)
                        CuadraButton(stringResource(R.string.sched_retry), actions::load, Modifier.fillMaxWidth(), kind = ButtonKind.PRIMARY)
                    }
                }
                ui.devices.isEmpty() -> item { Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) { Text(stringResource(R.string.dev_empty), color = CuadraColors.Muted, textAlign = TextAlign.Center) } }
                else -> items(ui.devices, key = { it.id }) { DeviceCard(it, it.id == ui.thisDeviceId, time, actions) }
            }
        }
    }
    ui.revoking?.let { d ->
        Sheet(actions::cancelRevoke, actions = {
            ButtonRow {
                CuadraButton(stringResource(R.string.cancel), actions::cancelRevoke, Modifier.share(1f))
                CuadraButton(stringResource(R.string.dev_revoke), actions::confirmRevoke, Modifier.share(1.3f), kind = ButtonKind.DANGER)
            }
        }) {
            Text(stringResource(R.string.dev_revoke_title), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(R.string.dev_revoke_body, d.name), style = MaterialTheme.typography.bodyLarge)
            if (d.pendingOps > 0) Text(pluralStringResource(R.plurals.dev_revoke_pending, d.pendingOps, d.pendingOps), color = CuadraColors.Orange, fontWeight = FontWeight.Bold)
            ErrorText(ui.revokeError)
        }
    }
}

private fun instantText(iso: String?, fmt: DateTimeFormatter): String? = iso?.let { runCatching { fmt.format(Instant.parse(it)) }.getOrDefault(it) }

@Composable
private fun DeviceCard(d: DeviceDto, isThis: Boolean, time: DateTimeFormatter, actions: DevicesActions) {
    CuadraCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(d.name, fontWeight = FontWeight.ExtraBold, maxLines = 3, ellipsize = true)
            val model = listOfNotNull(d.model, d.appVersion?.let { stringResource(R.string.dev_version, it) }).joinToString(" · ")
            if (model.isNotEmpty()) Text(model, style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted)
            Text(stringResource(R.string.dev_register, d.cashRegisterName ?: "—"), style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Ink2)
            Text(stringResource(R.string.dev_last_sync, instantText(d.lastSyncAt, time) ?: stringResource(R.string.dev_never)), style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Ink2)
            TagRow {
                if (isThis) Tag(stringResource(R.string.dev_this), CuadraColors.Green, CuadraColors.GreenSoft)
                if (d.revoked) Tag(stringResource(R.string.dev_revoked_tag), CuadraColors.Red, CuadraColors.RedSoft)
                if (d.pendingOps > 0) Tag(pluralStringResource(R.plurals.dev_pending, d.pendingOps, d.pendingOps), CuadraColors.Orange, CuadraColors.OrangeSoft)
            }
            // Revocar el teléfono en uso lo dejaría sin acceso: no se ofrece desde aquí.
            if (!d.revoked && !isThis) LinkAction(stringResource(R.string.dev_revoke), { actions.askRevoke(d) }, color = CuadraColors.Red)
        }
    }
}

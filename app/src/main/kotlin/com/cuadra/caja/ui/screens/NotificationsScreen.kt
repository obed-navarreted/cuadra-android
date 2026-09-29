package com.cuadra.caja.ui.screens

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import com.cuadra.caja.R
import com.cuadra.caja.data.local.NotificationEntity
import com.cuadra.caja.ui.NOTIFICATION_TYPES
import com.cuadra.caja.ui.NotificationsViewModel
import com.cuadra.caja.ui.common.ButtonKind
import com.cuadra.caja.ui.common.CuadraButton
import com.cuadra.caja.ui.common.CuadraCard
import com.cuadra.caja.ui.common.LocalMoney
import com.cuadra.caja.ui.common.NotificationTexts
import com.cuadra.caja.ui.theme.CuadraColors

@Composable
private fun typeLabel(type: String): String = when (type) {
    "LOW_STOCK" -> stringResource(R.string.notif_type_LOW_STOCK)
    "OUT_OF_STOCK" -> stringResource(R.string.notif_type_OUT_OF_STOCK)
    "SHIFT_CLOSED" -> stringResource(R.string.notif_type_SHIFT_CLOSED)
    "SHIFT_DIFFERENCE" -> stringResource(R.string.notif_type_SHIFT_DIFFERENCE)
    "SHIFT_NOT_CLOSED" -> stringResource(R.string.notif_type_SHIFT_NOT_CLOSED)
    "SALE_DELETED" -> stringResource(R.string.notif_type_SALE_DELETED)
    "DEVICE_STALE" -> stringResource(R.string.notif_type_DEVICE_STALE)
    "PIN_LOCKOUT" -> stringResource(R.string.notif_type_PIN_LOCKOUT)
    "MEMBER_JOINED" -> stringResource(R.string.notif_type_MEMBER_JOINED)
    "DAILY_SUMMARY" -> stringResource(R.string.notif_type_DAILY_SUMMARY)
    else -> stringResource(R.string.notif_type_SCHEDULED)
}

/** Bandeja: todo aviso queda aquí aunque el teléfono no tenga permiso o el aviso llegara de noche. Tocar uno lo marca leído y abre lo que corresponde. */
@Composable
fun NotificationsScreen(vm: NotificationsViewModel, canSchedule: Boolean, onSchedules: () -> Unit, onRoute: (String) -> Unit, onBack: () -> Unit) {
    val items by vm.items.collectAsState()
    val unread by vm.unread.collectAsState()
    val prefs by vm.prefs.collectAsState()
    val context = LocalContext.current
    // El permiso se pide aquí, en contexto (Android 13+), y no al abrir la app.
    var allowed by remember { mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled()) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed = NotificationManagerCompat.from(context).areNotificationsEnabled() }
    val fmt = LocalMoney.current

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.notif_title), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            CuadraButton(stringResource(R.string.back), onBack, height = 44)
        }
        if (!allowed) CuadraCard {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.notif_permission_title), fontWeight = FontWeight.ExtraBold)
                Text(stringResource(R.string.notif_permission_hint), style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted)
                if (Build.VERSION.SDK_INT >= 33) CuadraButton(stringResource(R.string.notif_permission_allow), { ask.launch(Manifest.permission.POST_NOTIFICATIONS) }, Modifier.fillMaxWidth(), kind = ButtonKind.PRIMARY, height = 48)
                CuadraButton(
                    stringResource(R.string.notif_permission_settings),
                    { context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) },
                    Modifier.fillMaxWidth(), height = 48,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (unread > 0) CuadraButton(stringResource(R.string.notif_mark_all), vm::markAllRead, Modifier.weight(1f), height = 48)
            CuadraButton(stringResource(R.string.notif_preferences), vm::openPrefs, Modifier.weight(1f), height = 48)
        }
        if (canSchedule) CuadraButton(stringResource(R.string.notif_schedules_open), onSchedules, Modifier.fillMaxWidth(), height = 48)
        if (items.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(stringResource(R.string.notif_empty), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(items, key = { it.id }) { n -> Row(n, fmt::format) { vm.open(n)?.let(onRoute) } }
        }
    }
    if (prefs.open) PrefsDialog(vm)
}

@Composable
private fun Row(n: NotificationEntity, money: (Long) -> String, onClick: () -> Unit) {
    val (title, body) = NotificationTexts.render(LocalContext.current.resources, n, money)
    val unread = n.readAt == null
    CuadraCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.padding(top = 6.dp).size(10.dp).background(if (unread) CuadraColors.Green else CuadraColors.Line, CircleShape))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, fontWeight = if (unread) FontWeight.ExtraBold else FontWeight.Bold)
                Text(body, style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Ink2)
                Text(DateUtils.getRelativeTimeSpanString(n.createdAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString(), style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted)
            }
        }
    }
}

@Composable
private fun Row(verticalAlignment: Alignment.Vertical, horizontalArrangement: Arrangement.Horizontal, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) =
    androidx.compose.foundation.layout.Row(verticalAlignment = verticalAlignment, horizontalArrangement = horizontalArrangement, content = content)

@Composable
private fun PrefsDialog(vm: NotificationsViewModel) {
    val ui by vm.prefs.collectAsState()
    Sheet(vm::closePrefs) {
        Text(stringResource(R.string.notif_preferences), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.notif_preferences_hint), style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted)
        if (ui.offline) Text(stringResource(R.string.notif_preferences_offline), color = CuadraColors.Orange, fontWeight = FontWeight.Bold)
        ui.prefs?.let { p ->
            NOTIFICATION_TYPES.forEach { type ->
                androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(typeLabel(type), Modifier.weight(1f))
                    Switch(p[type] != false, { vm.toggle(type, it) })
                }
            }
        }
        CuadraButton(stringResource(R.string.close), vm::closePrefs, Modifier.fillMaxWidth(), kind = ButtonKind.DARK)
    }
}

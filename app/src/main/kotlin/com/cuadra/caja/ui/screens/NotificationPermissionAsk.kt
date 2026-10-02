package com.cuadra.caja.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.core.app.NotificationManagerCompat
import com.cuadra.caja.AppContainer
import com.cuadra.caja.R
import com.cuadra.caja.domain.PushPolicy
import com.cuadra.caja.ui.common.ButtonKind
import com.cuadra.caja.ui.common.ButtonRow
import com.cuadra.caja.ui.common.CuadraButton
import com.cuadra.caja.ui.common.Sheet
import com.cuadra.caja.ui.common.Text

/**
 * Android 13+: el permiso de notificaciones se pide UNA sola vez, con su motivo, y nunca en el primer arranque (`PushPolicy.askNotificationPermission`).
 * «Ahora no» no vuelve a preguntar; queda «Permitir» en la bandeja (Más › Notificaciones).
 */
@Composable
fun NotificationPermissionAsk(container: AppContainer, hasMember: Boolean) {
    val context = LocalContext.current
    var show by remember {
        mutableStateOf(PushPolicy.askNotificationPermission(Build.VERSION.SDK_INT, NotificationManagerCompat.from(context).areNotificationsEnabled(),
            container.pushPrefs.permissionAsked, container.pushPrefs.launches, hasMember))
    }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { show = false }
    if (!show || !hasMember) return
    NotificationPermissionContent(
        onAllow = { container.pushPrefs.permissionAsked = true; if (Build.VERSION.SDK_INT >= 33) ask.launch(Manifest.permission.POST_NOTIFICATIONS) else show = false },
        onLater = { container.pushPrefs.permissionAsked = true; show = false },
    )
}

/** La hoja con la explicación (lo que dibuja la guardia de diseño). */
@Composable
fun NotificationPermissionContent(onAllow: () -> Unit, onLater: () -> Unit) {
    Sheet(onLater, actions = {
        ButtonRow {
            CuadraButton(stringResource(R.string.notif_perm_later), onLater, Modifier.share(1f))
            CuadraButton(stringResource(R.string.notif_perm_allow), onAllow, Modifier.share(1.3f), kind = ButtonKind.PRIMARY)
        }
    }) {
        Text(stringResource(R.string.notif_perm_title), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.notif_perm_body), Modifier.fillMaxWidth(), fontWeight = FontWeight.Normal)
    }
}

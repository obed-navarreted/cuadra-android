package com.cuadra.caja.ui.screens

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cuadra.caja.AppContainer
import com.cuadra.caja.BuildConfig
import com.cuadra.caja.R
import com.cuadra.caja.domain.AppUpdate
import com.cuadra.caja.domain.UpdateNeed
import com.cuadra.caja.ui.common.ButtonKind
import com.cuadra.caja.ui.common.CuadraButton
import com.cuadra.caja.ui.theme.CuadraColors
import kotlinx.coroutines.launch

/** Abre la ficha de la app en Play Store (market://) y, si no hay Play Store, en el navegador. */
fun openStoreListing(context: Context) {
    val id = context.packageName
    val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$id")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(market)
    } catch (_: ActivityNotFoundException) {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$id")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}

/** Abre la página de donación en el navegador. Sin red o sin navegador avisa con un mensaje amable; no rastrea nada. */
fun openDonationPage(context: Context, url: String) {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
    val online = cm?.getNetworkCapabilities(cm.activeNetwork)?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    val opened = online && runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.isSuccess
    if (!opened) android.widget.Toast.makeText(context, context.getString(if (online) R.string.support_open_failed else R.string.support_offline), android.widget.Toast.LENGTH_LONG).show()
}

/** Pantalla completa: la versión instalada ya no es compatible. Deja claro que los datos están a salvo. */
@Composable
fun UpdateRequiredScreen() {
    val context = LocalContext.current
    Column(
        Modifier.fillMaxSize().background(CuadraColors.Bg).statusBarsPadding().navigationBarsPadding().padding(24.dp),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.update_required_title), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, color = CuadraColors.Ink)
        Text(stringResource(R.string.update_required_body), Modifier.padding(top = 12.dp), style = MaterialTheme.typography.bodyLarge, color = CuadraColors.Ink2)
        Text(stringResource(R.string.update_required_safe), Modifier.padding(top = 12.dp, bottom = 24.dp), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = CuadraColors.Green)
        CuadraButton(stringResource(R.string.update_required_button), { openStoreListing(context) }, Modifier.fillMaxWidth(), kind = ButtonKind.PRIMARY)
    }
}

/** Franjas delgadas y descartables arriba de la pantalla principal: versión recomendada y anuncio de la plataforma. */
@Composable
fun NoticeBanners(container: AppContainer) {
    val cfg by container.appConfig.state.collectAsState(initial = null)
    val c = cfg ?: return
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val need = AppUpdate.need(BuildConfig.VERSION_NAME, c.minAppVersion, c.recommendedAppVersion, c.dismissedRecommended)
    if (need is UpdateNeed.Recommended) {
        Banner(
            stringResource(R.string.update_recommended_text, need.version), CuadraColors.OrangeSoft, CuadraColors.Orange,
            onClick = { openStoreListing(context) }, onDismiss = { scope.launch { container.appConfig.dismissRecommended(need.version) } },
        )
    }
    val a = c.announcement
    if (a != null && AppUpdate.announcementVisible(a.id, c.dismissedAnnouncementId)) {
        val text = listOf(a.title, a.body).filter { it.isNotBlank() }.joinToString(" - ")
        Banner(
            text, CuadraColors.GreenSoft, CuadraColors.Green,
            onClick = {
                if (AppUpdate.isAppLink(a.deepLink)) container.pendingRoute.value = a.deepLink!!.trim()
                scope.launch { container.appConfig.dismissAnnouncement(a.id) }
            },
            onDismiss = { scope.launch { container.appConfig.dismissAnnouncement(a.id) } },
        )
    }
}

@Composable
private fun Banner(text: String, bg: androidx.compose.ui.graphics.Color, fg: androidx.compose.ui.graphics.Color, onClick: () -> Unit, onDismiss: () -> Unit) {
    Row(Modifier.fillMaxWidth().background(bg).padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, Modifier.weight(1f).clickable(onClick = onClick).padding(vertical = 10.dp), color = fg, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
        val dismissLabel = stringResource(R.string.notice_dismiss)
        Text("X", Modifier.clickable(onClick = onDismiss).semantics { contentDescription = dismissLabel }.padding(horizontal = 16.dp, vertical = 10.dp), color = fg, fontWeight = FontWeight.ExtraBold)
    }
}

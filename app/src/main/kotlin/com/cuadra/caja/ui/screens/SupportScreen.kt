package com.cuadra.caja.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.cuadra.caja.R
import com.cuadra.caja.domain.SupportContact
import com.cuadra.caja.ui.common.ButtonKind
import com.cuadra.caja.ui.common.ButtonRow
import com.cuadra.caja.ui.common.CuadraButton
import com.cuadra.caja.ui.common.CuadraCard
import com.cuadra.caja.ui.common.ScreenFrame
import com.cuadra.caja.ui.common.Text
import com.cuadra.caja.ui.common.TitleBar
import com.cuadra.caja.ui.common.openInBrowser
import com.cuadra.caja.ui.common.rememberTextSharer
import com.cuadra.caja.ui.theme.CuadraColors

/** Acciones de «Apóyame» (el guardián las prueba con un objeto vacío). */
interface SupportActions {
    fun whatsApp() {}
    fun copyEmail() {}
    fun writeEmail() {}
}

/** Más › Apóyame: un texto amable, «Escribir por WhatsApp» y el correo con «Copiar correo». En la versión de Play no menciona pagos ni donaciones. */
@Composable
fun SupportScreen(number: String?, email: String?, play: Boolean, onBack: () -> Unit) {
    val context = LocalContext.current
    val sharer = rememberTextSharer()
    val mail = SupportContact.email(email)
    val message = stringResource(R.string.support_whatsapp_message)
    val label = stringResource(R.string.support_title)
    val actions = object : SupportActions {
        override fun whatsApp() { openInBrowser(context, SupportContact.whatsappUrl(number, message)) }
        override fun copyEmail() = sharer.copy(label, mail)
        override fun writeEmail() {
            runCatching {
                context.startActivity(android.content.Intent(android.content.Intent.ACTION_SENDTO, android.net.Uri.parse("mailto:$mail")).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
    }
    SupportContent(mail, play, actions, onBack)
}

@Composable
fun SupportContent(email: String, play: Boolean, actions: SupportActions, onBack: () -> Unit) {
    var copied by rememberSaveable { mutableStateOf(false) }
    ScreenFrame(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 16.dp), spacing = 12.dp, header = {
        TitleBar(end = { CuadraButton(stringResource(R.string.back), onBack, height = 48) }) {
            Text(stringResource(R.string.support_title), style = MaterialTheme.typography.headlineMedium)
        }
    }) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CuadraCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(if (play) R.string.support_body_play else R.string.support_body), style = MaterialTheme.typography.bodyLarge)
                }
            }
            CuadraButton(stringResource(R.string.support_whatsapp), actions::whatsApp, Modifier.fillMaxWidth(), kind = ButtonKind.WHATSAPP)
            CuadraCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.support_email_label), style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted)
                    Text(email, style = MaterialTheme.typography.titleMedium)
                    ButtonRow {
                        CuadraButton(stringResource(if (copied) R.string.support_copied else R.string.support_copy_email), { actions.copyEmail(); copied = true }, Modifier.share(1f))
                        CuadraButton(stringResource(R.string.support_write_email), actions::writeEmail, Modifier.share(1f))
                    }
                }
            }
        }
    }
}

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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.cuadra.caja.R
import com.cuadra.caja.core.i18n.AppLanguage
import com.cuadra.caja.data.sync.SyncStatus
import com.cuadra.caja.ui.common.CuadraButton
import com.cuadra.caja.ui.common.CuadraCard
import com.cuadra.caja.ui.common.LinkAction
import com.cuadra.caja.ui.common.MenuCard
import com.cuadra.caja.ui.common.MenuDivider
import com.cuadra.caja.ui.common.MenuGroup
import com.cuadra.caja.ui.common.MenuRow
import com.cuadra.caja.ui.common.SwitchRow
import com.cuadra.caja.ui.common.MenuItem
import com.cuadra.caja.ui.common.Text
import com.cuadra.caja.ui.theme.CuadraColors

/**
 * Lo que «Más» puede abrir. Cada entrada es `null` cuando esa persona (por su rol) o ese negocio (por sus módulos) no la tiene: la pantalla solo dibuja las que existen,
 * agrupadas por tema.
 */
data class MoreActions(
    val onLanguageChange: (AppLanguage) -> Unit = {},
    val onSyncNow: () -> Unit = {},
    val onLock: () -> Unit = {},
    val onSignOut: () -> Unit = {},
    val onSettings: (() -> Unit)? = null,
    val onTeam: (() -> Unit)? = null,
    val onActivity: (() -> Unit)? = null,
    val onTemplates: (() -> Unit)? = null,
    val onProducts: (() -> Unit)? = null,
    val onInventory: (() -> Unit)? = null,
    val onPurchases: (() -> Unit)? = null,
    val onSummary: (() -> Unit)? = null,
    val onDailyClose: (() -> Unit)? = null,
    val onNotifications: (() -> Unit)? = null,
    val onReader: (() -> Unit)? = null,
    val onPrinter: (() -> Unit)? = null,
    val onTextSize: (() -> Unit)? = null,
    val onHelp: (() -> Unit)? = null,
    /** «Apóyame»: contacto por WhatsApp y correo (sin pagos dentro de la app). */
    val onSupport: (() -> Unit)? = null,
    val onMyAccount: (() -> Unit)? = null,
    /** Solo si quien entró con Google administra la PLATAFORMA: abre la consola del panel web en el navegador (no hay consola en la app). */
    val onPlatformConsole: (() -> Unit)? = null,
    /** «Requiere atención»: lo rechazado por el servidor (se abre tocando el estado en rojo). */
    val onAttention: (() -> Unit)? = null,
    /** Preferencia de este teléfono: ofrecer enviar el comprobante por WhatsApp al terminar la venta. */
    val onOfferWhatsApp: (Boolean) -> Unit = {},
    /** Preferencia de este teléfono: «Pedir descripción al agregar» (la calculadora muestra el campo). */
    val onAskDescription: (Boolean) -> Unit = {},
)

@Composable
fun MoreScreen(
    businessName: String, memberName: String, status: SyncStatus, pending: Int, failed: Int, language: AppLanguage, unreadNotifications: Int, actions: MoreActions,
    /** Solo la guardia de diseño: dibuja el grupo «Avanzado» ya abierto. */
    advancedOpen: Boolean = false,
    /** Preferencia de este teléfono (apagada por omisión). */
    offerWhatsApp: Boolean = false,
    /** Preferencia de este teléfono (apagada por omisión). */
    askDescription: Boolean = false,
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(stringResource(R.string.more_title), style = MaterialTheme.typography.headlineMedium)
        CuadraCard {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(businessName, style = MaterialTheme.typography.headlineMedium)
                Text(stringResource(R.string.more_signed_in_as, memberName), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        CuadraCard {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val (text, color) = when {
                    status == SyncStatus.SYNCING -> stringResource(R.string.sync_syncing) to CuadraColors.Ink
                    status == SyncStatus.OFFLINE -> stringResource(R.string.sync_offline) to CuadraColors.Orange
                    status == SyncStatus.SUSPENDED -> stringResource(R.string.sync_suspended) to CuadraColors.Red
                    status == SyncStatus.NEEDS_ATTENTION || failed > 0 -> stringResource(R.string.sync_attention) to CuadraColors.Red
                    pending > 0 -> pluralStringResource(R.plurals.sync_pending_count, pending, pending) to CuadraColors.Orange
                    else -> stringResource(R.string.sync_ok) to CuadraColors.Green
                }
                // El aviso en rojo lleva a «Requiere atención»: ahí se ve qué fue, por qué, y se reintenta o descarta.
                if (failed > 0 && actions.onAttention != null) {
                    LinkAction(text, actions.onAttention, color = color, style = MaterialTheme.typography.bodyLarge)
                    LinkAction(pluralStringResource(R.plurals.more_attention_open, failed, failed), actions.onAttention, color = CuadraColors.Red)
                } else {
                    Text(text, color = color, style = MaterialTheme.typography.bodyLarge)
                }
                if (pending > 0 && status != SyncStatus.SYNCING) Text(pluralStringResource(R.plurals.sync_pending_count, pending, pending), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                CuadraButton(stringResource(R.string.more_sync_now), actions.onSyncNow, Modifier.fillMaxWidth(), enabled = status != SyncStatus.SYNCING)
            }
        }
        // Negocio: ajustes (dueño edita, admin ve), equipo, actividad (dueño y admin, solo lectura) y mensajes de WhatsApp (dueño y admin).
        MenuGroup(
            stringResource(R.string.more_group_business),
            listOfNotNull(
                actions.onSettings?.let { MenuItem(R.drawable.ic_menu_settings, stringResource(R.string.set_title), it) },
                actions.onTeam?.let { MenuItem(R.drawable.ic_menu_team, stringResource(R.string.team_title), it) },
                actions.onActivity?.let { MenuItem(R.drawable.ic_menu_activity, stringResource(R.string.act_title), it) },
                actions.onTemplates?.let { MenuItem(R.drawable.ic_menu_messages, stringResource(R.string.tpl_title), it) },
            ),
        )
        MenuGroup(
            stringResource(R.string.more_group_catalog),
            listOfNotNull(
                actions.onProducts?.let { MenuItem(R.drawable.ic_menu_products, stringResource(R.string.more_products), it) },
                actions.onInventory?.let { MenuItem(R.drawable.ic_menu_inventory, stringResource(R.string.more_inventory), it) },
                actions.onPurchases?.let { MenuItem(R.drawable.ic_menu_purchases, stringResource(R.string.more_purchases), it) },
            ),
        )
        // Cierre automático por jornada (dueño y admin): no hay que abrir ni cerrar nada.
        MenuGroup(
            stringResource(R.string.more_group_reports),
            listOfNotNull(
                actions.onSummary?.let { MenuItem(R.drawable.ic_menu_summary, stringResource(R.string.more_summary), it) },
                actions.onDailyClose?.let { MenuItem(R.drawable.ic_menu_close, stringResource(R.string.more_daily_close), it) },
            ),
        )
        MenuGroup(
            stringResource(R.string.more_group_tools),
            listOfNotNull(
                actions.onNotifications?.let { MenuItem(R.drawable.ic_menu_bell, stringResource(R.string.notif_title), it, badge = if (unreadNotifications > 0) pluralStringResource(R.plurals.more_unread_badge, unreadNotifications, unreadNotifications) else null) },
            ),
        )
        // Preferencias de ESTE teléfono: tamaño de letra, WhatsApp al terminar la venta y, cerrado por omisión, «Avanzado» (impresora, lector de códigos).
        PhonePreferences(actions, advancedOpen, offerWhatsApp, askDescription)
        MenuGroup(
            stringResource(R.string.more_group_help),
            listOfNotNull(
                actions.onHelp?.let { MenuItem(R.drawable.ic_menu_help, stringResource(R.string.help_title), it) },
                actions.onSupport?.let { MenuItem(R.drawable.ic_menu_heart, stringResource(R.string.support_title), it) },
            ),
        )
        MenuGroup(
            stringResource(R.string.more_group_account),
            listOfNotNull(
                actions.onMyAccount?.let { MenuItem(R.drawable.ic_menu_account, stringResource(R.string.account_title), it) },
                actions.onPlatformConsole?.let { MenuItem(R.drawable.ic_menu_settings, stringResource(R.string.more_platform_console), it, detail = stringResource(R.string.more_platform_console_help)) },
                MenuItem(R.drawable.ic_menu_lock, stringResource(R.string.register_lock), actions.onLock),
                MenuItem(R.drawable.ic_menu_logout, stringResource(R.string.more_sign_out), actions.onSignOut, detail = stringResource(R.string.more_sign_out_warning)),
            ),
        )
        LanguageChips(language, actions.onLanguageChange)
    }
}

/**
 * «Preferencias de este teléfono»: lo que cada teléfono decide por sí mismo (no viaja con el negocio ni con la persona). Tamaño de letra, el interruptor de
 * WhatsApp al terminar la venta, «Pedir descripción al agregar» y «Avanzado» (impresora térmica y lector de códigos, cerrado por omisión con estilo secundario: quien no las usa no las ve).
 */
@Composable
private fun PhonePreferences(actions: MoreActions, advancedOpen: Boolean, offerWhatsApp: Boolean, askDescription: Boolean) {
    var open by rememberSaveable { mutableStateOf(advancedOpen) }
    val optional = stringResource(R.string.more_optional)
    MenuCard(stringResource(R.string.more_group_phone)) {
        actions.onTextSize?.let { MenuRow(MenuItem(R.drawable.ic_menu_textsize, stringResource(R.string.more_text_size), it)); MenuDivider() }
        SwitchRow(
            stringResource(R.string.more_offer_whatsapp), offerWhatsApp, actions.onOfferWhatsApp, Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            help = stringResource(R.string.more_offer_whatsapp_help),
        )
        MenuDivider()
        SwitchRow(
            stringResource(R.string.more_ask_description), askDescription, actions.onAskDescription, Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            help = stringResource(R.string.more_ask_description_help),
        )
        if (actions.onPrinter != null || actions.onReader != null) {
            MenuDivider()
            if (!open) {
                MenuRow(MenuItem(R.drawable.ic_menu_settings, stringResource(R.string.more_group_advanced), { open = true }, detail = stringResource(R.string.more_advanced_hint)))
            } else {
                actions.onPrinter?.let { MenuRow(MenuItem(R.drawable.ic_printer, stringResource(R.string.printer_title), it, badge = optional)); MenuDivider() }
                actions.onReader?.let { MenuRow(MenuItem(R.drawable.ic_reader, stringResource(R.string.reader_title), it, badge = optional)); MenuDivider() }
                LinkAction(stringResource(R.string.more_advanced_hide), { open = false }, Modifier.padding(horizontal = 8.dp))
            }
        }
    }
}

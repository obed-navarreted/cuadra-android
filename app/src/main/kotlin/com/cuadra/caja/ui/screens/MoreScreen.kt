package com.cuadra.caja.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.cuadra.caja.R
import com.cuadra.caja.core.i18n.AppLanguage
import com.cuadra.caja.data.sync.SyncStatus
import com.cuadra.caja.ui.common.ButtonKind
import com.cuadra.caja.ui.common.CuadraButton
import com.cuadra.caja.ui.common.CuadraCard
import com.cuadra.caja.ui.theme.CuadraColors

@Composable
fun MoreScreen(
    businessName: String, memberName: String, status: SyncStatus, pending: Int, failed: Int, language: AppLanguage,
    onLanguageChange: (AppLanguage) -> Unit, onSyncNow: () -> Unit, onLock: () -> Unit, onSignOut: () -> Unit,
    modules: Map<String, Boolean>? = null, onModule: (String, Boolean) -> Unit = { _, _ -> },
    onInventory: (() -> Unit)? = null, onPurchases: (() -> Unit)? = null, onProducts: (() -> Unit)? = null,
    onNotifications: (() -> Unit)? = null, unreadNotifications: Int = 0, onSummary: (() -> Unit)? = null,
    onSupport: (() -> Unit)? = null,
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
                Text(text, color = color, style = MaterialTheme.typography.bodyLarge)
                if (pending > 0 && status != SyncStatus.SYNCING) Text(pluralStringResource(R.plurals.sync_pending_count, pending, pending), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                CuadraButton(stringResource(R.string.more_sync_now), onSyncNow, Modifier.fillMaxWidth(), enabled = status != SyncStatus.SYNCING)
            }
        }
        // Inventario y compras: solo quien administra, y solo si el negocio activó el módulo.
        onSummary?.let { CuadraButton(stringResource(R.string.more_summary), it, Modifier.fillMaxWidth()) }
        onNotifications?.let { CuadraButton(if (unreadNotifications > 0) stringResource(R.string.notif_unread_count, unreadNotifications) else stringResource(R.string.notif_title), it, Modifier.fillMaxWidth(), kind = if (unreadNotifications > 0) ButtonKind.PRIMARY else ButtonKind.OUTLINE) }
        onProducts?.let { CuadraButton(stringResource(R.string.more_products), it, Modifier.fillMaxWidth()) }
        onInventory?.let { CuadraButton(stringResource(R.string.more_inventory), it, Modifier.fillMaxWidth()) }
        onPurchases?.let { CuadraButton(stringResource(R.string.more_purchases), it, Modifier.fillMaxWidth()) }
        // Donación voluntaria: solo aparece si `DonationOffer` lo permite (config del servidor, https y build que no sea Play).
        onSupport?.let {
            CuadraButton(stringResource(R.string.more_support), it, Modifier.fillMaxWidth(), kind = ButtonKind.OUTLINE)
            Text(stringResource(R.string.more_support_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LanguageChips(language, onLanguageChange)
        // Solo el dueño decide qué módulos usa su negocio: lo que se apaga se esconde, no se borra.
        modules?.let { m ->
            CuadraCard {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.modules_title), fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold)
                    listOf("credit" to R.string.module_credit, "expenses" to R.string.module_expenses, "shifts" to R.string.module_shifts, "catalog" to R.string.module_catalog, "inventory" to R.string.module_inventory).forEach { (key, label) ->
                        val on = if (key == "shifts" || key == "inventory") m[key] == true else m[key] != false
                        androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Text(stringResource(label), Modifier.weight(1f))
                            androidx.compose.material3.Switch(on, { onModule(key, it) })
                        }
                    }
                }
            }
        }
        CuadraButton(stringResource(R.string.register_lock), onLock, Modifier.fillMaxWidth())
        CuadraButton(stringResource(R.string.more_sign_out), onSignOut, Modifier.fillMaxWidth(), kind = ButtonKind.OUTLINE)
        Text(stringResource(R.string.more_sign_out_warning), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

package com.cuadra.caja.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cuadra.caja.R
import com.cuadra.caja.data.local.BusinessEntity
import com.cuadra.caja.domain.ActivityAction
import com.cuadra.caja.domain.ActivityKind
import com.cuadra.caja.domain.ActivityRow
import com.cuadra.caja.ui.ActivityActions
import com.cuadra.caja.ui.ActivityUi
import com.cuadra.caja.ui.ActivityViewModel
import com.cuadra.caja.ui.asString
import com.cuadra.caja.ui.common.ButtonKind
import com.cuadra.caja.ui.common.CuadraButton
import com.cuadra.caja.ui.common.CuadraCard
import com.cuadra.caja.ui.common.CuadraChip
import com.cuadra.caja.ui.common.ChipFlow
import com.cuadra.caja.ui.common.ScreenFrame
import com.cuadra.caja.ui.common.Tag
import com.cuadra.caja.ui.common.TagRow
import com.cuadra.caja.ui.common.Text
import com.cuadra.caja.ui.common.TitleBar
import com.cuadra.caja.ui.common.money
import com.cuadra.caja.ui.theme.CuadraColors
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** «Actividad» (solo el dueño): quién hizo qué en el negocio, con frases claras; lo que hizo la plataforma se destaca con su motivo. */
@Composable
fun ActivityScreen(vm: ActivityViewModel, onBack: () -> Unit) {
    LaunchedEffect(Unit) { vm.enter() }
    val ui by vm.ui.collectAsState()
    val business by vm.business.collectAsState()
    com.cuadra.caja.ui.common.Refreshing(vm.refresher, busy = ui.loading, showOffline = false) { ActivityContent(ui, business?.timezone.orEmpty(), vm, onBack) }
}

/** Actividad sin ViewModel (estado + acciones): es lo que dibuja la guardia de diseño. */
@Composable
fun ActivityContent(ui: ActivityUi, timezone: String, actions: ActivityActions, onBack: () -> Unit) {
    val zone = runCatching { ZoneId.of(timezone) }.getOrDefault(ZoneId.of("UTC"))
    val visible = ui.visible
    ScreenFrame(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        header = {
            TitleBar(Modifier.padding(top = 16.dp), end = { CuadraButton(stringResource(R.string.back), onBack, height = 48) }) {
                Text(stringResource(R.string.act_title), style = MaterialTheme.typography.headlineMedium)
            }
            Text(stringResource(R.string.act_subtitle), style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted)
            ChipFlow {
                ActivityKind.entries.forEach { k -> CuadraChip(stringResource(kindRes(k)), ui.kind == k, { actions.kind(k) }) }
            }
        },
    ) {
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (ui.offline) item { Text(stringResource(R.string.act_offline), color = CuadraColors.Orange, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium) }
            ui.error?.let { e ->
                item {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(e.asString(), color = CuadraColors.Red, fontWeight = FontWeight.Bold)
                        CuadraButton(stringResource(R.string.sched_retry), actions::refresh, Modifier.fillMaxWidth())
                    }
                }
            }
            if (ui.loading && ui.rows.isEmpty()) item { CenterText(stringResource(R.string.set_loading)) }
            else if (visible.isEmpty() && ui.error == null) item { CenterText(stringResource(if (ui.rows.isEmpty()) R.string.act_empty else R.string.act_empty_filter)) }
            items(visible, key = { it.id }) { ActivityCard(it, zone) }
            if (ui.hasMore) item {
                CuadraButton(stringResource(R.string.act_more), actions::more, Modifier.fillMaxWidth().padding(bottom = 8.dp), enabled = !ui.loadingMore)
            }
            if (ui.offline && !ui.loading) item { CuadraButton(stringResource(R.string.sched_retry), actions::refresh, Modifier.fillMaxWidth().padding(bottom = 8.dp)) }
        }
    }
}

@Composable
private fun CenterText(text: String) = Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) { Text(text, color = CuadraColors.Muted, textAlign = TextAlign.Center) }

private fun kindRes(k: ActivityKind) = when (k) {
    ActivityKind.ALL -> R.string.act_kind_all
    ActivityKind.SALES -> R.string.act_kind_sales
    ActivityKind.PRODUCTS -> R.string.act_kind_products
    ActivityKind.TEAM -> R.string.act_kind_team
    ActivityKind.BUSINESS -> R.string.act_kind_business
    ActivityKind.PLATFORM -> R.string.act_kind_platform
}

@Composable
private fun ActivityCard(r: ActivityRow, zone: ZoneId) {
    val platform = r.platform
    CuadraCard(color = if (platform) CuadraColors.OrangeSoft else CuadraColors.Surface, borderColor = if (platform) CuadraColors.Orange else CuadraColors.Line) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (platform) TagRow { Tag(stringResource(R.string.act_by_platform), CuadraColors.Orange, CuadraColors.Surface) }
            Text(sentence(r), fontWeight = FontWeight.ExtraBold, color = if (platform) CuadraColors.Orange else CuadraColors.Ink)
            r.subject?.let { Text("«$it»", style = MaterialTheme.typography.bodyMedium, maxLines = 2, ellipsize = true) }
            r.amountMinor?.let { Text(stringResource(R.string.act_amount, money(it)), style = MaterialTheme.typography.bodyMedium) }
            val changed = r.fields.mapNotNull { fieldRes(it) }.distinct().map { stringResource(it) }
            if (changed.isNotEmpty()) Text(stringResource(R.string.act_changed, changed.joinToString(", ")), style = MaterialTheme.typography.bodyMedium)
            r.reason?.let { Text(stringResource(R.string.act_reason, it), style = MaterialTheme.typography.bodyMedium, fontWeight = if (platform) FontWeight.Bold else FontWeight.Normal) }
            val who = if (platform) "" else " · " + (r.actorName?.let { stringResource(R.string.act_by, it) } ?: stringResource(R.string.act_system))
            Text(whenText(r.at, zone) + who, style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted)
        }
    }
}

@Composable
private fun whenText(iso: String, zone: ZoneId): String {
    val locale = LocalConfiguration.current.locales[0]
    val at = runCatching { Instant.parse(iso).atZone(zone) }.getOrNull() ?: return iso
    val time = com.cuadra.caja.domain.ClockFormat.time(locale).format(at)
    val today = LocalDate.now(zone)
    return when (at.toLocalDate()) {
        today -> stringResource(R.string.act_today_at, time)
        today.minusDays(1) -> stringResource(R.string.act_yesterday_at, time)
        else -> stringResource(R.string.act_date_at, DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).format(at), time)
    }
}

@Composable
private fun sentence(r: ActivityRow): String {
    val res = when (r.action) {
        ActivityAction.SALE_COMPLETE -> R.string.act_SALE_COMPLETE
        ActivityAction.SALE_EDIT -> R.string.act_SALE_EDIT
        ActivityAction.SALE_CANCEL -> R.string.act_SALE_CANCEL
        ActivityAction.CREDIT_CREATE -> R.string.act_CREDIT_CREATE
        ActivityAction.CREDIT_FROM_SALE -> R.string.act_CREDIT_FROM_SALE
        ActivityAction.CREDIT_PAYMENT -> R.string.act_CREDIT_PAYMENT
        ActivityAction.CREDIT_PAYMENT_VOID -> R.string.act_CREDIT_PAYMENT_VOID
        ActivityAction.CREDIT_WRITE_OFF -> R.string.act_CREDIT_WRITE_OFF
        ActivityAction.CUSTOMER_CREATE -> R.string.act_CUSTOMER_CREATE
        ActivityAction.CUSTOMER_UPDATE -> R.string.act_CUSTOMER_UPDATE
        ActivityAction.EXPENSE_CREATE -> R.string.act_EXPENSE_CREATE
        ActivityAction.EXPENSE_VOID -> R.string.act_EXPENSE_VOID
        ActivityAction.PRODUCT_CREATE -> R.string.act_PRODUCT_CREATE
        ActivityAction.PRODUCT_UPDATE -> R.string.act_PRODUCT_UPDATE
        ActivityAction.PRODUCT_DEACTIVATE -> R.string.act_PRODUCT_DEACTIVATE
        ActivityAction.PRODUCT_IMPORT -> R.string.act_PRODUCT_IMPORT
        ActivityAction.PURCHASE_CREATE -> R.string.act_PURCHASE_CREATE
        ActivityAction.PURCHASE_VOID -> R.string.act_PURCHASE_VOID
        ActivityAction.MEMBER_CREATE -> R.string.act_MEMBER_CREATE
        ActivityAction.MEMBER_UPDATE -> R.string.act_MEMBER_UPDATE
        ActivityAction.MEMBER_PIN_RESET -> R.string.act_MEMBER_PIN_RESET
        ActivityAction.INVITATION_CREATE -> R.string.act_INVITATION_CREATE
        ActivityAction.INVITATION_REVOKE -> R.string.act_INVITATION_REVOKE
        ActivityAction.INVITATION_ACCEPT -> R.string.act_INVITATION_ACCEPT
        ActivityAction.DEVICE_CLAIM -> R.string.act_DEVICE_CLAIM
        ActivityAction.DEVICE_SELF_LINK -> R.string.act_DEVICE_SELF_LINK
        ActivityAction.DEVICE_REVOKE -> R.string.act_DEVICE_REVOKE
        ActivityAction.DEVICE_PIN_VERIFIED -> R.string.act_DEVICE_PIN_VERIFIED
        ActivityAction.OWNER_TRANSFER -> R.string.act_OWNER_TRANSFER
        ActivityAction.BUSINESS_CREATE -> R.string.act_BUSINESS_CREATE
        ActivityAction.BUSINESS_UPDATE -> R.string.act_BUSINESS_UPDATE
        ActivityAction.BUSINESS_DELETE_REQUESTED -> R.string.act_BUSINESS_DELETE_REQUESTED
        ActivityAction.PLATFORM_VIEW_AS -> R.string.act_PLATFORM_VIEW_AS
        ActivityAction.PLATFORM_PLAN_CHANGED -> R.string.act_PLATFORM_PLAN_CHANGED
        ActivityAction.PLATFORM_TRIAL_EXTENDED -> R.string.act_PLATFORM_TRIAL_EXTENDED
        ActivityAction.PLATFORM_SUSPENDED -> R.string.act_PLATFORM_SUSPENDED
        ActivityAction.PLATFORM_UNSUSPENDED -> R.string.act_PLATFORM_UNSUSPENDED
        ActivityAction.PLATFORM_FLAG -> R.string.act_PLATFORM_FLAG
        ActivityAction.PLATFORM_DELETION_MARKED -> R.string.act_PLATFORM_DELETION_MARKED
        null -> if (r.platform) R.string.act_platform_generic else null
    }
    return if (res != null) stringResource(res) else stringResource(R.string.act_unknown, r.rawAction)
}

/** Etiqueta de un campo que cambió (producto o negocio); un campo que no se conoce no se muestra (nada de códigos internos). */
private fun fieldRes(key: String): Int? = when (key) {
    "name" -> R.string.act_f_name
    "variant" -> R.string.act_f_variant
    "barcode", "shortCode" -> R.string.act_f_code
    "categoryId" -> R.string.act_f_category
    "unit" -> R.string.act_f_unit
    "pricing" -> R.string.act_f_pricing
    "priceMinor" -> R.string.act_f_price
    "costMinor" -> R.string.act_f_cost
    "isQuick", "quickPosition" -> R.string.act_f_quick
    "color" -> R.string.act_f_color
    "trackStock", "minStockMilli" -> R.string.act_f_stock
    "active" -> R.string.act_f_active
    "type" -> R.string.act_f_type
    "currency" -> R.string.act_f_currency
    "timezone" -> R.string.act_f_timezone
    "day_cutoff" -> R.string.act_f_cutoff
    "modules", "inventory_mode" -> R.string.act_f_modules
    "pos_views" -> R.string.act_f_pos_views
    "credit_requires_customer", "credit_default_due_days", "credit_overdue_days", "credit_limit_enforced" -> R.string.act_f_credit
    "default_locale" -> R.string.act_f_locale
    "shift_required", "shift_note_threshold_minor" -> R.string.act_f_shifts
    else -> null
}

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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.cuadra.caja.R
import com.cuadra.caja.domain.ModuleVisibility
import com.cuadra.caja.data.local.MemberEntity
import com.cuadra.caja.domain.BusinessCalendar
import com.cuadra.caja.domain.SaleDeletion
import com.cuadra.caja.domain.ReasonCheck
import com.cuadra.caja.domain.SaleView
import com.cuadra.caja.ui.HistoryActions
import com.cuadra.caja.ui.HistoryUi
import com.cuadra.caja.ui.HistoryViewModel
import com.cuadra.caja.ui.asString
import com.cuadra.caja.ui.common.ButtonKind
import com.cuadra.caja.ui.common.ButtonRow
import com.cuadra.caja.ui.common.ChipFlow
import com.cuadra.caja.ui.common.CuadraButton
import com.cuadra.caja.ui.common.CuadraCard
import com.cuadra.caja.ui.common.CuadraChip
import com.cuadra.caja.ui.common.LinkAction
import com.cuadra.caja.ui.common.MoneyText
import com.cuadra.caja.ui.common.RangePicker
import com.cuadra.caja.ui.common.SectionLabel
import com.cuadra.caja.ui.common.Sheet
import com.cuadra.caja.ui.common.SplitRow
import com.cuadra.caja.ui.common.Tag
import com.cuadra.caja.ui.common.TagRow
import com.cuadra.caja.domain.printing.PrintNotice
import com.cuadra.caja.domain.printing.PrinterBadge
import com.cuadra.caja.ui.common.Text
import com.cuadra.caja.ui.common.VoiceTextField
import com.cuadra.caja.ui.common.money
import com.cuadra.caja.ui.theme.CuadraColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

private val METHODS = listOf("CASH", "CARD", "TRANSFER", "CREDIT", "OTHER")

@Composable
internal fun methodName(method: String, other: String? = null): String = when (method) {
    "CASH" -> stringResource(R.string.method_CASH)
    "TRANSFER" -> stringResource(R.string.method_TRANSFER)
    "CARD" -> stringResource(R.string.method_CARD)
    "CREDIT" -> stringResource(R.string.method_CREDIT)
    else -> other?.takeIf { it.isNotBlank() } ?: stringResource(R.string.method_OTHER)
}

/**
 * Ventas. Cajero: las suyas (guardadas en el teléfono). Dueño y admin: la lista completa del servidor con rango, estado, método y persona, y los totales
 * del periodo; sin conexión, las de este teléfono. Tocar una venta abre su detalle; solo dueño y admin pueden eliminarla (con motivo).
 */
@Composable
fun HistoryScreen(vm: HistoryViewModel) {
    val ui by vm.ui.collectAsState()
    val local by vm.local.collectAsState()
    val manager by vm.manager.collectAsState()
    val role by vm.role.collectAsState()
    val calendar by vm.calendar.collectAsState()
    val members by vm.members.collectAsState()
    HistoryContent(ui, local, manager, role, calendar, members, vm)
}

/** Ventas sin ViewModel (estado + acciones): es lo que dibuja la guardia de diseño. */
@Composable
fun HistoryContent(
    ui: HistoryUi, local: List<SaleView>, manager: Boolean, role: String?, calendar: BusinessCalendar?, members: List<MemberEntity>, actions: HistoryActions,
    nowMillis: Long = System.currentTimeMillis(), initialMoreFilters: Boolean = false,
) {
    val zone = calendar?.zone ?: ZoneId.of("UTC")
    // El idioma se lee de la configuración de Compose: al cambiarlo desde la app, las fechas se vuelven a dar formato.
    val locale: Locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val time = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withLocale(locale).withZone(zone)
    val serverList = manager && !ui.offline && ui.error == null
    val rows = if (serverList) ui.sales else local
    var moreFilters by remember { mutableStateOf(initialMoreFilters) }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text(stringResource(R.string.history_title), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 16.dp, bottom = 2.dp)) }
        if (manager) {
            item { RangePicker(ui.range, calendar, actions::setRange, nowMillis = nowMillis) }
            item { Filters(ui, members, moreFilters, { moreFilters = !moreFilters }, actions) }
            if (ui.offline) item { Text(stringResource(R.string.sales_offline_note), color = CuadraColors.Orange, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium) }
            ui.error?.let { e ->
                item {
                    CuadraCard {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(stringResource(R.string.sales_error), fontWeight = FontWeight.Bold, color = CuadraColors.Red)
                            Text(e.asString(), style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted)
                            LinkAction(stringResource(R.string.sales_retry), actions::refresh)
                        }
                    }
                }
            }
            if (serverList) ui.totals?.let { t ->
                item {
                    ButtonRow(spacing = 10.dp) {
                        Totals(stringResource(R.string.summary_sales), money(t.totalMinor), pluralStringResource(R.plurals.summary_sales_hint, t.count.toInt(), t.count.toInt(), money(t.averageTicketMinor)), Modifier.share(1f))
                        Totals(stringResource(R.string.sales_status_cancelled), pluralStringResource(R.plurals.sales_cancelled_count, t.cancelledCount.toInt(), t.cancelledCount.toInt()), null, Modifier.share(1f), plainValue = true)
                    }
                }
            }
        }
        if (ui.loading) item { Text(stringResource(R.string.sales_loading), color = CuadraColors.Muted) }
        if (rows.isEmpty() && !ui.loading) item {
            Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(if (serverList) R.string.sales_empty_filtered else R.string.history_empty), color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
        } else items(rows, key = { it.id }) { SaleRow(it, time, actions) }
        if (serverList && ui.hasMore) item {
            CuadraButton(stringResource(if (ui.loadingMore) R.string.sales_loading else R.string.sales_load_more), actions::loadMore, Modifier.fillMaxWidth(), enabled = !ui.loadingMore)
        }
        item { Box(Modifier.padding(bottom = 8.dp)) {} }
    }
    ui.detail?.let { d ->
        SaleDetailSheet(d, time, SaleDeletion.canDelete(role, d.status), actions, ui.printer, ui.printNotice, canReturn = ui.canReturn, undoable = ui.undoable && !SaleDeletion.canDelete(role, d.status),
            lastReturn = ui.lastReturn, nowMillis = nowMillis)
    }
    ui.returnDraft?.let { ReturnSheet(it, actions) }
    ui.delete?.let { DeleteSaleSheet(it.reason, actions, undo = it.undo, tooLate = it.tooLate) }
}

@Composable
private fun Totals(label: String, value: String, hint: String?, modifier: Modifier, plainValue: Boolean = false) {
    CuadraCard(modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted)
            if (plainValue) Text(value, fontWeight = FontWeight.ExtraBold, color = CuadraColors.Red)
            else MoneyText(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
            hint?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted) }
        }
    }
}

/** Estado (cobradas / eliminadas) siempre a la vista; método de pago y persona en «Más filtros». */
@Composable
private fun Filters(ui: HistoryUi, members: List<MemberEntity>, more: Boolean, onMore: () -> Unit, actions: HistoryActions) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ChipFlow {
            CuadraChip(stringResource(R.string.sales_status_completed), "COMPLETED" in ui.query.statuses, { actions.toggleStatus("COMPLETED") })
            CuadraChip(stringResource(R.string.sales_status_cancelled), "CANCELLED" in ui.query.statuses, { actions.toggleStatus("CANCELLED") })
        }
        LinkAction(stringResource(if (more) R.string.sales_filters_less else R.string.sales_filters_more), onMore)
        if (more) {
            SectionLabel(stringResource(R.string.sales_filter_method))
            ChipFlow {
                CuadraChip(stringResource(R.string.sales_filter_all), ui.query.method == null, { actions.setMethod(null) })
                ModuleVisibility.historyMethods(com.cuadra.caja.ui.common.LocalModules.current, METHODS).forEach { m -> CuadraChip(methodName(m), ui.query.method == m, { actions.setMethod(if (ui.query.method == m) null else m) }) }
            }
            SectionLabel(stringResource(R.string.sales_filter_person))
            ChipFlow {
                CuadraChip(stringResource(R.string.sales_filter_all), ui.query.memberId == null, { actions.setMember(null) })
                members.forEach { m -> CuadraChip(m.displayName, ui.query.memberId == m.id, { actions.setMember(if (ui.query.memberId == m.id) null else m.id) }, userContent = true) }
            }
        }
    }
}

@Composable
private fun SaleRow(s: SaleView, time: DateTimeFormatter, actions: HistoryActions) {
    CuadraCard(Modifier.fillMaxWidth(), onClick = { actions.open(s) }) {
        SplitRow(end = { MoneyText(money(s.totalMinor), fontWeight = FontWeight.ExtraBold, textDecoration = if (s.cancelled) TextDecoration.LineThrough else null) }) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(time.format(Instant.ofEpochMilli(s.atMillis)), fontWeight = FontWeight.ExtraBold)
                s.soldBy?.let { Text(stringResource(R.string.history_by, it), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, ellipsize = true) }
                TagRow {
                    if (s.cancelled) Tag(stringResource(R.string.history_cancelled), CuadraColors.Red, CuadraColors.RedSoft)
                    else Tag(stringResource(R.string.history_completed), CuadraColors.Green, CuadraColors.GreenSoft)
                    if (s.unsynced) Tag(stringResource(R.string.history_unsynced), CuadraColors.Ink, CuadraColors.Soft)
                    ReviewTags(s)
                }
                // Una venta eliminada dice por qué, sin abrirla: el motivo es lo que hace trazable la eliminación.
                if (s.cancelled && !s.cancelReason.isNullOrBlank()) Text(stringResource(R.string.sale_cancel_reason, s.cancelReason), style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Red, maxLines = 2, ellipsize = true)
            }
        }
    }
}

/** Detalle de una venta: líneas, pagos (método, recibido, vuelto), quién vendió y cuándo, quién la editó y, si está eliminada, quién, cuándo y por qué. */
/** Etiquetas para revisar una venta: conflicto, llegó después de la baja, hora corregida y «con devolución». */
@Composable
private fun ReviewTags(s: SaleView) {
    if (s.conflict) Tag(stringResource(R.string.sale_tag_conflict), CuadraColors.Orange, CuadraColors.OrangeSoft)
    when (s.reviewFlag) {
        "LATE_AFTER_DISABLE" -> Tag(stringResource(R.string.sale_tag_late), CuadraColors.Orange, CuadraColors.OrangeSoft)
        "CLOCK_ADJUSTED" -> Tag(stringResource(R.string.sale_tag_clock), CuadraColors.Orange, CuadraColors.OrangeSoft)
    }
    if (s.returnedMinor > 0) Tag(stringResource(R.string.sale_tag_returned), CuadraColors.Ink, CuadraColors.Soft)
}

@Composable
fun SaleDetailSheet(
    s: SaleView, time: DateTimeFormatter, canDelete: Boolean, actions: HistoryActions, printer: PrinterBadge = PrinterBadge.OFF, printNotice: PrintNotice? = null,
    canReturn: Boolean = false, undoable: Boolean = false, lastReturn: com.cuadra.caja.domain.SaleReturnView? = null, nowMillis: Long = System.currentTimeMillis(),
) {
    Sheet(actions::closeDetail, actions = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Resultado de imprimir el recibo, pegado a los botones (siempre a la vista).
        printNotice?.let { n ->
            when (n) {
                PrintNotice.PRINTED -> Text(stringResource(R.string.print_ok), color = CuadraColors.Green, fontWeight = FontWeight.Bold)
                PrintNotice.NO_PRINTER -> Text(stringResource(R.string.print_none), color = CuadraColors.Orange, fontWeight = FontWeight.Bold)
                PrintNotice.FAILED -> Text(stringResource(R.string.print_failed), color = CuadraColors.Red, fontWeight = FontWeight.Bold)
            }
        }
        lastReturn?.let { r -> Text(stringResource(R.string.return_saved, money(r.totalMinor)), color = CuadraColors.Green, fontWeight = FontWeight.Bold) }
        if (canReturn || undoable || canDelete) ButtonRow {
            if (canReturn) CuadraButton(stringResource(R.string.sale_return_action), actions::askReturn, Modifier.share(1.2f), kind = ButtonKind.PRIMARY)
            if (undoable) CuadraButton(stringResource(R.string.sale_undo_action), { actions.askUndo(s.id) }, Modifier.share(1.2f), kind = ButtonKind.DANGER)
            else if (canDelete && s.returnedMinor == 0L) CuadraButton(stringResource(R.string.sale_delete), { actions.askDelete(s.id) }, Modifier.share(1.2f), kind = ButtonKind.DANGER)
        }
        ButtonRow {
            CuadraButton(stringResource(R.string.close), actions::closeDetail, Modifier.share(1f))
            // Solo con la impresora activada. Una venta eliminada también se puede imprimir: sale con el aviso «ANULADA».
            if (printer != PrinterBadge.OFF) {
                if (lastReturn != null) CuadraButton(stringResource(R.string.return_print), actions::printReturn, Modifier.share(1.2f))
                else CuadraButton(stringResource(R.string.print_again), { actions.printSale(s) }, Modifier.share(1.2f))
            }
        }
        }
    }) {
        Text(stringResource(R.string.sale_detail_title), style = MaterialTheme.typography.headlineMedium)
        SplitRow(end = { MoneyText(money(s.totalMinor), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, textDecoration = if (s.cancelled) TextDecoration.LineThrough else null) }) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(time.format(Instant.ofEpochMilli(s.atMillis)), fontWeight = FontWeight.ExtraBold)
                TagRow {
                    if (s.cancelled) Tag(stringResource(R.string.history_cancelled), CuadraColors.Red, CuadraColors.RedSoft)
                    else Tag(stringResource(R.string.history_completed), CuadraColors.Green, CuadraColors.GreenSoft)
                    if (s.unsynced) Tag(stringResource(R.string.history_unsynced), CuadraColors.Ink, CuadraColors.Soft)
                    ReviewTags(s)
                }
            }
        }
        if (undoable) Text(pluralStringResource(R.plurals.undo_minutes_left, com.cuadra.caja.domain.SaleUndo.minutesLeft(s.completedAtMillis, nowMillis), com.cuadra.caja.domain.SaleUndo.minutesLeft(s.completedAtMillis, nowMillis)), style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Orange)
        s.soldBy?.let { Text(stringResource(R.string.sale_sold_by, it), style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted) }
        s.editedBy?.let { who ->
            Text(s.editedAtMillis?.let { stringResource(R.string.sale_edited, who, time.format(Instant.ofEpochMilli(it))) } ?: stringResource(R.string.sale_edited_unknown, who), style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted)
        }
        if (s.cancelled) {
            CuadraCard {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val who = s.cancelledBy
                    if (who != null) {
                        Text(s.cancelledAtMillis?.let { stringResource(R.string.sale_cancelled_by, who, time.format(Instant.ofEpochMilli(it))) } ?: stringResource(R.string.sale_cancelled_unknown, who), fontWeight = FontWeight.Bold, color = CuadraColors.Red)
                    }
                    s.cancelReason?.takeIf { it.isNotBlank() }?.let { Text(stringResource(R.string.sale_cancel_reason, it), style = MaterialTheme.typography.bodyMedium) }
                }
            }
        }
        if (s.items.isNotEmpty()) {
            SectionLabel(stringResource(R.string.sale_lines))
            s.items.forEach { l ->
                SplitRow(end = { MoneyText(money(l.lineTotalMinor), fontWeight = FontWeight.Bold) }) {
                    Column {
                        Text(listOfNotNull(l.name, l.variant?.takeIf { it.isNotBlank() }).joinToString(" · "), fontWeight = FontWeight.Bold, maxLines = 3, ellipsize = true)
                        MoneyText(stringResource(R.string.sale_line_qty, qtyText(l.quantityMilli), money(l.unitPriceMinor)), style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted)
                        if (l.returnedMilli > 0) Text(stringResource(R.string.sale_line_returned, qtyText(l.returnedMilli)), style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Orange)
                    }
                }
            }
            if (s.discountMinor > 0) SplitRow(end = { MoneyText("−" + money(s.discountMinor), color = CuadraColors.Green) }) { Text(stringResource(R.string.sale_discount)) }
            SplitRow(end = { MoneyText(money(s.totalMinor), fontWeight = FontWeight.ExtraBold) }) { Text(stringResource(R.string.sale_total), fontWeight = FontWeight.ExtraBold) }
            if (s.returnedMinor > 0) SplitRow(end = { MoneyText("−" + money(s.returnedMinor), fontWeight = FontWeight.Bold, color = CuadraColors.Orange) }) { Text(stringResource(R.string.sale_returned_total)) }
        }
        if (s.returns.isNotEmpty()) {
            SectionLabel(stringResource(R.string.sale_returns_title))
            s.returns.forEach { r ->
                CuadraCard {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        SplitRow(end = { MoneyText("−" + money(r.totalMinor), fontWeight = FontWeight.ExtraBold) }) {
                            Text(stringResource(R.string.sale_return_by, time.format(Instant.ofEpochMilli(r.atMillis)), r.by ?: "—"), fontWeight = FontWeight.Bold)
                        }
                        r.lines.forEach { l -> Text("${qtyText(l.quantityMilli)} × ${l.name}", style = MaterialTheme.typography.bodyMedium, maxLines = 3, ellipsize = true) }
                        r.refunds.forEach { (m, a) -> SplitRow(end = { MoneyText(money(a), style = MaterialTheme.typography.bodyMedium) }) { Text(if (m == "CREDIT") stringResource(R.string.refund_CREDIT) else methodName(m), style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted) } }
                        Text(stringResource(R.string.sale_return_reason, r.reason), style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted)
                        if (r.pending) TagRow { Tag(stringResource(R.string.sale_return_pending), CuadraColors.Ink, CuadraColors.Soft) }
                    }
                }
            }
        }
        if (s.payments.isNotEmpty()) {
            SectionLabel(stringResource(R.string.sale_payments))
            s.payments.forEach { p ->
                SplitRow(end = { MoneyText(money(p.amountMinor), fontWeight = FontWeight.Bold) }) {
                    Column {
                        Text(methodName(p.method, p.otherLabel), fontWeight = FontWeight.Bold)
                        if (p.method == "CASH" && p.tenderedMinor != null) {
                            // Lo recibido y el vuelto son montos: cada uno en su línea, completo.
                            MoneyText(stringResource(R.string.sale_tendered, money(p.tenderedMinor)), style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted)
                            p.changeMinor?.takeIf { it > 0 }?.let { MoneyText(stringResource(R.string.sale_change, money(it)), style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted) }
                        }
                        if (p.method == "CREDIT" && !p.debtorLabel.isNullOrBlank()) Text(stringResource(R.string.sale_debtor, p.debtorLabel), style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted, maxLines = 2, ellipsize = true)
                        if (!p.reference.isNullOrBlank()) Text(p.reference, style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted, maxLines = 1, ellipsize = true)
                    }
                }
            }
        }
    }
}

/**
 * Eliminar una venta cobrada: el motivo es OBLIGATORIO (mín. 5 letras); se explica que la venta se conserva anulada. `undo`: «Anular esta venta» (la última
 * propia en los primeros 5 minutos); `tooLate`: ya pasó el plazo mientras la hoja estaba abierta.
 */
@Composable
fun DeleteSaleSheet(reason: String, actions: HistoryActions, undo: Boolean = false, tooLate: Boolean = false) =
    SaleReasonSheet(reason, actions::updateReason, actions::closeDelete, actions::confirmDelete, undo, tooLate)

/** La hoja del motivo para eliminar o anular una venta (también la usa «Venta cobrada» para «Anular esta venta»). */
@Composable
fun SaleReasonSheet(reason: String, onReason: (String) -> Unit, onClose: () -> Unit, onConfirm: () -> Unit, undo: Boolean, tooLate: Boolean) {
    val valid = SaleDeletion.check(reason) == ReasonCheck.OK
    Sheet(onClose, actions = {
        ButtonRow {
            CuadraButton(stringResource(R.string.cancel), onClose, Modifier.share(1f))
            CuadraButton(stringResource(if (undo) R.string.undo_confirm else R.string.sale_delete_confirm), onConfirm, Modifier.share(1.4f), kind = ButtonKind.DANGER, enabled = valid && !tooLate)
        }
    }) {
        Text(stringResource(if (undo) R.string.undo_title else R.string.sale_delete_title), style = MaterialTheme.typography.headlineMedium)
        if (tooLate) Text(stringResource(R.string.undo_too_late), color = CuadraColors.Red, fontWeight = FontWeight.Bold)
        Text(stringResource(if (undo) R.string.undo_help else R.string.sale_delete_help), style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted)
        VoiceTextField(
            reason, onReason, Modifier.fillMaxWidth(), minLines = 2, label = { Text(stringResource(R.string.sale_delete_reason)) },
            supportingText = if (!valid && reason.isNotEmpty()) ({ Text(stringResource(R.string.sale_delete_reason_short)) }) else null, isError = !valid && reason.isNotEmpty(),
        )
    }
}

/**
 * «Devolver productos»: cada línea con lo vendido y lo que queda por devolver, la cantidad a devolver («Todo» la completa), cómo se devuelve el dinero y
 * el motivo (obligatorio). Abajo, FIJOS, el monto a devolver y el botón.
 */
@Composable
fun ReturnSheet(d: com.cuadra.caja.ui.ReturnDraft, actions: HistoryActions) {
    val lines = d.lines
    val milli = d.milli
    Sheet(actions::closeReturn, actions = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SplitRow(end = { MoneyText(money(d.estimate), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold) }) {
                Text(stringResource(R.string.return_estimate), fontWeight = FontWeight.Bold)
            }
            ButtonRow {
                CuadraButton(stringResource(R.string.cancel), actions::closeReturn, Modifier.share(1f))
                CuadraButton(stringResource(R.string.return_confirm, money(d.estimate)), actions::confirmReturn, Modifier.share(1.4f), kind = ButtonKind.PRIMARY, enabled = d.canConfirm)
            }
        }
    }) {
        Text(stringResource(R.string.return_title), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.return_help), style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted)
        lines.forEach { l ->
            val bad = (milli[l.id] ?: 0) > l.remainingMilli
            CuadraCard {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(l.name, fontWeight = FontWeight.Bold, maxLines = 3, ellipsize = true)
                    Text(stringResource(R.string.return_line_hint, qtyText(l.quantityMilli), qtyText(l.remainingMilli)), style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted)
                    if (l.remainingMilli == 0L) {
                        Text(stringResource(R.string.return_nothing_left), style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted)
                    } else {
                        com.cuadra.caja.ui.common.NumberField(
                            d.texts[l.id].orEmpty(), { actions.setReturnQty(l.id, it) }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.return_qty)) },
                            isError = bad, supportingText = if (bad) ({ Text(stringResource(R.string.return_exceeds)) }) else null,
                        )
                        LinkAction(stringResource(R.string.return_all), { actions.returnAll(l.id) })
                    }
                }
            }
        }
        SectionLabel(stringResource(R.string.return_method))
        ChipFlow {
            com.cuadra.caja.domain.SaleReturnMath.methods(d.sale.payments.map { it.method }).forEach { m ->
                CuadraChip(stringResource(when (m) {
                    com.cuadra.caja.domain.RefundMethod.CASH -> R.string.return_method_CASH
                    com.cuadra.caja.domain.RefundMethod.SAME -> R.string.return_method_SAME
                    com.cuadra.caja.domain.RefundMethod.CREDIT_NOTE -> R.string.return_method_CREDIT_NOTE
                }), d.method == m, { actions.setReturnMethod(m) })
            }
        }
        val reasonShort = d.reason.isNotEmpty() && !com.cuadra.caja.domain.SaleReturnMath.reasonOk(d.reason)
        VoiceTextField(
            d.reason, actions::setReturnReason, Modifier.fillMaxWidth(), minLines = 2, label = { Text(stringResource(R.string.return_reason)) },
            supportingText = if (reasonShort) ({ Text(stringResource(R.string.return_reason_short)) }) else null, isError = reasonShort,
        )
    }
}

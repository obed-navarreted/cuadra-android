package com.cuadra.caja.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.clickable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.cuadra.caja.domain.PaymentMethods
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import com.cuadra.caja.ui.common.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cuadra.caja.R
import com.cuadra.caja.core.model.Money
import com.cuadra.caja.domain.CashSuggestions
import com.cuadra.caja.domain.CashTender
import com.cuadra.caja.domain.PayMethod
import com.cuadra.caja.domain.PaymentEntry
import com.cuadra.caja.domain.PaymentIssue
import com.cuadra.caja.ui.CajaUi
import com.cuadra.caja.ui.CajaActions
import com.cuadra.caja.ui.CajaViewModel
import com.cuadra.caja.ui.CobroUi
import com.cuadra.caja.ui.common.ButtonKind
import com.cuadra.caja.ui.common.ChipFlow
import com.cuadra.caja.ui.common.PrintNoticePopup
import com.cuadra.caja.domain.printing.PrinterBadge
import com.cuadra.caja.ui.common.MoneyText
import com.cuadra.caja.ui.common.NumberField
import com.cuadra.caja.ui.common.ScreenFrame
import com.cuadra.caja.ui.common.SplitRow
import com.cuadra.caja.ui.common.CuadraButton
import com.cuadra.caja.ui.common.CuadraCard
import com.cuadra.caja.ui.common.CuadraChip
import com.cuadra.caja.ui.common.LocalMoney
import com.cuadra.caja.ui.common.SectionLabel
import com.cuadra.caja.ui.common.money
import com.cuadra.caja.ui.common.VoiceTextField
import com.cuadra.caja.ui.theme.CuadraColors

fun methodRes(m: PayMethod) = when (m) {
    PayMethod.CASH -> R.string.method_CASH
    PayMethod.TRANSFER -> R.string.method_TRANSFER
    PayMethod.CARD -> R.string.method_CARD
    PayMethod.CREDIT -> R.string.method_CREDIT
    PayMethod.OTHER -> R.string.method_OTHER
}

@Composable
fun CobroScreen(ui: CajaUi, vm: CajaViewModel) = CobroContent(ui, vm)

/**
 * Cobro con pago mixto: cualquier combinación de métodos; el efectivo calcula el vuelto; lo fiado se anota con el nombre.
 *
 * Disposición: arriba fijo el título y el total; en medio, los métodos y sus campos (se desplaza y respeta el teclado); abajo FIJOS lo que falta o el
 * vuelto (así el teclado nunca lo tapa) y el botón de confirmar.
 */
@Composable
fun CobroContent(ui: CajaUi, actions: CajaActions, nowMillis: Long = System.currentTimeMillis()) {
    val cobro = ui.cobro ?: return
    val plan = cobro.plan
    val money = LocalMoney.current

    // El cobro ocupa toda la pantalla: la raíz ya pone el margen superior y aquí solo falta el inferior (la barra de navegación se oculta).
    val done = cobro.doneChangeMinor
    // Enviada a caja (ADR 0015): el mismo destello corto de «Venta cobrada» y a la venta nueva.
    val sent = cobro.sent
    val finished = done != null || sent
    // «Venta cobrada» vuelve sola a la venta nueva (1.2 s; 4 s con el botón de WhatsApp) y un toque en cualquier parte la adelanta. Mientras el envío por WhatsApp
    // está abierto no corre el tiempo (al cerrarlo se pasa a la venta nueva).
    val flashMillis = com.cuadra.caja.domain.SaleFlash.durationMillis(cobro.offerWhatsApp, cobro.doneShare != null)
    val progress = remember { Animatable(1f) }
    if (finished) LaunchedEffect(ui.share == null) {
        if (ui.share != null) return@LaunchedEffect
        progress.snapTo(1f)
        progress.animateTo(0f, tween(flashMillis.toInt(), easing = LinearEasing))
        actions.finishCobro()
    }
    Box(Modifier.fillMaxSize().then(if (finished) Modifier.pointerInput(Unit) { detectTapGestures { actions.finishCobro() } } else Modifier)) {
    ScreenFrame(
        Modifier.fillMaxSize().navigationBarsPadding().imePadding().padding(horizontal = 16.dp), spacing = 8.dp,
        header = {
            // El título nunca se parte letra por letra (letra grande): el total baja a otra línea, a todo el ancho y sin recortarse.
            SplitRow(Modifier.padding(top = 4.dp), endMaxFraction = 0.6f, end = { MoneyText(money.format(plan.totalMinor), fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleLarge) }) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!finished) CuadraButton("‹", actions::cancelCobro, Modifier.size(48.dp))
                    Text(stringResource(R.string.pay_title), style = MaterialTheme.typography.headlineSmall, maxLines = 1)
                }
            }
        },
        footer = {
            if (sent) {
                Unit
            } else if (cobro.toRegister && cobro.registerCheckout) {
                // «Enviar a caja»: sin método ni vuelto; el botón siempre a la vista (también con el teclado abierto escribiendo la nota).
                CuadraButton(
                    stringResource(R.string.pay_register_send), actions::confirmSendToRegister, Modifier.fillMaxWidth().padding(bottom = 8.dp), kind = ButtonKind.DARK,
                    enabled = !cobro.saving, height = 56,
                )
            } else if (done != null) {
                // «Venta cobrada» no pide nada: vuelve sola a una venta nueva. Solo con la preferencia de WhatsApp encendida se queda más y ofrece el botón, con una barra del tiempo que queda.
                // (El comprobante se imprime solo si la impresora está activada y conectada; «Anular» vive en el aviso de la venta nueva y en Ventas.)
                cobro.doneShare?.let {
                    FlashProgress(progress.value)
                    CuadraButton(stringResource(R.string.pay_done_send), { actions.shareDone(it) }, Modifier.fillMaxWidth().padding(bottom = 8.dp), kind = ButtonKind.WHATSAPP)
                }
            } else {
                // Vuelto o faltante SIEMPRE a la vista, junto al botón: con el teclado abierto el campo «Recibido» sube, esto no se mueve.
                val cash = plan.entries.firstOrNull { it.method == PayMethod.CASH }
                val received = cash?.tenderedMinor
                if (cash != null && received != null) {
                    if (received < cash.amountMinor) {
                        Text(
                            stringResource(R.string.pay_cash_short, money.format(cash.amountMinor - received)), color = CuadraColors.Orange, fontWeight = FontWeight.ExtraBold,
                            style = MaterialTheme.typography.titleLarge, maxLines = 2, minScale = 0.5f,
                        )
                    } else {
                        SplitRow(end = { MoneyText(money.format(cash.changeMinor), color = CuadraColors.Green, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.headlineMedium) }) {
                            Text(stringResource(R.string.pay_change), color = CuadraColors.Green, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                // Lo que falta, bien visible, con un toque para completarlo con otro método (agrega una línea con lo que falta).
                if (plan.missingMinor > 0 && plan.entries.isNotEmpty()) MissingBlock(plan.missingMinor, cobro, actions)
                plan.issues().forEach { issue ->
                    when (issue) {
                        is PaymentIssue.Excess -> Text(stringResource(R.string.pay_excess, money.format(issue.excessMinor)), color = CuadraColors.Red, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleLarge, maxLines = 2, minScale = 0.5f)
                        PaymentIssue.DebtorRequired -> Text(stringResource(R.string.pay_debtor_required), color = CuadraColors.Orange, fontWeight = FontWeight.ExtraBold)
                        else -> Unit
                    }
                }
                // Reglas de fiado del negocio: sin cliente (si lo exige) o pasado el límite (si bloquea), no se cobra.
                cobro.creditBlock?.let { b -> Text(stringResource(if (b == com.cuadra.caja.domain.CreditRules.Block.CUSTOMER_REQUIRED) R.string.pay_customer_required else R.string.pay_limit_blocked), color = CuadraColors.Red, fontWeight = FontWeight.ExtraBold) }
                val change = plan.changeMinor
                CuadraButton(
                    if (change > 0) stringResource(R.string.pay_confirm_change, money.format(change)) else stringResource(R.string.pay_confirm),
                    actions::confirmCobro, Modifier.fillMaxWidth().padding(bottom = 8.dp), kind = ButtonKind.PRIMARY, enabled = plan.canConfirm && !cobro.saving && cobro.creditBlock == null, height = 56,
                )
            }
        },
    ) {
        if (sent) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text("✓", style = MaterialTheme.typography.displaySmall, color = CuadraColors.Green)
                Text(stringResource(R.string.pay_register_sent), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                if (cobro.registerNote.isNotBlank()) Text(cobro.registerNote, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center, maxLines = 3, ellipsize = true)
            }
        } else if (done != null) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text("✓", style = MaterialTheme.typography.displaySmall, color = CuadraColors.Green)
                Text(stringResource(R.string.pay_done), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                // El vuelto, grande y en negrita: lo que la persona tiene que ver antes de que la pantalla vuelva sola a la venta nueva.
                if (com.cuadra.caja.domain.SaleFlash.showsChange(done)) {
                    Text(stringResource(R.string.pay_done_change, money.format(done)), style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.ExtraBold, color = CuadraColors.Green, textAlign = TextAlign.Center, maxLines = 2, minScale = 0.4f)
                }
            }
        } else {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (cobro.registerCheckout) CobroModeSwitch(cobro, actions)
                if (cobro.registerCheckout && cobro.toRegister) {
                    RegisterSendBody(cobro, actions)
                    return@Column
                }
                SectionLabel(stringResource(R.string.pay_how))
                ChipFlow {
                    cobro.availableMethods.forEach { m -> CuadraChip(stringResource(methodRes(m)), plan.entries.any { it.method == m }, { actions.toggleMethod(m) }) }
                }
                Text(stringResource(R.string.pay_combine_hint), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Normal, color = MaterialTheme.colorScheme.onSurfaceVariant)
                plan.entries.forEachIndexed { index, entry -> EntryCard(entry, plan.entries.size, cobro, actions) }
            }
        }
    }
    }
}

/** La barra fina del tiempo que queda en «Venta cobrada» con el botón de WhatsApp (de llena a vacía). */
@Composable
private fun FlashProgress(remaining: Float) {
    Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(CuadraColors.Line)) {
        Box(Modifier.fillMaxWidth(remaining.coerceIn(0f, 1f)).fillMaxHeight().background(CuadraColors.Green))
    }
}

/** «Falta C$ X» y, debajo, «Completar con: Efectivo · Transferencia · Tarjeta · Fiado»: un toque agrega una línea con lo que falta. */
@Composable
private fun MissingBlock(missingMinor: Long, cobro: CobroUi, actions: CajaActions) {
    val money = LocalMoney.current
    // «Falta C$ X» y a su lado (o debajo, si no cabe) «Completar con:»: una sola pieza para no comerse el pie con letra grande.
    SplitRow(
        end = { Text(stringResource(R.string.pay_complete_with), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Normal, color = MaterialTheme.colorScheme.onSurfaceVariant) },
        endMaxFraction = 0.45f,
    ) {
        Text(stringResource(R.string.pay_missing, money.format(missingMinor)), color = CuadraColors.Orange, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleLarge, maxLines = 2, minScale = 0.5f)
    }
    ChipFlow {
        PaymentMethods.completions(cobro.availableMethods).forEach { m ->
            val name = stringResource(methodRes(m))
            val description = stringResource(R.string.pay_complete_desc, name)
            CuadraChip(name, false, { actions.completeWith(m) }, Modifier.semantics { contentDescription = description })
        }
    }
}

@Composable
private fun EntryCard(entry: PaymentEntry, count: Int, cobro: CobroUi, actions: CajaActions) {
    val otherLabel = cobro.otherLabel
    val fmt = LocalMoney.current
    CuadraCard(padding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(methodRes(entry.method)), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold)
            // TODAS las líneas llevan su monto editable (transferencia, tarjeta, fiado…): nace con lo que falta, se puede bajar (pago parcial) y si el reparto
            // cambia por otro lado el campo se actualiza. El campo ocupa TODO el ancho y su letra baja si el monto no cabe: nunca se corta.
            var text by remember(entry.method) { mutableStateOf(CashTender.toText(entry.amountMinor, fmt.decimals)) }
            LaunchedEffect(entry.amountMinor) {
                if ((CashTender.parseMinor(text, fmt.decimals) ?: 0L) != entry.amountMinor) text = CashTender.toText(entry.amountMinor, fmt.decimals)
            }
            val removeDescription = stringResource(R.string.pay_remove_desc, stringResource(methodRes(entry.method)))
            NumberField(
                text, { text = CashTender.sanitize(it, fmt.decimals); actions.setAmount(entry.method, text) },
                textStyle = MaterialTheme.typography.titleLarge, prefix = fmt.currency.symbol, label = { Text(stringResource(R.string.pay_amount), maxLines = 1) },
                trailingIcon = if (count > 1) ({
                    Box(Modifier.size(48.dp).clickable(role = Role.Button) { actions.toggleMethod(entry.method) }.semantics { contentDescription = removeDescription }, contentAlignment = Alignment.Center) {
                        Text("✕", style = MaterialTheme.typography.titleSmall, maxLines = 1, minScale = 0.6f)
                    }
                }) else null,
            )
            when (entry.method) {
                PayMethod.CASH -> {
                    NumberField(
                        cobro.tenderedText, actions::setTenderedText,
                        textStyle = MaterialTheme.typography.titleLarge, prefix = fmt.currency.symbol, label = { Text(stringResource(R.string.pay_received), maxLines = 1) },
                        supportingText = { Text(stringResource(R.string.pay_received_hint)) },
                    )
                    ChipFlow {
                        CuadraChip(stringResource(R.string.pay_exact), entry.tenderedMinor == null || entry.tenderedMinor == entry.amountMinor, { actions.setTendered(entry.amountMinor) })
                        CashSuggestions.forAmount(entry.amountMinor, fmt.currency.code, fmt.decimals).forEach { s ->
                            CuadraChip(fmt.format(s), entry.tenderedMinor == s, { actions.setTendered(s) })
                        }
                    }
                }
                PayMethod.CREDIT -> CreditFields(entry, cobro, actions)
                PayMethod.OTHER -> VoiceTextField(otherLabel, actions::setOtherLabel, Modifier.fillMaxWidth(), singleLine = true, compact = true, placeholder = { Text(stringResource(R.string.pay_other_label)) })
                PayMethod.TRANSFER, PayMethod.CARD -> VoiceTextField(
                    entry.reference.orEmpty(), { actions.setReference(entry.method, it) }, Modifier.fillMaxWidth(), singleLine = true, compact = true,
                    placeholder = { Text(stringResource(R.string.pay_reference_hint)) },
                )
            }
        }
    }
}

/** A quién se le fía: un nombre basta. El cliente y el teléfono son opcionales; con teléfono se puede mandar el detalle por WhatsApp. */
@Composable
private fun CreditFields(entry: PaymentEntry, cobro: CobroUi, actions: CajaActions) {
    val fmt = LocalMoney.current
    Text(stringResource(R.string.method_CREDIT_help), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    VoiceTextField(
        cobro.debtor, actions::setDebtor, Modifier.fillMaxWidth(), singleLine = true, label = { Text(stringResource(R.string.pay_debtor)) },
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
    )
    if (cobro.customerMatches.isNotEmpty() || cobro.nameSuggestions.isNotEmpty()) {
        ChipFlow {
            cobro.customerMatches.forEach { cu -> CuadraChip(cu.name + if (cu.balanceMinor > 0) " · " + fmt.format(cu.balanceMinor) else "", false, { actions.pickCustomer(cu) }, userContent = true) }
            cobro.nameSuggestions.forEach { n -> CuadraChip(n, false, { actions.pickName(n) }, userContent = true) }
        }
    }
    NumberField(
        cobro.debtorPhone, actions::setDebtorPhone, label = { Text(stringResource(R.string.pay_debtor_phone), maxLines = 1) }, keyboardType = KeyboardType.Phone,
        isError = cobro.phoneInvalid, supportingText = { if (cobro.phoneInvalid) Text(stringResource(R.string.pay_debtor_invalid_phone)) },
    )
    cobro.customer?.let { cu ->
        val limit = cu.creditLimitMinor
        if (limit != null && cu.balanceMinor + entry.amountMinor > limit) Text(stringResource(R.string.pay_over_limit, fmt.format(limit)), color = CuadraColors.Orange, fontWeight = FontWeight.ExtraBold)
    }
    Text(stringResource(R.string.pay_debtor_optional), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    ChipFlow {
        if (cobro.customer == null) CuadraChip(stringResource(R.string.pay_debtor_save_customer), cobro.saveAsCustomer, actions::toggleSaveCustomer)
        if (cobro.offerWhatsApp) CuadraChip(stringResource(R.string.pay_debtor_send_wa), cobro.sendWhatsApp, actions::toggleSendWhatsApp)
    }
}

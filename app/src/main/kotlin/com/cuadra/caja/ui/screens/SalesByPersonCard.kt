package com.cuadra.caja.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cuadra.caja.R
import com.cuadra.caja.domain.PeopleMode
import com.cuadra.caja.domain.PersonShare
import com.cuadra.caja.domain.SalesByPerson
import com.cuadra.caja.ui.common.LinkAction
import com.cuadra.caja.ui.common.MoneyText
import com.cuadra.caja.ui.common.SegmentedChoice
import com.cuadra.caja.ui.common.SplitRow
import com.cuadra.caja.ui.common.Text
import com.cuadra.caja.ui.common.CuadraCard
import com.cuadra.caja.ui.common.money
import com.cuadra.caja.ui.theme.CuadraColors

/**
 * «Por persona» (solo dueño y admin): lo vendido en el rango, una fila por persona con sus ventas, su total y una barra fina con su parte del total. Las
 * 5 primeras y «Ver todos (N)». Tocar una fila filtra la lista por esa persona (`selectedId`), y tocarla otra vez quita el filtro. Con «Cobro en caja»
 * (`showControl`) se elige entre quien cobró y quien atendió.
 */
@Composable
fun SalesByPersonCard(
    rows: List<PersonShare>, mode: PeopleMode, showControl: Boolean, selectedId: String?, showAll: Boolean,
    onMode: (PeopleMode) -> Unit, onPerson: (String?) -> Unit, onShowAll: (Boolean) -> Unit, modifier: Modifier = Modifier,
) {
    if (rows.isEmpty()) return
    CuadraCard(modifier, padding = PaddingValues(horizontal = 6.dp, vertical = 12.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Column(Modifier.padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.people_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                if (showControl) {
                    SegmentedChoice(
                        null, listOf(stringResource(R.string.people_charged), stringResource(R.string.people_served)),
                        if (mode == PeopleMode.SERVED) 1 else 0, { onMode(if (it == 1) PeopleMode.SERVED else PeopleMode.CHARGED) },
                    )
                    Text(stringResource(if (mode == PeopleMode.SERVED) R.string.people_served_help else R.string.people_charged_help), style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted)
                }
            }
            SalesByPerson.visible(rows, showAll).forEach { p ->
                val on = p.id != null && p.id == selectedId
                PersonRow(p, on) { if (p.id != null) onPerson(if (on) null else p.id) }
            }
            val hidden = SalesByPerson.hiddenCount(rows, showAll)
            if (hidden > 0) LinkAction(stringResource(R.string.people_show_all, rows.size), { onShowAll(true) }, Modifier.padding(horizontal = 4.dp))
            else if (showAll && rows.size > SalesByPerson.TOP + 1) LinkAction(stringResource(R.string.people_show_less), { onShowAll(false) }, Modifier.padding(horizontal = 4.dp))
        }
    }
}

@Composable
private fun PersonRow(p: PersonShare, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(if (selected) CuadraColors.GreenSoft else Color.Transparent)
            .selectable(selected, enabled = p.id != null, role = Role.Button, onClick = onClick).heightIn(min = 48.dp).padding(horizontal = 8.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        SplitRow(end = { MoneyText(money(p.totalMinor), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold) }) {
            Column {
                Text(p.name, style = MaterialTheme.typography.titleSmall, fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Bold, maxLines = 2, ellipsize = true)
                Text(
                    pluralStringResource(R.plurals.people_sales_share, p.count.toInt().coerceAtLeast(0), p.count.toInt(), p.percent),
                    style = MaterialTheme.typography.bodyMedium, color = CuadraColors.Muted,
                )
            }
        }
        ShareBar(p.share, selected)
    }
}

/** La barra fina: pista suave y relleno verde redondeado con la parte del total (siempre se alcanza a ver si hay algo). */
@Composable
private fun ShareBar(share: Float, selected: Boolean) {
    val shape = RoundedCornerShape(50)
    Box(Modifier.fillMaxWidth().height(6.dp).clip(shape).background(if (selected) Color.White else CuadraColors.Soft)) {
        if (share > 0f) Box(Modifier.fillMaxWidth(share.coerceIn(0.03f, 1f)).fillMaxHeight().clip(shape).background(CuadraColors.Green))
    }
}

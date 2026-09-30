package com.cuadra.caja.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cuadra.caja.ui.theme.CuadraColors

class SectionEntry(val label: String, val icon: Int, val selected: Boolean, val onClick: () -> Unit)

/**
 * Barra de secciones: ícono de 24 dp con la etiqueta debajo, en UNA línea. La letra sigue el ajuste del sistema (sin tope): si la etiqueta
 * no cabe en su quinto del ancho, baja hasta lo justo (mínimo 45 %, o sea ≥ 10 sp aun con letra 2×) y nunca se recorta ni se parte.
 * La prueba de diseño (ui/guard) lo comprueba con letra 0.85x–2.0x en 320, 360 y 411 dp.
 */
@Composable
fun SectionBar(entries: List<SectionEntry>, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().background(CuadraColors.Surface).navigationBarsPadding()) {
        entries.forEach { e -> SectionItem(e, Modifier.weight(1f)) }
    }
}

@Composable
private fun SectionItem(e: SectionEntry, modifier: Modifier) {
    val color = if (e.selected) CuadraColors.Green else CuadraColors.Muted
    Column(
        modifier.heightIn(min = 64.dp).clickable(role = Role.Tab, onClick = e.onClick)
            .semantics(mergeDescendants = true) { this.selected = e.selected }
            .padding(horizontal = 2.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.size(width = 52.dp, height = 28.dp).background(if (e.selected) CuadraColors.Green.copy(alpha = 0.16f) else Color.Transparent, RoundedCornerShape(50)),
            contentAlignment = Alignment.Center,
        ) { Icon(painterResource(e.icon), contentDescription = null, tint = color, modifier = Modifier.size(24.dp)) }
        Text(
            e.label, color = color, fontSize = 11.sp, lineHeight = 14.sp, fontWeight = if (e.selected) FontWeight.ExtraBold else FontWeight.Bold,
            maxLines = 1, minScale = 0.45f, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
        )
    }
}

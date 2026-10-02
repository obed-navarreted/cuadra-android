package com.cuadra.caja.ui.screens

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.cuadra.caja.R
import com.cuadra.caja.ui.common.MoneyText
import com.cuadra.caja.ui.common.SplitRow
import com.cuadra.caja.ui.common.Text
import com.cuadra.caja.ui.common.money
import com.cuadra.caja.ui.theme.CuadraColors

/** Etiqueta de prueba de la línea de una promoción en el recibo. */
const val TAG_PROMO_LINE = "PromoLine"

/** «Promo 3 por C$ 100:   −C$ 70» en verde: debajo de las líneas que tocó (recibo en curso) o al final de las líneas (detalle de una venta). */
@Composable
fun PromoLineRow(quantity: Int, priceMinor: Long, discountMinor: Long, modifier: Modifier = Modifier) {
    SplitRow(modifier.testTag(TAG_PROMO_LINE), end = { MoneyText("−" + money(discountMinor), color = CuadraColors.Green, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleSmall) }) {
        Text(stringResource(R.string.promo_line, quantity, money(priceMinor)) + ":", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = CuadraColors.Green)
    }
}

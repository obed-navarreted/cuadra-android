package com.cuadra.caja.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.cuadra.caja.R
import com.cuadra.caja.domain.printing.PrintNotice
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.cuadra.caja.ui.theme.CuadraColors

/** Etiqueta de prueba de un aviso flotante: pasa POR ENCIMA del contenido a propósito (no ocupa lugar), así que la guardia no lo cuenta como traslape. */
const val TAG_OVERLAY = "TransientOverlay"

/** Cuánto dura a la vista un aviso flotante (ms). */
const val NOTICE_MILLIS = 3500L

/**
 * Aviso flotante de una línea (píldora oscura): NO ocupa lugar en la pantalla; se dibuja encima, anclado arriba y centrado, con hasta 90 % del ancho.
 * Se pone como último hijo de la raíz de una pantalla (`Box`) con `Modifier.align(Alignment.TopCenter)`; lo que hay debajo no se mueve.
 *
 * Visualmente mide unos 36 dp de alto (más si la letra crece); la acción («Deshacer») conserva su área tocable de 48 dp, que sobresale de la píldora.
 * `liveRegion`: los lectores de pantalla lo anuncian al aparecer o cambiar.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun FloatingNotice(
    text: String, actionLabel: String?, onAction: () -> Unit, modifier: Modifier = Modifier, ellipsize: Boolean = true,
    /** Ícono chico al inicio (advertencia, impresora). */
    @androidx.annotation.DrawableRes icon: Int? = null, iconTint: Color = CuadraColors.OnInkGreen,
    /** Segunda acción («Después»): también con 48 dp de área tocable. Si el texto y las acciones no caben en una línea, las acciones bajan debajo del texto. */
    secondLabel: String? = null, onSecond: () -> Unit = {},
) {
    val tall = LocalDensity.current.fontScale > 1.3f || secondLabel != null
    val lines = if (tall) 2 else 1
    Box(modifier.fillMaxWidth(0.9f), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.widthIn(min = 48.dp).testTag(TAG_OVERLAY).semantics { liveRegion = LiveRegionMode.Polite }) {
            // La píldora visible (36 dp) va detrás; el contenido mide 48 dp como mínimo para que «Deshacer» se pueda tocar sin apuntar fino.
            Surface(Modifier.matchParentSize().padding(vertical = 6.dp), shape = if (secondLabel != null) RoundedCornerShape(22.dp) else RoundedCornerShape(50), color = CuadraColors.Ink, shadowElevation = 6.dp) {}
            val body: @Composable () -> Unit = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (icon != null) Icon(painterResource(icon), contentDescription = null, tint = iconTint, modifier = Modifier.padding(end = 8.dp).size(18.dp))
                    Text(
                        text, Modifier.weight(1f, fill = false).padding(vertical = 8.dp), color = CuadraColors.Bg, style = MaterialTheme.typography.labelMedium,
                        maxLines = lines, ellipsize = ellipsize,
                    )
                }
            }
            if (secondLabel == null) {
                // Un aviso de una acción («Deshacer»): texto y acción en la misma fila, como siempre.
                Row(Modifier.heightIn(min = 48.dp).padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (icon != null) Icon(painterResource(icon), contentDescription = null, tint = iconTint, modifier = Modifier.padding(end = 8.dp).size(18.dp))
                    Text(
                        text, Modifier.weight(1f, fill = false).padding(vertical = 8.dp), color = CuadraColors.Bg, style = MaterialTheme.typography.labelMedium,
                        maxLines = lines, ellipsize = ellipsize,
                    )
                    if (actionLabel != null) LinkAction(actionLabel, onAction, color = CuadraColors.GreenSoft, style = MaterialTheme.typography.labelMedium)
                    else Box(Modifier.width(12.dp))
                }
            } else {
                // Dos acciones: texto y acciones en una fila si caben; si no, las acciones pasan a la línea de abajo (el texto nunca queda apretado hasta partir palabras).
                FlowRow(Modifier.heightIn(min = 48.dp).padding(start = 16.dp, end = 4.dp), verticalArrangement = Arrangement.Center, itemVerticalAlignment = Alignment.CenterVertically) {
                    body()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (actionLabel != null) LinkAction(actionLabel, onAction, color = CuadraColors.GreenSoft, style = MaterialTheme.typography.labelMedium)
                        LinkAction(secondLabel, onSecond, color = CuadraColors.Line, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

/**
 * El aviso después de imprimir: «Impreso» (chico, sin acciones), «Sin impresora conectada» con ícono de advertencia y «Reintentar» / «Después», o «No se pudo imprimir»
 * con las mismas acciones. Flotante como «Deshacer»: no ocupa lugar y la venta nunca se detiene por él.
 */
@Composable
fun PrintNoticePopup(notice: PrintNotice, onRetry: () -> Unit, onLater: () -> Unit, modifier: Modifier = Modifier) {
    when (notice) {
        PrintNotice.PRINTED -> FloatingNotice(stringResource(R.string.print_ok), null, {}, modifier, icon = R.drawable.ic_printer)
        PrintNotice.NO_PRINTER -> FloatingNotice(
            stringResource(R.string.print_none), stringResource(R.string.print_retry), onRetry, modifier, icon = R.drawable.ic_warning, iconTint = CuadraColors.OnInkOrange,
            secondLabel = stringResource(R.string.print_later), onSecond = onLater,
        )
        PrintNotice.FAILED -> FloatingNotice(
            stringResource(R.string.print_failed), stringResource(R.string.print_retry), onRetry, modifier, icon = R.drawable.ic_warning, iconTint = CuadraColors.OnInkOrange,
            secondLabel = stringResource(R.string.print_later), onSecond = onLater,
        )
    }
}

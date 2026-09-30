package com.cuadra.caja.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.cuadra.caja.ui.theme.CuadraColors

/**
 * `true` solo en las pruebas de diseño (ui/guard): los diálogos se dibujan dentro del mismo árbol, con los márgenes que tiene una ventana
 * de diálogo real, para poder medirlos con la misma guardia que las pantallas.
 */
val LocalInlineDialogs = staticCompositionLocalOf { false }

/** Etiqueta de prueba de la capa de un diálogo en línea: la guardia revisa SOLO esa capa (lo de debajo ya se revisó en su propio caso). */
const val TAG_DIALOG_LAYER = "DialogLayer" 

/** Diálogo de la app: una ventana aparte en el teléfono; en línea (mismo árbol) en la guardia de diseño. */
@Composable
fun AppDialog(onDismissRequest: () -> Unit, usePlatformDefaultWidth: Boolean = true, content: @Composable () -> Unit) {
    if (LocalInlineDialogs.current) {
        Box(Modifier.fillMaxSize().testTag(TAG_DIALOG_LAYER).padding(horizontal = if (usePlatformDefaultWidth) 20.dp else 0.dp, vertical = 40.dp), contentAlignment = Alignment.Center) { content() }
    } else {
        // Un diálogo es OTRA ventana y estrena su propio `LocalDensity` (con toda la letra del teléfono): se re-aplica el de la app para que valga la política de letra.
        val density = LocalDensity.current
        Dialog(onDismissRequest = onDismissRequest, properties = DialogProperties(usePlatformDefaultWidth = usePlatformDefaultWidth)) { CompositionLocalProvider(LocalDensity provides density, content = content) }
    }
}

/**
 * Hoja de diálogo. El contenido se desplaza si no cabe (letra grande, teclado) y las `actions` (botones) quedan FIJAS abajo, siempre a la vista:
 * la persona nunca tiene que buscar el botón de guardar al final de un formulario largo.
 */
@Composable
fun Sheet(onDismiss: () -> Unit, actions: (@Composable () -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    AppDialog(onDismiss) {
        // imePadding: con el teclado abierto la hoja se achica y «Guardar» (fijo abajo) sigue a la vista.
        Surface(Modifier.imePadding(), shape = RoundedCornerShape(24.dp), color = CuadraColors.Bg) {
            Column(Modifier.padding(20.dp)) {
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
                if (actions != null) Box(Modifier.padding(top = 12.dp).testTag(TAG_PINNED_ACTION)) { actions() }
            }
        }
    }
}

/**
 * Hoja que sube desde abajo (recibo): ocupa `heightFraction` del alto, se arrastra hacia abajo para cerrarla y su contenido se organiza como una pantalla
 * (`ScreenFrame`: centro que se desplaza y acciones fijas). En la guardia de diseño se dibuja en línea, pegada abajo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppBottomSheet(onDismiss: () -> Unit, heightFraction: Float = 0.75f, content: @Composable () -> Unit) {
    if (LocalInlineDialogs.current) {
        Box(Modifier.fillMaxSize().testTag(TAG_DIALOG_LAYER), contentAlignment = Alignment.BottomCenter) {
            Surface(Modifier.fillMaxWidth().fillMaxHeight(heightFraction), shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp), color = CuadraColors.Bg) { content() }
        }
    } else {
        // Igual que en `AppDialog`: la hoja es otra ventana; se re-aplica el `LocalDensity` de la app.
        val density = LocalDensity.current
        ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = CuadraColors.Bg) {
            CompositionLocalProvider(LocalDensity provides density) {
                Box(Modifier.fillMaxWidth().fillMaxHeight(heightFraction).navigationBarsPadding()) { content() }
            }
        }
    }
}

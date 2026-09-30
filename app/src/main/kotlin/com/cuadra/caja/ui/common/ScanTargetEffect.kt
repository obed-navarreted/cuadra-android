package com.cuadra.caja.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.cuadra.caja.CuadraApp

/** Mientras esta pantalla está a la vista, las lecturas del lector físico (Zebra/teclado) llegan a `onCode` (un editor con campo de código, por ejemplo). */
@Composable
fun ScanTargetEffect(onCode: (String) -> Unit) {
    val context = LocalContext.current
    val hub = remember { (context.applicationContext as? CuadraApp)?.container?.scanner }
    val current by rememberUpdatedState(onCode)
    DisposableEffect(hub) {
        val claim = hub?.claim { code, _ -> current(code) }
        onDispose { claim?.close() }
    }
}

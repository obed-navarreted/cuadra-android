package com.cuadra.caja

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.cuadra.caja.ui.AppRoot
import com.cuadra.caja.ui.common.ProvideAppFontScale
import kotlinx.coroutines.launch
import com.cuadra.caja.ui.theme.CuadraTheme

// AppCompatActivity: necesaria para el idioma por app en Android 8-12.
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as CuadraApp).container
        handle(intent)
        com.cuadra.caja.data.debug.DebugHooks.apply(container, intent)
        setContent {
            val choice by container.display.fontSize.collectAsState()
            ProvideAppFontScale(choice) { CuadraTheme { AppRoot(container) } }
        }
    }

    /** Al abrir y al volver a primer plano (máx. cada pocas horas): configuración del servidor. Sin red no pasa nada. */
    override fun onStart() {
        super.onStart()
        val c = (application as CuadraApp).container
        c.scope.launch { runCatching { c.appConfig.refreshIfDue() } }
        // Lector físico: DataWedge solo se escucha con la app a la vista.
        runCatching { c.scanner.attach() }
        // Primer arranque de esta instalación: en un Zebra enciende el lector del equipo y configura DataWedge; en los demás no cambia nada.
        runCatching { c.scanner.firstRun() }
        // Impresora (solo trabaja si la persona la activó): se conecta sola mientras la app está a la vista.
        runCatching { c.printer.attach() }
    }

    override fun onStop() {
        runCatching { (application as CuadraApp).container.scanner.detach() }
        runCatching { (application as CuadraApp).container.printer.detach() }
        super.onStop()
    }

    /**
     * Lector tipo teclado (Zebra en modo teclado, Bluetooth, USB): las ráfagas rápidas de teclas que terminan en Enter son una lectura. Solo si ningún campo
     * de texto tiene el foco; teclear normal pasa intacto (ver `WedgeKeys`).
     */
    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        val accepting = getSystemService(android.view.inputmethod.InputMethodManager::class.java)?.isAcceptingText == true
        if (runCatching { (application as CuadraApp).container.scanner.keys.dispatch(event, accepting) }.getOrDefault(false)) return true
        return super.dispatchKeyEvent(event)
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        handle(intent)
        val c = (application as CuadraApp).container
        com.cuadra.caja.data.debug.DebugHooks.apply(c, intent)
        if (com.cuadra.caja.BuildConfig.DEBUG && intent.getBooleanExtra("debug_zebra", false)) c.scanner.firstRun()
    }

    /** Un enlace `cuadra://…` (de una notificación) se deja pendiente; `AppRoot` lo abre cuando el teléfono está listo. */
    private fun handle(intent: android.content.Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme == "cuadra") (application as CuadraApp).container.pendingRoute.value = uri.toString()
    }
}

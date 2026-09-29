package com.cuadra.caja

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.cuadra.caja.ui.AppRoot
import kotlinx.coroutines.launch
import com.cuadra.caja.ui.theme.CuadraTheme

// AppCompatActivity: necesaria para el idioma por app en Android 8-12.
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as CuadraApp).container
        handle(intent)
        setContent { CuadraTheme { AppRoot(container) } }
    }

    /** Al abrir y al volver a primer plano (máx. cada pocas horas): configuración del servidor. Sin red no pasa nada. */
    override fun onStart() {
        super.onStart()
        val c = (application as CuadraApp).container
        c.scope.launch { runCatching { c.appConfig.refreshIfDue() } }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    /** Un enlace `cuadra://…` (de una notificación) se deja pendiente; `AppRoot` lo abre cuando el teléfono está listo. */
    private fun handle(intent: android.content.Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme == "cuadra") (application as CuadraApp).container.pendingRoute.value = uri.toString()
    }
}

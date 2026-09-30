package com.cuadra.caja.data.debug

import android.content.Intent
import com.cuadra.caja.AppContainer
import com.cuadra.caja.BuildConfig
import com.cuadra.caja.data.printer.DebugPrinter
import kotlinx.coroutines.launch

/**
 * Ganchos SOLO de compilaciones de depuración (en release `BuildConfig.DEBUG` es falso y no hacen nada) para probar en un emulador sin impresora ni Zebra:
 *  · `adb shell am start -n com.cuadra.caja/.MainActivity --es debug_printer connected|disconnected|off`: impresora simulada que conecta / que nunca conecta / la real.
 *    Lo «impreso» sale en el registro (`adb logcat -s CuentivaPrint`): los bytes y su lectura como texto.
 *  · `--ez debug_zebra true`: el equipo se comporta como un Zebra en el primer arranque (enciende el lector y muestra el aviso).
 *  · `--es debug_user_token <token>`: entra como si fuera con Google usando un token de sesión ya emitido por el servidor de desarrollo (para probar la
 *    pantalla de negocio nuevo, la consola de la plataforma y «Eliminar mi cuenta» en un emulador sin cuenta de Google).
 */
object DebugHooks {
    fun apply(c: AppContainer, intent: Intent?) {
        if (!BuildConfig.DEBUG || intent == null) return
        intent.getStringExtra("debug_printer")?.let {
            c.printer.debugMode = when (it) { "connected" -> DebugPrinter.CONNECTED; "disconnected" -> DebugPrinter.DISCONNECTED; else -> null }
        }
        if (intent.getBooleanExtra("debug_zebra", false)) c.scanner.debugZebra = true
        intent.getStringExtra("debug_user_token")?.takeIf { it.isNotBlank() }?.let { token ->
            kotlinx.coroutines.runBlocking { c.sessionStore.setUserToken(token) }
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch { c.auth.me() }
        }
    }
}

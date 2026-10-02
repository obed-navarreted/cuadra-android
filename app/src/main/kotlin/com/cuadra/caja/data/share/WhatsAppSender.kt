package com.cuadra.caja.data.share

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.FileProvider
import com.cuadra.caja.domain.ShareRoute
import com.cuadra.caja.domain.WhatsAppLinks
import com.cuadra.caja.domain.WhatsAppRoutes
import java.io.File

/**
 * Todo envío es MANUAL: se abre WhatsApp en el chat del cliente con el mensaje (o la imagen) ya listo y la persona solo toca enviar,
 * desde la cuenta de WhatsApp que tenga activa el teléfono. No hay envíos automáticos ni API de WhatsApp.
 */
object WhatsAppSender {
    const val PERSONAL = "com.whatsapp"
    const val BUSINESS = "com.whatsapp.w4b"
    private const val PREFS = "whatsapp"
    private const val KEY_PACKAGE = "package"

    /** Cuáles de las dos apps están instaladas (WhatsApp y/o WhatsApp Business). */
    fun installed(context: Context): List<String> = listOf(PERSONAL, BUSINESS).filter { isInstalled(context, it) }

    private fun isInstalled(context: Context, pkg: String) = try {
        context.packageManager.getPackageInfo(pkg, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    /** La app elegida antes, si sigue instalada. Con una sola instalada se usa esa; con las dos y sin elección, hay que preguntar. */
    fun preferred(context: Context): String? {
        val available = installed(context)
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_PACKAGE, null)
        return when {
            saved != null && saved in available -> saved
            available.size == 1 -> available[0]
            else -> null
        }
    }

    fun rememberChoice(context: Context, pkg: String) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_PACKAGE, pkg).apply()

    /**
     * Texto (ver `WhatsAppRoutes`): con número abre directo el chat del cliente (`wa.me`, número internacional); sin número abre el selector de contactos
     * de WhatsApp; sin WhatsApp instalado, la hoja de compartir del sistema. Nunca se envía solo.
     * @return false si no hay nada que pueda abrirlo.
     */
    /**
     * Abre `https://wa.me/<número>?text=<mensaje>` (la hoja «Enviar por WhatsApp» con número): en la app de WhatsApp elegida si hay, si no lo abre lo que el
     * teléfono tenga para ese enlace (el navegador lleva a WhatsApp). Nunca se envía solo. @return false si nada pudo abrirlo.
     */
    fun openChat(context: Context, phoneDigits: String, text: String, pkg: String?): Boolean {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(WhatsAppLinks.chat(phoneDigits, text)))
        if (pkg != null) intent.setPackage(pkg)
        if (context !is android.app.Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            if (pkg == null) false else openChat(context, phoneDigits, text, null)
        }
    }

    fun sendText(context: Context, phoneDigits: String?, text: String, pkg: String?): Boolean {
        val route = WhatsAppRoutes.forText(phoneDigits, installed(context).isNotEmpty())
        val intent = when (route) {
            ShareRoute.WHATSAPP_CHAT -> Intent(Intent.ACTION_VIEW, Uri.parse(WhatsAppLinks.chat(phoneDigits!!, text)))
            ShareRoute.WHATSAPP_PICKER, ShareRoute.SYSTEM_SHARE -> Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        }
        // Sin WhatsApp no se fuerza ningún paquete: el usuario elige en la hoja del sistema.
        return launch(context, intent, if (route == ShareRoute.SYSTEM_SHARE) null else pkg, chooser = route == ShareRoute.SYSTEM_SHARE)
    }

    /**
     * Imagen: WhatsApp no documenta una forma oficial de abrir el chat de un número al compartir una imagen. Se usa el extra `jid`,
     * que hoy funciona; si WhatsApp lo ignora, abre su selector de contactos y la persona elige al cliente (no se rompe nada).
     */
    fun sendImage(context: Context, phoneDigits: String?, caption: String?, image: File, pkg: String?): Boolean {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", image)
        val intent = Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        if (!caption.isNullOrBlank()) intent.putExtra(Intent.EXTRA_TEXT, caption)
        if (phoneDigits != null) intent.putExtra("jid", WhatsAppLinks.jid(phoneDigits))
        return launch(context, intent, pkg)
    }

    private fun launch(context: Context, base: Intent, pkg: String?, chooser: Boolean = false): Boolean {
        val intent = if (pkg != null && !chooser) base.setPackage(pkg) else Intent.createChooser(base, null)
        if (context !is android.app.Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }
}

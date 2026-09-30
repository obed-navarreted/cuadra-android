package com.cuadra.caja.ui.common

import android.content.Context
import android.content.Intent
import android.net.Uri

/** Abre una dirección web en el navegador (la consola de la plataforma, la página para eliminar la cuenta). Devuelve si se pudo. */
fun openInBrowser(context: Context, url: String?): Boolean {
    if (url.isNullOrBlank()) return false
    val ok = runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
    if (!ok) android.widget.Toast.makeText(context, context.getString(com.cuadra.caja.R.string.open_browser_failed), android.widget.Toast.LENGTH_LONG).show()
    return ok
}

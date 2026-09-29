package com.cuadra.caja.domain

import java.net.URI

/**
 * Decide si se ofrece la donación voluntaria y con qué enlace. Solo con modo `external_link`, URL https válida
 * y un build que no sea de Google Play (la política de Play no permite enlaces de pago externos para donaciones).
 */
object DonationOffer {
    const val MODE_EXTERNAL_LINK = "external_link"
    const val DISTRIBUTION_PLAY = "play"

    /** La URL a abrir, o `null` si no debe mostrarse nada. */
    fun from(mode: String?, url: String?, distribution: String): String? {
        if (mode?.trim() != MODE_EXTERNAL_LINK) return null
        if (distribution.trim().equals(DISTRIBUTION_PLAY, ignoreCase = true)) return null
        val clean = url?.trim().orEmpty()
        if (clean.isEmpty() || clean.any { it.isWhitespace() }) return null
        val uri = runCatching { URI(clean) }.getOrNull() ?: return null
        if (!"https".equals(uri.scheme, ignoreCase = true) || uri.host.isNullOrBlank()) return null
        return clean
    }
}

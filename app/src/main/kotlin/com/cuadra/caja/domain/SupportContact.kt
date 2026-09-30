package com.cuadra.caja.domain

import java.net.URLEncoder

/**
 * Contacto de «Apóyame»: WhatsApp y correo. Los datos vienen de `/api/config` (se pueden cambiar sin lanzar versión) y, sin ellos, valen los de fábrica.
 * En la versión de Google Play la pantalla solo muestra texto y contacto: no habla de pagos ni de donaciones.
 */
object SupportContact {
    const val DEFAULT_WHATSAPP = "50582724138"
    const val DEFAULT_EMAIL = "ndiazobed@gmail.com"
    const val DISTRIBUTION_PLAY = "play"

    /** Solo dígitos, de 8 a 15 (formato internacional sin «+»); lo demás se descarta y se usa el de fábrica. */
    fun whatsappNumber(configured: String?): String {
        val digits = configured.orEmpty().filter { it.isDigit() }
        return if (digits.length in 8..15) digits else DEFAULT_WHATSAPP
    }

    fun email(configured: String?): String = configured?.trim()?.takeIf { it.matches(Regex("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) } ?: DEFAULT_EMAIL

    /** `https://wa.me/<número>?text=<mensaje>` (el mensaje va codificado, con %20 y no con «+»). */
    fun whatsappUrl(number: String?, message: String): String =
        "https://wa.me/${whatsappNumber(number)}?text=" + URLEncoder.encode(message, "UTF-8").replace("+", "%20")

    fun isPlay(distribution: String): Boolean = distribution.trim().equals(DISTRIBUTION_PLAY, ignoreCase = true)
}

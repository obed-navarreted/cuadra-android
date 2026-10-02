package com.cuadra.caja.domain

/**
 * «Enviar por WhatsApp» con número (cualquier venta): la hoja pide el número (con el código del país del negocio ya escrito) o lo toma de un contacto, y abre
 * `https://wa.me/<número>?text=<comprobante>`. La app nunca envía sola: WhatsApp se abre con el mensaje listo y la persona toca «Enviar». Nada se recuerda.
 */
object WhatsAppReceipt {
    /** Lo que trae el campo al abrir la hoja: «+505 » (o vacío si no se conoce el país). */
    fun prefill(country: String?): String = PhoneNumbers.callingCode(country)?.let { "+$it " }.orEmpty()

    /**
     * El número para wa.me (solo cifras, con código de país) o `null` si no sirve. Acepta lo que se escribe o trae un contacto: espacios, guiones,
     * paréntesis, «+», «00» internacional y el 0 de larga distancia de adelante («0 8888 1234», «+505 0 8888 1234»). Un número nacional recibe el código
     * del país del negocio. Solo el código del país, sin número, no sirve.
     */
    fun number(raw: String?, country: String?): String? {
        if (raw.isNullOrBlank()) return null
        var text = raw.trim()
        val code = PhoneNumbers.callingCode(country)
        val national = PhoneNumbers.nationalLength(country)
        val digitsOnly = text.filter { it.isDigit() }
        val international = text.startsWith("+") || text.startsWith("00")
        if (international) {
            var digits = if (text.startsWith("00")) digitsOnly.drop(2) else digitsOnly
            // «+505 0 8888 1234»: el 0 de larga distancia sobra después del código del país.
            if (code != null && national != null && digits.startsWith(code + "0") && digits.length == code.length + 1 + national) {
                digits = code + digits.drop(code.length + 1)
            }
            if (code != null && digits == code) return null
            text = "+$digits"
        } else {
            // «0 8888 1234» (o «00…» ya se trató): el 0 de larga distancia de adelante sobra.
            var digits = digitsOnly
            if (national != null && digits.length == national + 1 && digits.startsWith("0")) digits = digits.drop(1)
            text = digits
        }
        return (PhoneNumbers.normalize(text, country) as? PhoneResult.Valid)?.digits
    }

    /** El texto del comprobante (la plantilla TICKET del negocio, o la de fábrica): negocio, fecha, una línea por producto y el total. */
    fun text(template: String, business: String, date: String, lines: List<Pair<String, String>>, total: String): String {
        val content = ShareBuilders.ticket(business, date, lines, total, CardLabels("", "", "", "", "", "", "", "", ""))
        return MessageTemplates.render(template, content.vars)
    }

    /** El enlace que abre el chat de ese número con el comprobante escrito. */
    fun link(number: String, text: String): String = WhatsAppLinks.chat(number, text)
}

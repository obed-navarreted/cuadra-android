package com.cuadra.caja.domain

/**
 * Plantillas de los mensajes de WhatsApp (dueño y admin): mismas variables que el panel web y que `MessageTemplates.render`. Sin texto propio se usa
 * el de fábrica (`MessageTemplates.default`); «Restaurar» borra el propio en el servidor.
 */
object TemplateEditor {
    const val BODY_MAX = 1500

    /** Variables que entiende la app al armar el mensaje. */
    val VARIABLES = listOf("negocio", "cliente", "fecha", "monto", "detalle", "saldo", "pagado_linea", "dias", "desde")

    /** Variables escritas en el texto que no existen: se avisan antes de guardar y saldrían tal cual. */
    fun unknownVariables(body: String): List<String> =
        Regex("\\{(\\w+)\\}").findAll(body).map { it.groupValues[1] }.distinct().filter { it !in VARIABLES }.toList()

    /** Datos de ejemplo para la vista previa (el nombre del negocio es el real). */
    fun sample(business: String, today: String, money: (Long) -> String): Map<String, String> = mapOf(
        "negocio" to business, "cliente" to "Marta", "fecha" to today, "monto" to money(25_000), "detalle" to "2 × Queso seco", "saldo" to money(140_250),
        "pagado_linea" to "", "dias" to "12", "desde" to today,
    )

    fun preview(body: String, sample: Map<String, String>): String = MessageTemplates.render(body, sample)

    /** El texto que rige hoy: el propio guardado o, sin él, el de fábrica. */
    fun effective(stored: String?, kind: MessageKind, locale: String): String = stored?.takeIf { it.isNotBlank() } ?: MessageTemplates.default(kind, locale)

    /** ¿Se puede guardar? Hay texto, no pasa del máximo y es distinto de lo que ya rige (guardar el de fábrica sin cambios no hace nada). */
    fun canSave(text: String, stored: String?, kind: MessageKind, locale: String): Boolean {
        val t = text.trim()
        return t.isNotEmpty() && t.length <= BODY_MAX && t != effective(stored, kind, locale).trim()
    }
}

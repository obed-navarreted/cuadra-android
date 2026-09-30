package com.cuadra.caja.domain

import com.cuadra.caja.data.remote.TicketBody

enum class TicketCategory { QUESTION, PROBLEM, SUGGESTION, BILLING }

enum class TicketError { MESSAGE_SHORT, MESSAGE_LONG, EMAIL, PHONE }

/** Lo que escribe la persona en «Escribir a soporte». */
data class TicketDraft(
    val category: TicketCategory = TicketCategory.QUESTION,
    val message: String = "",
    val email: String = "",
    val phone: String = "",
    val includeDiagnostics: Boolean = true,
)

/** Datos técnicos que ayudan a resolver un problema. NUNCA lleva tokens, PIN ni nada de las ventas o los clientes: solo lo que aquí se declara. */
data class Diagnostics(
    val appVersion: String, val androidVersion: String, val deviceModel: String, val businessId: String?, val lastSyncAt: String?, val pendingOps: Int, val language: String,
) {
    /** Lo mismo que [text], como pares «campo → valor» para mostrarlos uno por fila (un identificador largo baja de línea sin partirse). */
    fun rows(): List<Pair<String, String>> = listOf(
        "app" to appVersion, "android" to androidVersion, "device" to deviceModel, "business" to (businessId ?: "-"), "lastSync" to (lastSyncAt ?: "-"),
        "pendingOps" to pendingOps.toString(), "language" to language,
    )

    fun text(): String = buildString {
        appendLine("app=$appVersion")
        appendLine("android=$androidVersion")
        appendLine("device=$deviceModel")
        appendLine("business=${businessId ?: "-"}")
        appendLine("lastSync=${lastSyncAt ?: "-"}")
        appendLine("pendingOps=$pendingOps")
        append("language=$language")
    }
}

object SupportRules {
    const val MESSAGE_MIN = 10
    const val MESSAGE_MAX = 4000
    const val EMAIL_MAX = 200
    const val PHONE_MAX = 40

    private val EMAIL = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")

    /** Lo mínimo para que soporte pueda contestar: un mensaje de verdad y, si se da, un correo o teléfono que tengan forma de tal (igual que el panel web). */
    fun validate(d: TicketDraft): TicketError? {
        val m = d.message.trim()
        val email = d.email.trim()
        return when {
            m.length < MESSAGE_MIN -> TicketError.MESSAGE_SHORT
            m.length > MESSAGE_MAX -> TicketError.MESSAGE_LONG
            email.isNotEmpty() && (email.length > EMAIL_MAX || !EMAIL.matches(email)) -> TicketError.EMAIL
            d.phone.isNotBlank() && (d.phone.trim().length > PHONE_MAX || d.phone.count { it.isDigit() } < 6) -> TicketError.PHONE
            else -> null
        }
    }

    fun body(d: TicketDraft, businessId: String?, locale: String, diagnostics: Diagnostics?): TicketBody = TicketBody(
        category = d.category.name, message = d.message.trim(), replyToEmail = d.email.trim().ifEmpty { null }, replyToPhone = d.phone.trim().ifEmpty { null },
        diagnostics = if (d.includeDiagnostics) diagnostics?.text() else null, locale = if (locale == "en") "en" else "es", businessId = businessId,
    )

    /** Cuántos envíos seguidos admite el servidor (por hora): pasado eso responde `TOO_MANY_TICKETS`. */
    const val TOO_MANY_CODE = "TOO_MANY_TICKETS"
}

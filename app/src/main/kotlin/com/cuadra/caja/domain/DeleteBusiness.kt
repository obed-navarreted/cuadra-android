package com.cuadra.caja.domain

import java.util.Locale

/**
 * Confirmación para eliminar el negocio (solo el dueño). Tres cosas a la vez, y nada de «un toque y se fue»: escribir el nombre del negocio,
 * marcar «Entiendo que…» y pulsar el botón final. El botón solo se habilita cuando las tres cosas se cumplen y no hay una llamada en curso.
 */
object DeleteBusinessRules {
    /** Sin importar mayúsculas ni espacios de más (al inicio, al final o repetidos entre palabras). */
    fun normalize(text: String): String = text.trim().replace(Regex("\\s+"), " ").lowercase(Locale.ROOT)

    fun nameMatches(typed: String, businessName: String): Boolean = businessName.isNotBlank() && normalize(typed) == normalize(businessName)

    fun canConfirm(typed: String, businessName: String, understood: Boolean, busy: Boolean = false): Boolean =
        !busy && understood && nameMatches(typed, businessName)

    /** Solo la persona dueña ve y usa la zona de peligro (el servidor responde 403 a cualquier otra). */
    fun canDelete(role: String?): Boolean = role == TeamRules.OWNER
}

package com.cuadra.caja.domain

/** Cómo se junta lo dictado con lo que ya estaba escrito en el campo. */
object VoiceText {
    /**
     * Lo dictado se AGREGA al final (con un espacio) para poder decir un nombre en dos veces; si el campo estaba vacío empieza con mayúscula
     * ("queso seco" → "Queso seco"). Espacios de sobra se limpian.
     */
    fun merge(current: String, spoken: String): String {
        val said = spoken.trim().replace(Regex("\\s+"), " ")
        if (said.isEmpty()) return current
        val base = current.trimEnd()
        return if (base.isEmpty()) said.replaceFirstChar { it.uppercase() } else "$base $said"
    }
}

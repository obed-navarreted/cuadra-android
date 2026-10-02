package com.cuadra.caja.domain

sealed interface PhoneResult {
    /** No escribió nada. */
    data object None : PhoneResult
    data class Valid(val digits: String) : PhoneResult
    data object Invalid : PhoneResult
}

/**
 * Teléfono a formato internacional sin "+" ("50588551234"), que es lo que pide wa.me. Misma regla que el servidor (`Phones.java`):
 * un número nacional recibe el código del país del negocio; uno que ya trae su código o empieza con "+" se respeta.
 */
object PhoneNumbers {
    private val CALLING = mapOf(
        "NI" to "505", "HN" to "504", "GT" to "502", "SV" to "503", "CR" to "506", "PA" to "507", "MX" to "52", "CO" to "57", "PE" to "51",
        "EC" to "593", "CL" to "56", "AR" to "54", "DO" to "1", "ES" to "34", "US" to "1",
    )
    private val NATIONAL_LENGTH = mapOf(
        "NI" to 8, "HN" to 8, "GT" to 8, "SV" to 8, "CR" to 8, "PA" to 8, "MX" to 10, "CO" to 10, "PE" to 9, "EC" to 9, "CL" to 9, "AR" to 10,
        "DO" to 10, "ES" to 9, "US" to 10,
    )

    /** Código de país para marcar («505» para Nicaragua) o `null` si no se conoce. */
    fun callingCode(country: String?): String? = CALLING[country?.uppercase().orEmpty()]

    /** Cuántas cifras tiene un número nacional del país (sin el código), o `null` si no se conoce. */
    fun nationalLength(country: String?): Int? = NATIONAL_LENGTH[country?.uppercase().orEmpty()]

    fun normalize(raw: String?, country: String?): PhoneResult {
        if (raw.isNullOrBlank()) return PhoneResult.None
        val trimmed = raw.trim()
        val international = trimmed.startsWith("+") || trimmed.startsWith("00")
        var digits = trimmed.filter { it.isDigit() }
        if (trimmed.startsWith("00")) digits = digits.drop(2)
        if (digits.isEmpty()) return PhoneResult.Invalid
        val c = country?.uppercase().orEmpty()
        val code = CALLING[c]
        val national = NATIONAL_LENGTH[c]
        if (!international && code != null && national != null) {
            val alreadyHasCode = digits.startsWith(code) && digits.length >= code.length + national
            if (!alreadyHasCode && digits.length <= national) digits = code + digits
        }
        return if (digits.length in 8..15) PhoneResult.Valid(digits) else PhoneResult.Invalid
    }
}

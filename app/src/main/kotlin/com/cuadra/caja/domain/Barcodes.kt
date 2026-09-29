package com.cuadra.caja.domain

object Barcodes {
    /**
     * Un UPC-A de 12 dígitos y su EAN-13 con cero inicial son el mismo código, y según el lector llega uno u otro:
     * se prueban las dos formas (mismo criterio que el servidor). Cualquier otro código se busca tal cual.
     */
    fun forms(raw: String): List<String> {
        val code = raw.trim()
        val out = mutableListOf(code)
        if (code.matches(Regex("\\d{12}"))) out += "0$code"
        if (code.matches(Regex("0\\d{12}"))) out += code.substring(1)
        return out
    }
}

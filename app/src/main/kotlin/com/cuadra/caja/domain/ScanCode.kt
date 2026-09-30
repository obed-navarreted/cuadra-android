package com.cuadra.caja.domain

/** Resultado de buscar un código leído en el catálogo. `T` es el producto (en pruebas, cualquier cosa). */
sealed interface ScanResolution<out T> {
    /** No llegó nada útil (vacío o solo espacios): no se busca. */
    data object Empty : ScanResolution<Nothing>
    data class Found<T>(val code: String, val item: T) : ScanResolution<T>

    /** `checksumOk = false`: parece EAN/UPC pero el dígito de control no cuadra (lectura dudosa o código mal tecleado). */
    data class NotFound(val code: String, val checksumOk: Boolean) : ScanResolution<Nothing>
}

/** Lógica pura del lector de códigos: limpiar lo leído, validar EAN/UPC con tolerancia y resolverlo contra el catálogo. */
object ScanCode {
    /** Quita espacios, saltos de línea y caracteres de control; un código solo de dígitos con espacios internos ("7441 0016") se junta. */
    fun normalize(raw: String): String {
        val clean = raw.filter { !it.isISOControl() && it != '\u200B' && it != '\uFEFF' }.trim()
        return if (clean.matches(Regex("[0-9 ]+"))) clean.replace(" ", "") else clean
    }

    /** Dígito de control de EAN-13 / UPC-A (12) / EAN-8 (8). Cualquier otro largo o texto con letras: no aplica (null). */
    fun checksumValid(code: String): Boolean? {
        if (code.length !in setOf(8, 12, 13) || !code.all { it in '0'..'9' }) return null
        val digits = code.map { it - '0' }
        // De derecha a izquierda, sin el dígito de control: pesos 3, 1, 3, 1…
        val sum = digits.dropLast(1).reversed().mapIndexed { i, d -> if (i % 2 == 0) d * 3 else d }.sum()
        return (10 - sum % 10) % 10 == digits.last()
    }

    /** Formato por la forma del código cuando el lector no lo dice (teclado): 13 dígitos = EAN-13, etc. Desconocido: null. */
    fun guessFormat(code: String): String? = if (!code.all { it in '0'..'9' }) null else when (code.length) {
        13 -> "EAN-13"
        12 -> "UPC-A"
        8 -> "EAN-8"
        else -> null
    }

    /**
     * Busca el código con `lookup` (que recibe el código ya limpio y prueba las formas UPC-A/EAN-13 por su cuenta).
     * Un dígito de control malo NO impide la búsqueda: hay catálogos con códigos internos con forma de EAN; solo se avisa en `NotFound`.
     */
    suspend fun <T> resolve(raw: String, lookup: suspend (String) -> T?): ScanResolution<T> {
        val code = normalize(raw)
        if (code.isEmpty()) return ScanResolution.Empty
        val hit = lookup(code)
        return if (hit != null) ScanResolution.Found(code, hit) else ScanResolution.NotFound(code, checksumValid(code) != false)
    }
}

/** Evita que el mismo código, visto en varios cuadros seguidos, se agregue dos veces: se ignora el mismo código dentro de `windowMillis`. */
class ScanDebounce(private val windowMillis: Long = ScanTiming.DEBOUNCE_MS) {
    private var last: String? = null
    private var lastAt = Long.MIN_VALUE

    fun accept(code: String, nowMillis: Long): Boolean {
        if (code == last && nowMillis - lastAt < windowMillis) return false
        last = code
        lastAt = nowMillis
        return true
    }
}

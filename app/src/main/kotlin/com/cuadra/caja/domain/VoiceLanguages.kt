package com.cuadra.caja.domain

/**
 * Cadena de idiomas y reintentos del dictado (pura, sin Android). El reconocedor a veces dice «idioma no soportado/no disponible» porque se le pidió una
 * variante regional que no tiene (p. ej. `es-NI`) o porque se pidió el modelo sin conexión sin haberlo descargado. Por eso: primero el reconocedor
 * NORMAL (con internet), el del propio teléfono solo si no hay red, y una lista de variantes que se prueban una tras otra antes de mostrar un error.
 */
object VoiceLanguages {
    private val REGIONAL_FALLBACKS = mapOf(
        "es" to listOf("es-419", "es-US", "es-ES", "es"),
        "en" to listOf("en-US", "en-GB", "en"),
    )

    /**
     * Etiquetas a probar, en orden, a partir de la del teléfono/app (`es-NI`): esa misma → `es-419` → `es-US` → `es-ES` → `es`
     * (inglés: `en-US` → `en-GB` → `en`). Sin repetidas. Otro idioma: la etiqueta y su idioma base.
     */
    fun chain(localeTag: String): List<String> {
        val tag = localeTag.trim().replace('_', '-').ifEmpty { return emptyList() }
        val lang = tag.substringBefore('-').lowercase()
        val fallbacks = REGIONAL_FALLBACKS[lang] ?: listOf(lang)
        return (listOf(tag) + fallbacks).distinctBy { it.lowercase() }
    }
}

/** Con qué reconocedor se intenta: el normal (necesita internet) o el que va dentro del teléfono (sin internet). */
enum class VoiceEngine { STANDARD, ON_DEVICE }

data class VoiceAttempt(val tag: String, val engine: VoiceEngine)

/** Lo que el teléfono tiene ahora: si hay red y qué reconocedores existen. */
data class VoiceEnv(val hasNetwork: Boolean, val standardAvailable: Boolean = true, val onDeviceAvailable: Boolean = false)

/**
 * Un dictado en curso: el intento actual y los ya probados. `after(error, env)` decide qué probar cuando falla un intento:
 * - idioma no soportado / no disponible / error del cliente → la siguiente etiqueta de la cadena, con el mismo reconocedor;
 * - error de red con el reconocedor normal → el del teléfono (una sola vez, desde la primera etiqueta) si existe;
 * - cualquier otra cosa, o si no queda nada por probar → `null` (mostrar el error con lo que se probó en [triedTags]).
 */
data class VoicePlan(val chain: List<String>, val current: VoiceAttempt, val tried: List<VoiceAttempt> = listOf(current)) {
    val triedTags: List<String> get() = tried.map { it.tag }.distinct()

    fun after(error: VoiceError, env: VoiceEnv): VoicePlan? {
        val retryableLanguage = error == VoiceError.LANGUAGE_NOT_SUPPORTED || error == VoiceError.LANGUAGE_UNAVAILABLE || error == VoiceError.CLIENT
        if (retryableLanguage) {
            val i = chain.indexOf(current.tag)
            chain.getOrNull(i + 1)?.let { return advance(VoiceAttempt(it, current.engine)) }
            return null
        }
        if (error == VoiceError.NETWORK && current.engine == VoiceEngine.STANDARD && env.onDeviceAvailable && tried.none { it.engine == VoiceEngine.ON_DEVICE }) {
            val first = chain.firstOrNull() ?: return null
            return advance(VoiceAttempt(first, VoiceEngine.ON_DEVICE))
        }
        return null
    }

    private fun advance(next: VoiceAttempt) = copy(current = next, tried = tried + next)

    companion object {
        /** El primer intento: el normal en la etiqueta del teléfono; el del teléfono solo si NO hay red (y existe) o si el normal no existe. */
        fun start(chain: List<String>, env: VoiceEnv): VoicePlan {
            val tags = chain.ifEmpty { listOf("en") }
            val engine = if ((!env.hasNetwork && env.onDeviceAvailable) || (!env.standardAvailable && env.onDeviceAvailable)) VoiceEngine.ON_DEVICE else VoiceEngine.STANDARD
            return VoicePlan(tags, VoiceAttempt(tags.first(), engine))
        }
    }
}

package com.cuadra.caja.domain

/** Versión numérica de la app ("1.2.3"). Los sufijos ("-debug", "+build", "-rc1") se ignoran; lo no numérico no es una versión. */
object AppVersion {
    private val numeric = Regex("^\\s*[vV]?(\\d+(?:\\.\\d+)*)")

    fun parse(raw: String?): List<Int>? {
        val m = numeric.find(raw.orEmpty()) ?: return null
        return m.groupValues[1].split('.').map { it.toIntOrNull() ?: return null }
    }

    /** <0 si a es más vieja que b, 0 si iguales, >0 si más nueva. Largos distintos se rellenan con ceros ("1.2" == "1.2.0"). Nulo si alguna no se puede leer. */
    fun compare(a: String?, b: String?): Int? {
        val x = parse(a) ?: return null
        val y = parse(b) ?: return null
        for (i in 0 until maxOf(x.size, y.size)) {
            val c = x.getOrElse(i) { 0 }.compareTo(y.getOrElse(i) { 0 })
            if (c != 0) return c
        }
        return 0
    }
}

/** Lo que la configuración del servidor pide a esta versión de la app. */
sealed interface UpdateNeed {
    data object None : UpdateNeed
    /** Bloqueo: la versión instalada es menor que la mínima. */
    data class Required(val minVersion: String) : UpdateNeed
    /** Aviso descartable (una vez por versión recomendada). */
    data class Recommended(val version: String) : UpdateNeed
}

object AppUpdate {
    /**
     * Regla única: sin requisito (nulo/vacío/ilegible) o con versión instalada ilegible NUNCA se bloquea: la caja no se detiene por una configuración rara.
     * `dismissedRecommended` es la versión recomendada cuyo aviso la persona ya descartó.
     */
    fun need(current: String?, min: String?, recommended: String?, dismissedRecommended: String? = null): UpdateNeed {
        if (AppVersion.parse(current) == null) return UpdateNeed.None
        val belowMin = AppVersion.compare(current, min)?.let { it < 0 } ?: false
        if (belowMin) return UpdateNeed.Required(min!!.trim())
        val belowRec = AppVersion.compare(current, recommended)?.let { it < 0 } ?: false
        if (belowRec && recommended!!.trim() != dismissedRecommended?.trim()) return UpdateNeed.Recommended(recommended.trim())
        return UpdateNeed.None
    }

    /** Se vuelve a pedir la configuración si nunca se obtuvo o pasó el intervalo. */
    fun shouldRefresh(lastSuccessAt: Long?, now: Long, intervalMillis: Long): Boolean =
        lastSuccessAt == null || now - lastSuccessAt >= intervalMillis || now < lastSuccessAt

    /** Un anuncio se muestra si existe, tiene id y la persona no lo descartó. */
    fun announcementVisible(id: String?, dismissedId: String?): Boolean = !id.isNullOrBlank() && id != dismissedId

    /** Solo los enlaces propios de la app se abren; cualquier otro texto solo cierra el anuncio. */
    fun isAppLink(deepLink: String?): Boolean = deepLink?.trim()?.startsWith("cuadra://") == true
}

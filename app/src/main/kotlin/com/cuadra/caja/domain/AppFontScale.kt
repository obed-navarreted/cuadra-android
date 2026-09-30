package com.cuadra.caja.domain

/** Cómo la app usa el tamaño de letra del teléfono (Más › Tamaño de letra). Es una preferencia de ESTE teléfono. */
enum class FontSizeChoice {
    /** Por omisión: sigue la letra del teléfono, pero sin pasar de [AppFontScale.AUTO_CAP] (así una letra enorme del sistema no vuelve gigante toda la app). */
    AUTO,
    /** 0.9×, sin importar el teléfono. */
    SMALL,
    /** 1.0×, sin importar el teléfono. */
    NORMAL,
    /** 1.15×, sin importar el teléfono. */
    LARGE,
    /** Toda la letra del teléfono, aunque sea 2×: para quien necesita letra grande (accesibilidad). */
    SYSTEM;

    companion object {
        fun parse(raw: String?): FontSizeChoice = entries.firstOrNull { it.name == raw } ?: AUTO
    }
}

/** La escala de letra que la app aplica de verdad: función de la escala del sistema y de la elección de la persona. Pura (sin Android). */
object AppFontScale {
    const val AUTO_CAP = 1.15f
    const val SMALL = 0.9f
    const val NORMAL = 1.0f
    const val LARGE = 1.15f

    /** Rango que se acepta del sistema (un valor fuera de esto es un dato roto o un extremo absurdo). */
    private const val SYSTEM_MIN = 0.5f
    private const val SYSTEM_MAX = 3.0f

    /** `NaN`, infinito, cero o negativo se toman como 1.0. */
    fun sanitize(system: Float): Float = if (system.isFinite() && system > 0f) system.coerceIn(SYSTEM_MIN, SYSTEM_MAX) else 1f

    fun effective(system: Float, choice: FontSizeChoice): Float {
        val sys = sanitize(system)
        return when (choice) {
            FontSizeChoice.AUTO -> minOf(sys, AUTO_CAP)
            FontSizeChoice.SMALL -> SMALL
            FontSizeChoice.NORMAL -> NORMAL
            FontSizeChoice.LARGE -> LARGE
            FontSizeChoice.SYSTEM -> sys
        }
    }
}

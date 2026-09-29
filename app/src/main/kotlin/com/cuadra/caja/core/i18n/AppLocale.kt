package com.cuadra.caja.core.i18n

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/** Idioma de la app, por persona/dispositivo. Automático = el del sistema. No cambia la moneda del negocio. */
enum class AppLanguage(val tag: String?) {
    AUTOMATIC(null), SPANISH("es"), ENGLISH("en");

    companion object {
        fun fromTag(tag: String): AppLanguage = entries.firstOrNull { it.tag == tag } ?: AUTOMATIC
    }
}

object AppLocale {
    fun current(): AppLanguage {
        val tags = AppCompatDelegate.getApplicationLocales().toLanguageTags()
        return AppLanguage.fromTag(tags.substringBefore(',').substringBefore('-'))
    }

    /** Android recrea la actividad con el idioma nuevo: no se reinicia la app ni se pierde la sesión. */
    fun set(language: AppLanguage) {
        val locales = language.tag?.let { LocaleListCompat.forLanguageTags(it) } ?: LocaleListCompat.getEmptyLocaleList()
        AppCompatDelegate.setApplicationLocales(locales)
    }
}

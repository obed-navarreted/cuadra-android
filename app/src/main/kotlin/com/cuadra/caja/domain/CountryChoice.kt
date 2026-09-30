package com.cuadra.caja.domain

/** Un país con la moneda, zona horaria e idioma que sugiere al crear el negocio (todo editable). */
data class CountryOption(val code: String, val currency: String, val timezone: String, val locale: String = "es")

/** Lo elegido al crear el negocio. */
data class BusinessOrigin(val country: String, val currency: String, val timezone: String)

/**
 * País → moneda y zona sugeridas al crear el negocio (la misma tabla que el servidor, `CountryDefaults`, que manda cuando hay conexión). La sugerencia
 * inicial sale de la zona horaria del teléfono o, si no coincide ninguna, de la región de su idioma; si nada coincide, Nicaragua.
 */
object CountryChoice {
    val FALLBACK = listOf(
        CountryOption("AR", "ARS", "America/Argentina/Buenos_Aires"), CountryOption("BO", "BOB", "America/La_Paz"), CountryOption("CL", "CLP", "America/Santiago"),
        CountryOption("CO", "COP", "America/Bogota"), CountryOption("CR", "CRC", "America/Costa_Rica"), CountryOption("DO", "DOP", "America/Santo_Domingo"),
        CountryOption("EC", "USD", "America/Guayaquil"), CountryOption("ES", "EUR", "Europe/Madrid"), CountryOption("GT", "GTQ", "America/Guatemala"),
        CountryOption("HN", "HNL", "America/Tegucigalpa"), CountryOption("MX", "MXN", "America/Mexico_City"), CountryOption("NI", "NIO", "America/Managua"),
        CountryOption("PA", "USD", "America/Panama"), CountryOption("PE", "PEN", "America/Lima"), CountryOption("PR", "USD", "America/Puerto_Rico"),
        CountryOption("PY", "PYG", "America/Asuncion"), CountryOption("SV", "USD", "America/El_Salvador"), CountryOption("US", "USD", "America/New_York", "en"),
        CountryOption("UY", "UYU", "America/Montevideo"), CountryOption("VE", "VES", "America/Caracas"),
    )

    /** El país sugerido: el de la región del teléfono si está en la lista; si no, el de su zona horaria; si no, Nicaragua (o el primero). */
    fun suggested(options: List<CountryOption>, phoneRegion: String?, phoneZone: String?): CountryOption {
        val list = options.ifEmpty { FALLBACK }
        // La zona horaria dice dónde ESTÁ el teléfono; la región del idioma suele quedar en «US» de fábrica.
        return list.firstOrNull { it.timezone == phoneZone }
            ?: list.firstOrNull { it.code.equals(phoneRegion, ignoreCase = true) }
            ?: list.firstOrNull { it.code == "NI" } ?: list.first()
    }

    /** Lo que queda elegido al tomar un país: su moneda y su zona. */
    fun origin(c: CountryOption) = BusinessOrigin(c.code, c.currency, c.timezone)

    /** Monedas para elegir: la del país, el dólar (muchos negocios de la región venden en dólares) y la elegida ahora. */
    fun currencies(c: CountryOption, current: String?): List<String> = listOfNotNull(c.currency, "USD", current).distinct()

    /** Zonas para elegir: la del país y la del teléfono (si es otra y es válida). */
    fun timezones(c: CountryOption, phoneZone: String?): List<String> =
        listOfNotNull(c.timezone, phoneZone?.takeIf { BusinessSettingsRules.isZone(it) }).distinct()

    /** Nombre del país en el idioma de la pantalla (lo sabe Java); si no, el código. */
    fun name(code: String, locale: java.util.Locale): String =
        java.util.Locale.Builder().setRegion(code).build().getDisplayCountry(locale).ifBlank { code }
}

/** La consola de la plataforma vive en el panel web; la app solo la abre en el navegador. */
object PlatformConsole {
    /** `panelUrl/console`, o nulo si no se conoce el panel o no es una dirección web. */
    fun url(panelUrl: String?): String? {
        val base = panelUrl?.trim()?.trimEnd('/')?.takeIf { it.startsWith("https://") || it.startsWith("http://") } ?: return null
        return "$base/console"
    }
}

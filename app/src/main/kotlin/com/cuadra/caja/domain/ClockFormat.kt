package com.cuadra.caja.domain

import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.FormatStyle
import java.time.temporal.ChronoField
import java.util.Locale

/**
 * Las horas que se le muestran a una persona: siempre de 12 horas con AM/PM, solo horas y minutos, sin segundos.
 * En todos los idiomas: «3:18 PM», «12:05 PM», «12:00 AM». No depende de lo que diga ICU/CLDR en cada teléfono (es-NI, es-ES y en-US difieren).
 * Es el ÚNICO lugar donde se arma una hora para pantalla, recibos o textos; las fechas siguen el formato corto o medio del idioma.
 */
object ClockFormat {
    const val AM = "AM"
    const val PM = "PM"
    fun marker(pm: Boolean): String = if (pm) PM else AM

    private fun DateTimeFormatterBuilder.appendClock(locale: Locale): DateTimeFormatterBuilder =
        appendValue(ChronoField.CLOCK_HOUR_OF_AMPM).appendLiteral(':').appendValue(ChronoField.MINUTE_OF_HOUR, 2).appendLiteral(' ')
            .appendText(ChronoField.AMPM_OF_DAY, mapOf(0L to AM, 1L to PM))

    /** Solo la hora: «3:18 PM». */
    fun time(locale: Locale, zone: ZoneId? = null): DateTimeFormatter =
        DateTimeFormatterBuilder().appendClock(locale).toFormatter(locale).let { if (zone != null) it.withZone(zone) else it }

    /** Fecha corta del idioma y hora: «1/10/26, 3:18 PM». */
    fun dateTime(locale: Locale, zone: ZoneId? = null): DateTimeFormatter =
        DateTimeFormatterBuilder().appendLocalized(FormatStyle.SHORT, null).appendLiteral(", ").appendClock(locale).toFormatter(locale).let { if (zone != null) it.withZone(zone) else it }

    /** «02:00» o «21:30:00» (horas de configuración) → «2:00 AM», «9:30 PM». Si no es una hora, se devuelve tal cual. */
    fun hm(text: String?): String {
        val m = Regex("^(\\d{1,2}):(\\d{2})").find(text.orEmpty()) ?: return text.orEmpty()
        val h = m.groupValues[1].toInt()
        if (h > 23) return text.orEmpty()
        return "${if (h % 12 == 0) 12 else h % 12}:${m.groupValues[2]} ${marker(h >= 12)}"
    }
}

package com.cuadra.caja.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Los atajos del selector de rango (Resumen, Ventas, Gastos, Cierre del día). Mismos seis que la web, más «personalizado». */
enum class RangePreset { TODAY, YESTERDAY, LAST_7, LAST_30, THIS_MONTH, LAST_MONTH, CUSTOM }

/** Lo que la persona eligió: un atajo o, con `CUSTOM`, sus dos fechas (días de NEGOCIO, ambos incluidos). */
data class RangeChoice(val preset: RangePreset = RangePreset.TODAY, val custom: Pair<LocalDate, LocalDate>? = null)

/** Una elección ya convertida a días de negocio y a su ventana exacta [startMillis, endMillis). `inProgress`: el último día es el de hoy, aún no termina. */
data class ResolvedRange(
    val preset: RangePreset, val from: LocalDate, val to: LocalDate, val startMillis: Long, val endMillis: Long, val inProgress: Boolean,
) {
    val days: Int get() = (java.time.temporal.ChronoUnit.DAYS.between(from, to) + 1).toInt()
}

object RangePresets {
    val ALL = listOf(RangePreset.TODAY, RangePreset.YESTERDAY, RangePreset.LAST_7, RangePreset.LAST_30, RangePreset.THIS_MONTH, RangePreset.LAST_MONTH)

    /** El día de negocio de hoy: «Hoy» a las 00:30 sigue siendo la jornada que empezó ayer si aún no llegó el corte. */
    fun today(calendar: BusinessCalendar, nowMillis: Long): LocalDate = calendar.dateOf(nowMillis)

    /** Los días (primero, último) de un atajo. `CUSTOM` sin fechas cae en «Hoy». */
    fun days(choice: RangeChoice, calendar: BusinessCalendar, nowMillis: Long): Pair<LocalDate, LocalDate> {
        val today = today(calendar, nowMillis)
        return when (choice.preset) {
            RangePreset.TODAY -> today to today
            RangePreset.YESTERDAY -> today.minusDays(1) to today.minusDays(1)
            RangePreset.LAST_7 -> today.minusDays(6) to today
            RangePreset.LAST_30 -> today.minusDays(29) to today
            RangePreset.THIS_MONTH -> today.withDayOfMonth(1) to today
            RangePreset.LAST_MONTH -> {
                val lastOfPrevious = today.withDayOfMonth(1).minusDays(1)
                lastOfPrevious.withDayOfMonth(1) to lastOfPrevious
            }
            RangePreset.CUSTOM -> choice.custom ?: (today to today)
        }
    }

    fun resolve(choice: RangeChoice, calendar: BusinessCalendar, nowMillis: Long): ResolvedRange {
        val today = today(calendar, nowMillis)
        val (from, to) = days(choice, calendar, nowMillis)
        val last = if (to.isAfter(today)) today else to
        val first = if (from.isAfter(last)) last else from
        return ResolvedRange(choice.preset, first, last, calendar.startMillis(first), calendar.endMillis(last), inProgress = last == today)
    }
}

/** Texto exacto de una ventana: «28 sep 2:00 AM → 29 sep 2:00 AM» (zona y corte del NEGOCIO, idioma de la app). */
object RangeWindowText {
    /** Un instante: «28 sep 2:00 AM» (con año si no es el de `nowMillis`). Las horas en el idioma pedido; espacios normales para que pueda bajar de línea. */
    fun point(millis: Long, zone: ZoneId, locale: Locale, withYear: Boolean): String {
        val date = DateTimeFormatter.ofPattern(if (withYear) "d MMM yyyy" else "d MMM", locale).withZone(zone).format(Instant.ofEpochMilli(millis)).replace(".", "")
        val time = ClockFormat.time(locale, zone).format(Instant.ofEpochMilli(millis))
        return "$date $time".replace(Regex("[\\u00a0\\u202f]"), " ")
    }

    /** (inicio, fin) de la ventana; el fin es null si está en curso (la pantalla escribe «en curso»). */
    fun parts(range: ResolvedRange, calendar: BusinessCalendar, nowMillis: Long, locale: Locale): Pair<String, String?> {
        val zone = calendar.zone
        val nowYear = Instant.ofEpochMilli(nowMillis).atZone(zone).year
        val startYear = Instant.ofEpochMilli(range.startMillis).atZone(zone).year
        val endYear = Instant.ofEpochMilli(range.endMillis).atZone(zone).year
        val withYear = startYear != nowYear || endYear != nowYear
        val start = point(range.startMillis, zone, locale, withYear)
        return start to if (range.inProgress) null else point(range.endMillis, zone, locale, withYear)
    }
}

package com.cuadra.caja.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit

sealed interface RangeResult {
    /** Rango válido: días de negocio `from`..`to` incluidos; ventana [startMillis, endMillis). */
    data class Ok(val from: LocalDate, val to: LocalDate, val startMillis: Long, val endMillis: Long) : RangeResult {
        val days: Int get() = (ChronoUnit.DAYS.between(from, to) + 1).toInt()
    }

    /** "Desde" es posterior a "hasta". */
    data object Reversed : RangeResult

    /** "Desde" es posterior al día de negocio de hoy: aún no hay nada que sumar. */
    data object InFuture : RangeResult

    /** Más de `BusinessRange.MAX_DAYS` días. */
    data class TooLong(val days: Int) : RangeResult
}

/**
 * Rango de días de NEGOCIO (con su historial de zona y corte, ADR 0011), no del calendario del teléfono. Los preajustes del resumen (hoy, 7 días, mes)
 * y el rango personalizado usan esta misma conversión, así sus números coinciden para los mismos días.
 */
object BusinessRange {
    const val MAX_DAYS = 366

    /** `today` es el día de negocio actual; si se da, un "hasta" posterior se recorta a hoy y un "desde" posterior se rechaza. */
    fun resolve(from: LocalDate, to: LocalDate, calendar: BusinessCalendar, today: LocalDate? = null): RangeResult {
        if (from.isAfter(to)) return RangeResult.Reversed
        if (today != null && from.isAfter(today)) return RangeResult.InFuture
        val last = if (today != null && to.isAfter(today)) today else to
        val days = ChronoUnit.DAYS.between(from, last) + 1
        if (days > MAX_DAYS) return RangeResult.TooLong(days.toInt())
        return RangeResult.Ok(from, last, calendar.startMillis(from), calendar.endMillis(last))
    }
}

package com.cuadra.caja.domain

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Jornada comercial: cambia a la hora de corte del negocio (por defecto 02:00), no a medianoche. Igual que en el servidor
 * (`BusinessDayService`): una venta a la 1:30 a. m. pertenece al día anterior.
 */
data class BusinessDay(val date: LocalDate, val startMillis: Long, val endMillis: Long) {
    companion object {
        fun of(instant: Instant, zone: ZoneId, cutoff: LocalTime): BusinessDay {
            val local = instant.atZone(zone).toLocalDateTime().minusHours(cutoff.hour.toLong()).minusMinutes(cutoff.minute.toLong())
            return forDate(local.toLocalDate(), zone, cutoff)
        }

        fun forDate(date: LocalDate, zone: ZoneId, cutoff: LocalTime) = BusinessDay(
            date,
            date.atTime(cutoff).atZone(zone).toInstant().toEpochMilli(),
            date.plusDays(1).atTime(cutoff).atZone(zone).toInstant().toEpochMilli(),
        )
    }
}

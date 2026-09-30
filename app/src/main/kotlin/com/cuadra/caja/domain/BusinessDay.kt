package com.cuadra.caja.domain

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Jornada comercial: cambia a la hora de corte del negocio (por defecto 02:00), no a medianoche. Una venta a la 1:30 a. m. pertenece al día anterior.
 * Con una sola regla; para el historial de reglas del negocio usa [BusinessCalendar] (misma cuenta que el servidor).
 */
data class BusinessDay(val date: LocalDate, val startMillis: Long, val endMillis: Long) {
    companion object {
        fun of(instant: Instant, zone: ZoneId, cutoff: LocalTime): BusinessDay = BusinessCalendar(zone, cutoff).let { it.day(it.dateOf(instant)) }

        fun forDate(date: LocalDate, zone: ZoneId, cutoff: LocalTime): BusinessDay = BusinessCalendar(zone, cutoff).day(date)
    }
}

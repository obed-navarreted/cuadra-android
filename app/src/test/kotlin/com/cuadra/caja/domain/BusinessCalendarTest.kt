package com.cuadra.caja.domain

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Los MISMOS casos que `BusinessDayRulesTest` del servidor (Java) y que `business_date()` de SQL: una sola regla de jornada en tres sitios (ADR 0011).
 * La zona del teléfono no interviene: todo se decide con las reglas del negocio.
 */
class BusinessCalendarTest {
    private val managua = ZoneId.of("America/Managua")
    private val bogota = ZoneId.of("America/Bogota")
    private fun at(zone: ZoneId, local: String): Instant = LocalDateTime.parse(local).atZone(zone).toInstant()
    private fun d(y: Int, m: Int, day: Int) = LocalDate.of(y, m, day)

    @Test fun oneRuleKeepsTheTwoAmCutoff() {
        val cal = BusinessCalendar(managua, LocalTime.of(2, 0))
        assertEquals(d(2026, 9, 28), cal.dateOf(at(managua, "2026-09-29T01:30:00")))
        assertEquals(d(2026, 9, 29), cal.dateOf(at(managua, "2026-09-29T02:00:00")))
        assertEquals(at(managua, "2026-09-29T02:00:00"), cal.startOf(d(2026, 9, 29)))
        assertEquals(at(managua, "2026-09-30T02:00:00"), cal.endOf(d(2026, 9, 29)))
    }

    @Test fun changingTheCutoffOnlyAffectsDaysFromTheEffectiveDate() {
        val cal = BusinessCalendar(listOf(DayRule(BusinessCalendar.SINCE_FOREVER, managua, LocalTime.of(2, 0)), DayRule(d(2026, 10, 10), managua, LocalTime.of(4, 0))))
        // Antes del cambio, todo igual que antes.
        assertEquals(d(2026, 10, 5), cal.dateOf(at(managua, "2026-10-05T03:00:00")))
        assertEquals(at(managua, "2026-10-05T02:00:00"), cal.startOf(d(2026, 10, 5)))
        // El último día viejo dura hasta que empieza el primero nuevo (una sola vez, 26 h), sin hueco ni traslape.
        assertEquals(at(managua, "2026-10-10T04:00:00"), cal.endOf(d(2026, 10, 9)))
        assertEquals(cal.endOf(d(2026, 10, 9)), cal.startOf(d(2026, 10, 10)))
        assertEquals(d(2026, 10, 9), cal.dateOf(at(managua, "2026-10-10T03:59:00")))
        assertEquals(d(2026, 10, 10), cal.dateOf(at(managua, "2026-10-10T04:00:00")))
        // Después, el corte nuevo.
        assertEquals(d(2026, 10, 10), cal.dateOf(at(managua, "2026-10-11T03:00:00")))
        assertEquals(d(2026, 10, 11), cal.dateOf(at(managua, "2026-10-11T04:00:00")))
    }

    @Test fun changingTheTimeZoneKeepsDaysContiguousOverTwoWeeksInHalfHourSteps() {
        val cal = BusinessCalendar(listOf(DayRule(BusinessCalendar.SINCE_FOREVER, managua, LocalTime.of(2, 0)), DayRule(d(2026, 10, 10), bogota, LocalTime.of(2, 0))))
        val boundary = at(bogota, "2026-10-10T02:00:00")
        assertEquals(d(2026, 10, 9), cal.dateOf(boundary.minusSeconds(1)))
        assertEquals(d(2026, 10, 10), cal.dateOf(boundary))
        var t = boundary.minusSeconds(7 * 86400L)
        var checked = 0
        while (t.isBefore(boundary.plusSeconds(7 * 86400L))) {
            val day = cal.dateOf(t)
            assertTrue("$t antes del inicio de $day", !t.isBefore(cal.startOf(day)))
            assertTrue("$t después del fin de $day", t.isBefore(cal.endOf(day)))
            assertEquals(cal.endOf(day), cal.startOf(day.plusDays(1)))
            t = t.plusSeconds(1800)
            checked++
        }
        assertEquals(14 * 48, checked)
        assertEquals(d(2026, 10, 10), cal.pendingFrom(boundary.minusSeconds(3600)))
        assertNull(cal.pendingFrom(boundary))
    }

    @Test fun theCalendarNeverUsesThePhoneTimeZone() {
        val saved = java.util.TimeZone.getDefault()
        try {
            val cal = BusinessCalendar(managua, LocalTime.of(2, 0))
            val instant = at(managua, "2026-09-29T01:00:00")
            for (phone in listOf("Asia/Tokyo", "Pacific/Auckland", "America/Los_Angeles")) {
                java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone(phone))
                assertEquals("con el teléfono en $phone", d(2026, 9, 28), cal.dateOf(instant))
                assertEquals(at(managua, "2026-09-28T02:00:00").toEpochMilli(), cal.startMillis(d(2026, 9, 28)))
            }
        } finally {
            java.util.TimeZone.setDefault(saved)
        }
    }

    @Test fun rulesComeFromTheApiJsonAndFallBackToTheBusinessOwnZoneAndCutoff() {
        val json = BusinessCalendar.toJson(listOf(DayRuleDto("1970-01-01", "America/Managua", "02:00"), DayRuleDto("2026-10-10", "America/Managua", "04:00")))
        val cal = BusinessCalendar.fromJson(json, "America/Bogota", "05:00")
        assertEquals(2, cal.rules.size)
        assertEquals(LocalTime.of(4, 0), cal.cutoff)
        // Sin reglas (teléfono con datos anteriores a la migración): una sola regla desde siempre con lo del propio negocio.
        val old = BusinessCalendar.fromJson("[]", "America/Bogota", "05:00")
        assertEquals(1, old.rules.size)
        assertEquals(bogota, old.zone)
        assertEquals(LocalTime.of(5, 0), old.cutoff)
        assertEquals(1, BusinessCalendar.fromJson("no es json", "America/Managua", "02:00").rules.size)
    }

    @Test fun rulesArrivingOutOfOrderAreSorted() {
        val cal = BusinessCalendar(listOf(DayRule(d(2026, 10, 10), managua, LocalTime.of(4, 0)), DayRule(BusinessCalendar.SINCE_FOREVER, managua, LocalTime.of(2, 0))))
        assertEquals(LocalTime.of(4, 0), cal.cutoff)
        assertEquals(d(2026, 10, 9), cal.dateOf(at(managua, "2026-10-10T03:00:00")))
    }
}

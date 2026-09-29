package com.cuadra.caja.domain

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class BusinessDayTest {
    private val managua = ZoneId.of("America/Managua")   // UTC-6, sin horario de verano
    private val cutoff = LocalTime.of(2, 0)

    // Los mismos casos que valida el servidor (SaleTest.theBusinessDayChangesAtTheCutoffNotAtMidnight).
    @Test fun aSaleAt0130BelongsToThePreviousDay() {
        assertEquals(LocalDate.of(2026, 9, 19), BusinessDay.of(Instant.parse("2026-09-20T07:30:00Z"), managua, cutoff).date)
    }

    @Test fun aSaleAt0230StartsTheNewDay() {
        assertEquals(LocalDate.of(2026, 9, 20), BusinessDay.of(Instant.parse("2026-09-20T08:30:00Z"), managua, cutoff).date)
    }

    @Test fun theDayIsA24HourWindowStartingAtTheCutoff() {
        val day = BusinessDay.forDate(LocalDate.of(2026, 9, 20), managua, cutoff)
        assertEquals(Instant.parse("2026-09-20T08:00:00Z").toEpochMilli(), day.startMillis)
        assertEquals(Instant.parse("2026-09-21T08:00:00Z").toEpochMilli(), day.endMillis)
    }

    @Test fun exactlyAtTheCutoffBelongsToTheNewDay() {
        assertEquals(LocalDate.of(2026, 9, 20), BusinessDay.of(Instant.parse("2026-09-20T08:00:00Z"), managua, cutoff).date)
        assertEquals(LocalDate.of(2026, 9, 19), BusinessDay.of(Instant.parse("2026-09-20T07:59:59Z"), managua, cutoff).date)
    }

    @Test fun aMidnightCutoffBehavesLikeACalendarDay() {
        assertEquals(LocalDate.of(2026, 9, 20), BusinessDay.of(Instant.parse("2026-09-20T06:30:00Z"), managua, LocalTime.MIDNIGHT).date)
    }
}

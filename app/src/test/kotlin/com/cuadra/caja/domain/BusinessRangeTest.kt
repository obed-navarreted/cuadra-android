package com.cuadra.caja.domain

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BusinessRangeTest {
    private val zone = ZoneId.of("America/Managua")
    private val cutoff = LocalTime.of(2, 0)
    private val calendar = BusinessCalendar(zone, cutoff)
    private fun d(s: String) = LocalDate.parse(s)
    private fun ok(from: String, to: String, today: LocalDate? = null) = BusinessRange.resolve(d(from), d(to), calendar, today) as RangeResult.Ok
    private fun millis(date: String, hour: Int) = d(date).atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()

    @Test fun singleDayIsFromCutoffToNextCutoff() {
        val r = ok("2026-09-12", "2026-09-12")
        assertEquals(1, r.days)
        assertEquals(millis("2026-09-12", 2), r.startMillis)
        assertEquals(millis("2026-09-13", 2), r.endMillis)
    }

    @Test fun rangeIsInclusiveOfBothEnds() {
        val r = ok("2026-09-12", "2026-09-29")
        assertEquals(18, r.days)
        assertEquals(millis("2026-09-12", 2), r.startMillis)
        assertEquals(millis("2026-09-30", 2), r.endMillis)
    }

    @Test fun crossMonthAndYear() {
        val r = ok("2026-12-30", "2027-01-02")
        assertEquals(4, r.days)
        assertEquals(millis("2027-01-03", 2), r.endMillis)
    }

    @Test fun sameWindowAsTheDayByDayPresetCalculation() {
        // "7 días" = hoy − 6 hasta hoy: debe dar lo mismo que la ventana que construía el resumen.
        val today = BusinessDay.forDate(d("2026-09-29"), zone, cutoff)
        val r = ok("2026-09-23", "2026-09-29")
        assertEquals(BusinessDay.forDate(d("2026-09-23"), zone, cutoff).startMillis, r.startMillis)
        assertEquals(today.endMillis, r.endMillis)
    }

    @Test fun reversedIsInvalid() {
        assertEquals(RangeResult.Reversed, BusinessRange.resolve(d("2026-09-29"), d("2026-09-12"), calendar))
    }

    @Test fun capsAt366Days() {
        assertEquals(366, ok("2026-01-01", "2027-01-01").days)
        val long = BusinessRange.resolve(d("2026-01-01"), d("2027-01-02"), calendar)
        assertTrue(long is RangeResult.TooLong)
        assertEquals(367, (long as RangeResult.TooLong).days)
    }

    @Test fun futureEndIsClampedToTodayAndFutureStartRejected() {
        val today = d("2026-09-29")
        assertEquals(d("2026-09-29"), ok("2026-09-20", "2026-10-15", today).to)
        assertEquals(RangeResult.InFuture, BusinessRange.resolve(d("2026-09-30"), d("2026-10-02"), calendar, today))
    }
}

package com.cuadra.caja.domain

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RangePresetsTest {
    private val managua = ZoneId.of("America/Managua")
    private val calendar = BusinessCalendar(managua, LocalTime.of(2, 0))
    private fun now(local: String) = LocalDateTime.parse(local).atZone(managua).toInstant().toEpochMilli()
    private fun ms(local: String) = now(local)
    private fun d(s: String) = LocalDate.parse(s)
    private fun resolve(p: RangePreset, at: String, custom: Pair<LocalDate, LocalDate>? = null) = RangePresets.resolve(RangeChoice(p, custom), calendar, now(at))

    @Test fun todayAt0030IsStillTheBusinessDayThatStartedYesterday() {
        val r = resolve(RangePreset.TODAY, "2026-09-29T00:30:00")
        assertEquals(d("2026-09-28"), r.from)
        assertEquals(d("2026-09-28"), r.to)
        assertEquals(ms("2026-09-28T02:00:00"), r.startMillis)
        assertEquals(ms("2026-09-29T02:00:00"), r.endMillis)
        assertTrue(r.inProgress)
    }

    @Test fun todayAt0230IsTheNewDay() {
        val r = resolve(RangePreset.TODAY, "2026-09-29T02:30:00")
        assertEquals(d("2026-09-29"), r.from)
        assertEquals(ms("2026-09-29T02:00:00"), r.startMillis)
        assertEquals(ms("2026-09-30T02:00:00"), r.endMillis)
    }

    @Test fun yesterdayAt1400IsYesterdayTwoAmToTodayTwoAm() {
        val r = resolve(RangePreset.YESTERDAY, "2026-09-29T14:00:00")
        assertEquals(d("2026-09-28"), r.from)
        assertEquals(ms("2026-09-28T02:00:00"), r.startMillis)
        assertEquals(ms("2026-09-29T02:00:00"), r.endMillis)
        assertFalse(r.inProgress)
    }

    @Test fun yesterdayAt0030IsTheDayBeforeTheOneInProgress() {
        // A las 00:30 del 29 «hoy» es la jornada del 28 (en curso), así que «ayer» es la del 27.
        val r = resolve(RangePreset.YESTERDAY, "2026-09-29T00:30:00")
        assertEquals(d("2026-09-27"), r.from)
        assertEquals(ms("2026-09-28T02:00:00"), r.endMillis)
    }

    @Test fun lastSevenAndThirtyDaysIncludeToday() {
        val seven = resolve(RangePreset.LAST_7, "2026-09-29T14:00:00")
        assertEquals(d("2026-09-23"), seven.from)
        assertEquals(d("2026-09-29"), seven.to)
        assertEquals(7, seven.days)
        assertTrue(seven.inProgress)
        val thirty = resolve(RangePreset.LAST_30, "2026-09-29T14:00:00")
        assertEquals(d("2026-08-31"), thirty.from)
        assertEquals(30, thirty.days)
    }

    @Test fun thisMonthStartsOnTheFirstOfTheBusinessMonth() {
        val r = resolve(RangePreset.THIS_MONTH, "2026-09-29T14:00:00")
        assertEquals(d("2026-09-01"), r.from)
        assertEquals(d("2026-09-29"), r.to)
        // El 1 de octubre a la 1 AM todavía es la jornada del 30 de septiembre: «este mes» sigue siendo septiembre.
        val edge = resolve(RangePreset.THIS_MONTH, "2026-10-01T01:00:00")
        assertEquals(d("2026-09-01"), edge.from)
        assertEquals(d("2026-09-30"), edge.to)
    }

    @Test fun lastMonthCrossesTheYearAndHandlesShortMonths() {
        val jan = resolve(RangePreset.LAST_MONTH, "2027-01-15T10:00:00")
        assertEquals(d("2026-12-01"), jan.from)
        assertEquals(d("2026-12-31"), jan.to)
        assertEquals(ms("2026-12-01T02:00:00"), jan.startMillis)
        assertEquals(ms("2027-01-01T02:00:00"), jan.endMillis)
        assertFalse(jan.inProgress)
        val mar = resolve(RangePreset.LAST_MONTH, "2026-03-05T10:00:00")
        assertEquals(d("2026-02-01"), mar.from)
        assertEquals(d("2026-02-28"), mar.to)
        // Marzo a la 1 AM del día 1: la jornada de hoy es la del 28 de febrero, el mes pasado es enero.
        val early = resolve(RangePreset.LAST_MONTH, "2026-03-01T01:00:00")
        assertEquals(d("2026-01-01"), early.from)
        assertEquals(d("2026-01-31"), early.to)
    }

    @Test fun customUsesItsDatesAndIsClampedToToday() {
        val r = resolve(RangePreset.CUSTOM, "2026-09-29T14:00:00", d("2026-09-10") to d("2026-09-12"))
        assertEquals(d("2026-09-10"), r.from)
        assertEquals(ms("2026-09-13T02:00:00"), r.endMillis)
        assertFalse(r.inProgress)
        val future = resolve(RangePreset.CUSTOM, "2026-09-29T14:00:00", d("2026-09-25") to d("2026-10-30"))
        assertEquals(d("2026-09-29"), future.to)
        assertTrue(future.inProgress)
        assertEquals(d("2026-09-29"), resolve(RangePreset.CUSTOM, "2026-09-29T14:00:00").from)
    }

    @Test fun theRangeFollowsTheBusinessNotThePhone() {
        val saved = java.util.TimeZone.getDefault()
        try {
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Asia/Tokyo"))
            val r = resolve(RangePreset.YESTERDAY, "2026-09-29T14:00:00")
            assertEquals(ms("2026-09-28T02:00:00"), r.startMillis)
        } finally {
            java.util.TimeZone.setDefault(saved)
        }
    }

    @Test fun presetsAcrossACutoffChangeUseTheRulesList() {
        val cal = BusinessCalendar(listOf(DayRule(BusinessCalendar.SINCE_FOREVER, managua, LocalTime.of(2, 0)), DayRule(d("2026-10-10"), managua, LocalTime.of(4, 0))))
        val at = LocalDateTime.parse("2026-10-11T15:00:00").atZone(managua).toInstant().toEpochMilli()
        val y = RangePresets.resolve(RangeChoice(RangePreset.YESTERDAY), cal, at)
        assertEquals(d("2026-10-10"), y.from)
        assertEquals(ms("2026-10-10T04:00:00"), y.startMillis)
        assertEquals(ms("2026-10-11T04:00:00"), y.endMillis)
        // «Ayer» de la víspera termina donde empieza el primer día con el corte nuevo (día largo de 26 h, sin hueco).
        val before = RangePresets.resolve(RangeChoice(RangePreset.YESTERDAY), cal, LocalDateTime.parse("2026-10-10T15:00:00").atZone(managua).toInstant().toEpochMilli())
        assertEquals(d("2026-10-09"), before.from)
        assertEquals(ms("2026-10-10T04:00:00"), before.endMillis)
    }

    @Test fun windowTextShowsTheExactHoursInTheAppLanguage() {
        val r = resolve(RangePreset.YESTERDAY, "2026-09-29T14:00:00")
        val es = RangeWindowText.parts(r, calendar, now("2026-09-29T14:00:00"), Locale.forLanguageTag("es"))
        assertTrue(es.first, es.first.startsWith("28 sep") && es.first.contains(" 2:00 "))
        assertTrue(es.first, es.first.contains("AM") || es.first.contains("a. m."))
        assertTrue(es.second!!, es.second!!.startsWith("29 sep"))
        val en = RangeWindowText.parts(r, calendar, now("2026-09-29T14:00:00"), Locale.ENGLISH)
        assertTrue(en.first, en.first.startsWith("28 Sep 2:00"))
        assertEquals("28 Sep 2:00 AM", en.first)
        assertFalse(en.first.contains(" "))
    }

    @Test fun windowTextOfTodayIsInProgressAndOtherYearsShowTheYear() {
        val today = resolve(RangePreset.TODAY, "2026-09-29T00:30:00")
        val parts = RangeWindowText.parts(today, calendar, now("2026-09-29T00:30:00"), Locale.forLanguageTag("es"))
        assertTrue(parts.first, parts.first.startsWith("28 sep"))
        assertNull(parts.second)
        val lastMonth = resolve(RangePreset.LAST_MONTH, "2027-01-15T10:00:00")
        val jan = RangeWindowText.parts(lastMonth, calendar, now("2027-01-15T10:00:00"), Locale.forLanguageTag("es"))
        assertTrue(jan.first, jan.first.startsWith("1 dic 2026"))
        assertTrue(jan.second!!, jan.second!!.startsWith("1 ene 2027"))
    }
}

package com.cuadra.caja.domain

import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import org.junit.Test
import org.junit.Assert.assertEquals

class ClockFormatTest {
    private val utc = ZoneId.of("UTC")
    private fun at(t: String) = Instant.parse("2026-10-01T${t}Z")
    private val locales = listOf(Locale.forLanguageTag("es"), Locale.forLanguageTag("es-NI"), Locale.forLanguageTag("es-ES"), Locale.US, Locale.ENGLISH)

    @Test fun twelveHourWithAmPmInEveryLocale() {
        for (l in locales) {
            val f = ClockFormat.time(l, utc)
            assertEquals(l.toString(), "3:18 PM", f.format(at("15:18:42")))
            assertEquals(l.toString(), "12:05 PM", f.format(at("12:05:00")))
            assertEquals(l.toString(), "12:00 AM", f.format(at("00:00:59")))
            assertEquals(l.toString(), "1:30 AM", f.format(at("01:30:00")))
            assertEquals(l.toString(), "11:59 PM", f.format(at("23:59:00")))
        }
    }

    @Test fun dateTimeEndsWithTheClock() {
        for (l in locales) assertEquals(l.toString(), true, ClockFormat.dateTime(l, utc).format(at("15:18:00")).endsWith(", 3:18 PM"))
    }
}

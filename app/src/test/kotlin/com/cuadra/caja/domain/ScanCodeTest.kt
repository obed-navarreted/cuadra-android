package com.cuadra.caja.domain

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanCodeTest {
    @Test fun normalizeTrimsAndDropsControlCharacters() {
        assertEquals("7441001600153", ScanCode.normalize(" 7441001600153\r\n"))
        assertEquals("74410016", ScanCode.normalize("7441 0016"))
        assertEquals("ABC 12", ScanCode.normalize(" ABC 12 "))
        assertEquals("", ScanCode.normalize("  \n "))
    }

    @Test fun checksumOfEan13Upc8() {
        assertEquals(true, ScanCode.checksumValid("4006381333931"))   // EAN-13
        assertEquals(true, ScanCode.checksumValid("036000291452"))    // UPC-A
        assertEquals(true, ScanCode.checksumValid("96385074"))        // EAN-8
        assertEquals(false, ScanCode.checksumValid("4006381333932"))
        assertEquals(false, ScanCode.checksumValid("036000291453"))
        assertNull(ScanCode.checksumValid("12345"))
        assertNull(ScanCode.checksumValid("ABC-123"))
    }

    @Test fun resolveFoundNotFoundAndEmpty() = runBlocking {
        val catalog = mapOf("4006381333931" to "Lápiz")
        assertEquals(ScanResolution.Found("4006381333931", "Lápiz"), ScanCode.resolve(" 4006381333931\n") { catalog[it] })
        assertEquals(ScanResolution.NotFound("5901234123457", true), ScanCode.resolve("5901234123457") { catalog[it] })
        assertEquals(ScanResolution.Empty, ScanCode.resolve("  ") { catalog[it] })
    }

    @Test fun badCheckDigitStillLooksUpButIsFlagged() = runBlocking {
        val catalog = mapOf("4006381333932" to "Interno")
        assertTrue(ScanCode.resolve("4006381333932") { catalog[it] } is ScanResolution.Found)
        assertEquals(ScanResolution.NotFound("4006381333930", false), ScanCode.resolve("4006381333930") { catalog[it] })
    }

    @Test fun debounceIgnoresSameCodeWithinWindow() {
        val d = ScanDebounce(2000)
        assertTrue(d.accept("A", 0))
        assertFalse(d.accept("A", 500))
        assertTrue(d.accept("B", 600))
        assertTrue(d.accept("A", 700))
        assertTrue(d.accept("A", 2800))
    }
}

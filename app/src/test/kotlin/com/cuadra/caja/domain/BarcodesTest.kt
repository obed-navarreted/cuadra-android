package com.cuadra.caja.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class BarcodesTest {
    @Test fun upcAAlsoTriesTheEan13WithLeadingZero() {
        assertEquals(listOf("012345678905", "0012345678905"), Barcodes.forms("012345678905"))
    }

    @Test fun ean13WithLeadingZeroAlsoTriesTheUpcA() {
        assertEquals(listOf("0012345678905", "012345678905"), Barcodes.forms("0012345678905"))
    }

    @Test fun otherCodesAreLookedUpAsIs() {
        assertEquals(listOf("7441001600153"), Barcodes.forms("7441001600153"))
        assertEquals(listOf("ABC-123"), Barcodes.forms(" ABC-123 "))
        assertEquals(listOf("12345"), Barcodes.forms("12345"))
    }
}

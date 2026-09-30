package com.cuadra.caja.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SaleDeletionTest {
    @Test fun aReasonNeedsAtLeastFiveLetters() {
        assertEquals(ReasonCheck.TOO_SHORT, SaleDeletion.check(""))
        assertEquals(ReasonCheck.TOO_SHORT, SaleDeletion.check("    "))
        assertEquals(ReasonCheck.TOO_SHORT, SaleDeletion.check("abcd"))
        assertEquals(ReasonCheck.TOO_SHORT, SaleDeletion.check("  ab cd  "))
        assertEquals(ReasonCheck.TOO_SHORT, SaleDeletion.check("....."))
        assertEquals(ReasonCheck.OK, SaleDeletion.check("abcde"))
        assertEquals(ReasonCheck.OK, SaleDeletion.check("  cobrada dos veces "))
        assertEquals(ReasonCheck.OK, SaleDeletion.check("Error de precio"))
    }

    @Test fun theSentTextIsTrimmedOrNull() {
        assertEquals("cobrada dos veces", SaleDeletion.clean("  cobrada dos veces \n"))
        assertNull(SaleDeletion.clean("no"))
    }

    @Test fun onlyOwnerAndAdminDeleteAndOnlyPaidSales() {
        assertTrue(SaleDeletion.canDelete("OWNER", "COMPLETED"))
        assertTrue(SaleDeletion.canDelete("ADMIN", "COMPLETED"))
        assertFalse(SaleDeletion.canDelete("CASHIER", "COMPLETED"))
        assertFalse(SaleDeletion.canDelete(null, "COMPLETED"))
        assertFalse(SaleDeletion.canDelete("OWNER", "CANCELLED"))
        assertFalse(SaleDeletion.canDelete("OWNER", "PARKED"))
    }

    @Test fun theServerAndThePhoneAgreeOnTheMinimum() {
        // El servidor exige `reason.trim().length() >= 5`: lo que el teléfono deja pasar siempre lo cumple.
        for (text in listOf("abcde", " abcde ", "12345", "cobro doble")) assertTrue(SaleDeletion.clean(text)!!.length >= 5)
    }

    @Test fun listsMergeNewestFirstWithoutRepeats() {
        fun v(id: String, at: Long) = SaleView(id, "COMPLETED", null, 1, 0, 1, at, null, null, null, null, null, null, emptyList(), emptyList())
        val merged = SaleLists.merge(listOf(v("a", 10), v("b", 30)), listOf(v("c", 20), v("a", 10)))
        assertEquals(listOf("b", "c", "a"), merged.map { it.id })
    }

    @Test fun statusFiltersAlwaysKeepOne() {
        var q = SaleQuery()
        assertEquals(setOf("COMPLETED"), q.statuses)
        q = q.toggle("COMPLETED")
        assertEquals(setOf("COMPLETED"), q.statuses)
        q = q.toggle("CANCELLED")
        assertEquals(setOf("COMPLETED", "CANCELLED"), q.statuses)
        q = q.toggle("COMPLETED")
        assertEquals(setOf("CANCELLED"), q.statuses)
    }
}

package com.cuadra.caja.domain

import com.cuadra.caja.data.local.SaleEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RegisterQueueTest {
    private fun sale(id: String, status: String = "PARKED", sentAt: Long? = null, updated: Long = 0, total: Long = 100) = SaleEntity(
        id, status, "Mesa $id", null, total, 0, total, "m1", "Kevin", null, null, null, null, null, null, 0, updated, 1, sentToRegisterAt = sentAt, sentByName = sentAt?.let { "Kevin" },
    )

    @Test fun pendingTicketsComeFirstOldestFirstAndPlainParkedOnesStayApart() {
        val split = RegisterQueue.split(
            listOf(sale("a", updated = 5), sale("b", sentAt = 300), sale("c", sentAt = 100, total = 250), sale("d", updated = 9), sale("e", status = "COMPLETED", sentAt = 50)),
        )
        assertEquals(listOf("c", "b"), split.pending.map { it.id })
        assertEquals(listOf("d", "a"), split.parked.map { it.id })
        assertEquals(350L, split.pendingTotalMinor)
        assertEquals(4, RegisterQueue.chipCount(split))
    }

    @Test fun onlyAParkedTicketSentToTheRegisterIsPending() {
        assertTrue(RegisterQueue.isPending(sale("a", sentAt = 1)))
        assertFalse(RegisterQueue.isPending(sale("a")))
        assertFalse(RegisterQueue.isPending(sale("a", status = "COMPLETED", sentAt = 1)))
        assertFalse(RegisterQueue.isPending(sale("a", status = "OPEN", sentAt = 1)))
    }

    @Test fun theNoteIsTrimmedCollapsedAndCapped() {
        assertEquals("Mesa 4, Juan", RegisterQueue.cleanNote("  Mesa   4,  Juan "))
        assertNull(RegisterQueue.cleanNote("   "))
        assertEquals(RegisterQueue.NOTE_MAX, RegisterQueue.cleanNote("x".repeat(500))!!.length)
    }

    @Test fun theAgeReadsNowMinutesOrHoursAndNeverGoesNegative() {
        val now = 10_000_000L
        assertEquals(RegisterQueue.Age.Now, RegisterQueue.age(now - 30_000, now))
        assertEquals(RegisterQueue.Age.Now, RegisterQueue.age(now + 120_000, now))   // reloj de otro teléfono adelantado
        assertEquals(RegisterQueue.Age.Minutes(12), RegisterQueue.age(now - 12 * 60_000 - 59_000, now))
        assertEquals(RegisterQueue.Age.Hours(1, 5), RegisterQueue.age(now - 65 * 60_000, now))
        assertNull(RegisterQueue.age(null, now))
    }

    @Test fun aTicketWaitsLongFromFifteenMinutes() {
        val now = 10_000_000L
        assertFalse(RegisterQueue.waitingLong(now - 14 * 60_000 - 59_000, now))
        assertTrue(RegisterQueue.waitingLong(now - 15 * 60_000, now))
        assertFalse(RegisterQueue.waitingLong(null, now))
    }

    @Test fun searchMatchesNoteOrWhoSentItWithoutAccentsAndKeepsTheOrder() {
        val list = listOf(
            sale("1", sentAt = 1).copy(label = "Mesa 4, Juan"), sale("2", sentAt = 2).copy(label = "Terraza", sentByName = "Lucía"),
            sale("3", sentAt = 3).copy(label = null), sale("4", sentAt = 4).copy(label = "mesa 14"),
        )
        assertEquals(listOf("1", "4"), RegisterQueue.filter(list, "MESA").map { it.id })
        assertEquals(listOf("1"), RegisterQueue.filter(list, "juan mesa").map { it.id })
        assertEquals(listOf("2"), RegisterQueue.filter(list, "lucia").map { it.id })
        assertEquals(list, RegisterQueue.filter(list, "   "))
        assertEquals(emptyList<SaleEntity>(), RegisterQueue.filter(list, "barra"))
    }

    @Test fun backingOutOfChargingReturnsTheTicketToTheQueueOnlyWhenItCameFromThere() {
        assertTrue(RegisterQueue.returnsToQueueOnBackOut(chargingFromQueue = true, resumedPending = true, resumedId = "s1"))
        assertFalse(RegisterQueue.returnsToQueueOnBackOut(chargingFromQueue = false, resumedPending = true, resumedId = "s1"))   // agregar productos y luego Cobrar
        assertFalse(RegisterQueue.returnsToQueueOnBackOut(chargingFromQueue = true, resumedPending = false, resumedId = "s1"))
        assertFalse(RegisterQueue.returnsToQueueOnBackOut(chargingFromQueue = true, resumedPending = true, resumedId = null))
    }

    @Test fun servedAndChargedOnlyWhenTheyAreDifferentPeople() {
        assertEquals("Kevin" to "Ana", RegisterQueue.takenAndCharged("Kevin", "Ana"))
        assertNull(RegisterQueue.takenAndCharged("Ana", "Ana"))
        assertNull(RegisterQueue.takenAndCharged(null, "Ana"))
        assertNull(RegisterQueue.takenAndCharged("Kevin", null))
    }
}

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

    @Test fun servedAndChargedOnlyWhenTheyAreDifferentPeople() {
        assertEquals("Kevin" to "Ana", RegisterQueue.takenAndCharged("Kevin", "Ana"))
        assertNull(RegisterQueue.takenAndCharged("Ana", "Ana"))
        assertNull(RegisterQueue.takenAndCharged(null, "Ana"))
        assertNull(RegisterQueue.takenAndCharged("Kevin", null))
    }
}

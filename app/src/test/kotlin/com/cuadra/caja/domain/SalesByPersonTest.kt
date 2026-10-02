package com.cuadra.caja.domain

import com.cuadra.caja.data.remote.BreakdownRowDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SalesByPersonTest {
    private fun row(id: String?, name: String?, count: Long, total: Long) = BreakdownRowDto(id, name, count, total)

    @Test fun sortsByTotalAndComputesTheShareOfTheTotal() {
        val r = SalesByPerson.shares(listOf(row("a", "Ana", 2, 2_500), row("k", "Kevin", 5, 7_500), row("l", "Lucía", 1, 0)))
        assertEquals(listOf("Kevin", "Ana", "Lucía"), r.map { it.name })
        assertEquals(0.75f, r[0].share, 0.0001f)
        assertEquals(75, r[0].percent)
        assertEquals(25, r[1].percent)
        assertEquals(0f, r[2].share, 0f)
    }

    @Test fun tiesGoToTheOneWithMoreSalesThenByName() {
        val r = SalesByPerson.shares(listOf(row("b", "beto", 1, 100), row("a", "Ana", 1, 100), row("c", "Carla", 3, 100)))
        assertEquals(listOf("Carla", "Ana", "beto"), r.map { it.name })
    }

    @Test fun emptyZeroAndBlankNamesAreHandled() {
        assertTrue(SalesByPerson.shares(emptyList()).isEmpty())
        assertTrue(SalesByPerson.shares(listOf(row("x", "Nadie", 0, 0))).isEmpty())
        val r = SalesByPerson.shares(listOf(row("x", null, 1, 500), row("y", "  ", 1, 500)))
        assertEquals(listOf("—", "—"), r.map { it.name })
        assertEquals(50, r[0].percent)
        // Un total negativo (no debería llegar) no rompe la barra.
        val neg = SalesByPerson.shares(listOf(row("x", "A", 1, -100), row("y", "B", 1, 100)))
        assertEquals(1f, neg[0].share, 0f)
        assertEquals(0f, neg[1].share, 0f)
    }

    @Test fun hugeAmountsKeepTheShareBetweenZeroAndOne() {
        val r = SalesByPerson.shares(listOf(row("a", "A", 1, 9_999_999_900L), row("b", "B", 1, 100)))
        assertTrue(r.all { it.share in 0f..1f })
        assertEquals(100, r[0].percent)
    }

    @Test fun topFiveAndSeeAll() {
        fun n(k: Int) = SalesByPerson.shares((1..k).map { row("m$it", "P$it", 1, it * 100L) })
        assertEquals(1, SalesByPerson.visible(n(1), false).size)
        assertEquals(5, SalesByPerson.visible(n(5), false).size)
        assertEquals(0, SalesByPerson.hiddenCount(n(5), false))
        // Con 6 se ven las 6 (esconder una sola fila no ahorra nada).
        assertEquals(6, SalesByPerson.visible(n(6), false).size)
        val twelve = n(12)
        assertEquals(listOf("P12", "P11", "P10", "P9", "P8"), SalesByPerson.visible(twelve, false).map { it.name })
        assertEquals(7, SalesByPerson.hiddenCount(twelve, false))
        assertEquals(12, SalesByPerson.visible(twelve, true).size)
        assertEquals(0, SalesByPerson.hiddenCount(twelve, true))
    }

    @Test fun theChargedServedControlShowsWithRegisterCheckoutOrWhenTheTwoDiffer() {
        val same = PeopleBreakdown(listOf(row("a", "Ana", 2, 200)), listOf(row("a", "Ana", 2, 200)))
        val differ = PeopleBreakdown(listOf(row("l", "Lucía", 1, 450)), listOf(row("k", "Kevin", 1, 450)))
        assertFalse(SalesByPerson.showControl(false, same))
        assertFalse(SalesByPerson.showControl(false, null))
        assertTrue(SalesByPerson.showControl(true, same))
        assertTrue(SalesByPerson.showControl(true, null))
        assertTrue(SalesByPerson.showControl(false, differ))
        // Mismo reparto aunque el servidor las mande en otro orden.
        val reordered = PeopleBreakdown(listOf(row("a", "Ana", 1, 100), row("b", "Beto", 1, 50)), listOf(row("b", "Beto", 1, 50), row("a", "Ana", 1, 100)))
        assertFalse(SalesByPerson.showControl(false, reordered))
    }

    @Test fun modeChoosesTheRightGrouping() {
        val b = PeopleBreakdown(listOf(row("l", "Lucía", 1, 450)), listOf(row("k", "Kevin", 1, 450)))
        assertEquals("Lucía", b.rows(PeopleMode.CHARGED).single().label)
        assertEquals("Kevin", b.rows(PeopleMode.SERVED).single().label)
        assertEquals("member", PeopleMode.CHARGED.by)
        assertEquals("member_served", PeopleMode.SERVED.by)
    }

    @Test fun theCardAppliesOnlyWhenCompletedSalesAreIncluded() {
        assertTrue(SalesByPerson.applies(setOf("COMPLETED")))
        assertTrue(SalesByPerson.applies(setOf("COMPLETED", "CANCELLED")))
        assertFalse(SalesByPerson.applies(setOf("CANCELLED")))
    }
}

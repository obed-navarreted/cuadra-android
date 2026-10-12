package com.cuadra.caja.ui

import com.cuadra.caja.domain.AmountEntry
import com.cuadra.caja.domain.Cart
import com.cuadra.caja.domain.CartLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Cobro rápido: lo escrito en la calculadora entra como línea por el mismo camino que «Agregar» (`CajaUi.withPendingAdded`), con la cuenta y la calculadora limpias. */
class QuickChargeUiTest {
    private val cart250 = Cart(listOf(CartLine("l1", null, null, "Café", null, 25_000, null, 1000)))
    private fun entry(vararg d: Char) = d.fold(AmountEntry()) { e, c -> e.digit(c, 2) }

    @Test fun anEmptyCartWithOnlyTheKeypadBecomesOneVariosLine() {
        val ui = CajaUi(entry = entry('1', '0', '0')).withPendingAdded(2)
        assertEquals(1, ui.cart.lines.size)
        assertEquals("Varios", ui.cart.lines.single().name)
        assertEquals(10_000L, ui.cart.totalMinor)
        assertTrue(ui.entry.isEmpty)
        assertEquals("", ui.description)
    }

    @Test fun theCartKeepsItsLinesAndTheNewLineIsAddedWithoutMerging() {
        val ui = CajaUi(cart = cart250, entry = entry('1', '0', '0')).withPendingAdded(2)
        assertEquals(2, ui.cart.lines.size)
        assertEquals(35_000L, ui.cart.totalMinor)
        assertEquals(ui.cart.lines.last().id, ui.lastLineId)
    }

    @Test fun theDescriptionIsUsedWhenPresent() {
        val ui = CajaUi(entry = entry('5'), description = "Envío").withPendingAdded(2)
        assertEquals("Envío", ui.cart.lines.single().name)
    }

    @Test fun aMultiplicationBecomesOneLineWithItsQuantity() {
        val ui = CajaUi(entry = AmountEntry("5", "20", true)).withPendingAdded(2)
        assertEquals(5000L, ui.cart.lines.single().quantityMilli)
        assertEquals(10_000L, ui.cart.totalMinor)
    }

    @Test fun nothingPendingChangesNothing() {
        val zero = CajaUi(cart = cart250, entry = entry('0'))
        assertSame(zero, zero.withPendingAdded(2))
        val incomplete = CajaUi(entry = AmountEntry("5", "", true))
        assertSame(incomplete, incomplete.withPendingAdded(2))
        assertNull(incomplete.pending(2))
    }

    @Test fun inProductosThereIsNoKeypadSoNothingIsPending() {
        val ui = CajaUi(tab = PosTab.PRODUCTS, cart = cart250, entry = entry('1', '0', '0'))
        assertNull(ui.pending(2))
        assertSame(ui, ui.withPendingAdded(2))
    }

    @Test fun addingThePendingIsTheSameAsTheAddButton() {
        val base = CajaUi(cart = cart250, entry = entry('1', '2', '5'), description = "Pan")
        val a = base.withEntryAdded(2)
        val b = base.withPendingAdded(2)
        assertEquals(a.cart.totalMinor, b.cart.totalMinor)
        assertEquals(a.cart.lines.map { it.name to it.quantityMilli }, b.cart.lines.map { it.name to it.quantityMilli })
        assertEquals(a.entry, b.entry)
    }
}

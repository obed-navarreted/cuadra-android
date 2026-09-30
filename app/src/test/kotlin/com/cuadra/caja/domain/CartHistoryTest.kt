package com.cuadra.caja.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CartHistoryTest {
    private var n = 0
    private val id = { "id${n++}" }
    private fun CartWithUndo.cuajada(qty: Long = 1000) = addProduct("p1", null, "Cuajada", null, 2500, null, qty, newId = id)
    private fun CartWithUndo.pan() = addProduct("p2", null, "Pan", null, 500, null, newId = id)

    @Test fun addAddUndoUndoEmptiesTheCart() {
        var s = CartWithUndo().cuajada().pan()
        assertEquals(2, s.cart.lines.size)
        assertTrue(s.pushed)
        s = s.undoLast()
        assertEquals(listOf("Cuajada"), s.cart.lines.map { it.name })
        s = s.undoLast()
        assertTrue(s.cart.isEmpty)
        assertTrue(s.stack.isEmpty())
        assertNull(s.last)
    }

    @Test fun undoRemovesExactlyTheLastAdditionEvenOnAnExistingLine() {
        var s = CartWithUndo().cuajada().cuajada().cuajada()
        assertEquals(3000L, s.cart.lines.single().quantityMilli)
        s = s.undoLast()
        assertEquals(2000L, s.cart.lines.single().quantityMilli)
        s = s.undoLast().undoLast()
        assertTrue(s.cart.isEmpty)
    }

    @Test fun undoOfAWeighedAdditionSubtractsThatWeight() {
        var s = CartWithUndo().addProduct("q1", null, "Queso", null, 9000, null, 750, byWeight = true, newId = id)
        s = s.addProduct("q1", null, "Queso", null, 9000, null, 500, byWeight = true, newId = id)
        assertEquals(1250L, s.cart.lines.single().quantityMilli)
        s = s.undoLast()
        assertEquals(750L, s.cart.lines.single().quantityMilli)
    }

    @Test fun undoStripDescribesTheAddition() {
        val s = CartWithUndo().cuajada()
        val e = s.last as UndoEntry.Added
        assertEquals("Cuajada", e.label)
        assertEquals(1000L, e.quantityMilli)
        assertEquals(2500L, e.amountMinor)
        assertTrue(e.createdLine)
        val again = s.cuajada(2000).last as UndoEntry.Added
        assertEquals(5000L, again.amountMinor)
        assertFalse(again.createdLine)
    }

    @Test fun freeSalesUndoToo() {
        val s = CartWithUndo().addFree(8500, "Queso seco", 1000, id).addFree(8500, null, 1000, id)
        assertEquals(2, s.cart.lines.size)
        assertEquals(1, s.undoLast().cart.lines.size)
    }

    @Test fun deleteLineThenUndoRestoresPositionAndQuantity() {
        var s = CartWithUndo().cuajada(15_000).pan()
        val pan = s.cart.lines[1]
        s = s.addProduct("p3", null, "Leche", null, 3000, null, newId = id)
        val cuajadaId = s.cart.lines[0].id
        s = s.deleteLine(cuajadaId)
        assertEquals(listOf("Pan", "Leche"), s.cart.lines.map { it.name })
        assertTrue(s.pushed)
        assertTrue(s.last is UndoEntry.Deleted)
        s = s.undoLast()
        assertEquals(listOf("Cuajada", "Pan", "Leche"), s.cart.lines.map { it.name })
        assertEquals(15_000L, s.cart.lines[0].quantityMilli)
        assertEquals(pan, s.cart.lines[1])
        assertEquals(15_000L * 2500 / 1000 + 500 + 3000, s.cart.totalMinor)
    }

    @Test fun deletingAMiddleLineRestoresInTheMiddle() {
        var s = CartWithUndo().cuajada().pan().addProduct("p3", null, "Leche", null, 3000, null, newId = id)
        s = s.deleteLine(s.cart.lines[1].id).undoLast()
        assertEquals(listOf("Cuajada", "Pan", "Leche"), s.cart.lines.map { it.name })
    }

    @Test fun deleteUnknownLineDoesNothing() {
        val s = CartWithUndo().cuajada()
        assertEquals(s, s.deleteLine("nope"))
    }

    @Test fun typedQuantityChangesTheLineAndClearsTheStack() {
        var s = CartWithUndo().cuajada().pan()
        val lineId = s.cart.lines[0].id
        s = s.setQuantity(lineId, 15_000)
        assertEquals(15_000L, s.cart.lines[0].quantityMilli)
        assertTrue(s.stack.isEmpty())
        assertFalse(s.pushed)
        assertEquals(s, s.undoLast())   // nada que deshacer
    }

    @Test fun typingZeroDeletesWithUndo() {
        var s = CartWithUndo().cuajada(15_000)
        s = s.setQuantity(s.cart.lines[0].id, 0)
        assertTrue(s.cart.isEmpty)
        s = s.undoLast()
        assertEquals(15_000L, s.cart.lines.single().quantityMilli)
    }

    @Test fun plusMinusButtonsKeepTheStack() {
        var s = CartWithUndo().cuajada()
        s = s.changeQuantity(s.cart.lines[0].id, 1000)
        assertEquals(2000L, s.cart.lines[0].quantityMilli)
        assertEquals(1, s.stack.size)
        assertFalse(s.pushed)
    }

    @Test fun clearAndUndoRestoresEverything() {
        var s = CartWithUndo().cuajada(15_000).pan()
        val before = s.cart
        s = s.clear()
        assertTrue(s.cart.isEmpty)
        s = s.undoLast()
        assertEquals(before, s.cart)
        assertEquals(CartWithUndo().clear(), CartWithUndo())   // vaciar un recibo vacío no apila nada
    }

    @Test fun undoWhenTheLineIsGoneIsHarmless() {
        var s = CartWithUndo().cuajada().pan()
        val first = s.cart.lines[0].id
        s = s.copy(cart = s.cart.remove(first))
        s = s.undoLast().undoLast()
        assertTrue(s.cart.isEmpty)
    }

    @Test fun stackIsBounded() {
        var s = CartWithUndo()
        repeat(25) { s = s.cuajada() }
        assertEquals(CartWithUndo.MAX_STACK, s.stack.size)
        assertEquals(25_000L, s.cart.lines.single().quantityMilli)
    }

    @Test fun badgeCountIsTheNumberOfLines() {
        var s = CartWithUndo()
        assertEquals(0, s.cart.lineCount)
        repeat(15) { s = s.cuajada() }
        assertEquals(1, s.cart.lineCount)          // 15 unidades de un mismo producto: una línea
        s = s.pan()
        assertEquals(2, s.cart.lineCount)
        s = s.deleteLine(s.cart.lines[0].id)
        assertEquals(1, s.cart.lineCount)
    }

    @Test fun fifteenTapsThenOneDeleteIsOneGesture() {
        var s = CartWithUndo()
        repeat(15) { s = s.cuajada() }
        val lineId = s.cart.lines[0].id
        s = s.deleteLine(lineId)
        assertTrue(s.cart.isEmpty)
        s = s.undoLast()
        assertEquals(15_000L, s.cart.lines.single().quantityMilli)
    }
}

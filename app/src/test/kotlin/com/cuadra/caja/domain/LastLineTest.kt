package com.cuadra.caja.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** La tira de la última línea de la caja: qué línea es, cuánto suben − y +, y que todo pase por el historial de «deshacer». */
class LastLineTest {
    private var n = 0
    private val id = { "id${n++}" }

    /** Aplica un cambio como lo hace `CajaViewModel.edit` y devuelve (historial, línea de la tira). */
    private class Session(var state: CartWithUndo = CartWithUndo(), var last: String? = null) {
        fun edit(f: (CartWithUndo) -> CartWithUndo): Session {
            val next = f(state)
            last = LastLine.pick(last, state.cart, next.cart)
            state = CartWithUndo(next.cart, next.stack, next.pushed)
            return this
        }
        val line get() = LastLine.of(state.cart, last)
    }

    private fun Session.cuajada(qty: Long = 1000) = edit { it.addProduct("p1", null, "Cuajada", null, 2500, null, qty, newId = id) }
    private fun Session.pan() = edit { it.addProduct("p2", null, "Pan", null, 500, null, newId = id) }
    private fun Session.weighed(milli: Long) = edit { it.addProduct("p3", null, "Queso", null, 8000, null, milli, byWeight = true, newId = id) }

    @Test fun emptyCartHasNoStrip() {
        assertNull(Session().line)
        assertNull(LastLine.of(Cart(), "x"))
    }

    @Test fun theStripShowsTheLastAddedAndSwitchesWhenAnotherProductIsAdded() {
        val s = Session().cuajada()
        assertEquals("Cuajada", s.line!!.name)
        s.pan()
        assertEquals("Pan", s.line!!.name)
    }

    @Test fun addingTheSameProductIncrementsAndKeepsTheStripOnIt() {
        val s = Session().cuajada().pan().cuajada()
        // Se suma a la línea existente (no se duplica) y la tira vuelve a ella aunque no sea la última de la lista.
        assertEquals(2, s.state.cart.lines.size)
        assertEquals("Cuajada", s.line!!.name)
        assertEquals(2000L, s.line!!.quantityMilli)
    }

    @Test fun fiveMorePlusTapsGiveSixAndTheSubtotalFollows() {
        val s = Session().cuajada()
        repeat(5) { s.edit { st -> val l = s.line!!; st.changeQuantity(l.id, LastLine.plus(l) - l.quantityMilli) } }
        assertEquals(6000L, s.line!!.quantityMilli)
        assertEquals(15_000L, s.line!!.totalMinor)
        assertEquals(1, s.state.cart.lines.size)
    }

    @Test fun minusStepsDownAndAtOneItReachesZero() {
        val line = Session().cuajada(2000).line!!
        assertEquals(1000L, LastLine.minus(line))
        assertEquals(0L, LastLine.minus(line.copy(quantityMilli = 1000)))
    }

    @Test fun weighedProductsStepByAQuarterOnceFractional() {
        val s = Session().weighed(1500)
        val l = s.line!!
        assertEquals(250L, LastLine.step(l))
        assertEquals(1750L, LastLine.plus(l))
        assertEquals(1250L, LastLine.minus(l))
        // Por debajo de un cuarto, − la quita.
        assertEquals(0L, LastLine.minus(l.copy(quantityMilli = 250)))
        // Entera, el paso es una unidad.
        assertEquals(1000L, LastLine.step(l.copy(quantityMilli = 3000)))
    }

    @Test fun typedQuantityKeepsDecimalsAndTheStrip() {
        val s = Session().weighed(1000)
        val l = s.line!!
        s.edit { it.editLine(l.id, null, 2750) }
        assertEquals(2750L, s.line!!.quantityMilli)
        assertEquals(22_000L, s.line!!.totalMinor)
        assertTrue(s.line!!.allowsDecimals)
    }

    @Test fun plusStopsAtTheMaximumTheQuantitySheetAccepts() {
        val line = Session().cuajada(QuantityInput.MAX_MILLI).line!!
        assertTrue(LastLine.atMax(line))
        assertEquals(QuantityInput.MAX_MILLI, LastLine.plus(line))
        assertFalse(LastLine.atMax(line.copy(quantityMilli = 999_000)))
        val big = line.copy(quantityMilli = 999_000)
        assertEquals(1_000_000L, LastLine.plus(big))
    }

    @Test fun zeroRemovesTheLineAndUndoBringsItBackAsTheStripLine() {
        val s = Session().cuajada().pan()
        val pan = s.line!!
        s.edit { it.editLine(pan.id, null, 0) }
        assertEquals(listOf("Cuajada"), s.state.cart.lines.map { it.name })
        // Sin la línea elegida la tira cae en la última del recibo.
        assertEquals("Cuajada", s.line!!.name)
        assertTrue(s.state.last is UndoEntry.Deleted)
        s.edit { it.undoLast() }
        assertEquals(listOf("Cuajada", "Pan"), s.state.cart.lines.map { it.name })
        assertEquals("Pan", s.line!!.name)
    }

    @Test fun xRemovesTheLineWithDeleteLineAndItIsUndoable() {
        val s = Session().cuajada(6000)
        val l = s.line!!
        s.edit { it.deleteLine(l.id) }
        assertTrue(s.state.cart.isEmpty)
        assertNull(s.line)
        assertTrue(s.state.pushed)
        s.edit { it.undoLast() }
        assertEquals(6000L, s.line!!.quantityMilli)
    }

    @Test fun undoOfAnAddMovesTheStripToTheLineThatChanged() {
        val s = Session().cuajada().pan().cuajada()
        assertEquals(2000L, s.line!!.quantityMilli)
        s.edit { it.undoLast() }
        assertEquals("Cuajada", s.line!!.name)
        assertEquals(1000L, s.line!!.quantityMilli)
    }

    @Test fun anUnrelatedChangeKeepsTheChosenLine() {
        val s = Session().cuajada().pan()
        val before = s.state.cart
        // Sin cambios y con la línea elegida todavía en el recibo, se queda.
        assertEquals(s.last, LastLine.pick(s.last, before, before))
        // Si la elegida ya no existe y no hubo líneas nuevas ni cambios, no hay elegida (la tira usa la última del recibo).
        assertNull(LastLine.pick("gone", before, before))
    }

    @Test fun aResumedCartShowsItsLastLine() {
        val cart = Cart().addProduct("p1", null, "A", null, 100, null, newId = id).addProduct("p2", null, "B", null, 200, null, newId = id)
        assertEquals("B", LastLine.of(cart, null)!!.name)
        assertEquals("A", LastLine.of(cart, cart.lines.first().id)!!.name)
    }

    @Test fun manualFreeLinesAreStripLinesToo() {
        val s = Session().edit { it.addFree(3500, null, newId = id) }
        assertEquals(Cart.FREE_NAME, s.line!!.name)
        s.edit { st -> val l = s.line!!; st.changeQuantity(l.id, LastLine.plus(l) - l.quantityMilli) }
        assertEquals(2000L, s.line!!.quantityMilli)
        assertEquals(7000L, s.line!!.totalMinor)
    }

    @Test fun addPopupDoesNotApplyToAddedEntriesOnTheUi() {
        // El aviso «Deshacer» ya no sale al agregar: la tira lleva su propio − y ✕ (ver CajaUi.undo).
        val ui = com.cuadra.caja.ui.CajaUi(undoStack = listOf(UndoEntry.Added("l", "x", 1000, 100, true)), undoShown = true)
        assertNull(ui.undo)
        val del = com.cuadra.caja.ui.CajaUi(undoStack = listOf(UndoEntry.Added("l", "x", 1000, 100, true), UndoEntry.Deleted(CartLine("l2", null, null, "y", null, 1, null, 1000), 0)), undoShown = true)
        assertTrue(del.undo is UndoEntry.Deleted)
    }
}

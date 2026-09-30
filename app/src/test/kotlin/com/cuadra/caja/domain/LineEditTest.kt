package com.cuadra.caja.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Editar una línea del recibo antes de cobrar: las manuales cambian descripción y cantidad; los productos del catálogo, solo la cantidad. */
class LineEditTest {
    private var n = 0
    private val id = { "id${n++}" }

    private fun manual(desc: String? = null) = CartWithUndo().addFree(3000, desc, newId = id)

    @Test fun onlyManualLinesHaveAnEditableDescription() {
        val cart = Cart().addFree(3000, null, newId = id).addProduct("p1", null, "Cuajada", null, 2500, null, newId = id)
        assertTrue(cart.lines[0].descriptionEditable)
        assertFalse(cart.lines[1].descriptionEditable)
    }

    @Test fun defaultNameShowsAsEmptyInTheField() {
        val cart = Cart().addFree(3000, "", newId = id).addFree(100, "Envío", newId = id).addProduct("p1", null, "Varios", null, 2500, null, newId = id)
        assertEquals(Cart.FREE_NAME, cart.lines[0].name)
        assertEquals("", cart.lines[0].descriptionText)
        assertEquals("Envío", cart.lines[1].descriptionText)
        // Un producto del catálogo que se llama «Varios» conserva su nombre tal cual.
        assertEquals("Varios", cart.lines[2].descriptionText)
    }

    @Test fun manualLineGetsDescriptionAndQuantity() {
        val s = manual().let { it.editLine(it.cart.lines[0].id, "  Envío ", 2000) }
        val line = s.cart.lines.single()
        assertEquals("Envío", line.name)
        assertEquals(2000L, line.quantityMilli)
        assertEquals(6000L, s.cart.totalMinor)
    }

    @Test fun blankDescriptionFallsBackToVarios() {
        val s = manual("Envío").let { it.editLine(it.cart.lines[0].id, "   ", 1000) }
        assertEquals(Cart.FREE_NAME, s.cart.lines.single().name)
    }

    @Test fun descriptionIsCappedAt80() {
        val s = manual().let { it.editLine(it.cart.lines[0].id, "x".repeat(200), 1000) }
        assertEquals(Cart.MAX_DESCRIPTION, s.cart.lines.single().name.length)
    }

    @Test fun catalogProductKeepsItsNameAndOnlyChangesQuantity() {
        val s = CartWithUndo().addProduct("p1", null, "Cuajada", "500 g", 2500, null, newId = id)
        val edited = s.editLine(s.cart.lines[0].id, "Otro nombre", 3000)
        val line = edited.cart.lines.single()
        assertEquals("Cuajada", line.name)
        assertEquals("500 g", line.variant)
        assertEquals(3000L, line.quantityMilli)
    }

    @Test fun zeroQuantityRemovesTheLineWithUndo() {
        val s = manual("Envío")
        val removed = s.editLine(s.cart.lines[0].id, "Envío", 0)
        assertTrue(removed.cart.isEmpty)
        assertTrue(removed.pushed)
        assertEquals("Envío", removed.undoLast().cart.lines.single().name)
    }

    @Test fun onlyDescriptionKeepsTheUndoStackButQuantityClearsIt() {
        val s = manual()
        val lineId = s.cart.lines[0].id
        val renamed = s.editLine(lineId, "Envío", 1000)
        assertEquals(1, renamed.stack.size)
        // «Deshacer» la alta sigue quitando la línea (ya con su descripción nueva).
        assertTrue(renamed.undoLast().cart.isEmpty)
        val requantified = s.editLine(lineId, "Envío", 2000)
        assertTrue(requantified.stack.isEmpty())
    }

    @Test fun unknownLineChangesNothing() {
        val s = manual()
        assertEquals(s.cart, s.editLine("nope", "x", 5000).cart)
    }
}

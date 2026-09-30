package com.cuadra.caja.domain

/** Algo que la tira «Deshacer» puede revertir. */
sealed interface UndoEntry {
    /** Se agregó `quantityMilli` de una línea (nueva, o sumada a una existente). */
    data class Added(val lineId: String, val label: String, val quantityMilli: Long, val amountMinor: Long, val createdLine: Boolean) : UndoEntry

    /** Se quitó una línea completa; se vuelve a poner en su lugar y con su cantidad. */
    data class Deleted(val line: CartLine, val index: Int) : UndoEntry

    /** Se vació el recibo. */
    data class Cleared(val cart: Cart) : UndoEntry
}

/**
 * `pushed`: la última operación apiló algo nuevo (la pantalla muestra entonces la tira).
 * El recibo junto con su pila de «deshacer» (inmutable: cada operación devuelve otro). Es la lógica que usa `CajaViewModel`; vive en el dominio
 * para poder probarla sin Android. Agregar apila; `undoLast` revierte exactamente lo último apilado. Cambiar una cantidad escribiéndola
 * vacía lo anterior (las cantidades ya no serían las de entonces).
 */
data class CartWithUndo(val cart: Cart = Cart(), val stack: List<UndoEntry> = emptyList(), val pushed: Boolean = false) {
    val last: UndoEntry? get() = stack.lastOrNull()

    private fun push(next: Cart, e: UndoEntry?) = CartWithUndo(next, if (e == null) stack else (stack + e).takeLast(MAX_STACK), pushed = e != null)

    fun addProduct(
        productId: String, barcode: String?, name: String, variant: String?, priceMinor: Long, costMinor: Long?,
        quantityMilli: Long = 1000, byWeight: Boolean = false, newId: () -> String = { java.util.UUID.randomUUID().toString() },
    ): CartWithUndo {
        val next = cart.addProduct(productId, barcode, name, variant, priceMinor, costMinor, quantityMilli, newId, byWeight)
        return push(next, added(cart, next, quantityMilli))
    }

    fun addFree(amountMinor: Long, description: String?, quantityMilli: Long = 1000, newId: () -> String = { java.util.UUID.randomUUID().toString() }): CartWithUndo {
        val next = cart.addFree(amountMinor, description, quantityMilli, newId)
        return push(next, added(cart, next, quantityMilli))
    }

    /** Qué línea cambió con una alta: la nueva, o la que subió de cantidad. */
    private fun added(before: Cart, after: Cart, qty: Long): UndoEntry.Added? {
        val created = after.lines.firstOrNull { l -> before.lines.none { it.id == l.id } }
        val line = created ?: after.lines.firstOrNull { l -> before.lines.any { it.id == l.id && it.quantityMilli != l.quantityMilli } } ?: return null
        return UndoEntry.Added(line.id, line.label, qty, SaleMath.lineTotal(line.unitPriceMinor, qty), created != null)
    }

    /** Quita la línea completa (con «Deshacer»). */
    fun deleteLine(lineId: String): CartWithUndo {
        val index = cart.lines.indexOfFirst { it.id == lineId }
        if (index < 0) return this
        return push(cart.remove(lineId), UndoEntry.Deleted(cart.lines[index], index))
    }

    /** Cantidad escrita: 0 quita la línea (deshacible); otro valor cambia la cantidad y vacía la pila. */
    fun setQuantity(lineId: String, milli: Long): CartWithUndo =
        if (milli <= 0) deleteLine(lineId) else CartWithUndo(cart.setQuantity(lineId, milli), emptyList())

    /**
     * Editar una línea desde el recibo (descripción de una línea manual y cantidad). 0 la quita (deshacible); si cambia la cantidad se vacía la pila
     * (como al escribirla); si solo cambia la descripción, la pila sigue valiendo.
     */
    fun editLine(lineId: String, description: String?, milli: Long): CartWithUndo {
        val line = cart.lines.firstOrNull { it.id == lineId } ?: return copy(pushed = false)
        if (milli <= 0) return deleteLine(lineId)
        val next = cart.editLine(lineId, description, milli)
        return if (line.quantityMilli != milli) CartWithUndo(next, emptyList()) else CartWithUndo(next, stack)
    }

    /** Un − o + de una unidad: no toca la pila. */
    fun changeQuantity(lineId: String, deltaMilli: Long): CartWithUndo = copy(cart = cart.changeQuantity(lineId, deltaMilli), pushed = false)

    fun clear(): CartWithUndo = if (cart.isEmpty) this else push(Cart(), UndoEntry.Cleared(cart))

    /** Revierte lo último; sin nada apilado no hace nada. */
    fun undoLast(): CartWithUndo {
        val e = last ?: return this
        val rest = stack.dropLast(1)
        val next = when (e) {
            is UndoEntry.Added -> {
                val line = cart.lines.firstOrNull { it.id == e.lineId }
                when {
                    line == null -> cart
                    e.createdLine && line.quantityMilli <= e.quantityMilli -> cart.remove(e.lineId)
                    else -> cart.changeQuantity(e.lineId, -e.quantityMilli)
                }
            }
            is UndoEntry.Deleted ->
                if (cart.lines.any { it.id == e.line.id }) cart
                else cart.copy(lines = cart.lines.toMutableList().also { it.add(e.index.coerceIn(0, it.size), e.line) })
            is UndoEntry.Cleared -> if (cart.isEmpty) e.cart else cart
        }
        return CartWithUndo(next, rest)
    }

    companion object {
        const val MAX_STACK = 10
    }
}

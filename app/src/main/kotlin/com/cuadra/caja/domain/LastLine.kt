package com.cuadra.caja.domain

/**
 * La «tira de la última línea» de la caja (Manual y Productos): muestra UNA sola línea del recibo, la última que se agregó o se tocó, con su cantidad
 * editable en pantalla. Aquí vive la lógica pura (qué línea es, cuánto suben o bajan − y +); el dibujo está en `ui/screens/LastLineStrip.kt`.
 */
object LastLine {
    /**
     * La línea de la tira después de un cambio del recibo (`before` → `after`): la línea NUEVA (la última si hay varias); si no hay, la que cambió de cantidad
     * (sumar el mismo producto otra vez, − / +, escribirla, deshacer); si no cambió nada, la misma de antes mientras exista. `null` = ninguna (la tira cae en la
     * última línea del recibo, ver [of]).
     */
    fun pick(previousId: String?, before: Cart, after: Cart): String? {
        val created = after.lines.lastOrNull { l -> before.lines.none { it.id == l.id } }
        if (created != null) return created.id
        val changed = after.lines.lastOrNull { l -> before.lines.any { it.id == l.id && it.quantityMilli != l.quantityMilli } }
        if (changed != null) return changed.id
        return previousId?.takeIf { id -> after.lines.any { it.id == id } }
    }

    /** La línea que muestra la tira: la elegida si sigue en el recibo; si no, la última del recibo (p. ej. al retomar una cuenta apartada). Sin líneas, ninguna. */
    fun of(cart: Cart, id: String?): CartLine? = cart.lines.firstOrNull { it.id == id } ?: cart.lines.lastOrNull()

    /** Cuánto suma o resta un toque de − / +: una unidad; en una línea con cantidad fraccionaria (peso), un cuarto (igual que en la hoja del recibo). */
    fun step(line: CartLine): Long = if (line.quantityMilli % 1000L == 0L) 1000L else 250L

    /** La cantidad que queda al tocar «+» (con tope en lo máximo que acepta la hoja de cantidad). */
    fun plus(line: CartLine): Long = minOf(line.quantityMilli + step(line), QuantityInput.MAX_MILLI)

    /** La cantidad que queda al tocar «−»; 0 = la línea se quita (con «Deshacer»). */
    fun minus(line: CartLine): Long = (line.quantityMilli - step(line)).coerceAtLeast(0L)

    /** ¿«+» ya no sube más (tope)? */
    fun atMax(line: CartLine): Boolean = line.quantityMilli >= QuantityInput.MAX_MILLI
}

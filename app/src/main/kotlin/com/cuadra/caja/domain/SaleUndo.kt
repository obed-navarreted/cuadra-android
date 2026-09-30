package com.cuadra.caja.domain

/**
 * «Anular esta venta»: quien cobró puede anular SU ÚLTIMA venta durante los primeros 5 minutos (con motivo; el dueño recibe aviso). Después, solo dueño y
 * admins la eliminan (flujo de siempre). El servidor lo exige igual (`UNDO_NOT_ALLOWED`) midiendo con el reloj de ESTE teléfono (el mismo que puso la
 * hora del cobro), así que anular sin conexión dentro del plazo también vale.
 */
object SaleUndo {
    const val WINDOW_MILLIS: Long = 5 * 60_000L

    /** Milisegundos que quedan para anularla (0 = ya no se puede). */
    fun remaining(completedAtMillis: Long?, nowMillis: Long): Long {
        if (completedAtMillis == null) return 0
        val elapsed = nowMillis - completedAtMillis
        return if (elapsed < 0) WINDOW_MILLIS else (WINDOW_MILLIS - elapsed).coerceAtLeast(0)
    }

    /**
     * Se ofrece si: está cobrada, la cobró esta persona, es su última venta cobrada en este teléfono, no tiene devoluciones y no pasaron los 5 minutos.
     */
    fun canUndo(status: String, completedById: String?, me: String?, isMyLast: Boolean, returnedMinor: Long, completedAtMillis: Long?, nowMillis: Long): Boolean =
        status == "COMPLETED" && me != null && completedById == me && isMyLast && returnedMinor == 0L && remaining(completedAtMillis, nowMillis) > 0

    /** Minutos (redondeados hacia arriba) que quedan, para el texto «Puedes anularla durante N min». */
    fun minutesLeft(completedAtMillis: Long?, nowMillis: Long): Int = ((remaining(completedAtMillis, nowMillis) + 59_999) / 60_000).toInt()
}

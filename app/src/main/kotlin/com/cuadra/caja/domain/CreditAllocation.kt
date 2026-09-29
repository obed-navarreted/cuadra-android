package com.cuadra.caja.domain

data class OpenCredit(val id: String, val createdAt: Long, val balanceMinor: Long)

data class Allocation(val creditId: String, val amountMinor: Long)

/**
 * Un abono al cliente se reparte del fiado más viejo al más nuevo (FIFO), igual que el servidor. Si sobra dinero (el cliente pagó de más),
 * el excedente se suma al último abono: el dinero ya se recibió y nunca se pierde; el servidor deja un aviso para revisarlo.
 */
object CreditAllocation {
    fun fifo(open: List<OpenCredit>, amountMinor: Long): List<Allocation> {
        require(amountMinor > 0) { "El abono debe ser mayor que cero" }
        val ordered = open.filter { it.balanceMinor > 0 }.sortedWith(compareBy({ it.createdAt }, { it.id }))
        if (ordered.isEmpty()) return emptyList()
        var remaining = amountMinor
        val out = mutableListOf<Allocation>()
        for (c in ordered) {
            if (remaining == 0L) break
            val take = minOf(remaining, c.balanceMinor)
            out += Allocation(c.id, take)
            remaining -= take
        }
        if (remaining > 0) out[out.lastIndex] = out.last().copy(amountMinor = out.last().amountMinor + remaining)
        return out
    }

    /** Saldo de un fiado: monto − abonos vigentes (0 si está condonado o cancelado). */
    fun balance(amountMinor: Long, status: String, paidMinor: Long): Long =
        if (status == "WRITTEN_OFF" || status == "CANCELLED") 0 else maxOf(0, amountMinor - paidMinor)

    /** Estado que corresponde al saldo: pagado cuando llega a 0. Condonado y cancelado no cambian. */
    fun statusFor(current: String, balanceMinor: Long): String = when (current) {
        "WRITTEN_OFF", "CANCELLED" -> current
        else -> if (balanceMinor == 0L) "PAID" else "OPEN"
    }
}

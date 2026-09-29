package com.cuadra.caja.domain

enum class PayMethod { CASH, TRANSFER, CARD, CREDIT, OTHER }

/** Un pago del cobro. `amountMinor` es la parte de la venta que cubre; en efectivo `tenderedMinor` es lo que entregó el cliente. */
data class PaymentEntry(
    val method: PayMethod,
    val amountMinor: Long,
    val tenderedMinor: Long? = null,
    val reference: String? = null,
    val otherLabel: String? = null,
    /** Solo en fiado: quién debe (texto libre, obligatorio salvo que haya cliente), su teléfono opcional y el cliente vinculado, si existe. */
    val debtorLabel: String? = null,
    val debtorPhone: String? = null,
    val customerId: String? = null,
) {
    /** Vuelto: solo el efectivo se entrega de más. */
    val changeMinor: Long get() = if (method == PayMethod.CASH) (tenderedMinor ?: amountMinor) - amountMinor else 0
}

sealed interface PaymentIssue {
    /** Faltan `missingMinor` por cubrir. */
    data class Missing(val missingMinor: Long) : PaymentIssue

    /** Los pagos suman `excessMinor` más que la venta (solo el efectivo puede pasarse, y como vuelto). */
    data class Excess(val excessMinor: Long) : PaymentIssue
    data object TenderedTooLow : PaymentIssue
    data object OtherNeedsLabel : PaymentIssue

    /** Un fiado necesita decir a quién se le fía (un nombre basta; el cliente es opcional). */
    data object DebtorRequired : PaymentIssue
    data object NothingToPay : PaymentIssue
}

/**
 * Reparto del cobro entre métodos (pago mixto). El primero toma el total; al agregar otro método, este toma "el resto"
 * y ambos siguen siendo editables. La validación es la misma que hace el servidor.
 */
class PaymentPlan(val totalMinor: Long, val entries: List<PaymentEntry> = emptyList()) {
    /** Lo que realmente se registra: un método agregado que quedó en cero no es un pago y se descarta al cobrar. */
    val effective: List<PaymentEntry> get() = entries.filter { it.amountMinor > 0 }

    val paidMinor: Long get() = entries.sumOf { it.amountMinor }
    val remainingMinor: Long get() = totalMinor - paidMinor
    val changeMinor: Long get() = entries.sumOf { it.changeMinor }

    /** Efectivo entregado en total menos vuelto = lo que realmente entra a la caja. */
    val cashInMinor: Long get() = entries.filter { it.method == PayMethod.CASH }.sumOf { it.amountMinor }

    fun issues(): List<PaymentIssue> {
        if (totalMinor <= 0) return if (entries.isEmpty()) emptyList() else listOf(PaymentIssue.NothingToPay)
        val out = mutableListOf<PaymentIssue>()
        if (entries.isEmpty()) return listOf(PaymentIssue.Missing(totalMinor))
        effective.forEach { e ->
            if (e.method == PayMethod.CASH && (e.tenderedMinor ?: e.amountMinor) < e.amountMinor) out += PaymentIssue.TenderedTooLow
            if (e.method == PayMethod.OTHER && e.otherLabel.isNullOrBlank()) out += PaymentIssue.OtherNeedsLabel
            if (e.method == PayMethod.CREDIT && e.debtorLabel.isNullOrBlank() && e.customerId == null) out += PaymentIssue.DebtorRequired
        }
        val rest = remainingMinor
        if (rest > 0) out += PaymentIssue.Missing(rest)
        if (rest < 0) out += PaymentIssue.Excess(-rest)
        return out
    }

    val isValid: Boolean get() = issues().isEmpty()

    /**
     * Agrega un método. El primero toma el total; los siguientes toman lo que falte. Si ya no falta nada entra en cero:
     * se reparte editando el monto del primero (así el cobro mixto siempre se puede armar desde "todo en efectivo").
     */
    fun withMethod(method: PayMethod): PaymentPlan {
        if (entries.any { it.method == method }) return this
        val rest = remainingMinor.coerceAtLeast(0)
        return PaymentPlan(totalMinor, entries + PaymentEntry(method, if (entries.isEmpty()) totalMinor else rest))
    }

    fun withoutMethod(method: PayMethod): PaymentPlan {
        val left = entries.filterNot { it.method == method }
        // Al quitar un método, el que queda toma el total si es el único.
        return PaymentPlan(totalMinor, if (left.size == 1) listOf(left[0].copy(amountMinor = totalMinor, tenderedMinor = left[0].tenderedMinor?.takeIf { it >= totalMinor })) else left)
    }

    /** Cambia el monto de un método y reparte la diferencia en el último otro método, para que siga sumando el total. */
    fun withAmount(method: PayMethod, amountMinor: Long): PaymentPlan {
        val amount = amountMinor.coerceIn(0, totalMinor)
        val edited = entries.map { if (it.method == method) it.copy(amountMinor = amount, tenderedMinor = it.tenderedMinor?.takeIf { t -> t >= amount }) else it }
        val others = edited.filter { it.method != method }
        if (others.isEmpty()) return PaymentPlan(totalMinor, edited)
        val target = others.last().method
        val othersSum = others.sumOf { if (it.method == target) 0L else it.amountMinor }
        val targetAmount = (totalMinor - amount - othersSum).coerceAtLeast(0)
        return PaymentPlan(totalMinor, edited.map { if (it.method == target) it.copy(amountMinor = targetAmount, tenderedMinor = it.tenderedMinor?.takeIf { t -> t >= targetAmount }) else it })
    }

    fun withTendered(tenderedMinor: Long?): PaymentPlan =
        PaymentPlan(totalMinor, entries.map { if (it.method == PayMethod.CASH) it.copy(tenderedMinor = tenderedMinor) else it })

    /** Datos de quien debe: el nombre que se escribe, su teléfono y el cliente elegido (si eligió uno). */
    fun withDebtor(label: String?, phone: String?, customerId: String?): PaymentPlan = PaymentPlan(totalMinor, entries.map {
        if (it.method == PayMethod.CREDIT) it.copy(debtorLabel = label?.trim()?.ifEmpty { null }, debtorPhone = phone?.trim()?.ifEmpty { null }, customerId = customerId) else it
    })

    fun withReference(method: PayMethod, reference: String?): PaymentPlan =
        PaymentPlan(totalMinor, entries.map { if (it.method == method) it.copy(reference = reference?.trim()?.ifEmpty { null }) else it })

    companion object {
        /** Cobro inicial: todo en efectivo, "exacto". */
        fun cash(totalMinor: Long) = PaymentPlan(totalMinor, if (totalMinor > 0) listOf(PaymentEntry(PayMethod.CASH, totalMinor)) else emptyList())
    }
}

/** Billetes sugeridos para "paga con", según la moneda del negocio. */
object CashSuggestions {
    private val DEFAULT = listOf(10L, 20L, 50L, 100L, 200L, 500L, 1000L)
    private val BY_CURRENCY = mapOf(
        "NIO" to listOf(10L, 20L, 50L, 100L, 200L, 500L, 1000L),
        "HNL" to listOf(20L, 50L, 100L, 200L, 500L),
        "GTQ" to listOf(10L, 20L, 50L, 100L, 200L),
        "CRC" to listOf(1000L, 2000L, 5000L, 10000L, 20000L, 50000L),
        "USD" to listOf(1L, 5L, 10L, 20L, 50L, 100L),
        "MXN" to listOf(20L, 50L, 100L, 200L, 500L, 1000L),
        "COP" to listOf(2000L, 5000L, 10000L, 20000L, 50000L, 100000L),
        "PEN" to listOf(10L, 20L, 50L, 100L, 200L),
        "EUR" to listOf(5L, 10L, 20L, 50L, 100L, 200L),
    )

    /**
     * Hasta 4 montos ≥ lo que hay que cobrar: el billete más chico que alcanza, y los siguientes. Nunca repite ni sugiere el propio monto
     * (para eso está "Exacto"). `decimals` convierte el billete a unidad menor.
     */
    fun forAmount(amountMinor: Long, currency: String, decimals: Int): List<Long> {
        val scale = pow10(decimals)
        val notes = (BY_CURRENCY[currency.uppercase()] ?: DEFAULT).map { it * scale }
        val out = linkedSetOf<Long>()
        val smallest = notes.firstOrNull { it >= amountMinor }
        if (smallest != null && smallest != amountMinor) out += smallest
        // Múltiplos del billete más grande que sigue teniendo sentido (ej. 2 × 500 para 900).
        for (note in notes) {
            val roundUp = ((amountMinor + note - 1) / note) * note
            if (roundUp != amountMinor && roundUp - amountMinor < note * 2) out += roundUp
        }
        return out.filter { it > amountMinor }.sorted().take(4)
    }

    private fun pow10(n: Int): Long {
        var r = 1L
        repeat(n) { r *= 10 }
        return r
    }
}

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

    /** Lo que aún no cubre ningún pago (0 si ya está cubierto o pasado). */
    val missingMinor: Long get() = remainingMinor.coerceAtLeast(0)

    /** Cuánto se pasan los pagos del total (0 si no se pasan). El efectivo cubre el exceso con «Recibido» (vuelto), no con el monto. */
    val excessMinor: Long get() = (-remainingMinor).coerceAtLeast(0)

    /** ¿Se puede pulsar «Cobrar»? Cubre exactamente el total, sin datos que falten. */
    val canConfirm: Boolean get() = isValid
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

    /**
     * «Completar con X»: lo que falta entra como monto de ese método (línea nueva, o suma a la que ya existe). Si no falta nada no cambia nada.
     * En efectivo lo «Recibido» vuelve a «exacto» (el monto cambió).
     */
    fun completeWith(method: PayMethod): PaymentPlan {
        val missing = missingMinor
        if (missing <= 0) return this
        if (entries.none { it.method == method }) return PaymentPlan(totalMinor, entries + PaymentEntry(method, missing))
        return PaymentPlan(totalMinor, entries.map { if (it.method == method) it.copy(amountMinor = it.amountMinor + missing, tenderedMinor = null) else it })
    }

    /**
     * Cambia el monto de un método. Con más de un método, la diferencia va al último otro para que siga sumando el total; con uno solo, lo que
     * no cubre queda como «falta» (pago parcial) y lo que se pasa como «se pasa por» (no se recorta: la persona ve el error).
     */
    fun withAmount(method: PayMethod, amountMinor: Long): PaymentPlan {
        val amount = amountMinor.coerceAtLeast(0)
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

/** Qué métodos de pago se ofrecen en el cobro según los módulos del negocio: sin el módulo Fiado no hay «Fiado» (solo se oculta; el servidor no lo impone, para que las ventas viejas en cola sincronicen). */
object PaymentMethods {
    fun available(modules: Map<String, Boolean>): List<PayMethod> = PayMethod.entries.filter { it != PayMethod.CREDIT || SettingsModules.isOn(modules, "credit") }

    /** Métodos con botón «Completar con…» (no «Otro»: pide un nombre). */
    fun completions(available: List<PayMethod>): List<PayMethod> = available.filter { it != PayMethod.OTHER }
}

/**
 * Montos sugeridos para «Recibido» en efectivo, pensados en billetes de verdad: lo que un cliente puede entregar y que a la cajera le sirve para dar vuelto.
 * «Exacto» es aparte (otro botón). Puro: sin Android.
 */
object CashSuggestions {
    /** Billetes de la moneda (en unidades mayores) y `base`: el «cien» de esa moneda, desde donde se redondea hacia arriba. */
    private data class Notes(val bills: List<Long>, val base: Long)

    private val DEFAULT = Notes(listOf(10L, 20L, 50L, 100L, 200L, 500L, 1000L), 100L)
    private val BY_CURRENCY = mapOf(
        "NIO" to DEFAULT,
        "HNL" to Notes(listOf(20L, 50L, 100L, 200L, 500L), 100L),
        "GTQ" to Notes(listOf(10L, 20L, 50L, 100L, 200L), 100L),
        "CRC" to Notes(listOf(1000L, 2000L, 5000L, 10000L, 20000L, 50000L), 1000L),
        "USD" to Notes(listOf(1L, 5L, 10L, 20L, 50L, 100L), 10L),
        "MXN" to Notes(listOf(20L, 50L, 100L, 200L, 500L, 1000L), 100L),
        "COP" to Notes(listOf(2000L, 5000L, 10000L, 20000L, 50000L, 100000L), 10000L),
        "PEN" to Notes(listOf(10L, 20L, 50L, 100L, 200L), 100L),
        "EUR" to Notes(listOf(5L, 10L, 20L, 50L, 100L, 200L), 10L),
        "CLP" to Notes(listOf(1000L, 2000L, 5000L, 10000L, 20000L), 10000L),
        "PYG" to Notes(listOf(10000L, 20000L, 50000L, 100000L, 200000L), 100000L),
        "DOP" to Notes(listOf(50L, 100L, 200L, 500L, 1000L, 2000L), 100L),
    )

    /**
     * Hasta 3 montos ESTRICTAMENTE mayores que lo que hay que cobrar, de menor a mayor y sin repetir, que se pueden entregar con billetes comunes:
     * - Cuenta chica (menos que el «cien» de la moneda): el siguiente múltiplo del billete más chico y los billetes que siguen (NIO 35 → 40, 50, 100; 95 → 100, 200, 500).
     * - Cuenta de «cien» para arriba: se redondea hacia arriba a 1×, 5× y 10× ese paso (NIO 250 → 300, 500, 1000; 1100 → 1200, 1500, 2000; 1400 → 1500, 2000).
     *   Con cuentas 100 veces mayores el paso sube (×10), para no sugerir 12,340 → 12,400.
     * `decimals` convierte a unidad menor.
     */
    fun forAmount(amountMinor: Long, currency: String, decimals: Int): List<Long> {
        val scale = pow10(decimals)
        val notes = BY_CURRENCY[currency.uppercase()] ?: DEFAULT
        val total = amountMinor.coerceAtLeast(0)
        fun up(step: Long): Long = (total / step + 1) * step  // siguiente múltiplo estrictamente mayor
        val out = sortedSetOf<Long>()
        if (total < notes.base * scale) {
            out += up(notes.bills.first() * scale)
            notes.bills.map { it * scale }.filter { it > total }.take(3).forEach { out += it }
        } else {
            var base = notes.base * scale
            while (total >= base * 100) base *= 10
            out += up(base)
            out += up(base * 5)
            out += up(base * 10)
        }
        return out.take(3)
    }

    private fun pow10(n: Int): Long {
        var r = 1L
        repeat(n) { r *= 10 }
        return r
    }
}

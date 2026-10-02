package com.cuadra.caja.domain

import com.cuadra.caja.core.model.Money
import java.time.LocalDate

/** Lo que se escribe en el editor de una promoción («Cerveza 3 por C$ 100»). */
data class PromotionForm(
    val name: String = "", val quantity: String = "3", val price: String = "", val active: Boolean = true,
    val startsOn: LocalDate? = null, val endsOn: LocalDate? = null, val productIds: List<String> = emptyList(),
)

enum class PromotionError { NAME, QUANTITY, PRICE, PRODUCTS, DATES }

sealed interface PromotionCheck {
    data class Valid(val name: String, val quantity: Int, val priceMinor: Long, val productIds: List<String>) : PromotionCheck
    data class Invalid(val errors: Set<PromotionError>) : PromotionCheck
}

/** Reglas del editor (las mismas que exige el servidor): nombre, 2 o más unidades, un precio, al menos un producto y fechas en orden. */
object PromotionEditor {
    const val MAX_NAME = 80
    const val MAX_QUANTITY = 999

    fun quantityOf(text: String): Int? = text.trim().toIntOrNull()?.takeIf { it in 2..MAX_QUANTITY }

    fun check(f: PromotionForm, decimals: Int): PromotionCheck {
        val errors = mutableSetOf<PromotionError>()
        val name = f.name.trim()
        if (name.isEmpty() || name.length > MAX_NAME) errors += PromotionError.NAME
        val qty = quantityOf(f.quantity)
        if (qty == null) errors += PromotionError.QUANTITY
        val price = Money.parse(f.price, decimals)?.minor?.takeIf { it > 0 }
        if (price == null) errors += PromotionError.PRICE
        if (f.productIds.isEmpty()) errors += PromotionError.PRODUCTS
        if (f.startsOn != null && f.endsOn != null && f.endsOn.isBefore(f.startsOn)) errors += PromotionError.DATES
        return if (errors.isEmpty()) PromotionCheck.Valid(name, qty!!, price!!, f.productIds.distinct()) else PromotionCheck.Invalid(errors)
    }

    /**
     * El ejemplo del editor, calculado con el motor: `2N + 1` unidades del producto más caro de la lista (7 con «3 por …»). Nulo si aún falta la cantidad, el
     * precio o los productos.
     */
    fun example(f: PromotionForm, decimals: Int, unitPrices: List<Long>): Example? {
        val qty = quantityOf(f.quantity) ?: return null
        val price = Money.parse(f.price, decimals)?.minor?.takeIf { it > 0 } ?: return null
        val unit = unitPrices.maxOrNull()?.takeIf { it > 0 } ?: return null
        val units = 2L * qty + 1
        val total = PromotionEngine.example(qty, price, unit, units)
        return Example(units, unit, total, noBenefit = price >= Math.multiplyExact(unit, qty.toLong()))
    }

    /** «Ejemplo: 7 × C$ 45 = C$ 245». `noBenefit`: el paquete cuesta lo mismo o más que las unidades sueltas (no descuenta nada). */
    data class Example(val units: Long, val unitPriceMinor: Long, val totalMinor: Long, val noBenefit: Boolean)

    /** Qué se responde al agregar un producto a la lista (buscado o escaneado). */
    enum class AddOutcome { ADDED, ALREADY, NOT_ELIGIBLE }

    fun add(current: List<String>, productId: String, pricing: String): Pair<List<String>, AddOutcome> = when {
        pricing != Pricing.FIXED -> current to AddOutcome.NOT_ELIGIBLE
        productId in current -> current to AddOutcome.ALREADY
        else -> (current + productId) to AddOutcome.ADDED
    }
}

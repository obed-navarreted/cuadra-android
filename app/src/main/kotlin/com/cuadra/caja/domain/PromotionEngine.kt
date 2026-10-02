package com.cuadra.caja.domain

import java.math.BigInteger
import java.time.LocalDate

/**
 * Una promoción por cantidad tal como la conoce el teléfono (se sincroniza): «3 por C$ 100» sobre uno o varios productos. Las fechas son jornadas del
 * negocio (ADR 0011); vale desde el inicio de `startsOn` hasta el final de `endsOn`.
 */
data class PromotionRule(
    val id: String, val name: String, val quantity: Int, val priceMinor: Long, val productIds: Set<String>,
    val active: Boolean = true, val startsOn: LocalDate? = null, val endsOn: LocalDate? = null,
) {
    /** ¿Se aplica en esta jornada? Activa y dentro de sus fechas. */
    fun validOn(day: LocalDate): Boolean = active && quantity >= 2 && priceMinor > 0 && productIds.isNotEmpty() &&
        (startsOn == null || !day.isBefore(startsOn)) && (endsOn == null || !day.isAfter(endsOn))

    /** Estado que se muestra en la lista. */
    fun state(day: LocalDate): PromotionState = when {
        endsOn != null && day.isAfter(endsOn) -> PromotionState.ENDED
        !active -> PromotionState.PAUSED
        startsOn != null && day.isBefore(startsOn) -> PromotionState.SCHEDULED
        else -> PromotionState.ACTIVE
    }
}

enum class PromotionState { ACTIVE, PAUSED, SCHEDULED, ENDED }

/** Una línea del recibo que puede entrar en una promoción: producto de precio fijo y cantidad entera (`units`). */
data class PromoLine(val lineId: String, val productId: String, val unitPriceMinor: Long, val units: Long)

/**
 * Lo que una promoción aplicó en la cuenta: `packs` paquetes de `quantity` unidades y cuánto se descontó en total. `lineIds`: las líneas que tocó (en el
 * orden del recibo), para dibujar «Promo 3 por C$ 100: −C$ 70» debajo de la última de ellas.
 */
data class AppliedPromotion(
    val promotionId: String, val name: String, val quantity: Int, val priceMinor: Long, val packs: Long, val discountMinor: Long, val lineIds: List<String>,
) {
    val units: Long get() = packs * quantity
}

/** El resultado: las promociones aplicadas y cuánto se descuenta de cada línea (ya repartido; la suma de las líneas = la suma de las promociones). */
data class PromoResult(val applied: List<AppliedPromotion> = emptyList(), val lineDiscounts: Map<String, Long> = emptyMap()) {
    val discountMinor: Long get() = applied.sumOf { it.discountMinor }
    val isEmpty: Boolean get() = applied.isEmpty()
}

/**
 * El recibo con las promociones aplicadas: `cart` lleva el descuento de cada línea en su `discountMinor` (así el total, el cobro, el vuelto, el fiado, el
 * recibo y la devolución proporcional usan el precio con promoción) y `promo` dice qué se aplicó. El recibo en pantalla sigue siendo el de siempre (sin
 * descuentos en las líneas): se vuelve a calcular con cada cambio, así quitar unidades o deshacer recalcula solo.
 */
data class PricedCart(val cart: Cart, val promo: PromoResult = PromoResult()) {
    val totalMinor: Long get() = cart.totalMinor
    val isEmpty: Boolean get() = cart.isEmpty
}

/**
 * Motor de precios de las promociones por cantidad (lógica pura, sin Android; ver `PromotionEngineTest`). Reglas (PENDIENTES.md, «Promociones por cantidad»):
 * - Se aplica sola a las líneas de los productos de la promoción; NO a productos por peso ni de precio abierto, ni a cantidades con decimales.
 * - Junta unidades de varios productos («3 cervezas cualesquiera»): un paquete puede mezclar marcas.
 * - Los paquetes se arman a favor del cliente: con las unidades MÁS CARAS primero. Un paquete solo se arma si le conviene al cliente (descuento > 0).
 * - Con varias promociones se busca la combinación con más descuento sin usar una unidad dos veces (búsqueda exhaustiva con memoria y un tope; si se pasa
 *   del tope, la mejor elección paso a paso).
 * - El descuento de cada paquete se reparte entre sus unidades en proporción a su precio (el residuo a la última): devolver una unidad reembolsa su parte.
 */
object PromotionEngine {
    /** Tope de estados de la búsqueda (carros enormes con muchas promociones encimadas: se pasa a la elección paso a paso). */
    const val SEARCH_LIMIT = 20_000

    /** Líneas del recibo que pueden entrar en una promoción. `pricingOf`: cómo se vende cada producto (FIXED / BY_WEIGHT / OPEN), nulo si no se sabe. */
    fun eligible(cart: Cart, pricingOf: (String) -> String?): List<PromoLine> = cart.lines.mapNotNull { l ->
        val product = l.productId ?: return@mapNotNull null
        if (l.byWeight || l.quantityMilli <= 0 || l.quantityMilli % 1000L != 0L) return@mapNotNull null
        if (pricingOf(product) != Pricing.FIXED) return@mapNotNull null
        PromoLine(l.id, product, l.unitPriceMinor, l.quantityMilli / 1000L)
    }

    /** El recibo con las promociones que valen hoy. Sin promociones devuelve el mismo recibo. */
    fun price(cart: Cart, promotions: List<PromotionRule>, day: LocalDate, pricingOf: (String) -> String?): PricedCart {
        val valid = promotions.filter { it.validOn(day) }
        if (valid.isEmpty() || cart.isEmpty) return PricedCart(cart)
        val result = apply(eligible(cart, pricingOf), valid)
        if (result.isEmpty) return PricedCart(cart)
        val priced = cart.copy(lines = cart.lines.map { l -> result.lineDiscounts[l.id]?.let { d -> l.copy(discountMinor = d) } ?: l })
        return PricedCart(priced.copy(discountMinor = cart.discountMinor.coerceIn(0, priced.subtotalMinor)), result)
    }

    /** Ejemplo para el editor: `n` unidades a `unitPriceMinor` con la promoción (p. ej. 7 × C$ 45 con «3 por C$ 100» = C$ 245). */
    fun example(quantity: Int, priceMinor: Long, unitPriceMinor: Long, units: Long = 2L * quantity + 1): Long {
        val rule = PromotionRule("example", "", quantity, priceMinor, setOf("p"))
        val r = apply(listOf(PromoLine("l", "p", unitPriceMinor, units)), listOf(rule))
        return Math.subtractExact(Math.multiplyExact(unitPriceMinor, units), r.discountMinor)
    }

    // ---------- la búsqueda ----------

    /** Un grupo de unidades iguales: una línea (precio y producto) y cuántas unidades le quedan libres. */
    private class Group(val line: PromoLine, val order: Int, val contention: Int)

    /** Un paquete: cuántas unidades toma de cada grupo (índice → cantidad) y su descuento. */
    private class Pack(val promo: Int, val take: IntArray, val takeCounts: LongArray, val discount: Long)

    fun apply(lines: List<PromoLine>, promotions: List<PromotionRule>): PromoResult {
        val rules = promotions.filter { it.quantity >= 2 && it.priceMinor > 0 }
        if (lines.isEmpty() || rules.isEmpty()) return PromoResult()
        // Cuántas promociones quieren cada producto: con precios iguales se toman primero las unidades menos disputadas.
        val contention = lines.associate { l -> l.lineId to rules.count { l.productId in it.productIds } }
        // Más caras primero; a igual precio, las menos disputadas; después, el orden del recibo.
        val groups = lines.withIndex().filter { (_, l) -> l.units > 0 && (contention[l.lineId] ?: 0) > 0 }
            .map { (i, l) -> Group(l, i, contention[l.lineId] ?: 0) }
            .sortedWith(compareByDescending<Group> { it.line.unitPriceMinor }.thenBy { it.contention }.thenBy { it.order })
        if (groups.isEmpty()) return PromoResult()
        val start = LongArray(groups.size) { groups[it].line.units }
        val members = rules.map { r -> groups.indices.filter { groups[it].line.productId in r.productIds }.toIntArray() }

        fun packFor(p: Int, left: LongArray): Pack? {
            val need = rules[p].quantity.toLong()
            var got = 0L
            var gross = 0L
            val idx = ArrayList<Int>()
            val cnt = ArrayList<Long>()
            for (g in members[p]) {
                if (got == need) break
                val take = minOf(left[g], need - got)
                if (take <= 0) continue
                idx += g; cnt += take
                got += take
                gross = Math.addExact(gross, Math.multiplyExact(groups[g].line.unitPriceMinor, take))
            }
            if (got < need) return null
            val discount = gross - rules[p].priceMinor
            if (discount <= 0) return null
            return Pack(p, idx.toIntArray(), cnt.toLongArray(), discount)
        }

        // Búsqueda: mejor descuento desde un estado (lo que queda de cada grupo). Con memoria; si se pasa del tope, paso a paso.
        val memo = HashMap<List<Long>, Pair<Long, Pack?>>()
        fun best(left: LongArray): Long {
            val key = left.toList()
            memo[key]?.let { return it.first }
            if (memo.size >= SEARCH_LIMIT) { return greedyValue(left, rules.indices, ::packFor) }
            var bestValue = 0L
            var bestPack: Pack? = null
            for (p in rules.indices) {
                val pack = packFor(p, left) ?: continue
                val next = left.copyOf()
                for (k in pack.take.indices) next[pack.take[k]] -= pack.takeCounts[k]
                val v = pack.discount + best(next)
                if (v > bestValue) { bestValue = v; bestPack = pack }
            }
            memo[key] = bestValue to bestPack
            return bestValue
        }
        best(start)

        // Se rehace el camino elegido (con la memoria; donde no hay, paso a paso).
        val left = start.copyOf()
        val packs = ArrayList<Pack>()
        while (true) {
            // En la memoria: lo que eligió la búsqueda (nulo = parar). Fuera de ella (se pasó del tope): paso a paso, igual que se valoró.
            val entry = memo[left.toList()]
            val pack = if (entry != null) entry.second else greedyPick(left, rules.indices, ::packFor)
            if (pack == null) break
            packs += pack
            for (k in pack.take.indices) left[pack.take[k]] -= pack.takeCounts[k]
        }
        return result(packs, groups, rules, lines)
    }

    /** Elección paso a paso: el paquete con más descuento, una y otra vez. */
    private fun greedyPick(left: LongArray, promos: IntRange, packFor: (Int, LongArray) -> Pack?): Pack? =
        promos.mapNotNull { packFor(it, left) }.maxByOrNull { it.discount }

    private fun greedyValue(start: LongArray, promos: IntRange, packFor: (Int, LongArray) -> Pack?): Long {
        val left = start.copyOf()
        var total = 0L
        while (true) {
            val pack = greedyPick(left, promos, packFor) ?: return total
            total += pack.discount
            for (k in pack.take.indices) left[pack.take[k]] -= pack.takeCounts[k]
        }
    }

    private fun result(packs: List<Pack>, groups: List<Group>, rules: List<PromotionRule>, lines: List<PromoLine>): PromoResult {
        if (packs.isEmpty()) return PromoResult()
        val lineDiscount = LinkedHashMap<String, Long>()
        val perPromo = LinkedHashMap<Int, Triple<Long, Long, MutableSet<String>>>()   // promo → (paquetes, descuento, líneas)
        for (pack in packs) {
            // Reparto del descuento del paquete entre sus unidades, en proporción a su precio (el residuo a la última parte).
            val gross = pack.take.indices.sumOf { groups[pack.take[it]].line.unitPriceMinor * pack.takeCounts[it] }
            var given = 0L
            pack.take.indices.forEach { k ->
                val g = groups[pack.take[k]]
                val part = g.line.unitPriceMinor * pack.takeCounts[k]
                val share = if (k == pack.take.lastIndex) pack.discount - given else mulDivHalfUp(pack.discount, part, gross)
                given += share
                lineDiscount.merge(g.line.lineId, share, Long::plus)
            }
            val cur = perPromo[pack.promo] ?: Triple(0L, 0L, LinkedHashSet())
            cur.third += pack.take.map { groups[it].line.lineId }
            perPromo[pack.promo] = Triple(cur.first + 1, cur.second + pack.discount, cur.third)
        }
        val order = lines.withIndex().associate { (i, l) -> l.lineId to i }
        val applied = perPromo.entries.sortedBy { (p, _) -> rules[p].name.lowercase() + rules[p].id }.map { (p, v) ->
            val r = rules[p]
            AppliedPromotion(r.id, r.name, r.quantity, r.priceMinor, v.first, v.second, v.third.sortedBy { order[it] ?: 0 })
        }.sortedBy { a -> a.lineIds.maxOfOrNull { order[it] ?: 0 } ?: 0 }
        return PromoResult(applied, lineDiscount.filterValues { it > 0 })
    }

    private fun mulDivHalfUp(a: Long, b: Long, c: Long): Long {
        if (c == 0L) return 0
        val qr = BigInteger.valueOf(a).multiply(BigInteger.valueOf(b)).divideAndRemainder(BigInteger.valueOf(c))
        val q = qr[0].toLong()
        return if (qr[1].shiftLeft(1) >= BigInteger.valueOf(c)) q + 1 else q
    }
}

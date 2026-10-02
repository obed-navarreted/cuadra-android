package com.cuadra.caja.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PromotionEngineTest {
    private val today = LocalDate.of(2026, 10, 1)
    private val beer3 = PromotionRule("p1", "Cerveza 3 por C$ 100", 3, 10_000, setOf("tona", "victoria", "premium"))
    private val pricing = mapOf("tona" to Pricing.FIXED, "victoria" to Pricing.FIXED, "premium" to Pricing.FIXED, "queso" to Pricing.BY_WEIGHT,
        "servicio" to Pricing.OPEN, "agua" to Pricing.FIXED)

    private fun cart(vararg lines: Triple<String, Long, Long>): Cart =
        lines.fold(Cart()) { c, (product, price, units) -> c.addProduct(product, null, product, null, price, null, units * 1000, newId = { "l-$product-$price" }) }

    private fun price(c: Cart, vararg promos: PromotionRule) = PromotionEngine.price(c, promos.toList(), today) { pricing[it] }

    @Test
    fun sevenBeersAtFortyFiveWithThreeForOneHundredCost245() {
        val r = price(cart(Triple("tona", 4_500, 7)), beer3)
        assertEquals(24_500, r.totalMinor)
        assertEquals(1, r.promo.applied.size)
        val a = r.promo.applied.single()
        assertEquals(2, a.packs)
        assertEquals(6, a.units)
        assertEquals(7_000, a.discountMinor)
        // El descuento va en la línea: el total, el cobro y la devolución usan el precio con promoción.
        assertEquals(7_000, r.cart.lines.single().discountMinor)
        assertEquals(24_500, r.cart.lines.single().totalMinor)
    }

    /** Caso confirmado por el dueño: «3 por C$ 100» con cervezas de C$ 40. */
    @Test
    fun ownerCaseTenBeersAtFortyCost340AndTwelveCost400AndTwoHaveNoPack() {
        fun beers(units: Long) = Cart().addProduct("tona", null, "Cerveza", null, 4_000, null, units * 1000, newId = { "c" })
        val ten = price(beers(10), beer3)
        // «Cerveza 10 x C$ 40.00 = C$ 400.00» y «Promo 3 por C$ 100: -C$ 60.00»: total C$ 340.00 (3 paquetes + 1 suelta).
        assertEquals(40_000, SaleMath.lineTotal(4_000, 10_000))
        assertEquals(3, ten.promo.applied.single().packs)
        assertEquals(6_000, ten.promo.discountMinor)
        assertEquals(34_000, ten.totalMinor)
        assertEquals(40_000, price(beers(12), beer3).totalMinor)
        val two = price(beers(2), beer3)
        assertTrue(two.promo.isEmpty)
        assertEquals(8_000, two.totalMinor)
        // Quitar una unidad (9: 3 paquetes = C$ 300) y deshacer (vuelve a C$ 340).
        val history = CartWithUndo(beers(10), emptyList())
        assertEquals(30_000, price(history.changeQuantity("c", -1_000).cart, beer3).totalMinor)
        val removed = history.deleteLine("c")
        assertEquals(0, price(removed.cart, beer3).totalMinor)
        assertEquals(34_000, price(removed.undoLast().cart, beer3).totalMinor)
    }

    @Test
    fun mixedBrandsFormPacksAndTheMostExpensiveUnitsGoFirst() {
        // 2 Toña (45) + 2 Premium (60): un paquete con las 2 Premium y 1 Toña (165 → 100, −65); queda 1 Toña suelta.
        val r = price(cart(Triple("tona", 4_500, 2), Triple("premium", 6_000, 2)), beer3)
        assertEquals(6_500, r.promo.discountMinor)
        assertEquals(4_500 * 2 + 6_000 * 2 - 6_500, r.totalMinor)
        // Reparto en proporción al precio: Premium 2 × 60 = 120 de 165 → 47.27 (47.27); Toña 45 de 165 → el resto.
        val premium = r.cart.lines.first { it.productId == "premium" }.discountMinor
        val tona = r.cart.lines.first { it.productId == "tona" }.discountMinor
        assertEquals(6_500, premium + tona)
        assertEquals(4_727, premium)
        assertEquals(1_773, tona)
        assertEquals(listOf("l-tona-4500", "l-premium-6000"), r.promo.applied.single().lineIds)
    }

    @Test
    fun aPackIsOnlyFormedWhenItHelpsTheCustomer() {
        // 3 aguas a 20 = 60 < 100: no conviene.
        val cheap = PromotionRule("p2", "Agua 3 por C$ 100", 3, 10_000, setOf("agua"))
        val r = price(cart(Triple("agua", 2_000, 3)), cheap)
        assertTrue(r.promo.isEmpty)
        assertEquals(6_000, r.totalMinor)
    }

    @Test
    fun twoCompetingPromotionsPickTheBestWithoutUsingAUnitTwice() {
        val two = PromotionRule("p2", "Toña 2 por C$ 70", 2, 7_000, setOf("tona"))
        // 3 Toña: «3 por 100» (−35) le gana a «2 por 70» (−20 y 1 suelta).
        val three = price(cart(Triple("tona", 4_500, 3)), beer3, two)
        assertEquals(3_500, three.promo.discountMinor)
        assertEquals(listOf("p1"), three.promo.applied.map { it.promotionId })
        // 4 Toña: dos de «2 por 70» (−40) le ganan a una de «3 por 100» y 1 suelta (−35).
        val four = price(cart(Triple("tona", 4_500, 4)), beer3, two)
        assertEquals(4_000, four.promo.discountMinor)
        assertEquals(listOf("p2"), four.promo.applied.map { it.promotionId })
        assertEquals(2, four.promo.applied.single().packs)
        // 5 Toña: «3 por 100» + «2 por 70» = −35 −20 = −55 (mejor que 2 × «2 por 70» = −40).
        val five = price(cart(Triple("tona", 4_500, 5)), beer3, two)
        assertEquals(5_500, five.promo.discountMinor)
        assertEquals(setOf("p1", "p2"), five.promo.applied.map { it.promotionId }.toSet())
        // Ninguna unidad se usa dos veces: las unidades en paquetes no pasan de las del recibo.
        assertEquals(5L, five.promo.applied.sumOf { it.units })
        assertEquals(5_500, five.cart.lines.sumOf { it.discountMinor })
    }

    @Test
    fun overlappingPromotionsTakeTheLessContestedUnitFirstAtTheSamePrice() {
        // «Victoria 2 por 70» solo para Victoria; «3 por 100» para todas. 1 Toña + 4 Victoria a 45: lo mejor es 3 por 100 con la Toña + 2 Victoria (−35)
        // y 2 por 70 con las otras 2 Victoria (−20) = −55.
        val vic = PromotionRule("p3", "Victoria 2 por C$ 70", 2, 7_000, setOf("victoria"))
        val r = price(cart(Triple("tona", 4_500, 1), Triple("victoria", 4_500, 4)), beer3, vic)
        assertEquals(5_500, r.promo.discountMinor)
    }

    @Test
    fun weighedOpenPriceAndFractionalLinesAreExcluded() {
        val all = PromotionRule("p4", "Todo 2 por C$ 10", 2, 1_000, setOf("queso", "servicio", "tona"))
        val c = Cart()
            .addProduct("queso", null, "Queso", null, 9_000, null, 2_000, newId = { "q" }, byWeight = true)
            .addProduct("servicio", null, "Servicio", null, 50_000, null, 2_000, newId = { "s" })
            .addProduct("tona", null, "Toña", null, 4_500, null, 1_500, newId = { "t" })
        val r = price(c, all)
        assertTrue(r.promo.isEmpty)
        assertEquals(c.totalMinor, r.totalMinor)
    }

    @Test
    fun removingUnitsRecalculatesAndUndoRestores() {
        val base = cart(Triple("tona", 4_500, 7))
        val history = CartWithUndo(base, emptyList())
        assertEquals(24_500, price(history.cart, beer3).totalMinor)
        // Bajar a 5: 1 paquete (100) + 2 sueltas (90) = 190.
        val fewer = history.changeQuantity("l-tona-4500", -2_000)
        assertEquals(19_000, price(fewer.cart, beer3).totalMinor)
        // Quitar la línea y deshacer: vuelve a 245.
        val removed = fewer.deleteLine("l-tona-4500")
        assertEquals(0, price(removed.cart, beer3).totalMinor)
        val undone = removed.undoLast()
        assertEquals(19_000, price(undone.cart, beer3).totalMinor)
    }

    @Test
    fun pausedOrOutOfDatePromotionsDoNotApply() {
        val c = cart(Triple("tona", 4_500, 3))
        assertEquals(13_500, price(c, beer3.copy(active = false)).totalMinor)
        assertEquals(13_500, price(c, beer3.copy(startsOn = today.plusDays(1))).totalMinor)
        assertEquals(13_500, price(c, beer3.copy(endsOn = today.minusDays(1))).totalMinor)
        // El último día todavía vale (jornada completa).
        assertEquals(10_000, price(c, beer3.copy(startsOn = today, endsOn = today)).totalMinor)
        assertEquals(PromotionState.SCHEDULED, beer3.copy(startsOn = today.plusDays(1)).state(today))
        assertEquals(PromotionState.ENDED, beer3.copy(endsOn = today.minusDays(1)).state(today))
        assertEquals(PromotionState.PAUSED, beer3.copy(active = false).state(today))
    }

    @Test
    fun theSaleDiscountStillAppliesOnTopOfThePromotion() {
        val c = cart(Triple("tona", 4_500, 7)).withDiscount(500)
        val r = price(c, beer3)
        assertEquals(24_000, r.totalMinor)
    }

    @Test
    fun manyUnitsStayFastAndExact() {
        val r = price(cart(Triple("tona", 4_500, 999), Triple("victoria", 4_600, 500)), beer3, PromotionRule("p2", "Toña 2 por C$ 70", 2, 7_000, setOf("tona")))
        val d = r.promo.discountMinor
        assertTrue(d > 0)
        assertEquals(d, r.cart.lines.sumOf { it.discountMinor })
        assertTrue(r.promo.applied.sumOf { it.units } <= 1_499)
    }

    @Test
    fun theEditorExampleUsesTheEngine() {
        assertEquals(24_500, PromotionEngine.example(3, 10_000, 4_500))
        assertEquals(4_500 * 5 - 4_000, PromotionEngine.example(2, 7_000, 4_500))
    }
}

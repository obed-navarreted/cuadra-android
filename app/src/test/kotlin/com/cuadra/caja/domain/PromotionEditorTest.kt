package com.cuadra.caja.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PromotionEditorTest {
    private val ok = PromotionForm("Cerveza 3 por C$ 100", "3", "100", productIds = listOf("tona"))

    @Test fun aCompleteFormIsValid() {
        val r = PromotionEditor.check(ok, 2) as PromotionCheck.Valid
        assertEquals(3, r.quantity)
        assertEquals(10_000, r.priceMinor)
        assertEquals(listOf("tona"), r.productIds)
    }

    @Test fun reportsEveryMissingPiece() {
        val r = PromotionEditor.check(PromotionForm("", "1", "", productIds = emptyList(), startsOn = LocalDate.of(2026, 10, 9), endsOn = LocalDate.of(2026, 10, 1)), 2) as PromotionCheck.Invalid
        assertEquals(PromotionError.entries.toSet(), r.errors)
    }

    @Test fun theExampleIsSevenUnitsOfTheMostExpensiveProduct() {
        val ex = PromotionEditor.example(ok, 2, listOf(4_000, 4_500))!!
        assertEquals(7, ex.units)
        assertEquals(4_500, ex.unitPriceMinor)
        assertEquals(24_500, ex.totalMinor)
        assertEquals(false, ex.noBenefit)
        assertTrue(PromotionEditor.example(ok.copy(price = "200"), 2, listOf(4_500))!!.noBenefit)
    }

    @Test fun addingTwiceSaysItIsAlreadyThereAndWeighedProductsAreRefused() {
        val (one, first) = PromotionEditor.add(emptyList(), "tona", Pricing.FIXED)
        assertEquals(PromotionEditor.AddOutcome.ADDED, first)
        assertEquals(PromotionEditor.AddOutcome.ALREADY, PromotionEditor.add(one, "tona", Pricing.FIXED).second)
        assertEquals(PromotionEditor.AddOutcome.NOT_ELIGIBLE, PromotionEditor.add(one, "queso", Pricing.BY_WEIGHT).second)
        assertEquals(PromotionEditor.AddOutcome.NOT_ELIGIBLE, PromotionEditor.add(one, "servicio", Pricing.OPEN).second)
    }
}

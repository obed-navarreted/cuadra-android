package com.cuadra.caja.domain

import com.cuadra.caja.domain.printing.PrinterSettings
import com.cuadra.caja.domain.printing.ReceiptFixtures
import com.cuadra.caja.domain.printing.SaleReceipts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Los comprobantes con promociones: papel y WhatsApp muestran cada producto a su precio y «Promo 3 por C$ 100: -C$ 70». */
class PromoReceiptTest {
    private val f = ReceiptFixtures

    private val sale = SaleView(
        "e5f6a7b8-1111-4222-8333-444455556666", "COMPLETED", null, 24_500, 0, 24_500, f.AT, "Kevin", null, null, null, null, null,
        listOf(SaleLineView("Toña", null, 7000, 4_500, 7_000, 24_500, "l1")), listOf(SalePaymentView("CASH", null, 24_500, 30_000, 5_500, null, null)),
        promotions = listOf(SalePromotionView("Cerveza 3 por C$ 100", 3, 10_000, 6, 7_000)),
    )

    @Test fun thePrintedReceiptShowsTheRegularLineAndThePromotion() {
        val text = SaleReceipts.receipt(sale, "Bar", f.ZONE, PrinterSettings(enabled = true), "es", f.NIO).previewText()
        assertTrue(text, text.contains("7 x C$ 45.00") && text.contains("C$ 315.00"))
        assertTrue(text, text.contains("Promo 3 por C$ 100.00:"))
        assertTrue(text, text.contains("-C$ 70.00"))
        assertTrue(text, text.contains("C$ 245.00"))
        val en = SaleReceipts.receipt(sale, "Bar", f.ZONE, PrinterSettings(enabled = true), "en", f.NIO).previewText()
        assertTrue(en, en.contains("Promo 3 for C$ 100.00:"))
    }

    @Test fun ownerCaseTenBeersAtFortyPrintsTheLineThePromoAndTheTotal() {
        val s = sale.copy(
            subtotalMinor = 34_000, totalMinor = 34_000, items = listOf(SaleLineView("Cerveza", null, 10_000, 4_000, 6_000, 34_000, "l1")),
            payments = listOf(SalePaymentView("CASH", null, 34_000, 34_000, 0, null, null)), promotions = listOf(SalePromotionView("Cerveza 3 por C$ 100", 3, 10_000, 9, 6_000)),
        )
        val text = SaleReceipts.receipt(s, "Bar", f.ZONE, PrinterSettings(enabled = true), "es", f.NIO).previewText()
        assertTrue(text, text.contains("10 x C$ 40.00") && text.contains("C$ 400.00"))
        assertTrue(text, text.contains("Promo 3 por C$ 100.00: -C$ 60.00"))
        assertTrue(text, text.contains("C$ 340.00"))
    }

    @Test fun theWhatsAppTicketPutsThePromotionUnderTheLinesItTouched() {
        val lines = PromoText.ticketLines(
            listOf(Triple("l1", "Toña ×7", 31_500L), Triple("l2", "Pan", 500L)),
            listOf(Triple(listOf("l1"), PromoText.label(3, "C$ 100.00", "es"), 7_000L)),
        )
        assertEquals(listOf("Toña ×7" to 31_500L, "Promo 3 por C$ 100.00:" to -7_000L, "Pan" to 500L), lines)
        val text = WhatsAppReceipt.text("{detalle}\nTotal: {monto}", "Bar", "1 oct", lines.map { it.first to (if (it.second < 0) "-C$ 70.00" else "C$ ${it.second / 100}.00") }, "C$ 245.00")
        assertTrue(text, text.contains("· Promo 3 por C$ 100.00: -C$ 70.00"))
        assertTrue(text, text.contains("· Toña ×7") && text.contains("C$ 315.00"))
    }
}

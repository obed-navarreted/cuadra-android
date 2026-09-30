package com.cuadra.caja.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Cobro con monto editable en TODAS las líneas: parcial, «Completar con…», exceso y cobro con varios métodos. */
class PaymentPartialTest {
    private fun transferOnly(total: Long) = PaymentPlan(total, listOf(PaymentEntry(PayMethod.TRANSFER, total)))

    @Test fun aSingleTransferLineCanBeLoweredAndTheRestIsMissing() {
        val plan = transferOnly(30000).withAmount(PayMethod.TRANSFER, 10000)
        assertEquals(10000L, plan.paidMinor)
        assertEquals(20000L, plan.missingMinor)
        assertEquals(0L, plan.excessMinor)
        assertFalse(plan.canConfirm)
        assertEquals(listOf<PaymentIssue>(PaymentIssue.Missing(20000)), plan.issues())
    }

    @Test fun partialTransferIsCompletedWithCash() {
        val plan = transferOnly(30000).withAmount(PayMethod.TRANSFER, 10000).completeWith(PayMethod.CASH)
        assertEquals(listOf(PayMethod.TRANSFER, PayMethod.CASH), plan.entries.map { it.method })
        assertEquals(20000L, plan.entries[1].amountMinor)
        assertTrue(plan.canConfirm)
        assertEquals(0L, plan.missingMinor)
        assertEquals(20000L, plan.cashInMinor)
    }

    @Test fun cardExactIsValidAndCardPartialIsNot() {
        assertTrue(PaymentPlan(5000, listOf(PaymentEntry(PayMethod.CARD, 5000))).canConfirm)
        assertFalse(PaymentPlan(5000, listOf(PaymentEntry(PayMethod.CARD, 3000))).canConfirm)
    }

    @Test fun creditPartialPlusCashRemainder() {
        val plan = PaymentPlan(30000, listOf(PaymentEntry(PayMethod.CREDIT, 12000)))
        assertEquals(18000L, plan.missingMinor)
        // sin nombre de deudor todavía no se puede cobrar, aunque se complete
        val full = plan.completeWith(PayMethod.CASH)
        assertEquals(0L, full.missingMinor)
        assertTrue(PaymentIssue.DebtorRequired in full.issues())
        assertTrue(full.withDebtor("Karla", null, null).canConfirm)
    }

    @Test fun nonCashOverTotalIsAnErrorAndBlocksConfirm() {
        val plan = transferOnly(30000).withAmount(PayMethod.TRANSFER, 35000)
        assertEquals(5000L, plan.excessMinor)
        assertEquals(listOf<PaymentIssue>(PaymentIssue.Excess(5000)), plan.issues())
        assertFalse(plan.canConfirm)
    }

    @Test fun cashGivesChangeThroughTenderedNotThroughAmount() {
        val plan = PaymentPlan.cash(30000).withTendered(50000)
        assertTrue(plan.canConfirm)
        assertEquals(20000L, plan.changeMinor)
    }

    @Test fun completeWithAddsToAnExistingLineAndDoesNothingWhenNothingIsMissing() {
        val plan = PaymentPlan(30000, listOf(PaymentEntry(PayMethod.CASH, 10000, tenderedMinor = 10000)))
        val done = plan.completeWith(PayMethod.CASH)
        assertEquals(30000L, done.entries.single().amountMinor)
        assertEquals(null, done.entries.single().tenderedMinor)
        assertEquals(done, done.completeWith(PayMethod.CARD).let { done })   // nada falta: sin cambio
        assertEquals(1, done.completeWith(PayMethod.CARD).entries.size)
    }

    @Test fun zeroDecimalCurrenciesWorkInWholeUnits() {
        // CLP / PYG: la unidad menor es el peso entero.
        val plan = PaymentPlan(15000, listOf(PaymentEntry(PayMethod.TRANSFER, 15000))).withAmount(PayMethod.TRANSFER, 6000)
        assertEquals(9000L, plan.missingMinor)
        assertEquals("6000", CashTender.toText(6000, 0))
        assertEquals(6000L, CashTender.parseMinor("6000", 0))
        assertTrue(plan.completeWith(PayMethod.CARD).canConfirm)
    }

    @Test fun threeLinesTransferCardCash() {
        var plan = PaymentPlan(50000, listOf(PaymentEntry(PayMethod.TRANSFER, 50000)))
        plan = plan.withAmount(PayMethod.TRANSFER, 10000).completeWith(PayMethod.CARD)          // 100 + 400
        plan = plan.withAmount(PayMethod.CARD, 15000)                                             // con otro método presente, la diferencia va al último
        assertEquals(50000L, plan.paidMinor)
        plan = plan.withAmount(PayMethod.CARD, 15000).withMethod(PayMethod.CASH)                 // entra en cero: ya está cubierto
        assertEquals(0L, plan.entries.last().amountMinor)
        plan = plan.withAmount(PayMethod.CARD, 5000)                                              // la diferencia va al último otro (efectivo)
        assertEquals(listOf(35000L, 5000L, 10000L), plan.entries.map { it.amountMinor })
        assertTrue(plan.canConfirm)
        assertEquals(10000L, plan.cashInMinor)
    }

    @Test fun removingALineFromTwoLeavesTheOtherCoveringEverything() {
        val plan = transferOnly(30000).withAmount(PayMethod.TRANSFER, 10000).completeWith(PayMethod.CASH).withoutMethod(PayMethod.CASH)
        assertEquals(30000L, plan.entries.single().amountMinor)
        assertTrue(plan.canConfirm)
    }
}

class ModuleVisibilityTest {
    private val off = mapOf("credit" to false)

    @Test fun creditMethodIsOfferedOnlyWithTheCreditModule() {
        assertTrue(PayMethod.CREDIT in PaymentMethods.available(emptyMap()))
        assertTrue(PayMethod.CREDIT in PaymentMethods.available(mapOf("credit" to true)))
        assertFalse(PayMethod.CREDIT in PaymentMethods.available(off))
        assertEquals(listOf(PayMethod.CASH, PayMethod.TRANSFER, PayMethod.CARD, PayMethod.OTHER), PaymentMethods.available(off))
    }

    @Test fun completionsExcludeOtherAndCreditWhenOff() {
        assertEquals(listOf(PayMethod.CASH, PayMethod.TRANSFER, PayMethod.CARD, PayMethod.CREDIT), PaymentMethods.completions(PaymentMethods.available(emptyMap())))
        assertEquals(listOf(PayMethod.CASH, PayMethod.TRANSFER, PayMethod.CARD), PaymentMethods.completions(PaymentMethods.available(off)))
    }

    @Test fun templatesLinksAndFiltersHideCreditAndExpenses() {
        assertEquals(listOf(MessageKind.TICKET), ModuleVisibility.messageKinds(off))
        assertEquals(MessageKind.entries, ModuleVisibility.messageKinds(emptyMap()))
        assertEquals(listOf("CASH", "CARD"), ModuleVisibility.historyMethods(off, listOf("CASH", "CREDIT", "CARD")))
        val links = listOf(null, "cuadra://caja", "cuadra://fiados", "cuadra://gastos", "cuadra://inventario")
        assertEquals(listOf(null, "cuadra://caja", "cuadra://gastos"), ModuleVisibility.scheduleLinks(off, links))
        assertEquals(listOf(null, "cuadra://caja", "cuadra://fiados"), ModuleVisibility.scheduleLinks(mapOf("expenses" to false), links))
        assertFalse(ModuleVisibility.expenses(mapOf("expenses" to false)))
        assertTrue(ModuleVisibility.expenses(emptyMap()))
    }
}

class WhatsAppRoutesTest {
    @Test fun routes() {
        assertEquals(ShareRoute.WHATSAPP_CHAT, WhatsAppRoutes.forText("50588551234", true))
        assertEquals(ShareRoute.WHATSAPP_PICKER, WhatsAppRoutes.forText(null, true))
        assertEquals(ShareRoute.SYSTEM_SHARE, WhatsAppRoutes.forText("50588551234", false))
        assertEquals(ShareRoute.SYSTEM_SHARE, WhatsAppRoutes.forText(null, false))
    }

    @Test fun chatLinkUsesInternationalDigitsAndEncodesTheText() {
        val phone = (PhoneNumbers.normalize("8855 1234", "NI") as PhoneResult.Valid).digits
        assertEquals("50588551234", phone)
        assertEquals("https://wa.me/50588551234?text=Hola%20a%0Ab", WhatsAppLinks.chat(phone, "Hola a\nb"))
    }

    @Test fun ticketUsesTheTemplateWithLinesAndTotal() {
        val labels = CardLabels("R", "E", "F", "A", "S", "Saldo", "Total", "%d")
        val t = ShareBuilders.ticket("La Esquina", "29/09", listOf("Cuajada ×2" to "C$ 50.00", "Queso" to "C$ 40.00"), "C$ 90.00", labels)
        assertEquals(MessageKind.TICKET, t.kind)
        val text = MessageTemplates.render(MessageTemplates.default(MessageKind.TICKET, "es"), t.vars)
        assertTrue(text.contains("· Cuajada ×2 — C$ 50.00"))
        assertTrue(text.contains("· Queso — C$ 40.00"))
        assertTrue(text.contains("Total: C$ 90.00"))
        assertTrue(text.startsWith("La Esquina — 29/09"))
    }
}

package com.cuadra.caja.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PaymentPlanTest {
    @Test fun cashIsExactByDefaultAndHasNoChange() {
        val plan = PaymentPlan.cash(34250)
        assertTrue(plan.isValid)
        assertEquals(0L, plan.changeMinor)
    }

    @Test fun cashCanBeHandedOverInExcessAndTheExcessIsTheChange() {
        val plan = PaymentPlan.cash(34250).withTendered(50000)
        assertTrue(plan.isValid)
        assertEquals(15750L, plan.changeMinor)
    }

    @Test fun aSecondMethodTakesWhatIsLeftAndBothStayEditable() {
        // Ejemplo de la captura 03: C$ 342.50 → C$ 200 en efectivo + el resto fiado.
        val plan = PaymentPlan.cash(34250).withAmount(PayMethod.CASH, 20000).withMethod(PayMethod.CREDIT).withDebtor("doña Karla", null, null)
        assertEquals(20000L, plan.entries.first { it.method == PayMethod.CASH }.amountMinor)
        assertEquals(14250L, plan.entries.first { it.method == PayMethod.CREDIT }.amountMinor)
        assertTrue(plan.isValid)
        // Editar el efectivo redistribuye la diferencia al otro método para seguir sumando el total.
        val edited = plan.withAmount(PayMethod.CASH, 10000)
        assertEquals(24250L, edited.entries.first { it.method == PayMethod.CREDIT }.amountMinor)
        assertTrue(edited.isValid)
    }

    @Test fun onlyCashMayExceedItsShareAndOnlyAsChange() {
        val plan = PaymentPlan.cash(34250).withAmount(PayMethod.CASH, 20000).withMethod(PayMethod.CREDIT).withDebtor("Ana", null, null).withTendered(50000)
        assertEquals(30000L, plan.changeMinor)          // paga 500 por 200 → vuelto 300
        assertEquals(20000L, plan.cashInMinor)          // a la caja entran 200, no 500
        assertTrue(plan.isValid)
    }

    @Test fun reportsWhatIsMissingAndWhatIsExcess() {
        val missing = PaymentPlan(10000, listOf(PaymentEntry(PayMethod.CASH, 4000)))
        assertEquals(listOf<PaymentIssue>(PaymentIssue.Missing(6000)), missing.issues())
        val excess = PaymentPlan(10000, listOf(PaymentEntry(PayMethod.CASH, 8000), PaymentEntry(PayMethod.CARD, 5000)))
        assertEquals(listOf<PaymentIssue>(PaymentIssue.Excess(3000)), excess.issues())
        assertEquals(listOf<PaymentIssue>(PaymentIssue.Missing(10000)), PaymentPlan(10000).issues())
    }

    @Test fun rejectsCashHandedOverShortAndOtherWithoutLabel() {
        val short = PaymentPlan(10000, listOf(PaymentEntry(PayMethod.CASH, 10000, tenderedMinor = 5000)))
        assertTrue(PaymentIssue.TenderedTooLow in short.issues())
        val other = PaymentPlan(10000, listOf(PaymentEntry(PayMethod.OTHER, 10000)))
        assertTrue(PaymentIssue.OtherNeedsLabel in other.issues())
        assertTrue(PaymentPlan(10000, listOf(PaymentEntry(PayMethod.OTHER, 10000, otherLabel = "Billetera"))).isValid)
    }

    @Test fun removingAMethodLeavesTheRemainingOneCoveringEverything() {
        val plan = PaymentPlan.cash(34250).withAmount(PayMethod.CASH, 20000).withMethod(PayMethod.CREDIT).withoutMethod(PayMethod.CREDIT)
        assertEquals(1, plan.entries.size)
        assertEquals(34250L, plan.entries[0].amountMinor)
        assertTrue(plan.isValid)
    }

    @Test fun cashSuggestionsRoundUpToRealBills() {
        // C$ 342.50 (34250) → 350, 400, 500... nunca el propio monto ni menos.
        val s = CashSuggestions.forAmount(34250, "NIO", 2)
        assertTrue(s.all { it > 34250 })
        assertTrue(s.contains(35000L) || s.contains(40000L) || s.contains(50000L))
        assertTrue(s.size <= 4)
        assertEquals(s.sorted(), s)
        assertTrue(CashSuggestions.forAmount(100, "NIO", 2).contains(1000L))
        assertTrue(CashSuggestions.forAmount(5000, "NIO", 2).all { it > 5000 })
    }

    @Test fun aSecondMethodCanBeAddedEvenWhenCashAlreadyCoversEverything() {
        // Desde "todo en efectivo" se toca Fiado: entra en cero y se reparte editando el efectivo (el flujo real de la caja).
        val plan = PaymentPlan.cash(34250).withMethod(PayMethod.CREDIT)
        assertEquals(0L, plan.entries.first { it.method == PayMethod.CREDIT }.amountMinor)
        assertTrue(plan.isValid)                                   // sigue cubierto: el fiado en cero no estorba
        assertEquals(listOf(PayMethod.CASH), plan.effective.map { it.method })   // y no se registra como pago
        val split = plan.withAmount(PayMethod.CASH, 20000)
        assertEquals(listOf(20000L, 14250L), split.effective.map { it.amountMinor })
        assertEquals(listOf<PaymentIssue>(PaymentIssue.DebtorRequired), split.issues())   // fiado sin decir a quién: no se puede cobrar
        assertTrue(split.withDebtor("doña Karla", "8855 1234", null).isValid)
    }

    @Test fun aZeroAmountOtherMethodWithoutLabelDoesNotBlockTheCharge() {
        assertTrue(PaymentPlan.cash(10000).withMethod(PayMethod.OTHER).isValid)
    }

    @Test fun aCreditNeedsANameButACustomerAloneIsEnough() {
        val base = PaymentPlan.cash(10000).withAmount(PayMethod.CASH, 4000).withMethod(PayMethod.CREDIT)
        assertEquals(listOf<PaymentIssue>(PaymentIssue.DebtorRequired), base.issues())
        assertEquals(listOf<PaymentIssue>(PaymentIssue.DebtorRequired), base.withDebtor("   ", null, null).issues())   // solo espacios no cuenta
        assertTrue(base.withDebtor("Karla", null, null).isValid)                                                        // basta un nombre
        assertTrue(base.withDebtor(null, null, "customer-1").isValid)                                                   // o un cliente ya registrado
        assertTrue(PaymentPlan.cash(10000).withMethod(PayMethod.CREDIT).isValid)                                        // fiado en cero: ni se registra ni exige nombre
    }
}

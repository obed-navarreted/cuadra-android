package com.cuadra.caja.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShareBuildersTest {
    private val labels = CardLabels("Recordatorio", "Estado de cuenta", "Fiado", "Abono recibido", "Deuda saldada", "Saldo pendiente", "Total", "desde hace %d días")

    @Test fun aReminderCarriesTheBalanceAndTheDaysIntoTheMessage() {
        val c = ShareBuilders.reminder("Pulpería La Esquina", "Marta López", "C$ 1,402.50", 16, labels)
        val text = MessageTemplates.render(MessageTemplates.default(c.kind, "es"), c.vars)
        assertEquals(MessageKind.REMINDER, c.kind)
        assertTrue(text.contains("Hola, Marta López."))
        assertTrue(text.contains("saldo de C$ 1,402.50 en Pulpería La Esquina (desde hace 16 días)."))
        assertFalse(text.contains("{"))
        assertEquals("C$ 1,402.50", c.card.totalValue)
        assertEquals("Marta López", c.card.subtitle)
    }

    @Test fun aReminderForADebtFromTodayDoesNotSayZeroDays() {
        val c = ShareBuilders.reminder("La Esquina", "Marta", "C$ 70.00", 0, labels)
        val text = MessageTemplates.render(MessageTemplates.default(c.kind, "es"), c.vars)
        assertTrue(text.contains("saldo de C$ 70.00 en La Esquina. Cualquier abono"))
        assertFalse(text.contains("0 días"))
    }

    @Test fun aStatementListsMovementsAndMarksPaymentsAsNegative() {
        val lines = listOf(StatementLine("22 sep", "Fiado", "C$ 1,040.00", false), StatementLine("26 sep", "Abono", "C$ 500.00", true))
        val c = ShareBuilders.statement("La Esquina", "Marta", lines, "C$ 540.00", labels)
        val text = MessageTemplates.render(MessageTemplates.default(c.kind, "es"), c.vars)
        assertTrue(text.contains("· 22 sep Fiado C$ 1,040.00"))
        assertTrue(text.contains("· 26 sep Abono −C$ 500.00"))
        assertTrue(text.contains("Saldo pendiente: C$ 540.00"))
        assertEquals(listOf("− C$ 500.00"), c.card.lines.map { it.right }.filter { it.startsWith("−") })
    }

    @Test fun aLongStatementShowsOnlyTheLatestMovements() {
        val many = (1..20).map { StatementLine("$it sep", "Fiado", "C$ $it.00", false) }
        val c = ShareBuilders.statement("La Esquina", "Marta", many, "C$ 210.00", labels)
        assertEquals(8, c.card.lines.size)
        assertTrue(c.card.lines.last().left.contains("20 sep"))
        assertFalse(c.vars.getValue("detalle").contains("· 1 sep"))
    }

    @Test fun aNewCreditMentionsWhatWasPaidRightAwayOnlyIfSomethingWas() {
        val items = listOf("Queso seco 0.75 lb" to "C$ 67.50", "Coca-Cola ×2" to "C$ 110.00")
        val withPaid = ShareBuilders.creditNew("La Esquina", "doña Karla", "28/09", "C$ 142.50", items, "C$ 200.00", labels, "Pagó")
        val t1 = MessageTemplates.render(MessageTemplates.default(withPaid.kind, "es"), withPaid.vars)
        assertTrue(t1.contains("le fiamos C$ 142.50"))
        assertTrue(t1.contains("· Queso seco 0.75 lb"))
        assertTrue(t1.contains("Pagó C$ 200.00. Gracias por su compra."))
        val none = ShareBuilders.creditNew("La Esquina", "doña Karla", "28/09", "C$ 142.50", items, null, labels, "Pagó")
        val t2 = MessageTemplates.render(MessageTemplates.default(none.kind, "es"), none.vars)
        assertFalse(t2.contains("Pagó"))
        assertTrue(t2.endsWith("Gracias por su compra."))
    }

    @Test fun aPaymentThatClearsTheDebtUsesThePaidOffMessage() {
        val partial = ShareBuilders.payment("La Esquina", "Marta", "28/09", "C$ 100.00", "C$ 42.50", false, labels)
        val paidOff = ShareBuilders.payment("La Esquina", "Marta", "28/09", "C$ 42.50", "C$ 0.00", true, labels)
        assertEquals(MessageKind.PAYMENT, partial.kind)
        assertEquals(MessageKind.PAID_OFF, paidOff.kind)
        assertTrue(MessageTemplates.render(MessageTemplates.default(partial.kind, "en"), partial.vars).contains("remaining balance is C$ 42.50"))
        assertEquals("Deuda saldada", paidOff.card.title)
        assertEquals("Abono recibido", partial.card.title)
    }
}

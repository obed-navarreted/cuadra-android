package com.cuadra.caja.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** «Requiere atención»: cada operación rechazada o para revisar se explica en lenguaje llano (qué, cuánto, cuándo, quién, por qué). */
class AttentionTest {
    private val sale = """{"status":"COMPLETED","items":[{"id":"i1","name":"Queso","unitPriceMinor":9000,"quantityMilli":1500}],"payments":[{"id":"p1","method":"CREDIT","amountMinor":13500}]}"""

    @Test fun aSaleOverTheCreditLimitSaysTheLimitAndTheBalance() {
        val item = Attention.describe(7, "SALE_UPSERT", sale, 1_000, "FAILED", "CREDIT_LIMIT_EXCEEDED",
            """{"limitMinor":50000,"balanceMinor":60000,"amountMinor":13500,"customerName":"Marta"}""", "Kevin", null)
        assertEquals(Attention.What.SALE, item.what)
        assertEquals(13500L, item.amountMinor)       // sin la venta en el teléfono, sale de los pagos enviados
        assertEquals(Attention.Reason.CREDIT_LIMIT, item.reason)
        assertEquals(50000L, item.limitMinor); assertEquals(60000L, item.balanceMinor); assertEquals("Marta", item.customerName)
        assertEquals("Kevin", item.byName)
        assertTrue(item.canRetry)
    }

    @Test fun aConflictCopyIsForReviewAndCannotBeRetried() {
        val item = Attention.describe(8, "SALE_UPSERT", sale, 1_000, "REVIEW", "SALE_CONFLICT_COPY", """{"copySaleId":"x"}""", null, 20000)
        assertEquals(Attention.Reason.CONFLICT_COPY, item.reason)
        assertEquals(20000L, item.amountMinor)      // el total de la venta en el teléfono, si está
        assertTrue(item.review); assertFalse(item.canRetry)
        assertEquals(Attention.Reason.ALREADY_CLOSED_ELSEWHERE, Attention.describe(9, "SALE_UPSERT", sale, 1, "REVIEW", "SALE_STALE", null, null, null).reason)
    }

    @Test fun eachKindSaysWhatItWas() {
        assertEquals(Attention.What.CREDIT_PAYMENT to 2000L, Attention.describe(1, "CREDIT_PAYMENT", """{"creditId":"c","amountMinor":2000}""", 1, "FAILED", "CREDIT_CLOSED", null, null, null).let { it.what to it.amountMinor })
        assertEquals(Attention.What.WITHDRAWAL, Attention.describe(1, "CASH_MOVEMENT_UPSERT", """{"kind":"WITHDRAWAL","amountMinor":1}""", 1, "FAILED", "FORBIDDEN", null, null, null).what)
        assertEquals(Attention.What.DEPOSIT, Attention.describe(1, "CASH_MOVEMENT_UPSERT", """{"kind":"DEPOSIT","amountMinor":1}""", 1, "FAILED", "FORBIDDEN", null, null, null).what)
        assertEquals("Cuajada", Attention.describe(1, "PRODUCT_PATCH", """{"set":{"name":"Cuajada"}}""", 1, "FAILED", "PRODUCT_NOT_FOUND", null, null, null).name)
        assertEquals(Attention.What.OTHER, Attention.describe(1, "TELEPORT", "no es json", 1, "FAILED", null, "{roto", null, null).what)
        // Una apartada (sin pagos): el total sale de sus líneas.
        assertEquals(13500L, Attention.describe(1, "SALE_UPSERT", """{"status":"PARKED","items":[{"unitPriceMinor":9000,"quantityMilli":1500}]}""", 1, "FAILED", "X", null, null, null).amountMinor)
    }

    @Test fun serverCodesMapToPlainReasonsAndUnknownOnesKeepTheCode() {
        assertEquals(Attention.Reason.MEMBER_DISABLED, Attention.reasonOf("MEMBER_NOT_ACTIVE"))
        assertEquals(Attention.Reason.MEMBER_DISABLED, Attention.reasonOf("ACCESS_DISABLED"))
        assertEquals(Attention.Reason.DEVICE_NOT_TRUSTED, Attention.reasonOf("DEVICE_NOT_TRUSTED"))
        assertEquals(Attention.Reason.NO_OPEN_CREDITS, Attention.reasonOf("NO_OPEN_CREDITS"))
        assertEquals(Attention.Reason.PAYMENT_MISMATCH, Attention.reasonOf("PAYMENT_MISMATCH"))
        val unknown = Attention.describe(1, "SALE_UPSERT", sale, 1, "FAILED", "SOMETHING_NEW", null, null, null)
        assertEquals(Attention.Reason.OTHER, unknown.reason); assertEquals("SOMETHING_NEW", unknown.code)
        assertNull(unknown.limitMinor)
    }
}

package com.cuadra.caja.domain

import com.cuadra.caja.data.remote.MemberRefDto
import com.cuadra.caja.data.remote.SaleDto
import com.cuadra.caja.data.remote.SaleItemDto
import com.cuadra.caja.data.remote.SalePaymentDto
import com.cuadra.caja.data.local.SaleEntity
import com.cuadra.caja.data.local.SaleItemEntity
import com.cuadra.caja.data.local.SalePaymentEntity
import com.cuadra.caja.data.sync.toRows
import com.cuadra.caja.data.sync.toView
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SaleViewsTest {
    private val dto = SaleDto(
        "s1", "CANCELLED", null, null, null, null, 10_000, 500, 9_500, MemberRefDto("m1", "Kevin"), MemberRefDto("m1", "Kevin"), "2026-09-29T07:00:00Z",
        MemberRefDto("m2", "Ana"), "2026-09-29T09:00:00Z", MemberRefDto("m2", "Ana"), "2026-09-29T10:00:00Z", "cobrada dos veces", null, null,
        "2026-09-29T06:59:00Z", "2026-09-29T10:00:00Z", 9,
        listOf(SaleItemDto("i1", "p1", null, "Queso", "fresco", 5_000, null, 2_000, 500, 9_500)),
        listOf(SalePaymentDto("pay1", "CASH", null, 9_500, 10_000, 500)),
    )

    @Test fun aServerSaleShowsWhoWhenAndWhyItWasDeleted() {
        val v = dto.toView()
        assertTrue(v.cancelled)
        assertEquals("Kevin", v.soldBy)
        assertEquals("Ana", v.cancelledBy)
        assertEquals(Instant.parse("2026-09-29T10:00:00Z").toEpochMilli(), v.cancelledAtMillis)
        assertEquals("cobrada dos veces", v.cancelReason)
        assertEquals("Ana", v.editedBy)
        assertEquals(Instant.parse("2026-09-29T09:00:00Z").toEpochMilli(), v.editedAtMillis)
        assertEquals(Instant.parse("2026-09-29T07:00:00Z").toEpochMilli(), v.atMillis)
        assertEquals(9_500, v.items.single().lineTotalMinor)
        val p = v.payments.single()
        assertEquals(10_000L, p.tenderedMinor)
        assertEquals(500L, p.changeMinor)
        assertFalse(v.unsynced)
    }

    @Test fun theSyncedRowKeepsTheCancellationTimes() {
        val rows = dto.toRows()
        assertEquals(Instant.parse("2026-09-29T10:00:00Z").toEpochMilli(), rows.sale.cancelledAt)
        assertEquals(Instant.parse("2026-09-29T09:00:00Z").toEpochMilli(), rows.sale.editedAt)
        assertEquals("Ana", rows.sale.cancelledByName)
    }

    @Test fun aPhoneSaleComputesLineTotalsLikeTheServerAndFlagsUnsyncedOnes() {
        val sale = SaleEntity("s2", "COMPLETED", null, null, 7_000, 0, 7_000, "m1", "Kevin", "Kevin", 5_000, null, null, null, null, 4_000, 5_000, 0)
        val items = listOf(SaleItemEntity("s2", "i1", null, null, "Refresco", null, 3_500, null, 2_000, 0, 0), SaleItemEntity("s2", "i2", null, null, "Pan", null, 333, null, 1_500, 100, 1))
        val pay = listOf(SalePaymentEntity("s2", "p1", "CASH", null, 7_000, 10_000, 3_000, null, 0))
        val v = sale.toView(items, pay)
        assertEquals(listOf(7_000L, 400L), v.items.map { it.lineTotalMinor })   // 333 × 1.5 = 499.5 → 500, − 100
        assertTrue(v.unsynced)
        assertEquals("Kevin", v.soldBy)
        assertNull(v.cancelledBy)
        assertEquals(3_000L, v.payments.single().changeMinor)
    }
}

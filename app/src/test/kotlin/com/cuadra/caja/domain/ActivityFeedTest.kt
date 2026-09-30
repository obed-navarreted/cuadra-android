package com.cuadra.caja.domain

import com.cuadra.caja.data.remote.ActivityEntryDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivityFeedTest {
    private fun e(action: String, detail: String? = null, platform: Boolean = false, actor: String? = "Ana") = ActivityEntryDto(1, action, null, detail, actor, platform, "2026-09-29T10:00:00Z")

    @Test fun reasonIsSplitFromTheDetail() {
        assertEquals("Revisión" to "un reporte", ActivityFeed.splitReason("Revisión — motivo: un reporte"))
        assertEquals(null to "solo motivo", ActivityFeed.splitReason("motivo: solo motivo"))
        assertEquals("sin motivo" to null, ActivityFeed.splitReason("sin motivo"))
        assertEquals(null to null, ActivityFeed.splitReason(null))
    }

    @Test fun platformViewAsIsHighlightedWithItsReason() {
        val r = ActivityFeed.row(e("platform.view_as", "motivo: revisar un error de cierre", platform = true, actor = null))
        assertTrue(r.platform); assertEquals(ActivityKind.PLATFORM, r.kind); assertEquals(ActivityAction.PLATFORM_VIEW_AS, r.action); assertEquals("revisar un error de cierre", r.reason)
    }

    @Test fun anActionWithThePlatformPrefixIsPlatformEvenIfTheFlagIsMissing() {
        assertTrue(ActivityFeed.row(e("platform.suspended")).platform)
        assertEquals(ActivityKind.PLATFORM, ActivityFeed.row(e("platform.something_new")).kind)
    }

    @Test fun amountsAndSubjects() {
        assertEquals(123456L, ActivityFeed.row(e("sale.complete", "total=123456")).amountMinor)
        assertEquals(500L, ActivityFeed.row(e("credit.payment", "amount=500")).amountMinor)
        val p = ActivityFeed.row(e("product.create", """{"name":"Queso \"seco\"","pricing":"FIXED","priceMinor":2500}"""))
        assertEquals("Queso \"seco\"", p.subject); assertNull(p.amountMinor)
        assertEquals("Marta", ActivityFeed.row(e("customer.create", "Marta")).subject)
    }

    @Test fun cancellationReasonComesFromTheDetail() {
        assertEquals("cobrada dos veces", ActivityFeed.row(e("sale.cancel", "COMPLETED: cobrada dos veces")).reason)
        assertNull(ActivityFeed.row(e("sale.cancel", "COMPLETED")).reason)
        assertEquals("error de digitación", ActivityFeed.row(e("expense.void", "error de digitación")).reason)
    }

    @Test fun changedFields() {
        assertEquals(listOf("priceMinor", "name"), ActivityFeed.row(e("product.update", """{"changes":{"priceMinor":{"from":1,"to":2},"name":{"from":"a","to":"b"}}}""")).fields)
        assertEquals(listOf("timezone", "day_cutoff"), ActivityFeed.row(e("business.update", "timezone,day_cutoff")).fields)
        assertTrue(ActivityFeed.row(e("business.update", null)).fields.isEmpty())
    }

    @Test fun kindsFilterTheList() {
        val rows = listOf("sale.complete", "product.update", "member.create", "business.update", "platform.view_as", "credit.payment", "device.revoke", "schedule.create", "weird").map { ActivityFeed.row(e(it)) }
        assertEquals(listOf("sale.complete", "credit.payment"), ActivityFeed.filter(rows, ActivityKind.SALES).map { it.rawAction })
        assertEquals(listOf("product.update"), ActivityFeed.filter(rows, ActivityKind.PRODUCTS).map { it.rawAction })
        assertEquals(listOf("member.create", "device.revoke"), ActivityFeed.filter(rows, ActivityKind.TEAM).map { it.rawAction })
        assertEquals(listOf("business.update", "schedule.create", "weird"), ActivityFeed.filter(rows, ActivityKind.BUSINESS).map { it.rawAction })
        assertEquals(listOf("platform.view_as"), ActivityFeed.filter(rows, ActivityKind.PLATFORM).map { it.rawAction })
        assertEquals(rows.size, ActivityFeed.filter(rows, ActivityKind.ALL).size)
    }

    @Test fun unknownActionsKeepTheirCodeAndNoSentence() {
        val r = ActivityFeed.row(e("weird.thing"))
        assertNull(r.action); assertEquals("weird.thing", r.rawAction)
    }

    @Test fun everyKnownActionHasAUniqueCode() {
        assertEquals(ActivityAction.entries.size, ActivityAction.entries.map { it.code }.toSet().size)
        assertEquals(ActivityAction.SALE_CANCEL, ActivityAction.of("sale.cancel"))
    }
}

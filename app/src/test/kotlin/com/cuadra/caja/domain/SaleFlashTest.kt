package com.cuadra.caja.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SaleFlashTest {
    @Test fun quickFlashWithoutWhatsApp() {
        assertEquals(1_200L, SaleFlash.durationMillis(offerWhatsApp = false, hasShare = true))
        assertEquals(1_200L, SaleFlash.durationMillis(offerWhatsApp = false, hasShare = false))
        assertFalse(SaleFlash.offersWhatsApp(false, true))
    }

    @Test fun whatsAppWaitsLongerOnlyWithSomethingToSend() {
        assertEquals(4_000L, SaleFlash.durationMillis(offerWhatsApp = true, hasShare = true))
        assertEquals(1_200L, SaleFlash.durationMillis(offerWhatsApp = true, hasShare = false))
        assertTrue(SaleFlash.offersWhatsApp(true, true))
    }

    @Test fun changeIsHighlightedOnlyWhenPositive() {
        assertTrue(SaleFlash.showsChange(2_750))
        assertFalse(SaleFlash.showsChange(0))
        assertFalse(SaleFlash.showsChange(null))
    }

    @Test fun noticeWithChangeMergesChangeAndUndo() {
        val n = SaleNotice.of(10_000, 2_750, doneAtMillis = 1_000, nowMillis = 2_000)
        assertEquals(SaleNoticeContent.Kind.CHANGE, n.kind)
        assertEquals(2_750L, n.changeMinor)
        assertEquals(10_000L, n.totalMinor)
        assertTrue(n.canUndo)
    }

    @Test fun noticeWithoutChangeShowsTheTotal() {
        val n = SaleNotice.of(10_000, 0, 1_000, 2_000)
        assertEquals(SaleNoticeContent.Kind.SOLD, n.kind)
        assertEquals(10_000L, n.totalMinor)
        assertTrue(n.canUndo)
    }

    @Test fun undoIsOfferedOnlyInsideTheFiveMinuteWindow() {
        assertTrue(SaleNotice.of(500, 0, 0, SaleUndo.WINDOW_MILLIS - 1).canUndo)
        assertFalse(SaleNotice.of(500, 0, 0, SaleUndo.WINDOW_MILLIS).canUndo)
        assertFalse(SaleNotice.of(500, 100, null, 0).canUndo)
    }

    @Test fun undoneSaleOnlyConfirms() {
        val n = SaleNotice.of(500, 100, 0, 1, undone = true)
        assertEquals(SaleNoticeContent.Kind.UNDONE, n.kind)
        assertFalse(n.canUndo)
        assertEquals(3_500L, SaleNotice.visibleMillis(true))
        assertEquals(8_000L, SaleNotice.visibleMillis(false))
    }

    @Test fun sentToTheRegisterIsAShortConfirmationWithTheNoteAndNoUndo() {
        val c = SaleNotice.of(12_000, 0, doneAtMillis = 1_000, nowMillis = 2_000, sent = true, note = "Mesa 4")
        assertEquals(SaleNoticeContent.Kind.SENT, c.kind)
        assertEquals("Mesa 4", c.note)
        assertFalse(c.canUndo)
        assertEquals(null, SaleNotice.of(12_000, 0, 1_000, 2_000, sent = true, note = "  ").note)
        assertEquals(SaleNotice.UNDONE_MILLIS, SaleNotice.visibleMillis(true))
    }
}

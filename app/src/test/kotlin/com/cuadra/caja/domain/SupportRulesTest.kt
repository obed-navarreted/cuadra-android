package com.cuadra.caja.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SupportRulesTest {
    private val ok = TicketDraft(TicketCategory.PROBLEM, "La caja no sincroniza desde ayer")
    private val diag = Diagnostics("0.1.0", "Android 15 (API 35)", "Google Pixel 8", "b-123", "2026-09-29T10:00:00Z", 3, "es")

    @Test fun validation() {
        assertNull(SupportRules.validate(ok))
        assertEquals(TicketError.MESSAGE_SHORT, SupportRules.validate(ok.copy(message = "  corto  ")))
        assertEquals(TicketError.MESSAGE_SHORT, SupportRules.validate(ok.copy(message = "123456789")))
        assertNull(SupportRules.validate(ok.copy(message = "1234567890")))
        assertEquals(TicketError.MESSAGE_LONG, SupportRules.validate(ok.copy(message = "x".repeat(4001))))
        assertEquals(TicketError.EMAIL, SupportRules.validate(ok.copy(email = "no-es-correo")))
        assertNull(SupportRules.validate(ok.copy(email = "  ana@correo.com ")))
        assertEquals(TicketError.PHONE, SupportRules.validate(ok.copy(phone = "123")))
        assertNull(SupportRules.validate(ok.copy(phone = "+505 8888 7777")))
    }

    @Test fun bodyTrimsAndOmitsEmptyOptionals() {
        val b = SupportRules.body(ok.copy(message = "  Mensaje largo suficiente  ", email = " ", phone = ""), "biz-1", "en", diag)
        assertEquals("PROBLEM", b.category); assertEquals("Mensaje largo suficiente", b.message); assertNull(b.replyToEmail); assertNull(b.replyToPhone)
        assertEquals("en", b.locale); assertEquals("biz-1", b.businessId)
    }

    @Test fun localeIsEsUnlessEnglish() {
        assertEquals("es", SupportRules.body(ok, null, "fr", null).locale)
        assertEquals("es", SupportRules.body(ok, null, "es", null).locale)
    }

    @Test fun diagnosticsAreOnlyIncludedWhenAskedAndCarryTheDeclaredFields() {
        val with = SupportRules.body(ok.copy(includeDiagnostics = true), "b-123", "es", diag).diagnostics!!
        listOf("app=0.1.0", "android=Android 15 (API 35)", "device=Google Pixel 8", "business=b-123", "lastSync=2026-09-29T10:00:00Z", "pendingOps=3").forEach { assertTrue(it, it in with) }
        assertNull(SupportRules.body(ok.copy(includeDiagnostics = false), "b-123", "es", diag).diagnostics)
    }

    @Test fun diagnosticsNeverContainSecrets() {
        // Solo lo declarado en `Diagnostics`: ningún campo puede llevar un token, un PIN ni un hash.
        val text = diag.text().lowercase()
        listOf("token", "pin", "password", "secret", "bearer", "device ").forEach { assertFalse(it, it in text) }
        assertEquals(setOf("appVersion", "androidVersion", "deviceModel", "businessId", "lastSyncAt", "pendingOps", "language"), Diagnostics::class.java.declaredFields.map { it.name }.filter { !it.startsWith("\$") }.toSet())
        assertTrue("sin última sincronización se escribe un guion", Diagnostics("1", "a", "m", null, null, 0, "es").text().contains("lastSync=-"))
    }
}

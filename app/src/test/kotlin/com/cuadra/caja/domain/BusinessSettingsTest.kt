package com.cuadra.caja.domain

import com.cuadra.caja.data.local.BusinessEntity
import com.cuadra.caja.data.remote.NotificationSettingsDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BusinessSettingsTest {
    private val entity = BusinessEntity(
        "b1", "Tienda", "NI", "NIO", "America/Managua", "es", "02:00:00", "OFF", "{}", "[\"LIST\",\"TYPE\"]", false,
        type = "Pulpería", creditDefaultDueDays = 15, creditOverdueDays = 30, creditLimitEnforced = false,
    )
    private val base = SettingsDraft.of(entity)

    @Test fun draftReflectsTheBusinessInCanonicalOrder() {
        assertEquals("02:00", base.dayCutoff)
        assertEquals(setOf("TYPE", "LIST"), base.posViews)
        assertEquals("15", base.creditDefaultDueDays)
        assertEquals("Pulpería", base.type)
        assertNull(BusinessSettingsRules.validate(base))
    }

    @Test fun nothingChangedMeansAnEmptyPatch() {
        assertTrue(BusinessSettingsRules.patch(base, base).isEmpty)
    }

    @Test fun onlyChangedFieldsTravel() {
        val p = BusinessSettingsRules.patch(base.copy(name = " Nueva ", creditLimitEnforced = true), base)
        assertEquals("Nueva", p.name)
        assertEquals(true, p.creditLimitEnforced)
        assertNull(p.timezone); assertNull(p.dayCutoff); assertNull(p.posViews); assertNull(p.creditOverdueDays); assertNull(p.creditRequiresCustomer)
        assertNull(p.clearCreditDefaultDueDays); assertNull(p.creditDefaultDueDays)
    }

    @Test fun clearingTheDueDaysUsesTheExplicitFlagAndSettingThemSendsTheNumber() {
        val cleared = BusinessSettingsRules.patch(base.copy(creditDefaultDueDays = ""), base)
        assertEquals(true, cleared.clearCreditDefaultDueDays); assertNull(cleared.creditDefaultDueDays)
        val set = BusinessSettingsRules.patch(base.copy(creditDefaultDueDays = "20"), base)
        assertEquals(20, set.creditDefaultDueDays); assertNull(set.clearCreditDefaultDueDays)
        // Sin valor antes y sin valor ahora: nada que decir.
        val none = base.copy(creditDefaultDueDays = "")
        assertTrue(BusinessSettingsRules.patch(none, none).isEmpty)
    }

    @Test fun dayRuleChangeIsDetectedAndPosViewsAreSentInCanonicalOrder() {
        val d = base.copy(timezone = "America/New_York", dayCutoff = "04:00", posViews = setOf("QUICK", "TYPE"))
        assertTrue(BusinessSettingsRules.changesDayRule(d, base))
        assertFalse(BusinessSettingsRules.changesDayRule(base.copy(name = "x"), base))
        val p = BusinessSettingsRules.patch(d, base)
        assertEquals("America/New_York", p.timezone); assertEquals("04:00", p.dayCutoff); assertEquals(listOf("TYPE", "QUICK"), p.posViews)
    }

    @Test fun anEmptyTypeIsNeverSent() {
        assertNull(BusinessSettingsRules.patch(base.copy(type = ""), base).type)
        assertEquals("Quesería", BusinessSettingsRules.patch(base.copy(type = "Quesería"), base).type)
    }

    @Test fun validation() {
        assertEquals(SettingsError.NAME, BusinessSettingsRules.validate(base.copy(name = "  ")))
        assertEquals(SettingsError.NAME, BusinessSettingsRules.validate(base.copy(name = "x".repeat(121))))
        assertEquals(SettingsError.TIMEZONE, BusinessSettingsRules.validate(base.copy(timezone = "Mars/Olympus")))
        assertEquals(SettingsError.CUTOFF, BusinessSettingsRules.validate(base.copy(dayCutoff = "25:00")))
        assertEquals(SettingsError.CUTOFF, BusinessSettingsRules.validate(base.copy(dayCutoff = "2:00")))
        assertEquals(SettingsError.POS_VIEWS, BusinessSettingsRules.validate(base.copy(posViews = emptySet())))
        assertEquals(SettingsError.DUE_DAYS, BusinessSettingsRules.validate(base.copy(creditDefaultDueDays = "366")))
        assertNull(BusinessSettingsRules.validate(base.copy(creditDefaultDueDays = "0")))
        assertNull(BusinessSettingsRules.validate(base.copy(creditDefaultDueDays = "")))
        assertEquals(SettingsError.OVERDUE_DAYS, BusinessSettingsRules.validate(base.copy(creditOverdueDays = "0")))
        assertEquals(SettingsError.OVERDUE_DAYS, BusinessSettingsRules.validate(base.copy(creditOverdueDays = "")))
        assertEquals(SettingsError.OVERDUE_DAYS, BusinessSettingsRules.validate(base.copy(creditOverdueDays = "1a")))
        assertNull(BusinessSettingsRules.validate(base.copy(creditOverdueDays = "365")))
    }

    @Test fun dstTipOnlyForZonesThatChangeClocksAndACutoffBeforeFour() {
        assertTrue(DayRuleAdvice.observesDst("America/New_York", 2026))
        assertTrue(DayRuleAdvice.observesDst("America/Santiago", 2026))   // hemisferio sur: el verano cae en enero
        assertFalse(DayRuleAdvice.observesDst("America/Managua", 2026))
        assertFalse(DayRuleAdvice.observesDst("UTC", 2026))
        assertFalse(DayRuleAdvice.observesDst("no/zone", 2026))
        assertTrue(DayRuleAdvice.dstCutoffTip("Europe/Madrid", "02:00", 2026))
        assertTrue(DayRuleAdvice.dstCutoffTip("Europe/Madrid", "03:59", 2026))
        assertFalse(DayRuleAdvice.dstCutoffTip("Europe/Madrid", "04:00", 2026))
        assertFalse(DayRuleAdvice.dstCutoffTip("America/Managua", "02:00", 2026))
        assertFalse(DayRuleAdvice.dstCutoffTip("Europe/Madrid", "nope", 2026))
    }

    @Test fun timeTextSanitizingAddsTheColon() {
        assertEquals("02:00", TimeText.sanitize("0200"))
        assertEquals("02", TimeText.sanitize("02"))
        assertEquals("21:3", TimeText.sanitize("213"))
        assertEquals("21:30", TimeText.sanitize("21:30abc99"))
        assertEquals("", TimeText.sanitize("ab"))
        assertTrue(TimeText.isValid("23:59")); assertFalse(TimeText.isValid("24:00")); assertFalse(TimeText.isValid("7:00")); assertFalse(TimeText.isValid("07:60"))
    }

    @Test fun timeZoneSuggestions() {
        val empty = TimeZones.suggest("", "America/Managua", "NI")
        assertEquals(listOf("America/Managua"), empty)
        assertTrue("America/Mexico_City" in TimeZones.suggest("", "America/Managua", "MX"))
        assertEquals("America/Managua", TimeZones.suggest("managua", "UTC", "NI").first())
        assertTrue(TimeZones.suggest("new york", "UTC", "NI").contains("America/New_York"))
        assertTrue(TimeZones.suggest("zzzz", "UTC", "NI").isEmpty())
        assertTrue(TimeZones.suggest("a", "UTC", "NI").size <= TimeZones.MAX)
        assertEquals("UTC−6", TimeZones.offsetLabel("America/Managua"))
        assertEquals("UTC+5:30", TimeZones.offsetLabel("Asia/Kolkata"))
    }

    @Test fun notifyRules() {
        val d = NotifyDraft.of(NotificationSettingsDto("21:30:00", "07:00", true, "21:00", "08:00", 24))
        assertEquals("21:30", d.quietStart)
        assertNull(NotifyRules.validate(d))
        assertEquals(NotifyError.QUIET_START, NotifyRules.validate(d.copy(quietStart = "9:30")))
        assertEquals(NotifyError.QUIET_END, NotifyRules.validate(d.copy(quietEnd = "")))
        assertEquals(NotifyError.SUMMARY_TIME, NotifyRules.validate(d.copy(summaryTime = "99:99")))
        assertEquals(NotifyError.STALE_HOURS, NotifyRules.validate(d.copy(staleHours = "0")))
        assertEquals(NotifyError.STALE_HOURS, NotifyRules.validate(d.copy(staleHours = "721")))
        assertEquals(NotifyError.STALE_HOURS, NotifyRules.validate(d.copy(staleHours = "-5")))
        // El recordatorio de cierre que el servidor ya tenía se conserva al guardar.
        assertEquals("08:00", d.toDto().shiftReminderTime)
        assertEquals(24, d.toDto().staleHours)
    }

    @Test fun modulesDefaults() {
        assertTrue(SettingsModules.isOn(emptyMap(), "credit")); assertTrue(SettingsModules.isOn(emptyMap(), "team")); assertFalse(SettingsModules.isOn(emptyMap(), "inventory"))
        assertFalse(SettingsModules.isOn(mapOf("credit" to false), "credit")); assertTrue(SettingsModules.isOn(mapOf("inventory" to true), "inventory"))
        assertFalse("shifts" in SettingsModules.KEYS)
    }

    @Test fun posViewsMapToTheTwoRegisterTabs() {
        assertEquals(listOf(RegisterTab.MANUAL, RegisterTab.PRODUCTS), PosViews.tabs(listOf("TYPE", "QUICK", "LIST")))
        assertEquals(listOf(RegisterTab.MANUAL, RegisterTab.PRODUCTS), PosViews.tabs(listOf("LIST", "TYPE")))
        assertEquals(listOf(RegisterTab.PRODUCTS), PosViews.tabs(listOf("QUICK")))
        assertEquals(listOf(RegisterTab.PRODUCTS), PosViews.tabs(listOf("LIST")))
        assertEquals(listOf(RegisterTab.MANUAL), PosViews.tabs(listOf("TYPE")))
        // Nada (o algo desconocido): al menos una pestaña, «Manual».
        assertEquals(listOf(RegisterTab.MANUAL), PosViews.tabs(emptyList()))
        assertEquals(listOf(RegisterTab.MANUAL), PosViews.tabs(listOf("OTRA")))
    }

    @Test fun productsToggleSwitchesQuickAndListTogether() {
        assertEquals(setOf("TYPE", "QUICK", "LIST"), PosViews.withProducts(setOf("TYPE"), true))
        assertEquals(setOf("TYPE"), PosViews.withProducts(setOf("TYPE", "QUICK"), false))
        assertEquals(emptySet<String>(), PosViews.withProducts(setOf("LIST"), false))
    }
}

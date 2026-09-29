package com.cuadra.caja.domain

import com.cuadra.caja.data.remote.ScheduleAudienceDto
import com.cuadra.caja.data.remote.ScheduleDto
import com.cuadra.caja.data.remote.ScheduleRuleDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleDraftTest {
    private val ok = ScheduleDraft(title = "Hola", body = "Cuenten el fondo", all = true, time = "09:00")

    @Test fun aCompleteDailyDraftIsValid() = assertNull(ScheduleDrafts.validate(ok))

    @Test fun titleBodyAndAudienceAreRequiredInThatOrder() {
        assertEquals(DraftError.TITLE, ScheduleDrafts.validate(ok.copy(title = " ")))
        assertEquals(DraftError.TITLE, ScheduleDrafts.validate(ok.copy(title = "x".repeat(101))))
        assertEquals(DraftError.BODY, ScheduleDrafts.validate(ok.copy(body = "")))
        assertEquals(DraftError.AUDIENCE, ScheduleDrafts.validate(ok.copy(all = false)))
        assertNull(ScheduleDrafts.validate(ok.copy(all = false, roles = setOf("CASHIER"))))
        assertNull(ScheduleDrafts.validate(ok.copy(all = false, memberIds = setOf("m1"))))
    }

    @Test fun timeAndDateMustHaveTheRightFormat() {
        assertEquals(DraftError.TIME, ScheduleDrafts.validate(ok.copy(time = "9:00")))
        assertEquals(DraftError.TIME, ScheduleDrafts.validate(ok.copy(time = "24:00")))
        assertEquals(DraftError.TIME, ScheduleDrafts.validate(ok.copy(time = "09:60")))
        assertEquals(DraftError.DATE, ScheduleDrafts.validate(ok.copy(whenMode = ScheduleWhen.ONCE, date = "2026-13-01")))
        assertNull(ScheduleDrafts.validate(ok.copy(whenMode = ScheduleWhen.ONCE, date = "2026-10-05")))
        assertEquals(DraftError.END_DATE, ScheduleDrafts.validate(ok.copy(endDate = "mañana")))
    }

    @Test fun repeatSpecificFieldsAreChecked() {
        assertEquals(DraftError.DAYS, ScheduleDrafts.validate(ok.copy(repeat = ScheduleRepeat.WEEKLY)))
        assertEquals(DraftError.DAY_OF_MONTH, ScheduleDrafts.validate(ok.copy(repeat = ScheduleRepeat.MONTHLY, dayOfMonth = "32")))
        assertEquals(DraftError.EVERY_DAYS, ScheduleDrafts.validate(ok.copy(repeat = ScheduleRepeat.EVERY_N, everyDays = "0")))
        assertNull(ScheduleDrafts.validate(ok.copy(repeat = ScheduleRepeat.EVERY_N, everyDays = "3")))
    }

    @Test fun sendingNowOnlyNeedsTheMessageAndTheAudience() {
        assertNull(ScheduleDrafts.validate(ok.copy(whenMode = ScheduleWhen.NOW, time = "")))
        assertEquals("ONCE", ScheduleDrafts.rule(ok.copy(whenMode = ScheduleWhen.NOW)).type)
    }

    @Test fun rulesAreBuiltInTheServerFormat() {
        val weekly = ScheduleDrafts.rule(ok.copy(repeat = ScheduleRepeat.WEEKLY, days = setOf(5, 1), endDate = " 2026-12-31 "))
        assertEquals(ScheduleRuleDto(type = "WEEKLY", time = "09:00", days = listOf(1, 5), endDate = "2026-12-31"), weekly)
        assertEquals(ScheduleRuleDto(type = "ONCE", at = "2026-10-05T09:00"), ScheduleDrafts.rule(ok.copy(whenMode = ScheduleWhen.ONCE, date = "2026-10-05")))
        assertEquals(ScheduleRuleDto(type = "MONTHLY", time = "09:00", dayOfMonth = 15), ScheduleDrafts.rule(ok.copy(repeat = ScheduleRepeat.MONTHLY, dayOfMonth = "15")))
        assertEquals(ScheduleRuleDto(type = "EVERY_N_DAYS", time = "09:00", everyDays = 3), ScheduleDrafts.rule(ok.copy(repeat = ScheduleRepeat.EVERY_N, everyDays = "3")))
    }

    @Test fun theInputCarriesTheTrimmedTextAudienceAndLink() {
        val input = ScheduleDrafts.toInput(ok.copy(title = " Hola ", all = false, roles = setOf("CASHIER", "ADMIN"), link = "cuadra://caja"))
        assertEquals("Hola", input.title)
        assertEquals(ScheduleAudienceDto(all = null, roles = listOf("ADMIN", "CASHIER"), memberIds = emptyList()), input.audience)
        assertEquals("cuadra://caja", input.deepLink)
    }

    @Test fun editingAScheduleRoundTripsThroughTheDraft() {
        val dto = ScheduleDto(
            "s1", "Título", "Texto", "cuadra://cierre", ScheduleAudienceDto(roles = listOf("CASHIER"), memberIds = listOf("m1")),
            ScheduleRuleDto(type = "WEEKLY", time = "21:00", days = listOf(1, 3), endDate = "2027-01-01"), "America/Managua", null, null, true, 0, 0, "2026-09-01T00:00:00Z",
        )
        val draft = ScheduleDrafts.draftOf(dto)
        assertEquals("s1", draft.id)
        assertEquals(ScheduleRepeat.WEEKLY, draft.repeat)
        assertNull(ScheduleDrafts.validate(draft))
        assertEquals(dto.rule, ScheduleDrafts.rule(draft))
        val once = ScheduleDrafts.draftOf(dto.copy(rule = ScheduleRuleDto(type = "ONCE", at = "2026-10-05T09:30")))
        assertEquals(ScheduleWhen.ONCE, once.whenMode)
        assertEquals("2026-10-05" to "09:30", once.date to once.time)
    }

    @Test fun summariesAreDataNotText() {
        assertEquals(RuleSummary.Daily("21:00"), ScheduleDrafts.summary(ScheduleRuleDto(type = "DAILY", time = "21:00")))
        assertEquals(RuleSummary.Weekly(listOf(1, 4), "09:00"), ScheduleDrafts.summary(ScheduleRuleDto(type = "WEEKLY", time = "09:00", days = listOf(4, 1))))
        assertEquals(RuleSummary.Once("2026-10-05", "09:00"), ScheduleDrafts.summary(ScheduleRuleDto(type = "ONCE", at = "2026-10-05T09:00")))
        assertEquals(RuleSummary.EveryN(3, "10:00"), ScheduleDrafts.summary(ScheduleRuleDto(type = "EVERY_N_DAYS", time = "10:00", everyDays = 3)))
        assertTrue(ScheduleDrafts.summary(ScheduleRuleDto(type = "MONTHLY", time = "08:00", dayOfMonth = 31)) is RuleSummary.Monthly)
    }
}

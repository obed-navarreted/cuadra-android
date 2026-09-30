package com.cuadra.caja

import com.cuadra.caja.data.remote.ApiFailure
import com.cuadra.caja.ui.ErrorMessage
import com.cuadra.caja.ui.teamError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class TeamErrorsTest {
    private fun e(code: String, feature: String? = null, limit: Int? = null) = ApiFailure.Http(403, code, "x", feature, limit).teamError()

    @Test fun everyServerCodeOfTheTeamHasItsOwnText() {
        val codes = listOf(
            "CANNOT_MODIFY_OWNER", "CANNOT_MODIFY_SELF", "USE_OWNER_TRANSFER", "INVALID_ROLE", "INVALID_PIN", "INVALID_STATUS", "MEMBER_NOT_FOUND",
            "NAME_TAKEN", "ACCESS_CODE_TAKEN", "INVALID_ACCESS_CODE", "DEVICE_NOT_FOUND", "INVALID_CASH_REGISTER", "FORBIDDEN",
        )
        val texts = codes.map { e(it).res }
        assertEquals("cada código, un texto distinto", codes.size, texts.toSet().size)
        assertNotEquals(R.string.error_generic, texts.first())
        texts.forEach { assertNotEquals(R.string.error_generic, it) }
    }

    @Test fun planLimitsKeepTheirNumberAndOfflineIsOffline() {
        assertEquals(ErrorMessage(R.string.error_PLAN_LIMIT_MEMBERS, 3), e("PLAN_LIMIT", "MEMBERS", 3))
        assertEquals(ErrorMessage(R.string.error_PLAN_LIMIT_DEVICES, 2), e("PLAN_LIMIT", "DEVICES", 2))
        assertEquals(ErrorMessage(R.string.error_offline), ApiFailure.Offline(java.io.IOException("x")).teamError())
    }

    @Test fun unknownCodeIsGeneric() {
        assertEquals(ErrorMessage(R.string.error_generic), e("ALGO_NUEVO"))
        assertEquals(ErrorMessage(R.string.error_generic), (null as Throwable?).teamError())
    }
}

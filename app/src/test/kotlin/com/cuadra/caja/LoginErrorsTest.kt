package com.cuadra.caja

import com.cuadra.caja.data.remote.ApiFailure
import com.cuadra.caja.data.remote.parseProblem
import com.cuadra.caja.ui.LoginFailure
import com.cuadra.caja.ui.ErrorMessage
import com.cuadra.caja.ui.loginError
import com.cuadra.caja.ui.loginFailure
import com.cuadra.caja.ui.message
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** Cada respuesta de `POST /api/auth/member-login` tiene su propio texto (ADR 0012). */
class LoginErrorsTest {
    private fun http(status: Int, code: String, feature: String? = null) = ApiFailure.Http(status, code, "x", feature)

    @Test fun serverCodesMapToTheirFailure() {
        assertEquals(LoginFailure.INVALID_CREDENTIALS, http(401, "INVALID_CREDENTIALS").loginFailure())
        assertEquals(LoginFailure.LOCKED, http(429, "LOCKED").loginFailure())
        assertEquals(LoginFailure.RATE_LIMITED, http(429, "RATE_LIMITED").loginFailure())
        assertEquals(LoginFailure.OWNER_USES_GOOGLE, http(403, "OWNER_USES_GOOGLE").loginFailure())
        assertEquals(LoginFailure.PLAN_LIMIT_DEVICES, http(403, "PLAN_LIMIT", "DEVICES").loginFailure())
    }

    @Test fun otherPlanLimitsAndUnknownCodesAreGeneric() {
        assertEquals(LoginFailure.OTHER, http(403, "PLAN_LIMIT", "MEMBERS").loginFailure())
        assertEquals(LoginFailure.OTHER, http(500, "ALGO_NUEVO").loginFailure())
        assertEquals(LoginFailure.OTHER, (null as Throwable?).loginFailure())
        assertEquals(LoginFailure.OTHER, IllegalStateException("x").loginFailure())
    }

    @Test fun anyOtherUnauthorizedIsWrongCredentials() {
        assertEquals(LoginFailure.INVALID_CREDENTIALS, http(401, "HTTP_401").loginFailure())
    }

    @Test fun offlineIsItsOwnMessage() {
        assertEquals(LoginFailure.OFFLINE, ApiFailure.Offline(java.io.IOException("x")).loginFailure())
        assertEquals(ErrorMessage(R.string.login_err_offline), ApiFailure.Offline(java.io.IOException("x")).loginError())
    }

    @Test fun everyFailureHasADifferentText() {
        val res = LoginFailure.entries.filter { it != LoginFailure.OTHER }.map { f -> f.message().res }
        assertEquals(res.size, res.toSet().size)
        res.forEach { assertNotEquals(R.string.error_generic, it) }
        assertEquals(R.string.login_err_LOCKED, http(429, "LOCKED").loginError().res)
        assertEquals(R.string.login_err_PLAN_LIMIT_DEVICES, http(403, "PLAN_LIMIT", "DEVICES").loginError().res)
    }

    @Test fun aRealServerBodyIsClassified() {
        val locked = parseProblem(429, """{"type":"about:blank","status":429,"code":"LOCKED","detail":"Too many failed attempts"}""", "x")
        assertEquals(LoginFailure.LOCKED, locked.loginFailure())
        val plan = parseProblem(403, """{"status":403,"code":"PLAN_LIMIT","feature":"DEVICES","limit":2}""", "x")
        assertEquals(LoginFailure.PLAN_LIMIT_DEVICES, plan.loginFailure())
        assertEquals(LoginFailure.RATE_LIMITED, parseProblem(429, """{"code":"RATE_LIMITED","retryAfterSeconds":30}""", "x").loginFailure())
    }
}

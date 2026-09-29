package com.cuadra.caja

import com.cuadra.caja.data.remote.ApiFailure
import com.cuadra.caja.data.remote.parseProblem
import com.cuadra.caja.ui.ErrorMessage
import com.cuadra.caja.ui.errorMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ErrorsTest {
    @Test fun parsesPlanLimitExtras() {
        val f = parseProblem(403, """{"code":"PLAN_LIMIT","detail":"x","status":403,"feature":"MEMBERS","limit":3,"otro":{"a":1}}""", "fallback")
        assertEquals("PLAN_LIMIT", f.code)
        assertEquals("MEMBERS", f.feature)
        assertEquals(3, f.limit)
    }

    @Test fun tolerantOfGarbageAndOddTypes() {
        assertEquals("HTTP_500", parseProblem(500, "<html>", "boom").code)
        assertEquals("HTTP_502", parseProblem(502, null, "bad").code)
        val f = parseProblem(403, """{"code":"PLAN_LIMIT","feature":7,"limit":"abc"}""", "x")
        assertNull(f.feature)
        assertNull(f.limit)
    }

    @Test fun planLimitPerFeatureWithNumber() {
        fun e(feature: String?, limit: Int?) = ApiFailure.Http(403, "PLAN_LIMIT", "x", feature, limit).errorMessage()
        assertEquals(ErrorMessage(R.string.error_PLAN_LIMIT_MEMBERS, 3), e("MEMBERS", 3))
        assertEquals(ErrorMessage(R.string.error_PLAN_LIMIT_DEVICES, 2), e("DEVICES", 2))
        assertEquals(ErrorMessage(R.string.error_PLAN_LIMIT_SCHEDULES, 1), e("SCHEDULES", 1))
        assertEquals(ErrorMessage(R.string.error_PLAN_LIMIT_BUSINESSES, 1), e("BUSINESSES", 1))
        assertEquals(ErrorMessage(R.string.error_PLAN_LIMIT_REPORT_HISTORY, 30), e("REPORT_HISTORY", 30))
        assertEquals(ErrorMessage(R.string.error_PLAN_LIMIT_EXPORT), e("EXPORT", null))
    }

    @Test fun planLimitFallsBackWithoutFeatureOrLimit() {
        assertEquals(ErrorMessage(R.string.error_PLAN_LIMIT_generic), ApiFailure.Http(403, "PLAN_LIMIT", "x").errorMessage())
        assertEquals(ErrorMessage(R.string.error_PLAN_LIMIT_generic), ApiFailure.Http(403, "PLAN_LIMIT", "x", "MEMBERS", null).errorMessage())
        assertEquals(ErrorMessage(R.string.error_PLAN_LIMIT_generic), ApiFailure.Http(403, "PLAN_LIMIT", "x", "NUEVA_FUNCION", 5).errorMessage())
    }

    @Test fun suspendedAndOthers() {
        assertEquals(ErrorMessage(R.string.error_BUSINESS_SUSPENDED), ApiFailure.Http(403, "BUSINESS_SUSPENDED", "x").errorMessage())
        assertEquals(ErrorMessage(R.string.error_FORBIDDEN), ApiFailure.Http(403, "FORBIDDEN", "x").errorMessage())
        assertEquals(ErrorMessage(R.string.error_generic), ApiFailure.Http(500, "VIEW_AS_READ_ONLY", "x").errorMessage())
        assertEquals(ErrorMessage(R.string.error_offline), ApiFailure.Offline(java.io.IOException("x")).errorMessage())
    }
}

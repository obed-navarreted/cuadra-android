package com.cuadra.caja.ui

import android.content.res.Resources
import androidx.annotation.StringRes
import com.cuadra.caja.R
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.cuadra.caja.data.remote.ApiFailure

/** Un texto de error de pantalla: el recurso y, si lo lleva, el número que se inserta (p. ej. el límite del plan). */
data class ErrorMessage(@StringRes val res: Int, val arg: Int? = null) {
    fun text(resources: Resources): String = if (arg != null) resources.getString(res, arg) else resources.getString(res)
}

/** Traduce un fallo de la API a un texto de pantalla. Solo depende del `code` estable, nunca del texto del servidor. */
fun Throwable?.errorMessage(): ErrorMessage = when (this) {
    is ApiFailure.Offline -> ErrorMessage(R.string.error_offline)
    is ApiFailure.Http -> when (code) {
        "INVALID_GOOGLE_TOKEN" -> ErrorMessage(R.string.error_INVALID_GOOGLE_TOKEN)
        "FORBIDDEN" -> ErrorMessage(R.string.error_FORBIDDEN)
        "PLAN_LIMIT" -> planLimit(feature, limit)
        "BUSINESS_SUSPENDED" -> ErrorMessage(R.string.error_BUSINESS_SUSPENDED)
        else -> ErrorMessage(R.string.error_generic)
    }
    else -> ErrorMessage(R.string.error_generic)
}

/** Límite del plan, por función. Sin `limit` (servidor más viejo/nuevo) se usa la versión sin número. */
internal fun planLimit(feature: String?, limit: Int?): ErrorMessage {
    val n = limit?.takeIf { it >= 0 }
    fun pick(withNumber: Int, plain: Int) = if (n != null) ErrorMessage(withNumber, n) else ErrorMessage(plain)
    return when (feature) {
        "MEMBERS" -> pick(R.string.error_PLAN_LIMIT_MEMBERS, R.string.error_PLAN_LIMIT_generic)
        "DEVICES" -> pick(R.string.error_PLAN_LIMIT_DEVICES, R.string.error_PLAN_LIMIT_generic)
        "SCHEDULES" -> pick(R.string.error_PLAN_LIMIT_SCHEDULES, R.string.error_PLAN_LIMIT_generic)
        "BUSINESSES" -> pick(R.string.error_PLAN_LIMIT_BUSINESSES, R.string.error_PLAN_LIMIT_generic)
        "REPORT_HISTORY" -> pick(R.string.error_PLAN_LIMIT_REPORT_HISTORY, R.string.error_PLAN_LIMIT_generic)
        "EXPORT" -> ErrorMessage(R.string.error_PLAN_LIMIT_EXPORT)
        else -> ErrorMessage(R.string.error_PLAN_LIMIT_generic)
    }
}

/** Versión sin número, para las pantallas que solo guardan un recurso. */
@StringRes
fun Throwable?.messageRes(): Int = errorMessage().res

@Composable
fun ErrorMessage.asString(): String = if (arg != null) stringResource(res, arg) else stringResource(res)

package com.cuadra.caja.ui

import com.cuadra.caja.R
import com.cuadra.caja.data.remote.ApiFailure

/** Por qué no se pudo entrar con código + usuario + PIN (`POST /api/auth/member-login`, ADR 0012). */
enum class LoginFailure { INVALID_CREDENTIALS, LOCKED, OWNER_USES_GOOGLE, PLAN_LIMIT_DEVICES, RATE_LIMITED, OFFLINE, OTHER }

/** Clasifica el fallo por el `code` estable del servidor (y el estado 401), nunca por su texto. */
fun Throwable?.loginFailure(): LoginFailure = when (this) {
    is ApiFailure.Offline -> LoginFailure.OFFLINE
    is ApiFailure.Http -> when {
        code == "LOCKED" -> LoginFailure.LOCKED
        code == "RATE_LIMITED" -> LoginFailure.RATE_LIMITED
        code == "OWNER_USES_GOOGLE" -> LoginFailure.OWNER_USES_GOOGLE
        code == "PLAN_LIMIT" && feature == "DEVICES" -> LoginFailure.PLAN_LIMIT_DEVICES
        // Cualquier código, usuario o PIN malo responde igual (el servidor no dice cuál falló).
        code == "INVALID_CREDENTIALS" || status == 401 -> LoginFailure.INVALID_CREDENTIALS
        else -> LoginFailure.OTHER
    }
    else -> LoginFailure.OTHER
}

fun LoginFailure.message(): ErrorMessage = ErrorMessage(
    when (this) {
        LoginFailure.INVALID_CREDENTIALS -> R.string.login_err_INVALID_CREDENTIALS
        LoginFailure.LOCKED -> R.string.login_err_LOCKED
        LoginFailure.OWNER_USES_GOOGLE -> R.string.login_err_OWNER_USES_GOOGLE
        LoginFailure.PLAN_LIMIT_DEVICES -> R.string.login_err_PLAN_LIMIT_DEVICES
        LoginFailure.RATE_LIMITED -> R.string.login_err_RATE_LIMITED
        LoginFailure.OFFLINE -> R.string.login_err_offline
        LoginFailure.OTHER -> R.string.error_generic
    },
)

fun Throwable?.loginError(): ErrorMessage =
    if (this is com.cuadra.caja.data.repo.BusinessSwitchBlocked) businessSwitchBlocked() else loginFailure().message()

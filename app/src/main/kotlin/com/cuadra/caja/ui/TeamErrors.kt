package com.cuadra.caja.ui

import com.cuadra.caja.R
import com.cuadra.caja.data.remote.ApiFailure

/**
 * Errores de la gestión del equipo, uno por código estable del servidor (`MemberService`, `BusinessService`, `DeviceService`). Los límites del
 * plan (PLAN_LIMIT con `feature` MEMBERS/DEVICES) y la falta de conexión salen de [errorMessage]. Un código desconocido cae en el mensaje genérico.
 */
fun Throwable?.teamError(): ErrorMessage = when (this) {
    is ApiFailure.Http -> when (code) {
        "CANNOT_MODIFY_OWNER" -> ErrorMessage(R.string.team_err_CANNOT_MODIFY_OWNER)
        "CANNOT_MODIFY_SELF" -> ErrorMessage(R.string.team_err_CANNOT_MODIFY_SELF)
        "USE_OWNER_TRANSFER" -> ErrorMessage(R.string.team_err_USE_OWNER_TRANSFER)
        "INVALID_ROLE" -> ErrorMessage(R.string.team_err_INVALID_ROLE)
        "INVALID_PIN" -> ErrorMessage(R.string.team_err_INVALID_PIN)
        "PIN_TAKEN" -> ErrorMessage(R.string.team_err_PIN_TAKEN)
        "INVALID_STATUS" -> ErrorMessage(R.string.team_err_INVALID_STATUS)
        "MEMBER_NOT_FOUND" -> ErrorMessage(R.string.team_err_MEMBER_NOT_FOUND)
        "NAME_TAKEN" -> ErrorMessage(R.string.team_err_NAME_TAKEN)
        "ACCESS_CODE_TAKEN" -> ErrorMessage(R.string.team_err_ACCESS_CODE_TAKEN)
        "INVALID_ACCESS_CODE" -> ErrorMessage(R.string.team_err_INVALID_ACCESS_CODE)
        "DEVICE_NOT_FOUND" -> ErrorMessage(R.string.team_err_DEVICE_NOT_FOUND)
        "INVALID_CASH_REGISTER" -> ErrorMessage(R.string.team_err_INVALID_CASH_REGISTER)
        "PIN_VERIFICATION_REQUIRED" -> ErrorMessage(R.string.error_PIN_VERIFICATION_REQUIRED)
        "FORBIDDEN" -> ErrorMessage(R.string.team_err_FORBIDDEN)
        else -> errorMessage()
    }
    else -> errorMessage()
}

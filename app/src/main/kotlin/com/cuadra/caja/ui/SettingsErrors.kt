package com.cuadra.caja.ui

import com.cuadra.caja.R
import com.cuadra.caja.data.remote.ApiFailure
import com.cuadra.caja.domain.SettingsError
import com.cuadra.caja.domain.NotifyError
import com.cuadra.caja.domain.TicketError

/**
 * Errores de las pantallas de gestión (ajustes del negocio, plantillas, ayuda): uno por código estable del servidor. Los límites del plan y la falta de
 * conexión salen de [errorMessage]; un código desconocido cae en el mensaje genérico.
 */
fun Throwable?.settingsError(): ErrorMessage = when (this) {
    is ApiFailure.Http -> when (code) {
        "INVALID_TIMEZONE" -> ErrorMessage(R.string.set_err_INVALID_TIMEZONE)
        "INVALID_DAY_CUTOFF", "INVALID_TIME" -> ErrorMessage(R.string.set_err_INVALID_TIME)
        "INVALID_MODULE" -> ErrorMessage(R.string.set_err_INVALID_MODULE)
        "INVALID_POS_VIEWS" -> ErrorMessage(R.string.set_err_INVALID_POS_VIEWS)
        "INVALID_HOURS" -> ErrorMessage(R.string.set_err_INVALID_HOURS)
        "INVALID_BODY" -> ErrorMessage(R.string.tpl_err_INVALID_BODY)
        "TOO_MANY_TICKETS" -> ErrorMessage(R.string.help_err_too_many)
        "BUSINESS_NOT_FOUND" -> ErrorMessage(R.string.set_err_NOT_FOUND)
        "FORBIDDEN" -> ErrorMessage(R.string.set_err_FORBIDDEN)
        "CURRENCY_LOCKED" -> ErrorMessage(R.string.set_err_CURRENCY_LOCKED)
        else -> errorMessage()
    }
    else -> errorMessage()
}

/** Al eliminar el negocio: 403 = quien lo intenta no es el dueño. */
fun Throwable?.deleteError(): ErrorMessage = when {
    this is ApiFailure.Http && (status == 403 || code == "FORBIDDEN") -> ErrorMessage(R.string.set_delete_err_forbidden)
    else -> settingsError()
}

fun SettingsError.message(): ErrorMessage = ErrorMessage(
    when (this) {
        SettingsError.NAME -> R.string.set_val_name
        SettingsError.TIMEZONE -> R.string.set_val_timezone
        SettingsError.CUTOFF -> R.string.set_val_cutoff
        SettingsError.POS_VIEWS -> R.string.set_val_pos_views
        SettingsError.DUE_DAYS -> R.string.set_val_due_days
        SettingsError.OVERDUE_DAYS -> R.string.set_val_overdue_days
    },
)

fun NotifyError.message(): ErrorMessage = ErrorMessage(
    when (this) {
        NotifyError.QUIET_START, NotifyError.QUIET_END, NotifyError.SUMMARY_TIME -> R.string.set_val_time
        NotifyError.STALE_HOURS -> R.string.set_val_stale
    },
)

fun TicketError.message(): ErrorMessage = ErrorMessage(
    when (this) {
        TicketError.MESSAGE_SHORT -> R.string.help_val_short
        TicketError.MESSAGE_LONG -> R.string.help_val_long
        TicketError.EMAIL -> R.string.help_val_email
        TicketError.PHONE -> R.string.help_val_phone
    },
)

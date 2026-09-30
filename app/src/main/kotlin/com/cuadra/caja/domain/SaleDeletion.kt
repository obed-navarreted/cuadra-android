package com.cuadra.caja.domain

/** Resultado de revisar el motivo escrito para eliminar una venta. */
enum class ReasonCheck { OK, TOO_SHORT }

/**
 * Eliminar una venta cobrada es trazable: exige un MOTIVO de al menos 5 letras (el servidor impone lo mismo: `REASON_REQUIRED`). Aquí se valida antes
 * de encolar la operación para que no se pierda una eliminación por un motivo vacío.
 */
object SaleDeletion {
    const val MIN_REASON = 5
    const val MAX_REASON = 200

    /** Cuenta letras y dígitos (no espacios ni signos): «  ab  » o «.....» no son un motivo. */
    fun check(reason: String): ReasonCheck = if (reason.trim().count { it.isLetterOrDigit() } >= MIN_REASON) ReasonCheck.OK else ReasonCheck.TOO_SHORT

    /** El texto que se envía (sin espacios sobrantes) o null si no es válido. */
    fun clean(reason: String): String? = reason.trim().takeIf { check(it) == ReasonCheck.OK }

    /** Solo dueño y admins eliminan, y solo una venta cobrada (una ya anulada no se vuelve a eliminar). */
    fun canDelete(role: String?, status: String): Boolean = (role == "OWNER" || role == "ADMIN") && status == "COMPLETED"
}

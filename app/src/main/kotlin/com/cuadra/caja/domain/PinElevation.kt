package com.cuadra.caja.domain

/**
 * Elevación por PIN verificado (ADR 0012, actualización 2026-10-01). Cualquier persona del negocio entra en cualquier teléfono del negocio con su PIN (se
 * comprueba en el teléfono, sin conexión). El teléfono tiene un ROL BASE (el de quien lo vinculó): quien tenga más rol que eso actúa con su rol completo
 * solo cuando el SERVIDOR confirmó su PIN en este teléfono (un permiso que dura unas horas y se renueva al usarse). Mientras no, actúa con el rol base:
 * vende y cobra, y las pantallas de administración piden «Confirmar PIN».
 *
 * Aquí solo están las decisiones (sin red ni Android): qué hacer tras el PIN local y qué hacer con la respuesta del servidor.
 */
object PinElevation {
    fun rank(role: String?): Int = when (role) { "OWNER" -> 3; "ADMIN" -> 2; "CASHIER" -> 1; else -> 0 }

    /** El rol con que actúa mientras no confirme el PIN: el menor entre el suyo y el del teléfono (sin saber el del teléfono, el más bajo). */
    fun cappedRole(role: String, baseRole: String?): String = if (rank(role) <= rank(baseRole ?: "CASHIER")) role else baseRole ?: "CASHIER"

    /** ¿Necesita confirmar su PIN con el servidor para usar su rol en este teléfono? */
    fun needsVerification(role: String, baseRole: String?, grantExpiresAt: Long?, now: Long): Boolean =
        rank(role) > rank(baseRole ?: "CASHIER") && (grantExpiresAt == null || grantExpiresAt <= now)

    /** Tras acertar el PIN en el teléfono. */
    sealed interface AfterLocal {
        /** Entra con su rol (no supera al teléfono o ya tiene permiso vigente). */
        data class Ready(val role: String) : AfterLocal
        /** Hay que preguntarle al servidor (con el mismo PIN que acaba de escribir). */
        data object Verify : AfterLocal
    }

    fun afterLocalUnlock(role: String, baseRole: String?, grantExpiresAt: Long?, now: Long): AfterLocal =
        if (needsVerification(role, baseRole, grantExpiresAt, now)) AfterLocal.Verify else AfterLocal.Ready(role)

    /** Lo que respondió el servidor a `verify-pin`. */
    sealed interface ServerAnswer {
        data class Verified(val expiresAt: Long, val baseRole: String?) : ServerAnswer
        data object Offline : ServerAnswer
        /** El PIN no coincide en el servidor (el teléfono tenía un PIN viejo). */
        data object WrongPin : ServerAnswer
        data class Locked(val waitMillis: Long) : ServerAnswer
        /** La persona ya no está activa. */
        data object NotActive : ServerAnswer
        /** Cualquier otro fallo del servidor (5xx, negocio suspendido…). */
        data object Failed : ServerAnswer
    }

    /** Qué hacer con la sesión. */
    sealed interface Outcome {
        /** Entra con su rol completo; guarda el permiso hasta `expiresAt`. */
        data class Elevated(val role: String, val expiresAt: Long, val baseRole: String?) : Outcome
        /**
         * Entra igual, pero con el rol base (`actingRole`) hasta que confirme el PIN (`realRole`): sin conexión o si el servidor falló. `offline` decide el
         * mensaje («Conéctate para confirmar tu PIN la primera vez en este teléfono»).
         */
        data class Capped(val actingRole: String, val realRole: String, val offline: Boolean) : Outcome
        /** No entra: el servidor dice que el PIN no es (se refresca el directorio). */
        data object Wrong : Outcome
        data class Locked(val waitMillis: Long) : Outcome
        data object NotActive : Outcome
    }

    fun afterServer(role: String, baseRole: String?, answer: ServerAnswer): Outcome = when (answer) {
        is ServerAnswer.Verified -> Outcome.Elevated(role, answer.expiresAt, answer.baseRole ?: baseRole)
        ServerAnswer.Offline -> Outcome.Capped(cappedRole(role, baseRole), role, offline = true)
        ServerAnswer.Failed -> Outcome.Capped(cappedRole(role, baseRole), role, offline = false)
        ServerAnswer.WrongPin -> Outcome.Wrong
        is ServerAnswer.Locked -> Outcome.Locked(answer.waitMillis)
        ServerAnswer.NotActive -> Outcome.NotActive
    }

    /**
     * Tras saber de nuevo lo que el servidor dice de este teléfono (rol base y permisos): el rol con el que debe actuar la persona activa y, si no es su rol
     * real, cuál es el real (pendiente de confirmar). Sirve para bajar a alguien cuyo permiso se revocó y para subir a quien ya lo tiene.
     */
    data class Acting(val role: String, val realRole: String?)

    fun reconcile(realRole: String, baseRole: String?, grantExpiresAt: Long?, now: Long): Acting =
        if (needsVerification(realRole, baseRole, grantExpiresAt, now)) Acting(cappedRole(realRole, baseRole), realRole) else Acting(realRole, null)
}

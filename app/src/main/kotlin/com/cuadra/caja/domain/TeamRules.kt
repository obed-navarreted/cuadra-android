package com.cuadra.caja.domain

/** Qué puede hacer quien mira sobre una persona del equipo. Es un espejo de `canManage` del panel web (`equipo/lib.ts`). */
data class MemberPermissions(val edit: Boolean, val disable: Boolean, val resetPin: Boolean, val changeRole: Boolean) {
    val any: Boolean get() = edit || disable || resetPin || changeRole

    companion object {
        val NONE = MemberPermissions(edit = false, disable = false, resetPin = false, changeRole = false)
    }
}

/**
 * Reglas de gestión del equipo. El dueño gestiona a todos; el administrador a todos menos al dueño (cajeros y otros administradores); el cajero
 * solo a sí mismo. Nadie se da de baja ni se cambia el rol a sí mismo (sí su nombre, color y PIN). El servidor lo exige igual (`MemberService`):
 * esto solo evita mostrar botones que darían error.
 */
object TeamRules {
    const val OWNER = "OWNER"
    const val ADMIN = "ADMIN"
    const val CASHIER = "CASHIER"

    fun canManage(viewerRole: String?, viewerIsSelf: Boolean, targetRole: String?): MemberPermissions {
        if (viewerRole != OWNER && viewerRole != ADMIN && viewerRole != CASHIER) return MemberPermissions.NONE
        if (viewerIsSelf) return MemberPermissions(edit = true, disable = false, resetPin = true, changeRole = false)
        if (viewerRole == CASHIER || targetRole == OWNER) return MemberPermissions.NONE
        return MemberPermissions(edit = true, disable = true, resetPin = true, changeRole = true)
    }

    /** Roles que puede asignar quien mira al crear una persona: el dueño y el administrador ofrecen cajero y administrador; nunca dueño. */
    fun assignableRoles(viewerRole: String?): List<String> = if (viewerRole == OWNER || viewerRole == ADMIN) listOf(CASHIER, ADMIN) else emptyList()

    /** ¿Ve la lista de personas y teléfonos? Solo dueño y administrador. */
    fun canSeeTeam(role: String?): Boolean = role == OWNER || role == ADMIN

    /** Colores de avatar que ofrece el panel web. */
    val COLORS = listOf("#1f7a55", "#c9793a", "#2f6db5", "#8a4fb0", "#b5443a", "#3c8f9a", "#8c7a2b", "#5e594f")

    /** El nombre de la persona: 1 a 80 caracteres (el servidor rechaza más de 80). */
    const val NAME_MAX = 80
    fun cleanName(raw: String): String = raw.trim().take(NAME_MAX)
}

/** Por qué un PIN nuevo no sirve todavía. */
enum class PinProblem { TOO_SHORT, MISMATCH }

/** El PIN es de EXACTAMENTE 5 números escrito dos veces (mismo criterio que el servidor: `MemberService.validatePin`, ADR 0012). */
object PinRules {
    const val LENGTH = 5

    fun isValid(pin: String): Boolean = pin.length == LENGTH && pin.all { it in '0'..'9' }

    /** Solo dígitos y hasta 5: lo que se deja escribir en el campo. */
    fun sanitize(raw: String): String = raw.filter { it in '0'..'9' }.take(LENGTH)

    /** `null` si el PIN y su repetición son válidos e iguales. La repetición vacía no se reclama hasta que haya algo que comparar. */
    fun check(pin: String, confirm: String): PinProblem? = when {
        !isValid(pin) -> PinProblem.TOO_SHORT
        pin != confirm -> PinProblem.MISMATCH
        else -> null
    }

    /** Se puede guardar (botón habilitado). */
    fun canSave(pin: String, confirm: String): Boolean = check(pin, confirm) == null
}

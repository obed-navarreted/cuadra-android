package com.cuadra.caja.domain

/**
 * «Mi cuenta»: una sola pantalla con un solo botón «Guardar» que aplica lo que la persona cambió: su nombre y/o su PIN. Con PIN puesto se pide el ACTUAL
 * (se comprueba en el teléfono antes de llamar al servidor); sin PIN (el dueño que entra con Google) solo se pide el nuevo.
 */
data class AccountDraft(
    val originalName: String,
    val name: String = originalName,
    val hasPin: Boolean = true,
    val current: String = "",
    val pin: String = "",
    val confirm: String = "",
) {
    val cleanName: String get() = TeamRules.cleanName(name)
    val nameChanged: Boolean get() = cleanName.isNotEmpty() && cleanName != originalName.trim()

    /** Escribió algo en cualquiera de los campos del PIN: quiere cambiarlo. */
    val wantsPin: Boolean get() = current.isNotEmpty() || pin.isNotEmpty() || confirm.isNotEmpty()

    /** Un problema del PIN nuevo (corto o distinto), solo si ya escribió algo. */
    val pinProblem: PinProblem? get() = if (wantsPin) PinRules.check(pin, confirm) else null

    /** La repetición ya tiene tantos dígitos como el PIN y no coinciden: se avisa al escribir, no antes. */
    val showMismatch: Boolean get() = confirm.length >= pin.length && confirm.isNotEmpty() && pin != confirm

    private val currentOk: Boolean get() = !hasPin || PinRules.isValid(current)

    /** Se puede guardar: hay algo que guardar y lo que se escribió está completo. */
    val ready: Boolean
        get() = when {
            !wantsPin -> nameChanged
            else -> pinProblem == null && currentOk && !nameBlank
        }

    /** Nombre vacío: no se guarda (el servidor lo rechazaría) aunque se cambie el PIN. */
    val nameBlank: Boolean get() = name.isBlank()
}

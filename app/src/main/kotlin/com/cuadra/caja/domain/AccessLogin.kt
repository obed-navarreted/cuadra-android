package com.cuadra.caja.domain

/**
 * Entrada del equipo con CÓDIGO DEL NEGOCIO + PIN (ADR 0012; el PIN identifica a la persona, no se repite dentro del negocio). Todo lo que se decide sin pantalla vive aquí, con pruebas:
 * el código, el formulario de entrada, la elección del PIN nuevo (dos veces) y los textos para compartir.
 */
object AccessCode {
    const val LENGTH = 5

    /** Solo dígitos y hasta 5: lo que se deja escribir (también con espacios o guiones pegados). */
    fun sanitize(raw: String): String = raw.filter { it in '0'..'9' }.take(LENGTH)

    fun isComplete(code: String): Boolean = code.length == LENGTH && code.all { it in '0'..'9' }

    /** Un código que el dueño elige: 5 dígitos y sin cero al principio (mismo criterio que el servidor: `[1-9]\d{4}`). */
    fun isChosenValid(code: String): Boolean = code.length == LENGTH && code[0] in '1'..'9' && code.all { it in '0'..'9' }

    /** El código grande y separado para leerlo de lejos: «13085» → «1 3 0 8 5». */
    fun spaced(code: String): String = code.toList().joinToString(" ")

    /** Mensaje para que el dueño lo mande: `template` lleva `%1$s` (negocio) y `%2$s` (código). */
    fun shareMessage(template: String, business: String, code: String): String = template.format(business.trim(), code)

    /** Lo que se le da a una persona nueva: negocio, código y PIN (su nombre, solo de referencia). `template`: `%1$s` negocio, `%2$s` código, `%3$s` nombre, `%4$s` PIN. */
    fun credentialsMessage(template: String, business: String, code: String, name: String, pin: String): String =
        template.format(business.trim(), code, name.trim(), pin)
}

/** El formulario de entrada del equipo: código del negocio y PIN de 5 números con el teclado de la app (sin usuario: el PIN dice quién es). */
data class MemberLoginForm(val code: String = "", val pin: String = "") {
    fun withCode(raw: String) = copy(code = AccessCode.sanitize(raw))
    fun digit(d: Char) = if (d in '0'..'9' && pin.length < PinRules.LENGTH) copy(pin = pin + d) else this
    fun backspace() = copy(pin = pin.dropLast(1))

    /** Están los dos datos completos. */
    val ready: Boolean get() = AccessCode.isComplete(code) && PinRules.isValid(pin)

    /** Al escribir el 5.º número se entra solo, pero solo si el código ya estaba. */
    fun autoSubmits(afterDigit: MemberLoginForm): Boolean = afterDigit.ready && !ready
}

/** El PIN nuevo elegido con el teclado de la app, dos veces: primero el PIN y luego su repetición. */
data class NewPinEntry(val first: String = "", val second: String = "", val mismatch: Boolean = false) {
    enum class Stage { FIRST, REPEAT }

    val stage: Stage get() = if (first.length < PinRules.LENGTH) Stage.FIRST else Stage.REPEAT

    /** Lo que se ve en los puntos ahora. */
    val shown: String get() = if (stage == Stage.FIRST) first else second

    /** Los dos PIN están completos e iguales. */
    val done: Boolean get() = PinRules.canSave(first, second)

    fun digit(d: Char): NewPinEntry {
        if (d !in '0'..'9' || done) return this
        return when (stage) {
            Stage.FIRST -> copy(first = first + d, mismatch = false)
            Stage.REPEAT -> {
                val s = second + d
                // Si la repetición no coincide se vuelve a empezar, con aviso.
                if (s.length == PinRules.LENGTH && s != first) NewPinEntry(mismatch = true) else copy(second = s)
            }
        }
    }

    fun backspace(): NewPinEntry = when {
        done -> this
        stage == Stage.REPEAT && second.isEmpty() -> copy(first = first.dropLast(1))
        stage == Stage.REPEAT -> copy(second = second.dropLast(1))
        else -> copy(first = first.dropLast(1), mismatch = false)
    }
}

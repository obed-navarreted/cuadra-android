package com.cuadra.caja.domain

/**
 * El botón del medio de la barra de la caja (`Recibo · [medio] · Cobrar`): sin «Cobro en caja» es «Apartar»; con el ajuste, «Enviar a caja» (ADR 0015) en
 * el mismo lugar y con las mismas reglas de ancho. Lo ya apartado sigue en la lista en los dos casos.
 */
object ParkRules {
    enum class Middle { PARK, SEND }

    fun middle(registerCheckout: Boolean): Middle = if (registerCheckout) Middle.SEND else Middle.PARK

    fun canPark(registerCheckout: Boolean, cartEmpty: Boolean): Boolean = !registerCheckout && !cartEmpty

    /** «Enviar a caja» solo con el ajuste encendido y algo en el recibo (un recibo vacío no se envía: el botón queda apagado). */
    fun canSend(registerCheckout: Boolean, cartEmpty: Boolean): Boolean = registerCheckout && !cartEmpty
}

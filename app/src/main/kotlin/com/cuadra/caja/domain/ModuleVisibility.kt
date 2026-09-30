package com.cuadra.caja.domain

/**
 * Qué se ve u ofrece según los módulos que el negocio dejó encendidos (Ajustes → Módulos). Solo esconde cosas en ESTE teléfono: el servidor no lo exige,
 * para que las ventas viejas que esperan sincronizar (con fiado o gastos) se acepten igual.
 */
object ModuleVisibility {
    fun credit(modules: Map<String, Boolean>) = SettingsModules.isOn(modules, "credit")
    fun expenses(modules: Map<String, Boolean>) = SettingsModules.isOn(modules, "expenses")

    /** Plantillas de WhatsApp que se editan: sin Fiado solo queda el comprobante de venta. */
    fun messageKinds(modules: Map<String, Boolean>): List<MessageKind> = MessageKind.entries.filter { it == MessageKind.TICKET || credit(modules) }

    /** Filtro de métodos en Ventas: sin Fiado no se ofrece filtrar por él (las ventas viejas fiadas se siguen viendo en su detalle). */
    fun historyMethods(modules: Map<String, Boolean>, all: List<String>): List<String> = all.filter { it != "CREDIT" || credit(modules) }

    /** Destinos de un aviso programado: sin Fiado no se ofrece «Fiados»; sin Gastos, ni «Gastos» ni el cierre de caja de turno. */
    fun scheduleLinks(modules: Map<String, Boolean>, links: List<String?>): List<String?> = links.filter {
        when (it) {
            "cuadra://fiados" -> credit(modules)
            "cuadra://gastos" -> expenses(modules)
            "cuadra://inventario" -> SettingsModules.isOn(modules, "inventory")
            else -> true
        }
    }
}

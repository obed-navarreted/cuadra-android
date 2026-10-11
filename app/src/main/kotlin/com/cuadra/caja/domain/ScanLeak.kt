package com.cuadra.caja.domain

/**
 * Un lector tipo teclado escribe con el foco en un buscador: su PRIMERA tecla pasa al campo (podría ser una persona tecleando; desde la segunda, tan rápida, ya no)
 * y el resto de la ráfaga se consume. Al confirmar la lectura hay que quitar ese primer carácter para no dejar basura en el buscador.
 */
object ScanLeak {
    /** `typed` es el texto del campo cuando llega la lectura `code`: sin el primer carácter del código si quedó al final. */
    fun strip(typed: String, code: String): String {
        val first = code.firstOrNull() ?: return typed
        return if (typed.endsWith(first)) typed.dropLast(1) else typed
    }
}

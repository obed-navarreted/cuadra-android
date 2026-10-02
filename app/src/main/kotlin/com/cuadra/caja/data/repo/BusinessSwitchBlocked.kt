package com.cuadra.caja.data.repo

/**
 * Ya no se lanza: cada negocio tiene su propia base y su propia cola en el teléfono (ADR 0014), así que cambiar de cuenta o de negocio nunca queda
 * bloqueado por operaciones sin enviar. Se conserva el tipo (y su mensaje) por si algún día vuelve a hacer falta.
 */
class BusinessSwitchBlocked(val unsent: Int, val businessName: String?) : IllegalStateException("unsent operations of another business")

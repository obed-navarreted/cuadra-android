package com.cuadra.caja.data.repo

import com.cuadra.caja.data.local.CuadraDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * El teléfono cambia de negocio (entrar con el código de otro, o el dueño elige otro negocio): no se puede cambiar dejando algo sin enviar del anterior
 * (su cola se perdería o, peor, se mandaría al nuevo). Tiene `unsent` operaciones de `businessName` (pendientes o rechazadas sin resolver).
 */
class BusinessSwitchBlocked(val unsent: Int, val businessName: String?) : IllegalStateException("unsent operations of another business")

/**
 * Los datos del teléfono son de UN negocio (productos, ventas, fiados, cursor de sincronización). Antes de vincular el teléfono a otro negocio se
 * comprueba que el anterior no tenga nada sin enviar y, si no lo tiene, se borran sus datos: el nuevo empieza limpio y baja todo desde cero.
 */
class LocalBusinessData(private val db: CuadraDatabase) {
    /** De qué negocio son los datos del teléfono (nulo = no hay datos de ninguno). */
    suspend fun currentBusinessId(): String? = db.directory().syncState()?.businessId ?: db.directory().businessNow()?.id

    /** Lo que impediría cambiar a OTRO negocio cualquiera (antes de crear uno nuevo, cuando aún no se sabe su id). */
    suspend fun blockerForAnyOther(): BusinessSwitchBlocked? {
        if (currentBusinessId() == null) return null
        val unsent = db.outbox().unsentCount()
        return if (unsent > 0) BusinessSwitchBlocked(unsent, db.directory().businessNow()?.name) else null
    }

    /**
     * Deja el teléfono listo para `target`: si los datos ya son de ese negocio (o no hay datos) no hace nada; si son de otro y no queda nada sin enviar,
     * los borra; si queda algo, devuelve el bloqueo SIN borrar nada.
     */
    suspend fun prepareFor(target: String): BusinessSwitchBlocked? {
        val current = currentBusinessId()
        if (current == null || current == target) return null
        val unsent = db.outbox().unsentOutside(target)
        if (unsent > 0) return BusinessSwitchBlocked(unsent, db.directory().businessNow()?.name)
        withContext(Dispatchers.IO) { db.clearAllTables() }
        return null
    }
}

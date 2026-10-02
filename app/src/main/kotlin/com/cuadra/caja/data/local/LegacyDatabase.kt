package com.cuadra.caja.data.local

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Migración, UNA sola vez, de la base de la versión anterior (`cuadra.db`: un solo archivo para todos los negocios, que pudo quedar con datos MEZCLADOS
 * de varios negocios; ver ADR 0014). Se trata como NO confiable:
 *  · de ella solo se conserva la cola de salida (lo que el teléfono hizo y aún no se envió): cada operación se mueve a la base de SU negocio (la fila ya
 *    dice de cuál es); las que no lo dicen son del negocio al que estaba vinculado el teléfono, y si no había ninguno no se pueden asignar;
 *  · todo lo demás (ventas, miembros, productos…) se descarta: se vuelve a bajar del servidor desde cero, ya en la base de cada negocio.
 * El archivo viejo se borra solo cuando todo lo conservado quedó guardado en su nueva base. Nadie tiene que hacer nada.
 */
object LegacyDatabase {
    data class Result(val found: Boolean, val moved: Int, val dropped: Int, val deleted: Boolean)

    /** `sessionBusinessId`: el negocio al que está vinculado el teléfono ahora (para las filas viejas sin negocio). */
    suspend fun migrate(context: Context, targets: BusinessDatabases, sessionBusinessId: String?): Result = withContext(Dispatchers.IO) {
        val file = context.getDatabasePath(CuadraDatabase.LEGACY_FILE)
        if (!file.exists()) return@withContext Result(found = false, moved = 0, dropped = 0, deleted = false)
        val legacy = CuadraDatabase.create(context, CuadraDatabase.LEGACY_FILE)
        try {
            val ops = legacy.outbox().allOps()
            val owned = ops.map { if (it.businessId == null && sessionBusinessId != null) it.copy(businessId = sessionBusinessId) else it }
            val movable = owned.filter { it.businessId != null }
            targets.adoptOps(movable)
            // Comprobación antes de borrar: cada fila rescatada debe estar ya en la base de su negocio.
            val safe = movable.groupBy { it.businessId!! }.all { (b, list) ->
                val have = targets.forBusiness(b).outbox().allOps().map { it.opId }.toSet()
                list.all { it.opId in have }
            }
            legacy.close()
            val deleted = if (safe) context.deleteDatabase(CuadraDatabase.LEGACY_FILE) else false
            Result(found = true, moved = movable.size, dropped = owned.size - movable.size, deleted = deleted)
        } catch (e: Exception) {
            runCatching { legacy.close() }
            // Si algo falla se deja el archivo viejo donde está (no se usa para nada) y se reintenta en el próximo arranque: nunca se pierde la cola.
            Result(found = true, moved = 0, dropped = 0, deleted = false)
        }
    }
}

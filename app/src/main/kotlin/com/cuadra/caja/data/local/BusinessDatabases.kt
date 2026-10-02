package com.cuadra.caja.data.local

import android.content.Context
import androidx.room.Room
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File

/**
 * UNA base de datos (archivo) por negocio (ADR 0014). Lo que ve la pantalla sale SIEMPRE de la base del negocio de la sesión; los datos de otro
 * negocio viven en otro archivo, así que ninguna consulta (ventas, miembros, PIN, fiados…) puede mezclarlos.
 *  · Sin negocio elegido (entrada, onboarding) la base activa es una vacía en memoria: no hay datos de ningún negocio.
 *  · Cada negocio conserva su propia cola de salida: cambiar de cuenta o de negocio nunca pierde ni manda lo pendiente al negocio equivocado.
 *  · Al salir (o cambiar) la base del negocio anterior se BORRA si no tiene nada sin enviar; si lo tiene, se conserva solo para enviarlo.
 *  · Al abrir una base se comprueba que de verdad sea de ese negocio (`verify`); si no lo es, se rescata su cola y se descarta.
 * Es un `Db`: los repositorios la usan sin saber cuál es la actual.
 */
class BusinessDatabases(
    private val context: Context,
    private val scope: CoroutineScope,
    /** Dónde corren la verificación y la limpieza (las pruebas usan uno inmediato para que sean deterministas). */
    private val io: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.IO,
    private val opener: (String) -> CuadraDatabase = { file -> CuadraDatabase.create(context, file) },
    private val inMemory: () -> CuadraDatabase = { Room.inMemoryDatabaseBuilder(context.applicationContext, CuadraDatabase::class.java).build() },
) : Db {
    private val lock = Any()
    private val open = HashMap<String, CuadraDatabase>()
    private val none: CuadraDatabase = inMemory()
    private val _active = MutableStateFlow(none)

    /** La base del negocio actual (o la vacía). Para quien debe re-suscribirse cuando cambia: `flatMapLatest`. */
    val active: StateFlow<CuadraDatabase> = _active.asStateFlow()

    @Volatile var activeBusinessId: String? = null
        private set

    private fun current(): CuadraDatabase = _active.value

    override fun products() = current().products()
    override fun sales() = current().sales()
    override fun outbox() = current().outbox()
    override fun directory() = current().directory()
    override fun customers() = current().customers()
    override fun credits() = current().credits()
    override fun templates() = current().templates()
    override fun cash() = current().cash()
    override fun inventory() = current().inventory()
    override fun notifications() = current().notifications()
    override fun reports() = current().reports()
    override suspend fun <R> inTransaction(block: suspend () -> R): R = current().inTransaction(block)

    /**
     * Cambia la base activa al negocio de la sesión (nulo = ninguno). Se llama de forma SINCRÓNICA en cada cambio de sesión, antes de que la pantalla
     * lo vea. La base del negocio anterior se libera (se borra si no tiene nada sin enviar).
     */
    fun select(businessId: String?) {
        if (businessId == activeBusinessId) return
        val db = businessId?.let { forBusiness(it) } ?: none
        val previous: String?
        synchronized(lock) {
            if (businessId == activeBusinessId) return
            previous = activeBusinessId
            _active.value = db
            activeBusinessId = businessId
        }
        if (businessId == null) scope.launch(io) { runCatching { none.clearAllTables() } }
        if (previous != null && previous != businessId) scope.launch(io) { runCatching { releaseIfClean(previous) } }
    }

    /** La base de ESE negocio (se abre y se verifica si hace falta), sin cambiar la activa: para enviar la cola o bajar datos de un negocio concreto. */
    fun forBusiness(businessId: String): CuadraDatabase {
        require(isSafeId(businessId)) { "bad business id" }
        synchronized(lock) { open[businessId] }?.let { return it }
        val db = opener(fileName(businessId))
        runBlocking(io) { verify(db, businessId) }
        return synchronized(lock) { open.getOrPut(businessId) { db } }
    }

    /**
     * Garantía de que esta base es de `businessId`: su cursor y su negocio dicen lo mismo (o aún nada) y su cola no trae filas de otro negocio. Si algo no
     * cuadra se rescatan las filas ajenas de la cola (cada una va a la base de su negocio) y se borra todo lo demás: se vuelve a bajar del servidor.
     */
    internal suspend fun verify(db: CuadraDatabase, businessId: String) {
        val state = db.directory().syncState()
        val row = db.directory().businessNow()
        val foreign = db.outbox().foreignOps(businessId)
        val mismatch = (state?.businessId != null && state.businessId != businessId) || (row != null && row.id != businessId)
        if (mismatch || foreign.isNotEmpty()) {
            if (foreign.isNotEmpty()) adoptOps(foreign)
            if (mismatch) {
                val mine = db.outbox().allOps().filter { it.businessId == null || it.businessId == businessId }
                db.clearAllTables()
                mine.forEach { db.outbox().insertAdopted(it) }
            } else {
                db.outbox().deleteForeign(businessId)
            }
        }
        if (db.directory().syncState() == null) db.directory().setCursor(SyncStateEntity(cursor = 0, businessId = businessId))
    }

    /** Guarda en la base de su propio negocio las filas de cola rescatadas (las que no dicen de qué negocio son no se pueden asignar y se descartan). */
    suspend fun adoptOps(ops: List<OutboxEntity>) {
        ops.filter { it.businessId != null && isSafeId(it.businessId) }.groupBy { it.businessId!! }.forEach { (b, list) ->
            val target = forBusiness(b)
            list.forEach { target.outbox().insertAdopted(it) }
        }
    }

    /** Cierra y borra la base de un negocio SOLO si no tiene nada en la cola (todo se vuelve a bajar del servidor al volver a entrar). */
    suspend fun releaseIfClean(businessId: String): Boolean {
        if (businessId == activeBusinessId) return false
        val db = synchronized(lock) { open[businessId] } ?: runCatching { forBusiness(businessId) }.getOrNull() ?: return false
        if (db.outbox().totalCount() > 0) return false
        // Se vuelve a comprobar BAJO el cerrojo: si mientras tanto se volvió a entrar a ese negocio, no se toca.
        return deleteFiles(businessId, onlyIfInactive = true)
    }

    /** Borra la base de un negocio sin condiciones (negocio eliminado, o acceso desactivado: lo pendiente ya no tiene a dónde enviarse). */
    suspend fun delete(businessId: String) {
        if (businessId == activeBusinessId) { synchronized(lock) { _active.value = none; activeBusinessId = null } }
        deleteFiles(businessId)
    }

    private fun deleteFiles(businessId: String, onlyIfInactive: Boolean = false): Boolean = synchronized(lock) {
        if (onlyIfInactive && businessId == activeBusinessId) return@synchronized false
        open.remove(businessId)?.let { runCatching { it.close() } }
        context.deleteDatabase(fileName(businessId))
        true
    }

    /** ¿Existe en este teléfono la base de ese negocio? (para pruebas y diagnóstico) */
    fun exists(businessId: String): Boolean = context.getDatabasePath(fileName(businessId)).exists()

    /** Cierra todo (pruebas). */
    fun closeAll() = synchronized(lock) { open.values.forEach { runCatching { it.close() } }; open.clear(); _active.value = none; activeBusinessId = null }

    companion object {
        const val PREFIX = "cuentiva-"
        fun fileName(businessId: String) = "$PREFIX$businessId.db"
        private fun isSafeId(id: String?) = id != null && id.isNotEmpty() && id.length <= 64 && id.all { it.isLetterOrDigit() || it == '-' || it == '_' }
        fun dir(context: Context): File = context.getDatabasePath("x").parentFile ?: File(".")
    }
}

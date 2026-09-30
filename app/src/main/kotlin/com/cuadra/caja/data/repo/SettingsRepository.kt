package com.cuadra.caja.data.repo

import com.cuadra.caja.data.local.BusinessEntity
import com.cuadra.caja.data.local.CuadraDatabase
import com.cuadra.caja.data.remote.CuadraApi
import com.cuadra.caja.data.remote.NotificationSettingsDto
import com.cuadra.caja.data.remote.UpdateBusinessBody
import com.cuadra.caja.data.remote.apiCall
import com.cuadra.caja.data.session.SessionStore
import com.cuadra.caja.data.sync.toEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * «Ajustes del negocio»: leer y cambiar los datos del negocio, las reglas de avisos y (solo el dueño) eliminarlo. Todo necesita conexión y lo decide el servidor;
 * lo que cambia se guarda también en Room, así el resto de la app (Caja, Fiados, Cierre del día) reacciona de inmediato sin esperar a la sincronización.
 */
class SettingsRepository(
    private val db: CuadraDatabase,
    private val api: CuadraApi,
    private val session: SessionStore,
    private val schedules: ScheduleRepository,
) {
    private suspend fun <T> call(block: suspend (String) -> T): Result<T> {
        val b = session.current().businessId ?: return Result.failure(IllegalStateException("no business"))
        return apiCall { block(b) }
    }

    /** Vuelve a pedir el negocio (con su historial de reglas de jornada) y lo guarda. */
    suspend fun refresh(): Result<BusinessEntity> = call { api.business(it) }.map { save(it.toEntity()) }

    /** `PUT /b/{id}` con solo lo que cambió; la respuesta trae el negocio completo y se guarda. */
    suspend fun update(body: UpdateBusinessBody): Result<BusinessEntity> = call { api.updateBusiness(it, body) }.map { save(it.toEntity()) }

    private suspend fun save(e: BusinessEntity): BusinessEntity { db.directory().upsertBusiness(e); return e }

    suspend fun notifySettings(): Result<NotificationSettingsDto> = schedules.settings()
    suspend fun saveNotifySettings(s: NotificationSettingsDto): Result<NotificationSettingsDto> = schedules.updateSettings(s)

    /**
     * Pide eliminar el negocio (`DELETE /b/{id}` → 202; queda marcado y deja de funcionar para todo el equipo). Solo si el servidor lo acepta se cierra la sesión
     * de este teléfono y se borran sus datos locales (el negocio ya no existe para nadie: lo pendiente ya no tiene a dónde enviarse).
     */
    suspend fun deleteBusiness(): Result<Unit> {
        val r = call { api.deleteBusiness(it) }
        if (r.isSuccess) wipeLocal()
        return r
    }

    suspend fun wipeLocal() {
        session.clearAccess()
        withContext(Dispatchers.IO) { db.clearAllTables() }
    }
}

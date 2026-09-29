package com.cuadra.caja.data.repo

import com.cuadra.caja.data.remote.AppConfigState
import com.cuadra.caja.data.remote.AppConfigStore
import com.cuadra.caja.data.remote.CuadraApi
import com.cuadra.caja.data.remote.apiCall
import com.cuadra.caja.domain.AppUpdate
import kotlinx.coroutines.flow.Flow

/**
 * Configuración pública del servidor (versión mínima, anuncio). Falla en silencio: sin red o con error se conserva lo último conocido,
 * y NUNCA se interrumpe la caja por no poder leerla.
 */
class AppConfigRepository(
    private val api: CuadraApi,
    private val store: AppConfigStore,
    private val now: () -> Long = System::currentTimeMillis,
) {
    val state: Flow<AppConfigState> = store.flow

    @Volatile private var lastSuccess: Long? = null

    /** Pide la configuración al arrancar y, después, como máximo cada `intervalMillis`. */
    suspend fun refreshIfDue(intervalMillis: Long = REFRESH_MILLIS) {
        if (!AppUpdate.shouldRefresh(lastSuccess, now(), intervalMillis)) return
        apiCall { api.config() }.onSuccess {
            store.save(it, now())
            lastSuccess = now()
        }
    }

    suspend fun dismissRecommended(version: String) = store.dismissRecommended(version)
    suspend fun dismissAnnouncement(id: String) = store.dismissAnnouncement(id)

    companion object {
        const val REFRESH_MILLIS = 4L * 3600 * 1000
    }
}

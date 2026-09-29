package com.cuadra.caja.data.repo

import com.cuadra.caja.data.remote.ActiveBody
import com.cuadra.caja.data.remote.CuadraApi
import com.cuadra.caja.data.remote.NotificationSettingsDto
import com.cuadra.caja.data.remote.PreferenceBody
import com.cuadra.caja.data.remote.ScheduleDto
import com.cuadra.caja.data.remote.ScheduleInputDto
import com.cuadra.caja.data.remote.ScheduleRunDto
import com.cuadra.caja.data.remote.apiCall
import com.cuadra.caja.data.session.SessionStore

/**
 * Programaciones y preferencias de avisos. A diferencia de las ventas, esto es de gestión (quien administra, con conexión): no pasa por la cola
 * de salida. Sin conexión devuelve `ApiFailure.Offline` y la pantalla lo dice.
 */
class ScheduleRepository(private val api: CuadraApi, private val session: SessionStore) {
    private suspend fun business(): String? = session.current().businessId

    private suspend fun <T> call(block: suspend (String) -> T): Result<T> {
        val b = business() ?: return Result.failure(IllegalStateException("no business"))
        return apiCall { block(b) }
    }

    suspend fun list(): Result<List<ScheduleDto>> = call { api.schedules(it) }
    suspend fun save(id: String, input: ScheduleInputDto): Result<ScheduleDto> = call { api.saveSchedule(it, id, input) }
    suspend fun setActive(id: String, active: Boolean): Result<ScheduleDto> = call { api.setScheduleActive(it, id, ActiveBody(active)) }
    suspend fun sendNow(id: String, input: ScheduleInputDto): Result<ScheduleDto> = call { api.sendScheduleNow(it, id, input) }
    suspend fun delete(id: String): Result<Unit> = call { api.deleteSchedule(it, id) }
    suspend fun runs(id: String): Result<List<ScheduleRunDto>> = call { api.scheduleRuns(it, id) }

    suspend fun preferences(): Result<Map<String, Boolean>> = call { api.notificationPreferences(it) }
    suspend fun setPreference(type: String, enabled: Boolean): Result<Map<String, Boolean>> = call { api.setNotificationPreference(it, PreferenceBody(type, enabled)) }
    suspend fun settings(): Result<NotificationSettingsDto> = call { api.notificationSettings(it) }
    suspend fun updateSettings(s: NotificationSettingsDto): Result<NotificationSettingsDto> = call { api.updateNotificationSettings(it, s) }
}

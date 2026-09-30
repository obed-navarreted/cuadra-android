package com.cuadra.caja.data.remote

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/** Última configuración pública conocida del servidor, más lo que la persona ya descartó. Sirve sin conexión. */
data class AppConfigState(
    val minAppVersion: String? = null,
    val recommendedAppVersion: String? = null,
    val announcement: AnnouncementDto? = null,
    val dismissedRecommended: String? = null,
    val dismissedAnnouncementId: String? = null,
    val lastSuccessAt: Long? = null,
    val supportEmail: String? = null,
    val supportWhatsapp: String? = null,
    /** El panel web (la consola de la plataforma vive en `panelUrl/console`). */
    val panelUrl: String? = null,
)

private val Context.appConfigDataStore by preferencesDataStore("app_config")

class AppConfigStore(context: Context) {
    private val store = context.applicationContext.appConfigDataStore

    private object K {
        val min = stringPreferencesKey("min_app_version")
        val recommended = stringPreferencesKey("recommended_app_version")
        val announcement = stringPreferencesKey("announcement_json")
        val dismissedRecommended = stringPreferencesKey("dismissed_recommended")
        val dismissedAnnouncement = stringPreferencesKey("dismissed_announcement")
        val lastSuccess = longPreferencesKey("last_success_at")
        val supportEmail = stringPreferencesKey("support_email")
        val supportWhatsapp = stringPreferencesKey("support_whatsapp")
        val panelUrl = stringPreferencesKey("panel_url")
    }

    val flow: Flow<AppConfigState> = store.data.catch { if (it is IOException) emit(emptyPreferences()) else throw it }.map { it.toState() }

    /** Guarda la respuesta del servidor (la última manda: si el mínimo baja, el bloqueo también). */
    suspend fun save(cfg: ConfigDto, now: Long) = store.edit { p ->
        put(p, K.min, cfg.minAppVersion)
        put(p, K.recommended, cfg.recommendedAppVersion)
        put(p, K.announcement, cfg.announcement?.let { ApiJson.encodeToString(AnnouncementDto.serializer(), it) })
        put(p, K.supportEmail, cfg.supportEmail)
        put(p, K.supportWhatsapp, cfg.supportWhatsapp)
        put(p, K.panelUrl, cfg.panelUrl)
        p[K.lastSuccess] = now
    }

    suspend fun dismissRecommended(version: String) = store.edit { it[K.dismissedRecommended] = version }
    suspend fun dismissAnnouncement(id: String) = store.edit { it[K.dismissedAnnouncement] = id }

    private fun put(p: androidx.datastore.preferences.core.MutablePreferences, key: Preferences.Key<String>, value: String?) {
        if (value.isNullOrBlank()) p.remove(key) else p[key] = value
    }

    private fun Preferences.toState() = AppConfigState(
        minAppVersion = this[K.min],
        recommendedAppVersion = this[K.recommended],
        announcement = this[K.announcement]?.let { runCatching { ApiJson.decodeFromString(AnnouncementDto.serializer(), it) }.getOrNull() },
        dismissedRecommended = this[K.dismissedRecommended],
        dismissedAnnouncementId = this[K.dismissedAnnouncement],
        lastSuccessAt = this[K.lastSuccess],
        supportEmail = this[K.supportEmail],
        supportWhatsapp = this[K.supportWhatsapp],
        panelUrl = this[K.panelUrl],
    )
}

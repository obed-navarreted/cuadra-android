package com.cuadra.caja.data.session

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Estado de acceso del teléfono. Los tokens viajan cifrados en disco; aquí ya están descifrados. */
data class Session(
    val userToken: String? = null,
    val deviceToken: String? = null,
    val deviceId: String? = null,
    val businessId: String? = null,
    val memberId: String? = null,
    val memberName: String? = null,
    val memberRole: String? = null,
) {
    val isLinked: Boolean get() = deviceToken != null && businessId != null
    val hasMember: Boolean get() = memberId != null
}

private val Context.sessionDataStore by preferencesDataStore("session")

class SessionStore(context: Context, private val box: SecureBox = SecureBox()) {
    private val store = context.applicationContext.sessionDataStore

    private object K {
        val userToken = stringPreferencesKey("user_token")
        val deviceToken = stringPreferencesKey("device_token")
        val deviceId = stringPreferencesKey("device_id")
        val businessId = stringPreferencesKey("business_id")
        val memberId = stringPreferencesKey("member_id")
        val memberName = stringPreferencesKey("member_name")
        val memberRole = stringPreferencesKey("member_role")
    }

    val flow: Flow<Session> = store.data.catch { if (it is IOException) emit(emptyPreferences()) else throw it }.map { it.toSession() }

    suspend fun current(): Session = flow.first()

    suspend fun setUserToken(token: String?) = store.edit { p -> put(p, K.userToken, token?.let(box::encrypt)) }

    /** Vincula el teléfono a un negocio: a partir de aquí las llamadas van con el token del dispositivo. */
    suspend fun link(deviceToken: String, deviceId: String, businessId: String) = store.edit { p ->
        put(p, K.deviceToken, box.encrypt(deviceToken)); put(p, K.deviceId, deviceId); put(p, K.businessId, businessId)
        p.remove(K.memberId); p.remove(K.memberName); p.remove(K.memberRole)
    }

    /** Quién está usando la caja ahora (validado con su PIN en el teléfono). */
    suspend fun setActiveMember(id: String?, name: String?, role: String?) = store.edit { p ->
        put(p, K.memberId, id); put(p, K.memberName, name); put(p, K.memberRole, role)
    }

    /** Vuelve a la pantalla de elegir persona sin cerrar el negocio ni perder lo pendiente. */
    suspend fun lockMember() = setActiveMember(null, null, null)

    /** Desvincula del todo (el teléfono fue revocado o se cierra sesión). Lo pendiente sin enviar NO se borra: vive en Room. */
    suspend fun clearAccess() = store.edit { it.clear() }

    private fun put(p: androidx.datastore.preferences.core.MutablePreferences, key: Preferences.Key<String>, value: String?) {
        if (value == null) p.remove(key) else p[key] = value
    }

    private fun Preferences.toSession() = Session(
        userToken = this[K.userToken]?.let(box::decrypt),
        deviceToken = this[K.deviceToken]?.let(box::decrypt),
        deviceId = this[K.deviceId],
        businessId = this[K.businessId],
        memberId = this[K.memberId],
        memberName = this[K.memberName],
        memberRole = this[K.memberRole],
    )
}

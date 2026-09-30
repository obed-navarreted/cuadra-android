package com.cuadra.caja.data.session

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
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
    /** Entró con un PIN que el dueño le puso y debe elegir el suyo antes de usar la caja (ADR 0012). */
    val pinChangePending: Boolean = false,
    /**
     * El dueño dio de baja a la persona de este teléfono PERSONAL: el servidor lo revocó. Se muestra «Tu acceso fue desactivado» y el teléfono solo
     * termina de enviar lo que esa persona hizo antes de la baja (el token se conserva para eso).
     */
    val accessDisabled: Boolean = false,
    /** Nombre de la persona que acaba de ser dada de baja en este teléfono COMPARTIDO: se avisa una vez en la pantalla de elegir persona. */
    val disabledMemberNotice: String? = null,
    /** La cuenta de Google con que se entró es administradora de la PLATAFORMA (la consola vive en el panel web; la app solo la abre). */
    val platformAdmin: Boolean = false,
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
        val swipeHintSeen = booleanPreferencesKey("swipe_hint_seen")
        val pinChangePending = booleanPreferencesKey("pin_change_pending")
        val lastBusinessCode = stringPreferencesKey("last_business_code")
        val accessDisabled = booleanPreferencesKey("access_disabled")
        val disabledMemberNotice = stringPreferencesKey("disabled_member_notice")
        val platformAdmin = booleanPreferencesKey("platform_admin")
    }

    val flow: Flow<Session> = store.data.catch { if (it is IOException) emit(emptyPreferences()) else throw it }.map { it.toSession().also(::stamp) }

    /** Cada cambio de sesión actualiza el sello de la cola: lo que se encole desde ya lleva a esta persona y este negocio. */
    private fun stamp(s: Session) {
        com.cuadra.caja.data.local.OutboxStamp.memberId = s.memberId
        com.cuadra.caja.data.local.OutboxStamp.businessId = s.businessId
    }

    private suspend fun edit(block: suspend (androidx.datastore.preferences.core.MutablePreferences) -> Unit): Preferences =
        store.edit(block).also { stamp(it.toSession()) }

    suspend fun current(): Session = flow.first()

    suspend fun setUserToken(token: String?) = edit { p -> put(p, K.userToken, token?.let(box::encrypt)); if (token == null) p.remove(K.platformAdmin) }

    suspend fun setPlatformAdmin(admin: Boolean) { edit { p -> if (admin) p[K.platformAdmin] = true else p.remove(K.platformAdmin) } }

    /** Vincula el teléfono a un negocio: a partir de aquí las llamadas van con el token del dispositivo. */
    suspend fun link(deviceToken: String, deviceId: String, businessId: String) = edit { p ->
        put(p, K.deviceToken, box.encrypt(deviceToken)); put(p, K.deviceId, deviceId); put(p, K.businessId, businessId)
        p.remove(K.memberId); p.remove(K.memberName); p.remove(K.memberRole); p.remove(K.accessDisabled)
    }

    /**
     * Entrada del equipo con código + usuario + PIN (ADR 0012): vincula el teléfono Y deja a la persona activa en un solo paso (acaba de demostrar quién es
     * con su PIN, en el servidor). Suelta una sesión de Google que hubiera: este teléfono no tiene más poder que quien lo vinculó.
     */
    suspend fun linkMember(deviceToken: String, deviceId: String, businessId: String, memberId: String, name: String, role: String, pinChangePending: Boolean) = edit { p ->
        put(p, K.deviceToken, box.encrypt(deviceToken)); put(p, K.deviceId, deviceId); put(p, K.businessId, businessId)
        put(p, K.memberId, memberId); put(p, K.memberName, name); put(p, K.memberRole, role)
        p.remove(K.userToken); p.remove(K.accessDisabled); p.remove(K.platformAdmin)
        if (pinChangePending) p[K.pinChangePending] = true else p.remove(K.pinChangePending)
    }

    suspend fun setPinChangePending(pending: Boolean) { edit { p -> if (pending) p[K.pinChangePending] = true else p.remove(K.pinChangePending) } }

    /** El último código del negocio con el que se entró en este teléfono (sobrevive a cerrar sesión). */
    suspend fun lastBusinessCode(): String? = store.data.first()[K.lastBusinessCode]

    suspend fun setLastBusinessCode(code: String) { edit { it[K.lastBusinessCode] = code } }

    /** Quién está usando la caja ahora (validado con su PIN en el teléfono). */
    suspend fun setActiveMember(id: String?, name: String?, role: String?) = edit { p ->
        put(p, K.memberId, id); put(p, K.memberName, name); put(p, K.memberRole, role)
    }

    /** Vuelve a la pantalla de elegir persona sin cerrar el negocio ni perder lo pendiente. */
    suspend fun lockMember() = setActiveMember(null, null, null)

    /** «Tu acceso fue desactivado» (teléfono personal revocado por la baja de su persona). */
    suspend fun setAccessDisabled(disabled: Boolean) { edit { p -> if (disabled) p[K.accessDisabled] = true else p.remove(K.accessDisabled) } }

    /**
     * La persona activa de un teléfono compartido fue dada de baja: se vuelve a «¿Quién atiende?» con un aviso. Lo que hizo antes de la baja sigue en la
     * cola y se envía igual (el servidor lo acepta con su nombre).
     */
    suspend fun memberDisabled() = edit { p ->
        p[K.memberName]?.let { p[K.disabledMemberNotice] = it }
        p.remove(K.memberId); p.remove(K.memberName); p.remove(K.memberRole)
    }

    suspend fun dismissDisabledNotice() { edit { p -> p.remove(K.disabledMemberNotice) } }

    /** Desvincula del todo (el teléfono fue revocado o se cierra sesión). Lo pendiente sin enviar NO se borra: vive en Room. */
    suspend fun clearAccess() = edit { p ->
        val hint = p[K.swipeHintSeen]
        val code = p[K.lastBusinessCode]
        p.clear()
        if (hint != null) p[K.swipeHintSeen] = hint   // una preferencia de la pantalla, no del acceso
        if (code != null) p[K.lastBusinessCode] = code   // para no reescribirlo al volver a entrar
    }

    /** ¿Ya se mostró la pista de deslizar en la hoja del recibo? (una sola vez por teléfono). */
    suspend fun swipeHintSeen(): Boolean = store.data.first()[K.swipeHintSeen] == true

    suspend fun markSwipeHintSeen() { edit { it[K.swipeHintSeen] = true } }

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
        pinChangePending = this[K.pinChangePending] == true,
        accessDisabled = this[K.accessDisabled] == true,
        disabledMemberNotice = this[K.disabledMemberNotice],
        platformAdmin = this[K.platformAdmin] == true,
    )
}

package com.cuadra.caja.data.push

import android.content.Context
import android.util.Log
import com.cuadra.caja.BuildConfig
import com.cuadra.caja.data.remote.CuadraApi
import com.cuadra.caja.data.remote.PushTokenBody
import com.cuadra.caja.data.remote.apiCall
import com.cuadra.caja.data.session.Session
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Registra el token de Firebase de esta instalación en el negocio activo (`PUT /api/b/{negocio}/push-tokens`, con el teléfono y la persona que atiende). Sin
 * la configuración de Firebase (compilación sin `google-services.json`) o sin Google Play no hace nada: la app sigue con la sincronización frecuente.
 * `active`: ya hay un token registrado en este negocio (los avisos al instante deberían llegar).
 */
class PushRegistrar(private val context: Context, private val api: CuadraApi, private val prefs: PushPrefs) {
    @Volatile var active: Boolean = false
        private set

    /** ¿Firebase está disponible en esta compilación y este teléfono? */
    fun available(): Boolean = BuildConfig.HAS_FIREBASE && runCatching { com.google.firebase.FirebaseApp.getApps(context).isNotEmpty() }.getOrDefault(false)

    private suspend fun token(): String? = if (!available()) null else runCatching {
        suspendCancellableCoroutine<String?> { cont ->
            com.google.firebase.messaging.FirebaseMessaging.getInstance().token
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { cont.resume(null) }
        }
    }.getOrNull()

    /** Con un negocio, un teléfono vinculado y alguien atendiendo: registra el token (si cambió algo desde la última vez). `fresh`: un token nuevo de Firebase. */
    suspend fun register(session: Session, fresh: String? = null) {
        val business = session.businessId ?: return
        if (session.deviceToken == null && session.userToken == null) return
        val token = fresh ?: token() ?: run { active = false; return }
        val key = listOf(token, business, session.memberId.orEmpty()).joinToString("|")
        if (fresh == null && prefs.registeredKey == key) { active = true; return }
        val locale = java.util.Locale.getDefault().language
        apiCall { api.registerPushToken(business, PushTokenBody(token, "ANDROID", locale, BuildConfig.VERSION_NAME)) }
            .onSuccess { prefs.registeredKey = key; active = true }
            .onFailure { Log.i("CuentivaPush", "No se pudo registrar el token (se reintenta luego)") }
    }

    /** Al cerrar sesión: se olvida el registro (al volver a entrar se registra de nuevo para ese negocio). */
    fun forget() { prefs.registeredKey = null; active = false }
}

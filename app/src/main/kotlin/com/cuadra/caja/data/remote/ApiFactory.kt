package com.cuadra.caja.data.remote

import com.cuadra.caja.data.session.Session
import java.util.concurrent.TimeUnit
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * Qué credenciales lleva una petición. Una que ya trae su `Authorization` (elegida a propósito en la interfaz de Retrofit) se respeta tal cual.
 * Con teléfono vinculado y persona elegida: `Device` + `X-Member-Id`; con sesión de Google y sin persona: `Bearer`; solo teléfono: `Device`.
 */
internal fun authHeaders(s: Session, explicitAuthorization: String?, explicitMember: String? = null): Map<String, String> = when {
    explicitAuthorization != null -> emptyMap()
    // La petición ya dice como quién va (enviar la cola sin persona activa): solo se agrega el token del teléfono.
    explicitMember != null && s.deviceToken != null -> mapOf("Authorization" to "Device ${s.deviceToken}")
    // Teléfono vinculado con una persona ya elegida (PIN): el servidor actúa como esa persona.
    s.deviceToken != null && s.memberId != null -> mapOf("Authorization" to "Device ${s.deviceToken}", "X-Member-Id" to s.memberId)
    // Alguien entró con Google en este teléfono y aún no eligió persona: se usa su sesión.
    s.userToken != null -> mapOf("Authorization" to "Bearer ${s.userToken}")
    s.deviceToken != null -> mapOf("Authorization" to "Device ${s.deviceToken}")
    else -> emptyMap()
}

object ApiFactory {
    /**
     * `session` se lee en cada petición: al cambiar de cajero o vincular el teléfono no hace falta reconstruir nada.
     * Con teléfono vinculado: `Device <token>` + `X-Member-Id`; con sesión de Google: `Bearer <token>`.
     */
    fun create(baseUrl: String, session: () -> Session): CuadraApi = retrofit(baseUrl, session).create(CuadraApi::class.java)

    /** La gestión del equipo comparte el mismo cliente y las mismas credenciales. */
    fun createTeam(baseUrl: String, session: () -> Session): TeamApi = retrofit(baseUrl, session).create(TeamApi::class.java)

    private fun retrofit(baseUrl: String, session: () -> Session): Retrofit {
        val auth = Interceptor { chain ->
            val s = session()
            val req = chain.request().newBuilder().apply {
                authHeaders(s, chain.request().header("Authorization"), chain.request().header("X-Member-Id")).forEach { (k, v) -> header(k, v) }
            }.build()
            chain.proceed(req)
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(auth)
            .connectTimeout(8, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).writeTimeout(20, TimeUnit.SECONDS)
            .build()
        return Retrofit.Builder()
            .baseUrl(if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/")
            .client(client)
            .addConverterFactory(ApiJson.asConverterFactory("application/json".toMediaType()))
            .build()
    }
}

package com.cuadra.caja.data.remote

import com.cuadra.caja.data.session.Session
import java.util.concurrent.TimeUnit
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

object ApiFactory {
    /**
     * `session` se lee en cada petición: al cambiar de cajero o vincular el teléfono no hace falta reconstruir nada.
     * Con teléfono vinculado: `Device <token>` + `X-Member-Id`; con sesión de Google: `Bearer <token>`.
     */
    fun create(baseUrl: String, session: () -> Session): CuadraApi {
        val auth = Interceptor { chain ->
            val s = session()
            val req = chain.request().newBuilder().apply {
                when {
                    // Teléfono vinculado con una persona ya elegida (PIN): el servidor actúa como esa persona.
                    s.deviceToken != null && s.memberId != null -> {
                        header("Authorization", "Device ${s.deviceToken}")
                        header("X-Member-Id", s.memberId)
                    }
                    // Alguien entró con Google en este teléfono y aún no eligió persona: se usa su sesión.
                    s.userToken != null -> header("Authorization", "Bearer ${s.userToken}")
                    s.deviceToken != null -> header("Authorization", "Device ${s.deviceToken}")
                }
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
            .create(CuadraApi::class.java)
    }
}

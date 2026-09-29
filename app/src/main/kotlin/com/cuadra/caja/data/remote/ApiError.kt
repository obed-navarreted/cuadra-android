package com.cuadra.caja.data.remote

import java.io.IOException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import retrofit2.HttpException

/** Fallo de una llamada, ya clasificado: la app decide qué hacer según sea red, sesión o regla de negocio. */
sealed class ApiFailure(message: String) : Exception(message) {
    /** Sin conexión o timeout. Se reintenta más tarde; no es un error de los datos. */
    class Offline(cause: Throwable) : ApiFailure(cause.message ?: "offline")

    /** El servidor respondió con un error. `code` es el de Problem Details (estable, se traduce). */
    class Http(val status: Int, val code: String, message: String, val feature: String? = null, val limit: Int? = null) : ApiFailure(message) {
        val isAuth: Boolean get() = status == 401
    }
}

internal val ApiJson = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = true }

/** Ejecuta una llamada de Retrofit y la convierte en `Result` con `ApiFailure`. */
suspend fun <T> apiCall(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (e: HttpException) {
    val body = runCatching { e.response()?.errorBody()?.string() }.getOrNull()
    Result.failure(parseProblem(e.code(), body, e.message()))
} catch (e: IOException) {
    Result.failure(ApiFailure.Offline(e))
}

/**
 * Convierte el cuerpo de un error (Problem Details) en `ApiFailure.Http`. Tolerante: un cuerpo ilegible o con campos de más o de tipo raro
 * nunca lanza; conserva `feature` y `limit` (PLAN_LIMIT) cuando vienen.
 */
fun parseProblem(status: Int, body: String?, fallbackMessage: String): ApiFailure.Http {
    val obj = runCatching { ApiJson.parseToJsonElement(body.orEmpty()).jsonObject }.getOrNull()
    fun str(key: String) = (obj?.get(key) as? JsonPrimitive)?.takeIf { it.isString }?.content
    val limit = (obj?.get("limit") as? JsonPrimitive)?.content?.toDoubleOrNull()?.toInt()
    return ApiFailure.Http(status, str("code") ?: "HTTP_$status", str("detail") ?: fallbackMessage, str("feature"), limit)
}

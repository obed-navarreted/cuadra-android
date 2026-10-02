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
    class Http(val status: Int, val code: String, message: String, val feature: String? = null, val limit: Int? = null,
               /** SALE_LOCKED: quién tiene abierta la cuenta («La está cobrando Ana»). */
               val memberName: String? = null,
               /** PIN_VERIFICATION_REQUIRED: de quién hay que confirmar el PIN. */
               val memberId: String? = null) : ApiFailure(message) {
        val isAuth: Boolean get() = status == 401
    }
}

internal val ApiJson = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = true }

/** Ejecuta una llamada de Retrofit y la convierte en `Result` con `ApiFailure`. */
suspend fun <T> apiCall(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (e: HttpException) {
    val body = runCatching { e.response()?.errorBody()?.string() }.getOrNull()
    val problem = parseProblem(e.code(), body, e.message())
    // El servidor pide confirmar el PIN en este teléfono (ADR 0012, 2026-10-01): la sesión baja al rol base y las pantallas ofrecen «Confirmar PIN».
    if (problem.code == "PIN_VERIFICATION_REQUIRED") runCatching { PinVerificationSignal.onRequired?.invoke(problem.memberId) }
    Result.failure(problem)
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
    return ApiFailure.Http(status, str("code") ?: "HTTP_$status", str("detail") ?: fallbackMessage, str("feature"), limit, str("memberName"), str("memberId"))
}

/** Quién se entera cuando una llamada responde PIN_VERIFICATION_REQUIRED (lo pone `AppContainer`). Recibe el id de la persona, si vino. */
object PinVerificationSignal {
    @Volatile var onRequired: ((String?) -> Unit)? = null
}

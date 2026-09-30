package com.cuadra.caja.data.repo

import android.os.Build
import at.favre.lib.crypto.bcrypt.BCrypt
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import com.cuadra.caja.BuildConfig
import com.cuadra.caja.data.local.CuadraDatabase
import com.cuadra.caja.data.local.mergeMembers
import com.cuadra.caja.data.remote.CreateBusinessBody
import com.cuadra.caja.data.remote.CuadraApi
import com.cuadra.caja.data.remote.GoogleLoginBody
import com.cuadra.caja.data.remote.LinkInfoBody
import com.cuadra.caja.data.remote.MemberLoginBody
import com.cuadra.caja.data.remote.MemberLoginResult
import com.cuadra.caja.data.remote.MeDto
import com.cuadra.caja.data.remote.MembershipDto
import com.cuadra.caja.data.remote.PinBody
import com.cuadra.caja.data.remote.UpdateBusinessBody
import com.cuadra.caja.data.remote.apiCall
import com.cuadra.caja.data.session.SessionStore
import com.cuadra.caja.data.sync.toEntity
import com.cuadra.caja.domain.PinGuard

sealed interface UnlockResult {
    data class Ok(val memberId: String, val name: String, val role: String) : UnlockResult
    data object WrongPin : UnlockResult
    data class Locked(val waitMillis: Long) : UnlockResult
    data object NoPin : UnlockResult
}

/** Entrada con Google, negocio, vinculación del teléfono y cambio rápido de cajero con PIN (validado sin conexión). */
class AuthRepository(
    private val db: CuadraDatabase,
    private val api: CuadraApi,
    private val session: SessionStore,
    private val guard: PinGuard = PinGuard(),
    private val local: LocalBusinessData = LocalBusinessData(db),
) {
    private fun deviceInfo() = LinkInfoBody(
        deviceName = listOfNotNull(Build.MANUFACTURER, Build.MODEL).joinToString(" ").take(80).ifBlank { "Android" },
        model = Build.MODEL, osVersion = "Android ${Build.VERSION.RELEASE}", appVersion = BuildConfig.VERSION_NAME,
    )

    suspend fun signInWithGoogle(idToken: String): Result<MeDto> = apiCall { api.google(GoogleLoginBody(idToken)) }.mapCatching {
        session.setUserToken(it.token)
        api.me().also { me -> session.setPlatformAdmin(me.platformAdmin) }
    }

    suspend fun me(): Result<MeDto> {
        val token = session.current().userToken
        return apiCall { if (token != null) api.meAs("Bearer $token") else api.me() }.onSuccess { session.setPlatformAdmin(it.platformAdmin) }
    }

    /**
     * «Eliminar mi cuenta» (Google Play lo exige). El servidor la rechaza con OWNS_BUSINESSES si todavía es dueño de un negocio activo (la pantalla ya lo
     * explica antes). Con éxito, la sesión queda sin valor: quien llama cierra sesión en el teléfono.
     */
    suspend fun deleteAccount(): Result<Unit> {
        val token = session.current().userToken ?: return Result.failure(IllegalStateException("no Google session"))
        return apiCall { api.deleteMe("Bearer $token") }
    }

    /** Países con su moneda y zona sugeridas (del servidor; sin conexión, la lista de la app). */
    suspend fun countries(): List<com.cuadra.caja.domain.CountryOption> = apiCall { api.countries() }.getOrNull()
        ?.map { com.cuadra.caja.domain.CountryOption(it.code, it.currency, it.timezone, it.locale) }?.takeIf { it.isNotEmpty() } ?: com.cuadra.caja.domain.CountryChoice.FALLBACK

    /** Un negocio nuevo con solo el nombre (PLAN.md 5.0) y este teléfono ya vinculado. */
    suspend fun createBusiness(name: String, timezone: String? = phoneTimeZone(), country: String? = null, currency: String? = null): Result<String> {
        // Un negocio nuevo es otro negocio: si el anterior tiene algo sin enviar, se bloquea ANTES de crearlo.
        local.blockerForAnyOther()?.let { return Result.failure(it) }
        return apiCall { api.createBusiness(CreateBusinessBody(name = name.trim(), timezone = timezone, country = country, currency = currency)) }.mapCatching {
            linkThisPhone(it.id).getOrThrow()
            it.id
        }
    }

    /** Vincula este teléfono al negocio (dueño/admin con Google). Si los datos del teléfono son de otro negocio, primero se vacían (o se bloquea). */
    suspend fun linkThisPhone(businessId: String): Result<Unit> {
        local.prepareFor(businessId)?.let { return Result.failure(it) }
        return apiCall { api.selfLink(businessId, deviceInfo()) }.map {
            session.link(it.deviceToken, it.deviceId, businessId)
        }
    }

    /**
     * Entrada del equipo (ADR 0012): código del negocio + PIN (el servidor reconoce a la persona por su PIN). Si el servidor acepta, este teléfono queda vinculado y la persona activa
     * (acaba de demostrar quién es), y el directorio se trae COMO TELÉFONO (con los hashes de PIN que este teléfono puede usar sin conexión) ANTES de
     * cambiar la sesión, para que la pantalla no pase por «¿Quién atiende?». Necesita conexión.
     */
    suspend fun memberLogin(code: String, pin: String): Result<MemberLoginResult> {
        val info = deviceInfo()
        val body = MemberLoginBody(code, pin, info.deviceName, info.model, info.osVersion, info.appVersion)
        return apiCall { api.memberLogin(body) }.mapCatching { r ->
            // El código es de OTRO negocio que el de los datos del teléfono: se vacían (o se bloquea si el anterior tiene algo sin enviar). Se decide
            // después de entrar porque solo el servidor sabe de qué negocio es el código.
            local.prepareFor(r.businessId)?.let { throw it }
            // Si el directorio no llega ahora (red caída justo después), la primera sincronización lo trae; entrar no depende de eso.
            fetchDirectory(r.businessId, "Device ${r.deviceToken}")
            session.setLastBusinessCode(code)
            session.linkMember(r.deviceToken, r.deviceId, r.businessId, r.memberId, r.memberName, r.role, r.pinMustChange)
            r
        }
    }

    /** Termina el «Elige tu PIN nuevo» de quien entró con un PIN puesto por el dueño: lo guarda, refresca su hash y le deja usar la caja. */
    suspend fun finishPinChange(pin: String): Result<Unit> {
        val s = session.current()
        val businessId = s.businessId ?: return Result.failure(IllegalStateException("sin negocio"))
        val memberId = s.memberId ?: return Result.failure(IllegalStateException("sin persona"))
        return apiCall { api.setPin(businessId, memberId, PinBody(pin, mustChangePin = false)) }.map {
            loadDirectory()
            session.setPinChangePending(false)
        }
    }

    /** Trae miembros y negocio para poder elegir persona y validar PIN aunque la primera sincronización aún no termine. */
    suspend fun loadDirectory(): Result<Unit> {
        val s = session.current()
        val businessId = s.businessId ?: return Result.failure(IllegalStateException("sin negocio"))
        // Con el teléfono ya vinculado y la sesión de Google aún puesta (el dueño acaba de crear el negocio), el listado se pide como TELÉFONO:
        // solo así el servidor entrega el hash de cada PIN, y sin él este teléfono no puede validar el PIN que el dueño acaba de crear.
        val asDevice = s.deviceToken != null && s.memberId == null
        return fetchDirectory(businessId, if (asDevice) "Device ${s.deviceToken}" else null)
    }

    private suspend fun fetchDirectory(businessId: String, authorization: String?): Result<Unit> =
        apiCall { if (authorization != null) api.membersAs(businessId, authorization) else api.members(businessId) }.map { members -> db.directory().mergeMembers(members.map { it.toEntity() }) }

    /** El dueño (que entró con Google y no tiene PIN) crea el suyo para poder entrar rápido en este teléfono. */
    suspend fun setOwnPin(pin: String, memberId: String): Result<Unit> {
        val businessId = session.current().businessId ?: return Result.failure(IllegalStateException("sin negocio"))
        return apiCall { api.setPin(businessId, memberId, PinBody(pin)) }
    }

    fun membershipFor(me: MeDto, businessId: String): MembershipDto? = me.businesses.firstOrNull { it.businessId == businessId }

    /** Valida el PIN contra el hash guardado en el teléfono: funciona sin conexión. 5 fallos bloquean con espera creciente. */
    suspend fun unlock(memberId: String, pin: String): UnlockResult {
        val member = db.directory().member(memberId)
        return when (val r = checkPin(member, pin)) {
            is UnlockResult.Ok -> { session.setActiveMember(r.memberId, r.name, r.role); r }
            else -> r
        }
    }

    /**
     * «Escribe tu PIN» en un teléfono compartido: el PIN dice quién es (no se repite entre las personas activas del negocio). Se compara, sin conexión,
     * contra el hash de cada persona que este teléfono puede usar (en paralelo: bcrypt es lento a propósito). Mismo bloqueo por intentos que al elegir
     * a la persona.
     */
    suspend fun unlockByPin(pin: String): UnlockResult {
        if (guard.isLocked()) return UnlockResult.Locked(guard.waitMillis())
        val candidates = db.directory().activeMembers().first().filter { it.pinHash != null }
        val match = matchPin(pin, candidates.map { it.id to it.pinHash!! })?.let { id -> candidates.first { it.id == id } }
        if (match == null) {
            guard.recordFailure()
            return if (guard.isLocked()) UnlockResult.Locked(guard.waitMillis()) else UnlockResult.WrongPin
        }
        guard.recordSuccess()
        session.setActiveMember(match.id, match.displayName, match.role)
        return UnlockResult.Ok(match.id, match.displayName, match.role)
    }

    /**
     * Comprueba el PIN ACTUAL de una persona sin cambiar de cajero (para cambiar el propio PIN). Usa el mismo bloqueo por intentos que la
     * pantalla de PIN: quien adivina el PIN desde «Mi cuenta» se topa con la misma espera.
     */
    suspend fun verifyPin(memberId: String, pin: String): UnlockResult = checkPin(db.directory().member(memberId), pin)

    private fun checkPin(member: com.cuadra.caja.data.local.MemberEntity?, pin: String): UnlockResult {
        if (guard.isLocked()) return UnlockResult.Locked(guard.waitMillis())
        member ?: return UnlockResult.NoPin
        val hash = member.pinHash ?: return UnlockResult.NoPin
        val ok = runCatching { BCrypt.verifyer().verify(pin.toCharArray(), hash.toCharArray()).verified }.getOrDefault(false)
        if (!ok) {
            guard.recordFailure()
            return if (guard.isLocked()) UnlockResult.Locked(guard.waitMillis()) else UnlockResult.WrongPin
        }
        guard.recordSuccess()
        return UnlockResult.Ok(member.id, member.displayName, member.role)
    }

    suspend fun lock() = session.lockMember()

    suspend fun signOut() {
        runCatching { api.logout() }
        session.clearAccess()
    }

    /** El dueño activa o esconde un módulo (fiado, gastos, turnos…). El servidor decide si puede; el resultado se guarda en el teléfono. */
    suspend fun setModule(key: String, on: Boolean): Result<Unit> {
        val businessId = session.current().businessId ?: return Result.failure(IllegalStateException("no business"))
        return apiCall { api.updateBusiness(businessId, UpdateBusinessBody(modules = mapOf(key to on))) }.mapCatching { db.directory().upsertBusiness(it.toEntity()) }
    }
}

/**
 * La zona horaria del teléfono, SOLO para proponerla al crear el negocio (ADR 0011): desde ahí la zona y el corte son del negocio y ninguna
 * pantalla vuelve a usar la del teléfono para decidir a qué día pertenece algo. Un identificador que Java no reconoce (p. ej. "GMT+05:30") no se envía.
 */
internal fun phoneTimeZone(id: String = java.util.TimeZone.getDefault().id): String? = id.takeIf { runCatching { java.time.ZoneId.of(it) }.isSuccess && it.contains('/') || it == "UTC" }

/** Cuál de las personas (`id` → hash bcrypt) tiene este PIN; null si ninguna. Todas se comprueban a la vez en hilos de cálculo. */
internal suspend fun matchPin(pin: String, hashes: List<Pair<String, String>>): String? {
    if (!com.cuadra.caja.domain.PinRules.isValid(pin) || hashes.isEmpty()) return null
    return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
        hashes.map { (id, hash) ->
            async { id.takeIf { runCatching { BCrypt.verifyer().verify(pin.toCharArray(), hash.toCharArray()).verified }.getOrDefault(false) } }
        }.awaitAll().firstOrNull { it != null }
    }
}

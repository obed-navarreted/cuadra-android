package com.cuadra.caja.data.repo

import android.os.Build
import at.favre.lib.crypto.bcrypt.BCrypt
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import com.cuadra.caja.BuildConfig
import com.cuadra.caja.data.local.Db
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

/** «Confirmar PIN» con el servidor. */
sealed interface ConfirmPinResult {
    data object Ok : ConfirmPinResult
    data object WrongPin : ConfirmPinResult
    data class Locked(val waitMillis: Long) : ConfirmPinResult
    /** Sin conexión: «Conéctate para confirmar tu PIN la primera vez en este teléfono». */
    data object Offline : ConfirmPinResult
    data object Failed : ConfirmPinResult
}

sealed interface UnlockResult {
    /** `pendingVerification`: entró, pero con el rol base del teléfono hasta confirmar su PIN con el servidor (sin conexión al entrar). */
    data class Ok(val memberId: String, val name: String, val role: String, val pendingVerification: Boolean = false) : UnlockResult
    data object WrongPin : UnlockResult
    data class Locked(val waitMillis: Long) : UnlockResult
    data object NoPin : UnlockResult
}

/** Entrada con Google, negocio, vinculación del teléfono y cambio rápido de cajero con PIN (validado sin conexión). */
class AuthRepository(
    private val db: Db,
    private val api: CuadraApi,
    private val session: SessionStore,
    private val guard: PinGuard = PinGuard(),
    /** La base de UN negocio concreto (para guardar el directorio de un negocio al que la sesión aún no cambió). Por omisión, la misma `db`. */
    private val dbFor: (String) -> Db = { db },
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
        return apiCall { api.createBusiness(CreateBusinessBody(name = name.trim(), timezone = timezone, country = country, currency = currency)) }.mapCatching {
            linkThisPhone(it.id).getOrThrow()
            it.id
        }
    }

    /** Vincula este teléfono al negocio (dueño/admin con Google). Cada negocio tiene su propia base en el teléfono: nada se mezcla ni se pierde al cambiar. */
    suspend fun linkThisPhone(businessId: String): Result<Unit> {
        return apiCall { api.selfLink(businessId, deviceInfo()) }.map {
            session.link(it.deviceToken, it.deviceId, businessId)
            refreshDeviceAccess()
        }
    }

    // ---------- elevación por PIN verificado (ADR 0012, actualización 2026-10-01) ----------

    /**
     * Lo que el servidor dice de ESTE teléfono: su rol base y los permisos de PIN vigentes. Después ajusta a la persona activa: baja al rol base a quien
     * perdió su permiso (p. ej. le restablecieron el PIN) y sube a quien ya lo tiene. Sin conexión no cambia nada.
     */
    suspend fun refreshDeviceAccess(now: Long = System.currentTimeMillis()): Result<Unit> {
        val token = session.current().deviceToken ?: return Result.failure(IllegalStateException("sin teléfono"))
        return apiCall { api.deviceSelf("Device $token") }.map { me ->
            session.setDeviceAccess(me.baseRole, me.grants.mapNotNull { g -> instant(g.expiresAt)?.let { g.memberId to it } }.toMap())
            reconcileActiveMember(now)
        }
    }

    /** La persona activa actúa con el rol que le toca según el rol base y su permiso (ver `PinElevation.reconcile`). */
    suspend fun reconcileActiveMember(now: Long = System.currentTimeMillis()) {
        val s = session.current()
        val id = s.memberId ?: return
        val real = s.memberRealRole ?: s.memberRole ?: return
        val acting = com.cuadra.caja.domain.PinElevation.reconcile(real, s.baseRole, s.grants[id], now)
        if (acting.role != s.memberRole || acting.realRole != s.memberRealRole) session.setActiveMember(id, s.memberName, acting.role, acting.realRole)
    }

    /** El servidor respondió PIN_VERIFICATION_REQUIRED: el permiso de esa persona ya no vale aquí; si es la activa, baja al rol base. */
    suspend fun pinVerificationRequired(memberId: String?) {
        val s = session.current()
        val who = memberId ?: s.memberId ?: return
        session.dropGrant(who)
        if (who != s.memberId) return
        val real = s.memberRealRole ?: s.memberRole ?: return
        // Lo dice el servidor: aunque el teléfono creyera que su rol no supera al del teléfono, baja al de cajero hasta confirmar.
        val base = s.baseRole?.takeIf { com.cuadra.caja.domain.PinElevation.rank(it) < com.cuadra.caja.domain.PinElevation.rank(real) } ?: "CASHIER"
        if (com.cuadra.caja.domain.PinElevation.rank(real) > com.cuadra.caja.domain.PinElevation.rank(base)) session.setActiveMember(who, s.memberName, base, real)
    }

    /** Pregunta al servidor si `pin` es el de `memberId` (como teléfono). */
    private suspend fun askServer(memberId: String, pin: String): com.cuadra.caja.domain.PinElevation.ServerAnswer {
        val token = session.current().deviceToken ?: return com.cuadra.caja.domain.PinElevation.ServerAnswer.Failed
        val r = apiCall { api.verifyPin(memberId, com.cuadra.caja.data.remote.VerifyPinBody(pin), "Device $token") }
        return r.fold(
            onSuccess = { v -> instant(v.expiresAt)?.let { com.cuadra.caja.domain.PinElevation.ServerAnswer.Verified(it, v.baseRole) } ?: com.cuadra.caja.domain.PinElevation.ServerAnswer.Failed },
            onFailure = { e ->
                when {
                    e is com.cuadra.caja.data.remote.ApiFailure.Offline -> com.cuadra.caja.domain.PinElevation.ServerAnswer.Offline
                    e is com.cuadra.caja.data.remote.ApiFailure.Http && e.code == "INVALID_CREDENTIALS" -> com.cuadra.caja.domain.PinElevation.ServerAnswer.WrongPin
                    e is com.cuadra.caja.data.remote.ApiFailure.Http && (e.code == "LOCKED" || e.code == "RATE_LIMITED") -> com.cuadra.caja.domain.PinElevation.ServerAnswer.Locked(15 * 60_000L)
                    e is com.cuadra.caja.data.remote.ApiFailure.Http && e.code == "MEMBER_NOT_ACTIVE" -> com.cuadra.caja.domain.PinElevation.ServerAnswer.NotActive
                    else -> com.cuadra.caja.domain.PinElevation.ServerAnswer.Failed
                }
            },
        )
    }

    /** Tras acertar el PIN en el teléfono: deja a la persona activa con el rol que le toca, preguntando al servidor si hace falta. */
    private suspend fun activate(member: com.cuadra.caja.data.local.MemberEntity, pin: String, now: Long = System.currentTimeMillis()): UnlockResult {
        val s = session.current()
        val step = if (s.deviceToken == null) com.cuadra.caja.domain.PinElevation.AfterLocal.Ready(member.role)
        else com.cuadra.caja.domain.PinElevation.afterLocalUnlock(member.role, s.baseRole, s.grants[member.id], now)
        if (step is com.cuadra.caja.domain.PinElevation.AfterLocal.Ready) {
            session.setActiveMember(member.id, member.displayName, step.role)
            return UnlockResult.Ok(member.id, member.displayName, step.role)
        }
        return when (val o = com.cuadra.caja.domain.PinElevation.afterServer(member.role, s.baseRole, askServer(member.id, pin))) {
            is com.cuadra.caja.domain.PinElevation.Outcome.Elevated -> {
                session.addGrant(member.id, o.expiresAt, o.baseRole)
                session.setActiveMember(member.id, member.displayName, o.role)
                UnlockResult.Ok(member.id, member.displayName, o.role)
            }
            is com.cuadra.caja.domain.PinElevation.Outcome.Capped -> {
                session.setActiveMember(member.id, member.displayName, o.actingRole, o.realRole)
                UnlockResult.Ok(member.id, member.displayName, o.actingRole, pendingVerification = true)
            }
            // El PIN del teléfono era viejo o la persona ya no está: se trae el directorio de nuevo y no entra.
            com.cuadra.caja.domain.PinElevation.Outcome.Wrong, com.cuadra.caja.domain.PinElevation.Outcome.NotActive -> { loadDirectory(); UnlockResult.WrongPin }
            is com.cuadra.caja.domain.PinElevation.Outcome.Locked -> UnlockResult.Locked(o.waitMillis)
        }
    }

    /** «Confirmar PIN» (pantallas de administración o «Requiere atención»): el servidor confirma el PIN de `memberId` en este teléfono. */
    suspend fun confirmPin(memberId: String, pin: String, now: Long = System.currentTimeMillis()): ConfirmPinResult {
        val member = db.directory().member(memberId)
        // Primero en el teléfono: un PIN mal escrito no gasta intentos del servidor.
        when (val local = checkPin(member, pin)) {
            is UnlockResult.Ok -> Unit
            UnlockResult.WrongPin -> return ConfirmPinResult.WrongPin
            is UnlockResult.Locked -> return ConfirmPinResult.Locked(local.waitMillis)
            UnlockResult.NoPin -> if (member != null) return ConfirmPinResult.WrongPin
        }
        return when (val a = askServer(memberId, pin)) {
            is com.cuadra.caja.domain.PinElevation.ServerAnswer.Verified -> {
                session.addGrant(memberId, a.expiresAt, a.baseRole)
                reconcileActiveMember(now)
                ConfirmPinResult.Ok
            }
            com.cuadra.caja.domain.PinElevation.ServerAnswer.Offline -> ConfirmPinResult.Offline
            com.cuadra.caja.domain.PinElevation.ServerAnswer.WrongPin -> { loadDirectory(); ConfirmPinResult.WrongPin }
            is com.cuadra.caja.domain.PinElevation.ServerAnswer.Locked -> ConfirmPinResult.Locked(a.waitMillis)
            com.cuadra.caja.domain.PinElevation.ServerAnswer.NotActive, com.cuadra.caja.domain.PinElevation.ServerAnswer.Failed -> ConfirmPinResult.Failed
        }
    }

    private fun instant(iso: String?): Long? = iso?.let { runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrNull() }

    /**
     * Entrada del equipo (ADR 0012): código del negocio + PIN (el servidor reconoce a la persona por su PIN). Si el servidor acepta, este teléfono queda vinculado y la persona activa
     * (acaba de demostrar quién es), y el directorio se trae COMO TELÉFONO (con los hashes de PIN que este teléfono puede usar sin conexión) ANTES de
     * cambiar la sesión, para que la pantalla no pase por «¿Quién atiende?». Necesita conexión.
     */
    suspend fun memberLogin(code: String, pin: String): Result<MemberLoginResult> {
        val info = deviceInfo()
        val body = MemberLoginBody(code, pin, info.deviceName, info.model, info.osVersion, info.appVersion)
        return apiCall { api.memberLogin(body) }.mapCatching { r ->
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
        apiCall { if (authorization != null) api.membersAs(businessId, authorization) else api.members(businessId) }.map { members -> dbFor(businessId).directory().mergeMembers(members.map { it.toEntity() }) }

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
            is UnlockResult.Ok -> activate(member!!, pin)
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
        return activate(match, pin)
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

    /**
     * Cierra la sesión del teléfono. La base del negocio se libera sola (`BusinessDatabases`: se borra si no tiene nada sin enviar, si no se conserva
     * solo para enviarlo), y el estado del PIN (intentos fallidos) se reinicia: nada del negocio anterior queda a la vista para quien entre después.
     */
    suspend fun signOut() {
        runCatching { api.logout() }
        session.clearAccess()
        guard.reset()
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

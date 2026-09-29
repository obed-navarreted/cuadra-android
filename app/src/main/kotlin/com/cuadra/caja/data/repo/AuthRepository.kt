package com.cuadra.caja.data.repo

import android.os.Build
import at.favre.lib.crypto.bcrypt.BCrypt
import com.cuadra.caja.BuildConfig
import com.cuadra.caja.data.local.CuadraDatabase
import com.cuadra.caja.data.remote.CreateBusinessBody
import com.cuadra.caja.data.remote.CuadraApi
import com.cuadra.caja.data.remote.GoogleLoginBody
import com.cuadra.caja.data.remote.LinkInfoBody
import com.cuadra.caja.data.remote.LinkRequestCreatedDto
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
) {
    private fun deviceInfo() = LinkInfoBody(
        deviceName = listOfNotNull(Build.MANUFACTURER, Build.MODEL).joinToString(" ").take(80).ifBlank { "Android" },
        model = Build.MODEL, osVersion = "Android ${Build.VERSION.RELEASE}", appVersion = BuildConfig.VERSION_NAME,
    )

    suspend fun signInWithGoogle(idToken: String): Result<MeDto> = apiCall { api.google(GoogleLoginBody(idToken)) }.mapCatching {
        session.setUserToken(it.token)
        api.me()
    }

    suspend fun me(): Result<MeDto> = apiCall { api.me() }

    /** Un negocio nuevo con solo el nombre (PLAN.md 5.0) y este teléfono ya vinculado. */
    suspend fun createBusiness(name: String): Result<String> = apiCall { api.createBusiness(CreateBusinessBody(name = name.trim())) }.mapCatching {
        linkThisPhone(it.id)
        it.id
    }

    /** Vincula este teléfono al negocio (dueño/admin con Google). */
    suspend fun linkThisPhone(businessId: String): Result<Unit> = apiCall { api.selfLink(businessId, deviceInfo()) }.map {
        session.link(it.deviceToken, it.deviceId, businessId)
    }

    suspend fun requestLinkCode(): Result<LinkRequestCreatedDto> = apiCall { api.createLinkRequest(deviceInfo()) }

    /** Devuelve true cuando un dueño/admin ya reclamó el código y este teléfono quedó vinculado. */
    suspend fun pollLink(code: String, secret: String): Result<Boolean> = apiCall { api.pollLink(code, secret) }.map { s ->
        if (s.status == "CLAIMED" && s.deviceToken != null && s.deviceId != null && s.businessId != null) {
            session.link(s.deviceToken, s.deviceId, s.businessId)
            true
        } else {
            false
        }
    }

    /** Trae miembros y negocio para poder elegir persona y validar PIN aunque la primera sincronización aún no termine. */
    suspend fun loadDirectory(): Result<Unit> {
        val businessId = session.current().businessId ?: return Result.failure(IllegalStateException("sin negocio"))
        return apiCall { api.members(businessId) }.map { members -> db.directory().upsertMembers(members.map { it.toEntity() }) }
    }

    /** El dueño (que entró con Google y no tiene PIN) crea el suyo para poder entrar rápido en este teléfono. */
    suspend fun setOwnPin(pin: String, memberId: String): Result<Unit> {
        val businessId = session.current().businessId ?: return Result.failure(IllegalStateException("sin negocio"))
        return apiCall { api.setPin(businessId, memberId, PinBody(pin)) }
    }

    fun membershipFor(me: MeDto, businessId: String): MembershipDto? = me.businesses.firstOrNull { it.businessId == businessId }

    /** Valida el PIN contra el hash guardado en el teléfono: funciona sin conexión. 5 fallos bloquean con espera creciente. */
    suspend fun unlock(memberId: String, pin: String): UnlockResult {
        if (guard.isLocked()) return UnlockResult.Locked(guard.waitMillis())
        val member = db.directory().member(memberId) ?: return UnlockResult.NoPin
        val hash = member.pinHash ?: return UnlockResult.NoPin
        val ok = runCatching { BCrypt.verifyer().verify(pin.toCharArray(), hash.toCharArray()).verified }.getOrDefault(false)
        if (!ok) {
            guard.recordFailure()
            return if (guard.isLocked()) UnlockResult.Locked(guard.waitMillis()) else UnlockResult.WrongPin
        }
        guard.recordSuccess()
        session.setActiveMember(member.id, member.displayName, member.role)
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

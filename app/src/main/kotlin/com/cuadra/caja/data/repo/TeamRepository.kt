package com.cuadra.caja.data.repo

import com.cuadra.caja.data.local.CuadraDatabase
import com.cuadra.caja.data.local.MemberEntity
import com.cuadra.caja.data.remote.CreateMemberBody
import com.cuadra.caja.data.remote.CuadraApi
import com.cuadra.caja.data.sync.toEntity
import com.cuadra.caja.data.remote.DeviceDto
import com.cuadra.caja.data.remote.PinBody
import com.cuadra.caja.data.remote.SetAccessCodeBody
import com.cuadra.caja.data.remote.TeamApi
import com.cuadra.caja.data.remote.UpdateMemberBody
import com.cuadra.caja.data.remote.apiCall
import com.cuadra.caja.data.session.SessionStore
import kotlinx.coroutines.flow.Flow

/**
 * Personas, código del negocio y teléfonos del negocio. Todo cambio necesita conexión y lo decide el servidor (que además hace cumplir quién puede
 * qué). La lista de personas vive en Room: se ve sin conexión y, después de CADA cambio, el directorio se vuelve a pedir COMO TELÉFONO
 * (`AuthRepository.loadDirectory`): así el teléfono recibe el hash del PIN nuevo y lo valida sin conexión de inmediato.
 */
class TeamRepository(
    private val db: CuadraDatabase,
    private val api: TeamApi,
    private val session: SessionStore,
    private val auth: AuthRepository,
    private val businessApi: CuadraApi? = null,
) {
    /** Todas las personas guardadas en el teléfono (también las deshabilitadas). */
    fun members(): Flow<List<MemberEntity>> = db.directory().allMembers()

    /** Vuelve a pedir el directorio al servidor. Falla con `ApiFailure.Offline` sin conexión; lo que hubiera en Room sigue ahí. */
    suspend fun refresh(): Result<Unit> {
        val r = auth.loadDirectory()
        // El código del negocio puede haber cambiado desde el panel web: se vuelve a traer el negocio (si falla, queda el guardado).
        if (r.isSuccess) refreshBusiness()
        return r
    }

    private suspend fun refreshBusiness() {
        val api = businessApi ?: return
        val b = businessId() ?: return
        apiCall { api.business(b) }.onSuccess { db.directory().upsertBusiness(it.toEntity()) }
    }

    /** Renueva el código del negocio al azar (solo el dueño). Los teléfonos ya vinculados siguen; solo cambia para entradas NUEVAS. */
    suspend fun renewAccessCode(): Result<String> = online { api.renewAccessCode(it) }.map { saveCode(it.accessCode) }

    /** Elige el código del negocio (5 dígitos, sin cero al principio). Errores: `INVALID_ACCESS_CODE`, `ACCESS_CODE_TAKEN`. */
    suspend fun setAccessCode(code: String): Result<String> = online { api.setAccessCode(it, SetAccessCodeBody(code)) }.map { saveCode(it.accessCode) }

    private suspend fun saveCode(code: String): String {
        val b = businessId()
        if (b != null) db.directory().setAccessCode(b, code)
        return code
    }

    private suspend fun businessId(): String? = session.current().businessId

    private suspend inline fun <T> online(crossinline call: suspend (String) -> T): Result<T> {
        val b = businessId() ?: return Result.failure(IllegalStateException("sin negocio"))
        return apiCall { call(b) }
    }

    /** Después de un cambio exitoso se recarga el directorio; si eso falla el cambio ya está hecho en el servidor y se ve al volver a abrir. */
    private suspend fun <T> reloading(r: Result<T>): Result<T> { if (r.isSuccess) auth.loadDirectory(); return r }

    suspend fun create(name: String, role: String, pin: String, mustChange: Boolean): Result<Unit> =
        reloading(online { api.createMember(it, CreateMemberBody(name, role, pin, mustChange)) }).map { }

    /** Cambia nombre, color, rol o estado (solo lo que no es nulo). Si es la propia persona la que se renombra, la sesión también se entera. */
    suspend fun update(memberId: String, name: String?, color: String?, role: String?, status: String?): Result<Unit> =
        reloading(online { api.updateMember(it, memberId, UpdateMemberBody(name, role, status, color)) }).map {
            val s = session.current()
            if (name != null && s.memberId == memberId) session.setActiveMember(memberId, name, s.memberRole)
        }

    /**
     * Restablece el PIN de una persona (o el propio). Después el directorio se pide como TELÉFONO para recibir el hash nuevo. Si justo en ese momento
     * no hay conexión, el hash del PIN nuevo se calcula aquí, así el PIN nuevo funciona sin conexión en ESTE teléfono de inmediato (el próximo
     * directorio del servidor lo reemplaza por el suyo).
     */
    suspend fun resetPin(memberId: String, pin: String, mustChange: Boolean): Result<Unit> {
        val r = online { api.resetPin(it, memberId, PinBody(pin, mustChange)) }
        if (r.isSuccess && auth.loadDirectory().isFailure) storeLocalHash(memberId, pin, mustChange)
        return r
    }

    private suspend fun storeLocalHash(memberId: String, pin: String, mustChange: Boolean) {
        val m = db.directory().member(memberId) ?: return
        val hash = at.favre.lib.crypto.bcrypt.BCrypt.withDefaults().hashToString(10, pin.toCharArray())
        db.directory().upsertMembers(listOf(m.copy(pinHash = hash, pinSet = true, pinMustChange = mustChange)))
    }

    suspend fun devices(): Result<List<DeviceDto>> = online { api.devices(it) }
    suspend fun revokeDevice(id: String): Result<Unit> = online { api.revokeDevice(it, id) }
}

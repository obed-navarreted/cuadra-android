package com.cuadra.caja.data.repo

import androidx.room.withTransaction
import com.cuadra.caja.data.local.CuadraDatabase
import com.cuadra.caja.data.local.TemplateEntity
import com.cuadra.caja.data.remote.ActivityEntryDto
import com.cuadra.caja.data.remote.CuadraApi
import com.cuadra.caja.data.remote.PageDto
import com.cuadra.caja.data.remote.PlanDto
import com.cuadra.caja.data.remote.TemplateTextBody
import com.cuadra.caja.data.remote.TicketBody
import com.cuadra.caja.data.remote.TicketCreatedDto
import com.cuadra.caja.data.remote.apiCall
import com.cuadra.caja.data.session.SessionStore
import com.cuadra.caja.data.sync.toEntity

/** Plan, actividad, plantillas de mensajes y soporte: lecturas y escrituras de gestión que solo existen en el servidor (con conexión). */
class PlanRepository(private val api: CuadraApi, private val session: SessionStore) {
    private suspend fun <T> call(block: suspend (String) -> T): Result<T> {
        val b = session.current().businessId ?: return Result.failure(IllegalStateException("no business"))
        return apiCall { block(b) }
    }

    /** Lo último que dijo el servidor (en memoria): se muestra, con un aviso, si al volver a abrir la pantalla no hay conexión. */
    @Volatile var cached: PlanDto? = null
        private set

    suspend fun plan(): Result<PlanDto> = call { api.plan(it) }.onSuccess { cached = it }

    /** Una página del registro de actividad (solo el dueño; cualquier otro recibe 403). */
    suspend fun activity(page: Int, size: Int = PAGE): Result<PageDto<ActivityEntryDto>> = call { api.activity(it, page, size) }

    companion object { const val PAGE = 30 }
}

/** «Escribir a soporte»: `POST /support/tickets` con la sesión que tenga el teléfono (persona con PIN, dispositivo o cuenta de Google). */
class SupportRepository(private val api: CuadraApi) {
    suspend fun send(body: TicketBody): Result<TicketCreatedDto> = apiCall { api.createTicket(body) }
}

/**
 * Plantillas de los mensajes de WhatsApp. Lo que se guarda o se restaura se refleja también en Room (`message_templates`), que es de donde la hoja de compartir
 * lee el texto: el mensaje que sale por WhatsApp ya usa la plantilla editada sin esperar a la sincronización.
 */
class TemplateRepository(private val db: CuadraDatabase, private val api: CuadraApi, private val session: SessionStore) {
    private suspend fun <T> call(block: suspend (String) -> T): Result<T> {
        val b = session.current().businessId ?: return Result.failure(IllegalStateException("no business"))
        return apiCall { block(b) }
    }

    fun all() = db.templates().all()

    /** Pide las plantillas del servidor y deja Room igual (también borra las que ya no existen allá). */
    suspend fun refresh(): Result<Unit> = call { api.messageTemplates(it) }.map { remote ->
        db.withTransaction {
            val keep = remote.map { it.kind to it.locale }.toSet()
            db.templates().allNow().filter { (it.kind to it.locale) !in keep }.forEach { db.templates().delete(it.kind, it.locale) }
            remote.forEach { db.templates().upsert(it.toEntity()) }
        }
    }

    suspend fun save(kind: String, locale: String, body: String): Result<TemplateEntity> =
        call { api.saveMessageTemplate(it, kind, locale, TemplateTextBody(body)) }.map { dto -> dto.toEntity().also { db.templates().upsert(it) } }

    suspend fun reset(kind: String, locale: String): Result<Unit> = call { api.resetMessageTemplate(it, kind, locale) }.map { db.templates().delete(kind, locale) }
}

package com.cuadra.caja.data.repo

import com.cuadra.caja.data.local.Db
import com.cuadra.caja.data.local.CustomerEntity
import com.cuadra.caja.data.local.OutboxEntity
import com.cuadra.caja.data.remote.CustomerInputDto
import com.cuadra.caja.domain.PhoneNumbers
import com.cuadra.caja.domain.PhoneResult
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

sealed interface SaveCustomer {
    data class Saved(val customer: CustomerEntity) : SaveCustomer
    data object InvalidPhone : SaveCustomer
    data object InvalidName : SaveCustomer
}

class CustomerRepository(
    private val db: Db,
    private val requestSync: () -> Unit,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val json = Json { encodeDefaults = true; explicitNulls = false }

    fun search(query: String): Flow<List<CustomerEntity>> = db.customers().search(query.trim(), query.filter { it.isDigit() })
    fun observe(id: String): Flow<CustomerEntity?> = db.customers().observe(id)
    suspend fun get(id: String) = db.customers().get(id)

    /** Nombres ya usados en fiados anteriores, para completar mientras se escribe. */
    suspend fun nameSuggestions(query: String): List<String> = if (query.isBlank()) emptyList() else db.customers().labelSuggestions(query.trim())

    /**
     * Crea o edita un cliente: el teléfono se normaliza (formato internacional) con el país del negocio. Quedan guardados en el teléfono y
     * en la cola de salida en la misma transacción.
     */
    suspend fun save(id: String?, name: String, phone: String?, notes: String?, creditLimitMinor: Long?, country: String?, archived: Boolean = false): SaveCustomer {
        if (name.isBlank()) return SaveCustomer.InvalidName
        val normalized = when (val r = PhoneNumbers.normalize(phone, country)) {
            PhoneResult.None -> null
            is PhoneResult.Valid -> r.digits
            PhoneResult.Invalid -> return SaveCustomer.InvalidPhone
        }
        val customerId = id ?: UUID.randomUUID().toString()
        val existing = db.customers().get(customerId)
        val entity = CustomerEntity(
            id = customerId, name = name.trim(), phone = normalized, notes = notes?.trim()?.ifEmpty { null }, creditLimitMinor = creditLimitMinor,
            lastReminderAt = existing?.lastReminderAt, archived = archived, balanceMinor = existing?.balanceMinor ?: 0, oldestOpenAt = existing?.oldestOpenAt, rev = existing?.rev ?: 0,
        )
        val input = CustomerInputDto(entity.name, normalized, entity.notes, creditLimitMinor, archived)
        db.inTransaction {
            db.customers().upsert(entity)
            db.outbox().insert(OutboxEntity(opId = UUID.randomUUID().toString(), kind = "CUSTOMER_UPSERT", entityId = customerId, payload = json.encodeToString(input), createdAt = now()))
        }
        requestSync()
        return SaveCustomer.Saved(entity)
    }
}

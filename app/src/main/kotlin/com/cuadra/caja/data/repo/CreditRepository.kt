package com.cuadra.caja.data.repo

import androidx.room.withTransaction
import com.cuadra.caja.data.local.CreditEntity
import com.cuadra.caja.data.local.CreditItem
import com.cuadra.caja.data.local.CreditPaymentEntity
import com.cuadra.caja.data.local.CreditTotals
import com.cuadra.caja.data.local.CuadraDatabase
import com.cuadra.caja.data.local.OutboxEntity
import com.cuadra.caja.data.remote.EventInputDto
import com.cuadra.caja.data.remote.LinkCustomerBody
import com.cuadra.caja.data.remote.ManualCreditInputDto
import com.cuadra.caja.data.remote.PayInputDto
import com.cuadra.caja.data.remote.ReasonBody
import com.cuadra.caja.data.session.SessionStore
import com.cuadra.caja.domain.CreditAllocation
import com.cuadra.caja.domain.OpenCredit
import com.cuadra.caja.domain.PhoneNumbers
import com.cuadra.caja.domain.PhoneResult
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Lo que se necesita para armar el mensaje de "abono recibido" o "deuda saldada". */
data class PaymentReceipt(
    val paymentId: String, val amountMinor: Long, val method: String, val creditId: String?, val customerId: String?, val debtorLabel: String,
    val phone: String?, val creditBalanceMinor: Long, val customerBalanceMinor: Long?, val paidOff: Boolean,
)

sealed interface PayResult {
    data class Paid(val receipt: PaymentReceipt) : PayResult
    data object NothingOwed : PayResult
    data object NotFound : PayResult
}

sealed interface ManualResult {
    data class Created(val credit: CreditEntity) : ManualResult
    data object InvalidPhone : ManualResult
    data object DebtorRequired : ManualResult
}

/**
 * La libreta digital, con la misma regla que el servidor: saldo de un fiado = monto − abonos vigentes; saldo del cliente = suma de sus fiados.
 * Todo se guarda en el teléfono y en la cola de salida en una sola transacción; cobrar un abono nunca espera a la red.
 */
class CreditRepository(
    private val db: CuadraDatabase,
    private val session: SessionStore,
    private val requestSync: () -> Unit,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val json = Json { encodeDefaults = true; explicitNulls = false }

    fun list(status: String, linked: String, minDays: Int?, query: String): Flow<List<CreditItem>> {
        val olderThan = if (minDays == null) Long.MAX_VALUE else now() - minDays * DAY
        return db.credits().list(status, linked, olderThan, query.trim(), query.filter { it.isDigit() })
    }

    fun totals(overdueDays: Int): Flow<CreditTotals> = db.credits().totals(now() - overdueDays * DAY)
    fun forCustomer(customerId: String): Flow<List<CreditItem>> = db.credits().forCustomer(customerId)
    fun paymentsForCustomer(customerId: String): Flow<List<CreditPaymentEntity>> = db.credits().paymentsForCustomer(customerId)
    suspend fun get(id: String) = db.credits().get(id)

    /** "Fiado sin venta": una deuda que no nació de una venta (un préstamo, o lo que había en el cuaderno). */
    suspend fun addManual(label: String, phone: String?, customerId: String?, amountMinor: Long, note: String?, country: String?): ManualResult {
        val customer = customerId?.let { db.customers().get(it) }
        val name = label.trim().ifEmpty { customer?.name.orEmpty() }
        if (name.isEmpty()) return ManualResult.DebtorRequired
        val normalized = when (val r = PhoneNumbers.normalize(phone, country)) {
            PhoneResult.None -> customer?.phone
            is PhoneResult.Valid -> r.digits
            PhoneResult.Invalid -> return ManualResult.InvalidPhone
        }
        val id = UUID.randomUUID().toString()
        val time = now()
        val entity = CreditEntity(id, null, customerId, name, normalized, amountMinor, amountMinor, "OPEN", null, note?.trim()?.ifEmpty { null }, time, session.current().memberName, null, 0)
        val input = ManualCreditInputDto(name, normalized, customerId, amountMinor, entity.note, createdAt = Instant.ofEpochMilli(time).toString())
        db.withTransaction {
            db.credits().upsert(entity)
            customerId?.let { db.customers().recompute(listOf(it)) }
            db.outbox().insert(OutboxEntity(opId = UUID.randomUUID().toString(), kind = "CREDIT_UPSERT", entityId = id, payload = json.encodeToString(input), createdAt = time))
        }
        requestSync()
        return ManualResult.Created(entity)
    }

    /** Abona a un fiado concreto. */
    suspend fun payCredit(creditId: String, amountMinor: Long, method: String, reference: String?): PayResult {
        val credit = db.credits().get(creditId) ?: return PayResult.NotFound
        val id = UUID.randomUUID().toString()
        val time = now()
        val payment = payment(id, credit.id, credit.customerId, null, amountMinor, method, reference?.trim()?.ifEmpty { null }, time)
        db.withTransaction {
            db.credits().upsertPayments(listOf(payment))
            recompute(listOf(credit.id))
            db.outbox().insert(op("CREDIT_PAYMENT", id, PayInputDto(creditId = creditId, amountMinor = amountMinor, method = method, reference = payment.reference, occurredAt = iso(time))))
        }
        requestSync()
        return PayResult.Paid(receipt(id, amountMinor, method, credit.id, credit.customerId))
    }

    /** Abona al cliente: se reparte del fiado más viejo al más nuevo (igual que el servidor) y queda como un solo abono anulable. */
    suspend fun payCustomer(customerId: String, amountMinor: Long, method: String, reference: String?): PayResult {
        val customer = db.customers().get(customerId) ?: return PayResult.NotFound
        val open = db.credits().openForCustomer(customerId)
        val allocation = CreditAllocation.fifo(open.map { OpenCredit(it.id, it.createdAt, it.balanceMinor) }, amountMinor)
        if (allocation.isEmpty()) return PayResult.NothingOwed
        val group = UUID.randomUUID().toString()
        val time = now()
        val ref = reference?.trim()?.ifEmpty { null }
        val payments = allocation.map { a -> payment(childId(group, a.creditId), a.creditId, customerId, group, a.amountMinor, method, ref, time) }
        db.withTransaction {
            db.credits().upsertPayments(payments)
            recompute(allocation.map { it.creditId })
            db.outbox().insert(op("CREDIT_PAYMENT", group, PayInputDto(customerId = customerId, amountMinor = amountMinor, method = method, reference = ref, occurredAt = iso(time))))
        }
        requestSync()
        val paidOff = (db.customers().get(customerId)?.balanceMinor ?: 0) == 0L
        return PayResult.Paid(PaymentReceipt(group, amountMinor, method, null, customerId, customer.name, customer.phone, 0, db.customers().get(customerId)?.balanceMinor, paidOff))
    }

    suspend fun link(creditId: String, customerId: String) {
        val credit = db.credits().get(creditId) ?: return
        val customer = db.customers().get(customerId) ?: return
        db.withTransaction {
            db.credits().link(creditId, customerId, customer.phone)
            db.credits().linkPayments(creditId, customerId)
            db.customers().recompute(listOfNotNull(credit.customerId, customerId))
            db.outbox().insert(op("CREDIT_LINK", creditId, LinkCustomerBody(customerId)))
        }
        requestSync()
    }

    /** Condonar: cierra la deuda sin cobrarla. Solo dueño/admin (el servidor lo exige aunque la app no lo ofrezca). */
    suspend fun writeOff(creditId: String, reason: String) {
        val credit = db.credits().get(creditId) ?: return
        db.withTransaction {
            db.credits().upsert(credit.copy(status = "WRITTEN_OFF", balanceMinor = 0))
            credit.customerId?.let { db.customers().recompute(listOf(it)) }
            db.outbox().insert(op("CREDIT_WRITE_OFF", creditId, ReasonBody(reason)))
        }
        requestSync()
    }

    /** Anula un abono (o un abono repartido completo) y devuelve el saldo. Solo dueño/admin. */
    suspend fun voidPayment(paymentId: String, reason: String?) {
        val targets = db.credits().paymentsByIdOrGroup(paymentId)
        if (targets.isEmpty()) return
        db.withTransaction {
            db.credits().voidPayments(targets.map { it.id }, reason)
            recompute(targets.map { it.creditId }.distinct())
            db.outbox().insert(op("CREDIT_PAYMENT_VOID", paymentId, ReasonBody(reason)))
        }
        requestSync()
    }

    /** Se abrió WhatsApp (recordatorio, comprobante, estado de cuenta). No se puede saber si la persona llegó a enviarlo. */
    suspend fun recordEvent(creditId: String?, customerId: String?, kind: String, format: String) {
        if (creditId == null && customerId == null) return    // un fiado de solo nota sin id aún: no hay a qué asociar el registro
        val time = now()
        db.withTransaction {
            if (kind == "REMINDER_OPENED") {
                creditId?.let { db.credits().markReminder(it, time) }
                customerId?.let { db.customers().get(it)?.let { c -> db.customers().upsert(c.copy(lastReminderAt = time)) } }
            }
            db.outbox().insert(op("CREDIT_EVENT", UUID.randomUUID().toString(), EventInputDto(creditId, customerId, kind, format)))
        }
        requestSync()
    }

    // ---------- internos ----------

    private suspend fun recompute(creditIds: List<String>) {
        db.credits().recompute(creditIds)
        val customers = db.credits().customersOf(creditIds)
        if (customers.isNotEmpty()) db.customers().recompute(customers)
    }

    private suspend fun receipt(paymentId: String, amountMinor: Long, method: String, creditId: String, customerId: String?): PaymentReceipt {
        val credit = db.credits().get(creditId)!!
        val customerBalance = customerId?.let { db.customers().get(it)?.balanceMinor }
        return PaymentReceipt(paymentId, amountMinor, method, creditId, customerId, credit.debtorLabel, credit.debtorPhone, credit.balanceMinor, customerBalance, credit.balanceMinor == 0L)
    }

    private suspend fun payment(id: String, creditId: String, customerId: String?, group: String?, amount: Long, method: String, reference: String?, time: Long) =
        CreditPaymentEntity(id, creditId, customerId, group, amount, method, reference, session.current().memberName, time, false, null, 0)

    private inline fun <reified T> op(kind: String, entityId: String, payload: T) =
        OutboxEntity(opId = UUID.randomUUID().toString(), kind = kind, entityId = entityId, payload = json.encodeToString(payload), createdAt = now())

    private fun iso(time: Long) = Instant.ofEpochMilli(time).toString()

    companion object {
        private const val DAY = 24L * 60 * 60 * 1000

        /** Mismo id derivado que calcula el servidor para cada parte de un abono repartido (así ambos lados coinciden sin hablarse). */
        fun childId(group: String, creditId: String): String = UUID.nameUUIDFromBytes("pay:$group:$creditId".toByteArray(StandardCharsets.UTF_8)).toString()

        /** Id del fiado que nace del pago a fiado de una venta: el mismo que calcula el servidor. */
        fun saleCreditId(saleId: String, paymentId: String): String = UUID.nameUUIDFromBytes("credit:$saleId:$paymentId".toByteArray(StandardCharsets.UTF_8)).toString()
    }
}

package com.cuadra.caja.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * «Requiere atención»: cada operación que el servidor rechazó (o aplicó con algo que revisar) explicada en lenguaje llano: qué era, de cuánto, cuándo,
 * quién la hizo y por qué. La pantalla solo traduce estos valores a texto; aquí no hay recursos de Android (se prueba sin teléfono).
 */
object Attention {
    /** Qué era la operación. */
    enum class What { SALE, SALE_CANCEL, SALE_RETURN, CREDIT_PAYMENT, CREDIT, EXPENSE, WITHDRAWAL, DEPOSIT, PRODUCT, CUSTOMER, OTHER }

    /** Por qué requiere atención (a partir del código estable del servidor). */
    enum class Reason {
        /** La cuenta ya se cobró o descartó en otro teléfono: esta venta se guardó APARTE para revisar (no se perdió). */
        CONFLICT_COPY,
        /** La cuenta ya se cobró o descartó en otro teléfono con el mismo contenido. */
        ALREADY_CLOSED_ELSEWHERE,
        CREDIT_LIMIT, CREDIT_CLOSED, NO_OPEN_CREDITS, MEMBER_DISABLED, DEVICE_NOT_TRUSTED, FORBIDDEN, PAYMENT_MISMATCH, CUSTOMER_REQUIRED,
        CREDIT_HAS_PAYMENTS, NOT_FOUND, CODE_IN_USE,
        /** «Anular esta venta» llegó tarde (pasaron los 5 minutos) o ya no era la última: la venta sigue cobrada; el dueño o un admin pueden eliminarla. */
        UNDO_NOT_ALLOWED,
        /** La devolución no se pudo aplicar (se devolvió de más, el fiado ya no alcanzaba o no había permiso). */
        RETURN_REJECTED,
        /** Hecha por alguien dado de baja y no es creíble (muy vieja, antes de vincular el teléfono o ya se había enviado todo). */
        LATE_OP_REJECTED,
        OTHER,
    }

    data class Item(
        val seq: Long, val what: What, val amountMinor: Long?, val name: String?, val at: Long, val byName: String?, val reason: Reason, val code: String,
        /** Para CREDIT_LIMIT: límite, lo que ya debía y el nombre del cliente. */
        val limitMinor: Long? = null, val balanceMinor: Long? = null, val customerName: String? = null,
        /** Rechazada (se puede reintentar) o aplicada con algo que revisar (solo se quita de la lista). */
        val review: Boolean = false,
    ) {
        val canRetry: Boolean get() = !review
    }

    private val json = Json { ignoreUnknownKeys = true }

    fun reasonOf(code: String): Reason = when (code) {
        "SALE_CONFLICT_COPY" -> Reason.CONFLICT_COPY
        "SALE_STALE" -> Reason.ALREADY_CLOSED_ELSEWHERE
        "CREDIT_LIMIT_EXCEEDED" -> Reason.CREDIT_LIMIT
        "CREDIT_CLOSED" -> Reason.CREDIT_CLOSED
        "NO_OPEN_CREDITS" -> Reason.NO_OPEN_CREDITS
        "MEMBER_NOT_ACTIVE", "ACCESS_DISABLED" -> Reason.MEMBER_DISABLED
        "DEVICE_NOT_TRUSTED", "MEMBER_MISMATCH" -> Reason.DEVICE_NOT_TRUSTED
        "FORBIDDEN" -> Reason.FORBIDDEN
        "PAYMENT_MISMATCH", "PAYMENT_REQUIRED", "TENDERED_TOO_LOW" -> Reason.PAYMENT_MISMATCH
        "CUSTOMER_REQUIRED", "DEBTOR_REQUIRED" -> Reason.CUSTOMER_REQUIRED
        "CREDIT_HAS_PAYMENTS", "CREDIT_PAID_EXCEEDS" -> Reason.CREDIT_HAS_PAYMENTS
        "PRODUCT_NOT_FOUND", "SALE_NOT_FOUND", "CREDIT_NOT_FOUND", "PAYMENT_NOT_FOUND", "INVALID_CUSTOMER", "INVALID_CATEGORY" -> Reason.NOT_FOUND
        "BARCODE_IN_USE", "SHORT_CODE_IN_USE", "ID_TAKEN" -> Reason.CODE_IN_USE
        "UNDO_NOT_ALLOWED" -> Reason.UNDO_NOT_ALLOWED
        "RETURN_EXCEEDS_SOLD", "CREDIT_NOTE_EXCEEDS", "NO_CREDIT_TO_REDUCE", "RETURN_NOT_ALLOWED", "SALE_HAS_RETURNS", "SALE_NOT_COMPLETED", "EMPTY_RETURN" -> Reason.RETURN_REJECTED
        "LATE_OP_REJECTED" -> Reason.LATE_OP_REJECTED
        else -> Reason.OTHER
    }

    /**
     * Arma el renglón. `saleTotal`: el total de la venta en el teléfono (si aún está); `memberName`: nombre de quien la hizo (por su id guardado en la fila).
     */
    fun describe(
        seq: Long, kind: String, payload: String, createdAt: Long, state: String, code: String?, detail: String?,
        memberName: String?, saleTotal: Long?,
    ): Item {
        val p = runCatching { json.parseToJsonElement(payload) as? JsonObject }.getOrNull()
        val d = detail?.let { runCatching { json.parseToJsonElement(it) as? JsonObject }.getOrNull() }
        fun long(o: JsonObject?, k: String) = (o?.get(k) as? JsonPrimitive)?.longOrNull
        fun str(o: JsonObject?, k: String) = (o?.get(k) as? JsonPrimitive)?.takeIf { it.isString }?.content
        val (what, amount, name) = when (kind) {
            "SALE_UPSERT" -> Triple(What.SALE, saleTotal ?: paymentsTotal(p), null)
            "SALE_CANCEL" -> Triple(What.SALE_CANCEL, saleTotal, null)
            "SALE_RETURN" -> Triple(What.SALE_RETURN, null, null)
            "CREDIT_PAYMENT" -> Triple(What.CREDIT_PAYMENT, long(p, "amountMinor"), null)
            "CREDIT_UPSERT" -> Triple(What.CREDIT, long(p, "amountMinor"), str(p, "debtorLabel"))
            "EXPENSE_UPSERT" -> Triple(What.EXPENSE, long(p, "amountMinor"), str(p, "description"))
            "CASH_MOVEMENT_UPSERT" -> Triple(if (str(p, "kind") == "WITHDRAWAL") What.WITHDRAWAL else What.DEPOSIT, long(p, "amountMinor"), null)
            "PRODUCT_UPSERT" -> Triple(What.PRODUCT, null, str(p, "name"))
            "PRODUCT_PATCH" -> Triple(What.PRODUCT, null, str(p?.get("set") as? JsonObject, "name"))
            "CUSTOMER_UPSERT" -> Triple(What.CUSTOMER, null, str(p, "name"))
            else -> Triple(What.OTHER, null, null)
        }
        val c = code ?: "REJECTED"
        return Item(
            seq = seq, what = what, amountMinor = amount, name = name, at = createdAt, byName = memberName, reason = reasonOf(c), code = c,
            limitMinor = long(d, "limitMinor"), balanceMinor = long(d, "balanceMinor"), customerName = str(d, "customerName"),
            review = state == "REVIEW",
        )
    }

    /** Total de una venta a partir de lo que se envió: la suma de sus pagos (una cobrada) o de sus líneas (una apartada). */
    private fun paymentsTotal(p: JsonObject?): Long? {
        val pays = p?.get("payments") as? JsonArray
        if (!pays.isNullOrEmpty()) return pays.sumOf { ((it as? JsonObject)?.get("amountMinor") as? JsonPrimitive)?.longOrNull ?: 0L }
        val items = p?.get("items") as? JsonArray ?: return null
        return items.sumOf {
            val o = it as? JsonObject ?: return@sumOf 0L
            val price = (o["unitPriceMinor"] as? JsonPrimitive)?.longOrNull ?: 0L
            val qty = (o["quantityMilli"] as? JsonPrimitive)?.longOrNull ?: 0L
            val disc = (o["discountMinor"] as? JsonPrimitive)?.longOrNull ?: 0L
            SaleMath.lineTotal(price, qty, disc)
        } - ((p["discountMinor"] as? JsonPrimitive)?.longOrNull ?: 0L)
    }
}

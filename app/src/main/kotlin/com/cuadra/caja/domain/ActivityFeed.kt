package com.cuadra.caja.domain

import com.cuadra.caja.data.remote.ActivityEntryDto

/** Filtro por tipo de la lista de actividad. */
enum class ActivityKind { ALL, SALES, PRODUCTS, TEAM, BUSINESS, PLATFORM }

/** Acciones conocidas del registro (`audit_log.action`); cualquier otra se muestra como «Otra acción» con su código, sin inventar una frase. */
enum class ActivityAction(val code: String, val kind: ActivityKind) {
    SALE_COMPLETE("sale.complete", ActivityKind.SALES), SALE_EDIT("sale.edit", ActivityKind.SALES), SALE_CANCEL("sale.cancel", ActivityKind.SALES),
    CREDIT_CREATE("credit.create", ActivityKind.SALES), CREDIT_FROM_SALE("credit.from_sale", ActivityKind.SALES), CREDIT_PAYMENT("credit.payment", ActivityKind.SALES),
    CREDIT_PAYMENT_VOID("credit.payment_void", ActivityKind.SALES), CREDIT_WRITE_OFF("credit.write_off", ActivityKind.SALES),
    CUSTOMER_CREATE("customer.create", ActivityKind.SALES), CUSTOMER_UPDATE("customer.update", ActivityKind.SALES),
    EXPENSE_CREATE("expense.create", ActivityKind.SALES), EXPENSE_VOID("expense.void", ActivityKind.SALES),
    PRODUCT_CREATE("product.create", ActivityKind.PRODUCTS), PRODUCT_UPDATE("product.update", ActivityKind.PRODUCTS), PRODUCT_DEACTIVATE("product.deactivate", ActivityKind.PRODUCTS),
    PRODUCT_IMPORT("product.import", ActivityKind.PRODUCTS), PURCHASE_CREATE("purchase.create", ActivityKind.PRODUCTS), PURCHASE_VOID("purchase.void", ActivityKind.PRODUCTS),
    MEMBER_CREATE("member.create", ActivityKind.TEAM), MEMBER_UPDATE("member.update", ActivityKind.TEAM), MEMBER_PIN_RESET("member.pin_reset", ActivityKind.TEAM),
    INVITATION_CREATE("invitation.create", ActivityKind.TEAM), INVITATION_REVOKE("invitation.revoke", ActivityKind.TEAM), INVITATION_ACCEPT("invitation.accept", ActivityKind.TEAM),
    DEVICE_CLAIM("device.claim", ActivityKind.TEAM), DEVICE_SELF_LINK("device.self_link", ActivityKind.TEAM), DEVICE_REVOKE("device.revoke", ActivityKind.TEAM), DEVICE_PIN_VERIFIED("device.pin_verified", ActivityKind.TEAM),
    OWNER_TRANSFER("owner.transfer", ActivityKind.TEAM),
    BUSINESS_CREATE("business.create", ActivityKind.BUSINESS), BUSINESS_UPDATE("business.update", ActivityKind.BUSINESS), BUSINESS_DELETE_REQUESTED("business.delete_requested", ActivityKind.BUSINESS),
    PLATFORM_VIEW_AS("platform.view_as", ActivityKind.PLATFORM), PLATFORM_PLAN_CHANGED("platform.plan_changed", ActivityKind.PLATFORM),
    PLATFORM_TRIAL_EXTENDED("platform.trial_extended", ActivityKind.PLATFORM), PLATFORM_SUSPENDED("platform.suspended", ActivityKind.PLATFORM),
    PLATFORM_UNSUSPENDED("platform.unsuspended", ActivityKind.PLATFORM), PLATFORM_FLAG("platform.flag", ActivityKind.PLATFORM),
    PLATFORM_DELETION_MARKED("platform.deletion_marked", ActivityKind.PLATFORM);

    companion object {
        fun of(code: String): ActivityAction? = entries.firstOrNull { it.code == code }
    }
}

/**
 * Una fila de la lista lista para mostrar: qué pasó (`action`, o `null` si es desconocida), sobre qué (`subject`: nombre de un producto o cliente), cuánto (`amountMinor`),
 * el motivo aparte y los campos que cambió un negocio o producto. La pantalla arma la frase con textos traducidos.
 */
data class ActivityRow(
    val id: Long, val kind: ActivityKind, val action: ActivityAction?, val rawAction: String, val platform: Boolean, val actorName: String?, val at: String,
    val subject: String?, val amountMinor: Long?, val reason: String?, val fields: List<String>,
)

object ActivityFeed {
    /** Igual que el panel web: el servidor guarda el motivo dentro del detalle («… — motivo: texto») y se separa para mostrarlo aparte. */
    fun splitReason(detail: String?): Pair<String?, String?> {
        if (detail.isNullOrBlank()) return null to null
        val m = Regex("^(?:(.*?)\\s+—\\s+)?motivo:\\s*(.*)$", RegexOption.DOT_MATCHES_ALL).find(detail) ?: return detail to null
        return m.groupValues[1].ifBlank { null } to m.groupValues[2].trim().ifBlank { null }
    }

    private val TOTAL = Regex("(?:total|amount|qty|float)=(-?\\d+)")
    private val NAME = Regex("\"name\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")
    private val CHANGED = Regex("\"(name|variant|barcode|shortCode|categoryId|unit|pricing|priceMinor|costMinor|isQuick|quickPosition|color|trackStock|minStockMilli|active)\"\\s*:\\s*\\{\\s*\"from\"")

    fun kindOf(action: String, byPlatform: Boolean): ActivityKind = when {
        byPlatform || action.startsWith("platform.") -> ActivityKind.PLATFORM
        else -> ActivityAction.of(action)?.kind ?: when (action.substringBefore('.')) {
            "sale", "credit", "customer", "expense", "cash_movement", "shift" -> ActivityKind.SALES
            "product", "stock", "purchase", "supplier" -> ActivityKind.PRODUCTS
            "member", "device", "invitation", "owner" -> ActivityKind.TEAM
            else -> ActivityKind.BUSINESS
        }
    }

    fun row(e: ActivityEntryDto): ActivityRow {
        val action = ActivityAction.of(e.action)
        val platform = e.byPlatform || e.action.startsWith("platform.")
        val (note, reason) = splitReason(e.detail)
        val detail = note ?: e.detail
        val isJson = detail?.trimStart()?.startsWith("{") == true
        val subject = when {
            isJson -> NAME.find(detail!!)?.groupValues?.get(1)?.replace("\\\"", "\"")?.replace("\\\\", "\\")
            action == ActivityAction.CUSTOMER_CREATE || action == ActivityAction.CUSTOMER_UPDATE || action == ActivityAction.BUSINESS_CREATE -> detail
            else -> null
        }
        // Un motivo con su propia etiqueta («… — motivo: x») o, en anulaciones, el detalle completo (`sale.cancel`: «COMPLETED: cobrada dos veces»).
        val implicitReason = when (action) {
            ActivityAction.EXPENSE_VOID, ActivityAction.CREDIT_PAYMENT_VOID, ActivityAction.CREDIT_WRITE_OFF, ActivityAction.PURCHASE_VOID -> detail?.takeIf { it.isNotBlank() }
            ActivityAction.SALE_CANCEL -> detail?.substringAfter(": ", "")?.ifBlank { null }
            else -> null
        }
        return ActivityRow(
            id = e.id, kind = kindOf(e.action, e.byPlatform), action = action, rawAction = e.action, platform = platform, actorName = e.actorName?.ifBlank { null }, at = e.at,
            subject = subject, amountMinor = if (isJson) null else detail?.let { TOTAL.find(it)?.groupValues?.get(1)?.toLongOrNull() },
            reason = reason ?: implicitReason,
            fields = if (action == ActivityAction.PRODUCT_UPDATE || action == ActivityAction.BUSINESS_UPDATE) fieldsOf(e.detail) else emptyList(),
        )
    }

    /** Los campos que cambió una edición: en un producto vienen dentro del JSON; en el negocio, separados por comas. */
    fun fieldsOf(detail: String?): List<String> {
        if (detail.isNullOrBlank()) return emptyList()
        if (detail.trimStart().startsWith("{")) return CHANGED.findAll(detail).map { it.groupValues[1] }.distinct().toList()
        return detail.split(',').map { it.trim() }.filter { it.isNotEmpty() && it.all { c -> c.isLetterOrDigit() || c == '_' } }.distinct()
    }

    fun filter(rows: List<ActivityRow>, kind: ActivityKind): List<ActivityRow> = if (kind == ActivityKind.ALL) rows else rows.filter { it.kind == kind }
}

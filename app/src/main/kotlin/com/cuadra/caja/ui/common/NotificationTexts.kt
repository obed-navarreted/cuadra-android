package com.cuadra.caja.ui.common

import android.content.res.Resources
import com.cuadra.caja.R
import com.cuadra.caja.data.local.NotificationEntity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/**
 * El texto de un aviso se arma AQUÍ, con el idioma del teléfono y el dinero del negocio, a partir de `type` y `args`. El título y el mensaje que
 * manda el servidor solo se usan si el tipo es desconocido (una versión más nueva del servidor).
 */
object NotificationTexts {
    fun render(res: Resources, n: NotificationEntity, money: (Long) -> String): Pair<String, String> {
        val a = args(n.argsJson)
        fun s(key: String) = (a[key] as? JsonPrimitive)?.content.orEmpty()
        fun l(key: String) = (a[key] as? JsonPrimitive)?.content?.toDoubleOrNull()?.toLong() ?: 0L
        val fallback = (n.title.orEmpty()) to (n.body.orEmpty())
        return when (n.type) {
            "LOW_STOCK" -> res.getString(R.string.notif_low_stock_title, s("productName")) to res.getString(R.string.notif_low_stock_body, quantity(res, a).ifEmpty { s("stock") })
            "OUT_OF_STOCK" -> res.getString(R.string.notif_out_of_stock_title, s("productName")) to res.getString(R.string.notif_out_of_stock_body)
            "SHIFT_CLOSED" -> {
                val diff = l("differenceMinor")
                val result = when {
                    diff == 0L -> res.getString(R.string.result_balanced)
                    diff < 0 -> res.getString(R.string.result_short, money(-diff))
                    else -> res.getString(R.string.result_over, money(diff))
                }
                res.getString(R.string.notif_shift_closed_title) to res.getString(R.string.notif_shift_closed_body, s("memberName"), result)
            }
            "SHIFT_DIFFERENCE" -> res.getString(R.string.notif_shift_difference_title) to res.getString(R.string.notif_shift_difference_body, s("memberName"), money(kotlin.math.abs(l("differenceMinor"))))
            "SHIFT_NOT_CLOSED" -> res.getString(R.string.notif_shift_not_closed_title) to res.getString(R.string.notif_shift_not_closed_body, s("memberName"))
            "SALE_DELETED" -> res.getString(R.string.notif_sale_deleted_title) to res.getString(R.string.notif_sale_deleted_body, s("memberName"), money(l("totalMinor")))
            "SALE_CONFLICT" -> res.getString(R.string.notif_sale_conflict_title) to res.getString(R.string.notif_sale_conflict_body, money(l("totalMinor")), s("memberName"))
            "SALE_RETURNED" -> res.getString(R.string.notif_sale_returned_title) to res.getString(R.string.notif_sale_returned_body, s("memberName"), money(l("totalMinor")), s("reason"))
            "SALE_UNDONE" -> res.getString(R.string.notif_sale_undone_title) to res.getString(R.string.notif_sale_undone_body, s("memberName"), money(l("totalMinor")), s("reason"))
            "LATE_AFTER_DISABLE" -> res.getString(R.string.notif_late_after_disable_title) to res.getString(R.string.notif_late_after_disable_body, s("count"), s("memberName"), money(l("amountMinor")))
            // Cambio de precio de un cajero: antes y después; si solo cambió el costo, se dice así.
            "PRICE_CHANGED" -> if (a["toPriceMinor"] != null) {
                res.getString(R.string.notif_price_changed_title, s("productName")) to res.getString(R.string.notif_price_changed_body, s("memberName"), money(l("fromPriceMinor")), money(l("toPriceMinor")))
            } else {
                res.getString(R.string.notif_cost_changed_title, s("productName")) to res.getString(R.string.notif_cost_changed_body, s("memberName"))
            }
            "DEVICE_STALE" -> res.getString(R.string.notif_device_stale_title) to res.getString(R.string.notif_device_stale_body, s("deviceName"), s("pending"))
            "PIN_LOCKOUT" -> res.getString(R.string.notif_pin_lockout_title) to res.getString(R.string.notif_pin_lockout_body, s("memberName"))
            "MEMBER_JOINED" -> res.getString(R.string.notif_member_joined_title) to res.getString(R.string.notif_member_joined_body, s("memberName"))
            "DAILY_SUMMARY" -> res.getString(R.string.notif_daily_summary_title) to res.getString(R.string.notif_daily_summary_body, s("salesCount"), money(l("totalMinor")), money(l("expensesMinor")))
            // Un aviso programado lleva el texto que escribió quien lo programó.
            "SCHEDULED" -> (s("title").ifEmpty { fallback.first }) to (s("body").ifEmpty { fallback.second })
            // Aviso de la plataforma (anuncio): el texto lo escribió el equipo de Cuadra; se usa tal cual, con el guardado como respaldo.
            "PLATFORM_ANNOUNCEMENT" -> (s("title").ifEmpty { fallback.first }) to (s("body").ifEmpty { fallback.second })
            else -> fallback
        }
    }

    /** "13 lb" con la unidad en el idioma del teléfono; vacío si el aviso no trae los datos (versión vieja del servidor). */
    private fun quantity(res: Resources, a: JsonObject): String {
        val milli = (a["stockMilli"] as? JsonPrimitive)?.content?.toLongOrNull() ?: return ""
        val unit = (a["unit"] as? JsonPrimitive)?.content.orEmpty()
        val number = java.math.BigDecimal.valueOf(milli, 3).stripTrailingZeros().toPlainString()
        val label = when (unit) {
            "LB" -> res.getString(R.string.unit_lb)
            "KG" -> res.getString(R.string.unit_kg)
            "L" -> res.getString(R.string.unit_l)
            "M" -> res.getString(R.string.unit_m)
            else -> return number
        }
        return "$number $label"
    }

    fun channelName(res: Resources, channel: String): String = when (channel) {
        "STOCK" -> res.getString(R.string.notif_channel_stock)
        "CASH" -> res.getString(R.string.notif_channel_cash)
        "TEAM" -> res.getString(R.string.notif_channel_team)
        "SCHEDULED" -> res.getString(R.string.notif_channel_scheduled)
        "CREDIT" -> res.getString(R.string.notif_channel_credit)
        else -> res.getString(R.string.notif_channel_platform)
    }

    private fun args(json: String): JsonObject = runCatching { Json.parseToJsonElement(json).jsonObject }.getOrDefault(JsonObject(emptyMap()))
}

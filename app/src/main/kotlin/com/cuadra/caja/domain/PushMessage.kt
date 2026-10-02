package com.cuadra.caja.domain

/**
 * Un mensaje de datos de Firebase (sin texto visible): `{type: "SYNC", businessId}` = «sincroniza ya» (cambió una promoción, un precio, el equipo, una cuenta
 * por cobrar en caja…); `{type: "NOTIFY", businessId, notificationId}` = hay un aviso nuevo en la bandeja (se sincroniza y se muestra como notificación).
 * Lógica pura (`PushMessageTest`).
 */
sealed interface PushMessage {
    val businessId: String

    data class Sync(override val businessId: String) : PushMessage
    data class Notify(override val businessId: String, val notificationId: String?) : PushMessage

    companion object {
        fun parse(data: Map<String, String>): PushMessage? {
            val business = data["businessId"]?.takeIf { it.isNotBlank() } ?: return null
            return when (data["type"]) {
                "SYNC" -> Sync(business)
                "NOTIFY" -> Notify(business, data["notificationId"])
                else -> null
            }
        }
    }
}

/** Qué hacer con un mensaje según el negocio activo y si la app está a la vista. */
enum class PushAction { IGNORE, SYNC_NOW, SYNC_EXPEDITED }

object PushPolicy {
    /**
     * Solo se sincroniza el negocio ACTIVO (la base de otro negocio solo guarda su cola sin enviar, ADR 0014): un mensaje de otro negocio se ignora. Con la app
     * a la vista se sincroniza ya; en segundo plano, con un trabajo urgente de WorkManager (el sistema lo corre aunque la app esté cerrada).
     */
    fun action(m: PushMessage, activeBusinessId: String?, foreground: Boolean): PushAction = when {
        activeBusinessId == null || m.businessId != activeBusinessId -> PushAction.IGNORE
        foreground -> PushAction.SYNC_NOW
        else -> PushAction.SYNC_EXPEDITED
    }

    /** Sincronización frecuente con la app a la vista (respaldo cuando no llegan avisos de Firebase): cada 30 s. */
    const val FOREGROUND_POLL_MILLIS = 30_000L

    /** Con Firebase funcionando, el respaldo se espacia: un aviso perdido se recupera igual en pocos minutos. */
    const val FOREGROUND_POLL_WITH_PUSH_MILLIS = 5 * 60_000L

    /** Al volver a la app o abrir la caja no se repite si hubo otra hace menos de esto. */
    const val KICK_GAP_MILLIS = 5_000L

    /** Cada cuánto se sincroniza con la app a la vista según si Firebase está activo en este teléfono. */
    fun pollEvery(pushActive: Boolean): Long = if (pushActive) FOREGROUND_POLL_WITH_PUSH_MILLIS else FOREGROUND_POLL_MILLIS

    /**
     * El permiso de notificaciones (Android 13+) se pide UNA sola vez, con su explicación, y nunca en el primer arranque: desde la segunda vez que se abre la
     * app con alguien atendiendo, si aún no se concedió ni se preguntó.
     */
    fun askNotificationPermission(sdk: Int, granted: Boolean, alreadyAsked: Boolean, launches: Int, hasMember: Boolean): Boolean =
        sdk >= 33 && !granted && !alreadyAsked && launches >= 2 && hasMember
}

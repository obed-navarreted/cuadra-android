package com.cuadra.caja.data.push

import com.cuadra.caja.CuadraApp
import com.cuadra.caja.domain.PushAction
import com.cuadra.caja.domain.PushMessage
import com.cuadra.caja.domain.PushPolicy
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.launch

/**
 * Mensajes de Firebase. Todos son de DATOS: «sincroniza ya» (`SYNC`) y «hay un aviso nuevo» (`NOTIFY`). Los dos terminan en una sincronización del negocio
 * activo; al terminar, la bandeja muestra como notificación del sistema lo nuevo (`NotificationPresenter`: una sola vez, con las preferencias y las horas de
 * silencio que ya aplicó el servidor, y solo lo de la persona que atiende). Con la app a la vista se sincroniza ya; en segundo plano, con un trabajo urgente.
 */
class CuentivaMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        val c = (application as CuadraApp).container
        c.scope.launch { runCatching { c.push.register(c.sessionStore.current(), fresh = token) } }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val c = (application as CuadraApp).container
        val m = PushMessage.parse(message.data) ?: return
        c.scope.launch {
            val session = c.sessionStore.current()
            when (PushPolicy.action(m, session.businessId, c.foreground.visible)) {
                PushAction.IGNORE -> Unit
                PushAction.SYNC_NOW -> c.foreground.pushed()
                PushAction.SYNC_EXPEDITED -> com.cuadra.caja.data.sync.SyncScheduler.requestExpedited(applicationContext)
            }
        }
    }
}

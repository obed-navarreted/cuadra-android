package com.cuadra.caja.data.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.cuadra.caja.MainActivity
import com.cuadra.caja.R
import com.cuadra.caja.data.local.Db
import com.cuadra.caja.data.local.NotificationEntity
import com.cuadra.caja.data.session.SessionStore
import com.cuadra.caja.ui.common.MoneyFormat
import com.cuadra.caja.ui.common.NotificationTexts

/**
 * Muestra como notificación del sistema los avisos nuevos de la bandeja. La bandeja es la fuente de verdad: si no hay permiso, o el aviso es de
 * horas de silencio (`push = false`), sigue en la bandeja. Un aviso se muestra una sola vez (`shown`) y uno viejo nunca sale de golpe.
 * Cuando exista FCM solo cambiará CUÁNDO se ejecuta esto (al llegar el push), no cómo.
 */
class NotificationPresenter(
    private val context: Context,
    private val db: Db,
    private val session: SessionStore,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val channels = listOf("STOCK", "CASH", "TEAM", "SCHEDULED", "CREDIT", "PLATFORM")

    /** Un canal por tipo de aviso: la persona puede silenciar cada uno desde los ajustes del sistema. */
    fun ensureChannels() {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        for (c in channels) {
            val name = NotificationTexts.channelName(context.resources, c)
            manager.createNotificationChannel(NotificationChannel(channelId(c), name, importance(c)))
        }
    }

    fun canPost(): Boolean = NotificationManagerCompat.from(context).areNotificationsEnabled()

    suspend fun showNew() {
        val member = session.current().memberId ?: return
        val pending = db.notifications().toShow(member)
        if (pending.isEmpty()) return
        ensureChannels()
        val can = canPost()
        val recent = now() - RECENT_MILLIS
        val business = db.directory().businessNow()
        val fmt = business?.let { MoneyFormat.of(it.currency, it.country) } ?: MoneyFormat.Default
        for (n in pending) if (can && n.createdAt >= recent) post(n, fmt)
        db.notifications().markShown(pending.map { it.id })
    }

    @android.annotation.SuppressLint("MissingPermission")
    private fun post(n: NotificationEntity, fmt: MoneyFormat) {
        val (title, body) = NotificationTexts.render(context.resources, n) { fmt.format(it) }
        if (title.isBlank() && body.isBlank()) return
        val open = Intent(Intent.ACTION_VIEW, Uri.parse(n.deepLink ?: "cuadra://notificaciones")).setClass(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pi = PendingIntent.getActivity(context, n.id.hashCode(), open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(context, channelId(n.channel))
            .setSmallIcon(R.drawable.ic_stat_cuadra).setContentTitle(title).setContentText(body).setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pi).setAutoCancel(true).setGroup(channelId(n.channel)).setWhen(n.createdAt).build()
        runCatching { NotificationManagerCompat.from(context).notify(n.id.hashCode(), notification) }
    }

    /** Las novedades de Cuadra no suenan: importancia baja. El resto, la normal. */
    private fun importance(channel: String) = if (channel == "PLATFORM") NotificationManager.IMPORTANCE_LOW else NotificationManager.IMPORTANCE_DEFAULT

    private fun channelId(channel: String) = "cuadra_" + channel.lowercase()

    private companion object {
        /** Lo que llegó hace más de esto ya no es "un aviso": queda en la bandeja sin sonar. */
        const val RECENT_MILLIS = 12L * 3600 * 1000
    }
}

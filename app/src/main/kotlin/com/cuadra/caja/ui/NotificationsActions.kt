package com.cuadra.caja.ui

import com.cuadra.caja.data.local.NotificationEntity

/** Lo que las pantallas le piden al ViewModel. Tiene cuerpos vacíos por omisión: la guardia de diseño usa `object : NotificationsActions {}`. */
interface NotificationsActions {
    fun markAllRead() {}
    fun openPrefs() {}
    fun closePrefs() {}
    fun toggle(type: String, enabled: Boolean) {}
    fun open(n: NotificationEntity): String? = null
}

package com.cuadra.caja.data.push

import android.content.Context

/**
 * Lo poco que recuerda este teléfono de los avisos al instante: el último token registrado (y para qué negocio y persona: no se repite el registro), si ya se
 * preguntó por el permiso de notificaciones y cuántas veces se abrió la app (el permiso nunca se pide en el primer arranque). Archivo pequeño y local.
 */
class PushPrefs(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("push", Context.MODE_PRIVATE)

    var registeredKey: String?
        get() = prefs.getString("registered", null)
        set(v) { prefs.edit().putString("registered", v).apply() }

    var permissionAsked: Boolean
        get() = prefs.getBoolean("permission_asked", false)
        set(v) { prefs.edit().putBoolean("permission_asked", v).apply() }

    val launches: Int get() = prefs.getInt("launches", 0)

    fun countLaunch() { prefs.edit().putInt("launches", launches + 1).apply() }
}

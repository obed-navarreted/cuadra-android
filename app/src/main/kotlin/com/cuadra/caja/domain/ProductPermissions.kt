package com.cuadra.caja.domain

/**
 * Quién puede hacer qué con el catálogo (misma matriz que el servidor, `Role.java`).
 * Agregar y editar productos y precios: cualquiera que atiende la caja. Dar de baja o reactivar: solo dueño y administrador.
 */
object ProductPermissions {
    fun canEdit(role: String?): Boolean = role == "OWNER" || role == "ADMIN" || role == "CASHIER"
    fun canDelete(role: String?): Boolean = role == "OWNER" || role == "ADMIN"
}

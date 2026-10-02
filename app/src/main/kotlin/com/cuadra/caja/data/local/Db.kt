package com.cuadra.caja.data.local

/**
 * Lo que los repositorios necesitan de la base del teléfono. Hay UNA base por negocio (`BusinessDatabases`): quien recibe un `Db` lo usa sin saber de
 * cuál negocio es, y no hay forma de que una consulta lea filas de otro negocio porque viven en otro archivo.
 */
interface Db {
    fun products(): ProductDao
    fun sales(): SaleDao
    fun outbox(): OutboxDao
    fun directory(): DirectoryDao
    fun customers(): CustomerDao
    fun credits(): CreditDao
    fun templates(): TemplateDao
    fun cash(): CashDao
    fun inventory(): InventoryDao
    fun notifications(): NotificationDao
    fun reports(): ReportDao

    /** Todo o nada, en la base del negocio actual. */
    suspend fun <R> inTransaction(block: suspend () -> R): R
}

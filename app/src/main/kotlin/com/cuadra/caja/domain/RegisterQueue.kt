package com.cuadra.caja.domain

import com.cuadra.caja.data.local.SaleEntity

/**
 * Cobro en caja (docs/adr/0015): quien atiende arma la cuenta y la «envía a caja»; cualquiera la cobra después. Una cuenta por cobrar en caja es una cuenta
 * apartada (PARKED) con `sentToRegisterAt`; la nota («Mesa 4, Juan») es su etiqueta. Decisiones puras, sin interfaz.
 */
object RegisterQueue {
    /** Lo que cabe en la nota (el servidor acepta hasta 120). */
    const val NOTE_MAX = 60

    /** La lista de la caja: las por cobrar en caja (de la más vieja a la más nueva: la que más espera, primero) y las apartadas comunes (las recientes primero). */
    data class Split(val pending: List<SaleEntity>, val parked: List<SaleEntity>) {
        val pendingTotalMinor: Long get() = pending.sumOf { it.totalMinor }
    }

    fun isPending(s: SaleEntity): Boolean = s.status == "PARKED" && s.sentToRegisterAt != null

    fun split(all: List<SaleEntity>): Split {
        val (pending, parked) = all.filter { it.status == "PARKED" }.partition { it.sentToRegisterAt != null }
        return Split(pending.sortedWith(compareBy<SaleEntity> { it.sentToRegisterAt }.thenBy { it.id }), parked.sortedByDescending { it.updatedAt })
    }

    /** Cuántas cuentas cuenta el botón de la caja: con el ajuste, todas (por cobrar en caja + apartadas) en un solo lugar. */
    fun chipCount(split: Split): Int = split.pending.size + split.parked.size

    /** La nota limpia (sin espacios de más, a lo sumo [NOTE_MAX]); vacía = sin nota. */
    fun cleanNote(raw: String): String? = raw.trim().replace(Regex("\\s+"), " ").take(NOTE_MAX).ifEmpty { null }

    /**
     * «Atendió: Kevin · Cobró: Ana»: solo cuando la tomó una persona y la cobró otra. `null` si fueron la misma (o falta alguno): basta con «Atendió».
     */
    fun takenAndCharged(takenBy: String?, chargedBy: String?): Pair<String, String>? =
        if (takenBy.isNullOrBlank() || chargedBy.isNullOrBlank() || takenBy == chargedBy) null else takenBy to chargedBy
}

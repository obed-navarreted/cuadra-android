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

    /** Desde cuántas cuentas esperando aparece el buscador de la lista (con pocas se ven todas de un vistazo). */
    const val SEARCH_FROM = 6

    /** Desde cuántos minutos una cuenta «espera mucho» (su tiempo sale en naranja: que nadie se quede esperando). */
    const val WAITING_LONG_MIN = 15

    /** Minutos enteros que lleva esperando (0 = recién llegada; nunca negativo aunque el reloj de otro teléfono vaya adelantado). */
    fun ageMinutes(sentAt: Long?, nowMillis: Long): Long? = sentAt?.let { ((nowMillis - it) / 60_000L).coerceAtLeast(0) }

    fun waitingLong(sentAt: Long?, nowMillis: Long): Boolean = (ageMinutes(sentAt, nowMillis) ?: 0) >= WAITING_LONG_MIN

    /** Cómo se dice la espera: «ahora», «hace 12 min», «hace 1 h 5 min». */
    sealed interface Age {
        data object Now : Age
        data class Minutes(val minutes: Long) : Age
        data class Hours(val hours: Long, val minutes: Long) : Age
    }

    fun age(sentAt: Long?, nowMillis: Long): Age? = ageMinutes(sentAt, nowMillis)?.let { m ->
        when {
            m < 1 -> Age.Now
            m < 60 -> Age.Minutes(m)
            else -> Age.Hours(m / 60, m % 60)
        }
    }

    /**
     * Quien cobra abrió una cuenta de la lista con «Cobrar» y se echa atrás («‹»): vuelve a la lista (y la caja queda limpia) solo si vino de la lista y
     * sigue siendo una cuenta por cobrar en caja retomada. Un cobro normal se cierra y el recibo queda como estaba.
     */
    fun returnsToQueueOnBackOut(chargingFromQueue: Boolean, resumedPending: Boolean, resumedId: String?): Boolean =
        chargingFromQueue && resumedPending && resumedId != null

    /** El buscador de la lista: por la nota o por quién la envió/tomó, sin tildes ni mayúsculas; vacío = todas. Conserva el orden (la que más espera primero). */
    fun filter(pending: List<SaleEntity>, query: String): List<SaleEntity> {
        val q = ProductSearch.normalize(query)
        if (q.isBlank()) return pending
        val words = q.split(' ').filter { it.isNotBlank() }
        return pending.filter { s ->
            val hay = ProductSearch.normalize(listOfNotNull(s.label, s.sentByName, s.createdByName).joinToString(" "))
            words.all { it in hay }
        }
    }

    /**
     * «Atendió: Kevin · Cobró: Ana»: solo cuando la tomó una persona y la cobró otra. `null` si fueron la misma (o falta alguno): basta con «Atendió».
     */
    fun takenAndCharged(takenBy: String?, chargedBy: String?): Pair<String, String>? =
        if (takenBy.isNullOrBlank() || chargedBy.isNullOrBlank() || takenBy == chargedBy) null else takenBy to chargedBy
}

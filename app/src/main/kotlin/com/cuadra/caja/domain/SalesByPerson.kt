package com.cuadra.caja.domain

import com.cuadra.caja.data.remote.BreakdownRowDto

/** Cómo se reparte la tarjeta «Por persona» con «Cobro en caja»: quién cobró la venta o quién la atendió (la tomó o la envió a caja). */
enum class PeopleMode(val by: String) { CHARGED("member"), SERVED("member_served") }

/** Una fila de «Por persona»: `share` es la parte del total del periodo (0..1) y mueve la barra fina. */
data class PersonShare(val id: String?, val name: String, val count: Long, val totalMinor: Long, val share: Float) {
    val percent: Int get() = Math.round(share * 100f)
}

/** Lo que trajo el servidor para «Por persona»: las dos agrupaciones del mismo periodo (la de quien cobró y la de quien atendió). */
data class PeopleBreakdown(val charged: List<BreakdownRowDto>, val served: List<BreakdownRowDto>) {
    fun rows(mode: PeopleMode) = if (mode == PeopleMode.SERVED) served else charged
}

object SalesByPerson {
    const val TOP = 5

    /** De más a menos vendido (a igual total, más ventas y luego el nombre); la barra es la parte del total. Sin ventas no hay filas. */
    fun shares(rows: List<BreakdownRowDto>): List<PersonShare> {
        val positive = rows.filter { it.count > 0 || it.totalMinor != 0L }
        val total = positive.sumOf { it.totalMinor.coerceAtLeast(0) }
        return positive.map { r ->
            val share = if (total <= 0) 0f else (r.totalMinor.coerceAtLeast(0).toDouble() / total).toFloat().coerceIn(0f, 1f)
            PersonShare(r.key, r.label?.takeIf { it.isNotBlank() } ?: "—", r.count, r.totalMinor, share)
        }.sortedWith(compareByDescending<PersonShare> { it.totalMinor }.thenByDescending { it.count }.thenBy { it.name.lowercase() })
    }

    /** Las 5 primeras, o todas si se pidió «Ver todos». */
    fun visible(all: List<PersonShare>, showAll: Boolean): List<PersonShare> = if (showAll || all.size <= TOP + 1) all else all.take(TOP)

    /** «Ver todos (N)» solo si hay más de lo que se ve (con 6 personas se muestran las 6: ocultar una sola fila no ahorra nada). */
    fun hiddenCount(all: List<PersonShare>, showAll: Boolean): Int = if (showAll) 0 else all.size - visible(all, false).size

    /**
     * ¿Se ofrece «Cobró / Atendió»? Si el negocio usa «Cobro en caja», o si alguna venta del periodo la atendió una persona y la cobró otra: las dos
     * agrupaciones se reparten distinto.
     */
    fun showControl(registerCheckout: Boolean, breakdown: PeopleBreakdown?): Boolean {
        if (registerCheckout) return true
        val b = breakdown ?: return false
        return shares(b.charged).map { Triple(it.id, it.count, it.totalMinor) }.toSet() != shares(b.served).map { Triple(it.id, it.count, it.totalMinor) }.toSet()
    }

    /** El desglose cuenta ventas cobradas (y anuladas de otra jornada): con solo «Eliminadas» elegido la tarjeta no aplica. */
    fun applies(statuses: Set<String>) = "COMPLETED" in statuses
}

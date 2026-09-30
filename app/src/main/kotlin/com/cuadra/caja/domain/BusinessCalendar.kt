package com.cuadra.caja.domain

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** Una regla de jornada: desde la jornada `from` el día empieza a `cutoff` hora local de `zone`. */
data class DayRule(val from: LocalDate, val zone: ZoneId, val cutoff: LocalTime) {
    internal fun startOfDay(date: LocalDate): Instant = date.atTime(cutoff).atZone(zone).toInstant()

    internal fun localDate(instant: Instant): LocalDate =
        instant.atZone(zone).toLocalDateTime().minusHours(cutoff.hour.toLong()).minusMinutes(cutoff.minute.toLong()).toLocalDate()
}

/** Cómo viaja una regla (JSON de la API y de Room): `{from, timezone, dayCutoff}`. */
@Serializable
data class DayRuleDto(val from: String, val timezone: String, val dayCutoff: String)

/**
 * Las jornadas del negocio con su historial de reglas (ADR 0011). Es la MISMA regla que `BusinessDayService.Info` del servidor y `business_date()` de SQL:
 * los días son contiguos por construcción (`endOf(d) == startOf(d + 1)`) y en un cambio de regla el último día viejo termina donde empieza el primero
 * nuevo. La zona y el corte son del NEGOCIO: jamás se usa la zona del teléfono para decidir a qué día pertenece algo.
 */
class BusinessCalendar(rules: List<DayRule>) {
    val rules: List<DayRule> = rules.sortedBy { it.from }

    init {
        require(this.rules.isNotEmpty()) { "Hace falta al menos una regla" }
    }

    /** Una sola regla desde siempre (negocio sin historial de cambios). */
    constructor(zone: ZoneId, cutoff: LocalTime) : this(listOf(DayRule(SINCE_FOREVER, zone, cutoff)))

    /** La regla más reciente (puede empezar en el futuro): la zona con que se muestran las horas del negocio. */
    val current: DayRule get() = rules.last()
    val zone: ZoneId get() = current.zone
    val cutoff: LocalTime get() = current.cutoff

    private fun ruleForDate(date: LocalDate): DayRule {
        var found = rules.first()
        for (r in rules) if (!r.from.isAfter(date)) found = r
        return found
    }

    fun startOf(date: LocalDate): Instant = ruleForDate(date).startOfDay(date)

    fun endOf(date: LocalDate): Instant = startOf(date.plusDays(1))

    fun startMillis(date: LocalDate): Long = startOf(date).toEpochMilli()

    fun endMillis(date: LocalDate): Long = endOf(date).toEpochMilli()

    fun dateOf(instant: Instant): LocalDate {
        var r = rules.first()
        for (x in rules) if (x.from == SINCE_FOREVER || !x.startOfDay(x.from).isAfter(instant)) r = x
        var d = r.localDate(instant)
        // En el borde de un cambio de regla, ajusta al día cuyos límites contienen el instante.
        while (instant.isBefore(startOf(d))) d = d.minusDays(1)
        while (!instant.isBefore(startOf(d.plusDays(1)))) d = d.plusDays(1)
        return d
    }

    fun dateOf(millis: Long): LocalDate = dateOf(Instant.ofEpochMilli(millis))

    /** ¿Hay una regla que todavía no rige en `instant`? Devuelve la jornada desde la que regirá. */
    fun pendingFrom(instant: Instant): LocalDate? {
        val c = current
        return if (c.from == SINCE_FOREVER || !c.startOfDay(c.from).isAfter(instant)) null else c.from
    }

    /** La jornada `date` con su ventana [inicio, fin) en milisegundos. */
    fun day(date: LocalDate) = BusinessDay(date, startMillis(date), endMillis(date))

    companion object {
        val SINCE_FOREVER: LocalDate = LocalDate.of(1970, 1, 1)
        private val json = Json { ignoreUnknownKeys = true }

        /** Sin reglas (teléfono con datos anteriores a la migración) se usa lo configurado en el propio negocio: una regla desde siempre. */
        fun of(rules: List<DayRuleDto>, timezone: String, dayCutoff: String): BusinessCalendar {
            val parsed = rules.mapNotNull { r ->
                runCatching { DayRule(LocalDate.parse(r.from), ZoneId.of(r.timezone), LocalTime.parse(r.dayCutoff)) }.getOrNull()
            }
            if (parsed.isNotEmpty()) return BusinessCalendar(parsed)
            val zone = runCatching { ZoneId.of(timezone) }.getOrDefault(ZoneId.of("UTC"))
            val cutoff = runCatching { LocalTime.parse(dayCutoff) }.getOrDefault(LocalTime.of(2, 0))
            return BusinessCalendar(zone, cutoff)
        }

        fun fromJson(rulesJson: String?, timezone: String, dayCutoff: String): BusinessCalendar {
            val rules = rulesJson?.takeIf { it.isNotBlank() }?.let { runCatching { json.decodeFromString(ListSerializer(DayRuleDto.serializer()), it) }.getOrNull() }.orEmpty()
            return of(rules, timezone, dayCutoff)
        }

        fun toJson(rules: List<DayRuleDto>): String = json.encodeToString(ListSerializer(DayRuleDto.serializer()), rules)
    }
}

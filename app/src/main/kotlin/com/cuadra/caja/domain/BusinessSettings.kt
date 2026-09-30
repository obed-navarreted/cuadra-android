package com.cuadra.caja.domain

import com.cuadra.caja.data.local.BusinessEntity
import com.cuadra.caja.data.remote.NotificationSettingsDto
import com.cuadra.caja.data.remote.UpdateBusinessBody
import com.cuadra.caja.data.sync.posViews
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale

/** Módulos que el dueño puede encender o esconder (el servidor rechaza cualquier otro). Turnos NO se ofrece: el cierre es automático por jornada. */
object SettingsModules {
    val KEYS = listOf("credit", "expenses", "inventory", "catalog", "team")

    /** Un módulo sin valor guardado: todos están encendidos salvo inventario y turnos (igual que el servidor y el panel web). */
    fun isOn(modules: Map<String, Boolean>, key: String): Boolean = modules[key] ?: (key != "inventory" && key != "shifts")
}

/** Vistas de la caja (pestañas al vender). Se conserva al menos una. */
/** Pestañas de la caja: «Manual» (la calculadora) y «Productos» (buscador con los frecuentes). */
enum class RegisterTab { MANUAL, PRODUCTS }

object PosViews {
    val ALL = listOf("TYPE", "QUICK", "LIST")

    /** Las vistas del servidor que hoy son la pestaña «Productos» (antes «Rápidos» y «Lista»). */
    val PRODUCTS = setOf("QUICK", "LIST")

    /** En el orden canónico, sin repetir y sin valores desconocidos. */
    fun canonical(views: Collection<String>): List<String> = ALL.filter { it in views }

    /**
     * Pestañas de la caja según `pos_views` del negocio: TYPE → Manual; QUICK o LIST → Productos. Por omisión (negocio nuevo, datos aún sin llegar
     * o ilegibles) van LAS DOS, igual que el valor por omisión del servidor (V16).
     */
    fun tabs(views: Collection<String>): List<RegisterTab> {
        val tabs = buildList {
            if ("TYPE" in views) add(RegisterTab.MANUAL)
            if (views.any { it in PRODUCTS }) add(RegisterTab.PRODUCTS)
        }
        return tabs.ifEmpty { listOf(RegisterTab.MANUAL, RegisterTab.PRODUCTS) }
    }

    /** Encender o apagar «Productos» en Ajustes: van juntas las dos vistas viejas (QUICK y LIST). */
    fun withProducts(views: Set<String>, on: Boolean): Set<String> = if (on) views + PRODUCTS else views - PRODUCTS
}

/** Lo que el formulario de «Ajustes del negocio» deja editar: todo como texto o interruptor, tal como se escribe. */
data class SettingsDraft(
    val name: String,
    val type: String,
    val timezone: String,
    val dayCutoff: String,
    val posViews: Set<String>,
    val creditRequiresCustomer: Boolean,
    val creditLimitEnforced: Boolean,
    val creditDefaultDueDays: String,
    val creditOverdueDays: String,
    /** País y moneda: se cambian solo mientras no haya actividad (el servidor responde CURRENCY_LOCKED después). */
    val country: String = "",
    val currency: String = "",
) {
    companion object {
        fun of(b: BusinessEntity) = SettingsDraft(
            name = b.name,
            type = b.type.orEmpty(),
            timezone = b.timezone,
            dayCutoff = b.dayCutoff.take(5),
            posViews = PosViews.canonical(runCatching { b.posViews() }.getOrDefault(emptyList())).toSet(),
            creditRequiresCustomer = b.creditRequiresCustomer,
            creditLimitEnforced = b.creditLimitEnforced,
            creditDefaultDueDays = b.creditDefaultDueDays?.toString().orEmpty(),
            creditOverdueDays = b.creditOverdueDays.toString(),
            country = b.country,
            currency = b.currency,
        )
    }
}

/** Qué campo del formulario impide guardar. */
enum class SettingsError { NAME, TIMEZONE, CUTOFF, POS_VIEWS, DUE_DAYS, OVERDUE_DAYS }

object BusinessSettingsRules {
    const val NAME_MAX = 120
    const val TYPE_MAX = 40
    const val DUE_DAYS_MAX = 365
    const val OVERDUE_MIN = 1
    const val OVERDUE_MAX = 365

    /** La misma validación que el panel web (`validateForm`) y el servidor. La hora de corte es «HH:mm». */
    fun validate(d: SettingsDraft): SettingsError? = when {
        d.name.isBlank() || d.name.trim().length > NAME_MAX -> SettingsError.NAME
        !isZone(d.timezone) -> SettingsError.TIMEZONE
        !TimeText.isValid(d.dayCutoff) -> SettingsError.CUTOFF
        d.posViews.isEmpty() -> SettingsError.POS_VIEWS
        d.creditDefaultDueDays.isNotBlank() && whole(d.creditDefaultDueDays, 0, DUE_DAYS_MAX) == null -> SettingsError.DUE_DAYS
        whole(d.creditOverdueDays, OVERDUE_MIN, OVERDUE_MAX) == null -> SettingsError.OVERDUE_DAYS
        else -> null
    }

    fun isZone(id: String): Boolean = id.isNotBlank() && runCatching { ZoneId.of(id) }.isSuccess

    private fun whole(s: String, min: Int, max: Int): Int? = s.trim().takeIf { it.length in 1..4 && it.all { c -> c in '0'..'9' } }?.toInt()?.takeIf { it in min..max }

    /**
     * Solo lo que cambió respecto al negocio guardado (`UpdateBusiness`: ausente = sin cambio), así nunca se pisa algo que otra persona cambió mientras esta
     * pantalla estaba abierta. Los días de vencimiento vacíos, si antes había un valor, se piden con la bandera explícita `clearCreditDefaultDueDays`.
     * El tipo de negocio no se puede dejar vacío (el servidor no lo permite): un tipo vacío no se envía.
     */
    fun patch(d: SettingsDraft, current: SettingsDraft): UpdateBusinessBody {
        val dueNow = d.creditDefaultDueDays.trim()
        val dueBefore = current.creditDefaultDueDays.trim()
        return UpdateBusinessBody(
            name = d.name.trim().takeIf { it != current.name.trim() },
            type = d.type.trim().takeIf { it.isNotEmpty() && it != current.type.trim() },
            timezone = d.timezone.takeIf { it != current.timezone },
            dayCutoff = d.dayCutoff.takeIf { it != current.dayCutoff },
            posViews = PosViews.canonical(d.posViews).takeIf { it != PosViews.canonical(current.posViews) },
            creditRequiresCustomer = d.creditRequiresCustomer.takeIf { it != current.creditRequiresCustomer },
            creditLimitEnforced = d.creditLimitEnforced.takeIf { it != current.creditLimitEnforced },
            creditDefaultDueDays = if (dueNow.isNotEmpty() && dueNow != dueBefore) dueNow.toIntOrNull() else null,
            clearCreditDefaultDueDays = if (dueNow.isEmpty() && dueBefore.isNotEmpty()) true else null,
            creditOverdueDays = d.creditOverdueDays.trim().takeIf { it != current.creditOverdueDays.trim() }?.toIntOrNull(),
            currency = d.currency.trim().uppercase().takeIf { it.isNotEmpty() && it != current.currency.trim().uppercase() },
            country = d.country.trim().uppercase().takeIf { it.isNotEmpty() && it != current.country.trim().uppercase() },
        )
    }

    /** ¿Cambiar la zona o el corte? Es lo único que exige el aviso de la ADR 0011 antes de guardar. */
    fun changesDayRule(d: SettingsDraft, current: SettingsDraft): Boolean = d.timezone != current.timezone || d.dayCutoff != current.dayCutoff
}

/** Horas «HH:mm». */
object TimeText {
    private val PATTERN = Regex("^([01]\\d|2[0-3]):[0-5]\\d$")

    fun isValid(s: String): Boolean = PATTERN.matches(s)

    /** Lo que se deja escribir en un campo de hora: solo dígitos, con los dos puntos puestos solos («0200» → «02:00»), máximo 5 caracteres. */
    fun sanitize(raw: String): String {
        val digits = raw.filter { it in '0'..'9' }.take(4)
        return if (digits.length <= 2) digits else digits.substring(0, 2) + ":" + digits.substring(2)
    }
}

/** Consejos sobre la hora de corte según la zona (ADR 0011: 02:00 por defecto y 04:00 si la zona cambia de hora en verano). */
object DayRuleAdvice {
    val RECOMMENDED_DST = LocalTime.of(4, 0)

    /** ¿La zona cambia de hora durante el año? Compara el desfase de enero y de julio (basta con que difieran: en el sur el verano cae en enero). */
    fun observesDst(zoneId: String, year: Int = LocalDate.now().year): Boolean {
        val zone = runCatching { ZoneId.of(zoneId) }.getOrNull() ?: return false
        val rules = zone.rules
        val jan = rules.getOffset(LocalDate.of(year, 1, 1).atStartOfDay(ZoneId.of("UTC")).toInstant())
        val jul = rules.getOffset(LocalDate.of(year, 7, 1).atStartOfDay(ZoneId.of("UTC")).toInstant())
        return jan != jul
    }

    /** ¿Conviene avisar? Zona con cambio de hora y corte antes de las 04:00: a las 02:00 hay un salto o una hora repetida. */
    fun dstCutoffTip(zoneId: String, cutoff: String, year: Int = LocalDate.now().year): Boolean =
        TimeText.isValid(cutoff) && cutoff < "04:00" && observesDst(zoneId, year)
}

/**
 * Zonas horarias para elegir en el teléfono: primero la actual y las del país del negocio, después las que coinciden con lo escrito. Una lista corta
 * (nunca las 400): la persona escribe o dicta «Managua» y aparece.
 */
object TimeZones {
    const val MAX = 8

    private val REGIONS = setOf("Africa", "America", "Antarctica", "Asia", "Atlantic", "Australia", "Europe", "Indian", "Pacific")

    val all: List<String> by lazy { ZoneId.getAvailableZoneIds().filter { it.substringBefore('/') in REGIONS && it.contains('/') }.sorted() + "UTC" }

    /** Zonas de un país (código ISO de 2 letras) según los datos del JDK; vacío si no se conoce. */
    private fun ofCountry(country: String): List<String> = when (country.uppercase(Locale.ROOT)) {
        "NI" -> listOf("America/Managua")
        "CR" -> listOf("America/Costa_Rica")
        "HN" -> listOf("America/Tegucigalpa")
        "GT" -> listOf("America/Guatemala")
        "SV" -> listOf("America/El_Salvador")
        "PA" -> listOf("America/Panama")
        "MX" -> listOf("America/Mexico_City", "America/Cancun", "America/Tijuana", "America/Monterrey")
        "CO" -> listOf("America/Bogota")
        "PE" -> listOf("America/Lima")
        "EC" -> listOf("America/Guayaquil")
        "AR" -> listOf("America/Argentina/Buenos_Aires")
        "CL" -> listOf("America/Santiago")
        "VE" -> listOf("America/Caracas")
        "DO" -> listOf("America/Santo_Domingo")
        "US" -> listOf("America/New_York", "America/Chicago", "America/Denver", "America/Los_Angeles")
        "ES" -> listOf("Europe/Madrid", "Atlantic/Canary")
        else -> emptyList()
    }

    fun suggest(query: String, current: String, country: String): List<String> {
        val q = query.trim().replace(' ', '_').lowercase(Locale.ROOT)
        val out = linkedSetOf<String>()
        if (q.isEmpty()) {
            out += current
            out += ofCountry(country).filter { isKnown(it) }
            return out.take(MAX)
        }
        all.filter { it.lowercase(Locale.ROOT).contains(q) }.sortedWith(compareBy({ !it.lowercase(Locale.ROOT).substringAfterLast('/').startsWith(q) }, { it })).forEach { out += it }
        return out.take(MAX)
    }

    private fun isKnown(id: String) = BusinessSettingsRules.isZone(id)

    /** Cuántas horas de diferencia con UTC tiene ahora («UTC−6»), para reconocer la zona. */
    fun offsetLabel(id: String, at: java.time.Instant = java.time.Instant.now()): String {
        val zone = runCatching { ZoneId.of(id) }.getOrNull() ?: return ""
        val secs = zone.rules.getOffset(at).totalSeconds
        val sign = if (secs < 0) "−" else "+"
        val abs = kotlin.math.abs(secs)
        val h = abs / 3600
        val m = (abs % 3600) / 60
        return "UTC$sign$h" + if (m != 0) ":" + m.toString().padStart(2, '0') else ""
    }
}

/** Las reglas de avisos del negocio (`notification-settings`): silencio, resumen diario y horas sin sincronizar. */
data class NotifyDraft(
    val quietStart: String,
    val quietEnd: String,
    val summaryEnabled: Boolean,
    val summaryTime: String,
    val staleHours: String,
    /** Se conserva tal cual lo dijo el servidor (el recordatorio de cierre no se ofrece: no hay cierres manuales). */
    val shiftReminderTime: String? = null,
) {
    companion object {
        fun of(s: NotificationSettingsDto) = NotifyDraft(s.quietStart.take(5), s.quietEnd.take(5), s.summaryEnabled, s.summaryTime.take(5), s.staleHours.toString(), s.shiftReminderTime)
    }

    fun toDto(): NotificationSettingsDto = NotificationSettingsDto(quietStart, quietEnd, summaryEnabled, summaryTime, shiftReminderTime, staleHours.trim().toInt())
}

enum class NotifyError { QUIET_START, QUIET_END, SUMMARY_TIME, STALE_HOURS }

object NotifyRules {
    const val STALE_MAX = 720

    fun validate(d: NotifyDraft): NotifyError? = when {
        !TimeText.isValid(d.quietStart) -> NotifyError.QUIET_START
        !TimeText.isValid(d.quietEnd) -> NotifyError.QUIET_END
        !TimeText.isValid(d.summaryTime) -> NotifyError.SUMMARY_TIME
        d.staleHours.trim().toIntOrNull()?.takeIf { it in 1..STALE_MAX } == null || !d.staleHours.trim().all { it in '0'..'9' } -> NotifyError.STALE_HOURS
        else -> null
    }
}

package com.cuadra.caja.domain

import com.cuadra.caja.data.remote.ScheduleAudienceDto
import com.cuadra.caja.data.remote.ScheduleDto
import com.cuadra.caja.data.remote.ScheduleInputDto
import com.cuadra.caja.data.remote.ScheduleRuleDto
import java.time.LocalDate

enum class ScheduleWhen { NOW, ONCE, REPEAT }
enum class ScheduleRepeat { DAILY, WEEKLY, MONTHLY, EVERY_N }

/** Qué campo está mal, para decirlo con claridad en pantalla. */
enum class DraftError { TITLE, BODY, AUDIENCE, DATE, TIME, DAYS, DAY_OF_MONTH, EVERY_DAYS, END_DATE }

/** Lo que la persona va escribiendo en el editor de una programación. Todo texto tal cual se escribe; `validate` lo revisa. */
data class ScheduleDraft(
    val id: String? = null,
    val title: String = "",
    val body: String = "",
    val all: Boolean = false,
    val roles: Set<String> = emptySet(),
    val memberIds: Set<String> = emptySet(),
    val link: String? = null,
    val whenMode: ScheduleWhen = ScheduleWhen.REPEAT,
    val date: String = "",
    val time: String = "",
    val repeat: ScheduleRepeat = ScheduleRepeat.DAILY,
    val days: Set<Int> = emptySet(),
    val dayOfMonth: String = "",
    val everyDays: String = "",
    val endDate: String = "",
)

/** Cómo se lee una regla, como datos: la pantalla la convierte en texto en el idioma de la persona. */
sealed interface RuleSummary {
    data class Once(val date: String, val time: String) : RuleSummary
    data class Daily(val time: String) : RuleSummary
    data class Weekly(val days: List<Int>, val time: String) : RuleSummary
    data class Monthly(val day: Int, val time: String) : RuleSummary
    data class EveryN(val n: Int, val time: String) : RuleSummary
}

object ScheduleDrafts {
    const val TITLE_RECOMMENDED = 65
    const val BODY_RECOMMENDED = 240
    const val TITLE_MAX = 100
    const val BODY_MAX = 500

    /** Para "Enviar ahora" el servidor ignora la regla; se manda una válida cualquiera. */
    private const val NOW_RULE_AT = "2000-01-01T00:00"
    private val TIME = Regex("([01]\\d|2[0-3]):[0-5]\\d")

    fun validTime(text: String) = TIME.matches(text.trim())

    fun validDate(text: String) = parseDate(text) != null

    private fun parseDate(text: String): LocalDate? = runCatching { LocalDate.parse(text.trim()) }.getOrNull()

    /** Primer error del borrador, o null si se puede enviar. El servidor valida igual: esto solo ahorra ir y volver. */
    fun validate(d: ScheduleDraft): DraftError? {
        if (d.title.isBlank() || d.title.trim().length > TITLE_MAX) return DraftError.TITLE
        if (d.body.isBlank() || d.body.trim().length > BODY_MAX) return DraftError.BODY
        if (!d.all && d.roles.isEmpty() && d.memberIds.isEmpty()) return DraftError.AUDIENCE
        when (d.whenMode) {
            ScheduleWhen.NOW -> return null
            ScheduleWhen.ONCE -> {
                if (!validDate(d.date)) return DraftError.DATE
                if (!validTime(d.time)) return DraftError.TIME
            }
            ScheduleWhen.REPEAT -> {
                if (!validTime(d.time)) return DraftError.TIME
                when (d.repeat) {
                    ScheduleRepeat.WEEKLY -> if (d.days.isEmpty()) return DraftError.DAYS
                    ScheduleRepeat.MONTHLY -> if (d.dayOfMonth.trim().toIntOrNull()?.let { it in 1..31 } != true) return DraftError.DAY_OF_MONTH
                    ScheduleRepeat.EVERY_N -> if (d.everyDays.trim().toIntOrNull()?.let { it in 1..365 } != true) return DraftError.EVERY_DAYS
                    ScheduleRepeat.DAILY -> Unit
                }
                if (d.endDate.isNotBlank() && !validDate(d.endDate)) return DraftError.END_DATE
            }
        }
        return null
    }

    /** La regla del borrador (asume que `validate` no dio error). */
    fun rule(d: ScheduleDraft): ScheduleRuleDto = when (d.whenMode) {
        ScheduleWhen.NOW -> ScheduleRuleDto(type = "ONCE", at = NOW_RULE_AT)
        ScheduleWhen.ONCE -> ScheduleRuleDto(type = "ONCE", at = d.date.trim() + "T" + d.time.trim())
        ScheduleWhen.REPEAT -> {
            val end = d.endDate.trim().ifEmpty { null }
            val time = d.time.trim()
            when (d.repeat) {
                ScheduleRepeat.DAILY -> ScheduleRuleDto(type = "DAILY", time = time, endDate = end)
                ScheduleRepeat.WEEKLY -> ScheduleRuleDto(type = "WEEKLY", time = time, days = d.days.sorted(), endDate = end)
                ScheduleRepeat.MONTHLY -> ScheduleRuleDto(type = "MONTHLY", time = time, dayOfMonth = d.dayOfMonth.trim().toIntOrNull(), endDate = end)
                ScheduleRepeat.EVERY_N -> ScheduleRuleDto(type = "EVERY_N_DAYS", time = time, everyDays = d.everyDays.trim().toIntOrNull(), endDate = end)
            }
        }
    }

    fun toInput(d: ScheduleDraft): ScheduleInputDto = ScheduleInputDto(
        title = d.title.trim(), body = d.body.trim(), deepLink = d.link,
        audience = ScheduleAudienceDto(all = d.all.takeIf { it }, roles = d.roles.sorted(), memberIds = d.memberIds.sorted()),
        rule = rule(d), active = true,
    )

    /** Un borrador para editar lo ya programado. Las reglas de fecha única quedan como "Una vez". */
    fun draftOf(s: ScheduleDto): ScheduleDraft {
        val r = s.rule
        val base = ScheduleDraft(
            id = s.id, title = s.title, body = s.body, all = s.audience.all == true, roles = s.audience.roles.toSet(), memberIds = s.audience.memberIds.toSet(), link = s.deepLink,
        )
        return when (r.type) {
            "ONCE" -> base.copy(whenMode = ScheduleWhen.ONCE, date = r.at?.substringBefore('T').orEmpty(), time = r.at?.substringAfter('T', "").orEmpty())
            else -> base.copy(
                whenMode = ScheduleWhen.REPEAT, time = r.time.orEmpty(), endDate = r.endDate.orEmpty(),
                repeat = when (r.type) { "WEEKLY" -> ScheduleRepeat.WEEKLY; "MONTHLY" -> ScheduleRepeat.MONTHLY; "EVERY_N_DAYS" -> ScheduleRepeat.EVERY_N; else -> ScheduleRepeat.DAILY },
                days = r.days.orEmpty().toSet(), dayOfMonth = r.dayOfMonth?.toString().orEmpty(), everyDays = r.everyDays?.toString().orEmpty(),
            )
        }
    }

    fun summary(r: ScheduleRuleDto): RuleSummary {
        val time = r.time.orEmpty()
        return when (r.type) {
            "ONCE" -> RuleSummary.Once(r.at?.substringBefore('T').orEmpty(), r.at?.substringAfter('T', "").orEmpty())
            "WEEKLY" -> RuleSummary.Weekly(r.days.orEmpty().sorted(), time)
            "MONTHLY" -> RuleSummary.Monthly(r.dayOfMonth ?: 1, time)
            "EVERY_N_DAYS" -> RuleSummary.EveryN(r.everyDays ?: 1, time)
            else -> RuleSummary.Daily(time)
        }
    }
}

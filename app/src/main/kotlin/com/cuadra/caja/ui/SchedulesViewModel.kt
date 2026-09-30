package com.cuadra.caja.ui

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cuadra.caja.AppContainer
import com.cuadra.caja.R
import com.cuadra.caja.data.local.BusinessEntity
import com.cuadra.caja.data.local.MemberEntity
import com.cuadra.caja.data.remote.ApiFailure
import com.cuadra.caja.data.remote.ScheduleDto
import com.cuadra.caja.data.remote.ScheduleRunDto
import com.cuadra.caja.domain.DraftError
import com.cuadra.caja.domain.ScheduleDraft
import com.cuadra.caja.domain.ScheduleDrafts
import com.cuadra.caja.domain.ScheduleWhen
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SchedulesUi(
    val loading: Boolean = true,
    val schedules: List<ScheduleDto> = emptyList(),
    /** Error de carga (sin conexión u otro): se muestra con "Reintentar". */
    val loadError: ErrorMessage? = null,
    val editor: ScheduleDraft? = null,
    val editorError: DraftError? = null,
    val saveError: ErrorMessage? = null,
    val saving: Boolean = false,
    val deleteId: String? = null,
    val historyFor: ScheduleDto? = null,
    val history: List<ScheduleRunDto>? = null,
    val message: ErrorMessage? = null,
)

/** Plantillas rápidas (título, mensaje): los textos viven en recursos. */
val SCHEDULE_TEMPLATES: List<Pair<Int, Int>> = listOf(
    R.string.sched_tpl1_title to R.string.sched_tpl1_body,
    R.string.sched_tpl2_title to R.string.sched_tpl2_body,
    R.string.sched_tpl3_title to R.string.sched_tpl3_body,
    R.string.sched_tpl4_title to R.string.sched_tpl4_body,
)

/** Pantalla "Programar avisos": gestión con conexión (dueño y admins). Nada se guarda en el teléfono. */
class SchedulesViewModel(private val c: AppContainer) : ViewModel(), SchedulesActions {
    private val _ui = MutableStateFlow(SchedulesUi())
    val ui: StateFlow<SchedulesUi> = _ui.asStateFlow()

    val members: StateFlow<List<MemberEntity>> = c.db.directory().activeMembers().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val business: StateFlow<BusinessEntity?> = c.db.directory().business().stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        load()
    }

    private fun Throwable?.errorRes(): ErrorMessage = when (this) {
        is ApiFailure.Offline -> ErrorMessage(R.string.sched_err_offline)
        is ApiFailure.Http -> when (code) {
            "INVALID_TITLE" -> ErrorMessage(R.string.sched_err_title)
            "INVALID_BODY" -> ErrorMessage(R.string.sched_err_body)
            "INVALID_AUDIENCE" -> ErrorMessage(R.string.sched_err_audience)
            "INVALID_RULE" -> ErrorMessage(R.string.sched_err_rule)
            "RULE_ENDED" -> ErrorMessage(R.string.sched_err_ended)
            "INVALID_LINK" -> ErrorMessage(R.string.sched_err_link)
            "SCHEDULE_LIMIT" -> ErrorMessage(R.string.sched_err_limit)
            "FORBIDDEN" -> ErrorMessage(R.string.sched_err_forbidden)
            "PLAN_LIMIT", "BUSINESS_SUSPENDED" -> errorMessage()
            else -> ErrorMessage(R.string.sched_err_generic)
        }
        else -> ErrorMessage(R.string.sched_err_generic)
    }

    override fun load() {
        _ui.update { it.copy(loading = true, loadError = null) }
        viewModelScope.launch {
            c.schedules.list().fold(
                onSuccess = { list -> _ui.update { it.copy(loading = false, schedules = list) } },
                onFailure = { e -> _ui.update { it.copy(loading = false, loadError = e.errorRes()) } },
            )
        }
    }

    override fun dismissMessage() = _ui.update { it.copy(message = null) }

    // ---------- editor ----------
    override fun openNew() = _ui.update { it.copy(editor = ScheduleDraft(), editorError = null, saveError = null) }
    override fun edit(s: ScheduleDto) = _ui.update { it.copy(editor = ScheduleDrafts.draftOf(s), editorError = null, saveError = null) }
    override fun updateEditor(d: ScheduleDraft) = _ui.update { it.copy(editor = d, editorError = null, saveError = null) }
    override fun closeEditor() = _ui.update { it.copy(editor = null, editorError = null, saveError = null) }

    override fun applyTemplate(title: String, body: String) {
        val d = _ui.value.editor ?: return
        updateEditor(d.copy(title = title, body = body))
    }

    /** Programa (Una vez o Repetir) o envía al instante (Ahora), según el borrador. */
    override fun save() {
        val d = _ui.value.editor ?: return
        val error = ScheduleDrafts.validate(d)
        if (error != null) { _ui.update { it.copy(editorError = error) }; return }
        val id = d.id ?: UUID.randomUUID().toString()
        val input = ScheduleDrafts.toInput(d)
        _ui.update { it.copy(saving = true, saveError = null) }
        viewModelScope.launch {
            val result = if (d.whenMode == ScheduleWhen.NOW) c.schedules.sendNow(if (d.id == null) id else UUID.randomUUID().toString(), input) else c.schedules.save(id, input)
            result.fold(
                onSuccess = {
                    _ui.update { it.copy(saving = false, editor = null, message = if (d.whenMode == ScheduleWhen.NOW) ErrorMessage(R.string.sched_sent_now) else null) }
                    load()
                },
                onFailure = { e -> _ui.update { it.copy(saving = false, saveError = e.errorRes()) } },
            )
        }
    }

    // ---------- acciones sobre una programación ----------
    override fun toggle(s: ScheduleDto) {
        viewModelScope.launch {
            c.schedules.setActive(s.id, !s.active).fold(
                onSuccess = { load() },
                onFailure = { e -> _ui.update { it.copy(message = e.errorRes()) } },
            )
        }
    }

    /** Copia con id nuevo y título + `suffix` (el texto viene de recursos). */
    override fun duplicate(s: ScheduleDto, suffix: String) {
        val draft = ScheduleDrafts.draftOf(s).copy(id = null, title = (s.title + suffix).take(ScheduleDrafts.TITLE_MAX))
        viewModelScope.launch {
            c.schedules.save(UUID.randomUUID().toString(), ScheduleDrafts.toInput(draft)).fold(
                onSuccess = { load() },
                onFailure = { e -> _ui.update { it.copy(message = e.errorRes()) } },
            )
        }
    }

    override fun askDelete(id: String) = _ui.update { it.copy(deleteId = id) }
    override fun cancelDelete() = _ui.update { it.copy(deleteId = null) }

    override fun confirmDelete() {
        val id = _ui.value.deleteId ?: return
        _ui.update { it.copy(deleteId = null) }
        viewModelScope.launch {
            c.schedules.delete(id).fold(
                onSuccess = { load() },
                onFailure = { e -> _ui.update { it.copy(message = e.errorRes()) } },
            )
        }
    }

    override fun openHistory(s: ScheduleDto) {
        _ui.update { it.copy(historyFor = s, history = null) }
        viewModelScope.launch {
            c.schedules.runs(s.id).fold(
                onSuccess = { runs -> _ui.update { st -> if (st.historyFor?.id == s.id) st.copy(history = runs) else st } },
                onFailure = { e -> _ui.update { it.copy(historyFor = null, message = e.errorRes()) } },
            )
        }
    }

    override fun closeHistory() = _ui.update { it.copy(historyFor = null, history = null) }
}

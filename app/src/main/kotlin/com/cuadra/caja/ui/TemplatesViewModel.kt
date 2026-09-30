package com.cuadra.caja.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cuadra.caja.AppContainer
import com.cuadra.caja.R
import com.cuadra.caja.data.local.BusinessEntity
import com.cuadra.caja.data.local.TemplateEntity
import com.cuadra.caja.data.remote.ApiFailure
import com.cuadra.caja.domain.MessageKind
import com.cuadra.caja.domain.TemplateEditor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TemplatesUi(
    val kind: MessageKind = MessageKind.REMINDER,
    val locale: String = "es",
    /** Texto en edición por «tipo:idioma»: lo que se escribió y aún no se guardó. */
    val drafts: Map<String, String> = emptyMap(),
    val loading: Boolean = true,
    val offline: Boolean = false,
    val saving: Boolean = false,
    val notice: TeamNotice? = null,
    val confirmReset: Boolean = false,
) {
    val key: String get() = "${kind.name}:$locale"
}

interface TemplatesActions {
    fun enter() {}
    fun kind(k: MessageKind) {}
    fun locale(l: String) {}
    fun edit(text: String) {}
    fun insert(variable: String) {}
    fun save() {}
    fun askReset() {}
    fun cancelReset() {}
    fun reset() {}
    fun dismissNotice() {}
}

/** «Mensajes de WhatsApp» (dueño y admin): lo guardado se refleja en Room, de donde la hoja de compartir toma el texto de cada mensaje. */
class TemplatesViewModel(private val c: AppContainer) : ViewModel(), TemplatesActions {
    private val _ui = MutableStateFlow(TemplatesUi(locale = if (java.util.Locale.getDefault().language == "en") "en" else "es"))
    val ui: StateFlow<TemplatesUi> = _ui.asStateFlow()
    val stored: StateFlow<List<TemplateEntity>> = c.templates.all().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val business: StateFlow<BusinessEntity?> = c.db.directory().business().stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun storedFor(kind: MessageKind, locale: String): String? = stored.value.firstOrNull { it.kind == kind.name && it.locale == locale }?.body

    override fun enter() {
        _ui.update { it.copy(loading = true, notice = null, confirmReset = false) }
        viewModelScope.launch {
            c.templates.refresh().fold(
                onSuccess = { _ui.update { it.copy(loading = false, offline = false) } },
                onFailure = { e -> _ui.update { it.copy(loading = false, offline = e is ApiFailure.Offline, notice = if (e is ApiFailure.Offline) null else TeamNotice(e.settingsError(), true)) } },
            )
        }
    }

    override fun kind(k: MessageKind) = _ui.update { it.copy(kind = k, notice = null, confirmReset = false) }
    override fun locale(l: String) = _ui.update { it.copy(locale = l, notice = null, confirmReset = false) }

    /** El texto que se ve en el editor: lo que se está escribiendo o, sin cambios, el que rige hoy (el propio o el de fábrica). */
    fun textOf(s: TemplatesUi): String = s.drafts[s.key] ?: TemplateEditor.effective(storedFor(s.kind, s.locale), s.kind, s.locale)

    override fun edit(text: String) = _ui.update { it.copy(drafts = it.drafts + (it.key to text.take(TemplateEditor.BODY_MAX + 200)), notice = null) }
    override fun insert(variable: String) = edit(textOf(_ui.value).let { t -> if (t.isEmpty() || t.endsWith(" ") || t.endsWith("\n")) "$t{$variable}" else "$t {$variable}" })

    override fun save() {
        val s = _ui.value
        val text = textOf(s).trim()
        if (s.saving || !TemplateEditor.canSave(text, storedFor(s.kind, s.locale), s.kind, s.locale)) return
        _ui.update { it.copy(saving = true, notice = null) }
        viewModelScope.launch {
            c.templates.save(s.kind.name, s.locale, text).fold(
                onSuccess = { _ui.update { st -> st.copy(saving = false, drafts = st.drafts - s.key, notice = TeamNotice(ErrorMessage(R.string.tpl_saved), false)) } },
                onFailure = { e -> _ui.update { it.copy(saving = false, notice = TeamNotice(e.settingsError(), true)) } },
            )
        }
    }

    override fun askReset() = _ui.update { it.copy(confirmReset = true) }
    override fun cancelReset() = _ui.update { it.copy(confirmReset = false) }

    override fun reset() {
        val s = _ui.value
        if (s.saving) return
        _ui.update { it.copy(saving = true, confirmReset = false, notice = null) }
        viewModelScope.launch {
            c.templates.reset(s.kind.name, s.locale).fold(
                onSuccess = { _ui.update { st -> st.copy(saving = false, drafts = st.drafts - s.key, notice = TeamNotice(ErrorMessage(R.string.tpl_reset_done), false)) } },
                onFailure = { e -> _ui.update { it.copy(saving = false, notice = TeamNotice(e.settingsError(), true)) } },
            )
        }
    }

    override fun dismissNotice() = _ui.update { it.copy(notice = null) }
}

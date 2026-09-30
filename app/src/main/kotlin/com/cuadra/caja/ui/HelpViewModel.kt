package com.cuadra.caja.ui

import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cuadra.caja.AppContainer
import com.cuadra.caja.BuildConfig
import com.cuadra.caja.domain.Diagnostics
import com.cuadra.caja.domain.SupportRules
import com.cuadra.caja.domain.TicketDraft
import com.cuadra.caja.domain.TicketError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class HelpTab { FAQ, WRITE }

data class HelpUi(
    val tab: HelpTab = HelpTab.FAQ,
    /** Preguntas frecuentes abiertas (por posición). */
    val expanded: Set<Int> = emptySet(),
    val draft: TicketDraft = TicketDraft(),
    val diagnostics: Diagnostics? = null,
    val validation: TicketError? = null,
    val sending: Boolean = false,
    val error: ErrorMessage? = null,
    /** Referencia del mensaje enviado («#1a2b3c4d»); no nulo = se muestra el agradecimiento. */
    val reference: String? = null,
)

interface HelpActions {
    fun enter() {}
    fun tab(t: HelpTab) {}
    fun toggle(index: Int) {}
    fun update(d: TicketDraft) {}
    fun send() {}
    fun another() {}
}

/** «Ayuda y contacto»: preguntas frecuentes y el formulario para escribir a soporte (con datos técnicos opcionales, sin tokens ni PIN). */
class HelpViewModel(private val c: AppContainer) : ViewModel(), HelpActions {
    private val _ui = MutableStateFlow(HelpUi())
    val ui: StateFlow<HelpUi> = _ui.asStateFlow()
    val supportEmail: StateFlow<String?> = c.appConfig.state.map { it.supportEmail }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    override fun enter() {
        viewModelScope.launch { _ui.update { it.copy(diagnostics = diagnostics()) } }
    }

    private suspend fun diagnostics(): Diagnostics {
        val s = c.sessionStore.current()
        val pending = runCatching { c.db.outbox().pendingCountNow() }.getOrDefault(0)
        return Diagnostics(
            appVersion = BuildConfig.VERSION_NAME, androidVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            deviceModel = listOfNotNull(Build.MANUFACTURER, Build.MODEL).joinToString(" "), businessId = s.businessId,
            lastSyncAt = c.sync.lastDoneAt?.let { java.time.Instant.ofEpochMilli(it).toString() }, pendingOps = pending, language = java.util.Locale.getDefault().language,
        )
    }

    override fun tab(t: HelpTab) = _ui.update { it.copy(tab = t) }
    override fun toggle(index: Int) = _ui.update { it.copy(expanded = if (index in it.expanded) it.expanded - index else it.expanded + index) }
    override fun update(d: TicketDraft) = _ui.update { it.copy(draft = d, validation = null, error = null) }
    override fun another() = _ui.update { it.copy(reference = null, draft = it.draft.copy(message = ""), error = null) }

    override fun send() {
        val s = _ui.value
        if (s.sending) return
        SupportRules.validate(s.draft)?.let { err -> _ui.update { it.copy(validation = err) }; return }
        _ui.update { it.copy(sending = true, error = null) }
        viewModelScope.launch {
            val session = c.sessionStore.current()
            val diag = diagnostics()
            val body = SupportRules.body(s.draft, session.businessId, java.util.Locale.getDefault().language, diag)
            c.support.send(body).fold(
                onSuccess = { r -> _ui.update { it.copy(sending = false, reference = r.reference, diagnostics = diag) } },
                onFailure = { e -> _ui.update { it.copy(sending = false, error = e.settingsError()) } },
            )
        }
    }
}

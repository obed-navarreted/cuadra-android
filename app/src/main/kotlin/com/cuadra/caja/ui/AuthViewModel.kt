package com.cuadra.caja.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cuadra.caja.AppContainer
import com.cuadra.caja.data.local.MemberEntity
import com.cuadra.caja.data.remote.LinkRequestCreatedDto
import com.cuadra.caja.data.remote.MeDto
import com.cuadra.caja.data.repo.UnlockResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuthUi(
    val busy: Boolean = false,
    val errorRes: ErrorMessage? = null,
    val me: MeDto? = null,
    /** Código de vinculación en pantalla y si ya venció. */
    val link: LinkRequestCreatedDto? = null,
    val linkExpired: Boolean = false,
)

/** Entrada con Google, negocio nuevo o existente, y vinculación de teléfono por código. */
class AuthViewModel(private val c: AppContainer) : ViewModel() {
    private val _ui = MutableStateFlow(AuthUi())
    val ui: StateFlow<AuthUi> = _ui.asStateFlow()
    private var polling: Job? = null

    fun googleSignedIn(idToken: String) = run {
        c.auth.signInWithGoogle(idToken).fold({ me -> _ui.update { it.copy(me = me) } }, { e -> fail(e) })
    }

    fun loadMe() = run { c.auth.me().fold({ me -> _ui.update { it.copy(me = me) } }, { e -> fail(e) }) }

    fun createBusiness(name: String) = run { c.auth.createBusiness(name).fold({ }, { e -> fail(e) }) }

    fun useBusiness(businessId: String) = run { c.auth.linkThisPhone(businessId).fold({ }, { e -> fail(e) }) }

    fun showError(res: Int) = _ui.update { it.copy(errorRes = ErrorMessage(res), busy = false) }

    fun startLinkCode() {
        polling?.cancel()
        _ui.update { it.copy(busy = true, errorRes = null, link = null, linkExpired = false) }
        polling = viewModelScope.launch {
            val created = c.auth.requestLinkCode().getOrElse { e -> fail(e); return@launch }
            _ui.update { it.copy(busy = false, link = created) }
            val expires = java.time.Instant.parse(created.expiresAt).toEpochMilli()
            while (System.currentTimeMillis() < expires) {
                delay(3_000)
                val linked = c.auth.pollLink(created.code, created.pollSecret).getOrDefault(false)
                if (linked) return@launch    // la sesión cambia y la pantalla raíz avanza sola
            }
            _ui.update { it.copy(linkExpired = true) }
        }
    }

    fun stopPolling() {
        polling?.cancel()
    }

    private fun run(block: suspend () -> Unit) {
        _ui.update { it.copy(busy = true, errorRes = null) }
        viewModelScope.launch {
            block()
            _ui.update { it.copy(busy = false) }
        }
    }

    private fun fail(e: Throwable) = _ui.update { it.copy(errorRes = e.errorMessage(), busy = false) }

    override fun onCleared() {
        polling?.cancel()
    }
}

data class PinUi(
    val selected: MemberEntity? = null,
    val pin: String = "",
    val busy: Boolean = false,
    /** null = sin mensaje; -1 = PIN incorrecto; -2 = sin PIN; >0 = milisegundos de espera. */
    val lockedMillis: Long? = null,
    val wrong: Boolean = false,
    val noPin: Boolean = false,
    /** Alguien entró con Google en este teléfono y puede crear su PIN. */
    val creating: Boolean = false,
    val errorRes: ErrorMessage? = null,
)

class PinViewModel(private val c: AppContainer) : ViewModel() {
    val members: StateFlow<List<MemberEntity>> = c.db.directory().activeMembers().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private val _ui = MutableStateFlow(PinUi())
    val ui: StateFlow<PinUi> = _ui.asStateFlow()

    init {
        viewModelScope.launch { c.auth.loadDirectory() }
    }

    fun select(member: MemberEntity, canCreatePin: Boolean) {
        _ui.value = PinUi(selected = member, noPin = !member.pinSet && !canCreatePin, creating = !member.pinSet && canCreatePin)
    }

    fun back() {
        _ui.value = PinUi()
    }

    fun digit(d: Char) = _ui.update { if (it.pin.length < 6) it.copy(pin = it.pin + d, wrong = false) else it }
    fun backspace() = _ui.update { it.copy(pin = it.pin.dropLast(1), wrong = false) }

    fun submit() {
        val state = _ui.value
        val member = state.selected ?: return
        if (state.pin.length < 4 || state.busy) return
        _ui.update { it.copy(busy = true, errorRes = null) }
        viewModelScope.launch {
            if (state.creating) {
                c.auth.setOwnPin(state.pin, member.id).fold(
                    onSuccess = { c.auth.loadDirectory(); handle(c.auth.unlock(member.id, state.pin)) },
                    onFailure = { e -> _ui.update { it.copy(busy = false, errorRes = e.errorMessage(), pin = "") } },
                )
            } else {
                handle(c.auth.unlock(member.id, state.pin))
            }
        }
    }

    private fun handle(r: UnlockResult) = when (r) {
        is UnlockResult.Ok -> _ui.value = PinUi()    // la sesión cambia y la pantalla raíz avanza sola
        UnlockResult.WrongPin -> _ui.update { it.copy(busy = false, pin = "", wrong = true, lockedMillis = null) }
        is UnlockResult.Locked -> _ui.update { it.copy(busy = false, pin = "", wrong = false, lockedMillis = r.waitMillis) }
        UnlockResult.NoPin -> _ui.update { it.copy(busy = false, pin = "", noPin = true) }
    }

    fun signOut() {
        viewModelScope.launch { c.auth.signOut() }
    }
}

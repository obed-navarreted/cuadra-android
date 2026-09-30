package com.cuadra.caja.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cuadra.caja.AppContainer
import com.cuadra.caja.data.local.MemberEntity
import com.cuadra.caja.data.remote.MeDto
import com.cuadra.caja.data.repo.UnlockResult
import com.cuadra.caja.domain.AccessCode
import com.cuadra.caja.domain.MemberLoginForm
import com.cuadra.caja.domain.NewPinEntry
import com.cuadra.caja.domain.PinRules
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
    /** Países para elegir al crear el negocio (del servidor; sin conexión, la lista de la app). */
    val countries: List<com.cuadra.caja.domain.CountryOption> = com.cuadra.caja.domain.CountryChoice.FALLBACK,
)

/** Entrada con Google (el dueño): negocio nuevo o existente para este teléfono. */
class AuthViewModel(private val c: AppContainer) : ViewModel() {
    private val _ui = MutableStateFlow(AuthUi())
    val ui: StateFlow<AuthUi> = _ui.asStateFlow()

    fun googleSignedIn(idToken: String) = run {
        c.auth.signInWithGoogle(idToken).fold({ me -> _ui.update { it.copy(me = me) } }, { e -> fail(e) })
    }

    fun loadMe() = run {
        c.auth.me().fold({ me -> _ui.update { it.copy(me = me) } }, { e -> fail(e) })
        val list = c.auth.countries()
        _ui.update { it.copy(countries = list) }
    }

    fun createBusiness(name: String, origin: com.cuadra.caja.domain.BusinessOrigin) = run {
        c.auth.createBusiness(name, origin.timezone, origin.country, origin.currency).fold({ }, { e -> fail(e) })
    }

    fun useBusiness(businessId: String) = run { c.auth.linkThisPhone(businessId).fold({ }, { e -> fail(e) }) }

    fun showError(res: Int) = _ui.update { it.copy(errorRes = ErrorMessage(res), busy = false) }

    fun showError(message: ErrorMessage) = _ui.update { it.copy(errorRes = message, busy = false) }

    private fun run(block: suspend () -> Unit) {
        _ui.update { it.copy(busy = true, errorRes = null) }
        viewModelScope.launch {
            block()
            _ui.update { it.copy(busy = false) }
        }
    }

    private fun fail(e: Throwable) = _ui.update { it.copy(errorRes = e.errorMessage(), busy = false) }
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

    /**
     * Al escribir el 5.º número se entra solo (sin botón). Sin persona elegida es «Escribe tu PIN»: el PIN dice quién atiende (el camino principal);
     * elegir el nombre de la lista es el secundario.
     */
    fun digit(d: Char) {
        _ui.update { if (it.pin.length < PinRules.LENGTH && !it.busy && !it.noPin) it.copy(pin = it.pin + d, wrong = false) else it }
        if (_ui.value.pin.length == PinRules.LENGTH && !_ui.value.creating) submit()
    }
    fun backspace() = _ui.update { it.copy(pin = it.pin.dropLast(1), wrong = false) }

    fun submit() {
        val state = _ui.value
        if (!PinRules.isValid(state.pin) || state.busy) return
        val member = state.selected
        if (member == null) {
            _ui.update { it.copy(busy = true, errorRes = null) }
            viewModelScope.launch { handle(c.auth.unlockByPin(state.pin)) }
            return
        }
        _ui.update { it.copy(busy = true, errorRes = null) }
        viewModelScope.launch {
            if (state.creating) {
                c.auth.setOwnPin(state.pin, member.id).fold(
                    onSuccess = { c.auth.loadDirectory(); handle(c.auth.unlock(member.id, state.pin)) },
                    // PIN_TAKEN: el PIN es de otra persona del negocio.
                    onFailure = { e -> _ui.update { it.copy(busy = false, errorRes = e.teamError(), pin = "") } },
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

/** Lo que la pantalla «Entrar con el código del negocio» le pide al ViewModel. Cuerpos vacíos por omisión: la guardia usa `object : MemberLoginActions {}`. */
interface MemberLoginActions {
    fun setCode(raw: String) {}
    fun digit(d: Char) {}
    fun backspace() {}
    fun submit() {}
}

data class MemberLoginUi(val form: MemberLoginForm = MemberLoginForm(), val busy: Boolean = false, val error: ErrorMessage? = null)

/**
 * Entrada del equipo (ADR 0012): código del negocio + PIN (el PIN dice quién es). Al lograrlo la sesión cambia (teléfono vinculado y persona activa) y la pantalla
 * raíz avanza sola: a la caja, o a «Elige tu PIN nuevo» si el dueño puso un PIN que debe cambiarse.
 */
class MemberLoginViewModel(private val c: AppContainer) : ViewModel(), MemberLoginActions {
    private val _ui = MutableStateFlow(MemberLoginUi())
    val ui: StateFlow<MemberLoginUi> = _ui.asStateFlow()

    init {
        // Se acuerda del último código usado en este teléfono.
        viewModelScope.launch {
            val last = c.sessionStore.lastBusinessCode()?.let(AccessCode::sanitize).orEmpty()
            if (last.isNotEmpty()) _ui.update { if (it.form.code.isEmpty()) it.copy(form = it.form.withCode(last)) else it }
        }
    }

    private fun edit(f: (MemberLoginForm) -> MemberLoginForm) = _ui.update { if (it.busy) it else it.copy(form = f(it.form), error = null) }

    override fun setCode(raw: String) = edit { it.withCode(raw) }
    override fun backspace() = edit { it.backspace() }

    override fun digit(d: Char) {
        val before = _ui.value.form
        edit { it.digit(d) }
        if (!_ui.value.busy && before.autoSubmits(_ui.value.form)) submit()
    }

    override fun submit() {
        val s = _ui.value
        if (s.busy || !s.form.ready) return
        _ui.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            c.auth.memberLogin(s.form.code, s.form.pin).fold(
                // La sesión cambia y la pantalla raíz avanza sola.
                onSuccess = { _ui.update { it.copy(busy = false, form = it.form.copy(pin = "")) } },
                onFailure = { e -> _ui.update { it.copy(busy = false, form = it.form.copy(pin = ""), error = e.loginError()) } },
            )
        }
    }
}

interface ChangePinActions {
    fun digit(d: Char) {}
    fun backspace() {}
    fun submit() {}
}

data class ChangePinUi(val entry: NewPinEntry = NewPinEntry(), val busy: Boolean = false, val error: ErrorMessage? = null)

/** «Elige tu PIN nuevo»: quien entró con un PIN puesto por el dueño elige el suyo (5 números, dos veces) antes de usar la caja. */
class ChangePinViewModel(private val c: AppContainer) : ViewModel(), ChangePinActions {
    private val _ui = MutableStateFlow(ChangePinUi())
    val ui: StateFlow<ChangePinUi> = _ui.asStateFlow()

    override fun digit(d: Char) {
        _ui.update { if (it.busy) it else it.copy(entry = it.entry.digit(d), error = null) }
        if (_ui.value.entry.done) submit()
    }

    override fun backspace() = _ui.update { if (it.busy) it else it.copy(entry = it.entry.backspace(), error = null) }

    override fun submit() {
        val s = _ui.value
        if (s.busy || !s.entry.done) return
        _ui.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            c.auth.finishPinChange(s.entry.first).fold(
                onSuccess = { _ui.update { ChangePinUi() } },    // la sesión cambia y la pantalla raíz avanza sola
                onFailure = { e -> _ui.update { it.copy(busy = false, entry = NewPinEntry(), error = e.teamError()) } },
            )
        }
    }

    fun signOut() {
        viewModelScope.launch { c.auth.signOut() }
    }
}

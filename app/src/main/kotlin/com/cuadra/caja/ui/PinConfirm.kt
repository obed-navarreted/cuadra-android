package com.cuadra.caja.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cuadra.caja.AppContainer
import com.cuadra.caja.data.repo.ConfirmPinResult
import com.cuadra.caja.domain.PinRules
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** «Confirmar PIN» con el servidor (ADR 0012, 2026-10-01). `offline`: «Conéctate para confirmar tu PIN la primera vez en este teléfono». */
data class PinConfirmUi(
    val pin: String = "",
    val busy: Boolean = false,
    val wrong: Boolean = false,
    val lockedMillis: Long? = null,
    val offline: Boolean = false,
    val failed: Boolean = false,
)

/**
 * El teclado de «Confirmar PIN»: al 5.º número pregunta al servidor (como en la pantalla de PIN, sin botón). Lo usan la pantalla que pide confirmar el PIN
 * (administración) y «Requiere atención». `onOk` corre al confirmarse.
 */
class PinConfirmModel(
    private val scope: CoroutineScope,
    private val memberId: () -> String?,
    private val confirm: suspend (String, String) -> ConfirmPinResult,
    private val onOk: suspend () -> Unit = {},
) {
    private val _ui = MutableStateFlow(PinConfirmUi())
    val ui: StateFlow<PinConfirmUi> = _ui.asStateFlow()

    fun reset() { _ui.value = PinConfirmUi() }

    /** Antes de escribir: sin conexión ya se avisa (el servidor no se puede consultar). */
    fun setOffline(offline: Boolean) = _ui.update { if (it.busy) it else it.copy(offline = offline) }

    fun digit(d: Char) {
        _ui.update { if (it.busy || it.pin.length >= PinRules.LENGTH) it else it.copy(pin = it.pin + d, wrong = false, failed = false) }
        if (_ui.value.pin.length == PinRules.LENGTH && !_ui.value.busy) submit()
    }

    fun backspace() = _ui.update { if (it.busy) it else it.copy(pin = it.pin.dropLast(1), wrong = false) }

    fun submit() {
        val s = _ui.value
        val who = memberId() ?: return
        if (s.busy || !PinRules.isValid(s.pin)) return
        _ui.update { it.copy(busy = true, wrong = false, failed = false, lockedMillis = null) }
        scope.launch {
            when (val r = confirm(who, s.pin)) {
                ConfirmPinResult.Ok -> { _ui.value = PinConfirmUi(); onOk() }
                ConfirmPinResult.WrongPin -> _ui.update { it.copy(busy = false, pin = "", wrong = true) }
                is ConfirmPinResult.Locked -> _ui.update { it.copy(busy = false, pin = "", lockedMillis = r.waitMillis) }
                ConfirmPinResult.Offline -> _ui.update { it.copy(busy = false, pin = "", offline = true) }
                ConfirmPinResult.Failed -> _ui.update { it.copy(busy = false, pin = "", failed = true) }
            }
        }
    }
}

/** Lo que dibuja la pantalla «Confirma tu PIN». Cuerpos vacíos por omisión: la guardia usa `object : PinConfirmActions {}`. */
interface PinConfirmActions {
    fun digit(d: Char) {}
    fun backspace() {}
}

/** La pantalla que reemplaza a una de administración mientras la persona activa no confirma su PIN en este teléfono. */
class PinConfirmViewModel(private val c: AppContainer) : ViewModel(), PinConfirmActions {
    private val session = c.sessionStore.flow.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    private val model = PinConfirmModel(viewModelScope, { session.value?.memberId }, c.auth::confirmPin)
    val ui: StateFlow<PinConfirmUi> = model.ui

    fun setOffline(offline: Boolean) = model.setOffline(offline)
    override fun digit(d: Char) = model.digit(d)
    override fun backspace() = model.backspace()
}

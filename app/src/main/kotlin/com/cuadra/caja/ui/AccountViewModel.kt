package com.cuadra.caja.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cuadra.caja.AppContainer
import com.cuadra.caja.R
import com.cuadra.caja.data.repo.UnlockResult
import com.cuadra.caja.domain.AccountDraft
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AccountUi(
    val loaded: Boolean = false,
    val draft: AccountDraft = AccountDraft(""),
    val saving: Boolean = false,
    val error: ErrorMessage? = null,
    /** Espera restante por demasiados PIN actuales equivocados (mismo bloqueo que la pantalla de PIN). */
    val lockedMillis: Long? = null,
    val saved: Boolean = false,
    /** Entró con Google en este teléfono: puede eliminar su cuenta (si no es dueño de un negocio activo). Sin Google, es un usuario del equipo. */
    val hasGoogle: Boolean = false,
    /** Negocios activos de los que es dueño (mientras haya alguno, primero hay que eliminarlos). Nulo = aún no se sabe (sin conexión). */
    val ownedBusinesses: List<String>? = null,
    val deleteOpen: Boolean = false, val deleteTyped: String = "", val deleting: Boolean = false, val deleteError: ErrorMessage? = null,
    /** Se eliminó: la app cierra sesión. */
    val accountDeleted: Boolean = false,
)

/** «Mi cuenta»: cualquier persona (cajero incluido) cambia su propio nombre y su PIN. El PIN actual se comprueba en el teléfono primero. */
class AccountViewModel(private val c: AppContainer) : ViewModel(), AccountActions {
    private val _ui = MutableStateFlow(AccountUi())
    val ui: StateFlow<AccountUi> = _ui.asStateFlow()

    /** Vuelve a leer quién soy (nombre y si ya tengo PIN); se llama al abrir la pantalla. */
    fun load() {
        viewModelScope.launch {
            val id = c.sessionStore.current().memberId ?: return@launch
            val m = c.db.directory().member(id) ?: return@launch
            val google = c.sessionStore.current().userToken != null
            _ui.update { if (it.loaded && it.saving) it else AccountUi(loaded = true, draft = AccountDraft(originalName = m.displayName, hasPin = m.pinSet), hasGoogle = google) }
            if (google) c.auth.me().onSuccess { me -> _ui.update { it.copy(ownedBusinesses = me.businesses.filter { b -> b.role == "OWNER" }.map { b -> b.businessName }) } }
        }
    }

    override fun openDeleteAccount() = _ui.update { if (it.hasGoogle) it.copy(deleteOpen = true, deleteTyped = "", deleteError = null) else it }
    override fun typeDeleteWord(text: String) = _ui.update { it.copy(deleteTyped = text.take(20), deleteError = null) }
    override fun closeDeleteAccount() = _ui.update { if (it.deleting) it else it.copy(deleteOpen = false, deleteTyped = "", deleteError = null) }

    /** Elimina la cuenta en el servidor (necesita conexión). OWNS_BUSINESSES: todavía es dueño de un negocio; se explica y se ofrece eliminarlo. */
    override fun confirmDeleteAccount() {
        val s = _ui.value
        if (!s.hasGoogle || s.deleting || !s.ownedBusinesses.isNullOrEmpty()) return
        _ui.update { it.copy(deleting = true, deleteError = null) }
        viewModelScope.launch {
            c.auth.deleteAccount().fold(
                onSuccess = { _ui.update { it.copy(deleting = false, deleteOpen = false, accountDeleted = true) } },
                onFailure = { e ->
                    val msg = when {
                        e is com.cuadra.caja.data.remote.ApiFailure.Offline -> ErrorMessage(R.string.account_delete_offline)
                        e is com.cuadra.caja.data.remote.ApiFailure.Http && e.code == "OWNS_BUSINESSES" -> ErrorMessage(R.string.account_owns_title)
                        else -> e.errorMessage()
                    }
                    _ui.update { it.copy(deleting = false, deleteError = msg) }
                },
            )
        }
    }

    override fun update(d: AccountDraft) = _ui.update { it.copy(draft = d, error = null, lockedMillis = null, saved = false) }

    override fun save() {
        val d = _ui.value.draft
        if (!d.ready || _ui.value.saving) return
        _ui.update { it.copy(saving = true, error = null, lockedMillis = null, saved = false) }
        viewModelScope.launch {
            val id = c.sessionStore.current().memberId
            if (id == null) { _ui.update { it.copy(saving = false, error = ErrorMessage(R.string.error_generic)) }; return@launch }
            // 1) el PIN actual se comprueba aquí, con el hash guardado, antes de molestar al servidor.
            if (d.wantsPin && d.hasPin) {
                var r = c.auth.verifyPin(id, d.current)
                if (r is UnlockResult.NoPin) { c.auth.loadDirectory(); r = c.auth.verifyPin(id, d.current) }
                when (r) {
                    is UnlockResult.Ok -> Unit
                    UnlockResult.WrongPin -> return@launch fail(ErrorMessage(R.string.pin_wrong), clearCurrent = true)
                    is UnlockResult.Locked -> return@launch _ui.update { it.copy(saving = false, lockedMillis = r.waitMillis, draft = it.draft.copy(current = "")) }
                    UnlockResult.NoPin -> return@launch fail(ErrorMessage(R.string.account_err_unverifiable))
                }
            }
            // 2) el nombre y 3) el PIN. Si el nombre ya quedó y el PIN falla, el nombre no se pierde.
            var current = d
            if (d.nameChanged) {
                c.team.update(id, d.cleanName, null, null, null).fold(
                    onSuccess = { current = current.copy(originalName = d.cleanName, name = d.cleanName); _ui.update { it.copy(draft = current) } },
                    onFailure = { e -> return@launch fail(e.teamError()) },
                )
            }
            if (d.wantsPin) {
                c.team.resetPin(id, d.pin, false).onFailure { e -> return@launch fail(e.teamError()) }
                current = current.copy(hasPin = true, current = "", pin = "", confirm = "")
            }
            _ui.update { it.copy(saving = false, draft = current, saved = true) }
        }
    }

    private fun fail(e: ErrorMessage, clearCurrent: Boolean = false) = _ui.update {
        it.copy(saving = false, error = e, draft = if (clearCurrent) it.draft.copy(current = "") else it.draft)
    }
}

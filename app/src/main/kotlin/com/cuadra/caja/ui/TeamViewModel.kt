package com.cuadra.caja.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cuadra.caja.AppContainer
import com.cuadra.caja.R
import com.cuadra.caja.data.local.MemberEntity
import com.cuadra.caja.data.remote.ApiFailure
import com.cuadra.caja.domain.AccessCode
import com.cuadra.caja.domain.PinRules
import com.cuadra.caja.domain.TeamRules
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ConfirmKind { MAKE_ADMIN, MAKE_CASHIER, DISABLE, ENABLE }

/** Un resultado que se muestra sobre la lista: lo que salió bien o el error de una acción directa. */
data class TeamNotice(val text: ErrorMessage, val isError: Boolean)

sealed interface TeamDialog {
    val error: ErrorMessage?

    data class Edit(val member: MemberEntity, val name: String, val color: String?, override val error: ErrorMessage? = null) : TeamDialog
    data class Confirm(val member: MemberEntity, val kind: ConfirmKind, override val error: ErrorMessage? = null) : TeamDialog
    data class Pin(val member: MemberEntity, val pin: String = "", val confirm: String = "", val mustChange: Boolean = true, override val error: ErrorMessage? = null) : TeamDialog
    data class New(
        val name: String = "", val role: String = TeamRules.CASHIER, val pin: String = "", val confirm: String = "", val mustChange: Boolean = true,
        override val error: ErrorMessage? = null,
    ) : TeamDialog

    /** ¿Renovar el código del negocio? (solo el dueño; los teléfonos ya vinculados siguen funcionando). */
    data class RenewCode(override val error: ErrorMessage? = null) : TeamDialog

    /** Elegir un código propio de 5 números (solo el dueño). */
    data class ChooseCode(val code: String = "", override val error: ErrorMessage? = null) : TeamDialog

    /** Recién creada una persona: lo que hay que darle (código + usuario + PIN). El PIN no se vuelve a mostrar. */
    data class Credentials(val business: String, val code: String, val username: String, val pin: String, override val error: ErrorMessage? = null) : TeamDialog

    fun withError(e: ErrorMessage?): TeamDialog = when (this) {
        is RenewCode -> copy(error = e)
        is ChooseCode -> copy(error = e)
        is Credentials -> copy(error = e)
        is Edit -> copy(error = e)
        is Confirm -> copy(error = e)
        is Pin -> copy(error = e)
        is New -> copy(error = e)
    }
}

data class TeamUi(
    val viewerId: String? = null,
    val viewerRole: String? = null,
    val loading: Boolean = true,
    /** Sin conexión: se muestra lo último que se guardó en el teléfono, con un aviso. */
    val offline: Boolean = false,
    val loadError: ErrorMessage? = null,
    /** La persona cuya hoja de acciones está abierta. */
    val selected: MemberEntity? = null,
    val dialog: TeamDialog? = null,
    val saving: Boolean = false,
    val notice: TeamNotice? = null,
    /** El negocio y su código de acceso (5 dígitos) guardados en el teléfono; nulo si aún no se conocen. */
    val businessName: String = "",
    val accessCode: String? = null,
)

/** Lista de personas del negocio y su gestión (dueño y admin). Todo cambio necesita conexión y lo valida el servidor. */
class TeamViewModel(private val c: AppContainer) : ViewModel(), TeamActions {
    private val _ui = MutableStateFlow(TeamUi())
    val ui: StateFlow<TeamUi> = _ui.asStateFlow()
    val members: StateFlow<List<MemberEntity>> = c.team.members().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        viewModelScope.launch { c.sessionStore.flow.collect { s -> _ui.update { it.copy(viewerId = s.memberId, viewerRole = s.memberRole) } } }
        viewModelScope.launch { c.db.directory().business().collect { b -> _ui.update { it.copy(businessName = b?.name.orEmpty(), accessCode = b?.accessCode) } } }
    }

    /** Al abrir la pantalla: se descarta lo que quedó de otra persona o de otra vez y se pide el directorio. */
    override fun enter() {
        _ui.update { it.copy(notice = null, dialog = null, selected = null) }
        refresh()
    }

    override fun refresh() {
        _ui.update { it.copy(loading = true) }
        viewModelScope.launch {
            c.team.refresh().fold(
                onSuccess = { _ui.update { it.copy(loading = false, offline = false, loadError = null) } },
                onFailure = { e ->
                    val offline = e is ApiFailure.Offline
                    _ui.update { it.copy(loading = false, offline = offline, loadError = if (offline) null else e.teamError()) }
                },
            )
        }
    }

    override fun open(m: MemberEntity) = _ui.update { it.copy(selected = m, notice = null) }
    override fun closeSheet() = _ui.update { it.copy(selected = null) }
    override fun openEdit(m: MemberEntity) = _ui.update { it.copy(selected = null, dialog = TeamDialog.Edit(m, m.displayName, m.color)) }
    override fun openConfirm(m: MemberEntity, kind: ConfirmKind) = _ui.update { it.copy(selected = null, dialog = TeamDialog.Confirm(m, kind)) }
    override fun openPin(m: MemberEntity) = _ui.update { it.copy(selected = null, dialog = TeamDialog.Pin(m)) }
    override fun openRenewCode() = _ui.update { it.copy(notice = null, dialog = TeamDialog.RenewCode()) }
    override fun openChooseCode() = _ui.update { it.copy(notice = null, dialog = TeamDialog.ChooseCode()) }
    override fun openNew() = _ui.update { it.copy(notice = null, dialog = TeamDialog.New()) }
    override fun updateDialog(d: TeamDialog) = _ui.update { it.copy(dialog = d.withError(null)) }
    override fun closeDialog() = _ui.update { it.copy(dialog = null) }
    override fun dismissNotice() = _ui.update { it.copy(notice = null) }

    private fun launchChange(d: TeamDialog, ok: ErrorMessage, next: TeamDialog? = null, call: suspend () -> Result<Unit>) {
        if (_ui.value.saving) return
        _ui.update { it.copy(saving = true) }
        viewModelScope.launch {
            call().fold(
                onSuccess = { _ui.update { it.copy(saving = false, dialog = next, selected = null, notice = if (next == null) TeamNotice(ok, false) else null) } },
                // Un error de una confirmación (rol, deshabilitar) sale como aviso sobre la lista; los formularios lo muestran dentro.
                onFailure = { e ->
                    val msg = e.teamError()
                    _ui.update { if (d is TeamDialog.Confirm) it.copy(saving = false, dialog = null, notice = TeamNotice(msg, true)) else it.copy(saving = false, dialog = d.withError(msg)) }
                },
            )
        }
    }

    override fun save() {
        when (val d = _ui.value.dialog) {
            is TeamDialog.Edit -> {
                val name = TeamRules.cleanName(d.name)
                if (name.isEmpty()) return
                val nameArg = name.takeIf { it != d.member.displayName }
                val colorArg = d.color.takeIf { it != d.member.color }
                if (nameArg == null && colorArg == null) { closeDialog(); return }
                launchChange(d, ErrorMessage(R.string.team_saved)) { c.team.update(d.member.id, nameArg, colorArg, null, null) }
            }
            is TeamDialog.Pin -> {
                if (!PinRules.canSave(d.pin, d.confirm)) return
                launchChange(d, ErrorMessage(R.string.team_pin_saved, detail = d.member.displayName)) { c.team.resetPin(d.member.id, d.pin, d.mustChange) }
            }
            is TeamDialog.New -> {
                val name = TeamRules.cleanName(d.name)
                if (name.isEmpty() || !PinRules.canSave(d.pin, d.confirm)) return
                val card = TeamDialog.Credentials(_ui.value.businessName, _ui.value.accessCode.orEmpty(), name, d.pin)
                launchChange(d, ErrorMessage(R.string.team_created, detail = name), next = card) { c.team.create(name, d.role, d.pin, d.mustChange) }
            }
            is TeamDialog.ChooseCode -> {
                if (!AccessCode.isChosenValid(d.code)) return
                launchChange(d, ErrorMessage(R.string.team_code_saved)) { c.team.setAccessCode(d.code).map { } }
            }
            else -> Unit
        }
    }

    override fun confirm() {
        (_ui.value.dialog as? TeamDialog.RenewCode)?.let { d -> launchChange(d, ErrorMessage(R.string.team_code_renewed)) { c.team.renewAccessCode().map { } }; return }
        val d = _ui.value.dialog as? TeamDialog.Confirm ?: return
        val id = d.member.id
        when (d.kind) {
            ConfirmKind.MAKE_ADMIN -> launchChange(d, ErrorMessage(R.string.team_saved)) { c.team.update(id, null, null, TeamRules.ADMIN, null) }
            ConfirmKind.MAKE_CASHIER -> launchChange(d, ErrorMessage(R.string.team_saved)) { c.team.update(id, null, null, TeamRules.CASHIER, null) }
            ConfirmKind.DISABLE -> launchChange(d, ErrorMessage(R.string.team_saved)) { c.team.update(id, null, null, null, "DISABLED") }
            ConfirmKind.ENABLE -> launchChange(d, ErrorMessage(R.string.team_saved)) { c.team.update(id, null, null, null, "ACTIVE") }
        }
    }
}

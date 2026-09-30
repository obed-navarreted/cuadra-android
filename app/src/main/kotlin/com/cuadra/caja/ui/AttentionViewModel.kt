package com.cuadra.caja.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cuadra.caja.AppContainer
import com.cuadra.caja.domain.Attention
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AttentionUi(
    val loading: Boolean = true,
    val items: List<Attention.Item> = emptyList(),
    /** La operación que se está por descartar (se pide confirmación). */
    val discarding: Attention.Item? = null,
)

interface AttentionActions {
    fun retry(item: Attention.Item) {}
    fun askDiscard(item: Attention.Item) {}
    fun cancelDiscard() {}
    fun confirmDiscard() {}
}

/** «Requiere atención» (Más › estado de la sincronización): lo que el servidor rechazó o hay que revisar, explicado, con Reintentar y Descartar. */
class AttentionViewModel(private val c: AppContainer) : ViewModel(), AttentionActions {
    private val discarding = MutableStateFlow<Attention.Item?>(null)

    val ui: StateFlow<AttentionUi> = combine(c.attention.items().map { rows ->
        rows.map { op ->
            val who = op.memberId?.let { c.db.directory().member(it)?.displayName }
            val total = if (op.kind == "SALE_UPSERT" || op.kind == "SALE_CANCEL") c.db.sales().get(op.entityId)?.totalMinor else null
            Attention.describe(op.seq, op.kind, op.payload, op.createdAt, op.state, op.lastCode, op.lastDetail, who, total)
        }
    }, discarding) { items, d -> AttentionUi(loading = false, items = items, discarding = d) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AttentionUi())

    override fun retry(item: Attention.Item) {
        viewModelScope.launch { c.attention.retry(item.seq) }
    }

    override fun askDiscard(item: Attention.Item) = discarding.update { item }
    override fun cancelDiscard() = discarding.update { null }

    override fun confirmDiscard() {
        val item = discarding.value ?: return
        discarding.update { null }
        viewModelScope.launch { c.attention.discard(item.seq) }
    }
}

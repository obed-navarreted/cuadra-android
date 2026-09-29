package com.cuadra.caja.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cuadra.caja.AppContainer
import com.cuadra.caja.data.local.SaleEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class HistoryViewModel(c: AppContainer) : ViewModel() {
    val sales: StateFlow<List<SaleEntity>> = c.sales.recent(100).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
}

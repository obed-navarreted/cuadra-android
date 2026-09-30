package com.cuadra.caja.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cuadra.caja.AppContainer
import com.cuadra.caja.data.scanner.ReaderEnv
import com.cuadra.caja.data.scanner.ScanRecord
import com.cuadra.caja.data.scanner.ScannerSettings
import com.cuadra.caja.data.scanner.SetupUi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** Lo que muestra «Lector de códigos»: qué trae el equipo, los interruptores, el resultado de configurar Zebra y la última lectura de prueba. */
data class ReaderUi(
    val env: ReaderEnv = ReaderEnv(),
    val settings: ScannerSettings = ScannerSettings(),
    val setup: SetupUi = SetupUi(),
    val last: ScanRecord? = null,
    val count: Int = 0,
)

interface ReaderActions {
    fun setUseReader(on: Boolean) {}
    fun setUseCamera(on: Boolean) {}
    fun setBeep(on: Boolean) {}
    fun configureZebra() {}
    fun clearTest() {}
}

class ReaderViewModel(private val c: AppContainer) : ViewModel(), ReaderActions {
    private val hub = c.scanner

    init { hub.refreshEnv() }

    val ui: StateFlow<ReaderUi> = combine(hub.env, hub.settings, hub.setup, hub.last, hub.count) { env, settings, setup, last, count ->
        ReaderUi(env, settings, setup, last, count)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReaderUi())

    /** La prueba de Ajustes: mientras la pantalla está a la vista recibe las lecturas del teclado (sin agregar nada a ninguna venta). */
    fun claim() = hub.claim { _, _ -> }

    override fun setUseReader(on: Boolean) = hub.setUseReader(on)
    override fun setUseCamera(on: Boolean) = hub.setUseCamera(on)
    override fun setBeep(on: Boolean) = hub.setBeep(on)
    override fun configureZebra() = hub.configureDataWedge()
    override fun clearTest() = hub.clearTest()
}

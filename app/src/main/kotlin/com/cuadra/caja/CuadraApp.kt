package com.cuadra.caja

import android.app.Application
import com.cuadra.caja.data.sync.SyncScheduler
import kotlinx.coroutines.launch

class CuadraApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        SyncScheduler.schedulePeriodic(this)
        container.presenter.ensureChannels()
        container.scope.launch {
            // Una cuenta que quedó abierta al cerrarse el proceso se recupera como apartada: nunca se pierde.
            container.sales.recoverOpenAsParked()
        }
    }
}

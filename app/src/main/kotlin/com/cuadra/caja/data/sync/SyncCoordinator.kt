package com.cuadra.caja.data.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.cuadra.caja.CuadraApp
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Estado que ve la persona: discreto, en lenguaje simple, sin códigos HTTP (PLAN.md 14.4). */
enum class SyncStatus { IDLE, SYNCING, OFFLINE, NEEDS_ATTENTION, SUSPENDED }

/** El negocio está suspendido: lo pendiente sigue en la cola y se muestra un mensaje claro, no un error genérico. */
fun syncStatusOf(result: SyncResult): SyncStatus = when (result) {
    is SyncResult.Done -> if (result.rejected > 0) SyncStatus.NEEDS_ATTENTION else SyncStatus.IDLE
    SyncResult.Offline -> SyncStatus.OFFLINE
    is SyncResult.AuthProblem -> SyncStatus.NEEDS_ATTENTION
    is SyncResult.Failed -> if (result.code == "BUSINESS_SUSPENDED") SyncStatus.SUSPENDED else SyncStatus.NEEDS_ATTENTION
}

/** Ejecuta la sincronización de a una vez y publica su estado. */
class SyncCoordinator(
    private val engine: () -> SyncEngine?,
    /** El servidor no acepta este teléfono o esta persona; recibe el código (ACCESS_DISABLED, MEMBER_NOT_ACTIVE, UNAUTHENTICATED…). */
    private val onAuthProblem: suspend (String) -> Unit = {},
    /** Tras una sincronización terminada: por ejemplo mostrar los avisos nuevos. Un fallo aquí nunca cuenta como fallo de sincronizar. */
    private val onSynced: suspend () -> Unit = {},
) {
    private val mutex = Mutex()
    private val _status = MutableStateFlow(SyncStatus.IDLE)
    val status: StateFlow<SyncStatus> = _status.asStateFlow()

    /** Cuándo terminó bien la última sincronización desde que arrancó la app (para los datos técnicos de «Ayuda»); nulo si aún no hubo. */
    @Volatile var lastDoneAt: Long? = null
        private set

    suspend fun run(): SyncResult? {
        val e = engine() ?: return null
        if (!mutex.tryLock()) return null    // ya hay una en curso
        try {
            _status.value = SyncStatus.SYNCING
            val result = e.run()
            _status.value = syncStatusOf(result)
            if (result is SyncResult.AuthProblem) onAuthProblem(result.code)
            if (result is SyncResult.Done) { lastDoneAt = System.currentTimeMillis(); runCatching { onSynced() } }
            return result
        } catch (ex: Exception) {
            _status.value = SyncStatus.NEEDS_ATTENTION
            return SyncResult.Failed("UNEXPECTED")
        } finally {
            mutex.unlock()
        }
    }
}

object SyncScheduler {
    private const val ONE_TIME = "cuadra-sync-now"
    private const val PERIODIC = "cuadra-sync-periodic"
    private val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    /** Tras guardar una venta: 3 s después y con red. Varias escrituras seguidas comparten un solo envío. */
    fun requestSoon(context: Context) {
        val req = OneTimeWorkRequestBuilder<SyncWorker>().setConstraints(online).setInitialDelay(3, TimeUnit.SECONDS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build()
        WorkManager.getInstance(context).enqueueUniqueWork(ONE_TIME, ExistingWorkPolicy.KEEP, req)
    }

    private const val EXPEDITED = "cuadra-sync-push"

    /**
     * Llegó un aviso de Firebase con la app en segundo plano: un trabajo URGENTE (el sistema lo corre ya aunque la app esté cerrada; si no le quedan cupos de
     * urgencia, como trabajo normal). Varios avisos seguidos comparten uno.
     */
    fun requestExpedited(context: Context) {
        val req = OneTimeWorkRequestBuilder<SyncWorker>().setConstraints(online)
            .setExpedited(androidx.work.OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST).build()
        WorkManager.getInstance(context).enqueueUniqueWork(EXPEDITED, ExistingWorkPolicy.KEEP, req)
    }

    fun schedulePeriodic(context: Context) {
        val req = PeriodicWorkRequestBuilder<SyncWorker>(30, TimeUnit.MINUTES).setConstraints(online).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, req)
    }
}

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    /** Android 11 o menos corre un trabajo urgente como servicio en primer plano: necesita esta notificación discreta (canal de importancia mínima). */
    override suspend fun getForegroundInfo(): androidx.work.ForegroundInfo {
        val manager = applicationContext.getSystemService(android.app.NotificationManager::class.java)
        manager?.createNotificationChannel(android.app.NotificationChannel("cuadra_sync", applicationContext.getString(com.cuadra.caja.R.string.sync_channel), android.app.NotificationManager.IMPORTANCE_MIN))
        val n = androidx.core.app.NotificationCompat.Builder(applicationContext, "cuadra_sync").setSmallIcon(com.cuadra.caja.R.drawable.ic_stat_cuadra)
            .setContentTitle(applicationContext.getString(com.cuadra.caja.R.string.sync_running)).setPriority(androidx.core.app.NotificationCompat.PRIORITY_MIN).setOngoing(true).build()
        return androidx.work.ForegroundInfo(0x5C, n)
    }

    override suspend fun doWork(): Result {
        val container = (applicationContext as CuadraApp).container
        return when (container.sync.run()) {
            is SyncResult.Offline, is SyncResult.Failed -> Result.retry()
            else -> Result.success()
        }
    }
}

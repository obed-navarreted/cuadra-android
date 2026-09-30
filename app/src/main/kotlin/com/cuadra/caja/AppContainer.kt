package com.cuadra.caja

import android.content.Context
import com.cuadra.caja.data.local.CuadraDatabase
import com.cuadra.caja.data.remote.ApiFactory
import com.cuadra.caja.data.remote.AppConfigStore
import com.cuadra.caja.data.repo.AppConfigRepository
import com.cuadra.caja.data.repo.AuthRepository
import com.cuadra.caja.data.repo.CreditRepository
import com.cuadra.caja.data.repo.CustomerRepository
import com.cuadra.caja.data.repo.ExpenseRepository
import com.cuadra.caja.data.repo.InventoryRepository
import com.cuadra.caja.data.notify.NotificationPresenter
import com.cuadra.caja.data.repo.NotificationRepository
import com.cuadra.caja.data.repo.PurchaseRepository
import com.cuadra.caja.data.repo.ReportRepository
import com.cuadra.caja.data.repo.ScheduleRepository
import com.cuadra.caja.data.repo.SettingsRepository
import com.cuadra.caja.data.repo.PlanRepository
import com.cuadra.caja.data.repo.SupportRepository
import com.cuadra.caja.data.repo.TemplateRepository
import com.cuadra.caja.data.repo.ShiftRepository
import com.cuadra.caja.data.repo.TeamRepository
import com.cuadra.caja.data.repo.ProductRepository
import com.cuadra.caja.data.repo.SaleRepository
import com.cuadra.caja.data.scanner.ScanHub
import com.cuadra.caja.data.session.Session
import com.cuadra.caja.data.session.SessionStore
import com.cuadra.caja.data.sync.RetrofitSyncRemote
import com.cuadra.caja.data.sync.RoomSyncStore
import com.cuadra.caja.data.sync.SyncCoordinator
import com.cuadra.caja.data.sync.SyncEngine
import com.cuadra.caja.data.sync.SyncScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking

/**
 * Inyección manual (sin Hilt por ahora: son pocas piezas y todas viven lo mismo que la app; ver ADR 0002).
 * La sesión se lee en cada petición, así cambiar de cajero o vincular el teléfono no reconstruye nada.
 */
class AppContainer(private val context: Context) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val db: CuadraDatabase = CuadraDatabase.create(context)
    val sessionStore = SessionStore(context)

    /** Preferencias de pantalla de este teléfono (tamaño de letra). */
    val display = com.cuadra.caja.data.prefs.DisplayPrefs(context, scope)

    @Volatile private var sessionSnapshot: Session = runBlocking { sessionStore.current() }

    init {
        sessionStore.flow.onEach { sessionSnapshot = it }.launchIn(scope)
    }

    val api = ApiFactory.create(BuildConfig.API_URL) { sessionSnapshot }
    private val teamApi = ApiFactory.createTeam(BuildConfig.API_URL) { sessionSnapshot }

    private val requestSync: () -> Unit = { SyncScheduler.requestSoon(context) }

    val products = ProductRepository(db, api, sessionStore, requestSync)
    val sales = SaleRepository(db, api, sessionStore, requestSync)
    val auth = AuthRepository(db, api, sessionStore)
    val team = TeamRepository(db, teamApi, sessionStore, auth, api)
    val customers = CustomerRepository(db, requestSync)
    val credits = CreditRepository(db, sessionStore, requestSync)
    val expenses = ExpenseRepository(db, sessionStore, requestSync, api)
    val shifts = ShiftRepository(db, sessionStore, requestSync)
    val inventory = InventoryRepository(db, sessionStore, requestSync)
    val purchases = PurchaseRepository(db, sessionStore, requestSync)
    val schedules = ScheduleRepository(api, sessionStore)
    val reports = ReportRepository(api, sessionStore)
    val settings = SettingsRepository(db, api, sessionStore, schedules)
    val plan = PlanRepository(api, sessionStore)
    val support = SupportRepository(api)
    val templates = TemplateRepository(db, api, sessionStore)
    val notifications = NotificationRepository(db, sessionStore, requestSync)
    /** «Requiere atención»: lo rechazado por el servidor, con Reintentar y Descartar. */
    val attention = com.cuadra.caja.data.repo.AttentionRepository(db, sessionStore, requestSync)
    val appConfig = AppConfigRepository(api, AppConfigStore(context))
    val presenter = NotificationPresenter(context, db, sessionStore)

    /** Lectores de códigos: cámara, teclado (wedge) y Zebra DataWedge, todos por aquí. */
    val scanner = ScanHub(context, scope)

    /** Impresora térmica (opcional, apagada por omisión): Bluetooth clásico o cable USB, con reconexión automática. */
    val printer = com.cuadra.caja.data.printer.PrinterHub(context, scope, db)

    /** Enlace pendiente de abrir (de una notificación). La pantalla lo consume: `AppRoot` lo lee y lo deja en nulo. */
    val pendingRoute = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)

    val sync = SyncCoordinator(
        engine = {
            val s = sessionSnapshot
            if (s.deviceToken == null || s.businessId == null) null
            else SyncEngine(
                RetrofitSyncRemote(api, s.businessId) { sessionSnapshot.memberId },
                RoomSyncStore(db, s.businessId) { sessionSnapshot.memberId != null },
            )
        },
        onAuthProblem = { code ->
            when (code) {
                // Teléfono personal de alguien dado de baja: «Tu acceso fue desactivado». El token se conserva para terminar de enviar lo de antes de la baja.
                "ACCESS_DISABLED" -> sessionStore.setAccessDisabled(true)
                // La persona activa de un teléfono compartido fue dada de baja: vuelve a «¿Quién atiende?» con un aviso; lo suyo sigue en la cola.
                "MEMBER_NOT_ACTIVE" -> sessionStore.memberDisabled()
                // El teléfono fue revocado o la sesión no vale: se pide vincular de nuevo. Lo pendiente sigue en Room.
                else -> sessionStore.clearAccess()
            }
        },
        // Después de sincronizar: los avisos nuevos salen como notificación y los leídos viejos se limpian.
        onSynced = {
            presenter.showNew()
            db.notifications().pruneRead(System.currentTimeMillis() - 60L * 24 * 3600 * 1000)
        },
    )
}

package com.cuadra.caja

import android.content.Context
import com.cuadra.caja.data.local.Db
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.runBlocking

/**
 * Inyección manual (sin Hilt por ahora: son pocas piezas y todas viven lo mismo que la app; ver ADR 0002).
 * La sesión se lee en cada petición, así cambiar de cajero o vincular el teléfono no reconstruye nada.
 */
class AppContainer(private val context: Context) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    /** Una base por negocio (ADR 0014): `db` es siempre la del negocio de la sesión, o una vacía si aún no hay negocio. */
    val databases = com.cuadra.caja.data.local.BusinessDatabases(context, scope)
    val db: Db = databases
    val sessionStore = SessionStore(context)

    /** Preferencias de pantalla de este teléfono (tamaño de letra). */
    val display = com.cuadra.caja.data.prefs.DisplayPrefs(context, scope)

    @Volatile private var sessionSnapshot: Session = runBlocking { sessionStore.current() }

    init {
        // Primero se rescata la cola de la base vieja (si la hay) hacia la de cada negocio; después la base activa pasa a ser la del negocio de la sesión
        // y, de ahí en adelante, cambia sola con cada cambio de sesión (SessionStore avisa de forma síncrona).
        runBlocking { com.cuadra.caja.data.local.LegacyDatabase.migrate(context, databases, sessionSnapshot.businessId) }
        sessionStore.onSession = { databases.select(it.businessId) }
        databases.select(sessionSnapshot.businessId)
        sessionStore.flow.onEach { sessionSnapshot = it }.launchIn(scope)
    }

    val api = ApiFactory.create(BuildConfig.API_URL) { sessionSnapshot }
    private val teamApi = ApiFactory.createTeam(BuildConfig.API_URL) { sessionSnapshot }

    private val requestSync: () -> Unit = { SyncScheduler.requestSoon(context) }

    val products = ProductRepository(db, api, sessionStore, requestSync)
    /** Promociones por cantidad (la caja las aplica sola, sin conexión). */
    val promotions = com.cuadra.caja.data.repo.PromotionRepository(db, requestSync)
    val sales = SaleRepository(db, api, sessionStore, requestSync)
    val auth = AuthRepository(db, api, sessionStore, dbFor = databases::forBusiness)
    val team = TeamRepository(db, teamApi, sessionStore, auth, api)
    val customers = CustomerRepository(db, requestSync)
    val credits = CreditRepository(db, sessionStore, requestSync)
    val expenses = ExpenseRepository(db, sessionStore, requestSync, api)
    val shifts = ShiftRepository(db, sessionStore, requestSync)
    val inventory = InventoryRepository(db, sessionStore, requestSync)
    val purchases = PurchaseRepository(db, sessionStore, requestSync)
    val schedules = ScheduleRepository(api, sessionStore)
    val reports = ReportRepository(api, sessionStore)
    val settings = SettingsRepository(db, api, sessionStore, schedules, databases)
    val plan = PlanRepository(api, sessionStore)
    val support = SupportRepository(api)
    val templates = TemplateRepository(db, api, sessionStore)
    val notifications = NotificationRepository(db, sessionStore, requestSync)
    /** «Requiere atención»: lo rechazado por el servidor, con Reintentar y Descartar. */
    val attention = com.cuadra.caja.data.repo.AttentionRepository(db, sessionStore, requestSync)
    val appConfig = AppConfigRepository(api, AppConfigStore(context))

    init {
        // Una llamada respondió PIN_VERIFICATION_REQUIRED: la persona baja al rol base hasta «Confirmar PIN».
        com.cuadra.caja.data.remote.PinVerificationSignal.onRequired = { member -> scope.launch { auth.pinVerificationRequired(member) } }
    }
    val presenter = NotificationPresenter(context, db, sessionStore)

    /** Lectores de códigos: cámara, teclado (wedge) y Zebra DataWedge, todos por aquí. */
    val scanner = ScanHub(context, scope)

    /** Impresora térmica (opcional, apagada por omisión): Bluetooth clásico o cable USB, con reconexión automática. */
    val printer = com.cuadra.caja.data.printer.PrinterHub(context, scope, db)

    /** Enlace pendiente de abrir (de una notificación). La pantalla lo consume: `AppRoot` lo lee y lo deja en nulo. */
    val pendingRoute = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)

    /** Avisos al instante (Firebase): token de esta instalación registrado en el negocio activo. Sin Firebase no hace nada. */
    val pushPrefs = com.cuadra.caja.data.push.PushPrefs(context)
    val push = com.cuadra.caja.data.push.PushRegistrar(context, api, pushPrefs)

    val sync = SyncCoordinator(
        engine = {
            val s = sessionSnapshot
            if (s.deviceToken == null || s.businessId == null) null
            else SyncEngine(
                RetrofitSyncRemote(api, s.businessId) { sessionSnapshot.memberId },
                RoomSyncStore(databases.forBusiness(s.businessId), s.businessId) { sessionSnapshot.memberId != null },
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
            // Rol base del teléfono y permisos de PIN verificado (ADR 0012, 2026-10-01): la persona activa queda con el rol que le toca.
            runCatching { auth.refreshDeviceAccess() }
            presenter.showNew()
            db.notifications().pruneRead(System.currentTimeMillis() - 60L * 24 * 3600 * 1000)
        },
    )

    /** Sincronización con la app a la vista: al volver, al abrir la caja, al llegar un aviso y de respaldo cada 30 s sin Firebase (5 min con él). */
    val foreground = com.cuadra.caja.data.push.ForegroundSync(scope, run = { sync.run() }, pushActive = { push.active })

    init {
        // El token se registra en el negocio y con la persona que atiende; al cambiar de negocio o de persona, otra vez. Sin negocio se olvida.
        sessionStore.flow.map { Triple(it.businessId, it.memberId, it.deviceToken ?: it.userToken) }.distinctUntilChanged().onEach { (business, _, _) ->
            if (business == null) push.forget() else runCatching { push.register(sessionStore.current()) }
        }.launchIn(scope)
    }
}

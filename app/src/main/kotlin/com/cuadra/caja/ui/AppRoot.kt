package com.cuadra.caja.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.clickable
import androidx.compose.material3.MaterialTheme
import com.cuadra.caja.ui.common.Text
import com.cuadra.caja.ui.common.SectionBar
import com.cuadra.caja.ui.common.SectionEntry
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cuadra.caja.AppContainer
import com.cuadra.caja.data.session.Session
import com.cuadra.caja.BuildConfig
import com.cuadra.caja.domain.AppUpdate
import com.cuadra.caja.domain.UpdateNeed
import com.cuadra.caja.ui.screens.NoticeBanners
import com.cuadra.caja.ui.screens.UpdateRequiredScreen
import com.cuadra.caja.R
import com.cuadra.caja.core.i18n.AppLanguage
import com.cuadra.caja.core.i18n.AppLocale
import com.cuadra.caja.feature.auth.GoogleResult
import com.cuadra.caja.feature.auth.GoogleSignIn
import com.cuadra.caja.ui.common.MoneyFormat
import com.cuadra.caja.ui.common.ProvideMoney
import com.cuadra.caja.ui.screens.CajaScreen
import com.cuadra.caja.ui.screens.CreditsScreen
import com.cuadra.caja.ui.screens.DailyCloseScreen
import com.cuadra.caja.ui.screens.ExpensesScreen
import com.cuadra.caja.ui.screens.ShiftScreen
import com.cuadra.caja.ui.screens.InventoryScreen
import com.cuadra.caja.ui.screens.NotificationsScreen
import com.cuadra.caja.ui.screens.SchedulesScreen
import com.cuadra.caja.ui.screens.SummaryScreen
import com.cuadra.caja.ui.screens.PurchasesScreen
import com.cuadra.caja.ui.screens.ReaderScreen
import com.cuadra.caja.ui.screens.AccountScreen
import com.cuadra.caja.ui.screens.DevicesScreen
import com.cuadra.caja.ui.screens.TeamScreen
import com.cuadra.caja.ui.screens.TextSizeScreen
import com.cuadra.caja.ui.screens.BusinessSettingsScreen
import com.cuadra.caja.ui.screens.ActivityScreen
import com.cuadra.caja.ui.screens.HelpScreen
import com.cuadra.caja.ui.screens.TemplatesScreen
import com.cuadra.caja.ui.screens.MoreActions
import com.cuadra.caja.ui.screens.TeamTab
import com.cuadra.caja.data.sync.modules
import com.cuadra.caja.ui.screens.HistoryScreen
import com.cuadra.caja.ui.screens.ChangePinScreen
import com.cuadra.caja.ui.screens.MemberLoginScreen
import com.cuadra.caja.ui.screens.LoginScreen
import com.cuadra.caja.ui.screens.MoreScreen
import com.cuadra.caja.ui.screens.OnboardingScreen
import com.cuadra.caja.ui.screens.PinScreen
import com.cuadra.caja.ui.screens.AccessDisabledScreen
import com.cuadra.caja.ui.screens.DisabledMemberNotice
import com.cuadra.caja.ui.theme.CuadraColors
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.launch

private enum class Section { REGISTER, CREDITS, EXPENSES, SALES, MORE }

/** Pantallas que se abren desde Más y cubren el contenido (la barra de secciones sigue). */
private enum class Overlay { PROMOTIONS, PIN_CONFIRM, ATTENTION, INVENTORY, PURCHASES, CATALOG, NOTIFICATIONS, SCHEDULES, SUMMARY, DAILY_CLOSE, READER, TEAM, DEVICES, ACCOUNT, SETTINGS, SUPPORT, ACTIVITY, HELP, TEMPLATES, TEXT_SIZE, PRINTER }

/** Pantallas de administración: con el PIN de la persona activa sin confirmar en este teléfono, en su lugar va «Confirma tu PIN» (ADR 0012, 2026-10-01). */
private val ADMIN_OVERLAYS = setOf(Overlay.PROMOTIONS, Overlay.INVENTORY, Overlay.PURCHASES, Overlay.SUMMARY, Overlay.SETTINGS, Overlay.ACTIVITY, Overlay.TEMPLATES, Overlay.TEAM, Overlay.DEVICES, Overlay.DAILY_CLOSE, Overlay.SCHEDULES)

/**
 * Turnos y cierre de caja manuales: FUERA DE VISTA por decisión del propietario (el cierre es automático por jornada: «Cierre del día»). El código de turnos
 * (Room, sincronización, pantallas) queda dormido, sin borrarse; con esto apagado nada en Caja ni en Cobro exige un turno abierto.
 */
private const val SHIFTS_UI_ENABLED = false

private fun teamTab(tab: TeamTab) = when (tab) { TeamTab.PEOPLE -> Overlay.TEAM; TeamTab.DEVICES -> Overlay.DEVICES }

private inline fun <reified T : androidx.lifecycle.ViewModel> factory(crossinline make: () -> T) =
    viewModelFactory { initializer { make() } }

/**
 * Todo lo que una pantalla recuerda en memoria (listas del servidor, filtros, borradores, la lista de negocios de la cuenta de Google…) vive en un
 * `ViewModelStore` que SOLO dura mientras la sesión sea la misma (ADR 0014): al cerrar sesión, entrar con otra cuenta o cambiar de negocio se descarta
 * entero y las pantallas nacen vacías. Antes los ViewModel vivían lo que la Activity y la pantalla de ventas del negocio nuevo mostraba la lista del anterior.
 * Cambiar de persona (PIN) dentro del mismo negocio NO lo descarta: la venta en curso no se pierde.
 */
@Composable
internal fun SessionScope(session: Session?, content: @Composable () -> Unit) {
    val key = session?.let { listOf(it.userToken?.hashCode(), it.deviceToken?.hashCode(), it.deviceId, it.businessId) }
    androidx.compose.runtime.key(key) {
        val owner = remember { object : androidx.lifecycle.ViewModelStoreOwner { override val viewModelStore = androidx.lifecycle.ViewModelStore() } }
        androidx.compose.runtime.DisposableEffect(owner) { onDispose { owner.viewModelStore.clear() } }
        androidx.compose.runtime.CompositionLocalProvider(androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner provides owner, content = content)
    }
}

/** Decide qué pantalla toca según el acceso del teléfono: entrar → negocio → elegir persona → caja. */
@Composable
fun AppRoot(container: AppContainer) {
    val root: RootViewModel = viewModel(factory = factory { RootViewModel(container) })
    val sessionNow by root.session.collectAsState()
    SessionScope(sessionNow) { AppRootBody(container, root) }
}

@Composable
private fun AppRootBody(container: AppContainer, root: RootViewModel) {
    val auth: AuthViewModel = viewModel(factory = factory { AuthViewModel(container) })
    val session by root.session.collectAsState()
    val business by root.business.collectAsState()
    val authUi by auth.ui.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var language by remember { mutableStateOf(AppLocale.current()) }
    var showMemberLogin by rememberSaveable { mutableStateOf(false) }
    val gate = root.gate(session)
    val config by container.appConfig.state.collectAsState(initial = null)
    val blocked = config?.let { AppUpdate.need(BuildConfig.VERSION_NAME, it.minAppVersion, null) } is UpdateNeed.Required

    // Al quedar el teléfono listo (vinculado y con persona), se sincroniza; el resto lo hace WorkManager.
    LaunchedEffect(gate) {
        // Ya vinculado: la pantalla del código no debe reaparecer al volver a la entrada (p. ej. tras eliminar el negocio).
        if (gate == Gate.READY || gate == Gate.PICK_MEMBER || gate == Gate.CHANGE_PIN) showMemberLogin = false
        if (gate == Gate.READY || gate == Gate.PICK_MEMBER || gate == Gate.ACCESS_DISABLED) root.syncNow()
        if (gate == Gate.ONBOARDING) auth.loadMe()
    }

    val onLanguage: (AppLanguage) -> Unit = { language = it; AppLocale.set(it) }
    val fmt = business?.let { MoneyFormat.of(it.currency, it.country) } ?: MoneyFormat.Default

    ProvideMoney(fmt) {
        Box(Modifier.fillMaxSize().background(CuadraColors.Bg)) {
            when {
                // Versión no compatible (según lo último que dijo el servidor, también sin conexión): solo esta pantalla. Los datos siguen a salvo.
                blocked -> UpdateRequiredScreen()
                gate == Gate.LOADING -> Unit
                gate == Gate.ACCESS_DISABLED -> {
                    val pending by root.pending.collectAsState()
                    val failed by root.failed.collectAsState()
                    AccessDisabledScreen(business?.name, pending, failed, onRetry = root::syncNow, onExit = root::leaveDisabledAccess)
                }
                gate == Gate.SIGNED_OUT && showMemberLogin -> {
                    val login: MemberLoginViewModel = viewModel(factory = factory { MemberLoginViewModel(container) })
                    MemberLoginScreen(login, onBack = { showMemberLogin = false })
                }
                gate == Gate.SIGNED_OUT -> LoginScreen(
                    language, onLanguage, authUi,
                    onGoogle = { scope.launch { handleGoogle(context, auth) } },
                    onMemberLogin = { showMemberLogin = true },
                    onPlatformAccess = { com.cuadra.caja.ui.common.openInBrowser(context, com.cuadra.caja.domain.PlatformConsole.url(config?.panelUrl)) },
                )
                gate == Gate.ONBOARDING -> OnboardingScreen(
                    authUi.me, authUi, onCreate = auth::createBusiness, onUse = auth::useBusiness, onSignOut = root::signOut,
                    phoneZone = com.cuadra.caja.data.repo.phoneTimeZone(),
                    onPlatformConsole = { com.cuadra.caja.ui.common.openInBrowser(context, com.cuadra.caja.domain.PlatformConsole.url(config?.panelUrl)) },
                )
                gate == Gate.CHANGE_PIN -> {
                    val changePin: ChangePinViewModel = viewModel(factory = factory { ChangePinViewModel(container) })
                    ChangePinScreen(changePin)
                }
                gate == Gate.PICK_MEMBER -> {
                    val pin: PinViewModel = viewModel(factory = factory { PinViewModel(container) })
                    val members by pin.members.collectAsState()
                    val pinUi by pin.ui.collectAsState()
                    // Solo quien entró con Google en este teléfono puede crear su propio PIN.
                    PinScreen(
                        members, pinUi, canCreatePin = session?.userToken != null, onSelect = pin::select, onBack = pin::back,
                        onDigit = pin::digit, onBackspace = pin::backspace, onSubmit = pin::submit, onSignOut = pin::signOut,
                    )
                    // La persona que atendía fue dada de baja: se avisa una vez.
                    session?.disabledMemberNotice?.let { DisabledMemberNotice(it, root::dismissDisabledNotice) }
                }
                else -> Main(container, root, business, session?.memberName.orEmpty(), session?.memberRole.orEmpty(), session?.memberRealRole, language, onLanguage)
            }
        }
    }
}

private suspend fun handleGoogle(context: Context, auth: AuthViewModel) {
    when (val r = GoogleSignIn.idToken(context)) {
        is GoogleResult.Token -> auth.googleSignedIn(r.idToken)
        GoogleResult.NotConfigured -> auth.showError(R.string.auth_google_not_configured)
        GoogleResult.NoAccount -> auth.showError(R.string.auth_google_no_account)
        GoogleResult.Cancelled -> auth.showError(R.string.auth_google_cancelled)
        is GoogleResult.Failed -> auth.showError(ErrorMessage(R.string.auth_google_failed, detail = r.detail))
    }
}

@Composable
private fun Main(
    container: AppContainer, root: RootViewModel, business: com.cuadra.caja.data.local.BusinessEntity?, memberName: String, actingRole: String,
    /** Su rol real si aún no confirmó el PIN en este teléfono (mientras tanto actúa con `actingRole`, el rol base del teléfono). */
    realRole: String?,
    language: AppLanguage, onLanguage: (AppLanguage) -> Unit,
) {
    // Qué pantallas existen lo decide su rol REAL (las de administración piden confirmar el PIN); lo que se puede hacer dentro, el rol con que actúa.
    val role = realRole ?: actingRole
    val pinPending = realRole != null
    val businessName = business?.name.orEmpty()
    val timezone = business?.timezone.orEmpty()
    val appCfg by container.appConfig.state.collectAsState(initial = null)
    val fontChoice by container.display.fontSize.collectAsState()
    val offerWhatsApp by container.display.offerWhatsApp.collectAsState()
    val askDescription by container.display.askDescription.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val sessionNow by container.sessionStore.flow.collectAsState(initial = null)
    val platformAdmin = sessionNow?.platformAdmin == true
    val panelUrl = appCfg?.panelUrl
    // Con sesión de Google, se vuelve a preguntar quién es (una vez): así «Consola de la plataforma» aparece aunque el permiso se haya dado después.
    LaunchedEffect(sessionNow?.userToken != null) { if (sessionNow?.userToken != null) container.auth.me() }
    val modules = remember(business?.modulesJson) { business?.modules().orEmpty() }
    val hasCredit = modules["credit"] != false
    // Equipo: solo dueño y admin, y solo si el negocio no apagó el módulo `team` (se enciende por omisión).
    val hasTeam = modules["team"] != false && com.cuadra.caja.domain.TeamRules.canSeeTeam(role)
    val hasExpenses = modules["expenses"] != false
    val hasShifts = SHIFTS_UI_ENABLED && modules["shifts"] == true
    val isOwner = role == "OWNER"
    val isManager = role == "OWNER" || role == "ADMIN"
    val hasInventory = modules["inventory"] == true && isManager
    // El catálogo es independiente del inventario: se puede vender sin llevar existencias. Con inventario encendido, esa pantalla ya trae todo.
    val hasCatalog = modules["catalog"] != false && !hasInventory && com.cuadra.caja.domain.ProductPermissions.canEdit(actingRole)
    var overlay by rememberSaveable { mutableStateOf<Overlay?>(null) }
    /** De dónde se abrió «Promociones» (Productos o Inventario): atrás vuelve ahí. */
    var promotionsFrom by rememberSaveable { mutableStateOf<Overlay?>(Overlay.CATALOG) }
    val inventory: InventoryViewModel = viewModel(factory = factory { InventoryViewModel(container) })
    val promotionsVm: PromotionsViewModel = viewModel(factory = factory { PromotionsViewModel(container) })
    val purchasesVm: PurchasesViewModel = viewModel(factory = factory { PurchasesViewModel(container) })
    val notifVm: NotificationsViewModel = viewModel(factory = factory { NotificationsViewModel(container) })
    val unread by notifVm.unread.collectAsState()
    val schedulesVm: SchedulesViewModel = viewModel(factory = factory { SchedulesViewModel(container) })
    val summaryVm: SummaryViewModel = viewModel(factory = factory { SummaryViewModel(container) })
    val dailyCloseVm: DailyCloseViewModel = viewModel(factory = factory { DailyCloseViewModel(container) })
    val readerVm: ReaderViewModel = viewModel(factory = factory { ReaderViewModel(container) })
    val printerVm: PrinterViewModel = viewModel(factory = factory { PrinterViewModel(container) })
    val teamVm: TeamViewModel = viewModel(factory = factory { TeamViewModel(container) })
    val devicesVm: DevicesViewModel = viewModel(factory = factory { DevicesViewModel(container) })
    val accountVm: AccountViewModel = viewModel(factory = factory { AccountViewModel(container) })
    val settingsVm: SettingsViewModel = viewModel(factory = factory { SettingsViewModel(container) })
    val activityVm: ActivityViewModel = viewModel(factory = factory { ActivityViewModel(container) })
    val helpVm: HelpViewModel = viewModel(factory = factory { HelpViewModel(container) })
    val templatesVm: TemplatesViewModel = viewModel(factory = factory { TemplatesViewModel(container) })
    val attentionVm: AttentionViewModel = viewModel(factory = factory { AttentionViewModel(container) })
    val pinConfirmVm: PinConfirmViewModel = viewModel(factory = factory { PinConfirmViewModel(container) })
    // Entró sin poder confirmar su PIN: se avisa una vez por persona (se puede cerrar); tocarlo abre «Confirma tu PIN».
    var pendingNoticeClosedFor by rememberSaveable { mutableStateOf<String?>(null) }
    val activeMemberId = sessionNow?.memberId
    // Programar avisos se abre desde la bandeja: atrás vuelve a ella.
    androidx.activity.compose.BackHandler(enabled = overlay != null) { overlay = if (overlay == Overlay.SCHEDULES) Overlay.NOTIFICATIONS else null }
    if ((overlay == Overlay.INVENTORY || overlay == Overlay.PURCHASES) && !hasInventory) overlay = null
    if (overlay == Overlay.CATALOG && !hasCatalog) overlay = null
    if ((overlay == Overlay.TEAM || overlay == Overlay.DEVICES) && !hasTeam) overlay = null
    if ((overlay == Overlay.SCHEDULES || overlay == Overlay.SUMMARY || overlay == Overlay.DAILY_CLOSE || overlay == Overlay.SETTINGS || overlay == Overlay.TEMPLATES) && !isManager) overlay = null
    if (overlay == Overlay.ACTIVITY && !isManager) overlay = null
    if (overlay == Overlay.PROMOTIONS && !(isManager && (hasCatalog || hasInventory))) overlay = null
    // Al confirmarse el PIN, «Confirma tu PIN» (abierta desde el aviso) ya no tiene nada que hacer.
    if (overlay == Overlay.PIN_CONFIRM && !pinPending) overlay = null
    var section by rememberSaveable { mutableStateOf(Section.REGISTER) }
    val cash: CashViewModel = viewModel(factory = factory { CashViewModel(container) })
    val cashUi by cash.ui.collectAsState()
    val shift by cash.currentShift.collectAsState()
    // Un módulo apagado deja de mostrarse; si se apagó estando en él, se vuelve a la caja.
    if ((section == Section.CREDITS && !hasCredit) || (section == Section.EXPENSES && !hasExpenses)) section = Section.REGISTER
    // Si el negocio exige turno, no se vende sin uno abierto: se pide abrirlo primero.
    val mustOpenShift = hasShifts && business?.shiftRequired == true && shift == null && section == Section.REGISTER
    val caja: CajaViewModel = viewModel(factory = factory { CajaViewModel(container) })
    val history: HistoryViewModel = viewModel(factory = factory { HistoryViewModel(container) })
    val credits: CreditsViewModel = viewModel(factory = factory { CreditsViewModel(container) })
    val cajaUi by caja.ui.collectAsState()
    val status by root.syncStatus.collectAsState()
    val pending by root.pending.collectAsState()
    val failed by root.failed.collectAsState()

    // Enlaces de las notificaciones (cuadra://…): abren la pantalla que toca, respetando los módulos que el negocio tiene encendidos.
    val route by container.pendingRoute.collectAsState()
    LaunchedEffect(route) {
        val r = route ?: return@LaunchedEffect
        container.pendingRoute.value = null
        cash.showShift(false)
        overlay = null
        when {
            r.startsWith("cuadra://caja") -> section = Section.REGISTER
            r.startsWith("cuadra://ventas") -> section = Section.SALES
            r.startsWith("cuadra://cierre") -> { section = if (hasExpenses || hasShifts) Section.EXPENSES else Section.REGISTER; if (hasShifts) cash.showShift(true) }
            r.startsWith("cuadra://fiados") -> section = if (hasCredit) Section.CREDITS else Section.REGISTER
            r.startsWith("cuadra://gastos") -> section = if (hasExpenses) Section.EXPENSES else Section.REGISTER
            r.startsWith("cuadra://impresora") -> { section = Section.MORE; overlay = Overlay.PRINTER }
            r.startsWith("cuadra://inventario") -> { section = Section.MORE; overlay = if (hasInventory) Overlay.INVENTORY else if (hasCatalog) Overlay.CATALOG else null }
            else -> { section = Section.MORE; overlay = Overlay.NOTIFICATIONS }
        }
    }

    // Abrir la caja pone al día lo del negocio (promociones, precios, cuentas por cobrar en caja), con o sin avisos de Firebase.
    LaunchedEffect(section) { if (section == Section.REGISTER) container.foreground.kick() }

    androidx.compose.runtime.CompositionLocalProvider(com.cuadra.caja.ui.common.LocalModules provides modules) {
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        NoticeBanners(container)
        com.cuadra.caja.ui.screens.NotificationPermissionAsk(container, hasMember = activeMemberId != null)
        if (pinPending && pendingNoticeClosedFor != activeMemberId && overlay != Overlay.PIN_CONFIRM) {
            com.cuadra.caja.ui.screens.Banner(
                stringResource(R.string.pinconfirm_pending_notice), CuadraColors.OrangeSoft, CuadraColors.Orange,
                onClick = { cash.showShift(false); section = Section.MORE; overlay = Overlay.PIN_CONFIRM }, onDismiss = { pendingNoticeClosedFor = activeMemberId },
            )
        }
        Box(Modifier.weight(1f)) {
            when {
                pinPending && section == Section.MORE && (overlay == Overlay.PIN_CONFIRM || overlay in ADMIN_OVERLAYS) ->
                    com.cuadra.caja.ui.screens.PinConfirmScreen(pinConfirmVm) { overlay = if (overlay == Overlay.SCHEDULES) Overlay.NOTIFICATIONS else null }
                overlay == Overlay.ATTENTION && section == Section.MORE -> com.cuadra.caja.ui.screens.AttentionScreen(attentionVm) { overlay = null }
                overlay == Overlay.INVENTORY && section == Section.MORE -> InventoryScreen(inventory, timezone, onPromotions = if (isManager) ({ promotionsFrom = Overlay.INVENTORY; overlay = Overlay.PROMOTIONS }) else null) { overlay = null }
                overlay == Overlay.CATALOG && section == Section.MORE -> InventoryScreen(inventory, timezone, catalogOnly = true, onPromotions = if (isManager) ({ promotionsFrom = Overlay.CATALOG; overlay = Overlay.PROMOTIONS }) else null) { overlay = null }
                // Productos › Promociones: atrás vuelve a Productos.
                overlay == Overlay.PROMOTIONS && section == Section.MORE && isManager -> com.cuadra.caja.ui.screens.PromotionsScreen(promotionsVm) { overlay = promotionsFrom }
                overlay == Overlay.PURCHASES && section == Section.MORE -> PurchasesScreen(purchasesVm, timezone) { overlay = null }
                overlay == Overlay.SUMMARY && section == Section.MORE && isManager -> SummaryScreen(summaryVm, onBack = { overlay = null }, onYesterdayClose = { dailyCloseVm.open(com.cuadra.caja.domain.RangePreset.YESTERDAY); overlay = Overlay.DAILY_CLOSE })
                overlay == Overlay.READER && section == Section.MORE -> ReaderScreen(readerVm) { overlay = null }
                overlay == Overlay.PRINTER && section == Section.MORE -> com.cuadra.caja.ui.screens.PrinterScreen(printerVm) { overlay = null }
                overlay == Overlay.TEXT_SIZE && section == Section.MORE -> TextSizeScreen(fontChoice, container.display::setFontSize) { overlay = null }
                overlay == Overlay.ACCOUNT && section == Section.MORE -> {
                    val info = panelUrl?.trimEnd('/')?.let { "$it/" + if (language.tag == "en") "delete-account" else "eliminar-cuenta" }
                    AccountScreen(
                        accountVm, onDeleteBusiness = if (isOwner) ({ overlay = Overlay.SETTINGS }) else null, onDeleted = root::signOut, infoUrl = info,
                        onInfo = { com.cuadra.caja.ui.common.openInBrowser(context, info) },
                    ) { overlay = null }
                }
                overlay == Overlay.SETTINGS && section == Section.MORE && isManager -> BusinessSettingsScreen(settingsVm) { overlay = null }
                overlay == Overlay.SUPPORT && section == Section.MORE -> com.cuadra.caja.ui.screens.SupportScreen(appCfg?.supportWhatsapp, appCfg?.supportEmail, com.cuadra.caja.domain.SupportContact.isPlay(BuildConfig.DISTRIBUTION)) { overlay = null }
                overlay == Overlay.ACTIVITY && section == Section.MORE && isManager -> ActivityScreen(activityVm) { overlay = null }
                overlay == Overlay.HELP && section == Section.MORE -> HelpScreen(helpVm) { overlay = null }
                overlay == Overlay.TEMPLATES && section == Section.MORE && isManager -> TemplatesScreen(templatesVm) { overlay = null }
                overlay == Overlay.TEAM && section == Section.MORE && hasTeam -> TeamScreen(teamVm, onTab = { overlay = teamTab(it) }, onMyAccount = { overlay = Overlay.ACCOUNT }) { overlay = null }
                overlay == Overlay.DEVICES && section == Section.MORE && hasTeam -> DevicesScreen(devicesVm, onTab = { overlay = teamTab(it) }) { overlay = null }
                overlay == Overlay.DAILY_CLOSE && section == Section.MORE && isManager -> DailyCloseScreen(dailyCloseVm) { overlay = null }
                overlay == Overlay.SCHEDULES && section == Section.MORE && isManager -> SchedulesScreen(schedulesVm) { overlay = Overlay.NOTIFICATIONS }
                overlay == Overlay.NOTIFICATIONS && section == Section.MORE -> NotificationsScreen(notifVm, canSchedule = isManager, onSchedules = { overlay = Overlay.SCHEDULES }, onRoute = { container.pendingRoute.value = it }) { overlay = null }
                cashUi.showShift || mustOpenShift -> ShiftScreen(cash, timezone, required = mustOpenShift, onBack = { cash.showShift(false) })
                else -> when (section) {
                Section.REGISTER -> CajaScreen(caja, container, businessName, memberName, onLock = root::lock, canManageFrequents = com.cuadra.caja.domain.ProductPermissions.canEdit(actingRole))
                Section.CREDITS -> CreditsScreen(credits, container)
                Section.EXPENSES -> ExpensesScreen(cash, hasShifts) { cash.showShift(true) }
                Section.SALES -> HistoryScreen(history, container)
                Section.MORE -> MoreScreen(
                    businessName, memberName, status, pending, failed, language, unread,
                    MoreActions(
                        onLanguageChange = onLanguage, onSyncNow = root::syncNow, onLock = root::lock, onSignOut = root::signOut,
                        onSettings = if (isManager) ({ overlay = Overlay.SETTINGS }) else null,
                        onTeam = if (hasTeam) ({ overlay = Overlay.TEAM }) else null,
                        onActivity = if (isManager) ({ overlay = Overlay.ACTIVITY }) else null,
                        onTemplates = if (isManager) ({ overlay = Overlay.TEMPLATES }) else null,
                        onProducts = if (hasCatalog) ({ overlay = Overlay.CATALOG }) else null,
                        onInventory = if (hasInventory) ({ overlay = Overlay.INVENTORY }) else null,
                        onPurchases = if (hasInventory) ({ overlay = Overlay.PURCHASES }) else null,
                        onSummary = if (isManager) ({ overlay = Overlay.SUMMARY }) else null,
                        onDailyClose = if (isManager) ({ dailyCloseVm.open(com.cuadra.caja.domain.RangePreset.TODAY); overlay = Overlay.DAILY_CLOSE }) else null,
                        onNotifications = { overlay = Overlay.NOTIFICATIONS },
                        onReader = { overlay = Overlay.READER },
                        onPrinter = { overlay = Overlay.PRINTER },
                        onTextSize = { overlay = Overlay.TEXT_SIZE },
                        onHelp = { overlay = Overlay.HELP },
                        onSupport = { overlay = Overlay.SUPPORT },
                        onMyAccount = { overlay = Overlay.ACCOUNT },
                        onPlatformConsole = if (platformAdmin) ({ com.cuadra.caja.ui.common.openInBrowser(context, com.cuadra.caja.domain.PlatformConsole.url(panelUrl)) }) else null,
                        onAttention = { overlay = Overlay.ATTENTION },
                        onOfferWhatsApp = container.display::setOfferWhatsApp,
                        onAskDescription = container.display::setAskDescription,
                    ),
                    offerWhatsApp = offerWhatsApp,
                    askDescription = askDescription,
                )
                }
            }
            // Aviso de una sola vez en un Zebra: el lector del equipo se encendió solo.
            val zebraNotice by container.scanner.zebraNotice.collectAsState()
            if (zebraNotice) {
                LaunchedEffect(Unit) { kotlinx.coroutines.delay(com.cuadra.caja.ui.common.NOTICE_MILLIS * 2); container.scanner.dismissZebraNotice() }
                com.cuadra.caja.ui.common.FloatingNotice(
                    stringResource(R.string.zebra_detected), stringResource(R.string.zebra_detected_ok), container.scanner::dismissZebraNotice,
                    Modifier.align(Alignment.TopCenter).padding(top = 8.dp), icon = R.drawable.ic_reader,
                )
            }
        }
        // Primero el margen del sistema y después la altura: así los 64 dp son del contenido y las letras no se recortan.
        // Cobrar es una tarea completa: mientras dura, la barra de secciones se oculta para dejar todo el espacio a los campos.
        val inCheckout = section == Section.REGISTER && cajaUi.cobro != null
        // Con el teclado del sistema abierto la barra de secciones se oculta (queda tapada de todos modos) y la pantalla sube con `imePadding`.
        @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
        val imeOpen = WindowInsets.isImeVisible
        if (!inCheckout && !mustOpenShift && !imeOpen) {
            val go = { target: Section -> cash.showShift(false); overlay = null; section = target }
            SectionBar(buildList {
                add(SectionEntry(stringResource(R.string.nav_register), R.drawable.ic_nav_register, section == Section.REGISTER) { go(Section.REGISTER) })
                if (hasCredit) add(SectionEntry(stringResource(R.string.nav_credits), R.drawable.ic_nav_credit, section == Section.CREDITS) { go(Section.CREDITS) })
                if (hasExpenses) add(SectionEntry(stringResource(R.string.nav_expenses), R.drawable.ic_nav_expenses, section == Section.EXPENSES) { go(Section.EXPENSES) })
                add(SectionEntry(stringResource(R.string.nav_sales), R.drawable.ic_nav_sales, section == Section.SALES) { go(Section.SALES) })
                add(SectionEntry(stringResource(R.string.nav_more), R.drawable.ic_nav_more, section == Section.MORE) { go(Section.MORE) })
            })
        }
    }
    }
}

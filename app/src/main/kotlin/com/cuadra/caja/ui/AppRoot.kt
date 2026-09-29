package com.cuadra.caja.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.clickable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.cuadra.caja.BuildConfig
import com.cuadra.caja.domain.AppUpdate
import com.cuadra.caja.domain.DonationOffer
import com.cuadra.caja.domain.UpdateNeed
import com.cuadra.caja.ui.screens.NoticeBanners
import com.cuadra.caja.ui.screens.openDonationPage
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
import com.cuadra.caja.ui.screens.ExpensesScreen
import com.cuadra.caja.ui.screens.ShiftScreen
import com.cuadra.caja.ui.screens.InventoryScreen
import com.cuadra.caja.ui.screens.NotificationsScreen
import com.cuadra.caja.ui.screens.SchedulesScreen
import com.cuadra.caja.ui.screens.SummaryScreen
import com.cuadra.caja.ui.screens.PurchasesScreen
import com.cuadra.caja.data.sync.modules
import com.cuadra.caja.ui.screens.HistoryScreen
import com.cuadra.caja.ui.screens.LinkCodeScreen
import com.cuadra.caja.ui.screens.LoginScreen
import com.cuadra.caja.ui.screens.MoreScreen
import com.cuadra.caja.ui.screens.OnboardingScreen
import com.cuadra.caja.ui.screens.PinScreen
import com.cuadra.caja.ui.theme.CuadraColors
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.launch

private enum class Section { REGISTER, CREDITS, EXPENSES, SALES, MORE }

/** Pantallas que se abren desde Más y cubren el contenido (la barra de secciones sigue). */
private enum class Overlay { INVENTORY, PURCHASES, CATALOG, NOTIFICATIONS, SCHEDULES, SUMMARY }

private inline fun <reified T : androidx.lifecycle.ViewModel> factory(crossinline make: () -> T) =
    viewModelFactory { initializer { make() } }

/** Decide qué pantalla toca según el acceso del teléfono: entrar → negocio → elegir persona → caja. */
@Composable
fun AppRoot(container: AppContainer) {
    val root: RootViewModel = viewModel(factory = factory { RootViewModel(container) })
    val auth: AuthViewModel = viewModel(factory = factory { AuthViewModel(container) })
    val session by root.session.collectAsState()
    val business by root.business.collectAsState()
    val authUi by auth.ui.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var language by remember { mutableStateOf(AppLocale.current()) }
    var showLinkCode by rememberSaveable { mutableStateOf(false) }
    val gate = root.gate(session)
    val config by container.appConfig.state.collectAsState(initial = null)
    val blocked = config?.let { AppUpdate.need(BuildConfig.VERSION_NAME, it.minAppVersion, null) } is UpdateNeed.Required

    // Al quedar el teléfono listo (vinculado y con persona), se sincroniza; el resto lo hace WorkManager.
    LaunchedEffect(gate) {
        if (gate == Gate.READY || gate == Gate.PICK_MEMBER) root.syncNow()
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
                showLinkScreen(gate, showLinkCode) -> {
                    LaunchedEffect(Unit) { auth.startLinkCode() }
                    LinkCodeScreen(authUi, onNewCode = auth::startLinkCode, onBack = { auth.stopPolling(); showLinkCode = false })
                }
                gate == Gate.SIGNED_OUT -> LoginScreen(
                    language, onLanguage, authUi,
                    onGoogle = { scope.launch { handleGoogle(context, auth) } },
                    onLinkWithCode = { showLinkCode = true },
                )
                gate == Gate.ONBOARDING -> OnboardingScreen(
                    authUi.me, authUi, onCreate = auth::createBusiness, onUse = auth::useBusiness, onLinkWithCode = { showLinkCode = true }, onSignOut = root::signOut,
                )
                gate == Gate.PICK_MEMBER -> {
                    val pin: PinViewModel = viewModel(factory = factory { PinViewModel(container) })
                    val members by pin.members.collectAsState()
                    val pinUi by pin.ui.collectAsState()
                    // Solo quien entró con Google en este teléfono puede crear su propio PIN.
                    PinScreen(
                        members, pinUi, canCreatePin = session?.userToken != null, onSelect = pin::select, onBack = pin::back,
                        onDigit = pin::digit, onBackspace = pin::backspace, onSubmit = pin::submit, onSignOut = pin::signOut,
                    )
                }
                else -> Main(container, root, business, session?.memberName.orEmpty(), session?.memberRole.orEmpty(), language, onLanguage)
            }
        }
    }
}

private fun showLinkScreen(gate: Gate, requested: Boolean) = requested && (gate == Gate.SIGNED_OUT || gate == Gate.ONBOARDING)

private suspend fun handleGoogle(context: Context, auth: AuthViewModel) {
    when (val r = GoogleSignIn.idToken(context)) {
        is GoogleResult.Token -> auth.googleSignedIn(r.idToken)
        GoogleResult.NotConfigured -> auth.showError(R.string.auth_google_not_configured)
        GoogleResult.NoAccount -> auth.showError(R.string.auth_google_no_account)
        GoogleResult.Cancelled, GoogleResult.Failed -> auth.showError(R.string.auth_google_cancelled)
    }
}

@Composable
private fun Main(
    container: AppContainer, root: RootViewModel, business: com.cuadra.caja.data.local.BusinessEntity?, memberName: String, role: String,
    language: AppLanguage, onLanguage: (AppLanguage) -> Unit,
) {
    val businessName = business?.name.orEmpty()
    val timezone = business?.timezone.orEmpty()
    val appCfg by container.appConfig.state.collectAsState(initial = null)
    val donationUrl = appCfg?.let { DonationOffer.from(it.donationMode, it.donationUrl, BuildConfig.DISTRIBUTION) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val modules = remember(business?.modulesJson) { business?.modules().orEmpty() }
    val hasCredit = modules["credit"] != false
    val hasExpenses = modules["expenses"] != false
    val hasShifts = modules["shifts"] == true
    val isOwner = role == "OWNER"
    val isManager = role == "OWNER" || role == "ADMIN"
    val hasInventory = modules["inventory"] == true && isManager
    // El catálogo es independiente del inventario: se puede vender sin llevar existencias. Con inventario encendido, esa pantalla ya trae todo.
    val hasCatalog = modules["catalog"] != false && !hasInventory && isManager
    var overlay by rememberSaveable { mutableStateOf<Overlay?>(null) }
    val inventory: InventoryViewModel = viewModel(factory = factory { InventoryViewModel(container) })
    val purchasesVm: PurchasesViewModel = viewModel(factory = factory { PurchasesViewModel(container) })
    val notifVm: NotificationsViewModel = viewModel(factory = factory { NotificationsViewModel(container) })
    val unread by notifVm.unread.collectAsState()
    val schedulesVm: SchedulesViewModel = viewModel(factory = factory { SchedulesViewModel(container) })
    val summaryVm: SummaryViewModel = viewModel(factory = factory { SummaryViewModel(container) })
    // Programar avisos se abre desde la bandeja: atrás vuelve a ella.
    androidx.activity.compose.BackHandler(enabled = overlay != null) { overlay = if (overlay == Overlay.SCHEDULES) Overlay.NOTIFICATIONS else null }
    if ((overlay == Overlay.INVENTORY || overlay == Overlay.PURCHASES) && !hasInventory) overlay = null
    if (overlay == Overlay.CATALOG && !hasCatalog) overlay = null
    if ((overlay == Overlay.SCHEDULES || overlay == Overlay.SUMMARY) && !isManager) overlay = null
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
            r.startsWith("cuadra://cierre") -> { section = if (hasExpenses || hasShifts) Section.EXPENSES else Section.REGISTER; if (hasShifts) cash.showShift(true) }
            r.startsWith("cuadra://fiados") -> section = if (hasCredit) Section.CREDITS else Section.REGISTER
            r.startsWith("cuadra://gastos") -> section = if (hasExpenses) Section.EXPENSES else Section.REGISTER
            r.startsWith("cuadra://inventario") -> { section = Section.MORE; overlay = if (hasInventory) Overlay.INVENTORY else if (hasCatalog) Overlay.CATALOG else null }
            else -> { section = Section.MORE; overlay = Overlay.NOTIFICATIONS }
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        NoticeBanners(container)
        Box(Modifier.weight(1f)) {
            when {
                overlay == Overlay.INVENTORY && section == Section.MORE -> InventoryScreen(inventory, timezone) { overlay = null }
                overlay == Overlay.CATALOG && section == Section.MORE -> InventoryScreen(inventory, timezone, catalogOnly = true) { overlay = null }
                overlay == Overlay.PURCHASES && section == Section.MORE -> PurchasesScreen(purchasesVm, timezone) { overlay = null }
                overlay == Overlay.SUMMARY && section == Section.MORE && isManager -> SummaryScreen(summaryVm) { overlay = null }
                overlay == Overlay.SCHEDULES && section == Section.MORE && isManager -> SchedulesScreen(schedulesVm) { overlay = Overlay.NOTIFICATIONS }
                overlay == Overlay.NOTIFICATIONS && section == Section.MORE -> NotificationsScreen(notifVm, canSchedule = isManager, onSchedules = { overlay = Overlay.SCHEDULES }, onRoute = { container.pendingRoute.value = it }) { overlay = null }
                cashUi.showShift || mustOpenShift -> ShiftScreen(cash, timezone, required = mustOpenShift, onBack = { cash.showShift(false) })
                else -> when (section) {
                Section.REGISTER -> CajaScreen(caja, container, businessName, memberName, onLock = root::lock)
                Section.CREDITS -> CreditsScreen(credits, container)
                Section.EXPENSES -> ExpensesScreen(cash, timezone, hasShifts) { cash.showShift(true) }
                Section.SALES -> HistoryScreen(history, timezone)
                Section.MORE -> MoreScreen(businessName, memberName, status, pending, failed, language, onLanguage, root::syncNow, root::lock, root::signOut, if (isOwner) modules else null, cash::setModule,
                    if (hasInventory) ({ overlay = Overlay.INVENTORY }) else null, if (hasInventory) ({ overlay = Overlay.PURCHASES }) else null,
                    if (hasCatalog) ({ overlay = Overlay.CATALOG }) else null, { overlay = Overlay.NOTIFICATIONS }, unread, if (isManager) ({ overlay = Overlay.SUMMARY }) else null,
                    donationUrl?.let { url -> { openDonationPage(context, url) } })
                }
            }
        }
        // Primero el margen del sistema y después la altura: así los 64 dp son del contenido y las letras no se recortan.
        // Cobrar es una tarea completa: mientras dura, la barra de secciones se oculta para dejar todo el espacio a los campos.
        val inCheckout = section == Section.REGISTER && cajaUi.cobro != null
        if (!inCheckout && !mustOpenShift) Row(Modifier.fillMaxWidth().background(CuadraColors.Surface).navigationBarsPadding().height(64.dp)) {
            NavItem(stringResource(R.string.nav_register), section == Section.REGISTER, Modifier.weight(1f)) { cash.showShift(false); overlay = null; section = Section.REGISTER }
            if (hasCredit) NavItem(stringResource(R.string.nav_credits), section == Section.CREDITS, Modifier.weight(1f)) { cash.showShift(false); overlay = null; section = Section.CREDITS }
            if (hasExpenses) NavItem(stringResource(R.string.nav_expenses), section == Section.EXPENSES, Modifier.weight(1f)) { cash.showShift(false); overlay = null; section = Section.EXPENSES }
            NavItem(stringResource(R.string.nav_sales), section == Section.SALES, Modifier.weight(1f)) { cash.showShift(false); overlay = null; section = Section.SALES }
            NavItem(stringResource(R.string.nav_more), section == Section.MORE, Modifier.weight(1f)) { cash.showShift(false); overlay = null; section = Section.MORE }
        }
    }
}

@Composable
private fun NavItem(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier.fillMaxSize().clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Text(
            label, color = if (selected) CuadraColors.Green else CuadraColors.Muted,
            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Bold, style = MaterialTheme.typography.bodyMedium,
        )
    }
}

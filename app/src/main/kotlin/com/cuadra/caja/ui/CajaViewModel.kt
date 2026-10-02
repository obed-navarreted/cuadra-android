package com.cuadra.caja.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cuadra.caja.AppContainer
import com.cuadra.caja.core.model.Currency
import com.cuadra.caja.core.model.Money
import com.cuadra.caja.data.local.BusinessEntity
import com.cuadra.caja.data.local.CustomerEntity
import com.cuadra.caja.data.local.ProductEntity
import com.cuadra.caja.data.local.SaleEntity
import com.cuadra.caja.data.remote.ProductInputDto
import com.cuadra.caja.data.repo.ResumeResult
import com.cuadra.caja.data.repo.SaveCustomer
import com.cuadra.caja.data.repo.ScanLookup
import com.cuadra.caja.data.sync.posViews
import com.cuadra.caja.domain.AddFlow
import com.cuadra.caja.domain.Frequents
import com.cuadra.caja.domain.PosViews
import com.cuadra.caja.domain.ProductSearch
import com.cuadra.caja.domain.ProductsPane
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import com.cuadra.caja.domain.AmountEntry
import com.cuadra.caja.data.sync.calendar
import com.cuadra.caja.domain.Cart
import com.cuadra.caja.domain.CartWithUndo
import com.cuadra.caja.domain.CashTender
import com.cuadra.caja.domain.UndoEntry
import com.cuadra.caja.domain.OpenPriceEntry
import com.cuadra.caja.domain.PayMethod
import com.cuadra.caja.domain.Pricing
import com.cuadra.caja.domain.ProductEditor
import com.cuadra.caja.domain.ProductEditorResult
import com.cuadra.caja.domain.ProductError
import com.cuadra.caja.domain.ProductForm
import com.cuadra.caja.domain.PaymentMethods
import com.cuadra.caja.domain.WhatsAppOffer
import com.cuadra.caja.domain.PaymentPlan
import com.cuadra.caja.data.sync.modules
import com.cuadra.caja.domain.PhoneNumbers
import com.cuadra.caja.domain.PhoneResult
import com.cuadra.caja.domain.ScanCode
import com.cuadra.caja.domain.ScanResolution
import com.cuadra.caja.domain.SaleMath
import com.cuadra.caja.domain.printing.PrintNotice
import com.cuadra.caja.domain.printing.PrinterBadge
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Pestañas de la caja: «Manual» (calculadora) y «Productos» (buscador y frecuentes). */
typealias PosTab = com.cuadra.caja.domain.RegisterTab

/** Venta de un producto por peso: se escribe el peso o el monto y la caja calcula el otro. */
data class Weighing(val product: ProductEntity, val byAmount: Boolean = true, val text: String = "") {
    /** Cantidad en milésimas que resulta de lo escrito, o null si aún no es válido. */
    fun quantityMilli(decimals: Int): Long? {
        if (byAmount) {
            val amount = Money.parse(text, decimals)?.minor ?: return null
            return SaleMath.quantityForAmount(amount, product.priceMinor)
        }
        val bd = runCatching { java.math.BigDecimal(text.ifEmpty { return null }) }.getOrNull() ?: return null
        if (bd.stripTrailingZeros().scale() > 3 || bd.signum() <= 0) return null
        return runCatching { bd.movePointRight(3).longValueExact() }.getOrNull()
    }

    fun totalMinor(decimals: Int): Long? = quantityMilli(decimals)?.let { runCatching { SaleMath.lineTotal(product.priceMinor, it) }.getOrNull() }
}

data class CobroUi(
    val plan: PaymentPlan,
    /**
     * El recibo con las promociones tal como estaba al tocar «Cobrar»: el cobro, el vuelto y lo que se guarda usan ESTE total aunque una promoción cambie
     * mientras tanto (llega una pausa por la sincronización): lo que la persona vio y cobró es lo que queda.
     */
    val priced: com.cuadra.caja.domain.PricedCart? = null,
    val tenderedText: String = "",
    val debtor: String = "",
    val debtorPhone: String = "",
    /** Cliente elegido de la lista (opcional: un nombre escrito basta). */
    val customer: CustomerEntity? = null,
    val saveAsCustomer: Boolean = false,
    val sendWhatsApp: Boolean = true,
    /** Preferencia de este teléfono al empezar el cobro: ¿se ofrece enviar el comprobante por WhatsApp al terminar? (apagada por omisión) */
    val offerWhatsApp: Boolean = false,
    val phoneInvalid: Boolean = false,
    val nameSuggestions: List<String> = emptyList(),
    val customerMatches: List<CustomerEntity> = emptyList(),
    val otherLabel: String = "",
    val saving: Boolean = false,
    /** Venta ya cobrada: muestra el vuelto y "Nueva venta". */
    val doneChangeMinor: Long? = null,
    /** Si hubo fiado, lo necesario para enviar el detalle por WhatsApp. */
    val doneShare: ShareRequest? = null,
    /** Reglas de fiado del negocio (Ajustes del negocio) al empezar el cobro. */
    val requiresCustomer: Boolean = false,
    val limitEnforced: Boolean = false,
    /** Métodos que se ofrecen (sin el módulo Fiado del negocio no hay «Fiado»; ver `PaymentMethods`). */
    val availableMethods: List<PayMethod> = PayMethod.entries,
    /** La venta ya cobrada (para «Imprimir recibo» en la pantalla de venta completa). */
    val doneSaleId: String? = null,
    /** Cuándo se cobró (reloj de este teléfono): «Anular esta venta» se ofrece los primeros 5 minutos. */
    val doneAtMillis: Long? = null,
    /** Cobro en caja (ADR 0015) encendido en el negocio: arriba se elige «Cobrar ahora» o «Enviar a caja». */
    val registerCheckout: Boolean = false,
    /** «Enviar a caja» elegido: sin método de pago ni vuelto, solo una nota opcional. */
    val toRegister: Boolean = false,
    val registerNote: String = "",
    /** Ya enviada a caja: destello corto y venta nueva con el aviso «Enviada a caja · nota». */
    val sent: Boolean = false,
) {
    /** ¿Una regla del negocio impide fiar así? (cliente obligatorio o límite de crédito que bloquea). Nulo si no hay fiado o todo está bien. */
    val creditBlock: com.cuadra.caja.domain.CreditRules.Block?
        get() {
            val credit = plan.effective.firstOrNull { it.method == PayMethod.CREDIT } ?: return null
            return com.cuadra.caja.domain.CreditRules.block(requiresCustomer, limitEnforced, customer != null, saveAsCustomer, customer?.creditLimitMinor, customer?.balanceMinor ?: 0, credit.amountMinor)
        }
}

/** El aviso flotante que queda en la venta nueva tras cobrar («Vuelto C$ X · Anular» / «Venta cobrada · C$ X · Anular»); `undone`: «Venta anulada». */
data class SaleNoticeUi(val saleId: String, val totalMinor: Long, val changeMinor: Long, val doneAtMillis: Long, val undone: Boolean = false,
                        /** «Enviada a caja · nota» (cobro en caja): no es una venta, no ofrece «Anular». */
                        val sent: Boolean = false, val note: String? = null)

/** El detalle de una cuenta «Por cobrar en caja» abierto desde la lista de la caja. */
data class QueueDetailUi(val sale: SaleEntity, val items: List<com.cuadra.caja.data.local.SaleItemEntity>, val promotions: List<com.cuadra.caja.data.local.SalePromotionEntity> = emptyList())

/** «Anular esta cuenta» (por cobrar en caja): motivo obligatorio (5 letras o más). */
data class QueueCancelUi(val saleId: String, val label: String?, val totalMinor: Long, val reason: String = "")

/** La hoja «Anular esta venta» abierta desde el aviso: el motivo que se escribe y si ya pasó el plazo al confirmar. */
data class SaleUndoUi(val saleId: String, val doneAtMillis: Long, val reason: String = "", val tooLate: Boolean = false)

/** Producto nuevo desde la caja. `pricing`: FIXED (precio fijo), BY_WEIGHT (por libra) u OPEN (sin precio fijo: se pregunta al vender; el precio es solo un sugerido). */
data class ProductDraft(
    val name: String = "", val price: String = "", val pricing: String = Pricing.FIXED, val quick: Boolean = true, val barcode: String = "",
    /** Al guardarlo se agrega a la venta (vino de un código desconocido o de «Crear producto “x”»). */
    val addAfterSave: Boolean = false,
)

/** Producto de precio abierto que se está agregando: se escribe el precio de esta venta. */
data class OpenPricing(val product: ProductEntity, val entry: OpenPriceEntry)

sealed interface Notice {
    /** Código que no está en el catálogo: se ofrece crearlo. `checksumOk = false`: parece EAN/UPC pero no pasa la verificación (lectura dudosa). */
    data class CodeUnknown(val code: String, val checksumOk: Boolean = true) : Notice
    data object CodeUnknownOffline : Notice
    /** La cuenta está abierta en otro teléfono; `by`: quién («La está cobrando Ana»), si se sabe. */
    data class TicketLocked(val by: String? = null) : Notice
    data object InvalidProduct : Notice
    /** El código que se quiso guardar ya lo tiene otro producto. */
    data class BarcodeInUse(val owner: String) : Notice
}

data class CajaUi(
    val cart: Cart = Cart(),
    val entry: AmountEntry = AmountEntry(),
    val description: String = "",
    val tab: PosTab = PosTab.MANUAL,
    val query: String = "",
    val resumedId: String? = null,
    val resumedLabel: String? = null,
    /** La cuenta retomada estaba «Por cobrar en caja»: «Vaciar» la devuelve a la lista (anularla pide motivo y se hace desde su detalle). */
    val resumedPending: Boolean = false,
    /** Detalle y anulación de una cuenta por cobrar en caja (ADR 0015). */
    val queueDetail: QueueDetailUi? = null,
    val queueCancel: QueueCancelUi? = null,
    val weighing: Weighing? = null,
    val openPrice: OpenPricing? = null,
    val cobro: CobroUi? = null,
    val draft: ProductDraft? = null,
    val parking: Boolean = false,
    val showParked: Boolean = false,
    /** «Por cobrar en caja» / Apartadas se está refrescando (lo de antes sigue a la vista). */
    val queueRefreshing: Boolean = false,
    val notice: Notice? = null,
    val scanning: Boolean = false,
    val scanAdded: Int = 0,
    val share: ShareRequest? = null,
    /** Hoja con el detalle del recibo abierta. */
    val receiptOpen: Boolean = false,
    /** Línea cuya cantidad se está escribiendo (diálogo), si hay. */
    val editingLineId: String? = null,
    /** Pila de «deshacer» (lo último al final). La tira muestra la última mientras `undoShown`. */
    val undoStack: List<UndoEntry> = emptyList(),
    val undoShown: Boolean = false,
    /** Cambia con cada aviso nuevo: reinicia el conteo de los ~4 s para ocultar la tira. */
    val undoTick: Int = 0,
    /** Pista de los gestos de la hoja del recibo (solo la primera vez). */
    val swipeHint: Boolean = false,
    /** Estado de la impresora: `OFF` si la opción está desactivada (entonces no se dibuja nada de impresión). */
    val printer: PrinterBadge = PrinterBadge.OFF,
    /** Aviso flotante de impresión («Impreso», «Sin impresora conectada», «No se pudo imprimir»); `printTick` reinicia su conteo. */
    val printNotice: PrintNotice? = null,
    val printTick: Int = 0,
    /** Aviso de la última venta cobrada (vuelto / anular); se va con el primer toque del teclado o de un producto, o a los 8 s. */
    val saleNotice: SaleNoticeUi? = null,
    val saleUndo: SaleUndoUi? = null,
    /** Producto con el menú de frecuentes abierto (pulsación larga). */
    val productMenu: ProductEntity? = null,
    /** Pestaña Productos en modo «Ordenar frecuentes». */
    val reordering: Boolean = false,
    /** La línea que muestra la tira «última línea» (la última agregada o tocada); `null` = la última del recibo (ver `LastLine.of`). */
    val lastLineId: String? = null,
    /** Promociones por cantidad del negocio (todas: el motor se queda con las que valen en `promoDay`), cómo se vende cada producto y la jornada de hoy. */
    val promotions: List<com.cuadra.caja.domain.PromotionRule> = emptyList(),
    val pricingById: Map<String, String> = emptyMap(),
    val promoDay: java.time.LocalDate = java.time.LocalDate.of(2000, 1, 1),
) {
    /**
     * El recibo con las promociones aplicadas (se recalcula con cada cambio del recibo, de las promociones o del día): su total es el que se cobra; sus líneas
     * llevan el descuento repartido. `cart` sigue siendo el recibo sin descuentos (así sumar el mismo producto agrupa y deshacer funciona igual).
     */
    val priced: com.cuadra.caja.domain.PricedCart by lazy {
        com.cuadra.caja.domain.PromotionEngine.price(cart, promotions, promoDay) { pricingById[it] }
    }

    /**
     * El aviso flotante «Deshacer»: solo para lo que se QUITA (línea eliminada, recibo vaciado). Agregar ya no abre aviso: la tira de la última línea
     * lleva su propio − / ✕, así que ese popup sobraba (y tapaba el hueco donde ahora vive la tira).
     */
    val undo: UndoEntry? get() = if (undoShown) undoStack.lastOrNull()?.takeIf { it !is UndoEntry.Added } else null

    /** La línea de la tira «última línea», o `null` con el recibo vacío. */
    val lastLine get() = com.cuadra.caja.domain.LastLine.of(priced.cart, lastLineId)
    val editingLine get() = editingLineId?.let { id -> cart.lines.firstOrNull { it.id == id } }
}

/** A dónde va una lectura del lector físico: a la venta, al campo del código del editor de producto, o se ignora (cobro, diálogos con teclado). */
enum class HardwareScanRoute { ADD, EDITOR, IGNORE }

fun CajaUi.hardwareScanRoute(): HardwareScanRoute = when {
    cobro != null || saleUndo != null || queueDetail != null || queueCancel != null || weighing != null || openPrice != null || receiptOpen || editingLineId != null || parking || showParked || notice != null || share != null || productMenu != null -> HardwareScanRoute.IGNORE
    draft != null -> HardwareScanRoute.EDITOR
    else -> HardwareScanRoute.ADD
}

class CajaViewModel(private val c: AppContainer) : ViewModel(), CajaActions {

    /** «Por cobrar en caja» y Apartadas: al abrirlas, al volver al frente, al deslizar y cada 15 s mientras están a la vista (llegan cuentas de otros teléfonos). */
    val refresher = ScreenRefresh(viewModelScope) { c.pullNow() }
    private val _ui = MutableStateFlow(CajaUi())

    init {
        viewModelScope.launch { refresher.refreshing.collect { busy -> _ui.update { it.copy(queueRefreshing = busy) } } }
    }
    val ui: StateFlow<CajaUi> = _ui.asStateFlow()

    val business: StateFlow<BusinessEntity?> = c.db.directory().business().stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Pestañas que este negocio usa (`pos_views`: TYPE → Manual; QUICK/LIST → Productos; al menos una). */
    val tabs: StateFlow<List<PosTab>> = business.map { b -> PosViews.tabs(runCatching { b?.posViews() }.getOrNull().orEmpty()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, listOf(PosTab.MANUAL))

    /** El catálogo activo, ya preparado para buscar (se rehace solo cuando cambia el catálogo). */
    private val catalog: StateFlow<Pair<List<ProductEntity>, ProductSearch.Index>> = c.products.active().map { it to ProductSearch.Index(it) }
        .flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList<ProductEntity>() to ProductSearch.Index(emptyList()))

    /** Los más vendidos de las últimas 30 jornadas (ventas de este teléfono), para completar los frecuentes. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val bestSellers: StateFlow<List<String>> = business.flatMapLatest { b ->
        if (b == null) flowOf(emptyList()) else {
            val cal = b.calendar()
            val today = cal.dateOf(Instant.now())
            c.sales.bestSellers(cal.startMillis(today.minusDays(ProductSearch.BEST_SELLER_DAYS - 1L)))
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** La pestaña «Productos»: frecuentes (+ más vendidos) con el buscador vacío; resultados con texto. */
    val products: StateFlow<ProductsPane> = combine(_ui.map { it.query }.distinctUntilChanged(), catalog, bestSellers) { q, (all, index), best ->
        ProductSearch.pane(index, all, q, best)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, ProductsPane())

    val parked: StateFlow<List<SaleEntity>> = c.sales.parked().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val parkedLineCounts: StateFlow<Map<String, Int>> = c.sales.parkedLineCounts().stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    /** Cobro en caja (ADR 0015) encendido en este negocio. */
    val registerCheckout: StateFlow<Boolean> = business.map { it?.registerCheckout == true }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** Lo vendido en la jornada actual según lo que este teléfono conoce (efectivo, fiado, etc.). */
    @OptIn(ExperimentalCoroutinesApi::class)
    val soldToday: StateFlow<Long> = combine(business, c.sessionStore.flow.map { com.cuadra.caja.domain.SoldToday.memberFilter(it.memberRole, it.memberId) }.distinctUntilChanged()) { b, member -> b to member }
        .flatMapLatest { (b, member) ->
            if (b == null) flowOf(0L) else {
                val cal = b.calendar()
                val day = cal.day(cal.dateOf(Instant.now()))
                // Un cajero ve solo lo que cobró él; dueño y admins, todo el negocio (`SoldToday`).
                (if (member == null) c.sales.dayTotals(day.startMillis, day.endMillis) else c.sales.dayTotalsBy(day.startMillis, day.endMillis, member)).map { it.total }
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, 0L)

    init {
        // Promociones: las del negocio, cómo se vende cada producto de ellas (por peso y precio abierto no entran) y la jornada de hoy (cambia sola a medianoche
        // del negocio: se revisa cada minuto).
        val day = kotlinx.coroutines.flow.flow {
            while (true) {
                emit(business.value?.calendar()?.dateOf(Instant.now()))
                kotlinx.coroutines.delay(60_000)
            }
        }.distinctUntilChanged()
        combine(c.promotions.rules(), catalog, business, day) { rules, (all, _), b, d ->
            val wanted = rules.flatMapTo(HashSet()) { it.productIds }
            Triple(rules, all.filter { it.id in wanted }.associate { it.id to it.pricing }, d ?: b?.calendar()?.dateOf(Instant.now()))
        }.onEach { (rules, pricing, d) -> _ui.update { it.copy(promotions = rules, pricingById = pricing, promoDay = d ?: it.promoDay) } }.launchIn(viewModelScope)
    }

    init {
        // El indicador de la impresora sigue al estado de la conexión (solo existe con la opción activada).
        c.printer.badge.onEach { b -> _ui.update { it.copy(printer = b) } }.launchIn(viewModelScope)
    }

    private val decimals: Int get() = business.value?.let { Currency.of(it.currency).decimals } ?: 2

    // ---------- teclado ----------
    override fun key(d: Char) = _ui.update { it.copy(entry = it.entry.digit(d, decimals), saleNotice = null) }
    override fun dot() = _ui.update { it.copy(entry = it.entry.dot(decimals), saleNotice = null) }
    override fun times() = _ui.update { it.copy(entry = it.entry.times(), saleNotice = null) }
    override fun backspace() = _ui.update { it.copy(entry = it.entry.backspace(), saleNotice = null) }
    override fun setDescription(text: String) = _ui.update { it.copy(description = text.take(80)) }
    override fun setTab(tab: PosTab) = _ui.update { it.copy(tab = tab) }

    /** Aplica un cambio del recibo con su pila de «deshacer»: si apila algo, se muestra la tira. */
    private fun edit(f: (CartWithUndo) -> CartWithUndo) = _ui.update { s ->
        val next = f(CartWithUndo(s.cart, s.undoStack))
        s.copy(
            cart = next.cart, undoStack = next.stack, saleNotice = null, lastLineId = com.cuadra.caja.domain.LastLine.pick(s.lastLineId, s.cart, next.cart),
            undoShown = if (next.pushed) true else s.undoShown && next.stack.isNotEmpty(), undoTick = if (next.pushed) s.undoTick + 1 else s.undoTick,
        )
    }

    override fun addEntry() {
        val s = _ui.value
        val r = s.entry.result(decimals) ?: return
        edit { it.addFree(r.unitPriceMinor, s.description, r.quantityMilli) }
        _ui.update { it.copy(entry = AmountEntry(), description = "") }
    }

    // ---------- productos ----------
    override fun tapProduct(p: ProductEntity) {
        when (ProductSearch.addFlow(p)) {
            AddFlow.WEIGH -> _ui.update { it.copy(weighing = Weighing(p)) }
            AddFlow.OPEN_PRICE -> _ui.update { it.copy(openPrice = OpenPricing(p, OpenPriceEntry.start(p.priceMinor, decimals))) }
            AddFlow.DIRECT -> edit { it.addProduct(p.id, p.barcode, p.name, p.variant, p.priceMinor, p.costMinor) }
        }
    }

    // ---------- frecuentes ----------
    override fun openProductMenu(p: ProductEntity) = _ui.update { it.copy(productMenu = p) }
    override fun closeProductMenu() = _ui.update { it.copy(productMenu = null) }

    /** Marca (al final) o quita un frecuente: un PRODUCT_UPSERT que conserva todo lo demás del producto. */
    override fun toggleFrequent(p: ProductEntity) {
        _ui.update { it.copy(productMenu = null) }
        viewModelScope.launch {
            val current = c.db.products().get(p.id) ?: p
            c.products.save(current.id, Frequents.toggle(current, catalog.value.first))
        }
    }

    override fun startReorder() = _ui.update { it.copy(reordering = true, query = "") }
    override fun finishReorder() = _ui.update { it.copy(reordering = false) }

    override fun moveFrequent(productId: String, delta: Int) {
        viewModelScope.launch { c.products.saveAll(Frequents.move(products.value.marked, productId, delta)) }
    }

    override fun createFromQuery(query: String) {
        val (name, barcode) = ProductSearch.draftFor(query)
        _ui.update { it.copy(draft = ProductDraft(name = name, barcode = barcode, quick = false, addAfterSave = true)) }
    }

    override fun setQuery(q: String) = _ui.update { it.copy(query = q.take(80), reordering = if (q.isNotBlank()) false else it.reordering) }

    /** Lo escrito en el buscador tiene forma de código: se busca exacto (local, UPC/EAN, y si hace falta el servidor). */
    override fun submitCode(raw: String) = lookupCode(raw)

    private fun lookupCode(raw: String, onFound: () -> Unit = {}) {
        viewModelScope.launch {
            var offline = false
            val r = ScanCode.resolve(raw) { code ->
                when (val l = c.products.byBarcode(code)) {
                    is ScanLookup.Found -> l.product
                    ScanLookup.UnknownOffline -> { offline = true; null }
                    ScanLookup.Unknown -> null
                }
            }
            when (r) {
                ScanResolution.Empty -> Unit
                is ScanResolution.Found -> { tapProduct(r.item); setQuery(""); onFound() }
                is ScanResolution.NotFound ->
                    if (offline) _ui.update { it.copy(notice = Notice.CodeUnknownOffline) }
                    else _ui.update { it.copy(notice = Notice.CodeUnknown(r.code, r.checksumOk)) }
            }
        }
    }

    // ---------- lector de códigos ----------
    override fun openScanner() = _ui.update { it.copy(scanning = true, scanAdded = 0) }
    override fun closeScanner() = _ui.update { it.copy(scanning = false) }

    /** Un código leído o tecleado en el lector: producto → a la venta (los de peso abren su diálogo); desconocido → ofrece crearlo. */
    override fun onScanned(raw: String) = lookupCode(raw) { _ui.update { it.copy(scanAdded = it.scanAdded + 1) } }

    /** Una lectura del lector físico (teclado o DataWedge): misma ruta que la cámara, salvo lo que haya abierto (`hardwareScanRoute`). */
    fun onHardwareScan(code: String) {
        when (_ui.value.hardwareScanRoute()) {
            HardwareScanRoute.ADD -> onScanned(code)
            HardwareScanRoute.EDITOR -> _ui.value.draft?.let { updateDraft(it.copy(barcode = code.take(ProductEditor.MAX_BARCODE))) }
            HardwareScanRoute.IGNORE -> Unit
        }
    }

    /** Desde el aviso de código desconocido: abre el editor con el código ya puesto (al guardar, el producto se agrega a la venta). */
    override fun createFromCode(code: String) = _ui.update { it.copy(notice = null, draft = ProductDraft(barcode = code, addAfterSave = true)) }

    override fun dismissNotice() = _ui.update { it.copy(notice = null) }

    // ---------- pesar ----------
    override fun weighingMode(byAmount: Boolean) = _ui.update { s -> s.weighing?.let { s.copy(weighing = it.copy(byAmount = byAmount, text = "")) } ?: s }
    override fun weighingKey(d: Char) = _ui.update { s -> s.weighing?.let { s.copy(weighing = it.copy(text = (it.text + d).take(10))) } ?: s }
    override fun weighingDot() = _ui.update { s -> s.weighing?.takeIf { !it.text.contains('.') }?.let { s.copy(weighing = it.copy(text = it.text.ifEmpty { "0" } + ".")) } ?: s }
    override fun weighingBackspace() = _ui.update { s -> s.weighing?.let { s.copy(weighing = it.copy(text = it.text.dropLast(1))) } ?: s }
    override fun weighingCancel() = _ui.update { it.copy(weighing = null) }
    fun weighingPreset(text: String, byAmount: Boolean) = _ui.update { s -> s.weighing?.let { s.copy(weighing = it.copy(byAmount = byAmount, text = text)) } ?: s }

    override fun weighingConfirm() {
        val w = _ui.value.weighing ?: return
        val qty = w.quantityMilli(decimals) ?: return
        edit { it.addProduct(w.product.id, w.product.barcode, w.product.name, w.product.variant, w.product.priceMinor, w.product.costMinor, qty, byWeight = true) }
        _ui.update { it.copy(weighing = null) }
    }

    // ---------- precio abierto ----------
    private fun updateOpenPrice(f: (OpenPriceEntry) -> OpenPriceEntry) = _ui.update { s -> s.openPrice?.let { s.copy(openPrice = it.copy(entry = f(it.entry))) } ?: s }
    override fun openPriceKey(d: Char) = updateOpenPrice { it.digit(d, decimals) }
    override fun openPriceDot() = updateOpenPrice { it.dot(decimals) }
    override fun openPriceBackspace() = updateOpenPrice { it.backspace() }
    override fun openPriceCancel() = _ui.update { it.copy(openPrice = null) }

    /** Agrega la línea (cantidad 1) al precio escrito. Ese precio queda en la línea: cambiar la cantidad no lo toca. */
    override fun openPriceConfirm() {
        val o = _ui.value.openPrice ?: return
        val price = o.entry.minor(decimals) ?: return
        val p = o.product
        edit { it.addProduct(p.id, p.barcode, p.name, p.variant, price, p.costMinor) }
        _ui.update { it.copy(openPrice = null) }
    }

    // ---------- recibo ----------
    override fun changeQuantity(lineId: String, deltaMilli: Long) = edit { it.changeQuantity(lineId, deltaMilli) }

    /** Quita la línea completa (con «Deshacer»). */
    override fun deleteLine(lineId: String) {
        edit { it.deleteLine(lineId) }
        _ui.update { it.copy(editingLineId = null) }
    }

    override fun editQuantity(lineId: String) = _ui.update { s -> if (s.cart.lines.any { it.id == lineId }) s.copy(editingLineId = lineId) else s }
    override fun closeQuantityEdit() = _ui.update { it.copy(editingLineId = null) }

    /** Hoja de edición de la línea: cantidad (en milésimas; 0 la quita con «Deshacer») y, en una línea manual, la descripción. */
    override fun editLine(lineId: String, description: String?, milli: Long) {
        edit { it.editLine(lineId, description, milli) }
        _ui.update { it.copy(editingLineId = null) }
    }

    /**
     * «Vaciar». Si la cuenta era una apartada retomada, vaciarla la DESCARTA (en el teléfono y en el servidor): si no, seguiría viva como apartada y lo que
     * se deshiciera y cobrara después saldría con otro id, dejando la misma cuenta cobrable dos veces.
     */
    override fun clearCart() {
        val resumed = _ui.value.resumedId
        val pending = _ui.value.resumedPending
        edit { it.clear() }
        _ui.update { it.copy(resumedId = null, resumedLabel = null, resumedPending = false) }
        // Una cuenta por cobrar en caja no se descarta al vaciar: vuelve a la lista tal como estaba (se anula con motivo desde su detalle).
        if (resumed != null) viewModelScope.launch { if (pending) c.sales.releaseToQueue(resumed) else c.sales.cancel(resumed, null) }
    }

    // ---------- «deshacer» y hoja del recibo ----------
    /** Revierte exactamente lo último apilado (una alta, una línea quitada o el vaciado). Si queda algo más, la tira lo ofrece de nuevo. */
    override fun undoLast() {
        edit { it.undoLast() }
        _ui.update { s -> s.copy(undoShown = s.undoStack.isNotEmpty(), undoTick = s.undoTick + 1) }
    }

    override fun hideUndo() = _ui.update { it.copy(undoShown = false) }

    override fun openReceipt() {
        _ui.update { it.copy(receiptOpen = true) }
        if (_ui.value.cart.isEmpty) return
        viewModelScope.launch {
            if (!c.sessionStore.swipeHintSeen()) {
                _ui.update { it.copy(swipeHint = true) }
                c.sessionStore.markSwipeHintSeen()
            }
        }
    }

    override fun closeReceipt() = _ui.update { it.copy(receiptOpen = false, swipeHint = false, editingLineId = null) }
    override fun dismissSwipeHint() = _ui.update { it.copy(swipeHint = false) }

    // ---------- crear producto ----------
    override fun openDraft() = _ui.update { it.copy(draft = ProductDraft()) }
    override fun updateDraft(draft: ProductDraft) = _ui.update { it.copy(draft = draft) }
    override fun closeDraft() = _ui.update { it.copy(draft = null) }

    override fun saveDraft() {
        val d = _ui.value.draft ?: return
        viewModelScope.launch {
            val owner = c.products.ownerOfBarcode(d.barcode, null)
            val form = ProductForm(name = d.name, barcode = d.barcode, pricing = d.pricing, unit = "LB", price = d.price, isQuick = d.quick)
            val result = ProductEditor.build(null, form, decimals, owner?.name)
            // Desde la caja un precio fijo o por peso en cero no sirve para vender: se pide un precio de verdad.
            val zeroPrice = d.pricing != Pricing.OPEN && Money.parse(d.price, decimals)?.minor == 0L
            when {
                result is ProductEditorResult.Invalid && ProductError.BARCODE_IN_USE in result.errors -> _ui.update { it.copy(notice = Notice.BarcodeInUse(owner?.name.orEmpty())) }
                result is ProductEditorResult.Invalid || zeroPrice -> _ui.update { it.copy(notice = Notice.InvalidProduct) }
                result is ProductEditorResult.Valid -> {
                    val saved = c.products.save(null, result.input)
                    _ui.update { it.copy(draft = null, notice = null, query = if (d.addAfterSave) "" else it.query) }
                    // Vino de un código desconocido o de «Crear producto “x”»: se agrega a la venta que se estaba haciendo.
                    if (d.addAfterSave || d.barcode.isNotBlank()) tapProduct(saved)
                }
            }
        }
    }

    // ---------- apartar / retomar ----------
    override fun askPark() = _ui.update { if (it.cart.isEmpty) it else it.copy(parking = true, receiptOpen = false, undoShown = false) }
    override fun cancelPark() = _ui.update { it.copy(parking = false) }
    override fun toggleParked(show: Boolean) = _ui.update { it.copy(showParked = show) }
    override fun refreshQueue(trigger: com.cuadra.caja.domain.RefreshTrigger) { refresher.request(trigger) }

    override fun park(label: String) {
        val s = _ui.value
        if (s.cart.isEmpty) return
        viewModelScope.launch {
            c.sales.park(s.priced.cart, label.ifBlank { s.resumedLabel }, s.resumedId ?: java.util.UUID.randomUUID().toString(), s.priced.promo.applied)
            _ui.update { it.copy(cart = Cart(), resumedId = null, resumedLabel = null, resumedPending = false, parking = false, entry = AmountEntry(), description = "", undoStack = emptyList(), undoShown = false, lastLineId = null) }
        }
    }

    override fun resume(saleId: String) {
        viewModelScope.launch { resumeNow(saleId) }
    }

    /** Retoma la cuenta en la caja; `false` si no se pudo (abierta en otro teléfono: avisa quién la tiene). */
    private suspend fun resumeNow(saleId: String): Boolean {
        // Lo que había en pantalla se aparta antes de retomar otra cuenta: nunca se pierde (PLAN: ARMarket 2.2).
        val current = _ui.value
        when (val r = c.sales.resume(saleId)) {
            is ResumeResult.Ok -> {
                if (!current.cart.isEmpty) c.sales.park(current.priced.cart, current.resumedLabel, current.resumedId ?: java.util.UUID.randomUUID().toString(), current.priced.promo.applied)
                _ui.update { it.copy(cart = r.sale.cart, resumedId = r.sale.saleId, resumedLabel = r.sale.label, resumedPending = r.sale.pendingCheckout, showParked = false, queueDetail = null,
                    undoStack = emptyList(), undoShown = false, lastLineId = null) }
                return true
            }
            is ResumeResult.Locked -> { _ui.update { it.copy(notice = Notice.TicketLocked(r.by)) }; return false }
            ResumeResult.NotFound -> return false
        }
    }

    // ---------- cobro en caja (ADR 0015) ----------
    override fun openQueueTicket(saleId: String) {
        viewModelScope.launch {
            val (sale, items, _) = c.sales.detail(saleId) ?: return@launch
            val promos = c.sales.promotionsOf(saleId)
            _ui.update { it.copy(queueDetail = QueueDetailUi(sale, items.sortedBy { i -> i.position }, promos)) }
        }
    }

    override fun closeQueueTicket() = _ui.update { it.copy(queueDetail = null) }

    /** «Cobrar»: la carga en la caja (con la reserva del servidor: otro cajero no la cobra a la vez) y abre «Cobrar» con método y vuelto por decidir. */
    override fun chargeQueued(saleId: String) {
        viewModelScope.launch {
            if (resumeNow(saleId)) {
                _ui.update { it.copy(queueDetail = null, showParked = false) }
                startCobro()
                updateCobro { it.copy(toRegister = false) }
            }
        }
    }

    /** «Agregar productos»: la carga en la caja para sumar líneas; después se vuelve a enviar a caja o se cobra. */
    override fun addToQueued(saleId: String) {
        viewModelScope.launch { if (resumeNow(saleId)) _ui.update { it.copy(queueDetail = null, showParked = false) } }
    }

    override fun askCancelQueued(saleId: String) = _ui.update { s ->
        val d = s.queueDetail?.sale?.takeIf { it.id == saleId }
        s.copy(queueCancel = QueueCancelUi(saleId, d?.label, d?.totalMinor ?: 0))
    }

    override fun setQueueCancelReason(text: String) = _ui.update { s -> s.copy(queueCancel = s.queueCancel?.copy(reason = text.take(com.cuadra.caja.domain.SaleDeletion.MAX_REASON))) }
    override fun closeQueueCancel() = _ui.update { it.copy(queueCancel = null) }

    override fun confirmCancelQueued() {
        val q = _ui.value.queueCancel ?: return
        val reason = com.cuadra.caja.domain.SaleDeletion.clean(q.reason) ?: return
        _ui.update { it.copy(queueCancel = null, queueDetail = null) }
        viewModelScope.launch { c.sales.cancel(q.saleId, reason) }
    }

    override fun setCobroMode(toRegister: Boolean) = updateCobro { if (!it.registerCheckout) it else it.copy(toRegister = toRegister) }
    override fun setRegisterNote(text: String) = updateCobro { it.copy(registerNote = text.take(com.cuadra.caja.domain.RegisterQueue.NOTE_MAX)) }

    /** «Enviar a caja»: sin método ni vuelto. Va por la cola (sin conexión también) y aparece en «Por cobrar en caja» de todos los teléfonos. */
    override fun confirmSendToRegister() {
        val s = _ui.value
        val cobro = s.cobro ?: return
        if (!cobro.registerCheckout || !cobro.toRegister || cobro.saving || s.cart.isEmpty) return
        _ui.update { it.copy(cobro = cobro.copy(saving = true)) }
        viewModelScope.launch {
            val note = com.cuadra.caja.domain.RegisterQueue.cleanNote(cobro.registerNote)
            val priced = cobro.priced ?: s.priced
            val saleId = c.sales.sendToRegister(priced.cart, note, s.resumedId ?: java.util.UUID.randomUUID().toString(), priced.promo.applied)
            _ui.update { it.copy(cobro = cobro.copy(saving = false, sent = true, registerNote = note.orEmpty(), doneSaleId = saleId, doneAtMillis = System.currentTimeMillis())) }
        }
    }

    override fun discardParked(saleId: String) {
        viewModelScope.launch { c.sales.cancel(saleId, null) }
    }

    // ---------- cobro ----------
    override fun startCobro() = _ui.update {
        val priced = it.priced
        if (it.cart.isEmpty || priced.totalMinor <= 0) it
        else it.copy(
            cobro = CobroUi(
                PaymentPlan.cash(priced.totalMinor), priced = priced, requiresCustomer = business.value?.creditRequiresCustomer == true, limitEnforced = business.value?.creditLimitEnforced == true,
                availableMethods = PaymentMethods.available(business.value?.let { b -> runCatching { b.modules() }.getOrNull() }.orEmpty()),
                offerWhatsApp = c.display.offerWhatsApp.value,
                registerCheckout = business.value?.registerCheckout == true, registerNote = it.resumedLabel.orEmpty(),
            ),
            receiptOpen = false, undoShown = false, editingLineId = null, saleNotice = null,
        )
    }
    override fun cancelCobro() = _ui.update { it.copy(cobro = null) }

    private fun updateCobro(f: (CobroUi) -> CobroUi) = _ui.update { s -> s.cobro?.takeIf { it.doneChangeMinor == null && !it.sent }?.let { s.copy(cobro = f(it)) } ?: s }

    /**
     * Lo escrito en "Recibido" manda sobre el efectivo entregado: tras cualquier cambio del reparto se vuelve a aplicar al plan (así el vuelto
     * y el "aún faltan" siempre se calculan con lo que se ve en el campo). Si estaba en "exacto" sigue exacto con el monto nuevo.
     */
    private fun CobroUi.retender(oldCashMinor: Long?): CobroUi {
        val cash = plan.entries.firstOrNull { it.method == PayMethod.CASH } ?: return copy(tenderedText = "")
        var text = tenderedText
        if (oldCashMinor != null && text.isNotEmpty() && text == CashTender.toText(oldCashMinor, decimals)) text = CashTender.toText(cash.amountMinor, decimals)
        return copy(tenderedText = text, plan = plan.withTendered(CashTender.parseMinor(text, decimals)))
    }

    private fun CobroUi.cashAmount(): Long? = plan.entries.firstOrNull { it.method == PayMethod.CASH }?.amountMinor

    override fun toggleMethod(m: PayMethod) = updateCobro { c ->
        if (m !in c.availableMethods) return@updateCobro c
        val has = c.plan.entries.any { it.method == m }
        val plan = if (has) (if (c.plan.entries.size > 1) c.plan.withoutMethod(m) else c.plan) else c.plan.withMethod(m)
        c.copy(plan = plan).retender(c.cashAmount())
    }

    override fun setAmount(m: PayMethod, text: String) = updateCobro { c ->
        val minor = Money.parse(CashTender.sanitize(text, decimals), decimals)?.minor ?: 0
        c.copy(plan = c.plan.withAmount(m, minor)).retender(c.cashAmount())
    }

    override fun completeWith(m: PayMethod) = updateCobro { c ->
        if (m !in c.availableMethods) c else c.copy(plan = c.plan.completeWith(m)).retender(c.cashAmount())
    }

    /** Un billete sugerido o "Exacto" (null): rellena el campo "Recibido". */
    override fun setTendered(minor: Long?) = updateCobro { it.copy(plan = it.plan.withTendered(minor), tenderedText = minor?.let { m -> CashTender.toText(m, decimals) } ?: "") }

    /** Lo que se escribe en "Recibido": se limpia (dígitos y un separador, decimales de la moneda) y se aplica al efectivo. */
    override fun setTenderedText(raw: String) = updateCobro { c ->
        val text = CashTender.sanitize(raw, decimals)
        c.copy(tenderedText = text, plan = c.plan.withTendered(CashTender.parseMinor(text, decimals)))
    }

    /** Escribir el nombre de quien debe: suelta el cliente elegido antes (el texto manda) y busca sugerencias. */
    override fun setDebtor(text: String) {
        updateCobro { it.copy(debtor = text.take(80), customer = null, plan = it.plan.withDebtor(text, it.debtorPhone, null)) }
        viewModelScope.launch {
            val names = c.customers.nameSuggestions(text)
            val matches = if (text.isBlank()) emptyList() else c.customers.search(text).first().take(4)
            updateCobro { it.copy(nameSuggestions = names.filter { n -> matches.none { m -> m.name == n } }, customerMatches = matches) }
        }
    }

    override fun setDebtorPhone(text: String) = updateCobro { it.copy(debtorPhone = text.take(20), phoneInvalid = false, plan = it.plan.withDebtor(it.debtor, text, it.customer?.id)) }

    override fun pickCustomer(cust: CustomerEntity) = updateCobro {
        it.copy(debtor = cust.name, debtorPhone = cust.phone.orEmpty(), customer = cust, nameSuggestions = emptyList(), customerMatches = emptyList(), phoneInvalid = false,
            plan = it.plan.withDebtor(cust.name, cust.phone, cust.id))
    }

    override fun pickName(name: String) = updateCobro { it.copy(debtor = name, nameSuggestions = emptyList(), customerMatches = emptyList(), plan = it.plan.withDebtor(name, it.debtorPhone, null)) }
    override fun toggleSaveCustomer() = updateCobro { it.copy(saveAsCustomer = !it.saveAsCustomer) }
    override fun toggleSendWhatsApp() = updateCobro { it.copy(sendWhatsApp = !it.sendWhatsApp) }
    /** Cerrar el envío por WhatsApp: si se abrió sobre «Venta cobrada», de ahí se pasa a la venta nueva. */
    override fun shareDismiss() {
        _ui.update { it.copy(share = null) }
        if (_ui.value.cobro?.doneSaleId != null) finishCobro()
    }
    override fun shareDone(request: ShareRequest?) = _ui.update { it.copy(share = request) }
    override fun setOtherLabel(text: String) = updateCobro { c ->
        c.copy(otherLabel = text.take(40), plan = PaymentPlan(c.plan.totalMinor, c.plan.entries.map { if (it.method == PayMethod.OTHER) it.copy(otherLabel = text.trim().ifEmpty { null }) else it }))
    }
    override fun setReference(m: PayMethod, text: String) = updateCobro { it.copy(plan = it.plan.withReference(m, text)) }

    override fun confirmCobro() {
        val s = _ui.value
        val cobro = s.cobro ?: return
        if (!cobro.plan.isValid || cobro.saving || cobro.creditBlock != null) return
        val credit = cobro.plan.effective.firstOrNull { it.method == PayMethod.CREDIT }
        val country = business.value?.country
        // Un teléfono a medias no se guarda: se avisa antes de cobrar (con la venta ya hecha no se podría corregir sin editarla).
        if (credit != null && PhoneNumbers.normalize(cobro.debtorPhone, country) == PhoneResult.Invalid) {
            _ui.update { it.copy(cobro = cobro.copy(phoneInvalid = true)) }
            return
        }
        _ui.update { it.copy(cobro = cobro.copy(saving = true)) }
        viewModelScope.launch {
            var plan = cobro.plan
            var customer = cobro.customer
            // "Guardar como cliente": se crea antes de cobrar y el fiado queda vinculado a él desde el principio.
            if (credit != null && cobro.saveAsCustomer && customer == null) {
                val saved = c.customers.save(null, cobro.debtor, cobro.debtorPhone, null, null, country)
                if (saved is SaveCustomer.Saved) {
                    customer = saved.customer
                    plan = plan.withDebtor(cobro.debtor, cobro.debtorPhone, saved.customer.id)
                }
            }
            val saleId = s.resumedId ?: java.util.UUID.randomUUID().toString()
            val priced = cobro.priced ?: s.priced
            c.sales.complete(priced.cart, plan, saleId, s.resumedLabel, priced.promo.applied)
            val creditShare = credit?.let { cr ->
                val creditId = c.db.credits().idBySale(saleId)
                val credited = plan.effective.filter { it.method == PayMethod.CREDIT }.sumOf { it.amountMinor }
                val paidNow = plan.effective.filter { it.method != PayMethod.CREDIT }.sumOf { it.amountMinor }.takeIf { it > 0 }
                val name = cr.debtorLabel ?: customer?.name ?: cobro.debtor
                val phone = (PhoneNumbers.normalize(cobro.debtorPhone, country) as? PhoneResult.Valid)?.digits ?: customer?.phone
                ShareRequest.CreditNew(
                    creditId, customer?.id ?: cr.customerId, name, phone, credited, paidNow,
                    shareLines(priced),
                )
            }
            // WhatsApp al terminar es OPCIONAL (Más › Preferencias de este teléfono, apagado por omisión): apagado, ni botón ni diálogo automático (tampoco con fiado).
            // Encendido: con fiado el detalle se abre solo (si la persona lo dejó marcado); una venta normal ofrece «Enviar comprobante» con un botón, nunca por sí sola.
            val ticket = ShareRequest.Ticket(shareLines(priced), plan.totalMinor)
            _ui.update { it.copy(cobro = cobro.copy(plan = plan, saving = false, doneChangeMinor = plan.changeMinor, doneShare = WhatsAppOffer.doneShare(cobro.offerWhatsApp, creditShare, ticket), doneSaleId = saleId, doneAtMillis = System.currentTimeMillis()), share = WhatsAppOffer.autoShare(cobro.offerWhatsApp, cobro.sendWhatsApp, creditShare)) }
            // Impresora: la venta YA está cobrada y la pantalla lista; imprimir (con su reconexión) va aparte y nunca la retrasa ni la bloquea.
            val printerSettings = c.printer.settings.value
            if (printerSettings.enabled && printerSettings.autoPrint) printSale(saleId)
        }
    }

    // ---------- «Anular esta venta» (la última propia, primeros 5 minutos), desde el aviso de la venta nueva ----------
    override fun askUndoSale() = _ui.update { s -> s.saleNotice?.takeIf { !it.undone && !it.sent }?.let { s.copy(saleUndo = SaleUndoUi(it.saleId, it.doneAtMillis)) } ?: s }
    override fun setUndoReason(text: String) = _ui.update { s -> s.copy(saleUndo = s.saleUndo?.copy(reason = text.take(com.cuadra.caja.domain.SaleDeletion.MAX_REASON))) }
    override fun closeUndo() = _ui.update { it.copy(saleUndo = null) }
    override fun hideSaleNotice() = _ui.update { if (it.saleUndo != null) it else it.copy(saleNotice = null) }

    override fun confirmUndo() {
        val undo = _ui.value.saleUndo ?: return
        val reason = com.cuadra.caja.domain.SaleDeletion.clean(undo.reason) ?: return
        viewModelScope.launch {
            val me = c.sessionStore.current().memberId
            val last = c.sales.lastCompletedBy(me)
            // Se vuelve a comprobar al confirmar: pudieron pasar los 5 minutos con la hoja abierta.
            val ok = last?.id == undo.saleId && com.cuadra.caja.domain.SaleUndo.remaining(undo.doneAtMillis, System.currentTimeMillis()) > 0
            if (!ok) {
                _ui.update { it.copy(saleUndo = it.saleUndo?.copy(tooLate = true)) }
                return@launch
            }
            c.sales.cancel(undo.saleId, reason)
            _ui.update { it.copy(saleUndo = null, saleNotice = SaleNoticeUi(undo.saleId, it.saleNotice?.totalMinor ?: 0, 0, undo.doneAtMillis, undone = true)) }
        }
    }

    /** Pasa de «Venta cobrada» a la venta nueva (un toque o el tiempo) y deja el aviso flotante con el vuelto y «Anular». */
    override fun finishCobro() = _ui.update { s ->
        val done = s.cobro
        val saleId = done?.doneSaleId ?: return@update s
        val notice = done.doneAtMillis?.let {
            if (done.sent) SaleNoticeUi(saleId, done.plan.totalMinor, 0, it, sent = true, note = done.registerNote.ifBlank { null })
            else SaleNoticeUi(saleId, done.plan.totalMinor, done.doneChangeMinor ?: 0, it)
        }
        CajaUi(tab = s.tab, printer = s.printer, reordering = s.reordering, printNotice = s.printNotice, printTick = s.printTick, saleNotice = notice,
            promotions = s.promotions, pricingById = s.pricingById, promoDay = s.promoDay)
    }

    /**
     * Las líneas del comprobante de WhatsApp: cada producto a su precio de siempre y, debajo de la última línea que tocó, «Promo 3 por C$ 100: -C$ 70» (el
     * total ya es el de la promoción).
     */
    private fun shareLines(priced: com.cuadra.caja.domain.PricedCart): List<Pair<String, Long>> {
        val b = business.value
        val fmt = b?.let { com.cuadra.caja.ui.common.MoneyFormat.of(it.currency, it.country) } ?: com.cuadra.caja.ui.common.MoneyFormat.Default
        val language = com.cuadra.caja.core.i18n.AppLocale.current().tag ?: java.util.Locale.getDefault().language
        return com.cuadra.caja.domain.PromoText.ticketLines(
            priced.cart.lines.map { l -> Triple(l.id, l.name + if (l.quantityMilli != 1000L) " ×" + com.cuadra.caja.ui.screens.qtyText(l.quantityMilli) else "", SaleMath.lineTotal(l.unitPriceMinor, l.quantityMilli)) },
            priced.promo.applied.map { a -> Triple(a.lineIds, com.cuadra.caja.domain.PromoText.label(a.quantity, fmt.format(a.priceMinor), language), a.discountMinor) },
        )
    }

    // ---------- impresora ----------
    /** Imprime el recibo de una venta y deja el aviso que corresponde. Con la opción apagada no hace nada (ni pide permisos). */
    private fun printSale(saleId: String) {
        viewModelScope.launch {
            val view = c.sales.view(saleId) ?: return@launch
            val notice = PrintNotice.of(c.printer.printSale(view))
            _ui.update { it.copy(printNotice = notice, printTick = it.printTick + 1) }
        }
    }

    override fun printAgain() {
        val id = _ui.value.cobro?.doneSaleId ?: _ui.value.saleNotice?.saleId
        _ui.update { it.copy(printNotice = null) }
        // En la pantalla de venta completa se vuelve a armar el recibo; ya en la caja (pasó a otra venta) se reintenta el último trabajo que falló.
        if (id != null) printSale(id)
        else viewModelScope.launch {
            val notice = PrintNotice.of(c.printer.retry())
            _ui.update { it.copy(printNotice = notice, printTick = it.printTick + 1) }
        }
    }

    override fun dismissPrintNotice() = _ui.update { it.copy(printNotice = null) }
    override fun openPrinterSettings() { c.pendingRoute.value = "cuadra://impresora" }
}

/** Utilidad de pantalla: lista con sincronización pendiente. */
fun Flow<List<SaleEntity>>.asState(scope: kotlinx.coroutines.CoroutineScope): StateFlow<List<SaleEntity>> = stateIn(scope, SharingStarted.Eagerly, emptyList())

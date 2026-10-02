package com.cuadra.caja.ui.guard

import com.cuadra.caja.data.local.BusinessEntity
import com.cuadra.caja.data.local.CashMovementEntity
import com.cuadra.caja.data.local.CreditEntity
import com.cuadra.caja.data.local.CreditItem
import com.cuadra.caja.data.local.CreditTotals
import com.cuadra.caja.data.local.CustomerEntity
import com.cuadra.caja.data.local.ExpenseCategoryEntity
import com.cuadra.caja.data.local.ExpenseEntity
import com.cuadra.caja.data.local.ExpenseTotals
import com.cuadra.caja.data.local.MethodAmountRow
import com.cuadra.caja.data.local.NotificationEntity
import com.cuadra.caja.data.local.CategoryEntity
import com.cuadra.caja.data.local.ProductEntity
import com.cuadra.caja.data.local.ProductProfit
import com.cuadra.caja.data.local.ProductStock
import com.cuadra.caja.data.local.PurchaseEntity
import com.cuadra.caja.data.local.PurchaseItemEntity
import com.cuadra.caja.data.local.PurchaseRow
import com.cuadra.caja.data.local.SaleEntity
import com.cuadra.caja.data.local.SalesTotals
import com.cuadra.caja.data.local.ShiftEntity
import com.cuadra.caja.data.local.StockMovementEntity
import com.cuadra.caja.data.local.SupplierEntity
import com.cuadra.caja.data.local.SupplierPaymentEntity
import com.cuadra.caja.data.local.TopProductRow
import com.cuadra.caja.data.remote.FieldChangeDto
import com.cuadra.caja.data.remote.ProductHistoryEntryDto
import com.cuadra.caja.data.remote.ScheduleAudienceDto
import com.cuadra.caja.data.remote.ScheduleDto
import com.cuadra.caja.data.remote.ScheduleRuleDto
import com.cuadra.caja.data.remote.ScheduleRunDto
import com.cuadra.caja.data.repo.PurchaseLine
import com.cuadra.caja.domain.AmountEntry
import com.cuadra.caja.domain.Cart
import com.cuadra.caja.domain.CartLine
import com.cuadra.caja.domain.ClosingBreakdown
import com.cuadra.caja.domain.UndoEntry
import com.cuadra.caja.domain.PaymentEntry
import com.cuadra.caja.domain.PaymentPlan
import com.cuadra.caja.domain.Profit
import com.cuadra.caja.ui.CajaUi
import com.cuadra.caja.ui.CashRow
import com.cuadra.caja.ui.CobroUi
import com.cuadra.caja.ui.CustomerDetailUi
import com.cuadra.caja.ui.HistoryState
import com.cuadra.caja.ui.LedgerRow
import com.cuadra.caja.ui.MovementUi
import com.cuadra.caja.ui.NOTIFICATION_TYPES
import com.cuadra.caja.ui.PosTab
import com.cuadra.caja.ui.PrefsUi
import com.cuadra.caja.ui.ProductDetail
import com.cuadra.caja.ui.SummaryData
import com.cuadra.caja.data.local.MemberEntity
import com.cuadra.caja.data.remote.MeDto
import com.cuadra.caja.data.remote.MembershipDto

/**
 * Datos de PEOR CASO para la guardia de diseño: nombres de 60 a 200 caracteres, montos como C$ 1,234,567.89 y 99,999,999, cantidades de 5+ cifras,
 * teléfonos y referencias de 30 caracteres, palabras sueltas larguísimas. Si una pantalla aguanta esto con letra 2× en 320 dp, aguanta la vida real.
 */
object Fixtures {
    const val NAME_60 = "Aceite de oliva extra virgen importado de España botella 1 L"
    const val NAME_120 = "Repuesto original para licuadora de vaso de vidrio con aspas de acero inoxidable y tapa de goma resistente al calor, modelo 4655"
    const val NAME_200 = "Cuaderno profesional de espiral con cubierta dura, cien hojas rayadas, marca reconocida, edición limitada de regreso a clases con diseño de dinosaurios y separadores de colores incluidos en el empaque original"
    const val CODE_LONG = "7501234567890123456789012345678901234567890123456789012345678901"
    const val LONG_WORD = "Electroencefalografistaotorrinolaringologicamente"
    const val PERSON_LONG = "María de los Ángeles Fernández de la Concepción Rodríguez Sánchez"
    const val BUSINESS_NAME = "Quesería y Abarrotes La Esperanza de Doña Carmen Sucursal Centro"
    const val BUSINESS_120 = "Distribuidora Comercial Internacional de Alimentos, Bebidas y Productos de Limpieza del Norte y del Pacífico de Nicaragua S.A. de C.V."
    const val REF_30 = "REF-2026-09-29-TRANSFER-0001234"
    const val PHONE_30 = "+505 8888 7777 ext 123456789012"
    const val PHONE = "50588887777"
    const val EMAIL_LONG = "administracion.general.de.la.distribuidora.del.norte@correo-de-ejemplo-muy-largo.com.ni"

    /** C$ 1,234,567.89 y C$ 99,999,999.00 en unidad menor (2 decimales). */
    const val BIG = 123_456_789L
    const val HUGE = 9_999_999_900L
    const val MID = 1_234_500L

    /** 12,345.678 unidades. */
    const val QTY_BIG = 12_345_678L

    const val NOW = 1_790_000_000_000L
    const val DAY = 24L * 60 * 60 * 1000

    /** «Requiere atención»: una de cada razón, con nombres, montos y códigos del peor caso. */
    val attention: List<com.cuadra.caja.domain.Attention.Item> by lazy {
        listOf(
            com.cuadra.caja.domain.Attention.Item(1, com.cuadra.caja.domain.Attention.What.SALE, HUGE, null, NOW, PERSON_LONG, com.cuadra.caja.domain.Attention.Reason.CREDIT_LIMIT, "CREDIT_LIMIT_EXCEEDED", limitMinor = HUGE, balanceMinor = HUGE, customerName = PERSON_LONG),
            com.cuadra.caja.domain.Attention.Item(2, com.cuadra.caja.domain.Attention.What.SALE, BIG, null, NOW, PERSON_LONG, com.cuadra.caja.domain.Attention.Reason.CONFLICT_COPY, "SALE_CONFLICT_COPY", review = true),
            com.cuadra.caja.domain.Attention.Item(3, com.cuadra.caja.domain.Attention.What.SALE, MID, null, NOW, null, com.cuadra.caja.domain.Attention.Reason.ALREADY_CLOSED_ELSEWHERE, "SALE_STALE", review = true),
            com.cuadra.caja.domain.Attention.Item(4, com.cuadra.caja.domain.Attention.What.CREDIT_PAYMENT, HUGE, null, NOW, PERSON_LONG, com.cuadra.caja.domain.Attention.Reason.CREDIT_CLOSED, "CREDIT_CLOSED"),
            com.cuadra.caja.domain.Attention.Item(5, com.cuadra.caja.domain.Attention.What.WITHDRAWAL, HUGE, null, NOW, PERSON_LONG, com.cuadra.caja.domain.Attention.Reason.PIN_VERIFICATION_REQUIRED, "PIN_VERIFICATION_REQUIRED", memberId = "m1"),
            com.cuadra.caja.domain.Attention.Item(6, com.cuadra.caja.domain.Attention.What.PRODUCT, null, NAME_200, NOW, PERSON_LONG, com.cuadra.caja.domain.Attention.Reason.CODE_IN_USE, "BARCODE_IN_USE"),
            com.cuadra.caja.domain.Attention.Item(7, com.cuadra.caja.domain.Attention.What.OTHER, null, null, NOW, PERSON_LONG, com.cuadra.caja.domain.Attention.Reason.OTHER, CODE_LONG),
            com.cuadra.caja.domain.Attention.Item(8, com.cuadra.caja.domain.Attention.What.EXPENSE, BIG, NAME_120, NOW, PERSON_LONG, com.cuadra.caja.domain.Attention.Reason.MEMBER_DISABLED, "MEMBER_NOT_ACTIVE"),
        )
    }

    fun member(i: Int, name: String = "Persona $i", role: String = "CASHIER") =
        MemberEntity("m$i", name, role, "ACTIVE", hasGoogle = role != "CASHIER", pinSet = true, pinMustChange = false, color = null, pinHash = null)

    /** «Por persona»: `n` personas (nombres largos y montos enormes en las primeras si se pide), de más a menos vendido. */
    fun peopleRows(n: Int, long: Boolean = false, huge: Boolean = false) = (1..n).map { i ->
        com.cuadra.caja.data.remote.BreakdownRowDto("m$i", if (long && i % 2 == 1) (if (i % 3 == 0) PERSON_LONG else NAME_120) else if (i == 2) LONG_WORD else "Persona $i", (13 - i).toLong() * 7_000, if (huge && i == 1) HUGE else (n - i + 1) * 45_000L + 123)
    }
    fun people(n: Int, long: Boolean = false, huge: Boolean = false) = com.cuadra.caja.domain.PeopleBreakdown(peopleRows(n, long, huge), peopleRows(n, long, huge).reversed().mapIndexed { i, r -> r.copy(totalMinor = (i + 1) * 30_000L) })

    val members = listOf(
        member(1, PERSON_LONG, "OWNER"), member(2, "Ana", "ADMIN"), member(3, LONG_WORD, "CASHIER"), member(4, NAME_60, "CASHIER"), member(5, "Kevin", "CASHIER"),
    )

    val me = MeDto(
        "u1", "dueno@example.com", PERSON_LONG, null, "es", false,
        listOf(
            MembershipDto("b1", BUSINESS_120, "m1", "OWNER", "NIO", "America/Managua"),
            MembershipDto("b2", BUSINESS_NAME, "m2", "ADMIN", "NIO", "America/Managua"),
            MembershipDto("b3", "Kiosco", "m3", "CASHIER", "NIO", "America/Managua"),
        ),
    )


    fun business(name: String = BUSINESS_NAME) = BusinessEntity(
        "b1", name, "NI", "NIO", "America/Managua", "es", "00:00", "STOCK",
        """{"credit":true,"expenses":true,"shifts":true,"catalog":true,"inventory":true}""", "[\"TYPE\",\"QUICK\",\"LIST\"]", false,
    )

    // ---------- catálogo y venta ----------

    fun product(i: Int, name: String, price: Long = MID, variant: String? = null, pricing: String = "FIXED", cost: Long? = 800_000L, track: Boolean = true, stock: Long = QTY_BIG, min: Long? = 5_000L, unit: String = "UNIT", active: Boolean = true) =
        ProductEntity("p$i", "7501234567890", null, name, variant, null, unit, pricing, price, cost, true, i, null, track, stock, min, active, 1)

    val products = listOf(
        product(1, NAME_200, BIG), product(2, "Refresco", 3_500), product(3, NAME_60, HUGE, "Botella 3 L"), product(4, LONG_WORD, 12_000),
        product(5, "Queso fresco", 9_000, pricing = "BY_WEIGHT", unit = "LB"), product(6, NAME_120, 250_000, "Caja x 24"), product(7, "Pan", 500), product(8, "Café molido", 18_000, "Bolsa"),
        // Precio abierto: sin precio, y con un sugerido (nombre larguísimo).
        product(10, "Servicio a domicilio", 0, pricing = "OPEN", cost = null), product(11, NAME_120, HUGE, pricing = "OPEN"),
    )

    // ---------- pestaña «Productos» ----------
    /** Con código corto y código de barras larguísimo (lo que se ve en la fila de un resultado). */
    val searchProducts = products.mapIndexed { i, p -> if (i % 2 == 0) p.copy(shortCode = "REF-${i}00" + if (i == 0) LONG_WORD else "", barcode = if (i == 0) "7501234567890123456789012345678901234567890" else p.barcode) else p.copy(isQuick = i % 3 == 0) }

    /** Frecuentes y más vendidos (los que el negocio no marcó). */
    fun pane(frequents: Int = 3, best: Int = 5, query: String = "", results: List<ProductEntity> = emptyList()): com.cuadra.caja.domain.ProductsPane {
        val marked = products.take(frequents)
        val sellers = products.drop(frequents).take(best).map { it.copy(isQuick = false) }
        return com.cuadra.caja.domain.ProductsPane(
            query,
            marked.map { com.cuadra.caja.domain.Suggestion(it, com.cuadra.caja.domain.SuggestionSource.FREQUENT) } + sellers.map { com.cuadra.caja.domain.Suggestion(it, com.cuadra.caja.domain.SuggestionSource.BEST_SELLER) },
            results, marked,
        )
    }

    val pane = pane()

    val productCategories = listOf(
        CategoryEntity("c1", NAME_60, true, 1), CategoryEntity("c2", "Bebidas", true, 1), CategoryEntity("c3", LONG_WORD, true, 1), CategoryEntity("c4", "Lácteos", true, 1),
    )

    /** Línea agregada a mano (teclado): su descripción se puede editar. */
    val manualLine = CartLine("lm", null, null, "Varios", null, HUGE, null, 1000)

    val lines: List<CartLine> = listOf(
        CartLine("l1", "p1", null, NAME_200, null, BIG, null, 1000), CartLine("l2", "p2", null, "Refresco", "Lata", 3_500, null, QTY_BIG),
        CartLine("l3", "p3", null, NAME_60, "Botella 3 L", HUGE, null, 1000), CartLine("l4", null, null, LONG_WORD, null, 12_000, null, 750),
    ) + (5..14).map { CartLine("l$it", "p$it", null, "Producto de ejemplo número $it con nombre largo", null, MID, null, 2000L + it) }

    val fullCart = Cart(lines)

    /** 15 líneas con nombres largos y una de 15 unidades (el caso del propietario: 15 veces «+»). */
    /** Un carrito con exactamente [n] líneas (para el número del botón «Recibo · N»: 3, 15, 999). */
    fun cartOf(n: Int) = Cart((1..n).map { CartLine("lc$it", "p$it", null, "Cuajada $it", null, 2_500, null, 1000) })
    val cart15 = Cart(lines + CartLine("l15", "p15", null, "Cuajada", null, 2_500, null, 15_000))
    val undoAdded = UndoEntry.Added("l15", NAME_200, 1000, BIG, false)
    val undoDeleted = UndoEntry.Deleted(lines[0], 0)
    // Tira de la última línea: el recibo termina en la línea que se prueba (la tira toma la última del recibo).
    val stripShort = CartLine("s1", "ps1", null, "Cola", null, 2_500, null, 6000)
    val stripLongName = CartLine("s2", "ps2", null, NAME_200, "Botella 3 L", 2_500, null, 6000)
    val stripWeighed = CartLine("s3", "ps3", null, "Queso fresco", null, 8_000, null, 2_750, byWeight = true)
    val stripBigAmount = CartLine("s4", "ps4", null, NAME_60, null, HUGE, null, 1000)
    val strip999 = CartLine("s5", "ps5", null, "Fósforos", null, 500, null, 999_000)
    val stripBigQty = CartLine("s6", "ps6", null, LONG_WORD, null, 12_345, null, 12_345_678, byWeight = true)
    val stripManual = CartLine("s7", null, null, "Varios", null, 3_500, null, 2000)
    fun cartEndingIn(line: CartLine) = Cart(lines.take(3) + line)
    val oneLineCart = Cart(listOf(CartLine("x1", "p2", null, "Refresco", null, 3_500, null, 2000)))

    // ---------- promociones por cantidad ----------
    val promoDay: java.time.LocalDate = java.time.LocalDate.of(2026, 10, 1)
    val promoRules = listOf(
        com.cuadra.caja.domain.PromotionRule("pr1", NAME_120, 3, 10_000, setOf("pb1", "pb2")),
        com.cuadra.caja.domain.PromotionRule("pr2", "Grande", 2, HUGE, setOf("p1", "p3")),
    )
    /** Un recibo con promociones: 7 cervezas de dos marcas (dos paquetes) y líneas de precio enorme que forman paquetes de 2 por un precio enorme. */
    val promoCart = Cart(lines.take(3).map { it.copy(quantityMilli = 3_000) } + listOf(
        CartLine("b1", "pb1", null, "Toña", null, 4_500, null, 4000), CartLine("b2", "pb2", null, NAME_200, "Lata", 4_500, null, 3000),
    ))
    fun promoUi(tab: PosTab = PosTab.MANUAL, cart: Cart = promoCart) = cajaUi(tab, cart).copy(promotions = promoRules, pricingById = (1..20).associate { "p$it" to "FIXED" } + mapOf("pb1" to "FIXED", "pb2" to "FIXED"), promoDay = promoDay)

    fun entry(vararg keys: Char) = keys.fold(AmountEntry()) { e, c -> e.digit(c, 2) }
    val bigEntry = AmountEntry("99999999", "123456", true)
    val entryTimes = AmountEntry("3", "25", true)

    fun sale(i: Int, label: String? = "Mesa 4 de la señora con pedido especial y descuento", status: String = "PARKED", total: Long = BIG) = SaleEntity(
        "s$i", status, label, null, total, 0, total, "m1", if (i % 2 == 0) PERSON_LONG else "Ana", if (i % 2 == 0) PERSON_LONG else null, NOW - i * DAY, null, null, null, null, NOW - i * DAY, NOW, if (i % 3 == 0) 0 else 5,
    )

    val parked = listOf(sale(1, NAME_120), sale(2, "Mesa 4"), sale(3, null, total = HUGE))

    /** Cobro en caja (ADR 0015): cuentas enviadas a caja (nota larguísima, corta y sin nota) junto a las apartadas comunes. */
    val queued = listOf(
        sale(4, NAME_200, total = HUGE).copy(sentToRegisterAt = NOW - 3_600_000, sentByName = PERSON_LONG),
        sale(5, "Mesa 4").copy(sentToRegisterAt = NOW - 600_000, sentByName = "Kevin"),
        sale(6, null, total = BIG).copy(sentToRegisterAt = NOW - 60_000, sentByName = "Ana"),
    )
    val queueCounts = mapOf("s4" to 999, "s5" to 1, "s6" to 12)

    /** Muchas cuentas esperando (aparece el buscador): de 2 h 30 min a recién llegada, alguna sin nota. */
    val queuedMany = (10..19).map { i -> sale(i, if (i % 4 == 0) null else if (i % 3 == 0) NAME_120 else "Mesa $i", total = if (i % 2 == 0) HUGE else BIG).copy(sentToRegisterAt = NOW - (19 - i) * 1_000_000L, sentByName = if (i % 2 == 0) PERSON_LONG else "Kevin") }

    /** Quién tiene abierta cada cuenta en otro teléfono. */
    val queueLocks = mapOf("s4" to PERSON_LONG, "s5" to "Ana", "s12" to "Lucía")
    val queueItems = lines.mapIndexed { i, l -> com.cuadra.caja.data.local.SaleItemEntity("s4", l.id, l.productId, l.barcode, l.name, l.variant, l.unitPriceMinor, l.unitCostMinor, l.quantityMilli, l.discountMinor, i) }
    val sales = (1..12).map { sale(it, status = if (it % 4 == 0) "CANCELLED" else "COMPLETED", total = if (it % 2 == 0) HUGE else BIG) }

    // ---------- días, ventas del servidor y cierre del día ----------

    /** Calendario del negocio (Managua, corte 02:00) y un «ahora» fijo para que las ventanas del selector de rango sean siempre las mismas. */
    val calendar = com.cuadra.caja.domain.BusinessCalendar(java.time.ZoneId.of("America/Managua"), java.time.LocalTime.of(2, 0))

    /** Las ventas de este teléfono como filas de lista (sin líneas: el detalle las lee aparte). */
    val localSales = sales.map { it.toRowView() }
    private fun SaleEntity.toRowView() = com.cuadra.caja.domain.SaleView(
        id, status, label, subtotalMinor, discountMinor, totalMinor, completedAt ?: createdAt, completedByName ?: createdByName, null, null,
        if (status == "CANCELLED") "Ana" else null, if (status == "CANCELLED") updatedAt else null, if (status == "CANCELLED") NAME_120 else null, emptyList(), emptyList(), unsynced = rev == 0L,
    )

    private val saleLines = listOf(
        com.cuadra.caja.domain.SaleLineView(NAME_120, NAME_60, QTY_BIG, BIG, BIG, HUGE),
        com.cuadra.caja.domain.SaleLineView("Refresco", null, 2_000, 3_500, 0, 7_000),
        com.cuadra.caja.domain.SaleLineView(LONG_WORD, null, 1_500, 99_999, 500, 149_499),
    )
    private val salePayments = listOf(
        com.cuadra.caja.domain.SalePaymentView("CASH", null, HUGE, HUGE + 1_000, 1_000, null, null),
        com.cuadra.caja.domain.SalePaymentView("TRANSFER", null, BIG, null, null, REF_30, null),
        com.cuadra.caja.domain.SalePaymentView("CREDIT", null, MID, null, null, null, PERSON_LONG),
        com.cuadra.caja.domain.SalePaymentView("OTHER", NAME_60, 1, null, null, null, null),
    )
    fun saleView(i: Int, cancelled: Boolean = false, edited: Boolean = false, total: Long = if (i % 2 == 0) HUGE else BIG) = com.cuadra.caja.domain.SaleView(
        "v$i", if (cancelled) "CANCELLED" else "COMPLETED", null, total, BIG, total, NOW - i * DAY / 24, if (i % 2 == 0) PERSON_LONG else "Kevin",
        if (edited) PERSON_LONG else null, if (edited) NOW - i * DAY / 48 else null,
        if (cancelled) PERSON_LONG else null, if (cancelled) NOW - i * DAY / 96 else null, if (cancelled) NAME_200 else null, saleLines, salePayments,
    )
    /** Atendió una persona y cobró otra (cobro en caja): nombres larguísimos. */
    val servedAndCharged = saleView(2).copy(id = "v99", takenBy = NAME_120, soldBy = PERSON_LONG, sentBy = NAME_120, sentAtMillis = NOW - DAY / 12)
    val serverSales = (1..8).map { saleView(it, cancelled = it % 3 == 0, edited = it % 2 == 0) }.mapIndexed { i, v ->
        // Algunas para revisar: en conflicto, llegó después de la baja, hora corregida, con devolución.
        when (i) { 0 -> v.copy(conflict = true, returnedMinor = BIG); 1 -> v.copy(reviewFlag = "LATE_AFTER_DISABLE"); 3 -> v.copy(reviewFlag = "CLOCK_ADJUSTED", conflict = true); else -> v }
    }

    /** Una venta con líneas devolvibles (ids), una devolución larga ya hecha (con nota de crédito) y etiquetas. */
    val returnLines = listOf(
        com.cuadra.caja.domain.SaleLineView(NAME_120, NAME_60, QTY_BIG, BIG, BIG, HUGE, "l1", 1_000),
        com.cuadra.caja.domain.SaleLineView("Refresco", null, 2_000, 3_500, 0, 7_000, "l2", 2_000),
        com.cuadra.caja.domain.SaleLineView(LONG_WORD, null, 1_500, 99_999, 500, 149_499, "l3", 0),
    )
    val returnedSale = saleView(5).copy(
        items = returnLines, conflict = true, reviewFlag = "LATE_AFTER_DISABLE", returnedMinor = HUGE, completedById = "m2", completedAtMillis = NOW - 60_000,
        returns = listOf(com.cuadra.caja.domain.SaleReturnView("r1", NAME_200, "SAME", HUGE, PERSON_LONG, NOW - 30_000,
            listOf(com.cuadra.caja.domain.ReturnLineView("l1", NAME_120, 1_000, BIG), com.cuadra.caja.domain.ReturnLineView("l2", "Refresco", 2_000, 7_000)),
            listOf("CASH" to BIG, "CREDIT" to MID, "TRANSFER" to 1), pending = true)),
    )
    val saleTotals = com.cuadra.caja.data.remote.SalesTotalsDto(12_345, HUGE, BIG, BIG, 12)

    private fun close(i: Int, empty: Boolean = false) = com.cuadra.caja.domain.DayCloseCard(
        java.time.LocalDate.of(2026, 9, 29 - i), 1_790_000_000_000L - (i + 1) * DAY, 1_790_000_000_000L - i * DAY,
        if (empty) 0 else 12_345, if (empty) 0 else HUGE,
        if (empty) com.cuadra.caja.domain.MethodTotals() else com.cuadra.caja.domain.MethodTotals(HUGE, BIG, BIG, MID, 1),
        if (empty) com.cuadra.caja.domain.MethodTotals() else com.cuadra.caja.domain.MethodTotals(BIG, MID, 1, 0, 1),
        if (empty) 0 else BIG, if (empty) 0 else HUGE, if (empty) 0 else MID, if (empty) 0 else BIG, if (empty) 0 else HUGE,
        if (empty) 0 else 12, if (empty) 0 else BIG,
    )
    val dayCloses = listOf(close(0), close(1, empty = true), close(2))

    /** Una jornada con devoluciones, ventas de días anteriores anuladas y todas las anulaciones tardías. */
    val adjustedClose = close(0).copy(
        returnsCount = 12, returnsMinor = HUGE, cashRefundsMinor = BIG, priorCancelledCount = 3, priorCancelledMinor = HUGE, priorCancelledCashMinor = BIG, netSalesMinor = -HUGE,
        laterVoids = listOf("EXPENSE_DRAWER", "EXPENSE_OTHER", "CREDIT_PAYMENT", "WITHDRAWAL", "DEPOSIT").mapIndexed { i, k ->
            com.cuadra.caja.domain.LaterVoid(k, (i + 1).toLong(), HUGE, if (k == "EXPENSE_OTHER") 0 else if (i % 2 == 0) HUGE else -HUGE)
        },
    )
    val syncWarnings = listOf(
        com.cuadra.caja.data.remote.DeviceSyncDto("d1", NAME_120, 12_345, "2026-09-29T10:00:00Z", false),
        com.cuadra.caja.data.remote.DeviceSyncDto("d2", "Caja 2", 0, "2026-09-28T10:00:00Z", true),
    )

    fun customer(i: Int, name: String, balance: Long = BIG, phone: String? = PHONE, limit: Long? = HUGE) = CustomerEntity("c$i", name, phone, "Nota del cliente", limit, NOW - 3 * DAY, false, balance, NOW - 40 * DAY, 3)
    val customers = listOf(customer(1, PERSON_LONG), customer(2, "Ana", 0, null, null), customer(3, LONG_WORD, HUGE, PHONE_30), customer(4, NAME_120), customer(5, "Pedro Pablo", 1_500))

    fun cajaUi(
        tab: PosTab = PosTab.MANUAL, cart: Cart = Cart(), entry: AmountEntry = AmountEntry(), cobro: CobroUi? = null, query: String = "",
        undo: UndoEntry? = null, hint: Boolean = false, editing: String? = null, last: String? = null,
    ) = CajaUi(cart = cart, entry = entry, description = "", tab = tab, query = query, cobro = cobro, undoStack = listOfNotNull(undo), undoShown = undo != null, swipeHint = hint, editingLineId = editing, lastLineId = last)

    fun cobro(total: Long, vararg entries: PaymentEntry, tendered: String = "", debtor: String = "", phone: String = "", matches: List<CustomerEntity> = emptyList(), suggestions: List<String> = emptyList(), done: Long? = null, other: String = "") =
        CobroUi(PaymentPlan(total, entries.toList()), tenderedText = tendered, debtor = debtor, debtorPhone = phone, customerMatches = matches, nameSuggestions = suggestions, doneChangeMinor = done, otherLabel = other)

    // ---------- fiados ----------

    val ledgerRows: List<LedgerRow> = listOf(
        LedgerRow.Customer(customer(1, PERSON_LONG), listOf(CreditItem(credit(1, PERSON_LONG), PERSON_LONG), CreditItem(credit(2, PERSON_LONG), PERSON_LONG)), HUGE, NOW - 50 * DAY),
        LedgerRow.Customer(customer(4, NAME_120), listOf(CreditItem(credit(3, NAME_120), NAME_120)), BIG, NOW - 20 * DAY),
        LedgerRow.Note(CreditItem(credit(4, NAME_200, customerId = null, note = NAME_120), null), NOW - 10 * DAY),
        LedgerRow.Note(CreditItem(credit(5, "Doña Rosa", customerId = null, status = "PAID"), null), NOW - 2 * DAY),
    )

    fun credit(i: Int, debtor: String, customerId: String? = "c1", note: String? = null, status: String = "OPEN", balance: Long = BIG) =
        CreditEntity("k$i", null, customerId, debtor, PHONE, BIG, if (status == "PAID") 0 else balance, status, null, note, NOW - i * DAY, PERSON_LONG, NOW - 9 * DAY, 2)

    val movements = (1..8).map { i ->
        MovementUi(i % 2 == 0, NOW - i * DAY, if (i % 2 == 0) BIG else HUGE, if (i % 2 == 0) "CASH" else NAME_120, PERSON_LONG, i == 3, "k1", if (i % 2 == 0) "pay$i" else null, null)
    }
    val detail = CustomerDetailUi(customers[0], movements, listOf(credit(1, PERSON_LONG, note = NAME_120), credit(2, "Otro", note = null)))
    val totals = CreditTotals(12_345, HUGE, 1234, BIG)

    // ---------- gastos y turnos ----------

    val categories = listOf(
        ExpenseCategoryEntity("e1", "goods", null, true, 1), ExpenseCategoryEntity("e2", "utilities", null, true, 1), ExpenseCategoryEntity("e3", "payroll", null, true, 1),
        ExpenseCategoryEntity("e4", "rent", null, true, 1), ExpenseCategoryEntity("e5", null, NAME_60, true, 1), ExpenseCategoryEntity("e6", "maintenance", null, true, 1),
    )

    fun expense(i: Int, description: String?, source: String = "CASH_DRAWER", voided: Boolean = false, rev: Long = 3) =
        ExpenseEntity("x$i", "e${i % 5 + 1}", description, if (i % 2 == 0) HUGE else BIG, source, "r1", PERSON_LONG, "m1", NOW - i * 3_600_000, voided, null, rev)

    fun movement(i: Int, kind: String, reason: String?) = CashMovementEntity("mv$i", kind, BIG, reason, "r1", PERSON_LONG, "m1", NOW - i * 3_600_000, i == 3, null, 2)

    val cashRows: List<CashRow> = listOf(
        CashRow.Expense(expense(1, NAME_200), "goods"), CashRow.Expense(expense(2, null, "BANK"), NAME_60), CashRow.Expense(expense(3, "Luz", "CARD", voided = true, rev = 0), "utilities"),
        CashRow.Movement(movement(4, "DEPOSIT", NAME_120)), CashRow.Movement(movement(5, "WITHDRAWAL", null)),
    )
    val expenseTotals = ExpenseTotals(HUGE, BIG, 12)

    val shift = ShiftEntity("sh1", "r1", "Caja principal", PERSON_LONG, "m1", NOW - 5 * 3_600_000, BIG, null, null, null, null, null, null, null, "OPEN", null, 0, 1)
    fun closedShift(i: Int, diff: Long) = ShiftEntity("sc$i", "r1", "Caja", PERSON_LONG, "m1", NOW - i * DAY, BIG, PERSON_LONG, NOW - i * DAY + 3_600_000, HUGE, HUGE + diff, diff, null, null, "CLOSED", null, 0, 1)
    val recentShifts = listOf(closedShift(1, 0), closedShift(2, -BIG), closedShift(3, HUGE), shift)
    val breakdown = ClosingBreakdown(HUGE, 12_345, BIG, BIG, HUGE, BIG, HUGE, BIG, BIG, BIG, 3)

    // ---------- inventario ----------

    val stock = products.mapIndexed { i, p -> ProductStock(p, if (i == 2) -QTY_BIG else if (i == 3) 1000 else QTY_BIG) } + ProductStock(product(9, "Sin control", track = false), 0)

    fun movementEntity(i: Int, kind: String, qty: Long) = StockMovementEntity("st$i", "p1", kind, qty, BIG, null, null, NAME_120, PERSON_LONG, "m1", NOW - i * DAY, 2)

    val stockMovements = listOf(movementEntity(1, "PURCHASE", QTY_BIG), movementEntity(2, "SALE", -QTY_BIG), movementEntity(3, "ADJUSTMENT", 500), movementEntity(4, "DAMAGE", -1000))
    val profit = ProductProfit(QTY_BIG, HUGE, BIG, HUGE)
    val productDetail = ProductDetail(ProductStock(product(1, NAME_200, BIG, "Variante de 40 caracteres de largo para probar"), QTY_BIG), stockMovements, profit)

    fun str(v: String) = kotlinx.serialization.json.JsonPrimitive(v)
    fun num(v: Long) = kotlinx.serialization.json.JsonPrimitive(v)
    val history = HistoryState.Loaded(
        listOf(
            ProductHistoryEntryDto(1, "product.update", "m1", PERSON_LONG, "OWNER", "2026-09-28T15:04:05Z", mapOf("name" to FieldChangeDto(str(NAME_120), str(NAME_200)), "priceMinor" to FieldChangeDto(num(MID), num(BIG))), null),
            ProductHistoryEntryDto(2, "product.create", "m2", "Ana", "ADMIN", "2026-09-01T10:00:00Z", mapOf("priceMinor" to FieldChangeDto(null, num(MID))), null),
        ),
    )

    // ---------- compras ----------

    fun supplier(i: Int, name: String) = SupplierEntity("sp$i", name, PHONE_30, "Notas", true, 1)
    val suppliers = listOf(supplier(1, NAME_120), supplier(2, "Distribuidora del Norte"), supplier(3, PERSON_LONG), supplier(4, LONG_WORD))
    val balances = mapOf("sp1" to HUGE, "sp3" to BIG)

    fun purchase(i: Int, supplier: String?, voided: Boolean = false) = PurchaseRow(PurchaseEntity("pu$i", "sp$i", supplier, HUGE, "Nota de la compra", PERSON_LONG, NOW - i * DAY, voided, null, if (i == 3) 0 else 3), if (i == 2) HUGE else BIG)
    val purchases = listOf(purchase(1, NAME_120), purchase(2, "Distribuidora"), purchase(3, null), purchase(4, PERSON_LONG, voided = true))
    val purchaseItems = (1..6).map { PurchaseItemEntity("pu1", "it$it", "p$it", if (it % 2 == 0) NAME_120 else "Aceite", QTY_BIG, MID, HUGE, it) }
    val purchasePayments = listOf(
        SupplierPaymentEntity("pp1", "pu1", "sp1", BIG, "CASH_DRAWER", null, PERSON_LONG, NOW - DAY, false, null, 2),
        SupplierPaymentEntity("pp2", "pu1", "sp1", HUGE, "BANK", null, PERSON_LONG, NOW - 2 * DAY, true, null, 2),
    )
    val purchaseLines = (1..6).map { PurchaseLine("pl$it", "p$it", if (it % 2 == 0) NAME_120 else "Aceite", QTY_BIG, MID) }

    // ---------- avisos y programaciones ----------

    fun notif(i: Int, type: String, args: String, read: Boolean = false) = NotificationEntity("n$i", type, "STOCK", args, null, null, null, true, NOW - i * 3_600_000, if (read) NOW else null, null, null, null, 1)
    val notifications = listOf(
        notif(1, "LOW_STOCK", """{"productName":"$NAME_120","stockMilli":$QTY_BIG,"unit":"LB"}"""),
        notif(2, "SHIFT_CLOSED", """{"memberName":"$PERSON_LONG","differenceMinor":-$BIG}""", true),
        notif(3, "DAILY_SUMMARY", """{"salesCount":"12345","totalMinor":$HUGE,"expensesMinor":$BIG}"""),
        notif(4, "SCHEDULED", """{"title":"$NAME_120","body":"$NAME_200"}"""),
        notif(5, "SALE_DELETED", """{"memberName":"$PERSON_LONG","totalMinor":$HUGE}""", true),
        notif(6, "DEVICE_STALE", """{"deviceName":"$NAME_60","pending":"12345"}"""),
    )
    val prefs = PrefsUi(open = true, prefs = NOTIFICATION_TYPES.associateWith { it.length % 2 == 0 })

    fun schedule(i: Int, title: String, rule: ScheduleRuleDto, active: Boolean = true, next: String? = "2026-10-01T14:00:00Z") = ScheduleDto(
        "sc$i", title, NAME_200, "cuadra://caja", ScheduleAudienceDto(all = i % 2 == 0, roles = listOf("ADMIN", "CASHIER"), memberIds = listOf("m1", "m2", "m3")), rule, "America/Managua", next,
        if (next == null) "2026-09-01T14:00:00Z" else null, active, 12_345, 9_876, "2026-09-01T10:00:00Z",
    )
    val schedules = listOf(
        schedule(1, NAME_120, ScheduleRuleDto("WEEKLY", time = "08:00", days = listOf(1, 2, 3, 4, 5, 6, 7))), schedule(2, "Cierre", ScheduleRuleDto("DAILY", time = "20:00"), active = false),
        schedule(3, LONG_WORD, ScheduleRuleDto("ONCE", at = "2026-10-01T08:00"), active = false, next = null), schedule(4, "Mensual", ScheduleRuleDto("MONTHLY", time = "09:00", dayOfMonth = 15)),
    )
    val scheduleRuns = listOf(ScheduleRunDto("2026-09-28T14:00:00Z", "SENT", 12_345), ScheduleRunDto("2026-09-27T14:00:00Z", "SKIPPED_LATE", 3))

    // ---------- resumen ----------

    val summary = SummaryData(
        SalesTotals(12_345, HUGE, BIG), listOf(MethodAmountRow("CASH", HUGE), MethodAmountRow("TRANSFER", BIG), MethodAmountRow("CARD", BIG), MethodAmountRow("CREDIT", HUGE), MethodAmountRow("OTHER", 1)),
        Profit(HUGE, BIG, BIG, BIG, -BIG, 45), listOf(TopProductRow("p1", NAME_120, QTY_BIG, HUGE, BIG, true), TopProductRow("p2", "Refresco", 5000, BIG, 0, false), TopProductRow(null, NAME_200, 1000, MID, MID, true)),
        HUGE, 12,
    )
}

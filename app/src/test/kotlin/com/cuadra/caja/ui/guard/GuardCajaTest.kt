package com.cuadra.caja.ui.guard

import androidx.compose.ui.test.junit4.createEmptyComposeRule
import com.cuadra.caja.data.local.ProductEntity
import com.cuadra.caja.domain.PayMethod
import com.cuadra.caja.domain.UndoEntry
import com.cuadra.caja.domain.PaymentEntry
import com.cuadra.caja.ui.CajaActions
import com.cuadra.caja.ui.Notice
import com.cuadra.caja.ui.PosTab
import com.cuadra.caja.ui.ProductDraft
import com.cuadra.caja.ui.OpenPricing
import com.cuadra.caja.ui.Weighing
import com.cuadra.caja.domain.OpenPriceEntry
import com.cuadra.caja.domain.Pricing
import com.cuadra.caja.ui.screens.OpenPriceDialog
import com.cuadra.caja.ui.common.SectionEntry
import com.cuadra.caja.ui.common.SectionBar
import com.cuadra.caja.ui.common.ScanButton
import com.cuadra.caja.ui.screens.CajaContent
import com.cuadra.caja.ui.screens.CobroContent
import com.cuadra.caja.ui.screens.NoticeDialog
import com.cuadra.caja.ui.screens.ParkDialog
import com.cuadra.caja.ui.screens.ParkedDialog
import com.cuadra.caja.ui.screens.ProductDialog
import com.cuadra.caja.ui.screens.ShareSheet
import com.cuadra.caja.ui.screens.ShareUi
import com.cuadra.caja.ui.screens.WeighingDialog
import com.cuadra.caja.ui.common.ScannerSheet
import com.cuadra.caja.ui.common.ScannerUi
import com.cuadra.caja.domain.ScanHint
import com.cuadra.caja.R
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import com.cuadra.caja.ui.SaleNoticeUi
import com.cuadra.caja.ui.SaleUndoUi
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h900dp-xhdpi", application = android.app.Application::class)
class GuardCajaTest {
    @get:Rule val rule = createEmptyComposeRule()

    @Test fun cajaCobroAndSheets() {
        val runner = GuardRunner(rule, "caja")
        runner.run(
            listOf(
                GuardCase("Caja: teclado, carrito vacío", GuardMatrix.FULL, CAJA_KEYS) { Caja(Fixtures.cajaUi()) },
                GuardCase("Caja: teclado, carrito lleno", GuardMatrix.FULL, CAJA_KEYS) { Caja(Fixtures.cajaUi(cart = Fixtures.fullCart, entry = Fixtures.entry('1', '2', '5'))) },
                GuardCase("Caja: teclado, monto enorme", GuardMatrix.FULL, CAJA_KEYS) { Caja(Fixtures.cajaUi(cart = Fixtures.oneLineCart, entry = Fixtures.bigEntry)) },
                GuardCase("Caja: teclado, multiplicación", GuardMatrix.FULL, CAJA_KEYS) { Caja(Fixtures.cajaUi(cart = Fixtures.oneLineCart, entry = Fixtures.entryTimes)) },
                GuardCase("Caja: número del recibo con 3 líneas", GuardMatrix.FULL, CAJA_KEYS, expectBadge = 3) { Caja(Fixtures.cajaUi(cart = Fixtures.cartOf(3), entry = Fixtures.entry('7'))) },
                GuardCase("Caja: número del recibo con 15 líneas", GuardMatrix.FULL, CAJA_KEYS, expectBadge = 15) { Caja(Fixtures.cajaUi(cart = Fixtures.cartOf(15), entry = Fixtures.entry('7'))) },
                GuardCase("Caja: número del recibo con 999 líneas", GuardMatrix.FULL, CAJA_KEYS, expectBadge = 999) { Caja(Fixtures.cajaUi(cart = Fixtures.cartOf(999), entry = Fixtures.entry('7'))) },
                GuardCase("Caja: número del recibo con 999 líneas, teléfono bajo", GuardMatrix.SMALL_PHONE, CAJA_KEYS, expectBadge = 999) { Caja(Fixtures.cajaUi(cart = Fixtures.cartOf(999), entry = Fixtures.entry('7'))) },
                GuardCase("Caja: teclado, carrito de 15 líneas (agregar ya no abre aviso: la tira lo reemplaza)", GuardMatrix.FULL, CAJA_KEYS) { Caja(Fixtures.cajaUi(cart = Fixtures.cart15, entry = Fixtures.entry('7'), undo = Fixtures.undoAdded)) },
                GuardCase("Caja: teclado, tira de línea eliminada", GuardMatrix.FULL, CAJA_KEYS) { Caja(Fixtures.cajaUi(cart = Fixtures.cart15, undo = Fixtures.undoDeleted), business = Fixtures.BUSINESS_120, member = Fixtures.PERSON_LONG) },
                GuardCase("Caja: una sola pestaña", GuardMatrix.FULL, CAJA_KEYS) { Caja(Fixtures.cajaUi(cart = Fixtures.oneLineCart), tabs = listOf(PosTab.MANUAL)) },
                GuardCase("Caja: solo la pestaña Productos", GuardMatrix.FULL, expectTotal = true) { Caja(Fixtures.cajaUi(PosTab.PRODUCTS, Fixtures.oneLineCart), tabs = listOf(PosTab.PRODUCTS)) },
                // «Pedir descripción al agregar» (por omisión apagado: los demás casos ya lo dibujan sin el campo).
                GuardCase("Caja: descripción encendida, carrito lleno", GuardMatrix.FULL, CAJA_KEYS) { Caja(Fixtures.cajaUi(cart = Fixtures.fullCart, entry = Fixtures.entry('1', '2', '5')).copy(description = Fixtures.NAME_60), askDescription = true) },
                GuardCase("Caja: descripción encendida, monto enorme", GuardMatrix.FULL, CAJA_KEYS) { Caja(Fixtures.cajaUi(cart = Fixtures.oneLineCart, entry = Fixtures.bigEntry), askDescription = true, readerReady = true) },
                GuardCase("Caja: descripción encendida (teclado)", GuardMatrix.KEYBOARD, expectTotal = true) { Caja(Fixtures.cajaUi(cart = Fixtures.fullCart, entry = Fixtures.entry('3', '0')).copy(description = Fixtures.NAME_60), askDescription = true) },
                GuardCase("Caja: descripción encendida, teléfono bajo", GuardMatrix.SMALL_PHONE, CAJA_KEYS) { Caja(Fixtures.cajaUi(cart = Fixtures.cart15, entry = Fixtures.entry('1', '2', '5')), askDescription = true, readerReady = true) },
                GuardCase("Caja: descripción apagada (por omisión), teléfono bajo con monto enorme", GuardMatrix.SMALL_PHONE, CAJA_KEYS) { Caja(Fixtures.cajaUi(cart = Fixtures.cart15, entry = Fixtures.bigEntry), askDescription = false) },
                GuardCase("Caja: teléfono bajo, carrito lleno", GuardMatrix.SMALL_PHONE, CAJA_KEYS) { Caja(Fixtures.cajaUi(cart = Fixtures.cart15, entry = Fixtures.entry('1', '2', '5'))) },
                GuardCase("Caja: teléfono bajo, lector listo, negocio largo y monto enorme", GuardMatrix.SMALL_PHONE, CAJA_KEYS) { Caja(Fixtures.cajaUi(cart = Fixtures.oneLineCart, entry = Fixtures.bigEntry), business = Fixtures.BUSINESS_120, member = Fixtures.PERSON_LONG, readerReady = true) },
                GuardCase("Caja: teléfono bajo, aviso Deshacer (línea quitada)", GuardMatrix.SMALL_PHONE, CAJA_KEYS) { Caja(Fixtures.cajaUi(cart = Fixtures.cart15, entry = Fixtures.entry('7'), undo = Fixtures.undoDeleted), readerReady = true) },
                GuardCase("Caja: teléfono bajo, Productos (frecuentes y más vendidos)", GuardMatrix.SMALL_PHONE, expectTotal = true) { Caja(Fixtures.cajaUi(PosTab.PRODUCTS, Fixtures.cart15), readerReady = true) },
                GuardCase("Caja: teléfono bajo, Productos buscando", GuardMatrix.SMALL_PHONE, expectTotal = true) { Caja(Fixtures.cajaUi(PosTab.PRODUCTS, Fixtures.cart15, query = "ref"), products = Fixtures.pane(query = "ref", results = Fixtures.searchProducts)) },
                GuardCase("Caja: Productos con aviso Deshacer (línea quitada)", GuardMatrix.FULL, expectTotal = true) { Caja(Fixtures.cajaUi(PosTab.PRODUCTS, Fixtures.cart15, undo = Fixtures.undoDeleted)) },
                // Tira de la última línea (regla L): Manual y Productos, nombres cortos y larguísimos, por peso con decimales, montos enormes, 999 unidades, cantidad de 8 cifras,
                // línea manual («Varios»), con el aviso de línea quitada, con teclado y en el teléfono bajo.
                GuardCase("Tira Manual: nombre corto", GuardMatrix.FULL, CAJA_KEYS, expectStrip = true) { Caja(Fixtures.cajaUi(cart = Fixtures.cartEndingIn(Fixtures.stripShort), entry = Fixtures.entry('1', '2'))) },
                GuardCase("Tira Manual: nombre de 200 caracteres", GuardMatrix.FULL, CAJA_KEYS, expectStrip = true) { Caja(Fixtures.cajaUi(cart = Fixtures.cartEndingIn(Fixtures.stripLongName), entry = Fixtures.entry('1', '2'))) },
                GuardCase("Tira Manual: por peso con decimales", GuardMatrix.FULL, CAJA_KEYS, expectStrip = true) { Caja(Fixtures.cajaUi(cart = Fixtures.cartEndingIn(Fixtures.stripWeighed))) },
                GuardCase("Tira Manual: monto enorme y 999 unidades", GuardMatrix.FULL, CAJA_KEYS, expectStrip = true) { Caja(Fixtures.cajaUi(cart = Fixtures.cartEndingIn(Fixtures.stripBigAmount), entry = Fixtures.bigEntry)) },
                GuardCase("Tira Manual: 999 unidades", GuardMatrix.FULL, CAJA_KEYS, expectStrip = true) { Caja(Fixtures.cajaUi(cart = Fixtures.cartEndingIn(Fixtures.strip999))) },
                GuardCase("Tira Manual: cantidad de 8 cifras con palabra larguísima", GuardMatrix.FULL, CAJA_KEYS, expectStrip = true) { Caja(Fixtures.cajaUi(cart = Fixtures.cartEndingIn(Fixtures.stripBigQty))) },
                GuardCase("Tira Manual: línea manual «Varios»", GuardMatrix.FULL, CAJA_KEYS, expectStrip = true) { Caja(Fixtures.cajaUi(cart = Fixtures.cartEndingIn(Fixtures.stripManual))) },
                GuardCase("Tira Manual: una sola línea, lector listo, negocio largo", GuardMatrix.FULL, CAJA_KEYS, expectStrip = true, stripWhen = { STRIP_NORMAL(it) && it.effectiveScale <= 1.0f }) { Caja(Fixtures.cajaUi(cart = Fixtures.oneLineCart), business = Fixtures.BUSINESS_120, member = Fixtures.PERSON_LONG, readerReady = true) },
                GuardCase("Tira Manual: con el aviso «Línea quitada · Deshacer»", GuardMatrix.FULL, CAJA_KEYS) { Caja(Fixtures.cajaUi(cart = Fixtures.cartEndingIn(Fixtures.stripLongName), undo = Fixtures.undoDeleted)) },
                GuardCase("Tira Manual: descripción encendida", GuardMatrix.FULL, CAJA_KEYS) { Caja(Fixtures.cajaUi(cart = Fixtures.cartEndingIn(Fixtures.stripShort)).copy(description = Fixtures.NAME_60), askDescription = true) },
                GuardCase("Tira Manual: teclado del sistema", GuardMatrix.KEYBOARD, expectTotal = true) { Caja(Fixtures.cajaUi(cart = Fixtures.cartEndingIn(Fixtures.stripLongName), entry = Fixtures.entry('3', '0')).copy(description = Fixtures.NAME_60), askDescription = true) },
                GuardCase("Tira Manual: teléfono bajo, nombre largo", GuardMatrix.SMALL_PHONE, CAJA_KEYS) { Caja(Fixtures.cajaUi(cart = Fixtures.cartEndingIn(Fixtures.stripLongName), entry = Fixtures.entry('1', '2'))) },
                GuardCase("Tira Manual: teléfono bajo, monto enorme", GuardMatrix.SMALL_PHONE, CAJA_KEYS) { Caja(Fixtures.cajaUi(cart = Fixtures.cartEndingIn(Fixtures.stripBigAmount), entry = Fixtures.bigEntry), readerReady = true) },
                GuardCase("Tira Productos: nombre corto", GuardMatrix.FULL, expectTotal = true, expectStrip = true) { Caja(Fixtures.cajaUi(PosTab.PRODUCTS, Fixtures.cartEndingIn(Fixtures.stripShort))) },
                GuardCase("Tira Productos: nombre de 200 caracteres", GuardMatrix.FULL, expectTotal = true, expectStrip = true) { Caja(Fixtures.cajaUi(PosTab.PRODUCTS, Fixtures.cartEndingIn(Fixtures.stripLongName))) },
                GuardCase("Tira Productos: por peso con decimales", GuardMatrix.FULL, expectTotal = true, expectStrip = true) { Caja(Fixtures.cajaUi(PosTab.PRODUCTS, Fixtures.cartEndingIn(Fixtures.stripWeighed))) },
                GuardCase("Tira Productos: monto enorme y 999 unidades", GuardMatrix.FULL, expectTotal = true, expectStrip = true) { Caja(Fixtures.cajaUi(PosTab.PRODUCTS, Fixtures.cartEndingIn(Fixtures.stripBigAmount))) },
                GuardCase("Tira Productos: cantidad de 8 cifras", GuardMatrix.FULL, expectTotal = true, expectStrip = true) { Caja(Fixtures.cajaUi(PosTab.PRODUCTS, Fixtures.cartEndingIn(Fixtures.stripBigQty))) },
                GuardCase("Tira Productos: hueco reservado con el recibo vacío", GuardMatrix.FULL, expectTotal = true, expectStrip = true) { Caja(Fixtures.cajaUi(PosTab.PRODUCTS)) },
                GuardCase("Tira Productos: buscando con resultados", GuardMatrix.FULL, expectTotal = true, expectStrip = true) { Caja(Fixtures.cajaUi(PosTab.PRODUCTS, Fixtures.cartEndingIn(Fixtures.stripLongName), query = "ref"), products = Fixtures.pane(query = "ref", results = Fixtures.searchProducts)) },
                GuardCase("Tira Productos: teclado abierto buscando", GuardMatrix.KEYBOARD) { Caja(Fixtures.cajaUi(PosTab.PRODUCTS, Fixtures.cartEndingIn(Fixtures.stripShort), query = "ref"), products = Fixtures.pane(query = "ref", results = Fixtures.searchProducts)) },
                GuardCase("Tira Productos: con el aviso «Línea quitada · Deshacer»", GuardMatrix.FULL, expectTotal = true, expectStrip = true) { Caja(Fixtures.cajaUi(PosTab.PRODUCTS, Fixtures.cartEndingIn(Fixtures.stripLongName), undo = Fixtures.undoDeleted)) },
                GuardCase("Tira Productos: teléfono bajo, nombre largo", GuardMatrix.SMALL_PHONE, expectTotal = true, expectStrip = true) { Caja(Fixtures.cajaUi(PosTab.PRODUCTS, Fixtures.cartEndingIn(Fixtures.stripLongName)), readerReady = true) },
                GuardCase("Tira Productos: teléfono bajo, monto enorme", GuardMatrix.SMALL_PHONE, expectTotal = true, expectStrip = true) { Caja(Fixtures.cajaUi(PosTab.PRODUCTS, Fixtures.cartEndingIn(Fixtures.stripBigAmount))) },
                GuardCase("Tira Productos: teléfono bajo, hueco vacío", GuardMatrix.SMALL_PHONE, expectTotal = true, expectStrip = true) { Caja(Fixtures.cajaUi(PosTab.PRODUCTS)) },
                GuardCase("Recibo: hoja con 15 líneas y nombres largos", GuardMatrix.FULL) { Caja(Fixtures.cajaUi(cart = Fixtures.cart15).copy(receiptOpen = true)) },
                GuardCase("Recibo: hoja con pista y tira Deshacer", GuardMatrix.FULL) { Caja(Fixtures.cajaUi(cart = Fixtures.cart15, undo = Fixtures.undoDeleted, hint = true).copy(receiptOpen = true)) },
                GuardCase("Recibo: hoja llena con Vaciar, tira «Recibo vaciado»", GuardMatrix.FULL) { Caja(Fixtures.cajaUi(cart = Fixtures.cart15, undo = UndoEntry.Cleared(Fixtures.cart15)).copy(receiptOpen = true)) },
                GuardCase("Recibo: hoja con una línea", GuardMatrix.FULL) { Caja(Fixtures.cajaUi(cart = Fixtures.oneLineCart).copy(receiptOpen = true)) },
                GuardCase("Recibo: hoja vacía", GuardMatrix.FULL) { Caja(Fixtures.cajaUi().copy(receiptOpen = true)) },
                // Promociones por cantidad: la línea «Promo N por X: −Y» bajo la última línea que tocó, con nombres y montos enormes.
                GuardCase("Recibo: hoja con promociones", GuardMatrix.FULL) { Caja(Fixtures.promoUi().copy(receiptOpen = true)) },
                // Caso del dueño: 10 cervezas a C$ 40 con «3 por C$ 100» = C$ 340.
                GuardCase("Recibo: 10 cervezas con «3 por C$ 100»", GuardMatrix.FULL) {
                    Caja(Fixtures.promoUi(cart = com.cuadra.caja.domain.Cart(listOf(com.cuadra.caja.domain.CartLine("b1", "pb1", null, "Cerveza", null, 4_000, null, 10_000)))).copy(receiptOpen = true))
                },
                GuardCase("Tira Manual: la última línea con promoción", GuardMatrix.FULL, CAJA_KEYS, expectStrip = true) { Caja(Fixtures.promoUi()) },
                GuardCase("Tira Productos: la última línea con promoción", GuardMatrix.FULL, expectTotal = true, expectStrip = true) { Caja(Fixtures.promoUi(PosTab.PRODUCTS)) },
                GuardCase("Tira Manual: promoción, teléfono bajo", GuardMatrix.SMALL_PHONE, CAJA_KEYS) { Caja(Fixtures.promoUi()) },
                GuardCase("Cantidad: escribir (nombre largo)", GuardMatrix.FULL) { Quantity(Fixtures.lines[0], "1") },
                GuardCase("Cantidad: escribir (teclado)", GuardMatrix.KEYBOARD) { Quantity(Fixtures.lines[0], "15") },
                GuardCase("Cantidad: por peso con decimales", GuardMatrix.FULL) { Quantity(Fixtures.lines[3], "0.75") },
                GuardCase("Cantidad: cero elimina", GuardMatrix.FULL) { Quantity(Fixtures.lines[1], "0") },
                GuardCase("Cantidad: inválida", GuardMatrix.FULL) { Quantity(Fixtures.lines[2], "1.5") },
                // Editar una línea MANUAL (sin producto): descripción (dictable) y cantidad.
                GuardCase("Línea manual: descripción larga y cantidad", GuardMatrix.FULL) { Quantity(Fixtures.manualLine, "2", Fixtures.NAME_60 + " " + Fixtures.LONG_WORD) },
                GuardCase("Línea manual: descripción y cantidad (teclado)", GuardMatrix.KEYBOARD) { Quantity(Fixtures.manualLine, "2", Fixtures.NAME_60) },
                GuardCase("Línea manual: sin descripción («Varios»)", GuardMatrix.FULL) { Quantity(Fixtures.manualLine, "1", "") },
                GuardCase("Línea manual: cero elimina (teclado)", GuardMatrix.KEYBOARD) { Quantity(Fixtures.manualLine, "0", "Envío") },
                GuardCase("Línea de catálogo: solo cantidad (teclado)", GuardMatrix.KEYBOARD) { Quantity(Fixtures.lines[1], "3") },
                GuardCase("Productos: vacío (sin frecuentes ni ventas)", GuardMatrix.FULL, expectTotal = true) { Caja(Fixtures.cajaUi(PosTab.PRODUCTS), products = com.cuadra.caja.domain.ProductsPane()) },
                GuardCase("Productos: vacío, quien no gestiona", GuardMatrix.FULL, expectTotal = true) { Caja(Fixtures.cajaUi(PosTab.PRODUCTS), products = com.cuadra.caja.domain.ProductsPane(), canManage = false) },
                GuardCase("Productos: solo frecuentes (nombres largos)", GuardMatrix.FULL, expectTotal = true) { Caja(Fixtures.cajaUi(PosTab.PRODUCTS, Fixtures.fullCart), products = Fixtures.pane(frequents = 10, best = 0)) },
                GuardCase("Productos: frecuentes y más vendidos", GuardMatrix.FULL, expectTotal = true) { Caja(Fixtures.cajaUi(PosTab.PRODUCTS, Fixtures.fullCart)) },
                GuardCase("Productos: solo más vendidos", GuardMatrix.FULL, expectTotal = true) { Caja(Fixtures.cajaUi(PosTab.PRODUCTS), products = Fixtures.pane(frequents = 0, best = 8)) },
                GuardCase("Productos: búsqueda con resultados", GuardMatrix.FULL, expectTotal = true) { Caja(Fixtures.cajaUi(PosTab.PRODUCTS, Fixtures.fullCart, query = "ref"), products = Fixtures.pane(query = "ref", results = Fixtures.searchProducts)) },
                GuardCase("Productos: búsqueda con resultados (teclado)", GuardMatrix.KEYBOARD) { Caja(Fixtures.cajaUi(PosTab.PRODUCTS, Fixtures.fullCart, query = "ref"), products = Fixtures.pane(query = "ref", results = Fixtures.searchProducts)) },
                GuardCase("Productos: sin resultados, crear", GuardMatrix.FULL, expectTotal = true) { Caja(Fixtures.cajaUi(PosTab.PRODUCTS, query = Fixtures.NAME_60), products = Fixtures.pane(query = Fixtures.NAME_60)) },
                GuardCase("Productos: sin resultados (teclado)", GuardMatrix.KEYBOARD) { Caja(Fixtures.cajaUi(PosTab.PRODUCTS, Fixtures.fullCart, query = Fixtures.LONG_WORD), products = Fixtures.pane(query = Fixtures.LONG_WORD)) },
                GuardCase("Productos: ordenar frecuentes", GuardMatrix.FULL, expectTotal = true) { Caja(Fixtures.cajaUi(PosTab.PRODUCTS, Fixtures.fullCart).copy(reordering = true), products = Fixtures.pane(frequents = 10, best = 0)) },
                GuardCase("Productos: menú de frecuente (marcar)", GuardMatrix.FULL) { com.cuadra.caja.ui.screens.ProductMenuSheet(Fixtures.products[0].copy(isQuick = false), object : CajaActions {}) },
                GuardCase("Productos: menú de frecuente (quitar, precio abierto)", GuardMatrix.FULL) { com.cuadra.caja.ui.screens.ProductMenuSheet(Fixtures.products[9], object : CajaActions {}) },
                GuardCase("Caja: negocio de 120 caracteres", GuardMatrix.FULL, expectTotal = true) { Caja(Fixtures.cajaUi(cart = Fixtures.oneLineCart), business = Fixtures.BUSINESS_120, member = Fixtures.PERSON_LONG) },
                GuardCase("Cobro: efectivo con vuelto", GuardMatrix.FULL) { Cobro(Fixtures.cobro(Fixtures.BIG, PaymentEntry(PayMethod.CASH, Fixtures.BIG, tenderedMinor = Fixtures.HUGE), tendered = "99999999")) },
                GuardCase("Cobro: efectivo con vuelto (teclado)", GuardMatrix.KEYBOARD) { Cobro(Fixtures.cobro(Fixtures.BIG, PaymentEntry(PayMethod.CASH, Fixtures.BIG, tenderedMinor = Fixtures.HUGE), tendered = "99999999")) },
                GuardCase("Cobro: efectivo insuficiente", GuardMatrix.FULL) { Cobro(Fixtures.cobro(Fixtures.HUGE, PaymentEntry(PayMethod.CASH, Fixtures.HUGE, tenderedMinor = Fixtures.BIG), tendered = "1234567.89")) },
                GuardCase("Cobro: efectivo insuficiente (teclado)", GuardMatrix.KEYBOARD) { Cobro(Fixtures.cobro(Fixtures.HUGE, PaymentEntry(PayMethod.CASH, Fixtures.HUGE, tenderedMinor = Fixtures.BIG), tendered = "1234567.89")) },
                GuardCase("Cobro: tarjeta", GuardMatrix.FULL) { Cobro(Fixtures.cobro(Fixtures.BIG, PaymentEntry(PayMethod.CARD, Fixtures.BIG, reference = Fixtures.REF_30))) },
                GuardCase("Cobro: mixto efectivo y tarjeta", GuardMatrix.FULL) { Cobro(Fixtures.cobro(Fixtures.HUGE, PaymentEntry(PayMethod.CASH, Fixtures.BIG, tenderedMinor = Fixtures.HUGE), PaymentEntry(PayMethod.CARD, Fixtures.HUGE - Fixtures.BIG, reference = Fixtures.REF_30), tendered = "99999999")) },
                GuardCase("Cobro: mixto efectivo y tarjeta (teclado)", GuardMatrix.KEYBOARD) { Cobro(Fixtures.cobro(Fixtures.HUGE, PaymentEntry(PayMethod.CASH, Fixtures.BIG, tenderedMinor = Fixtures.HUGE), PaymentEntry(PayMethod.CARD, Fixtures.HUGE - Fixtures.BIG, reference = Fixtures.REF_30), tendered = "99999999")) },
                GuardCase("Cobro: fiado", GuardMatrix.FULL) { Cobro(Fixtures.cobro(Fixtures.BIG, PaymentEntry(PayMethod.CREDIT, Fixtures.BIG, debtorLabel = Fixtures.PERSON_LONG), debtor = Fixtures.PERSON_LONG, phone = Fixtures.PHONE_30, matches = Fixtures.customers, suggestions = listOf(Fixtures.NAME_60, Fixtures.LONG_WORD))) },
                GuardCase("Cobro: fiado (teclado)", GuardMatrix.KEYBOARD) { Cobro(Fixtures.cobro(Fixtures.BIG, PaymentEntry(PayMethod.CREDIT, Fixtures.BIG, debtorLabel = Fixtures.PERSON_LONG), debtor = Fixtures.PERSON_LONG, phone = Fixtures.PHONE_30, matches = Fixtures.customers, suggestions = listOf(Fixtures.NAME_60, Fixtures.LONG_WORD))) },
                GuardCase("Cobro: fiado, el negocio exige cliente (regla de Ajustes)", GuardMatrix.FULL) { Cobro(Fixtures.cobro(Fixtures.BIG, PaymentEntry(PayMethod.CREDIT, Fixtures.BIG, debtorLabel = Fixtures.PERSON_LONG), debtor = Fixtures.PERSON_LONG).copy(requiresCustomer = true)) },
                GuardCase("Cobro: fiado, el negocio exige cliente (teclado)", GuardMatrix.KEYBOARD) { Cobro(Fixtures.cobro(Fixtures.BIG, PaymentEntry(PayMethod.CREDIT, Fixtures.BIG, debtorLabel = Fixtures.PERSON_LONG), debtor = Fixtures.PERSON_LONG).copy(requiresCustomer = true)) },
                GuardCase("Cobro: fiado pasa el límite y el negocio bloquea", GuardMatrix.FULL) { Cobro(Fixtures.cobro(Fixtures.HUGE, PaymentEntry(PayMethod.CREDIT, Fixtures.HUGE, debtorLabel = Fixtures.PERSON_LONG, customerId = "c1"), debtor = Fixtures.PERSON_LONG).copy(limitEnforced = true, customer = Fixtures.customers.first().copy(creditLimitMinor = 1_000L, balanceMinor = Fixtures.BIG))) },
                GuardCase("Cobro: cinco métodos", GuardMatrix.FULL) {
                    Cobro(Fixtures.cobro(Fixtures.HUGE, PaymentEntry(PayMethod.CASH, 1_000_000, tenderedMinor = 2_000_000), PaymentEntry(PayMethod.TRANSFER, 1_000_000, reference = Fixtures.REF_30), PaymentEntry(PayMethod.CARD, 1_000_000, reference = Fixtures.REF_30),
                        PaymentEntry(PayMethod.CREDIT, 1_000_000, debtorLabel = "Ana"), PaymentEntry(PayMethod.OTHER, 5_999_999_900, otherLabel = Fixtures.NAME_60), tendered = "20000", debtor = "Ana", other = Fixtures.NAME_60))
                },
                GuardCase("Cobro: cinco métodos (teclado)", GuardMatrix.KEYBOARD) {
                    Cobro(Fixtures.cobro(Fixtures.HUGE, PaymentEntry(PayMethod.CASH, 1_000_000, tenderedMinor = 2_000_000), PaymentEntry(PayMethod.TRANSFER, 1_000_000, reference = Fixtures.REF_30), PaymentEntry(PayMethod.CARD, 1_000_000, reference = Fixtures.REF_30),
                        PaymentEntry(PayMethod.CREDIT, 1_000_000, debtorLabel = "Ana"), PaymentEntry(PayMethod.OTHER, 5_999_999_900, otherLabel = Fixtures.NAME_60), tendered = "20000", debtor = "Ana", other = Fixtures.NAME_60))
                },
                GuardCase("Cobro: solo transferencia, monto editable", GuardMatrix.FULL) { Cobro(Fixtures.cobro(Fixtures.BIG, PaymentEntry(PayMethod.TRANSFER, Fixtures.BIG, reference = Fixtures.REF_30))) },
                GuardCase("Cobro: solo transferencia (teclado)", GuardMatrix.KEYBOARD) { Cobro(Fixtures.cobro(Fixtures.BIG, PaymentEntry(PayMethod.TRANSFER, Fixtures.BIG, reference = Fixtures.REF_30))) },
                GuardCase("Cobro: transferencia parcial, falta el resto", GuardMatrix.FULL) { Cobro(Fixtures.cobro(Fixtures.HUGE, PaymentEntry(PayMethod.TRANSFER, Fixtures.BIG, reference = Fixtures.REF_30))) },
                GuardCase("Cobro: transferencia parcial, falta el resto (teclado)", GuardMatrix.KEYBOARD) { Cobro(Fixtures.cobro(Fixtures.HUGE, PaymentEntry(PayMethod.TRANSFER, Fixtures.BIG, reference = Fixtures.REF_30))) },
                GuardCase("Cobro: tarjeta parcial y fiado por el resto", GuardMatrix.FULL) { Cobro(Fixtures.cobro(Fixtures.HUGE, PaymentEntry(PayMethod.CARD, Fixtures.BIG), PaymentEntry(PayMethod.CREDIT, Fixtures.HUGE - Fixtures.BIG, debtorLabel = Fixtures.PERSON_LONG), debtor = Fixtures.PERSON_LONG, matches = Fixtures.customers)) },
                GuardCase("Cobro: transferencia se pasa del total", GuardMatrix.FULL) { Cobro(Fixtures.cobro(Fixtures.BIG, PaymentEntry(PayMethod.TRANSFER, Fixtures.HUGE))) },
                GuardCase("Cobro: tres líneas (transferencia, tarjeta, efectivo con vuelto)", GuardMatrix.FULL) { Cobro(Fixtures.cobro(Fixtures.HUGE, PaymentEntry(PayMethod.TRANSFER, Fixtures.BIG), PaymentEntry(PayMethod.CARD, Fixtures.BIG), PaymentEntry(PayMethod.CASH, Fixtures.HUGE - 2 * Fixtures.BIG, tenderedMinor = Fixtures.HUGE), tendered = "99999999")) },
                GuardCase("Cobro: módulo Fiado apagado (sin Fiado, con Falta)", GuardMatrix.FULL) { Cobro(Fixtures.cobro(Fixtures.HUGE, PaymentEntry(PayMethod.CARD, Fixtures.BIG)).copy(availableMethods = com.cuadra.caja.domain.PaymentMethods.available(mapOf("credit" to false)))) },
                GuardCase("Cobro: listo con vuelto", GuardMatrix.FULL) { Cobro(Fixtures.cobro(Fixtures.BIG, PaymentEntry(PayMethod.CASH, Fixtures.BIG), done = Fixtures.HUGE)) },
                // «Venta cobrada» ya no pide nada: sin botones de «Nueva venta» ni «Anular»; vuelve sola (1.2 s; 4 s con WhatsApp encendido y su botón).
                GuardCase("Cobro: listo sin vuelto (tarjeta, 1.2 s)", GuardMatrix.FULL) { Cobro(Fixtures.cobro(Fixtures.BIG, PaymentEntry(PayMethod.CARD, Fixtures.BIG), done = 0L).copy(doneSaleId = "s1", doneAtMillis = Fixtures.NOW)) },
                GuardCase("Cobro: listo con vuelto enorme, WhatsApp encendido (4 s, botón y barra)", GuardMatrix.FULL) {
                    Cobro(Fixtures.cobro(Fixtures.BIG, PaymentEntry(PayMethod.CASH, Fixtures.BIG), done = Fixtures.HUGE).copy(doneSaleId = "s1", doneAtMillis = Fixtures.NOW, offerWhatsApp = true,
                        doneShare = com.cuadra.caja.ui.ShareRequest.Ticket(listOf(Fixtures.NAME_120 to Fixtures.BIG), Fixtures.BIG)))
                },
                // El aviso de la venta nueva (vuelto / «Anular») y la hoja del motivo (con teclado y ya fuera de plazo).
                GuardCase("Caja: aviso «Vuelto C$ X · Anular»", GuardMatrix.FULL, CAJA_KEYS) { Caja(Fixtures.cajaUi(cart = Fixtures.cartOf(3), entry = Fixtures.entry('7')).copy(saleNotice = SaleNoticeUi("s1", Fixtures.BIG, Fixtures.HUGE, System.currentTimeMillis()))) },
                GuardCase("Caja: aviso «Venta cobrada · C$ X · Anular»", GuardMatrix.FULL, CAJA_KEYS) { Caja(Fixtures.cajaUi(cart = Fixtures.cartOf(3), entry = Fixtures.entry('7')).copy(saleNotice = SaleNoticeUi("s1", Fixtures.HUGE, 0, System.currentTimeMillis()))) },
                GuardCase("Caja: aviso del vuelto con el comprobante impreso", GuardMatrix.FULL, CAJA_KEYS) {
                    Caja(Fixtures.cajaUi(cart = Fixtures.cartOf(3), entry = Fixtures.entry('7')).copy(saleNotice = SaleNoticeUi("s1", Fixtures.BIG, 2_750, System.currentTimeMillis()), printer = com.cuadra.caja.domain.printing.PrinterBadge.CONNECTED, printNotice = com.cuadra.caja.domain.printing.PrintNotice.PRINTED))
                },
                GuardCase("Caja: aviso de venta anulada", GuardMatrix.FULL, CAJA_KEYS) { Caja(Fixtures.cajaUi(cart = Fixtures.cartOf(3), entry = Fixtures.entry('7')).copy(saleNotice = SaleNoticeUi("s1", Fixtures.BIG, 0, Fixtures.NOW, undone = true))) },
                GuardCase("Caja: anular (motivo)", GuardMatrix.FULL) { Caja(Fixtures.cajaUi(cart = Fixtures.cartOf(3)).copy(saleNotice = SaleNoticeUi("s1", Fixtures.BIG, 0, Fixtures.NOW), saleUndo = SaleUndoUi("s1", Fixtures.NOW))) },
                GuardCase("Caja: anular fuera de plazo (teclado)", GuardMatrix.KEYBOARD) {
                    Caja(Fixtures.cajaUi(cart = Fixtures.cartOf(3)).copy(saleNotice = SaleNoticeUi("s1", Fixtures.BIG, 0, Fixtures.NOW), saleUndo = SaleUndoUi("s1", Fixtures.NOW, Fixtures.NAME_120, tooLate = true)))
                },
                // WhatsApp al terminar es opcional: apagado no hay botón; encendido sí (con el detalle del fiado si lo hubo) y la casilla del fiado solo si está encendido.
                GuardCase("Cobro: listo, WhatsApp encendido (botón)", GuardMatrix.FULL) { Cobro(Fixtures.cobro(Fixtures.BIG, PaymentEntry(PayMethod.CASH, Fixtures.BIG), done = Fixtures.HUGE).copy(offerWhatsApp = true, doneShare = com.cuadra.caja.ui.ShareRequest.Ticket(listOf(Fixtures.NAME_120 to Fixtures.BIG), Fixtures.BIG))) },
                GuardCase("Cobro: listo sin vuelto, WhatsApp encendido (botón)", GuardMatrix.FULL) { Cobro(Fixtures.cobro(Fixtures.BIG, PaymentEntry(PayMethod.CARD, Fixtures.BIG), done = 0L).copy(offerWhatsApp = true, doneShare = com.cuadra.caja.ui.ShareRequest.Ticket(listOf(Fixtures.NAME_120 to Fixtures.BIG), Fixtures.BIG))) },
                GuardCase("Cobro: fiado con la casilla de WhatsApp (encendido)", GuardMatrix.FULL) { Cobro(Fixtures.cobro(Fixtures.HUGE, PaymentEntry(PayMethod.CREDIT, Fixtures.HUGE, debtorLabel = Fixtures.PERSON_LONG), debtor = Fixtures.PERSON_LONG).copy(offerWhatsApp = true)) },
                GuardCase("Cobro: fiado sin casilla de WhatsApp (apagado)", GuardMatrix.FULL) { Cobro(Fixtures.cobro(Fixtures.HUGE, PaymentEntry(PayMethod.CREDIT, Fixtures.HUGE, debtorLabel = Fixtures.PERSON_LONG), debtor = Fixtures.PERSON_LONG)) },
                GuardCase("Diálogo: aparcadas", GuardMatrix.FULL) { ParkedDialog(Fixtures.parked, object : CajaActions {}) },
                // Cobro en caja (ADR 0015): el botón del encabezado, la lista, el detalle, anular con motivo, «Cobrar ahora / Enviar a caja», el destello y su aviso.
                GuardCase("Caja: cobro en caja, botón «Por cobrar en caja»", GuardMatrix.FULL, CAJA_KEYS) { Caja(Fixtures.cajaUi(cart = Fixtures.cartOf(3), entry = Fixtures.entry('7')), registerCheckout = true, parked = Fixtures.queued + Fixtures.parked) },
                GuardCase("Caja: cobro en caja, teléfono bajo", GuardMatrix.SMALL_PHONE, CAJA_KEYS) { Caja(Fixtures.cajaUi(cart = Fixtures.cart15, entry = Fixtures.entry('7')), business = Fixtures.BUSINESS_120, registerCheckout = true, parked = Fixtures.queued + Fixtures.parked) },
                GuardCase("Caja: aviso «Enviada a caja · nota»", GuardMatrix.FULL, CAJA_KEYS) { Caja(Fixtures.cajaUi(entry = Fixtures.entry('7')).copy(saleNotice = SaleNoticeUi("s1", Fixtures.HUGE, 0, Fixtures.NOW, sent = true, note = Fixtures.NAME_60)), registerCheckout = true) },
                GuardCase("Por cobrar en caja: lista con apartadas", GuardMatrix.FULL) { com.cuadra.caja.ui.screens.RegisterQueueSheet(com.cuadra.caja.domain.RegisterQueue.split(Fixtures.queued + Fixtures.parked), object : CajaActions {}, counts = Fixtures.queueCounts) },
                GuardCase("Por cobrar en caja: vacía", GuardMatrix.FULL) { com.cuadra.caja.ui.screens.RegisterQueueSheet(com.cuadra.caja.domain.RegisterQueue.split(emptyList()), object : CajaActions {}) },
                GuardCase("Por cobrar en caja: actualizando (cada 15 s)", GuardMatrix.FULL) {
                    com.cuadra.caja.ui.screens.RegisterQueueSheet(com.cuadra.caja.domain.RegisterQueue.split(Fixtures.queued + Fixtures.parked), object : CajaActions {}, counts = Fixtures.queueCounts, refreshing = true)
                },
                GuardCase("Apartadas: actualizando", GuardMatrix.FULL) { com.cuadra.caja.ui.screens.ParkedDialog(Fixtures.parked, object : CajaActions {}, refreshing = true) },
                GuardCase("Por cobrar en caja: descartar una apartada", GuardMatrix.FULL) { com.cuadra.caja.ui.screens.RegisterQueueSheet(com.cuadra.caja.domain.RegisterQueue.split(Fixtures.queued + Fixtures.parked), object : CajaActions {}, initialConfirm = Fixtures.parked.first().id) },
                GuardCase("Por cobrar en caja: detalle", GuardMatrix.FULL) { com.cuadra.caja.ui.screens.QueueTicketSheet(com.cuadra.caja.ui.QueueDetailUi(Fixtures.queued.first(), Fixtures.queueItems), object : CajaActions {}) },
                GuardCase("Por cobrar en caja: detalle sin nota", GuardMatrix.FULL) { com.cuadra.caja.ui.screens.QueueTicketSheet(com.cuadra.caja.ui.QueueDetailUi(Fixtures.queued.last(), Fixtures.queueItems.take(1)), object : CajaActions {}) },
                GuardCase("Por cobrar en caja: anular (motivo corto)", GuardMatrix.FULL) { com.cuadra.caja.ui.screens.QueueCancelSheet(com.cuadra.caja.ui.QueueCancelUi("s4", Fixtures.NAME_200, Fixtures.HUGE, "ab"), object : CajaActions {}) },
                GuardCase("Por cobrar en caja: anular (teclado)", GuardMatrix.KEYBOARD) { com.cuadra.caja.ui.screens.QueueCancelSheet(com.cuadra.caja.ui.QueueCancelUi("s4", Fixtures.NAME_60, Fixtures.HUGE, Fixtures.NAME_120), object : CajaActions {}) },
                GuardCase("Diálogo: la está cobrando otra persona", GuardMatrix.FULL) { NoticeDialog(Notice.TicketLocked(Fixtures.PERSON_LONG), object : CajaActions {}) },
                GuardCase("Cobro: cobrar ahora con el ajuste", GuardMatrix.FULL) { Cobro(Fixtures.cobro(Fixtures.BIG, PaymentEntry(PayMethod.CASH, Fixtures.BIG, tenderedMinor = Fixtures.HUGE), tendered = "99999999").copy(registerCheckout = true)) },
                GuardCase("Cobro: enviar a caja con nota", GuardMatrix.FULL) { Cobro(Fixtures.cobro(Fixtures.HUGE, PaymentEntry(PayMethod.CASH, Fixtures.HUGE)).copy(registerCheckout = true, toRegister = true, registerNote = Fixtures.NAME_60)) },
                GuardCase("Cobro: enviar a caja (teclado)", GuardMatrix.KEYBOARD) { Cobro(Fixtures.cobro(Fixtures.HUGE, PaymentEntry(PayMethod.CASH, Fixtures.HUGE)).copy(registerCheckout = true, toRegister = true, registerNote = Fixtures.NAME_60)) },
                GuardCase("Cobro: enviada a caja (destello)", GuardMatrix.FULL) { Cobro(Fixtures.cobro(Fixtures.HUGE, PaymentEntry(PayMethod.CASH, Fixtures.HUGE)).copy(registerCheckout = true, toRegister = true, sent = true, registerNote = Fixtures.NAME_60, doneSaleId = "s1", doneAtMillis = Fixtures.NOW)) },
                // Comprobante por WhatsApp con número (al terminar de cobrar y desde el detalle de una venta).
                GuardCase("WhatsApp con número: prefijo del país", GuardMatrix.FULL) { WaNumber(com.cuadra.caja.ui.screens.WhatsAppNumberUi("+505 ")) },
                GuardCase("WhatsApp con número: número inválido y dos apps", GuardMatrix.FULL) { WaNumber(com.cuadra.caja.ui.screens.WhatsAppNumberUi(Fixtures.PHONE_30, invalid = true, failed = true, choices = listOf("com.whatsapp", "com.whatsapp.w4b"), needsChoice = true)) },
                GuardCase("WhatsApp con número (teclado)", GuardMatrix.KEYBOARD) { WaNumber(com.cuadra.caja.ui.screens.WhatsAppNumberUi("+505 8888 1234")) },
                GuardCase("Diálogo: confirmar descartar una apartada", GuardMatrix.FULL) { ParkedDialog(Fixtures.parked, object : CajaActions {}, initialConfirm = Fixtures.parked.first().id) },
                GuardCase("Diálogo: aparcar", GuardMatrix.FULL) { ParkDialog(Fixtures.NAME_60, object : CajaActions {}) },
                GuardCase("Diálogo: aparcar (teclado)", GuardMatrix.KEYBOARD) { ParkDialog(Fixtures.NAME_60, object : CajaActions {}) },
                GuardCase("Diálogo: pesar", GuardMatrix.FULL) { WeighingDialog(Weighing(Fixtures.products[4].copy(name = Fixtures.NAME_120), false, "12345.678"), object : CajaActions {}) },
                GuardCase("Diálogo: pesar por monto", GuardMatrix.FULL) { WeighingDialog(Weighing(Fixtures.products[4], true, "99999999.99"), object : CajaActions {}) },
                GuardCase("Diálogo: producto nuevo", GuardMatrix.FULL) { ProductDialog(ProductDraft(Fixtures.NAME_120, "99999999.99", Pricing.BY_WEIGHT, true, "7501234567890123456789012345678901234567890"), object : CajaActions {}) },
                GuardCase("Diálogo: producto nuevo (teclado)", GuardMatrix.KEYBOARD) { ProductDialog(ProductDraft(Fixtures.NAME_120, "99999999.99", Pricing.BY_WEIGHT, true, "7501234567890123456789012345678901234567890"), object : CajaActions {}) },
                GuardCase("Diálogo: producto nuevo, precio abierto", GuardMatrix.FULL) { ProductDialog(ProductDraft(Fixtures.NAME_120, "", Pricing.OPEN, true, ""), object : CajaActions {}) },
                GuardCase("Diálogo: producto nuevo, precio abierto (teclado)", GuardMatrix.KEYBOARD) { ProductDialog(ProductDraft(Fixtures.NAME_120, "99999999.99", Pricing.OPEN, false, "7501234567890123456789012345678901234567890"), object : CajaActions {}) },
                GuardCase("Diálogo: precio abierto (nombre largo, sugerido enorme)", GuardMatrix.FULL) { OpenPriceDialog(OpenPricing(Fixtures.products[9].copy(name = Fixtures.NAME_200), OpenPriceEntry.start(Fixtures.HUGE, 2)), object : CajaActions {}) },
                GuardCase("Diálogo: precio abierto vacío", GuardMatrix.FULL) { OpenPriceDialog(OpenPricing(Fixtures.products[8], OpenPriceEntry()), object : CajaActions {}) },
                GuardCase("Diálogo: precio abierto, monto de 12 cifras", GuardMatrix.FULL) { OpenPriceDialog(OpenPricing(Fixtures.products[8], OpenPriceEntry("999999999999.99")), object : CajaActions {}) },
                GuardCase("Aviso: código ya lo tiene otro producto", GuardMatrix.FULL) { NoticeDialog(Notice.BarcodeInUse(Fixtures.NAME_200), object : CajaActions {}) },
                GuardCase("Diálogo: código desconocido", GuardMatrix.FULL) { NoticeDialog(Notice.CodeUnknown("7501234567890123456789012345678901234567890"), object : CajaActions {}) },
                GuardCase("Diálogo: aviso simple", GuardMatrix.FULL) { NoticeDialog(Notice.TicketLocked(), object : CajaActions {}) },
                GuardCase("Lector: con cámara", GuardMatrix.FULL) { Scanner(ScannerUi(addedCount = 12345, manual = "7501234567890123456789012345678901234567890")) },
                GuardCase("Lector: con cámara (teclado)", GuardMatrix.KEYBOARD) { Scanner(ScannerUi(addedCount = 12345, manual = "7501234567890123456789012345678901234567890")) },
                GuardCase("Lector: sin permiso", GuardMatrix.FULL) { Scanner(ScannerUi(granted = false, denied = true)) },
                GuardCase("Lector: sin cámara", GuardMatrix.FULL) { Scanner(ScannerUi(hasCamera = false)) },
                GuardCase("Lector: pista «acerca o aleja» tras 4 s", GuardMatrix.FULL) { Scanner(ScannerUi(hint = ScanHint.FOCUS, continuous = false)) },
                GuardCase("Lector: poca luz, sugiere linterna", GuardMatrix.FULL) { Scanner(ScannerUi(hint = ScanHint.LOW_LIGHT, hasZoom = true, zoom = 0.4f)) },
                GuardCase("Lector: poca luz y sin linterna", GuardMatrix.FULL) { Scanner(ScannerUi(hint = ScanHint.LOW_LIGHT, hasTorch = false, hasZoom = false)) },
                GuardCase("Lector: «Escaneado» encima de la cámara", GuardMatrix.FULL) { Scanner(ScannerUi(scanned = true, addedCount = 99999)) },
                GuardCase("Lector: cámara ocupada por otra app", GuardMatrix.FULL) { Scanner(ScannerUi(cameraBusy = true)) },
                GuardCase("Lector: cámara apagada en Ajustes", GuardMatrix.FULL) { Scanner(ScannerUi(cameraOff = true, manual = "7501234567890")) },
                GuardCase("Lector: cámara con error", GuardMatrix.FULL) { Scanner(ScannerUi(cameraError = true)) },
                GuardCase("Lector: pista con zoom (teclado)", GuardMatrix.KEYBOARD) { Scanner(ScannerUi(hint = ScanHint.LOW_LIGHT, hasZoom = true, zoom = 1f, manual = "7501234567890")) },
                GuardCase("Caja: lector listo (carrito lleno)", GuardMatrix.FULL, CAJA_KEYS) { Caja(Fixtures.cajaUi(cart = Fixtures.fullCart, entry = Fixtures.entry('1', '2')), readerReady = true) },
                GuardCase("Caja: lector listo, negocio largo y monto enorme", GuardMatrix.FULL, CAJA_KEYS) { Caja(Fixtures.cajaUi(cart = Fixtures.oneLineCart, entry = Fixtures.bigEntry), business = Fixtures.BUSINESS_120, member = Fixtures.PERSON_LONG, readerReady = true) },
                GuardCase("Caja: lector listo, Productos", GuardMatrix.FULL) { Caja(Fixtures.cajaUi(PosTab.PRODUCTS, Fixtures.cart15, undo = Fixtures.undoDeleted), readerReady = true) },
                GuardCase("WhatsApp: texto", GuardMatrix.FULL) { Share(ShareUi(false, R.string.share_title_credit, Fixtures.PERSON_LONG, Fixtures.PHONE_30, false, Fixtures.NAME_200, card(), false, emptyList(), true)) },
                GuardCase("WhatsApp: imagen y elegir app", GuardMatrix.FULL) { Share(ShareUi(false, R.string.share_title_statement, Fixtures.NAME_120, null, true, "", card(), true, listOf("com.whatsapp", "com.whatsapp.w4b"), false)) },
                GuardCase("Voz: campo compacto en reposo", GuardMatrix.FULL) { VoiceField(com.cuadra.caja.domain.VoiceState.Idle, "") },
                GuardCase("Voz: campo compacto escuchando con texto largo", GuardMatrix.FULL) { VoiceField(com.cuadra.caja.domain.VoiceState.Listening(Fixtures.NAME_60), Fixtures.NAME_60) },
                GuardCase("Voz: campo compacto escuchando (teclado)", GuardMatrix.KEYBOARD) { VoiceField(com.cuadra.caja.domain.VoiceState.Listening(""), "") },
                GuardCase("Voz: campo normal escuchando", GuardMatrix.FULL) { VoiceFieldNormal(com.cuadra.caja.domain.VoiceState.Listening("")) },
                GuardCase("Voz: diálogo motivo del permiso", GuardMatrix.FULL) { VoiceDialog(com.cuadra.caja.domain.VoiceState.AwaitingPermission, rationale = true) },
                GuardCase("Voz: diálogo sin dictado en el teléfono", GuardMatrix.FULL) { VoiceDialog(com.cuadra.caja.domain.VoiceState.Failed(com.cuadra.caja.domain.VoiceError.NO_SERVICE)) },
                GuardCase("Voz: diálogo permiso negado", GuardMatrix.FULL) { VoiceDialog(com.cuadra.caja.domain.VoiceState.Failed(com.cuadra.caja.domain.VoiceError.PERMISSION)) },
                GuardCase("Voz: diálogo permiso bloqueado", GuardMatrix.FULL) { VoiceDialog(com.cuadra.caja.domain.VoiceState.Failed(com.cuadra.caja.domain.VoiceError.PERMISSION, true)) },
                GuardCase("Voz: diálogo sin internet", GuardMatrix.FULL) { VoiceDialog(com.cuadra.caja.domain.VoiceState.Failed(com.cuadra.caja.domain.VoiceError.NETWORK)) },
                GuardCase("Voz: diálogo idioma no disponible", GuardMatrix.FULL) { VoiceDialog(com.cuadra.caja.domain.VoiceState.Failed(com.cuadra.caja.domain.VoiceError.LANGUAGE_UNAVAILABLE)) },
                GuardCase("Voz: diálogo idioma no disponible con los idiomas probados", GuardMatrix.FULL) { VoiceDialog(com.cuadra.caja.domain.VoiceState.Failed(com.cuadra.caja.domain.VoiceError.LANGUAGE_NOT_SUPPORTED), tried = listOf("es-NI", "es-419", "es-US", "es-ES", "es")) },
                GuardCase("Voz: diálogo genérico con los idiomas probados", GuardMatrix.FULL) { VoiceDialog(com.cuadra.caja.domain.VoiceState.Failed(com.cuadra.caja.domain.VoiceError.CLIENT), tried = listOf("en-US", "en-GB", "en")) },
                GuardCase("WhatsApp: comprobante de venta", GuardMatrix.FULL) { Share(ShareUi(false, R.string.share_title_ticket, "", null, false, Fixtures.NAME_200, card(), false, emptyList(), false)) },
                GuardCase("Barra de secciones", GuardMatrix.FULL) { Bar(0) },
                GuardCase("Barra de secciones, Gastos activo", GuardMatrix.FULL) { Bar(2) },
                GuardCase("Botón de escanear", GuardMatrix.FULL) { ScanButton({}) },
            ),
        )
        runner.assertClean()
    }

    @Composable private fun VoiceField(state: com.cuadra.caja.domain.VoiceState, text: String) =
        androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.padding(16.dp)) {
            com.cuadra.caja.ui.common.VoiceTextField(text, {}, androidx.compose.ui.Modifier.fillMaxWidth(), singleLine = true, compact = true, voiceState = state, placeholder = { com.cuadra.caja.ui.common.Text(stringResource(R.string.register_description_hint)) })
        }

    @Composable private fun VoiceFieldNormal(state: com.cuadra.caja.domain.VoiceState) =
        androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.padding(16.dp)) {
            com.cuadra.caja.ui.common.VoiceTextField("", {}, androidx.compose.ui.Modifier.fillMaxWidth(), singleLine = true, voiceState = state, label = { com.cuadra.caja.ui.common.Text(stringResource(R.string.register_description_hint)) })
        }

    @Composable private fun VoiceDialog(state: com.cuadra.caja.domain.VoiceState, rationale: Boolean = false, tried: List<String> = emptyList()) {
        val d = com.cuadra.caja.ui.common.VoiceDictation(object : com.cuadra.caja.ui.common.VoiceDictation.Host {
            override fun tap(d: com.cuadra.caja.ui.common.VoiceDictation) {}
            override fun dismiss(d: com.cuadra.caja.ui.common.VoiceDictation) {}
            override fun confirmRationale(d: com.cuadra.caja.ui.common.VoiceDictation) {}
            override fun retry(d: com.cuadra.caja.ui.common.VoiceDictation) {}
            override fun openSettings() {}
        })
        d.showRationale = rationale
        d.triedLanguages = tried
        com.cuadra.caja.ui.common.VoiceDialogs(d, state)
    }

    private val tabs = listOf(PosTab.MANUAL, PosTab.PRODUCTS)

    @Composable private fun Caja(
        ui: com.cuadra.caja.ui.CajaUi, tabs: List<PosTab> = this.tabs, products: com.cuadra.caja.domain.ProductsPane = Fixtures.pane,
        business: String = Fixtures.BUSINESS_NAME, member: String = "Kevin", readerReady: Boolean = false, askDescription: Boolean = false, canManage: Boolean = true,
        registerCheckout: Boolean = false, parked: List<com.cuadra.caja.data.local.SaleEntity> = Fixtures.parked,
    ) = CajaContent(ui, tabs, products, parked, Fixtures.HUGE, business, member, object : CajaActions {}, {}, readerReady = readerReady, askDescription = askDescription, canManageFrequents = canManage,
        registerCheckout = registerCheckout, lineCounts = Fixtures.queueCounts)

    @Composable private fun WaNumber(ui: com.cuadra.caja.ui.screens.WhatsAppNumberUi) = com.cuadra.caja.ui.screens.WhatsAppNumberSheet(ui, {}, {}, {}, {}, {})

    @Composable private fun Quantity(line: com.cuadra.caja.domain.CartLine, text: String, description: String = line.descriptionText) =
        com.cuadra.caja.ui.screens.QuantityDialog(line, object : CajaActions {}, initialText = text, initialDescription = description)

    @Composable private fun Cobro(cobro: com.cuadra.caja.ui.CobroUi) = CobroContent(Fixtures.cajaUi(cart = Fixtures.fullCart, cobro = cobro), object : CajaActions {}, nowMillis = Fixtures.NOW)

    @Composable private fun Scanner(ui: ScannerUi) = ScannerSheet(ui, {}, {}, {}, {}, {}, {}, {}, camera = {})

    @Composable private fun Share(ui: ShareUi) = ShareSheet(ui, {}, {}, {}, {}, {})

    private fun card() = com.cuadra.caja.domain.ShareCard(
        Fixtures.BUSINESS_120, Fixtures.NAME_60, Fixtures.PERSON_LONG,
        listOf(com.cuadra.caja.domain.CardLine(Fixtures.NAME_120, "C$ 1,234,567.89", false), com.cuadra.caja.domain.CardLine("Abono", "C$ 99,999,999.00", true)),
        "Saldo", "C$ 99,999,999.00",
    )

    @Composable private fun Bar(selected: Int) {
        val labels = listOf(R.string.nav_register to R.drawable.ic_nav_register, R.string.nav_credits to R.drawable.ic_nav_credit, R.string.nav_expenses to R.drawable.ic_nav_expenses, R.string.nav_sales to R.drawable.ic_nav_sales, R.string.nav_more to R.drawable.ic_nav_more)
        SectionBar(labels.mapIndexed { i, (l, ic) -> SectionEntry(stringResource(l), ic, i == selected) {} })
    }
}

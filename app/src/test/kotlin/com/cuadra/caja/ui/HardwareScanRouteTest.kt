package com.cuadra.caja.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/** A dónde va una lectura del lector físico según lo que haya abierto en la caja. */
class HardwareScanRouteTest {
    @Test fun plainRegisterAddsToTheTicket() = assertEquals(HardwareScanRoute.ADD, CajaUi().hardwareScanRoute())

    @Test fun productEditorGetsTheCode() = assertEquals(HardwareScanRoute.EDITOR, CajaUi(draft = ProductDraft()).hardwareScanRoute())

    @Test fun dialogsAndPaymentIgnoreIt() {
        assertEquals(HardwareScanRoute.IGNORE, CajaUi(receiptOpen = true).hardwareScanRoute())
        assertEquals(HardwareScanRoute.IGNORE, CajaUi(editingLineId = "l1").hardwareScanRoute())
        assertEquals(HardwareScanRoute.IGNORE, CajaUi(showParked = true).hardwareScanRoute())
        // Menú de frecuentes abierto (pulsación larga): una lectura no agrega nada por detrás.
        assertEquals(HardwareScanRoute.IGNORE, CajaUi(productMenu = com.cuadra.caja.ui.guard.Fixtures.products[0]).hardwareScanRoute())
        assertEquals(HardwareScanRoute.IGNORE, CajaUi(notice = Notice.TicketLocked()).hardwareScanRoute())
        assertEquals(HardwareScanRoute.IGNORE, CajaUi(draft = ProductDraft(), notice = Notice.InvalidProduct).hardwareScanRoute())
    }

    // Pestaña «Productos» con el buscador (aunque tenga texto o el foco): el lector físico se comporta igual que en «Manual».
    @Test fun productsTabAndSearchTextStillAddToTheTicket() {
        assertEquals(HardwareScanRoute.ADD, CajaUi(tab = PosTab.PRODUCTS).hardwareScanRoute())
        assertEquals(HardwareScanRoute.ADD, CajaUi(tab = PosTab.PRODUCTS, query = "cerv").hardwareScanRoute())
        assertEquals(HardwareScanRoute.ADD, CajaUi(tab = PosTab.PRODUCTS, query = "7", reordering = false).hardwareScanRoute())
    }

    // Con el lector de la cámara abierto, quien recibe el físico es el propio lector (reclama el destino), no la caja.
    @Test fun editorStillWinsOverTheProductsTab() {
        assertEquals(HardwareScanRoute.EDITOR, CajaUi(tab = PosTab.PRODUCTS, draft = ProductDraft()).hardwareScanRoute())
    }
}

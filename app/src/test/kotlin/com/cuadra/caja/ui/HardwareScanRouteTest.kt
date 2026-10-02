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
}

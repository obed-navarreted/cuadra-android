package com.cuadra.caja.domain.printing

import com.cuadra.caja.data.scanner.DataWedge
import com.cuadra.caja.domain.SaleLineView
import com.cuadra.caja.domain.SalePaymentView
import com.cuadra.caja.domain.SaleView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SaleReceiptsTest {
    private val f = ReceiptFixtures

    private fun sale(status: String = "COMPLETED", payments: List<SalePaymentView>) = SaleView(
        "e5f6a7b8-1111-4222-8333-444455556666", status, null, 12_250, 0, 12_250, f.AT, "Kevin", null, null, if (status == "CANCELLED") "Ana" else null, null, if (status == "CANCELLED") "error" else null,
        listOf(SaleLineView("Cuajada fresca", null, 2000, 2750, 0, 5500), SaleLineView("Queso seco", "Bolsa", 750, 9000, 0, 6750)), payments,
    )

    private fun receipt(s: SaleView, settings: PrinterSettings = PrinterSettings(enabled = true), lang: String = "es") =
        SaleReceipts.receipt(s, "Quesería La Esperanza", f.ZONE, settings, lang, f.NIO)

    @Test fun aSaleBecomesAReceiptWithWeighedUnitsAndCashChange() {
        val text = receipt(sale(payments = listOf(SalePaymentView("CASH", null, 12_250, 15_000, 2_750, null, null)))).previewText()
        assertTrue(text.contains("#E5F6A7"))
        assertTrue(text.contains("Atendió: Kevin"))
        assertTrue(text.contains("  2 x C$ 27.50          C$ 55.00")) // cantidad entera: unidades
        assertTrue(text.contains("Queso seco (Bolsa)"))
        assertTrue(text.contains("0.75 lb x C$ 90.00")) // cantidad con decimales: pesada, en libras
        assertTrue(text.contains("Vuelto"))
        assertFalse(text.contains("ANULADA"))
    }

    @Test fun cancelledSaleReprintsWithTheBanner() {
        val text = receipt(sale("CANCELLED", listOf(SalePaymentView("CASH", null, 12_250, null, null, null, null)))).previewText()
        assertTrue(text.contains("A N U L A D A"))
        assertFalse(text.contains("error")) // el motivo de la anulación es interno: no sale en el papel
    }

    @Test fun creditShowsTheCustomerAndNeverTheReference() {
        val text = receipt(sale(payments = listOf(SalePaymentView("CARD", null, 5_000, null, null, "4111111111111111", null), SalePaymentView("CREDIT", null, 7_250, null, null, null, "Doña Rosa")))).previewText()
        assertTrue(text.contains("Fiado"))
        assertTrue(text.contains("Cliente: Doña Rosa"))
        assertTrue(text.contains("Tarjeta"))
        assertFalse(text.contains("4111"))
    }

    @Test fun headerAndFooterComeFromTheSettingsAndFooterDefaultsToThanks() {
        val s = PrinterSettings(enabled = true, address = "Barrio Central", phone = "8888-1234", taxId = "RUC 123", footer = "¡Vuelva pronto!")
        val text = receipt(sale(payments = listOf(SalePaymentView("CASH", null, 12_250, null, null, null, null))), s).previewText()
        assertTrue(text.contains("Barrio Central"))
        assertTrue(text.contains("Tel. 8888-1234"))
        assertTrue(text.contains("RUC 123"))
        assertTrue(text.contains("¡Vuelva pronto!"))
        assertFalse(text.contains("Gracias por su compra"))
        val plain = receipt(sale(payments = listOf(SalePaymentView("CASH", null, 12_250, null, null, null, null)))).previewText()
        assertTrue(plain.contains("Gracias por su compra"))
        assertTrue(plain.trimEnd().endsWith("Comprobante de venta"))
        assertTrue(receipt(sale(payments = emptyList()), lang = "en").previewText().trimEnd().endsWith("Sales receipt"))
    }

    @Test fun widthAndCharsetFollowTheSettings() {
        val s = PrinterSettings(enabled = true, widthMm = 80, charset = PrintCharset.ASCII)
        val r = receipt(sale(payments = listOf(SalePaymentView("CASH", null, 12_250, null, null, null, null))), s)
        assertEquals(48, r.columns)
        assertEquals(PrintCharset.ASCII, r.charset)
        assertTrue(r.previewText().contains("Atendio: Kevin"))
        assertEquals(32, PrinterSettings().columns)
    }

    @Test fun otherPaymentUsesItsOwnLabel() {
        val text = receipt(sale(payments = listOf(SalePaymentView("OTHER", "Cheque", 12_250, null, null, null, null)))).previewText()
        assertTrue(text.contains("Cheque"))
    }

    @Test fun previewAndTestReceiptsHaveTheAdvertisedContent() {
        val s = PrinterSettings(enabled = true, address = "Barrio Central", footer = "Vuelva pronto")
        val p = PrinterSamples.preview("Mi Negocio", s, "es", f.NIO, f.ZONE, f.AT).previewText()
        assertTrue(p.contains("Mi Negocio")); assertTrue(p.contains("Barrio Central")); assertTrue(p.contains("Vuelva pronto")); assertTrue(p.contains("Café molido"))
        val t = PrinterSamples.test("Mi Negocio", s, "es", f.NIO, f.ZONE, f.AT)
        val text = t.previewText()
        assertTrue(text.contains("PRUEBA DE IMPRESORA"))
        assertTrue(text.contains("áéíóú ÁÉÍÓÚ ñÑ üÜ ¿? ¡! €"))
        assertEquals(20, text.lines().count { it.startsWith("Producto de prueba") })
        assertTrue(EscPosDump.toText(t.toBytes(), t.charset).endsWith("[cut]"))
    }

    @Test fun printNoticeMapsEveryOutcome() {
        assertEquals(PrintNotice.PRINTED, PrintNotice.of(PrintOutcome.PRINTED))
        assertEquals(PrintNotice.NO_PRINTER, PrintNotice.of(PrintOutcome.NOT_CONNECTED))
        assertEquals(PrintNotice.FAILED, PrintNotice.of(PrintOutcome.FAILED))
        assertNull(PrintNotice.of(PrintOutcome.DISABLED))
    }

    @Test fun dataWedgeProfileEnablesTheScannerInTriggerModeForThisApp() {
        val steps = DataWedge.setConfigSteps("com.cuadra.caja")
        @Suppress("UNCHECKED_CAST")
        val appList = steps[0].config["APP_LIST"] as List<Map<String, Any>>
        assertEquals("com.cuadra.caja", appList.single()["PACKAGE_NAME"])
        assertEquals(listOf("*"), appList.single()["ACTIVITY_LIST"])
        @Suppress("UNCHECKED_CAST")
        val barcode = (steps[1].config["PLUGIN_CONFIG"] as Map<String, Any>)
        assertEquals("BARCODE", barcode["PLUGIN_NAME"])
        @Suppress("UNCHECKED_CAST")
        val params = barcode["PARAM_LIST"] as Map<String, Any>
        assertEquals("true", params["scanner_input_enabled"]) // el gatillo del equipo escanea
        assertEquals("0", params["aim_type"]) // modo de disparo por omisión: gatillo
        assertEquals("auto", params["scanner_selection"])
        assertEquals("true", steps[0].config["PROFILE_ENABLED"])
    }
}

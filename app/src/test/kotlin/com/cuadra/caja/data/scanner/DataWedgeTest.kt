package com.cuadra.caja.data.scanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DataWedgeTest {
    private val steps = DataWedge.setConfigSteps("com.cuadra.caja")

    @Suppress("UNCHECKED_CAST")
    private fun plugin(step: DataWedge.Step) = step.config["PLUGIN_CONFIG"] as Map<String, Any>

    @Suppress("UNCHECKED_CAST")
    private fun params(step: DataWedge.Step) = plugin(step)["PARAM_LIST"] as Map<String, Any>

    @Test fun everyStepTargetsTheCuentivaProfileBoundToThisApp() {
        assertEquals(4, steps.size)
        assertEquals(steps.size, steps.map { it.id }.toSet().size)
        for (s in steps) {
            assertEquals("Cuentiva", s.config["PROFILE_NAME"])
            assertEquals("true", s.config["PROFILE_ENABLED"])
            @Suppress("UNCHECKED_CAST") val app = (s.config["APP_LIST"] as List<Map<String, Any>>).single()
            assertEquals("com.cuadra.caja", app["PACKAGE_NAME"])
            assertEquals(listOf("*"), app["ACTIVITY_LIST"])
        }
    }

    @Test fun firstStepCreatesTheProfileIfMissingRestUpdate() {
        assertEquals("CREATE_IF_NOT_EXIST", steps[0].config["CONFIG_MODE"])
        assertFalse(steps[0].config.containsKey("PLUGIN_CONFIG"))
        assertTrue(steps.drop(1).all { it.config["CONFIG_MODE"] == "UPDATE" })
    }

    @Test fun barcodePluginEnablesTheSixDecoders() {
        val s = steps[1]
        assertEquals("BARCODE", plugin(s)["PLUGIN_NAME"])
        val p = params(s)
        for (d in listOf("decoder_ean13", "decoder_ean8", "decoder_upca", "decoder_upce0", "decoder_code128", "decoder_code39")) assertEquals(d, "true", p[d])
        assertEquals("true", p["scanner_input_enabled"])
    }

    @Test fun intentOutputOnByBroadcastToOurAction() {
        val p = params(steps[2])
        assertEquals("INTENT", plugin(steps[2])["PLUGIN_NAME"])
        assertEquals("true", p["intent_output_enabled"])
        assertEquals("com.cuadra.caja.SCAN", p["intent_action"])
        assertEquals("2", p["intent_delivery"]) // 2 = broadcast
    }

    @Test fun keystrokeOutputIsOff() {
        assertEquals("KEYSTROKE", plugin(steps[3])["PLUGIN_NAME"])
        assertEquals("false", params(steps[3])["keystroke_output_enabled"])
    }

    @Test fun readScanTakesDataStringAndCleansIt() {
        val r = DataWedge.readScan(" 7501055300075\r\n", "LABEL-TYPE-EAN13")
        assertEquals(DataWedgeRead("7501055300075", "LABEL-TYPE-EAN13"), r)
        assertNull(DataWedge.readScan(null, "LABEL-TYPE-EAN13"))
        assertNull(DataWedge.readScan("  ", null))
        assertNotNull(DataWedge.readScan("ABC-123", null))
    }

    @Test fun friendlyLabels() {
        assertEquals("EAN-13", DataWedge.friendlyLabel("LABEL-TYPE-EAN13"))
        assertEquals("UPC-A", DataWedge.friendlyLabel("LABEL-TYPE-UPCA"))
        assertEquals("Code 128", DataWedge.friendlyLabel("LABEL-TYPE-CODE128"))
        assertEquals("CODABAR", DataWedge.friendlyLabel("LABEL-TYPE-CODABAR"))
        assertNull(DataWedge.friendlyLabel(null)); assertNull(DataWedge.friendlyLabel("LABEL-TYPE-"))
    }

    @Test fun resultParsingAndSummary() {
        val ok = DataWedge.parseResult("com.symbol.datawedge.api.SET_CONFIG", "SUCCESS", "cuentiva-1-profile", null)!!
        assertTrue(ok.ok)
        val bad = DataWedge.parseResult("com.symbol.datawedge.api.SET_CONFIG", "FAILURE", "cuentiva-2-barcode", "PARAM_INPUT_FAIL")!!
        assertFalse(bad.ok); assertEquals("PARAM_INPUT_FAIL", bad.detail)
        assertNull(DataWedge.parseResult("com.symbol.datawedge.api.GET_VERSION_INFO", "SUCCESS", null, null))
        assertNull(DataWedge.parseResult(null, null, null, null))
        // crear un perfil que ya existe no es falla
        assertTrue(DataWedge.parseResult("com.symbol.datawedge.api.SET_CONFIG", "FAILURE", "x", "RESULT_CODE=PROFILE_ALREADY_EXISTS")!!.ok)

        assertEquals(SetupOutcome.Waiting, DataWedge.summarize(listOf(ok), 4))
        assertEquals(SetupOutcome.Ok, DataWedge.summarize(List(4) { ok }, 4))
        assertEquals(SetupOutcome.Failed("PARAM_INPUT_FAIL"), DataWedge.summarize(listOf(ok, bad), 4))
    }

    @Test fun scanRecordFillsFormatAndChecksum() {
        val r = scanRecord("4006381333931", ScanSource.WEDGE, null, 1L)
        assertEquals("EAN-13", r.format); assertEquals(true, r.checksumValid)
        val bad = scanRecord("4006381333932", ScanSource.DATAWEDGE, "EAN-13", 1L)
        assertEquals(false, bad.checksumValid)
        val text = scanRecord("LOTE-77", ScanSource.CAMERA, "Code 128", 1L)
        assertNull(text.checksumValid); assertEquals("Code 128", text.format)
        assertNull(scanRecord("LOTE-77", ScanSource.WEDGE, null, 1L).format)
    }
}

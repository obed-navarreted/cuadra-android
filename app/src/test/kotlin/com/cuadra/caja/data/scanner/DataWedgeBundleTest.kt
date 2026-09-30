package com.cuadra.caja.data.scanner

import android.os.Bundle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Los pasos de `DataWedge.setConfigSteps` se vuelven `Bundle` como DataWedge los espera (Bundle anidado, arreglo de Bundle, arreglo de texto). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class DataWedgeBundleTest {
    @Test fun intentStepBecomesNestedBundles() {
        val step = DataWedge.setConfigSteps("com.cuadra.caja")[2]
        val b = DataWedgeClient.toBundle(step.config)
        assertEquals("Cuentiva", b.getString("PROFILE_NAME"))
        @Suppress("DEPRECATION") val apps = b.getParcelableArray("APP_LIST")
        assertNotNull(apps); assertEquals(1, apps!!.size)
        val app = apps[0] as Bundle
        assertEquals("com.cuadra.caja", app.getString("PACKAGE_NAME"))
        assertEquals("*", app.getStringArray("ACTIVITY_LIST")!!.single())
        val plugin = b.getBundle("PLUGIN_CONFIG")!!
        assertEquals("INTENT", plugin.getString("PLUGIN_NAME"))
        val params = plugin.getBundle("PARAM_LIST")!!
        assertEquals("com.cuadra.caja.SCAN", params.getString("intent_action"))
        assertEquals("2", params.getString("intent_delivery"))
    }
}

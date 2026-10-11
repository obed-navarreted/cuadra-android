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
        val steps = DataWedge.setConfigSteps("com.cuadra.caja")
        // La app se liga al perfil solo en el primer paso (un arreglo de Bundle con la lista de actividades).
        val first = DataWedgeClient.toBundle(steps[0].config)
        @Suppress("DEPRECATION") val apps = first.getParcelableArray("APP_LIST")
        assertNotNull(apps); assertEquals(1, apps!!.size)
        val app = apps[0] as Bundle
        assertEquals("com.cuadra.caja", app.getString("PACKAGE_NAME"))
        assertEquals("*", app.getStringArray("ACTIVITY_LIST")!!.single())
        val b = DataWedgeClient.toBundle(steps[2].config)
        assertEquals("Cuentiva", b.getString("PROFILE_NAME"))
        assertEquals(false, b.containsKey("APP_LIST"))
        val plugin = b.getBundle("PLUGIN_CONFIG")!!
        assertEquals("INTENT", plugin.getString("PLUGIN_NAME"))
        val params = plugin.getBundle("PARAM_LIST")!!
        assertEquals("com.cuadra.caja.SCAN", params.getString("intent_action"))
        assertEquals("2", params.getString("intent_delivery"))
    }
}

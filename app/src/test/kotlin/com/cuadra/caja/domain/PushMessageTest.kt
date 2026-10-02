package com.cuadra.caja.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PushMessageTest {
    @Test fun parsesSyncAndNotifyMessagesAndIgnoresTheRest() {
        assertEquals(PushMessage.Sync("b1"), PushMessage.parse(mapOf("type" to "SYNC", "businessId" to "b1")))
        assertEquals(PushMessage.Notify("b1", "n1"), PushMessage.parse(mapOf("type" to "NOTIFY", "businessId" to "b1", "notificationId" to "n1")))
        assertNull(PushMessage.parse(mapOf("type" to "SYNC")))
        assertNull(PushMessage.parse(mapOf("type" to "OTHER", "businessId" to "b1")))
        assertNull(PushMessage.parse(emptyMap()))
    }

    @Test fun onlyTheActiveBusinessSyncsNowInForegroundAndExpeditedInBackground() {
        val m = PushMessage.Sync("b1")
        assertEquals(PushAction.SYNC_NOW, PushPolicy.action(m, "b1", foreground = true))
        assertEquals(PushAction.SYNC_EXPEDITED, PushPolicy.action(m, "b1", foreground = false))
        // Otro negocio (su base solo guarda la cola sin enviar) o sin sesión: nada.
        assertEquals(PushAction.IGNORE, PushPolicy.action(m, "b2", foreground = true))
        assertEquals(PushAction.IGNORE, PushPolicy.action(m, null, foreground = false))
    }

    @Test fun pollsEveryThirtySecondsWithoutPushAndRarelyWithIt() {
        assertEquals(30_000L, PushPolicy.pollEvery(pushActive = false))
        assertTrue(PushPolicy.pollEvery(pushActive = true) > 60_000L)
    }

    @Test fun notificationPermissionIsAskedOnceNeverOnTheFirstLaunch() {
        assertFalse(PushPolicy.askNotificationPermission(33, granted = false, alreadyAsked = false, launches = 1, hasMember = true))
        assertTrue(PushPolicy.askNotificationPermission(33, granted = false, alreadyAsked = false, launches = 2, hasMember = true))
        assertFalse(PushPolicy.askNotificationPermission(33, granted = false, alreadyAsked = true, launches = 5, hasMember = true))
        assertFalse(PushPolicy.askNotificationPermission(33, granted = true, alreadyAsked = false, launches = 5, hasMember = true))
        assertFalse(PushPolicy.askNotificationPermission(32, granted = false, alreadyAsked = false, launches = 5, hasMember = true))
        assertFalse(PushPolicy.askNotificationPermission(34, granted = false, alreadyAsked = false, launches = 5, hasMember = false))
    }
}

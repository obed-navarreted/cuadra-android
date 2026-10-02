package com.cuadra.caja.domain

import com.cuadra.caja.domain.PinElevation.AfterLocal
import com.cuadra.caja.domain.PinElevation.Outcome
import com.cuadra.caja.domain.PinElevation.ServerAnswer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Elevación por PIN verificado (ADR 0012, 2026-10-01): qué pasa tras el PIN local, con la respuesta del servidor y al refrescar el teléfono. */
class PinElevationTest {
    private val now = 1_000_000L
    private val later = now + 60_000L

    @Test fun onAPhoneLinkedByACashierAnAdminMustBeVerifiedButACashierNever() {
        assertEquals(AfterLocal.Verify, PinElevation.afterLocalUnlock("ADMIN", "CASHIER", null, now))
        assertEquals(AfterLocal.Verify, PinElevation.afterLocalUnlock("OWNER", "CASHIER", null, now))
        assertEquals(AfterLocal.Ready("CASHIER"), PinElevation.afterLocalUnlock("CASHIER", "CASHIER", null, now))
        // Vender y cobrar como cajero nunca necesita el servidor.
        assertFalse(PinElevation.needsVerification("CASHIER", null, null, now))
    }

    @Test fun aValidGrantOrAPhoneWithEnoughRoleSkipsTheServer() {
        assertEquals(AfterLocal.Ready("ADMIN"), PinElevation.afterLocalUnlock("ADMIN", "CASHIER", later, now))
        assertEquals(AfterLocal.Ready("ADMIN"), PinElevation.afterLocalUnlock("ADMIN", "OWNER", null, now))
        assertEquals(AfterLocal.Ready("ADMIN"), PinElevation.afterLocalUnlock("ADMIN", "ADMIN", null, now))
        // El dueño en un teléfono de admin, sí.
        assertEquals(AfterLocal.Verify, PinElevation.afterLocalUnlock("OWNER", "ADMIN", null, now))
        // Un permiso vencido ya no vale.
        assertEquals(AfterLocal.Verify, PinElevation.afterLocalUnlock("ADMIN", "CASHIER", now, now))
        // Sin saber el rol base (teléfono de antes de esta versión), se pregunta.
        assertEquals(AfterLocal.Verify, PinElevation.afterLocalUnlock("ADMIN", null, null, now))
    }

    @Test fun theServerAnswerDecidesTheSession() {
        assertEquals(Outcome.Elevated("ADMIN", later, "CASHIER"), PinElevation.afterServer("ADMIN", null, ServerAnswer.Verified(later, "CASHIER")))
        // Sin conexión entra igual, para vender (rol base), con el aviso de conectarse.
        assertEquals(Outcome.Capped("CASHIER", "ADMIN", offline = true), PinElevation.afterServer("ADMIN", "CASHIER", ServerAnswer.Offline))
        assertEquals(Outcome.Capped("ADMIN", "OWNER", offline = true), PinElevation.afterServer("OWNER", "ADMIN", ServerAnswer.Offline))
        assertEquals(Outcome.Capped("CASHIER", "OWNER", offline = false), PinElevation.afterServer("OWNER", null, ServerAnswer.Failed))
        assertEquals(Outcome.Wrong, PinElevation.afterServer("ADMIN", "CASHIER", ServerAnswer.WrongPin))
        assertEquals(Outcome.Locked(5_000), PinElevation.afterServer("ADMIN", "CASHIER", ServerAnswer.Locked(5_000)))
        assertEquals(Outcome.NotActive, PinElevation.afterServer("ADMIN", "CASHIER", ServerAnswer.NotActive))
    }

    @Test fun refreshingThePhoneLowersSomeoneWhoLostTheGrantAndRaisesSomeoneWhoHasIt() {
        assertEquals(PinElevation.Acting("CASHIER", "ADMIN"), PinElevation.reconcile("ADMIN", "CASHIER", null, now))
        assertEquals(PinElevation.Acting("ADMIN", null), PinElevation.reconcile("ADMIN", "CASHIER", later, now))
        assertEquals(PinElevation.Acting("CASHIER", null), PinElevation.reconcile("CASHIER", "CASHIER", null, now))
        assertEquals(PinElevation.Acting("OWNER", null), PinElevation.reconcile("OWNER", "OWNER", null, now))
    }

    @Test fun cappedRoleIsNeverAboveThePhone() {
        assertEquals("CASHIER", PinElevation.cappedRole("OWNER", "CASHIER"))
        assertEquals("ADMIN", PinElevation.cappedRole("OWNER", "ADMIN"))
        assertEquals("CASHIER", PinElevation.cappedRole("ADMIN", null))
        assertEquals("CASHIER", PinElevation.cappedRole("CASHIER", "OWNER"))
        assertTrue(PinElevation.rank("OWNER") > PinElevation.rank("ADMIN"))
    }
}

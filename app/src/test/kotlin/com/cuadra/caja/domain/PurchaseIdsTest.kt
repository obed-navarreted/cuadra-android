package com.cuadra.caja.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class PurchaseIdsTest {
    // Mismos valores que fija InventoryTest.derivedIdsMatchThePhoneContract en el servidor.
    private val p = "11111111-1111-1111-1111-111111111111"
    private val l = "22222222-2222-2222-2222-222222222222"

    @Test fun movementIdMatchesTheServer() = assertEquals("3e115f58-236c-3ab1-8eec-c14e0ee7a09c", PurchaseIds.movement(p, l))
    @Test fun reversalIdMatchesTheServer() = assertEquals("89a568a0-0968-31d0-baea-11585fbb0236", PurchaseIds.reversal(p, l))
    @Test fun firstPaymentIdMatchesTheServer() = assertEquals("f2538269-ca64-3508-ad34-b1c225eb5b42", PurchaseIds.firstPayment(p))
}

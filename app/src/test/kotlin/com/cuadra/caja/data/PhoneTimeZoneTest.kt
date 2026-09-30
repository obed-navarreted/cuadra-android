package com.cuadra.caja.data

import com.cuadra.caja.data.repo.phoneTimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhoneTimeZoneTest {
    @Test fun aRegionIdIsSentWhenCreatingTheBusiness() {
        assertEquals("America/Managua", phoneTimeZone("America/Managua"))
        assertEquals("UTC", phoneTimeZone("UTC"))
    }

    @Test fun anIdTheServerCouldNotReadIsNotSent() {
        assertNull(phoneTimeZone("GMT+05:30"))
        assertNull(phoneTimeZone("Mars/Olympus"))
        assertNull(phoneTimeZone(""))
    }
}

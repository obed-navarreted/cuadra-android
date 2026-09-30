package com.cuadra.caja.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceProfileTest {
    @Test fun zebraAndSymbolManufacturersAreZebra() {
        assertTrue(DeviceProfile.isZebra("Zebra Technologies", "TC77", false))
        assertTrue(DeviceProfile.isZebra("Symbol Technologies", "MC9090", false))
        assertTrue(DeviceProfile.isZebra("Zebra Technologies", "cualquier modelo nuevo", false))
    }

    @Test fun caseAndSpacesDoNotMatter() {
        assertTrue(DeviceProfile.isZebra("ZEBRA TECHNOLOGIES", "TC77", false))
        assertTrue(DeviceProfile.isZebra("zebra", "x", false))
        assertTrue(DeviceProfile.isZebra("  Symbol technologies ", null, false))
        assertTrue(DeviceProfile.isZebra("SYMBOL", "", false))
    }

    @Test fun theModelAloneIsEnoughOnlyWhenTheManufacturerIsMissing() {
        assertTrue(DeviceProfile.isZebra(null, "TC77", false))
        assertTrue(DeviceProfile.isZebra("", "tc52", false))
        assertTrue(DeviceProfile.isZebra(null, "MC33", false))
        assertTrue(DeviceProfile.isZebra(" ", "EC30", false))
        assertFalse(DeviceProfile.isZebra("samsung", "TC77", false))
    }

    @Test fun dataWedgeInstalledMeansZebra() {
        assertTrue(DeviceProfile.isZebra("samsung", "SM-G991B", true))
        assertTrue(DeviceProfile.isZebra(null, null, true))
    }

    @Test fun ordinaryPhonesAreNotZebra() {
        assertFalse(DeviceProfile.isZebra("samsung", "SM-A546E", false))
        assertFalse(DeviceProfile.isZebra("Samsung", "SM-G991B", false))
        assertFalse(DeviceProfile.isZebra("Google", "Pixel 8", false))
        assertFalse(DeviceProfile.isZebra("Xiaomi", "23129RN51X", false))
        assertFalse(DeviceProfile.isZebra("Google", "sdk_gphone64_x86_64", false))
        assertFalse(DeviceProfile.isZebra(null, null, false))
        assertFalse(DeviceProfile.isZebra("", "Pixel 8", false))
        assertFalse(DeviceProfile.isZebra("Motorola", "moto g(60)", false))
    }
}

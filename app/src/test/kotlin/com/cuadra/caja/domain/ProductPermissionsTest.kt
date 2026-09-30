package com.cuadra.caja.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductPermissionsTest {
    @Test fun cashierEditsButCannotDelete() {
        assertTrue(ProductPermissions.canEdit("CASHIER"))
        assertFalse(ProductPermissions.canDelete("CASHIER"))
    }

    @Test fun adminEditsAndDeletes() {
        assertTrue(ProductPermissions.canEdit("ADMIN"))
        assertTrue(ProductPermissions.canDelete("ADMIN"))
    }

    @Test fun ownerEditsAndDeletes() {
        assertTrue(ProductPermissions.canEdit("OWNER"))
        assertTrue(ProductPermissions.canDelete("OWNER"))
    }

    @Test fun unknownOrMissingRoleCanDoNothing() {
        assertFalse(ProductPermissions.canEdit(null))
        assertFalse(ProductPermissions.canDelete(null))
        assertFalse(ProductPermissions.canDelete("nope"))
    }
}

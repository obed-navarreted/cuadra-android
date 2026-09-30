package com.cuadra.caja.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TeamRulesTest {
    private val all = MemberPermissions(edit = true, disable = true, resetPin = true, changeRole = true)
    private val selfOnly = MemberPermissions(edit = true, disable = false, resetPin = true, changeRole = false)

    // ---- la matriz 3 x 3 de quien mira contra a quién mira (otra persona) ----
    @Test fun ownerManagesEveryoneElse() {
        assertEquals(all, TeamRules.canManage("OWNER", false, "ADMIN"))
        assertEquals(all, TeamRules.canManage("OWNER", false, "CASHIER"))
    }

    @Test fun adminManagesCashiersAndOtherAdminsButNeverTheOwner() {
        assertEquals(all, TeamRules.canManage("ADMIN", false, "CASHIER"))
        assertEquals(all, TeamRules.canManage("ADMIN", false, "ADMIN"))
        assertEquals(MemberPermissions.NONE, TeamRules.canManage("ADMIN", false, "OWNER"))
    }

    @Test fun cashierManagesNobodyElse() {
        for (target in listOf("OWNER", "ADMIN", "CASHIER")) assertEquals(MemberPermissions.NONE, TeamRules.canManage("CASHIER", false, target))
    }

    // ---- uno mismo: nombre, color y PIN; nunca rol ni estado ----
    @Test fun everyoneCanEditThemselvesButNotRoleOrStatus() {
        for (role in listOf("OWNER", "ADMIN", "CASHIER")) assertEquals(role, selfOnly, TeamRules.canManage(role, true, role))
    }

    @Test fun ownerCannotBeModifiedByAnAdminNotEvenThePin() {
        val p = TeamRules.canManage("ADMIN", false, "OWNER")
        assertFalse(p.edit || p.resetPin || p.disable || p.changeRole)
        assertFalse(p.any)
    }

    @Test fun unknownRolesGetNothing() {
        assertEquals(MemberPermissions.NONE, TeamRules.canManage(null, false, "CASHIER"))
        assertEquals(MemberPermissions.NONE, TeamRules.canManage("SUPERUSER", true, "CASHIER"))
        assertEquals(MemberPermissions.NONE, TeamRules.canManage("", false, null))
    }

    @Test fun assignableRolesNeverIncludeOwner() {
        assertEquals(listOf("CASHIER", "ADMIN"), TeamRules.assignableRoles("OWNER"))
        assertEquals(listOf("CASHIER", "ADMIN"), TeamRules.assignableRoles("ADMIN"))
        assertTrue(TeamRules.assignableRoles("CASHIER").isEmpty())
        assertTrue(TeamRules.assignableRoles(null).isEmpty())
    }

    @Test fun onlyOwnerAndAdminSeeTheTeam() {
        assertTrue(TeamRules.canSeeTeam("OWNER")); assertTrue(TeamRules.canSeeTeam("ADMIN"))
        assertFalse(TeamRules.canSeeTeam("CASHIER")); assertFalse(TeamRules.canSeeTeam(null))
    }

    @Test fun namesAreTrimmedAndCapped() {
        assertEquals("Ana", TeamRules.cleanName("  Ana "))
        assertEquals(80, TeamRules.cleanName("x".repeat(200)).length)
    }

    // ---- PIN (exactamente 5 números, ADR 0012) ----
    @Test fun pinNeedsExactlyFiveDigits() {
        assertTrue(PinRules.isValid("12345")); assertTrue(PinRules.isValid("00000"))
        assertFalse(PinRules.isValid("1234")); assertFalse(PinRules.isValid("123456")); assertFalse(PinRules.isValid("123")); assertFalse(PinRules.isValid("1234567"))
        assertFalse(PinRules.isValid("12a45")); assertFalse(PinRules.isValid("")); assertFalse(PinRules.isValid("12 45")); assertFalse(PinRules.isValid("١٢٣٤٥"))
        assertEquals(5, PinRules.LENGTH)
    }

    @Test fun bothEntriesMustMatch() {
        assertNull(PinRules.check("43210", "43210"))
        assertEquals(PinProblem.MISMATCH, PinRules.check("43210", "43211"))
        assertEquals(PinProblem.MISMATCH, PinRules.check("43210", ""))
        assertEquals(PinProblem.TOO_SHORT, PinRules.check("4321", "4321"))
        assertEquals(PinProblem.TOO_SHORT, PinRules.check("432101", "432101"))
        assertTrue(PinRules.canSave("12345", "12345"))
        assertFalse(PinRules.canSave("1234", "12345"))
        assertFalse(PinRules.canSave("123456", "123456"))
    }

    @Test fun fieldOnlyTakesDigitsUpToFive() {
        assertEquals("12345", PinRules.sanitize("12a34-5678"))
        assertEquals("", PinRules.sanitize("abc"))
    }
}

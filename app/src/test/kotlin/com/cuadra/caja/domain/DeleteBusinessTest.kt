package com.cuadra.caja.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeleteBusinessTest {
    private val name = "Quesería  La Esperanza"

    @Test fun nameMatchIgnoresCaseAndSpacing() {
        assertTrue(DeleteBusinessRules.nameMatches("Quesería  La Esperanza", name))
        assertTrue(DeleteBusinessRules.nameMatches("  quesería la esperanza ", name))
        assertTrue(DeleteBusinessRules.nameMatches("QUESERÍA LA   ESPERANZA", name))
        assertFalse(DeleteBusinessRules.nameMatches("Queseria La Esperanza", name))
        assertFalse(DeleteBusinessRules.nameMatches("Quesería La", name))
        assertFalse(DeleteBusinessRules.nameMatches("", name))
    }

    @Test fun anEmptyNameNeverMatches() {
        assertFalse(DeleteBusinessRules.nameMatches("", ""))
        assertFalse(DeleteBusinessRules.nameMatches("  ", "  "))
    }

    @Test fun theButtonNeedsTheNameTheCheckboxAndNoCallInProgress() {
        assertTrue(DeleteBusinessRules.canConfirm("quesería la esperanza", name, understood = true))
        assertFalse(DeleteBusinessRules.canConfirm("quesería la esperanza", name, understood = false))
        assertFalse(DeleteBusinessRules.canConfirm("otro nombre", name, understood = true))
        assertFalse(DeleteBusinessRules.canConfirm("quesería la esperanza", name, understood = true, busy = true))
        assertFalse(DeleteBusinessRules.canConfirm("", name, understood = false))
    }

    @Test fun onlyTheOwnerSeesTheDangerZone() {
        assertTrue(DeleteBusinessRules.canDelete("OWNER"))
        assertFalse(DeleteBusinessRules.canDelete("ADMIN")); assertFalse(DeleteBusinessRules.canDelete("CASHIER")); assertFalse(DeleteBusinessRules.canDelete(null))
    }

    @Test fun ownerAndAdminEditSettingsButOnlyTheOwnerDeletes() {
        fun ui(role: String?) = com.cuadra.caja.ui.SettingsUi(role = role)
        assertTrue(ui("OWNER").canEdit); assertTrue(ui("ADMIN").canEdit)
        assertFalse(ui("CASHIER").canEdit); assertFalse(ui(null).canEdit)
        assertTrue(ui("OWNER").isOwner); assertFalse(ui("ADMIN").isOwner)
    }
}

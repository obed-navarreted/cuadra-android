package com.cuadra.caja.domain

import com.cuadra.caja.data.local.MemberEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PinHashMergeTest {
    private fun member(id: String, pinSet: Boolean, hash: String?) = MemberEntity(id, "Ana", "OWNER", "ACTIVE", true, pinSet, false, null, hash)

    @Test fun `un dato sin hash no borra el hash de quien tiene PIN`() {
        // Caso real: el dueño crea su PIN; la sincronización llega con su sesión de Google (sin hash) y antes lo borraba.
        val merged = mergePinHashes(listOf(member("a", pinSet = true, hash = null)), mapOf("a" to "\$2a\$hash"))
        assertEquals("\$2a\$hash", merged.single().pinHash)
    }

    @Test fun `un hash nuevo reemplaza al viejo`() {
        val merged = mergePinHashes(listOf(member("a", pinSet = true, hash = "nuevo")), mapOf("a" to "viejo"))
        assertEquals("nuevo", merged.single().pinHash)
    }

    @Test fun `si ya no tiene PIN el hash se borra`() {
        val merged = mergePinHashes(listOf(member("a", pinSet = false, hash = null)), mapOf("a" to "viejo"))
        assertNull(merged.single().pinHash)
    }

    @Test fun `una persona nueva sin hash queda sin hash`() {
        val merged = mergePinHashes(listOf(member("b", pinSet = true, hash = null)), emptyMap())
        assertNull(merged.single().pinHash)
    }
}

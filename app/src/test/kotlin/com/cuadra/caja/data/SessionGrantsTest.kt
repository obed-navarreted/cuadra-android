package com.cuadra.caja.data

import com.cuadra.caja.data.session.decodeGrants
import com.cuadra.caja.data.session.encodeGrants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Los permisos de PIN verificado viajan con la sesión como texto: ida y vuelta, y lo ilegible se ignora. */
class SessionGrantsTest {
    @Test fun grantsRoundTripAndBadPartsAreIgnored() {
        val grants = mapOf("8c1e-ana" to 1_790_000_000_000L, "b2-owner" to 5L)
        assertEquals(grants, decodeGrants(encodeGrants(grants)))
        assertNull(encodeGrants(emptyMap()))
        assertEquals(emptyMap<String, Long>(), decodeGrants(null))
        assertEquals(mapOf("a" to 1L), decodeGrants("a=1;roto;=5;b=x"))
    }
}

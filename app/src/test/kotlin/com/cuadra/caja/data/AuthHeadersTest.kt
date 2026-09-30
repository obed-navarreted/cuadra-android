package com.cuadra.caja.data

import com.cuadra.caja.data.remote.authHeaders
import com.cuadra.caja.data.session.Session
import org.junit.Test
import org.junit.Assert.assertEquals

class AuthHeadersTest {
    private fun session(user: String? = null, device: String? = null, member: String? = null) =
        Session(userToken = user, deviceToken = device, memberId = member)

    @Test fun `un telefono con persona elegida actua como esa persona`() {
        assertEquals(mapOf("Authorization" to "Device d", "X-Member-Id" to "m"), authHeaders(session(device = "d", member = "m", user = "u"), null))
    }

    @Test fun `sesion de Google sin persona usa Bearer`() {
        assertEquals(mapOf("Authorization" to "Bearer u"), authHeaders(session(user = "u"), null))
        // Aun con el teléfono vinculado, sin persona elegida manda la sesión de Google (comportamiento existente)…
        assertEquals(mapOf("Authorization" to "Bearer u"), authHeaders(session(user = "u", device = "d"), null))
    }

    @Test fun `una peticion con credenciales explicitas no se toca`() {
        // …salvo que la interfaz pida otra cosa a propósito: así el listado de miembros se pide como teléfono y trae el hash del PIN.
        assertEquals(emptyMap<String, String>(), authHeaders(session(user = "u", device = "d"), "Device d"))
    }

    @Test fun `solo telefono y sin nada`() {
        assertEquals(mapOf("Authorization" to "Device d"), authHeaders(session(device = "d"), null))
        assertEquals(emptyMap<String, String>(), authHeaders(session(), null))
    }
}

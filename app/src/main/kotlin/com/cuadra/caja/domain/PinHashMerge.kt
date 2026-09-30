package com.cuadra.caja.domain

import com.cuadra.caja.data.local.MemberEntity

/**
 * El hash del PIN solo lo entrega el servidor a un TELÉFONO (con su credencial de dispositivo). Cuando el mismo dato llega por otra vía
 * (la sesión de Google del dueño, justo tras crear el negocio), viene sin hash: guardarlo tal cual BORRARÍA el hash que ya teníamos y el
 * teléfono diría "esta persona aún no tiene PIN" aunque sí lo tenga. Regla: si la persona tiene PIN (`pinSet`) y el dato nuevo no trae hash,
 * se conserva el que había; si ya no tiene PIN, se borra.
 */
fun mergePinHashes(incoming: List<MemberEntity>, existingHashes: Map<String, String?>): List<MemberEntity> = incoming.map { m ->
    if (m.pinHash == null && m.pinSet) existingHashes[m.id]?.let { m.copy(pinHash = it) } ?: m else m
}

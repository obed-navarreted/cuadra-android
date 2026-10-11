package com.cuadra.caja.domain

import com.cuadra.caja.data.local.ProductEntity

/**
 * «Comprobar producto» (Más › Productos): se lee un código (lector del equipo, Bluetooth/USB o cámara) para saber si el producto existe. No vende nada.
 * Existe: se muestra su ficha (nombre, precio, código, existencias) con «Editar». No existe: se ofrece crearlo con el código ya puesto; si el dígito de control
 * de un EAN/UPC no cuadra, se avisa «El código parece incorrecto» pero igual se puede crear (hay catálogos con códigos internos con forma de EAN).
 * La búsqueda trata UPC-A (12 dígitos) y EAN-13 (con un 0 delante) como el mismo código (`Barcodes.forms`, lo hace `lookup`).
 */
sealed interface ProductCheck {
    data class Found(val code: String, val product: ProductEntity) : ProductCheck
    data class Missing(val code: String, val badChecksum: Boolean) : ProductCheck
}

object ProductChecker {
    /** `null` = no llegó nada útil (vacío). `lookup` recibe el código limpio y devuelve el producto que lo tenga (cualquier forma UPC/EAN del mismo código). */
    suspend fun check(raw: String, lookup: suspend (String) -> ProductEntity?): ProductCheck? = when (val r = ScanCode.resolve(raw, lookup)) {
        ScanResolution.Empty -> null
        is ScanResolution.Found -> ProductCheck.Found(r.code, r.item)
        is ScanResolution.NotFound -> ProductCheck.Missing(r.code, badChecksum = !r.checksumOk)
    }

    /** Con qué texto se filtra la lista para mostrar el producto encontrado: su código guardado (como lo escribieron) o, si no tiene, su nombre. */
    fun listQuery(p: ProductEntity): String = p.barcode?.takeIf { it.isNotBlank() } ?: p.name
}

package com.cuadra.caja.domain

import com.cuadra.caja.domain.printing.ReceiptLabels

/**
 * Textos de las promociones fuera de la pantalla (comprobante de WhatsApp, recibo impreso): «Promo 3 por C$ 100: -C$ 70». Las pantallas usan los recursos
 * (`promo_line`); el papel y el mensaje, las etiquetas del recibo en el idioma de la app (`ReceiptLabels`).
 */
object PromoText {
    /** «Promo 3 por C$ 100» (`priceText` ya formateado con la moneda del negocio). */
    fun label(quantity: Int, priceText: String, language: String?): String = ReceiptLabels.of(language).promo.format(quantity, priceText)

    /**
     * Las líneas del comprobante: cada producto a su precio de siempre y, debajo de la última línea que tocó cada promoción, su línea («Promo …:», monto en
     * negativo). Las promociones sin líneas conocidas (una venta del servidor) van al final. `lines`: (id, texto, monto sin promoción).
     */
    fun ticketLines(lines: List<Triple<String, String, Long>>, promos: List<Triple<List<String>, String, Long>>): List<Pair<String, Long>> {
        val out = ArrayList<Pair<String, Long>>()
        val placed = HashSet<Int>()
        lines.forEach { (id, text, gross) ->
            out += text to gross
            promos.forEachIndexed { i, (ids, label, discount) -> if (ids.lastOrNull() == id && placed.add(i)) out += "$label:" to -discount }
        }
        promos.forEachIndexed { i, (_, label, discount) -> if (placed.add(i)) out += "$label:" to -discount }
        return out
    }
}

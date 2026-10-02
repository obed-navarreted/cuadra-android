package com.cuadra.caja.domain.printing

import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId

/** Una línea del recibo. `text` ya está a la medida: las de dos columnas («izquierda … derecha») llegan rellenas hasta el ancho; las centradas van sin relleno (las centra la impresora). */
data class ReceiptLine(val text: String, val align: Align = Align.LEFT, val bold: Boolean = false, val size: TextSize = TextSize.NORMAL)

/** Textos fijos del recibo en cada idioma. El recibo sigue el idioma de la app; el dinero, el país del negocio. */
data class ReceiptLabels(
    val servedBy: String, val total: String, val subtotal: String, val discount: String, val received: String, val change: String, val cancelled: String,
    val thanks: String, val ticket: String, val phone: String, val customer: String,
    val cash: String, val transfer: String, val card: String, val credit: String, val other: String,
    val months: List<String>,
    /** Comprobante de devolución. */
    val returnTitle: String = "COMPROBANTE DE DEVOLUCION", val returnTotal: String = "DEVUELTO", val returnReason: String = "Motivo: %s",
    val creditNote: String = "Nota de credito (fiado)", val ofSale: String = "Venta %s", val returnTicket: String = "Comprobante de devolucion",
    /** Promoción por cantidad aplicada: «Promo 3 por C$ 100» (cantidad, precio del paquete ya formateado). */
    val promo: String = "Promo %d por %s",
) {
    fun method(m: String, otherLabel: String?): String = when (m) {
        "CASH" -> cash; "TRANSFER" -> transfer; "CARD" -> card; "CREDIT" -> credit
        else -> otherLabel?.takeIf { it.isNotBlank() } ?: other
    }

    companion object {
        val ES = ReceiptLabels(
            servedBy = "Atendió: %s", total = "TOTAL", subtotal = "Subtotal", discount = "Descuento", received = "Recibido", change = "Vuelto", cancelled = "ANULADA",
            thanks = "Gracias por su compra", ticket = "Comprobante de venta", phone = "Tel. %s", customer = "Cliente: %s",
            cash = "Efectivo", transfer = "Transferencia", card = "Tarjeta", credit = "Fiado", other = "Otro",
            months = listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic"),
            returnTitle = "DEVOLUCIÓN", returnTotal = "DEVUELTO", returnReason = "Motivo: %s", creditNote = "Nota de crédito (fiado)", ofSale = "Venta %s",
            returnTicket = "Comprobante de devolución", promo = "Promo %d por %s",
        )
        val EN = ReceiptLabels(
            servedBy = "Served by: %s", total = "TOTAL", subtotal = "Subtotal", discount = "Discount", received = "Received", change = "Change", cancelled = "VOID",
            thanks = "Thank you for your purchase", ticket = "Sales receipt", phone = "Tel. %s", customer = "Customer: %s",
            cash = "Cash", transfer = "Transfer", card = "Card", credit = "On credit", other = "Other",
            months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"),
            returnTitle = "RETURN", returnTotal = "REFUNDED", returnReason = "Reason: %s", creditNote = "Credit note (on credit)", ofSale = "Sale %s",
            returnTicket = "Return receipt", promo = "Promo %d for %s",
        )

        fun of(language: String?): ReceiptLabels = if (language?.lowercase()?.startsWith("en") == true) EN else ES
    }
}

data class ReceiptItem(
    val name: String, val variant: String?, val quantityMilli: Long, val unitPriceMinor: Long, val discountMinor: Long, val totalMinor: Long,
    /** Unidad de una cantidad pesada («lb»); nulo = unidades. */
    val unit: String? = null,
)

/** Una promoción aplicada en la venta: «Promo 3 por C$ 100: -C$ 70». */
data class ReceiptPromotion(val quantity: Int, val priceMinor: Long, val discountMinor: Long)

data class ReceiptPayment(val method: String, val otherLabel: String?, val amountMinor: Long, val tenderedMinor: Long?, val changeMinor: Long?, val customer: String?)

/** Todo lo que el recibo necesita, sin depender de Android: ya con el dinero formateado por quien llama (`money`). */
data class ReceiptData(
    val businessName: String, val address: String, val phone: String, val taxId: String,
    val saleId: String, val atMillis: Long, val zone: ZoneId, val cashier: String?,
    val items: List<ReceiptItem>, val subtotalMinor: Long, val discountMinor: Long, val totalMinor: Long, val payments: List<ReceiptPayment>,
    val cancelled: Boolean, val footer: String, val labels: ReceiptLabels, val money: (Long) -> String,
    /** Promociones por cantidad: con ellas cada línea va a su precio de siempre y debajo, «Promo 3 por C$ 100: -C$ 70». */
    val promotions: List<ReceiptPromotion> = emptyList(),
)

/** Lo que lleva el comprobante de una devolución: cada línea devuelta con su monto, el total y cómo se devolvió el dinero. */
data class ReturnReceiptData(
    val businessName: String, val address: String, val phone: String, val taxId: String,
    val saleId: String, val returnId: String, val atMillis: Long, val zone: ZoneId, val by: String?,
    val lines: List<Pair<ReceiptItem, Long>>, val totalMinor: Long, val refunds: List<Pair<String, Long>>, val reason: String,
    val footer: String, val labels: ReceiptLabels, val money: (Long) -> String,
)

/** Un recibo listo: las líneas y con qué columnas y juego de caracteres se imprime. */
data class Receipt(val lines: List<ReceiptLine>, val columns: Int, val charset: PrintCharset) {
    /** Los bytes ESC/POS de `copies` copias, cada una con su corte. */
    fun toBytes(copies: Int = 1): ByteArray {
        val p = EscPos(charset)
        repeat(copies.coerceIn(1, 5)) {
            p.init()
            for (l in lines) p.align(l.align).bold(l.bold).size(l.size).text(l.text).lf()
            p.align(Align.LEFT).bold(false).size(TextSize.NORMAL).feed(3).cut()
        }
        return p.toBytes()
    }

    /** El recibo como texto monoespaciado (lo mismo que sale en el papel, `columns` de ancho): sirve para la vista previa. */
    fun previewText(): String = lines.joinToString("\n") { l ->
        val shown = PrintText.printable(l.text, charset)
        val wide = if (l.size.widthFactor == 2) shown.map { "$it " }.joinToString("").trimEnd() else shown
        when (l.align) {
            Align.CENTER -> " ".repeat(((columns - wide.length) / 2).coerceAtLeast(0)) + wide
            Align.RIGHT -> wide.padStart(columns)
            Align.LEFT -> wide
        }.trimEnd()
    }
}

object ReceiptFormatter {
    const val COLUMNS_58 = 32
    const val COLUMNS_80 = 48

    /** Referencia corta de la venta: «#» y los primeros 6 caracteres del id, en mayúsculas. */
    fun reference(saleId: String): String = "#" + saleId.filter { it.isLetterOrDigit() }.take(6).uppercase()

    /** «29 sep 2026  14:32», en la zona horaria del NEGOCIO (nunca la del teléfono). */
    fun dateTime(atMillis: Long, zone: ZoneId, labels: ReceiptLabels): String {
        val t = Instant.ofEpochMilli(atMillis).atZone(zone)
        return String.format(java.util.Locale.ROOT, "%d %s %d  %02d:%02d", t.dayOfMonth, labels.months[t.monthValue - 1], t.year, t.hour, t.minute)
    }

    /** Cantidad sin ceros de más: 2 → «2», 750 milésimas → «0.75». */
    fun quantity(milli: Long): String = BigDecimal.valueOf(milli, 3).stripTrailingZeros().toPlainString()

    /** Parte un texto en líneas de `width` caracteres SIN partir una palabra (salvo que ella sola pase del ancho). */
    fun wrap(text: String, width: Int): List<String> {
        val w = width.coerceAtLeast(1)
        val words = text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return emptyList()
        val lines = mutableListOf<String>()
        var cur = StringBuilder()
        for (word0 in words) {
            var word = word0
            while (word.length > w) {
                if (cur.isNotEmpty()) { lines += cur.toString(); cur = StringBuilder() }
                lines += word.substring(0, w)
                word = word.substring(w)
            }
            if (cur.isEmpty()) cur.append(word)
            else if (cur.length + 1 + word.length <= w) cur.append(' ').append(word)
            else { lines += cur.toString(); cur = StringBuilder(word) }
        }
        if (cur.isNotEmpty()) lines += cur.toString()
        return lines
    }

    /** «izquierda ........ derecha» a `width`; si no caben juntas, la derecha baja a su propia línea (alineada a la derecha). */
    fun leftRight(left: String, right: String, width: Int): List<String> {
        if (left.length + 1 + right.length <= width) return listOf(left + " ".repeat(width - left.length - right.length) + right)
        // La sangría («  2 x …») se conserva en cada línea aunque el texto baje.
        val indent = left.length - left.trimStart().length
        val pad = " ".repeat(indent.coerceAtMost(width / 2))
        val wrapped = wrap(left.trim(), width - pad.length).map { pad + it }.ifEmpty { listOf("") }
        val last = wrapped.last()
        return if (last.length + 1 + right.length <= width) wrapped.dropLast(1) + (last + " ".repeat(width - last.length - right.length) + right)
        else wrapped + right.padStart(width)
    }

    fun build(d: ReceiptData, columns: Int = COLUMNS_58, charset: PrintCharset = PrintCharset.PC858): Receipt {
        val w = columns
        val out = mutableListOf<ReceiptLine>()
        fun line(text: String, align: Align = Align.LEFT, bold: Boolean = false, size: TextSize = TextSize.NORMAL) { out += ReceiptLine(text, align, bold, size) }
        fun lines(list: List<String>, align: Align = Align.LEFT, bold: Boolean = false, size: TextSize = TextSize.NORMAL) = list.forEach { line(it, align, bold, size) }
        fun rule() = line("-".repeat(w))
        fun money(minor: Long): String = d.money(minor)
        val l = d.labels

        // Encabezado: nombre grande y centrado; dirección, teléfono y RUC solo si se escribieron.
        lines(wrap(d.businessName, w).ifEmpty { listOf("") }, Align.CENTER, bold = true, size = TextSize.TALL)
        if (d.address.isNotBlank()) lines(wrap(d.address, w), Align.CENTER)
        if (d.phone.isNotBlank()) lines(wrap(l.phone.format(d.phone.trim()), w), Align.CENTER)
        if (d.taxId.isNotBlank()) lines(wrap(d.taxId, w), Align.CENTER)
        rule()
        lines(leftRight(dateTime(d.atMillis, d.zone, l), reference(d.saleId), w))
        d.cashier?.takeIf { it.isNotBlank() }?.let { lines(wrap(l.servedBy.format(it.trim()), w)) }
        if (d.cancelled) {
            rule()
            line(l.cancelled, Align.CENTER, bold = true, size = TextSize.BIG)
        }
        rule()

        // Renglones: nombre (en las líneas que haga falta), luego «cantidad x precio» a la izquierda y el total de la línea a la derecha.
        for (i in d.items) {
            lines(wrap(i.name + (i.variant?.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: ""), w))
            val qty = quantity(i.quantityMilli) + (i.unit?.let { " $it" } ?: "")
            lines(leftRight("  $qty x ${money(i.unitPriceMinor)}", money(i.totalMinor), w))
            if (i.discountMinor > 0) lines(leftRight("  ${l.discount}", "-" + money(i.discountMinor), w))
        }
        for (p in d.promotions) lines(leftRight(l.promo.format(p.quantity, money(p.priceMinor)) + ":", "-" + money(p.discountMinor), w))
        rule()

        if (d.discountMinor > 0) {
            lines(leftRight(l.subtotal, money(d.subtotalMinor), w))
            lines(leftRight(l.discount, "-" + money(d.discountMinor), w))
        }
        // TOTAL grande: doble ancho y alto si cabe en la mitad de las columnas; si no, doble alto; si ni así, el monto baja a su línea.
        val total = money(d.totalMinor)
        when {
            l.total.length + 1 + total.length <= w / 2 -> line(leftRight(l.total, total, w / 2).single(), bold = true, size = TextSize.BIG)
            l.total.length + 1 + total.length <= w -> line(leftRight(l.total, total, w).single(), bold = true, size = TextSize.TALL)
            else -> { line(l.total, bold = true, size = TextSize.TALL); lines(wrap(total, w).map { it.padStart(w) }, bold = true, size = TextSize.TALL) }
        }

        // Pagos por método. El efectivo dice cuánto se recibió y el vuelto; el fiado, a quién; NUNCA datos de tarjeta ni referencias.
        for (p in d.payments) {
            lines(leftRight(l.method(p.method, p.otherLabel), money(p.amountMinor), w))
            if (p.method == "CASH" && p.tenderedMinor != null && p.tenderedMinor > p.amountMinor) {
                lines(leftRight("  ${l.received}", money(p.tenderedMinor), w))
                lines(leftRight("  ${l.change}", money(p.changeMinor ?: (p.tenderedMinor - p.amountMinor)), w))
            }
            if (p.method == "CREDIT" && !p.customer.isNullOrBlank()) lines(wrap("  " + l.customer.format(p.customer.trim()), w))
        }
        rule()
        lines(wrap(d.footer.ifBlank { l.thanks }, w), Align.CENTER)
        line(l.ticket, Align.CENTER)
        return Receipt(out, w, charset)
    }

    /** Comprobante de devolución: mismo encabezado que el recibo, «DEVOLUCIÓN» grande, lo devuelto (en negativo), el total, cómo salió el dinero y el motivo. */
    fun buildReturn(d: ReturnReceiptData, columns: Int = COLUMNS_58, charset: PrintCharset = PrintCharset.PC858): Receipt {
        val w = columns
        val out = mutableListOf<ReceiptLine>()
        fun line(text: String, align: Align = Align.LEFT, bold: Boolean = false, size: TextSize = TextSize.NORMAL) { out += ReceiptLine(text, align, bold, size) }
        fun lines(list: List<String>, align: Align = Align.LEFT, bold: Boolean = false, size: TextSize = TextSize.NORMAL) = list.forEach { line(it, align, bold, size) }
        fun rule() = line("-".repeat(w))
        val l = d.labels
        lines(wrap(d.businessName, w).ifEmpty { listOf("") }, Align.CENTER, bold = true, size = TextSize.TALL)
        if (d.address.isNotBlank()) lines(wrap(d.address, w), Align.CENTER)
        if (d.phone.isNotBlank()) lines(wrap(l.phone.format(d.phone.trim()), w), Align.CENTER)
        if (d.taxId.isNotBlank()) lines(wrap(d.taxId, w), Align.CENTER)
        rule()
        line(l.returnTitle, Align.CENTER, bold = true, size = if (l.returnTitle.length * 2 <= w) TextSize.BIG else TextSize.TALL)
        lines(leftRight(dateTime(d.atMillis, d.zone, l), reference(d.returnId), w))
        lines(wrap(l.ofSale.format(reference(d.saleId)), w))
        d.by?.takeIf { it.isNotBlank() }?.let { lines(wrap(l.servedBy.format(it.trim()), w)) }
        rule()
        for ((i, amount) in d.lines) {
            lines(wrap(i.name + (i.variant?.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: ""), w))
            val qty = quantity(i.quantityMilli) + (i.unit?.let { " $it" } ?: "")
            lines(leftRight("  $qty", "-" + d.money(amount), w))
        }
        rule()
        val total = "-" + d.money(d.totalMinor)
        if (l.returnTotal.length + 1 + total.length <= w) line(leftRight(l.returnTotal, total, w).single(), bold = true, size = TextSize.TALL)
        else { line(l.returnTotal, bold = true, size = TextSize.TALL); lines(wrap(total, w).map { it.padStart(w) }, bold = true, size = TextSize.TALL) }
        for ((method, amount) in d.refunds) lines(leftRight(if (method == "CREDIT") l.creditNote else l.method(method, null), d.money(amount), w))
        if (d.reason.isNotBlank()) lines(wrap(l.returnReason.format(d.reason.trim()), w))
        rule()
        if (d.footer.isNotBlank()) lines(wrap(d.footer, w), Align.CENTER)
        line(l.returnTicket, Align.CENTER)
        return Receipt(out, w, charset)
    }
}

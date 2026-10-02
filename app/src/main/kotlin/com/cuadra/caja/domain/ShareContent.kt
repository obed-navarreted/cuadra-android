package com.cuadra.caja.domain

/** Una fila de la tarjeta (imagen): izquierda (fecha/concepto) y derecha (monto). */
data class CardLine(val left: String, val right: String, val emphasis: Boolean = false)

/** Lo que se dibuja en la imagen que se comparte. Todo llega ya formateado (dinero, fechas, idioma): aquí no hay nada dependiente del teléfono. */
data class ShareCard(val business: String, val title: String, val subtitle: String?, val lines: List<CardLine>, val totalLabel: String, val totalValue: String)

/** Un mismo contenido en sus dos formatos: texto (plantilla con variables) e imagen (tarjeta). */
data class ShareContent(val kind: MessageKind, val vars: Map<String, String>, val card: ShareCard)

/** Un movimiento del estado de cuenta ya formateado. */
data class StatementLine(val date: String, val label: String, val amount: String, val isPayment: Boolean)

/** Textos de la tarjeta en el idioma de quien envía. */
data class CardLabels(
    val reminder: String, val statement: String, val credit: String, val payment: String, val paidOff: String, val balance: String, val total: String,
    /** "desde hace 16 días" / "open for 16 days": lleva `%d`. Solo se usa si el fiado tiene 1 día o más. */
    val openFor: String = "%d",
    /** Título de la tarjeta del comprobante de una venta. */
    val ticket: String = "Ticket",
)


object ShareBuilders {
    private fun vars(business: String, customer: String, date: String = "", amount: String = "", detail: String = "", balance: String = "", paidLine: String = "", days: String = "", since: String = "") =
        mapOf("negocio" to business, "cliente" to customer, "fecha" to date, "monto" to amount, "detalle" to detail, "saldo" to balance, "pagado_linea" to paidLine, "dias" to days, "desde" to since)

    /** Recordatorio de deuda: cuánto debe y desde hace cuántos días. */
    fun reminder(business: String, debtor: String, balance: String, days: Long, labels: CardLabels) = ShareContent(
        // "desde" es " (desde hace 16 días)" o vacío si la deuda es de hoy: decir "desde hace 0 días" suena mal.
        MessageKind.REMINDER, vars(business, debtor, balance = balance, days = days.toString(), since = if (days >= 1) " (" + labels.openFor.format(days) + ")" else ""),
        ShareCard(business, labels.reminder, debtor, listOf(CardLine(labels.balance, balance, emphasis = true)), labels.balance, balance),
    )

    /** Estado de cuenta: los últimos movimientos y el saldo. El texto lista los movimientos con "· ". */
    fun statement(business: String, customer: String, lines: List<StatementLine>, balance: String, labels: CardLabels): ShareContent {
        val shown = lines.takeLast(MAX)
        val detail = shown.joinToString("\n") { "· ${it.date} ${it.label} ${if (it.isPayment) "−" else ""}${it.amount}" }
        return ShareContent(
            MessageKind.STATEMENT, vars(business, customer, detail = detail, balance = balance),
            ShareCard(business, labels.statement, customer, shown.map { CardLine("${it.date}  ${it.label}", (if (it.isPayment) "− " else "") + it.amount) }, labels.balance, balance),
        )
    }

    /** Fiado nuevo (al cobrar una venta): el detalle de la compra, lo fiado y, si hubo, lo que pagó en el momento. */
    fun creditNew(business: String, debtor: String, date: String, credited: String, items: List<Pair<String, String>>, paidNow: String?, labels: CardLabels, paidLinePrefix: String): ShareContent {
        val detail = items.take(MAX).joinToString("\n") { line(it) }
        val paidLine = paidNow?.let { "$paidLinePrefix $it. " }.orEmpty()
        return ShareContent(
            MessageKind.CREDIT_NEW, vars(business, debtor, date = date, amount = credited, detail = detail, paidLine = paidLine),
            ShareCard(business, labels.credit, debtor, items.take(MAX).map { CardLine(it.first, it.second) }, labels.total, credited),
        )
    }

    /** Comprobante de una venta: cada línea con su monto y el total (plantilla «Comprobante de venta»). */
    fun ticket(business: String, date: String, items: List<Pair<String, String>>, total: String, labels: CardLabels): ShareContent {
        val detail = items.take(TICKET_MAX).joinToString("\n") { line(it) }
        return ShareContent(
            MessageKind.TICKET, vars(business, "", date = date, amount = total, detail = detail),
            ShareCard(business, labels.ticket, null, items.take(TICKET_MAX).map { CardLine(it.first, it.second) }, labels.total, total),
        )
    }

    /** Abono recibido, o "deuda saldada" si con él ya no debe nada. */
    fun payment(business: String, debtor: String, date: String, amount: String, balance: String, paidOff: Boolean, labels: CardLabels) = ShareContent(
        if (paidOff) MessageKind.PAID_OFF else MessageKind.PAYMENT, vars(business, debtor, date = date, amount = amount, balance = balance),
        ShareCard(business, if (paidOff) labels.paidOff else labels.payment, debtor, listOf(CardLine(date, amount), CardLine(labels.balance, balance, emphasis = true)), labels.balance, balance),
    )

    private const val MAX = 8

    /** «· Toña ×7 — C$ 315.00»; una línea de promoción («Promo 3 por C$ 100:») va con su monto pegado: «· Promo 3 por C$ 100: -C$ 70.00». */
    private fun line(it: Pair<String, String>): String = if (it.first.endsWith(":")) "· ${it.first} ${it.second}" else "· ${it.first} — ${it.second}"

    /** Un comprobante lleva TODAS las líneas de la venta (hasta este tope), no solo las primeras. */
    private const val TICKET_MAX = 40
}

/**
 * «Ofrecer enviar el comprobante por WhatsApp al terminar la venta» (preferencia de ESTE teléfono, apagada por omisión). Decide qué queda en la pantalla
 * «Venta cobrada» y si algo se abre solo. Genérico para poder probarlo sin la interfaz.
 */
object WhatsAppOffer {
    /** Lo que se ofrece con el botón «Enviar detalle por WhatsApp»: nada si la preferencia está apagada. */
    fun <T : Any> doneShare(offer: Boolean, credit: T?, ticket: T): T? = if (offer) credit ?: ticket else null

    /** Lo que se abre SOLO al terminar: únicamente el detalle del fiado, y solo con la preferencia encendida y la casilla marcada. */
    fun <T : Any> autoShare(offer: Boolean, checked: Boolean, credit: T?): T? = if (offer && checked) credit else null
}

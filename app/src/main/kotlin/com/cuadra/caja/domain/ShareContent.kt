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
        val detail = items.take(MAX).joinToString("\n") { "· ${it.first}" }
        val paidLine = paidNow?.let { "$paidLinePrefix $it. " }.orEmpty()
        return ShareContent(
            MessageKind.CREDIT_NEW, vars(business, debtor, date = date, amount = credited, detail = detail, paidLine = paidLine),
            ShareCard(business, labels.credit, debtor, items.take(MAX).map { CardLine(it.first, it.second) }, labels.total, credited),
        )
    }

    /** Abono recibido, o "deuda saldada" si con él ya no debe nada. */
    fun payment(business: String, debtor: String, date: String, amount: String, balance: String, paidOff: Boolean, labels: CardLabels) = ShareContent(
        if (paidOff) MessageKind.PAID_OFF else MessageKind.PAYMENT, vars(business, debtor, date = date, amount = amount, balance = balance),
        ShareCard(business, if (paidOff) labels.paidOff else labels.payment, debtor, listOf(CardLine(date, amount), CardLine(labels.balance, balance, emphasis = true)), labels.balance, balance),
    )

    private const val MAX = 8
}

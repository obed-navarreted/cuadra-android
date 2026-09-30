package com.cuadra.caja.domain

/**
 * La pantalla «Venta cobrada» y lo que queda después (decisiones puras, sin interfaz):
 * - el destello dura [QUICK_MILLIS] y vuelve solo a una venta nueva; con la preferencia de WhatsApp encendida (y algo que enviar) dura [WHATSAPP_MILLIS] y
 *   muestra el botón «Enviar por WhatsApp» con una barra de tiempo;
 * - ya en la venta nueva, un aviso flotante [SaleNotice] muestra el vuelto (si hubo) y «Anular» durante [SaleNotice.VISIBLE_MILLIS].
 */
object SaleFlash {
    const val QUICK_MILLIS: Long = 1_200
    const val WHATSAPP_MILLIS: Long = 4_000

    /** ¿Se ofrece el botón de WhatsApp (y por eso la pantalla espera más)? Solo con la preferencia encendida y algo que enviar. */
    fun offersWhatsApp(offerWhatsApp: Boolean, hasShare: Boolean): Boolean = offerWhatsApp && hasShare

    /** Cuánto se queda la pantalla antes de volver sola a una venta nueva. */
    fun durationMillis(offerWhatsApp: Boolean, hasShare: Boolean): Long = if (offersWhatsApp(offerWhatsApp, hasShare)) WHATSAPP_MILLIS else QUICK_MILLIS

    /** El vuelto se destaca en el destello solo si hubo (> 0). */
    fun showsChange(changeMinor: Long?): Boolean = (changeMinor ?: 0L) > 0L
}

/** Qué dice el aviso flotante que queda tras cobrar. */
data class SaleNoticeContent(val kind: Kind, val totalMinor: Long, val changeMinor: Long, val canUndo: Boolean) {
    enum class Kind { CHANGE, SOLD, UNDONE }
}

object SaleNotice {
    /** El aviso de una venta cobrada se queda a la vista hasta el primer toque del teclado o de un producto, o hasta este tiempo. */
    const val VISIBLE_MILLIS: Long = 8_000

    /** «Venta anulada» es solo una confirmación: dura lo de cualquier aviso. */
    const val UNDONE_MILLIS: Long = 3_500

    /**
     * Contenido del aviso: con vuelto, «Vuelto C$ X · Anular»; sin vuelto, «Venta cobrada · C$ X · Anular»; «Anular» solo mientras siga el plazo de 5 minutos
     * ([SaleUndo]); una venta ya anulada solo confirma.
     */
    fun of(totalMinor: Long, changeMinor: Long, doneAtMillis: Long?, nowMillis: Long, undone: Boolean = false): SaleNoticeContent {
        if (undone) return SaleNoticeContent(SaleNoticeContent.Kind.UNDONE, totalMinor, 0, canUndo = false)
        val kind = if (SaleFlash.showsChange(changeMinor)) SaleNoticeContent.Kind.CHANGE else SaleNoticeContent.Kind.SOLD
        return SaleNoticeContent(kind, totalMinor, changeMinor.coerceAtLeast(0), canUndo = SaleUndo.remaining(doneAtMillis, nowMillis) > 0)
    }

    fun visibleMillis(undone: Boolean): Long = if (undone) UNDONE_MILLIS else VISIBLE_MILLIS
}

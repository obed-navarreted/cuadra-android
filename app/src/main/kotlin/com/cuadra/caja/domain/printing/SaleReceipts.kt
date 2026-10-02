package com.cuadra.caja.domain.printing

import com.cuadra.caja.core.model.Currency
import com.cuadra.caja.core.model.Money
import com.cuadra.caja.domain.SaleView
import java.time.ZoneId
import java.util.Locale

/** De una venta (`SaleView`) y los ajustes de la impresora al recibo listo para imprimir. Puro: la prueba lo comprueba sin Android. */
object SaleReceipts {
    /**
     * Dinero como sale en el papel: el formato del país del negocio con los decimales de su moneda. Si el símbolo de la moneda no existe en el juego de caracteres
     * de la impresora («₡», «₲»), se imprime el código («CRC»).
     */
    fun money(currency: Currency, locale: Locale, charset: PrintCharset): (Long) -> String {
        val printable = if (PrintText.canEncode(currency.symbol, charset)) currency else currency.copy(symbol = currency.code)
        // Los separadores de miles de algunos países son espacios «irrompibles»: en el papel es un espacio normal.
        return { minor -> Money(minor).format(printable, locale).replace('\u00A0', ' ').replace('\u202F', ' ') }
    }

    fun data(
        sale: SaleView, businessName: String, zone: ZoneId, s: PrinterSettings, language: String?, money: (Long) -> String,
    ) = ReceiptData(
        businessName = businessName, address = s.address, phone = s.phone, taxId = s.taxId, saleId = sale.id, atMillis = sale.atMillis, zone = zone, cashier = sale.soldBy,
        // Con promociones, cada línea a su precio de siempre (el descuento de la promoción va en su propia línea, debajo).
        items = sale.items.map {
            if (sale.promotions.isEmpty()) ReceiptItem(it.name, it.variant, it.quantityMilli, it.unitPriceMinor, it.discountMinor, it.lineTotalMinor, if (it.quantityMilli % 1000L != 0L) "lb" else null)
            else ReceiptItem(it.name, it.variant, it.quantityMilli, it.unitPriceMinor, 0, it.lineTotalMinor + it.discountMinor, if (it.quantityMilli % 1000L != 0L) "lb" else null)
        },
        subtotalMinor = sale.subtotalMinor, discountMinor = sale.discountMinor, totalMinor = sale.totalMinor,
        payments = sale.payments.map { ReceiptPayment(it.method, it.otherLabel, it.amountMinor, it.tenderedMinor, it.changeMinor, it.debtorLabel) },
        cancelled = sale.cancelled, footer = s.footer, labels = ReceiptLabels.of(language), money = money,
        promotions = sale.promotions.map { ReceiptPromotion(it.quantity, it.priceMinor, it.discountMinor) },
    )

    fun receipt(sale: SaleView, businessName: String, zone: ZoneId, s: PrinterSettings, language: String?, money: (Long) -> String): Receipt =
        ReceiptFormatter.build(data(sale, businessName, zone, s, language, money), s.columns, s.charset)

    /** El comprobante de una devolución de `sale` (las cantidades pesadas llevan «lb», igual que el recibo). */
    fun returnReceipt(sale: SaleView, ret: com.cuadra.caja.domain.SaleReturnView, businessName: String, zone: ZoneId, s: PrinterSettings, language: String?, money: (Long) -> String): Receipt {
        val byId = sale.items.associateBy { it.id }
        val lines = ret.lines.map { r ->
            val item = byId[r.saleItemId]
            ReceiptItem(item?.name ?: r.name, item?.variant, r.quantityMilli, item?.unitPriceMinor ?: 0, 0, r.amountMinor, if (r.quantityMilli % 1000L != 0L) "lb" else null) to r.amountMinor
        }
        val d = ReturnReceiptData(businessName, s.address, s.phone, s.taxId, sale.id, ret.id, ret.atMillis, zone, ret.by, lines, ret.totalMinor, ret.refunds, ret.reason,
            s.footer, ReceiptLabels.of(language), money)
        return ReceiptFormatter.buildReturn(d, s.columns, s.charset)
    }
}

/** Recibos de ejemplo: la vista previa de Ajustes (con SUS encabezados y pie) y la prueba de impresión (acentos, ñ, 20 renglones y corte). */
object PrinterSamples {
    private fun item(name: String, milli: Long, price: Long, unit: String? = null) =
        ReceiptItem(name, null, milli, price, 0, Math.floorDiv(price * milli + 500, 1000L), unit)

    /** Un recibo corto de muestra con los ajustes actuales. */
    fun preview(businessName: String, s: PrinterSettings, language: String?, money: (Long) -> String, zone: ZoneId, atMillis: Long): Receipt {
        val items = listOf(item("Cuajada fresca", 2000, 2750), item("Queso seco", 750, 9000, "lb"), item("Café molido", 1000, 4500))
        val subtotal = items.sumOf { it.totalMinor }
        val d = ReceiptData(
            businessName, s.address, s.phone, s.taxId, "a1b2c3d4", atMillis, zone, "Kevin", items, subtotal, 0, subtotal,
            listOf(ReceiptPayment("CASH", null, subtotal, 20_000, 20_000 - subtotal, null)), false, s.footer, ReceiptLabels.of(language), money,
        )
        return ReceiptFormatter.build(d, s.columns, s.charset)
    }

    /** Prueba de impresión: reglas de columnas, todas las letras con tilde, ñ, ¿ ¡ €, un recibo de 20 renglones y el corte. */
    fun test(businessName: String, s: PrinterSettings, language: String?, money: (Long) -> String, zone: ZoneId, atMillis: Long): Receipt {
        val items = (1..20).map { item(String.format(Locale.ROOT, "Producto de prueba %02d", it), 1000L * (1 + it % 3), 1_000L * it + 250) }
        val subtotal = items.sumOf { it.totalMinor }
        val d = ReceiptData(
            businessName, s.address, s.phone, s.taxId, "prueba", atMillis, zone, "Kevin", items, subtotal, 0, subtotal,
            listOf(ReceiptPayment("CASH", null, subtotal, subtotal + 5_000, 5_000, null)), false, s.footer, ReceiptLabels.of(language), money,
        )
        val body = ReceiptFormatter.build(d, s.columns, s.charset).lines
        val w = s.columns
        val head = buildList {
            add(ReceiptLine("PRUEBA DE IMPRESORA", Align.CENTER, bold = true, size = TextSize.TALL))
            add(ReceiptLine("1234567890".repeat(5).take(w)))
            add(ReceiptLine("áéíóú ÁÉÍÓÚ ñÑ üÜ ¿? ¡! €"))
            add(ReceiptLine("ABCDEFGHIJKLMNÑOPQRSTUVWXYZ"))
            add(ReceiptLine("abcdefghijklmnñopqrstuvwxyz"))
            add(ReceiptLine("-".repeat(w)))
        }
        return Receipt(head + body, w, s.charset)
    }
}

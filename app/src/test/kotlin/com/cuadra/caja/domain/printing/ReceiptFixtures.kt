package com.cuadra.caja.domain.printing

import com.cuadra.caja.core.model.Currency
import java.time.ZoneId
import java.util.Locale

/** Ventas de ejemplo para las pruebas de instantánea del recibo. La hora es fija: 29 sep 2026 14:32 en Managua (UTC-6) = 20:32 UTC. */
object ReceiptFixtures {
    val ZONE: ZoneId = ZoneId.of("America/Managua")
    const val AT = 1_790_713_920_000L // 2026-09-29T20:32:00Z
    val NIO = SaleReceipts.money(Currency.of("NIO"), Locale.Builder().setLanguage("es").setRegion("NI").build(), PrintCharset.PC858)
    val CRC = SaleReceipts.money(Currency.of("CRC"), Locale.Builder().setLanguage("es").setRegion("CR").build(), PrintCharset.PC858)

    fun item(name: String, milli: Long, price: Long, variant: String? = null, unit: String? = null, discount: Long = 0) =
        ReceiptItem(name, variant, milli, price, discount, Math.floorDiv(price * milli + 500, 1000L) - discount, unit)

    fun data(
        items: List<ReceiptItem>, payments: List<ReceiptPayment>, name: String = "Quesería La Esperanza", id: String = "a1b2c3d4-0000-4000-8000-000000000001",
        cashier: String? = "Kevin", cancelled: Boolean = false, discount: Long = 0, money: (Long) -> String = NIO, labels: ReceiptLabels = ReceiptLabels.ES,
        address: String = "", phone: String = "", tax: String = "", footer: String = "",
    ): ReceiptData {
        val subtotal = items.sumOf { it.totalMinor }
        return ReceiptData(name, address, phone, tax, id, AT, ZONE, cashier, items, subtotal, discount, subtotal - discount, payments, cancelled, footer, labels, money)
    }

    fun cash(amount: Long, tendered: Long? = null) = ReceiptPayment("CASH", null, amount, tendered, tendered?.let { it - amount }, null)

    val simple = data(
        listOf(item("Cuajada fresca", 2000, 2750), item("Queso seco", 750, 9000, unit = "lb")),
        listOf(cash(12250, 15000)), address = "Barrio Central, Estelí", phone = "8888-1234", tax = "RUC J0310000000001",
    )
}

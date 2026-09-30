package com.cuadra.caja.domain.printing

import com.cuadra.caja.domain.printing.ReceiptFixtures as F

/** Los escenarios de instantánea del recibo (el mismo conjunto lo usan las pruebas de texto y de bytes). */
object Scenarios {
    const val NAME_200 = "Cuaderno profesional de espiral con cubierta dura, cien hojas rayadas, marca reconocida, edición limitada de regreso a clases con diseño de dinosaurios y separadores de colores incluidos en el empaque original"

    val all: Map<String, Receipt> = linkedMapOf(
        "simple" to ReceiptFormatter.build(F.simple),
        "weighed" to ReceiptFormatter.build(F.data(listOf(F.item("Queso seco", 750, 9000, unit = "lb"), F.item("Carne molida", 2345, 6500, unit = "lb")), listOf(F.cash(21_993)))),
        "open_price" to ReceiptFormatter.build(F.data(listOf(F.item("Servicio a domicilio", 1000, 7500), F.item("Varios", 3000, 1250)), listOf(F.cash(11_250)))),
        "long_names" to ReceiptFormatter.build(F.data(
            listOf(F.item(NAME_200, 1000, 12_000, variant = "Caja x 24"), F.item("Supercalifragilisticoespialidosoextraordinariamentelargo", 1000, 500)), listOf(F.cash(12_500)),
            name = "Distribuidora Comercial Internacional de Alimentos, Bebidas y Productos de Limpieza",
        )),
        "accents" to ReceiptFormatter.build(F.data(listOf(F.item("Piña colada ¡fría!", 1000, 3500), F.item("Café molido 100% (ñandú) – año", 1000, 18_000, variant = "Bolsa"), F.item("Jalapeño €", 2000, 1000)), listOf(F.cash(23_500)), name = "Panadería «La Niña»", cashier = "María Peña", footer = "¡Gracias, vuelva pronto!")),
        "three_payments" to ReceiptFormatter.build(F.data(
            listOf(F.item("Refresco", 3000, 3500), F.item("Pan", 10_000, 500), F.item("Queso fresco", 1500, 9000, unit = "lb")),
            listOf(ReceiptPayment("TRANSFER", null, 10_000, null, null, null), ReceiptPayment("CARD", null, 5_000, null, null, null), F.cash(14_000, 20_000)),
        )),
        "credit" to ReceiptFormatter.build(F.data(
            listOf(F.item("Arroz 25 lb", 1000, 95_000), F.item("Aceite", 2000, 8_000)),
            listOf(F.cash(30_000, 30_000), ReceiptPayment("CREDIT", null, 81_000, null, null, "María de los Ángeles Fernández de la Concepción")),
        )),
        "cancelled" to ReceiptFormatter.build(F.data(listOf(F.item("Cuajada fresca", 1000, 2750)), listOf(F.cash(2750)), cancelled = true)),
        "zero_decimals" to ReceiptFormatter.build(F.data(listOf(F.item("Café", 2000, 1500), F.item("Jugo", 1000, 2500)), listOf(F.cash(5500, 10_000)), money = F.CRC, name = "Soda El Rancho")),
        "width_80" to ReceiptFormatter.build(F.data(listOf(F.item("Cuajada fresca", 2000, 2750), F.item(NAME_200, 1000, 12_000)), listOf(F.cash(17_500, 20_000)), address = "Barrio Central, Estelí, frente a la iglesia", phone = "8888-1234"), 48),
        "huge" to ReceiptFormatter.build(F.data(listOf(F.item("Camión de carga", 1000, 9_999_999_900L), F.item("Repuestos", 99_999_000, 1_234_567_89)), listOf(F.cash(123_555_544_431_11L, 123_555_544_431_11L)), discount = 1_000_000)),
        "ascii" to ReceiptFormatter.build(F.data(listOf(F.item("Piña colada ¡fría!", 1000, 3500)), listOf(F.cash(3500)), name = "Panadería «La Niña»", footer = "¡Gracias!"), 32, PrintCharset.ASCII),
        "english" to ReceiptFormatter.build(F.data(listOf(F.item("Fresh cheese", 2000, 2750)), listOf(F.cash(5500, 10_000)), labels = ReceiptLabels.EN)),
    )
}

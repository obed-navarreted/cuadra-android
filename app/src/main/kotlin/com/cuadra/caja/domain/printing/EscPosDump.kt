package com.cuadra.caja.domain.printing

/**
 * Lee los bytes ESC/POS que produce `EscPos` y los deja como texto legible: cada comando entre corchetes («[ESC @]», «[align center]», «[size 11]»…) y el texto tal
 * como lo dibujaría la impresora. Sirve para las pruebas de instantánea y para ver en el registro lo que se «imprimió» con la impresora simulada.
 */
object EscPosDump {
    fun toText(bytes: ByteArray, charset: PrintCharset): String {
        val sb = StringBuilder()
        val text = java.io.ByteArrayOutputStream()
        fun flush() { if (text.size() > 0) { sb.append(decode(text.toByteArray(), charset)); text.reset() } }
        var i = 0
        fun b(k: Int) = if (i + k < bytes.size) bytes[i + k].toInt() and 0xFF else 0
        while (i < bytes.size) {
            val c = bytes[i].toInt() and 0xFF
            when {
                c == 0x0A -> { flush(); sb.append('\n'); i++ }
                c == 0x1B -> {
                    flush()
                    when (b(1)) {
                        0x40 -> { sb.append("[init]"); i += 2 }
                        0x4D -> { sb.append("[font ${b(2)}]"); i += 3 }
                        0x74 -> { sb.append("[codepage ${b(2)}]"); i += 3 }
                        0x61 -> { sb.append("[align ${listOf("left", "center", "right").getOrElse(b(2)) { "?" }}]"); i += 3 }
                        0x45 -> { sb.append(if (b(2) != 0) "[bold]" else "[/bold]"); i += 3 }
                        0x64 -> { sb.append("[feed ${b(2)}]"); i += 3 }
                        else -> { sb.append("[ESC ${"%02X".format(b(1))}]"); i += 2 }
                    }
                }
                c == 0x1D -> {
                    flush()
                    when (b(1)) {
                        0x21 -> { sb.append("[size ${"%02X".format(b(2))}]"); i += 3 }
                        0x56 -> { sb.append("[cut]"); i += 4 }
                        else -> { sb.append("[GS ${"%02X".format(b(1))}]"); i += 2 }
                    }
                }
                c == 0x10 && b(1) == 0x04 -> { flush(); sb.append("[status]"); i += 3 }
                else -> { text.write(c); i++ }
            }
        }
        flush()
        return sb.toString()
    }

    private fun decode(b: ByteArray, charset: PrintCharset): String {
        val sb = StringBuilder()
        for (x in b) {
            val v = x.toInt() and 0xFF
            sb.append(if (v < 0x80 || charset == PrintCharset.ASCII) v.toChar() else PrintText.high(v))
        }
        return sb.toString()
    }
}

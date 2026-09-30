package com.cuadra.caja.domain.printing

import java.io.ByteArrayOutputStream
import java.text.Normalizer

/** Juego de caracteres con el que se manda el texto a la impresora. */
enum class PrintCharset(val key: String) {
    /** Página de códigos 858 (`ESC t 19`): á é í ó ú ñ ü ¿ ¡ € y demás del español. Lo normal en las impresoras de 58 mm. */
    PC858("pc858"),

    /** Solo ASCII: las tildes se quitan («café» → «cafe», «ñ» → «n»). Para impresoras que dibujan mal las tildes. */
    ASCII("ascii");

    companion object {
        fun parse(key: String?): PrintCharset = entries.firstOrNull { it.key == key } ?: PC858
    }
}

/** Texto → bytes de la impresora, con la tabla de PC858 (= PC850 con «€» en 0xD5) y el respaldo «sin acentos». Puro: sin Android. */
object PrintText {
    // Mitad alta (0x80-0xFF) de PC858, 16 caracteres por fila.
    private const val HIGH =
        "ÇüéâäàåçêëèïîìÄÅ" +
            "ÉæÆôöòûùÿÖÜø£Ø×ƒ" +
            "áíóúñÑªº¿®¬½¼¡«»" +
            "░▒▓│┤ÁÂÀ©╣║╗╝¢¥┐" +
            "└┴┬├─┼ãÃ╚╔╩╦╠═╬¤" +
            "ðÐÊËÈ€ÍÎÏ┘┌█▄¦Ì▀" +
            "ÓßÔÒõÕµþÞÚÛÙýÝ¯´" +
            "­±‗¾¶§÷¸°¨·¹³²■ "

    private val toByte: Map<Char, Byte> = HIGH.withIndex().associate { (i, c) -> c to (0x80 + i).toByte() }

    /** Espacios raros (irrompibles, finos) y tabulaciones pasan a un espacio normal; los caracteres de control se quitan. */
    private fun clean(c: Char): Char? = when {
        c == ' ' || c == ' ' || c == ' ' || c == ' ' || c == '\t' -> ' '
        c < ' ' || c == '\u007F' -> null
        else -> c
    }

    /** Quita las marcas de acento («é» → «e») y cambia lo que no tiene descomposición («ß» → «ss», «ø» → «o»). Lo que siga sin ser ASCII queda como «?». */
    fun transliterate(c: Char): String {
        when (c) {
            'ß' -> return "ss"; 'æ' -> return "ae"; 'Æ' -> return "AE"; 'ø' -> return "o"; 'Ø' -> return "O"; 'ð' -> return "d"; 'Ð' -> return "D"
            'þ' -> return "th"; 'Þ' -> return "TH"; 'ı' -> return "i"; 'ł' -> return "l"; 'Ł' -> return "L"
            '¿' -> return "?"; '¡' -> return "!"; '€' -> return "EUR"; '£' -> return "GBP"; '¥' -> return "JPY"; '¢' -> return "c"
            '«', '»', '“', '”', '„' -> return "\""; '‘', '’', '‚' -> return "'"; '–', '—', '−' -> return "-"; '…' -> return "..."; '×' -> return "x"
            '·', '•' -> return "-"; 'º' -> return "o"; 'ª' -> return "a"; '°' -> return "o"; '©' -> return "(c)"; '®' -> return "(R)"
        }
        if (c.code < 0x80) return c.toString()
        val plain = Normalizer.normalize(c.toString(), Normalizer.Form.NFD).filter { it.code < 0x80 }
        return plain.ifEmpty { "?" }
    }

    /** ¿Todo el texto sale tal cual en este juego de caracteres (sin cambiarlo)? Sirve para decidir si un símbolo de moneda se puede imprimir. */
    fun canEncode(text: String, charset: PrintCharset): Boolean = text.all { c ->
        val k = clean(c) ?: return@all true
        k.code < 0x80 || (charset == PrintCharset.PC858 && k in toByte)
    }

    fun encode(text: String, charset: PrintCharset): ByteArray {
        val out = ByteArrayOutputStream(text.length)
        for (raw in text) {
            val c = clean(raw) ?: continue
            when {
                c.code < 0x80 -> out.write(c.code)
                charset == PrintCharset.PC858 && c in toByte -> out.write(toByte.getValue(c).toInt())
                else -> transliterate(c).forEach { out.write(it.code) }
            }
        }
        return out.toByteArray()
    }

    /** El texto tal como quedará impreso (para la vista previa): lo que la impresora no puede dibujar ya viene cambiado. */
    fun printable(text: String, charset: PrintCharset): String = String(encode(text, charset).map { b -> decode(b, charset) }.toCharArray())

    /** El carácter de PC858 que corresponde al byte alto `v` (0x80-0xFF). */
    fun high(v: Int): Char = HIGH[v - 0x80]

    private fun decode(b: Byte, charset: PrintCharset): Char {
        val v = b.toInt() and 0xFF
        return if (v < 0x80 || charset == PrintCharset.ASCII) v.toChar() else HIGH[v - 0x80]
    }
}

enum class Align(val code: Int) { LEFT(0), CENTER(1), RIGHT(2) }

/** Tamaño de letra: `GS ! n`. `WIDE` y `BIG` duplican el ancho, así que caben la mitad de las columnas. */
enum class TextSize(val code: Int, val widthFactor: Int) { NORMAL(0x00, 1), TALL(0x01, 1), WIDE(0x10, 2), BIG(0x11, 2) }

/**
 * Constructor de comandos ESC/POS (el «idioma» de casi todas las impresoras térmicas de 58 y 80 mm). Cada función agrega bytes; `toBytes()` entrega el trabajo.
 * Todo son comandos estándar (Epson): inicializar, fuente A, página de códigos, alineación, negrita, tamaño, avance y corte parcial.
 */
class EscPos(private val charset: PrintCharset) {
    private val out = ByteArrayOutputStream()

    private fun raw(vararg b: Int): EscPos { b.forEach { out.write(it) }; return this }

    /** `ESC @` (reinicia), `ESC M 0` (fuente A, 12x24 = 32 columnas en 58 mm) y `ESC t 19` (PC858) si el juego es PC858. */
    fun init(): EscPos {
        raw(0x1B, 0x40).raw(0x1B, 0x4D, 0x00)
        if (charset == PrintCharset.PC858) raw(0x1B, 0x74, PC858_PAGE)
        return this
    }

    fun align(a: Align) = raw(0x1B, 0x61, a.code)
    fun bold(on: Boolean) = raw(0x1B, 0x45, if (on) 1 else 0)
    fun size(s: TextSize) = raw(0x1D, 0x21, s.code)
    fun text(t: String): EscPos { out.write(PrintText.encode(t, charset)); return this }
    fun lf() = raw(0x0A)

    /** `ESC d n`: avanza n líneas. */
    fun feed(n: Int) = raw(0x1B, 0x64, n.coerceIn(0, 255))

    /** `GS V 66 0`: avanza hasta la posición de corte y corta (parcial). Sin cortador, la impresora lo ignora. */
    fun cut() = raw(0x1D, 0x56, 0x42, 0x00)

    fun toBytes(): ByteArray = out.toByteArray()

    companion object {
        /** Número de la página de códigos PC858 en `ESC t` (Epson). */
        const val PC858_PAGE = 19

        /** `DLE EOT 1`: pide el estado en tiempo real; no imprime nada. Se usa para comprobar que el enlace sigue vivo. */
        val STATUS_QUERY = byteArrayOf(0x10, 0x04, 0x01)
    }
}

package com.cuadra.caja.domain.printing

/** Por dónde se conecta la impresora. */
enum class PrinterLink(val key: String) {
    BLUETOOTH("bt"), USB("usb");

    companion object {
        fun parse(key: String?): PrinterLink = entries.firstOrNull { it.key == key } ?: BLUETOOTH
    }
}

/**
 * Ajustes de la impresora de ESTE teléfono (no del negocio). Todo empieza APAGADO: quien no usa impresora no ve ningún cambio.
 * `deviceKey` es la dirección Bluetooth (AA:BB:…) o, por cable, «vendorId:productId».
 */
data class PrinterSettings(
    val enabled: Boolean = false,
    val link: PrinterLink = PrinterLink.BLUETOOTH,
    val deviceKey: String? = null,
    val deviceName: String? = null,
    val widthMm: Int = 58,
    val autoPrint: Boolean = true,
    val copies: Int = 1,
    val charset: PrintCharset = PrintCharset.PC858,
    val address: String = "",
    val phone: String = "",
    val taxId: String = "",
    val footer: String = "",
) {
    /** 32 columnas en 58 mm; 48 en 80 mm. */
    val columns: Int get() = if (widthMm >= 80) ReceiptFormatter.COLUMNS_80 else ReceiptFormatter.COLUMNS_58

    /** ¿Hay una impresora elegida para el tipo de conexión? */
    val hasDevice: Boolean get() = !deviceKey.isNullOrBlank()

    companion object {
        const val MAX_LINE = 60
        const val MAX_FOOTER = 90
    }
}

enum class ConnState { DISCONNECTED, CONNECTING, CONNECTED, ERROR }

/** Por qué no hay conexión (para explicarlo en Ajustes). */
enum class ConnIssue { NONE, NO_DEVICE, BLUETOOTH_OFF, NO_PERMISSION, USB_PERMISSION, NOT_FOUND, FAILED }

/** Cómo terminó un intento de imprimir. */
enum class PrintOutcome { PRINTED, DISABLED, NOT_CONNECTED, FAILED }

/** Espera entre intentos de conexión: 2 s → 4 → 8 → 16 → 30 s (tope). Puro. */
object Backoff {
    private val STEPS = longArrayOf(2_000, 4_000, 8_000, 16_000, 30_000)

    /** `failures` = cuántos intentos seguidos fallaron (1 = el primero). */
    fun delayMs(failures: Int): Long = STEPS[(failures - 1).coerceIn(0, STEPS.size - 1)]
}

/** El indicador de la caja: apagado (opción desactivada: no se dibuja nada) o el estado del enlace. */
enum class PrinterBadge { OFF, CONNECTED, CONNECTING, DISCONNECTED;
    companion object {
        fun of(enabled: Boolean, state: ConnState): PrinterBadge = when {
            !enabled -> OFF
            state == ConnState.CONNECTED -> CONNECTED
            state == ConnState.CONNECTING -> CONNECTING
            else -> DISCONNECTED
        }
    }
}

/** Lo que se le dice a la persona después de intentar imprimir. */
enum class PrintNotice {
    PRINTED, NO_PRINTER, FAILED;

    companion object {
        fun of(outcome: PrintOutcome): PrintNotice? = when (outcome) {
            PrintOutcome.PRINTED -> PRINTED
            PrintOutcome.NOT_CONNECTED -> NO_PRINTER
            PrintOutcome.FAILED -> FAILED
            PrintOutcome.DISABLED -> null
        }
    }
}

package com.cuadra.caja.data.scanner

import com.cuadra.caja.domain.ScanCode

/** De dónde vino una lectura. */
enum class ScanSource { CAMERA, WEDGE, DATAWEDGE }

/** Una lectura ya limpia, con lo que se sabe de ella (para la prueba de Ajustes). */
data class ScanRecord(val code: String, val source: ScanSource, val format: String?, val checksumValid: Boolean?, val atMillis: Long)

/** Lo que llega de DataWedge en la difusión (broadcast) de la lectura. */
data class DataWedgeRead(val data: String, val labelType: String?)

/**
 * Zebra DataWedge sin Android: construye los pasos de configuración (`SET_CONFIG`) y lee los extras de la lectura y de las respuestas.
 * Las estructuras son mapas/listas simples (`Map<String, Any>`, `List<Any>`, `String`); `DataWedgeClient` las convierte en `Bundle`.
 * Los valores de los parámetros van como texto («true»/«false»), que es lo que DataWedge espera.
 */
object DataWedge {
    const val PACKAGE = "com.symbol.datawedge"
    const val API_ACTION = "com.symbol.datawedge.api.ACTION"
    const val RESULT_ACTION = "com.symbol.datawedge.api.RESULT_ACTION"
    const val EXTRA_SET_CONFIG = "com.symbol.datawedge.api.SET_CONFIG"
    const val EXTRA_SWITCH_PROFILE = "com.symbol.datawedge.api.SWITCH_TO_PROFILE"
    const val EXTRA_SEND_RESULT = "SEND_RESULT"
    const val EXTRA_COMMAND_ID = "COMMAND_IDENTIFIER"

    /** Acción con la que DataWedge nos difunde cada lectura. */
    const val SCAN_ACTION = "com.cuadra.caja.SCAN"
    const val EXTRA_DATA = "com.symbol.datawedge.data_string"
    const val EXTRA_LABEL_TYPE = "com.symbol.datawedge.label_type"
    const val PROFILE = "Cuentiva"

    /** Decodificadores que se dejan encendidos (además de los que ya traiga DataWedge por omisión). */
    val DECODERS = listOf("decoder_ean13", "decoder_ean8", "decoder_upca", "decoder_upce0", "decoder_code128", "decoder_code39")

    private fun plugin(name: String, params: Map<String, Any>): Map<String, Any> =
        mapOf("PLUGIN_NAME" to name, "RESET_CONFIG" to "true", "PARAM_LIST" to params)

    /** Un paso: los extras del intent `SET_CONFIG` (`id` identifica la respuesta de DataWedge). */
    data class Step(val id: String, val config: Map<String, Any>)

    /**
     * Pasos en orden (van separados porque DataWedge solo aplica de forma fiable un plugin por intent):
     * 1 crea el perfil «Cuentiva» ligado a esta app; 2 decodificadores; 3 salida por intent (broadcast) hacia `SCAN_ACTION`; 4 salida por teclado APAGADA.
     */
    fun setConfigSteps(appPackage: String): List<Step> {
        val appList = listOf(mapOf("PACKAGE_NAME" to appPackage, "ACTIVITY_LIST" to listOf("*")))
        fun base(mode: String, plugin: Map<String, Any>?): Map<String, Any> = buildMap {
            put("PROFILE_NAME", PROFILE); put("PROFILE_ENABLED", "true"); put("CONFIG_MODE", mode); put("APP_LIST", appList)
            if (plugin != null) put("PLUGIN_CONFIG", plugin)
        }
        val barcode = plugin("BARCODE", buildMap {
            // Lector del equipo ENCENDIDO (el gatillo físico escanea) y modo de disparo por omisión: gatillo (aim_type 0).
            put("scanner_selection", "auto"); put("scanner_input_enabled", "true"); put("aim_type", "0")
            DECODERS.forEach { put(it, "true") }
        })
        val intent = plugin("INTENT", mapOf("intent_output_enabled" to "true", "intent_action" to SCAN_ACTION, "intent_delivery" to "2"))
        val keystroke = plugin("KEYSTROKE", mapOf("keystroke_output_enabled" to "false"))
        return listOf(
            Step("cuentiva-1-profile", base("CREATE_IF_NOT_EXIST", null)),
            Step("cuentiva-2-barcode", base("UPDATE", barcode)),
            Step("cuentiva-3-intent", base("UPDATE", intent)),
            Step("cuentiva-4-keystroke", base("UPDATE", keystroke)),
        )
    }

    /** Extras de la lectura. Sin `data_string` o vacío: null. */
    fun readScan(data: String?, labelType: String?): DataWedgeRead? {
        val clean = data?.let(ScanCode::normalize).orEmpty()
        return if (clean.isEmpty()) null else DataWedgeRead(clean, labelType)
    }

    /** «LABEL-TYPE-EAN13» → «EAN-13». Lo desconocido se devuelve sin el prefijo. */
    fun friendlyLabel(labelType: String?): String? {
        val t = labelType?.removePrefix("LABEL-TYPE-")?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return when (t.uppercase()) {
            "EAN13" -> "EAN-13"
            "EAN8" -> "EAN-8"
            "UPCA" -> "UPC-A"
            "UPCE0", "UPCE1", "UPCE" -> "UPC-E"
            "CODE128" -> "Code 128"
            "CODE39" -> "Code 39"
            "QRCODE" -> "QR"
            else -> t
        }
    }

    /** Resultado de un paso, según la respuesta de DataWedge (`RESULT` = SUCCESS/FAILURE, `RESULT_INFO` con el motivo). */
    data class StepResult(val id: String?, val ok: Boolean, val detail: String?)

    fun parseResult(command: String?, result: String?, commandId: String?, resultInfo: String?): StepResult? {
        if (command == null || !command.endsWith("SET_CONFIG") || result == null) return null
        val exists = resultInfo?.contains("PROFILE_ALREADY_EXISTS") == true // crear un perfil que ya existe no es un problema
        return StepResult(commandId, result.equals("SUCCESS", true) || exists, resultInfo?.takeIf { it.isNotBlank() })
    }

    /** Resumen de todo el proceso: cuando llegaron todas las respuestas, ¿todo bien? Con alguna en falla: el primer motivo. */
    fun summarize(results: List<StepResult>, expected: Int): SetupOutcome = when {
        results.any { !it.ok } -> SetupOutcome.Failed(results.first { !it.ok }.detail)
        results.size >= expected -> SetupOutcome.Ok
        else -> SetupOutcome.Waiting
    }
}

sealed interface SetupOutcome {
    data object Waiting : SetupOutcome
    data object Ok : SetupOutcome
    data class Failed(val detail: String?) : SetupOutcome
}

/** Registro de una lectura del lector físico o de la cámara. */
fun scanRecord(code: String, source: ScanSource, format: String?, now: Long) =
    ScanRecord(code, source, format ?: ScanCode.guessFormat(code), ScanCode.checksumValid(code), now)

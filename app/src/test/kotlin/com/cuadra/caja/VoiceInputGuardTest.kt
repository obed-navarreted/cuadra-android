package com.cuadra.caja

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regla de producto: **todo campo de texto libre de la app se puede dictar por voz** (nombres, descripciones, motivos, notas, búsquedas).
 * Esta prueba lee el código fuente y falla si aparece un campo de texto (`OutlinedTextField`, `TextField`, `BasicTextField`) que no sea `VoiceTextField`, salvo:
 *  · campos de monto, cantidad, teléfono, PIN, correo (llevan su `KeyboardType` numérico o similar), o
 *  · un campo marcado con un comentario `// sin voz: <motivo>` en la línea de arriba (por ejemplo, un código que se escanea).
 */
class VoiceInputGuardTest {
    private val numeric = Regex("KeyboardType\\.(Decimal|Number|Phone|Password|NumberPassword|Email|Uri)")
    private val field = Regex("\\b(OutlinedTextField|BasicTextField|TextField)\\(")

    @Test fun everyFreeTextFieldOffersVoiceDictation() {
        val offenders = mutableListOf<String>()
        File("src/main/kotlin").walkTopDown().filter { it.isFile && it.extension == "kt" && it.name != "VoiceTextField.kt" }.forEach { file ->
            val text = file.readText()
            for (m in field.findAll(text)) {
                // No es una declaración ni un import: es una llamada.
                val line = text.substring(text.lastIndexOf('\n', m.range.first) + 1, m.range.first)
                if (line.trimStart().startsWith("import") || line.contains("fun ")) continue
                var depth = 0
                var end = m.range.last
                for (i in m.range.last until text.length) {
                    if (text[i] == '(') depth++
                    if (text[i] == ')') { depth--; if (depth == 0) { end = i; break } }
                }
                val call = text.substring(m.range.first, end + 1)
                val previousLine = text.substring(0, text.lastIndexOf('\n', m.range.first).coerceAtLeast(0)).substringAfterLast('\n')
                val lineNumber = text.substring(0, m.range.first).count { it == '\n' } + 1
                val ok = numeric.containsMatchIn(call) || previousLine.contains("sin voz:")
                if (!ok) offenders += "${file.path}:$lineNumber"
            }
        }
        assertTrue("Campos de texto libre SIN dictado por voz (usa VoiceTextField):\n  " + offenders.joinToString("\n  "), offenders.isEmpty())
    }

    @Test fun theGuardActuallyFindsTheFieldsOfTheApp() {
        val used = File("src/main/kotlin").walkTopDown().filter { it.isFile && it.extension == "kt" }.sumOf { Regex("VoiceTextField\\(").findAll(it.readText()).count() }
        assertTrue("Debe haber campos con voz en la app (encontró $used)", used >= 20)
    }
}

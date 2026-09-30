package com.cuadra.caja.ui.guard

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reglas de código de docs/notas/reglas-de-interfaz-app.md que se comprueban leyendo el fuente (las de dibujo las revisa `LayoutGuard`):
 *  · las pantallas usan `ui.common.Text` (nunca recorta en silencio), no `material3.Text`;
 *  · nada de `horizontalScroll` en pantallas (lo que no cabe baja de línea);
 *  · nada de tamaños de letra sueltos (`fontSize = 34.sp`, `TextUnit(34f…)`) fuera de la escala de `ui/theme/Theme.kt` (y de las piezas de texto y la barra de secciones);
 *  · nada de `Dialog(` directo: los diálogos pasan por `Sheet`/`AppDialog`, que se desplazan y fijan sus botones.
 */
class LayoutGuardRulesTest {
    private val ui = File("src/main/kotlin/com/cuadra/caja/ui")

    private fun sources() = ui.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    @Test fun screensNeverUseMaterialText() {
        val offenders = sources().filter { it.name != "Text.kt" }.filter { Regex("import androidx\\.compose\\.material3\\.Text\\s*$", RegexOption.MULTILINE).containsMatchIn(it.readText()) }
        assertTrue("Usa com.cuadra.caja.ui.common.Text en vez de material3.Text:\n" + offenders.joinToString("\n") { it.path }, offenders.isEmpty())
    }

    @Test fun noHorizontalScrollInScreens() {
        val offenders = sources().filter { "horizontalScroll(" in it.readText() }
        assertTrue("Nada se desplaza de lado: usa ChipFlow/FlowRow.\n" + offenders.joinToString("\n") { it.path }, offenders.isEmpty())
    }

    @Test fun dialogsGoThroughSheetOrAppDialog() {
        val call = Regex("(^|[^\\w.])(androidx\\.compose\\.ui\\.window\\.)?Dialog\\(")
        val offenders = sources().filter { it.name != "Dialogs.kt" }.filter { f ->
            f.readText().lines().any { l -> val t = l.trimStart(); call.containsMatchIn(l) && !t.startsWith("import") && !t.startsWith("//") && !t.startsWith("*") && !t.startsWith("/*") }
        }
        assertTrue("Usa Sheet/AppDialog en vez de Dialog(...):\n" + offenders.joinToString("\n") { it.path }, offenders.isEmpty())
    }

    /** Todo tamaño sale de la escala (Theme.kt). Excepciones deliberadas: el texto que se achica solo (`Text.kt`, `NumberField.kt`) y la etiqueta de la barra de secciones (11 sp fijos). */
    @Test fun noOneOffFontSizes() {
        val allowed = setOf("Text.kt", "NumberField.kt", "SectionBar.kt")
        val loose = Regex("fontSize\\s*=\\s*[0-9.]+\\.sp|TextUnit\\(\\s*[0-9.]+f?\\s*,|fontSize\\s*=\\s*androidx\\.compose\\.ui\\.unit\\.TextUnit\\(")
        val offenders = sources().filter { it.name !in allowed }.filter { f ->
            f.readText().lines().any { l -> val t = l.trimStart(); loose.containsMatchIn(l) && !t.startsWith("//") && !t.startsWith("*") && !t.startsWith("/*") }
        }
        assertTrue("Usa un estilo de la escala tipográfica (MaterialTheme.typography.*) en vez de un tamaño suelto:\n" + offenders.joinToString("\n") { it.path }, offenders.isEmpty())
    }

    /**
     * Toda ventana aparte (diálogo, hoja, menú, popup) estrena su propio `LocalDensity` con TODA la letra del teléfono y se salta la política de letra de la app
     * (Automático 1.15×…): quien crea una debe re-aplicar el `LocalDensity` de la app dentro. En las pruebas los diálogos van en línea, así que esto solo se puede vigilar leyendo el fuente.
     */
    @Test fun everyWindowReappliesTheAppDensity() {
        val window = Regex("(^|[^\\w.])(ModalBottomSheet|DropdownMenu|Popup|Dialog|AlertDialog|DatePickerDialog)\\(")
        val offenders = sources().filter { f ->
            val lines = f.readText().lines()
            lines.any { l -> val t = l.trimStart(); window.containsMatchIn(l) && !t.startsWith("import") && !t.startsWith("//") && !t.startsWith("*") && !t.startsWith("/*") && !t.contains("Inline") && !t.startsWith("fun ") && !t.contains("Dialog(state") } &&
                !lines.any { "LocalDensity provides" in it }
        }
        assertTrue("Una ventana aparte debe re-aplicar `LocalDensity provides` (política de letra):\n" + offenders.joinToString("\n") { it.path }, offenders.isEmpty())
    }
}

package com.cuadra.caja

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/** Español e inglés tienen exactamente las mismas cadenas, con los mismos parámetros de formato (PLAN.md 7.2). */
class StringsParityTest {
    /** Todos los `strings*.xml` de la carpeta: cada función grande puede llevar su propio archivo. */
    private fun load(dir: String): Map<String, String> {
        val out = sortedMapOf<String, String>()
        for (file in File(dir).listFiles { f -> f.name.startsWith("strings") && f.name.endsWith(".xml") }!!.sortedBy { it.name }) {
            loadInto(file, out)
        }
        return out
    }

    private fun loadInto(file: File, out: MutableMap<String, String>) {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val root = doc.documentElement
        for (i in 0 until root.childNodes.length) {
            val node = root.childNodes.item(i) as? Element ?: continue
            when (node.tagName) {
                "string" -> out["string:" + node.getAttribute("name")] = node.textContent
                "plurals" -> for (j in 0 until node.childNodes.length) {
                    val item = node.childNodes.item(j) as? Element ?: continue
                    out["plurals:" + node.getAttribute("name") + ":" + item.getAttribute("quantity")] = item.textContent
                }
            }
        }
    }

    private fun args(text: String) = Regex("%(\\d+\\$)?[sd]").findAll(text).map { it.value }.sorted().toList()

    private val es = load("src/main/res/values")
    private val en = load("src/main/res/values-en")

    @Test fun sameKeysInBothLanguages() {
        assertEquals(es.keys, en.keys)
    }

    @Test fun sameFormatArgumentsInBothLanguages() {
        for (key in es.keys) assertEquals("Parámetros distintos en $key", args(es.getValue(key)), args(en.getValue(key)))
    }

    @Test fun noEmptyTranslations() {
        assertTrue(es.values.none { it.isBlank() } && en.values.none { it.isBlank() })
    }
}

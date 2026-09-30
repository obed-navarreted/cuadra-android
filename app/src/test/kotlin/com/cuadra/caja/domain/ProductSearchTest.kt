package com.cuadra.caja.domain

import com.cuadra.caja.data.local.ProductEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Búsqueda de la pestaña «Productos», frecuentes con relleno de más vendidos, y marcar/ordenar frecuentes. */
class ProductSearchTest {
    private fun p(
        id: String, name: String, barcode: String? = null, short: String? = null, quick: Boolean = false, pos: Int? = null, pricing: String = "FIXED",
        active: Boolean = true, variant: String? = null,
    ) = ProductEntity(id, barcode, short, name, variant, "cat", "UNIT", pricing, 2500, 1800, quick, pos, "#00ff00", true, 5000, 1000, active, 7)

    private val catalog = listOf(
        p("cafe", "Café molido", barcode = "7501234567890", short = "CM1", variant = "Bolsa"),
        p("cuaj", "Cuajada fresca", barcode = "012345678905", short = "12"),
        p("pan", "Pan dulce", short = "120"),
        p("refr", "Refresco de piña", barcode = "7401000000011"),
        p("ñame", "Ñame"),
        p("baja", "Café viejo", active = false),
    )

    @Test fun normalizeIgnoresCaseAccentsAndSpaces() {
        assertEquals("cafe molido", ProductSearch.normalize("  CAFÉ   Molido "))
        assertEquals("name pina", ProductSearch.normalize("Ñame Piña"))
        assertEquals("", ProductSearch.normalize(null))
    }

    @Test fun searchByNameWithoutAccentsOrCase() {
        assertEquals(listOf("cafe"), ProductSearch.search(catalog, "cafe").map { it.id })
        assertEquals(listOf("cafe"), ProductSearch.search(catalog, "CAFÉ MOL").map { it.id })
        assertEquals(listOf("refr"), ProductSearch.search(catalog, "pina").map { it.id })
        assertEquals(listOf("ñame"), ProductSearch.search(catalog, "name").map { it.id })
        // La variante también cuenta; palabras en cualquier orden.
        assertEquals(listOf("cafe"), ProductSearch.search(catalog, "bolsa cafe").map { it.id })
    }

    @Test fun inactiveProductsNeverAppear() {
        assertFalse(ProductSearch.search(catalog, "viejo").any { it.id == "baja" })
    }

    @Test fun exactShortCodeComesFirstThenPrefix() {
        // «12» es el código corto exacto de la cuajada; «120» (pan) empieza con 12.
        assertEquals(listOf("cuaj", "pan"), ProductSearch.search(catalog, "12").map { it.id }.take(2))
        assertEquals(listOf("cafe"), ProductSearch.search(catalog, "cm1").map { it.id })
    }

    @Test fun barcodeExactAndUpcEanEquivalent() {
        assertEquals("cafe", ProductSearch.search(catalog, "7501234567890").first().id)
        // UPC-A de 12 cifras guardado; se busca su EAN-13 con cero delante.
        assertEquals("cuaj", ProductSearch.search(catalog, "0012345678905").first().id)
        // Parte de un código de barras (4+ cifras) también encuentra, pero después de los nombres.
        assertEquals(listOf("refr"), ProductSearch.search(catalog, "74010").map { it.id })
    }

    @Test fun nameStartMatchesRankBeforeContains() {
        val list = listOf(p("a", "Leche de coco"), p("b", "Coco rallado"), p("c", "Agua de coco"))
        assertEquals(listOf("b", "c", "a"), ProductSearch.search(list, "coco").map { it.id })
    }

    @Test fun blankQueryHasNoResults() {
        assertTrue(ProductSearch.search(catalog, "   ").isEmpty())
    }

    @Test fun suggestionsAreMarkedFrequentsInOrder() {
        val list = listOf(p("a", "A", quick = true, pos = 2), p("b", "B", quick = true, pos = 1), p("c", "C", quick = true, pos = null), p("d", "D"))
        val s = ProductSearch.suggestions(list, emptyList())
        assertEquals(listOf("b", "a", "c"), s.map { it.product.id })
        assertTrue(s.all { it.source == SuggestionSource.FREQUENT })
    }

    @Test fun fewFrequentsAreFilledWithBestSellersWithoutRepeating() {
        val list = listOf(p("a", "A", quick = true, pos = 1), p("b", "B"), p("c", "C"), p("x", "X", active = false)) + (1..10).map { p("s$it", "S$it") }
        // «a» ya es frecuente (no se repite), «x» está dado de baja, «zz» ya no existe en el catálogo.
        val best = listOf("a", "b", "x", "zz", "c") + (1..10).map { "s$it" }
        val s = ProductSearch.suggestions(list, best)
        assertEquals(ProductSearch.FILL_TARGET, s.size)
        assertEquals(listOf("a", "b", "c", "s1", "s2", "s3", "s4", "s5"), s.map { it.product.id })
        assertEquals(SuggestionSource.FREQUENT, s[0].source)
        assertTrue(s.drop(1).all { it.source == SuggestionSource.BEST_SELLER })
        assertEquals(s.size, s.map { it.product.id }.toSet().size)
    }

    @Test fun enoughFrequentsNeedNoFill() {
        val list = (1..9).map { p("f$it", "F$it", quick = true, pos = it) } + p("b", "B")
        val s = ProductSearch.suggestions(list, listOf("b"))
        assertEquals(9, s.size)
        assertTrue(s.none { it.source == SuggestionSource.BEST_SELLER })
    }

    @Test fun emptyEverythingGivesNothing() {
        assertTrue(ProductSearch.suggestions(emptyList(), emptyList()).isEmpty())
    }

    @Test fun paneSwitchesBetweenSuggestionsAndResults() {
        val list = catalog + p("q", "Queso", quick = true, pos = 1)
        val index = ProductSearch.Index(list)
        val empty = ProductSearch.pane(index, list, "", listOf("pan"))
        assertFalse(empty.searching)
        assertEquals(listOf("q"), empty.frequents.map { it.id })
        assertEquals(listOf("pan"), empty.bestSellers.map { it.id })
        val found = ProductSearch.pane(index, list, "ques", listOf("pan"))
        assertTrue(found.searching)
        assertEquals(listOf("q"), found.results.map { it.id })
        assertTrue(found.suggestions.isEmpty())
        assertEquals(listOf("q"), found.marked.map { it.id })
    }

    @Test fun addFlowFollowsPricing() {
        assertEquals(AddFlow.DIRECT, ProductSearch.addFlow(p("a", "A")))
        assertEquals(AddFlow.OPEN_PRICE, ProductSearch.addFlow(p("a", "A", pricing = Pricing.OPEN)))
        assertEquals(AddFlow.WEIGH, ProductSearch.addFlow(p("a", "A", pricing = Pricing.BY_WEIGHT)))
    }

    @Test fun createFromQueryUsesNameOrBarcode() {
        assertEquals("Queso seco" to "", ProductSearch.draftFor("  Queso seco "))
        assertEquals("" to "7501234567890", ProductSearch.draftFor("7501234567890"))
        assertEquals("123" to "", ProductSearch.draftFor("123"))
    }

    // ---------- marcar y ordenar frecuentes ----------

    @Test fun markingPutsItLastAndKeepsEveryOtherField() {
        val list = listOf(p("a", "A", quick = true, pos = 1), p("b", "B", quick = true, pos = 4), p("c", "C"))
        val c = list[2]
        val payload = Frequents.toggle(c, list)
        assertTrue(payload.isQuick)
        assertEquals(5, payload.quickPosition)
        // Todo lo demás, igual que un guardado normal del producto (el servidor reemplaza el producto completo).
        assertEquals(ProductPayload.build(c, c.name, c.priceMinor, c.costMinor, c.unit, c.trackStock, c.minStockMilli).copy(isQuick = true, quickPosition = 5), payload)
        assertEquals("cat", payload.categoryId); assertEquals("#00ff00", payload.color); assertEquals(1800L, payload.costMinor); assertEquals(1000L, payload.minStockMilli)
    }

    @Test fun unmarkingClearsThePosition() {
        val a = p("a", "A", barcode = "750", short = "7", quick = true, pos = 1, pricing = Pricing.OPEN, variant = "Grande")
        val payload = Frequents.toggle(a, listOf(a))
        assertFalse(payload.isQuick)
        assertEquals(null, payload.quickPosition)
        assertEquals("750", payload.barcode); assertEquals("7", payload.shortCode); assertEquals(Pricing.OPEN, payload.pricing); assertEquals("Grande", payload.variant)
        assertTrue(payload.active)
    }

    @Test fun firstFrequentGetsPositionOne() {
        assertEquals(1, Frequents.toggle(p("a", "A"), emptyList()).quickPosition)
    }

    @Test fun moveRenumbersAndSendsOnlyWhatChanged() {
        val marked = listOf(p("a", "A", quick = true, pos = 1), p("b", "B", quick = true, pos = 2), p("c", "C", quick = true, pos = 3))
        val up = Frequents.move(marked, "c", -1)
        assertEquals(listOf("b" to 3, "c" to 2), up.map { it.first to it.second.quickPosition }.sortedBy { it.first })
        assertTrue(up.all { it.second.isQuick })
        val cUp = up.first { it.first == "c" }.second
        assertEquals(ProductPayload.withFrequent(marked[2], true, 2), cUp)
        // En los extremos no hay nada que mover.
        assertTrue(Frequents.move(marked, "a", -1).isEmpty())
        assertTrue(Frequents.move(marked, "c", 1).isEmpty())
        assertTrue(Frequents.move(marked, "zz", 1).isEmpty())
    }

    @Test fun moveFixesGapsAndNullPositions() {
        val marked = listOf(p("a", "A", quick = true, pos = 5), p("b", "B", quick = true, pos = 9), p("c", "C", quick = true, pos = null))
        val down = Frequents.move(marked, "a", 1).toMap()
        assertEquals(1, down.getValue("b").quickPosition)
        assertEquals(2, down.getValue("a").quickPosition)
        assertEquals(3, down.getValue("c").quickPosition)
    }
}

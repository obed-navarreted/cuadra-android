package com.cuadra.caja.domain

import com.cuadra.caja.data.local.ProductEntity
import java.text.Normalizer
import java.util.Locale

/** Qué pasa al tocar un producto en la caja: se agrega directo, se pregunta el precio (precio abierto) o se pesa. */
enum class AddFlow { DIRECT, OPEN_PRICE, WEIGH }

/** De dónde sale una sugerencia de la pestaña «Productos» con el buscador vacío. */
enum class SuggestionSource { FREQUENT, BEST_SELLER }

data class Suggestion(val product: ProductEntity, val source: SuggestionSource)

/** Lo que muestra la pestaña «Productos»: con el buscador vacío, las sugerencias (frecuentes y más vendidos); con texto, los resultados. */
data class ProductsPane(
    val query: String = "",
    val suggestions: List<Suggestion> = emptyList(),
    val results: List<ProductEntity> = emptyList(),
    /** Los frecuentes marcados, en su orden (para «Ordenar frecuentes»). */
    val marked: List<ProductEntity> = emptyList(),
) {
    val searching: Boolean get() = query.isNotBlank()
    val frequents: List<ProductEntity> get() = suggestions.filter { it.source == SuggestionSource.FREQUENT }.map { it.product }
    val bestSellers: List<ProductEntity> get() = suggestions.filter { it.source == SuggestionSource.BEST_SELLER }.map { it.product }
}

/**
 * Búsqueda de la caja, TODA en el teléfono (instantánea y sin red): por nombre (y variante), código corto o código de barras, sin distinguir
 * mayúsculas ni tildes («cafe» encuentra «Café»). Sin texto: los frecuentes que marcó el negocio (en su orden) y, si son pocos, se completan
 * con los más vendidos de los últimos 30 días (sin repetir).
 */
object ProductSearch {
    /** Con menos frecuentes que esto, se completa con los más vendidos. */
    const val FILL_TARGET = 8
    const val MAX_RESULTS = 100
    /** Ventana de «Más vendidos», en jornadas del negocio. */
    const val BEST_SELLER_DAYS = 30

    private val MARKS = Regex("\\p{Mn}+")
    private val SPACES = Regex("\\s+")

    /** Minúsculas, sin tildes ni diéresis y con los espacios reducidos a uno. */
    fun normalize(s: String?): String {
        if (s.isNullOrBlank()) return ""
        val plain = MARKS.replace(Normalizer.normalize(s, Normalizer.Form.NFD), "")
        return SPACES.replace(plain.lowercase(Locale.ROOT).trim(), " ")
    }

    fun addFlow(p: ProductEntity): AddFlow = when (p.pricing) {
        Pricing.BY_WEIGHT -> AddFlow.WEIGH
        Pricing.OPEN -> AddFlow.OPEN_PRICE
        else -> AddFlow.DIRECT
    }

    /** Catálogo preparado para buscar (lo normalizado se calcula una vez por cambio del catálogo, no en cada tecla). */
    class Index(products: List<ProductEntity>) {
        private class Entry(val p: ProductEntity, val name: String, val words: List<String>, val short: String, val barcode: String, val sortKey: String)

        private val entries = products.filter { it.active }.map { p ->
            val name = normalize(listOfNotNull(p.name, p.variant).joinToString(" "))
            Entry(p, name, name.split(' '), normalize(p.shortCode), p.barcode?.trim().orEmpty(), normalize(p.name))
        }

        fun search(query: String, limit: Int = MAX_RESULTS): List<ProductEntity> {
            val q = normalize(query)
            if (q.isEmpty()) return emptyList()
            val forms = Barcodes.forms(query.trim()).toSet()
            val tokens = q.split(' ')
            return entries.mapNotNull { e -> rank(e, q, tokens, forms)?.let { e to it } }
                .sortedWith(compareBy<Pair<Entry, Int>>({ it.second }, { it.first.sortKey }, { it.first.p.id }))
                .take(limit).map { it.first.p }
        }

        /** 0 = código exacto (barras o corto), 1 = código corto que empieza así, 2 = nombre que empieza así, 3 = cada palabra buscada empieza una palabra, 4 = contiene, 5 = parte de un código de barras. */
        private fun rank(e: Entry, q: String, tokens: List<String>, forms: Set<String>): Int? {
            if (e.barcode.isNotEmpty() && e.barcode in forms) return 0
            if (e.short.isNotEmpty() && e.short == q) return 0
            if (e.short.isNotEmpty() && e.short.startsWith(q)) return 1
            if (e.name.startsWith(q)) return 2
            if (tokens.all { t -> e.name.contains(t) }) return if (tokens.all { t -> e.words.any { it.startsWith(t) } }) 3 else 4
            if (q.length >= 4 && e.barcode.contains(q)) return 5
            return null
        }
    }

    fun search(products: List<ProductEntity>, query: String, limit: Int = MAX_RESULTS): List<ProductEntity> = Index(products).search(query, limit)

    /** Los frecuentes marcados (activos), en el orden que eligió el negocio; los que no tienen posición van al final, por nombre. */
    fun marked(products: List<ProductEntity>): List<ProductEntity> =
        products.filter { it.active && it.isQuick }
            .sortedWith(compareBy<ProductEntity>({ it.quickPosition == null }, { it.quickPosition ?: 0 }, { normalize(it.name) }, { it.id }))

    /**
     * Sugerencias con el buscador vacío: primero los frecuentes (todos); si son menos de `target`, se completan hasta `target` con los más vendidos
     * (`bestSellerIds`, del más vendido al menos), sin repetir y solo si siguen activos en el catálogo.
     */
    fun suggestions(products: List<ProductEntity>, bestSellerIds: List<String>, target: Int = FILL_TARGET): List<Suggestion> {
        val marked = marked(products)
        val out = marked.mapTo(mutableListOf()) { Suggestion(it, SuggestionSource.FREQUENT) }
        if (out.size >= target) return out
        val byId = products.filter { it.active }.associateBy { it.id }
        val seen = marked.mapTo(mutableSetOf()) { it.id }
        for (id in bestSellerIds) {
            if (out.size >= target) break
            val p = byId[id] ?: continue
            if (seen.add(id)) out += Suggestion(p, SuggestionSource.BEST_SELLER)
        }
        return out
    }

    /** Lo que muestra la pestaña «Productos» para lo escrito. */
    fun pane(index: Index, products: List<ProductEntity>, query: String, bestSellerIds: List<String>): ProductsPane {
        val marked = marked(products)
        return if (query.isBlank()) ProductsPane(query, suggestions(products, bestSellerIds), marked = marked)
        else ProductsPane(query, results = index.search(query), marked = marked)
    }

    /** «Crear producto “x”» desde una búsqueda sin resultados: si lo escrito parece un código de barras (solo cifras, 6 o más) va como código; si no, como nombre. */
    fun draftFor(query: String): Pair<String, String> {
        val q = query.trim()
        return if (q.length >= 6 && q.all { it.isDigit() }) "" to q else q to ""
    }
}

/**
 * Marcar/quitar frecuentes y ordenarlos. Cada cambio es un PRODUCT_UPSERT completo (el servidor reemplaza el producto): todo lo demás se conserva
 * con `ProductPayload`.
 */
object Frequents {
    /** Posición para uno que se marca ahora: al final de los que ya hay. */
    fun nextPosition(products: List<ProductEntity>): Int {
        val marked = products.filter { it.active && it.isQuick }
        return maxOf(marked.mapNotNull { it.quickPosition }.maxOrNull() ?: 0, marked.size) + 1
    }

    /** Marca (al final) o quita (sin posición) un producto de los frecuentes. */
    fun toggle(p: ProductEntity, products: List<ProductEntity>): com.cuadra.caja.data.remote.ProductInputDto =
        if (p.isQuick) ProductPayload.withFrequent(p, false, null) else ProductPayload.withFrequent(p, true, nextPosition(products.filter { it.id != p.id }))

    /**
     * Mueve un frecuente (`delta` −1 = subir, +1 = bajar) dentro de `marked` (ya en su orden). Renumera 1..n y devuelve SOLO los que cambian de posición
     * (id → lo que se envía). En los extremos o con un id desconocido no cambia nada.
     */
    fun move(marked: List<ProductEntity>, id: String, delta: Int): List<Pair<String, com.cuadra.caja.data.remote.ProductInputDto>> {
        val from = marked.indexOfFirst { it.id == id }
        if (from < 0 || delta == 0) return emptyList()
        val to = (from + delta).coerceIn(0, marked.lastIndex)
        if (to == from) return emptyList()
        val order = marked.toMutableList().also { it.add(to, it.removeAt(from)) }
        return order.mapIndexedNotNull { i, p -> if (p.quickPosition != i + 1) p.id to ProductPayload.withFrequent(p, true, i + 1) else null }
    }
}

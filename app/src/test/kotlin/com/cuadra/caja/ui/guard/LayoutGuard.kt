package com.cuadra.caja.ui.guard

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.TextLayoutResult
import com.cuadra.caja.ui.common.TAG_ALLOW_ELLIPSIS
import com.cuadra.caja.ui.common.TAG_BOTTOM_BAR
import com.cuadra.caja.ui.common.TAG_DIALOG_LAYER
import com.cuadra.caja.ui.common.TAG_KEY
import com.cuadra.caja.ui.common.TAG_OVERLAY
import com.cuadra.caja.ui.common.TAG_PINNED_ACTION
import com.cuadra.caja.ui.common.TAG_SCREEN_BODY
import com.cuadra.caja.ui.common.TAG_STICKY
import com.cuadra.caja.ui.common.TAG_TOTAL_CARD

/** Un problema de diseño encontrado: dónde (pantalla + ajustes + nodo) y por qué (regla + detalle). */
data class Finding(val screen: String, val cfg: GuardCfg, val rule: GuardRule, val node: String, val detail: String) {
    override fun toString() = "[$screen | $cfg] ${rule.code} «$node» $detail"
}

/** Las reglas de docs/notas/reglas-de-interfaz-app.md que revisa la guardia. */
enum class GuardRule(val code: String, val meaning: String) {
    A_TEXT_CLIPPED("A", "texto recortado o con «…» sin etiqueta AllowEllipsis"),
    B_OUTSIDE_SCREEN("B", "elemento fuera de la pantalla (horizontal siempre; vertical fuera de una zona con desplazamiento) o zona que se desplaza de lado"),
    C_TOUCH_TARGET("C", "elemento tocable menor de 48 x 48 dp"),
    D_WORD_SPLIT("D", "palabra partida a media palabra entre dos líneas"),
    E_OVERLAP("E", "dos elementos tocables se traslapan"),
    F_CLIPPED_BY_PARENT("F", "elemento recortado por su contenedor"),
    G_PINNED_ACTION("G", "acción fija (PinnedAction) fuera de la pantalla o dentro de una zona que se desplaza"),
    H_BODY_TOO_SMALL("H", "el centro de una pantalla con encabezado y pie fijos se quedó con menos de 96 dp (el resto lo comen los fijos)"),
    I_KEY_HIDDEN("I", "una tecla de la calculadora (0-9, ., ⌫, ×, Agregar) fuera de la ventana visible, recortada, tapada por la barra inferior o que no se dibuja"),
    K_RECEIPT_BADGE("K", "el número del botón «Recibo · N» de la barra inferior no se dibuja, se corta, se achica, lleva «…» o se sale de su botón o de la pantalla"),
    J_TOTAL_HIDDEN("J", "la tarjeta oscura del total de la caja no se dibuja, está fuera de la ventana, recortada, o tapada por las teclas, la barra inferior, el aviso flotante o algo fijo"),
}


/** Una palabra más larga que esto (una URL, un código) puede partirse al final del renglón si es lo único posible; las normales, nunca. */
const val MAX_UNBREAKABLE = com.cuadra.caja.ui.common.MAX_UNBREAKABLE_WORD

/** Alto mínimo útil del centro de una pantalla (`ScreenFrame`). */
const val MIN_BODY_DP = 96f

private const val EPS = 1.5f // px

/**
 * Revisa el árbol de semántica ya dibujado y devuelve TODOS los problemas de diseño (no se detiene en el primero).
 * `unmerged` es el árbol sin fusionar (cada Text, cada botón por separado).
 */
object LayoutGuard {

    fun inspect(root: SemanticsNode, screen: String, cfg: GuardCfg, density: Float): List<Finding> {
        val out = mutableListOf<Finding>()
        val widthPx = cfg.widthDp * density
        val heightPx = cfg.heightDp * density
        val nodes = mutableListOf<SemanticsNode>()
        fun walk(n: SemanticsNode) { nodes += n; n.children.forEach(::walk) }
        walk(root)
        // Si hay un diálogo en línea, solo se revisa su capa (lo de debajo se revisa en su propio caso).
        val layer = nodes.lastOrNull { it.config.getOrNull(SemanticsProperties.TestTag) == TAG_DIALOG_LAYER }
        if (layer != null) {
            nodes.clear()
            fun walkLayer(n: SemanticsNode) { nodes += n; n.children.forEach(::walkLayer) }
            walkLayer(layer)
        }

        fun add(rule: GuardRule, n: SemanticsNode, detail: String) { out += Finding(screen, cfg, rule, describe(n), detail) }
        fun rectOf(n: SemanticsNode): FloatArray {
            val p = n.positionInRoot
            return floatArrayOf(p.x, p.y, p.x + n.size.width, p.y + n.size.height)
        }
        fun ancestors(n: SemanticsNode): List<SemanticsNode> { val l = mutableListOf<SemanticsNode>(); var p = n.parent; while (p != null) { l += p; p = p.parent }; return l }
        fun hidden(n: SemanticsNode) = n.config.contains(SemanticsProperties.InvisibleToUser)
        fun inVerticalScroll(n: SemanticsNode) = ancestors(n).any { it.config.contains(SemanticsProperties.VerticalScrollAxisRange) }
        fun isEditable(n: SemanticsNode) = n.config.contains(SemanticsProperties.EditableText)
        fun textLayout(n: SemanticsNode): TextLayoutResult? {
            val action = n.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action ?: return null
            val list = mutableListOf<TextLayoutResult>()
            return if (action(list)) list.firstOrNull() else null
        }
        fun tagged(n: SemanticsNode, tag: String) = n.config.getOrNull(SemanticsProperties.TestTag) == tag

        // Un aviso flotante pasa POR ENCIMA del contenido a propósito (no ocupa lugar); que la prueba de posiciones fijas garantice que nada se mueve.
        fun inOverlay(n: SemanticsNode) = tagged(n, TAG_OVERLAY) || ancestors(n).any { tagged(it, TAG_OVERLAY) }
        fun isSticky(n: SemanticsNode) = tagged(n, TAG_STICKY) || ancestors(n).any { tagged(it, TAG_STICKY) }

        val visible = nodes.filter { !hidden(it) && it.size.width > 0 && it.size.height > 0 }

        for (n in visible) {
            val r = rectOf(n)
            val isText = textLayout(n) != null && !isEditable(n)
            val clickable = n.config.contains(SemanticsActions.OnClick)
            val scrollsSideways = n.config.getOrNull(SemanticsProperties.HorizontalScrollAxisRange)?.let { it.maxValue() > 0f } == true

            // B: nada sale de la pantalla a los lados; una zona que se desplaza de lado esconde contenido.
            if (scrollsSideways) add(GuardRule.B_OUTSIDE_SCREEN, n, "se desplaza de lado (usa ChipFlow/FlowRow)")
            if (isText || clickable) {
                if (r[0] < -EPS || r[2] > widthPx + EPS) add(GuardRule.B_OUTSIDE_SCREEN, n, "x=${dp(r[0], density)}..${dp(r[2], density)}dp, pantalla ${cfg.widthDp}dp")
                if (!inVerticalScroll(n) && (r[1] < -EPS || r[3] > heightPx + EPS)) add(GuardRule.B_OUTSIDE_SCREEN, n, "y=${dp(r[1], density)}..${dp(r[3], density)}dp fuera de la pantalla (${cfg.heightDp}dp) y sin zona de desplazamiento")
            }

            // A y D: texto.
            if (isText) {
                val t = textLayout(n)!!
                val allowed = tagged(n, TAG_ALLOW_ELLIPSIS)
                if (t.hasVisualOverflow && !allowed) add(GuardRule.A_TEXT_CLIPPED, n, "no cabe (líneas=${t.lineCount}, ancho=${t.size.width}px, contenido=${t.multiParagraph.width.toInt()}px, alto=${t.size.height}/${t.multiParagraph.height.toInt()}px)")
                splitWord(t)?.let { add(GuardRule.D_WORD_SPLIT, n, "«$it» se parte entre dos líneas") }
            }

            // F: recortado por un contenedor (bounds recortados < tamaño real). El eje vertical se ignora dentro de zonas con desplazamiento;
            // lo que queda fuera de la ventana de esa zona (recortado del todo) tampoco cuenta.
            val b = n.boundsInRoot
            val scrolledAway = inVerticalScroll(n) && (b.width <= 0f || b.height <= 0f)
            if ((isText || clickable) && !scrolledAway) {
                val clippedX = b.width < n.size.width - EPS
                val clippedY = b.height < n.size.height - EPS && !inVerticalScroll(n)
                val offRoot = r[0] < -EPS || r[2] > widthPx + EPS
                if (clippedX && !offRoot) add(GuardRule.F_CLIPPED_BY_PARENT, n, "visible ${dp(b.width, density)}dp de ${dp(n.size.width.toFloat(), density)}dp de ancho")
                if (clippedY && !(r[3] > heightPx + EPS)) add(GuardRule.F_CLIPPED_BY_PARENT, n, "visible ${dp(b.height, density)}dp de ${dp(n.size.height.toFloat(), density)}dp de alto")
            }

            // C: tamaño mínimo tocable.
            if (clickable && n.config.getOrNull(SemanticsProperties.Role).let { it != Role.Switch && it != Role.Checkbox && it != Role.RadioButton }) {
                // Área tocable real: los componentes de Material (Switch…) amplían su zona táctil a 48 dp aunque se dibujen más chicos.
                // (solo los interruptores y casillas de Material: el resto de lo tocable debe MEDIR 48 dp de verdad).
                val role = n.config.getOrNull(SemanticsProperties.Role)
                val materialToggle = role == Role.Switch || role == Role.Checkbox || role == Role.RadioButton
                val touch = n.touchBoundsInRoot
                val wDp = (if (materialToggle) maxOf(n.size.width.toFloat(), touch.width) else n.size.width.toFloat()) / density
                val hDp = (if (materialToggle) maxOf(n.size.height.toFloat(), touch.height) else n.size.height.toFloat()) / density
                if (wDp < 47.5f || hDp < 47.5f) add(GuardRule.C_TOUCH_TARGET, n, "mide ${"%.1f".format(wDp)} x ${"%.1f".format(hDp)} dp")
            }

            // H: el centro de la pantalla conserva un alto útil aunque haya encabezado y pie fijos.
            if (tagged(n, TAG_SCREEN_BODY) && n.size.height / density < MIN_BODY_DP) add(GuardRule.H_BODY_TOO_SMALL, n, "mide ${"%.0f".format(n.size.height / density)} dp de alto")

            // G: acciones fijas.
            if (tagged(n, TAG_PINNED_ACTION)) {
                if (r[1] < -EPS || r[3] > heightPx + EPS || r[0] < -EPS || r[2] > widthPx + EPS) add(GuardRule.G_PINNED_ACTION, n, "fuera de la pantalla: y=${dp(r[1], density)}..${dp(r[3], density)}dp")
                if (inVerticalScroll(n)) add(GuardRule.G_PINNED_ACTION, n, "dentro de una zona que se desplaza: no está fija")
            }
        }

        // Los textos deben caber en su botón/chip/tarjeta tocable (el texto que se sale de su contenedor se ve pisando lo de al lado).
        for (n in visible) {
            val isText = textLayout(n) != null && !isEditable(n)
            if (!isText) continue
            val parent = ancestors(n).firstOrNull { it.config.contains(SemanticsActions.OnClick) && !isEditable(it) } ?: continue
            if (ancestors(n).any { isEditable(it) }) continue
            val a = rectOf(n)
            val p = rectOf(parent)
            if (a[0] < p[0] - EPS || a[2] > p[2] + EPS || a[1] < p[1] - EPS || a[3] > p[3] + EPS) {
                if (!inVerticalScroll(n) || (a[0] < p[0] - EPS || a[2] > p[2] + EPS)) out += Finding(screen, cfg, GuardRule.F_CLIPPED_BY_PARENT, describe(n), "se sale de «${describe(parent)}»")
            }
        }

        // E: elementos tocables que se traslapan (los anidados no cuentan: un icono dentro de un campo es normal).
        val clickables = visible.filter { it.config.contains(SemanticsActions.OnClick) }
        val anc = clickables.associateWith { c -> ancestors(c).map { it.id }.toSet() }
        for (i in clickables.indices) for (j in i + 1 until clickables.size) {
            val a = clickables[i]
            val b = clickables[j]
            if (b.id in anc.getValue(a) || a.id in anc.getValue(b)) continue
            // Un encabezado fijo (buscador) pasa por encima de las filas que se desplazan por debajo: es lo esperado.
            if (isSticky(a) || isSticky(b)) continue
            if (inOverlay(a) || inOverlay(b)) continue
            // Con los límites RECORTADOS por su contenedor: lo que una zona con desplazamiento deja fuera de su ventana no se traslapa con nada.
            val ba = a.boundsInRoot
            val bb = b.boundsInRoot
            val w = minOf(ba.right, bb.right) - maxOf(ba.left, bb.left)
            val h = minOf(ba.bottom, bb.bottom) - maxOf(ba.top, bb.top)
            if (w > EPS && h > EPS) out += Finding(screen, cfg, GuardRule.E_OVERLAP, describe(a), "se traslapa con «${describe(b)}» (${dp(w, density)} x ${dp(h, density)} dp)")
        }

        // I: cada tecla de la calculadora se ve ENTERA dentro de la ventana (sin recortes de un contenedor que se desplaza) y ninguna barra fija la tapa.
        val bars = visible.filter { tagged(it, TAG_BOTTOM_BAR) }
        for (k in visible.filter { tagged(it, TAG_KEY) }) {
            val r = rectOf(k)
            val b = k.boundsInRoot
            if (r[0] < -EPS || r[2] > widthPx + EPS || r[1] < -EPS || r[3] > heightPx + EPS) add(GuardRule.I_KEY_HIDDEN, k, "fuera de la ventana: x=${dp(r[0], density)}..${dp(r[2], density)}dp, y=${dp(r[1], density)}..${dp(r[3], density)}dp (ventana ${cfg.widthDp} x ${cfg.heightDp}dp)")
            else if (b.width < k.size.width - EPS || b.height < k.size.height - EPS) add(GuardRule.I_KEY_HIDDEN, k, "recortada: se ve ${dp(b.width, density)} x ${dp(b.height, density)}dp de ${dp(k.size.width.toFloat(), density)} x ${dp(k.size.height.toFloat(), density)}dp")
            for (bar in bars) {
                val q = rectOf(bar)
                val w = minOf(r[2], q[2]) - maxOf(r[0], q[0])
                val h = minOf(r[3], q[3]) - maxOf(r[1], q[1])
                if (w > EPS && h > EPS) add(GuardRule.I_KEY_HIDDEN, k, "tapada por la barra inferior (${dp(w, density)} x ${dp(h, density)}dp)")
            }
        }
        return out.distinct()
    }

    /**
     * J: la tarjeta del total (`TAG_TOTAL_CARD`) existe UNA vez, está ENTERA dentro de la ventana, sin recorte de un contenedor, ninguna tecla, la barra inferior, el aviso
     * flotante ni otra zona fija la tapan, y lo que lleva dentro (monto, botón de escanear, «Lector listo») queda dentro de ella.
     */
    fun inspectTotal(root: SemanticsNode, screen: String, cfg: GuardCfg, density: Float): List<Finding> {
        val out = mutableListOf<Finding>()
        val nodes = mutableListOf<SemanticsNode>()
        fun walk(n: SemanticsNode) { nodes += n; n.children.forEach(::walk) }
        walk(root)
        fun tagged(n: SemanticsNode, tag: String) = n.config.getOrNull(SemanticsProperties.TestTag) == tag
        fun rectOf(n: SemanticsNode): FloatArray { val p = n.positionInRoot; return floatArrayOf(p.x, p.y, p.x + n.size.width, p.y + n.size.height) }
        val cards = nodes.filter { tagged(it, TAG_TOTAL_CARD) }
        fun fail(node: SemanticsNode?, detail: String) { out += Finding(screen, cfg, GuardRule.J_TOTAL_HIDDEN, node?.let { describe(it) } ?: "tarjeta del total", detail) }
        if (cards.size != 1) { fail(cards.firstOrNull(), "se dibujan ${cards.size} tarjetas del total y se esperaba 1"); return out }
        val card = cards.single()
        val r = rectOf(card)
        val widthPx = cfg.widthDp * density
        val heightPx = cfg.heightDp * density
        if (card.size.width <= 0 || card.size.height <= 0) { fail(card, "mide 0"); return out }
        if (r[0] < -EPS || r[2] > widthPx + EPS || r[1] < -EPS || r[3] > heightPx + EPS) fail(card, "fuera de la ventana: x=${dp(r[0], density)}..${dp(r[2], density)}dp, y=${dp(r[1], density)}..${dp(r[3], density)}dp (ventana ${cfg.widthDp} x ${cfg.heightDp}dp)")
        val b = card.boundsInRoot
        if (b.width < card.size.width - EPS || b.height < card.size.height - EPS) fail(card, "recortada: se ve ${dp(b.width, density)} x ${dp(b.height, density)}dp de ${dp(card.size.width.toFloat(), density)} x ${dp(card.size.height.toFloat(), density)}dp")
        fun overlap(o: SemanticsNode): Pair<Float, Float>? {
            val q = rectOf(o)
            val w = minOf(r[2], q[2]) - maxOf(r[0], q[0])
            val h = minOf(r[3], q[3]) - maxOf(r[1], q[1])
            return if (w > EPS && h > EPS) w to h else null
        }
        fun ancestorsOf(n: SemanticsNode): List<SemanticsNode> { val l = mutableListOf<SemanticsNode>(); var p = n.parent; while (p != null) { l += p; p = p.parent }; return l }
        for (o in nodes) {
            if (o.id == card.id || o.size.width <= 0 || o.size.height <= 0) continue
            val what = when {
                tagged(o, TAG_KEY) -> "una tecla"
                tagged(o, TAG_BOTTOM_BAR) -> "la barra inferior"
                tagged(o, TAG_OVERLAY) -> "el aviso flotante"
                tagged(o, TAG_PINNED_ACTION) -> "una zona fija (calculadora o barra)"
                else -> continue
            }
            overlap(o)?.let { (w, h) -> fail(card, "tapada por $what (${dp(w, density)} x ${dp(h, density)}dp)") }
        }
        // Lo que lleva dentro debe quedar dentro de la tarjeta (texto y botones): el monto y el botón de escanear no se salen ni se recortan.
        val inside = nodes.filter { n -> n.id != card.id && ancestorsOf(n).any { it.id == card.id } && n.size.width > 0 && n.size.height > 0 && (n.config.contains(SemanticsActions.OnClick) || n.config.getOrNull(SemanticsActions.GetTextLayoutResult) != null) }
        if (inside.isEmpty()) fail(card, "la tarjeta del total no lleva monto ni botón dentro")
        for (n in inside) {
            val q = rectOf(n)
            if (q[0] < r[0] - EPS || q[2] > r[2] + EPS || q[1] < r[1] - EPS || q[3] > r[3] + EPS) fail(n, "se sale de la tarjeta del total")
        }
        return out.distinct()
    }

    /**
     * K: el número del botón «Recibo · N» (`TAG_RECEIPT_BADGE`) existe una vez, con los dígitos de `expected`, en una sola línea, sin desbordar ni «…», con la letra
     * SIN reducir, entero dentro de la ventana y dentro del botón que lo contiene (sin recorte del contenedor).
     */
    fun inspectReceiptBadge(root: SemanticsNode, screen: String, cfg: GuardCfg, density: Float, expected: Int): List<Finding> {
        val out = mutableListOf<Finding>()
        val nodes = mutableListOf<SemanticsNode>()
        fun walk(n: SemanticsNode) { nodes += n; n.children.forEach(::walk) }
        walk(root)
        val badges = nodes.filter { it.config.getOrNull(SemanticsProperties.TestTag) == com.cuadra.caja.ui.common.TAG_RECEIPT_BADGE }
        fun fail(n: SemanticsNode?, detail: String) { out += Finding(screen, cfg, GuardRule.K_RECEIPT_BADGE, n?.let { describe(it) } ?: "número del recibo", detail) }
        if (badges.size != 1) { fail(badges.firstOrNull(), "se dibujan ${badges.size} números del recibo y se esperaba 1"); return out }
        val n = badges.single()
        val action = n.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action
        val list = mutableListOf<TextLayoutResult>()
        val t = if (action != null && action(list)) list.firstOrNull() else null
        if (t == null) { fail(n, "no tiene diseño de texto"); return out }
        val text = t.layoutInput.text.text
        if (text.filter { it.isDigit() } != expected.toString()) fail(n, "muestra «$text» y se esperaba el número $expected")
        if (t.lineCount != 1) fail(n, "ocupa ${t.lineCount} líneas")
        if (t.hasVisualOverflow || t.didOverflowWidth || t.didOverflowHeight) fail(n, "el texto desborda su caja")
        if (t.getLineEnd(0, visibleEnd = true) < text.length) fail(n, "termina en «…» o cortado")
        if (n.size.width < t.size.width || n.size.height < t.size.height) fail(n, "la caja (${n.size.width}x${n.size.height}px) es menor que el texto (${t.size.width}x${t.size.height}px)")
        val b = n.boundsInRoot
        if (b.width < n.size.width - EPS || b.height < n.size.height - EPS) fail(n, "recortado por un contenedor: se ve ${dp(b.width, density)}x${dp(b.height, density)}dp de ${dp(n.size.width.toFloat(), density)}x${dp(n.size.height.toFloat(), density)}dp")
        val p = n.positionInRoot
        if (p.x < -EPS || p.x + n.size.width > cfg.widthDp * density + EPS) fail(n, "fuera de la ventana")
        var a = n.parent
        while (a != null && !a.config.contains(SemanticsActions.OnClick)) a = a.parent
        if (a == null) fail(n, "no está dentro de un botón")
        else {
            val ap = a.positionInRoot
            if (p.x < ap.x - EPS || p.x + n.size.width > ap.x + a.size.width + EPS) fail(n, "se sale del botón: número x=${dp(p.x, density)}..${dp(p.x + n.size.width, density)}dp, botón x=${dp(ap.x, density)}..${dp(ap.x + a.size.width, density)}dp")
        }
        return out.distinct()
    }

    /** La palabra (corta) que se parte entre dos líneas, o null. */
    private fun splitWord(t: TextLayoutResult): String? {
        val text = t.layoutInput.text.text
        for (i in 0 until t.lineCount - 1) {
            val end = t.getLineEnd(i, visibleEnd = false)
            if (end in 1 until text.length && text[end - 1].isLetterOrDigit() && text[end].isLetterOrDigit()) {
                var a = end - 1
                while (a > 0 && text[a - 1].isLetterOrDigit()) a--
                var b = end
                while (b < text.length && text[b].isLetterOrDigit()) b++
                if (b - a <= MAX_UNBREAKABLE) return text.substring(a, b)
            }
        }
        return null
    }

    private fun dp(px: Float, density: Float) = "%.0f".format(px / density)

    fun describe(n: SemanticsNode): String {
        fun firstText(x: SemanticsNode): String? = x.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text } ?: x.children.firstNotNullOfOrNull { firstText(it) }
        val text = firstText(n)
        val desc = n.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ")
        val tag = n.config.getOrNull(SemanticsProperties.TestTag)
        val label = text ?: desc ?: tag ?: n.config.getOrNull(SemanticsProperties.Role)?.toString() ?: "nodo#${n.id}"
        return label.replace('\n', ' ').let { if (it.length > 48) it.take(45) + "..." else it }
    }
}
